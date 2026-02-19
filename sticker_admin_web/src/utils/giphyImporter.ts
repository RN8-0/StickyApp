import { GiphyFetch } from '@giphy/js-fetch-api';
import { storage } from '../firebase';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { stickerProcessor } from './stickerProcessor';
import type { Sticker } from '../types';

// Giphy API Configuration
const GIPHY_API_KEY = 'LLWhfEaYJSNyuhTXUEnSol15YU00raps';
const gf = new GiphyFetch(GIPHY_API_KEY);

export interface GiphyImportProgress {
    current: number;
    total: number;
    message: string;
    currentSticker?: string;
}

export interface GiphyImportOptions {
    category: string;
    count: number;
    packId: string;
    onProgress?: (progress: GiphyImportProgress) => void;
}

/**
 * Giphy'den GIF'i indir ve Blob olarak döndür
 */
async function downloadGiphyGif(url: string): Promise<Blob> {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`GIF download failed: ${response.statusText}`);
    }
    return await response.blob();
}

/**
 * Giphy kategorisinden sticker'ları otomatik import et
 */
export async function importStickersFromGiphy(
    options: GiphyImportOptions
): Promise<Sticker[]> {
    const { category, count, packId, onProgress } = options;
    
    onProgress?.({
        current: 0,
        total: count,
        message: `Giphy'den "${category}" araması yapılıyor...`
    });

    try {
        // Giphy'den GIF'leri al (search veya trending)
        let giphyData;
        
        if (category === 'trending') {
            giphyData = await gf.trending({ limit: count, type: 'stickers' });
        } else {
            giphyData = await gf.search(category, { 
                limit: count, 
                type: 'stickers',
                sort: 'relevant'
            });
        }

        const gifs = giphyData.data;
        
        if (gifs.length === 0) {
            throw new Error(`"${category}" kategorisinde sticker bulunamadı.`);
        }

        onProgress?.({
            current: 0,
            total: gifs.length,
            message: `${gifs.length} sticker bulundu, işleniyor...`
        });

        const importedStickers: Sticker[] = [];
        
        // Her GIF'i işle (batch processing)
        for (let i = 0; i < gifs.length; i++) {
            const gif = gifs[i];
            const gifTitle = gif.title || `Sticker ${i + 1}`;
            
            try {
                onProgress?.({
                    current: i + 1,
                    total: gifs.length,
                    message: `İşleniyor: ${gifTitle}`,
                    currentSticker: gif.images.downsized_medium.url
                });

                // 1. GIF'i indir (en uygun boyut)
                const gifUrl = gif.images.fixed_height.url || gif.images.downsized_medium.url;
                const gifBlob = await downloadGiphyGif(gifUrl);
                const gifFile = new File([gifBlob], `${gif.id}.gif`, { type: 'image/gif' });

                // 2. GIF → WebP dönüşümü (mevcut stickerProcessor ile)
                const webpBlob = await stickerProcessor.processAnimated(gifFile, (progress) => {
                    onProgress?.({
                        current: i + 1,
                        total: gifs.length,
                        message: `${gifTitle}: ${progress.message}`,
                        currentSticker: gif.images.downsized_medium.url
                    });
                });

                // 3. WhatsApp 500KB kontrolü
                if (webpBlob.size > 500 * 1024) {
                    console.warn(`[GIPHY IMPORT] Sticker çok büyük, atlanıyor: ${gifTitle} (${Math.round(webpBlob.size / 1024)}KB)`);
                    continue;
                }

                // 4. Firebase Storage'a yükle
                const fileName = `giphy_${gif.id}_${Date.now()}.webp`;
                const storagePath = `stickers/${packId}/${fileName}`;
                const storageRef = ref(storage, storagePath);
                
                await uploadBytes(storageRef, webpBlob);
                const downloadURL = await getDownloadURL(storageRef);

                // 5. Sticker objesini oluştur
                importedStickers.push({
                    image_file: fileName,
                    url: downloadURL,
                    emojis: extractEmojisFromTitle(gif.title) || ['😀']
                });

                onProgress?.({
                    current: i + 1,
                    total: gifs.length,
                    message: `✅ ${gifTitle} eklendi (${Math.round(webpBlob.size / 1024)}KB)`
                });

            } catch (error: any) {
                console.error(`[GIPHY IMPORT] Hata (${gifTitle}):`, error);
                // Hata olsa bile devam et
                onProgress?.({
                    current: i + 1,
                    total: gifs.length,
                    message: `⚠️ ${gifTitle} atlandı: ${error.message}`
                });
            }
        }

        return importedStickers;

    } catch (error: any) {
        console.error('[GIPHY IMPORT] Genel hata:', error);
        throw new Error(`Giphy import hatası: ${error.message}`);
    }
}

/**
 * GIF başlığından emoji çıkar (WhatsApp için)
 */
function extractEmojisFromTitle(title: string | undefined): string[] | null {
    if (!title) return null;
    
    // Emoji regex pattern
    const emojiRegex = /[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu;
    
    const emojis = title.match(emojiRegex);
    
    if (emojis && emojis.length > 0) {
        return emojis.slice(0, 3); // Max 3 emoji
    }
    
    // Başlıktan kategori-based emoji tahmin et
    const lowerTitle = title.toLowerCase();
    
    const emojiMap: Record<string, string[]> = {
        'cat': ['🐱', '😻', '🐈'],
        'dog': ['🐶', '🐕', '🦮'],
        'love': ['❤️', '💕', '😍'],
        'happy': ['😊', '😁', '😄'],
        'sad': ['😢', '😭', '🥺'],
        'funny': ['😂', '🤣', '😆'],
        'angry': ['😠', '😡', '💢'],
        'food': ['🍔', '🍕', '🍩'],
        'party': ['🎉', '🥳', '🎊'],
        'animal': ['🐾', '🦁', '🐻']
    };
    
    for (const [keyword, emojis] of Object.entries(emojiMap)) {
        if (lowerTitle.includes(keyword)) {
            return emojis;
        }
    }
    
    return null;
}

/**
 * Popüler kategoriler listesi
 */
export const GIPHY_CATEGORIES = [
    { id: 'trending', name: 'Trend Olanlar', emoji: '🔥' },
    { id: 'cats', name: 'Kediler', emoji: '🐱' },
    { id: 'dogs', name: 'Köpekler', emoji: '🐶' },
    { id: 'love', name: 'Aşk', emoji: '❤️' },
    { id: 'happy', name: 'Mutluluk', emoji: '😊' },
    { id: 'sad', name: 'Üzgün', emoji: '😢' },
    { id: 'funny', name: 'Komik', emoji: '😂' },
    { id: 'reactions', name: 'Tepkiler', emoji: '😮' },
    { id: 'memes', name: 'Memes', emoji: '🎭' },
    { id: 'anime', name: 'Anime', emoji: '⛩️' },
    { id: 'gaming', name: 'Oyun', emoji: '🎮' },
    { id: 'food', name: 'Yemek', emoji: '🍔' },
    { id: 'animals', name: 'Hayvanlar', emoji: '🐾' },
    { id: 'party', name: 'Parti', emoji: '🎉' },
    { id: 'congratulations', name: 'Tebrikler', emoji: '👏' },
    { id: 'good morning', name: 'Günaydın', emoji: '☀️' },
    { id: 'good night', name: 'İyi Geceler', emoji: '🌙' },
];
