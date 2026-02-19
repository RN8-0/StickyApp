import { storage } from '../firebase';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { stickerProcessor } from './stickerProcessor';
import type { Sticker } from '../types';

import { fetchFromKlipy } from './klipyImporter';

export type StickerSource = 'giphy' | 'klipy';

export interface StickerImportProgress {
    current: number;
    total: number;
    message: string;
    currentSticker?: string;
}

export interface StickerImportOptions {
    source: StickerSource;
    contentType?: 'gifs' | 'stickers';
    query: string;
    count: number;
    packId: string;
    onProgress?: (progress: StickerImportProgress) => void;
}

interface GifData {
    id: string;
    title: string;
    url: string;
    previewUrl: string;
}

async function fetchFromGiphy(query: string, count: number, contentType: 'gifs' | 'stickers' = 'stickers'): Promise<GifData[]> {
    // Cloud Function proxy kullan (CORS bypass)
    const endpoint = query === 'trending' ? 'trending' : 'search';
    const proxyUrl = `https://us-central1-sticky-dcd20.cloudfunctions.net/giphyProxy?endpoint=${endpoint}&type=${contentType}&limit=${count}${query !== 'trending' ? `&query=${encodeURIComponent(query)}` : ''}`;

    const response = await fetch(proxyUrl);
    
    if (!response.ok) {
        throw new Error(`Cloud Function error: ${response.status}`);
    }

    const giphyData = await response.json();

    return giphyData.data.map((gif: any) => ({
        id: String(gif.id),
        title: gif.title || 'Sticker',
        url: gif.images.fixed_height.url || gif.images.downsized_medium.url,
        previewUrl: gif.images.downsized_medium.url
    }));
}

async function fetchFromKlipyAdapter(query: string, count: number): Promise<GifData[]> {
    const results = await fetchFromKlipy(query, count);
    
    if (results.length === 0) {
        throw new Error(`"${query}" için sonuç bulunamadı`);
    }

    return results.map(item => ({
        id: item.id,
        title: item.title || 'Sticker',
        url: item.url,
        previewUrl: item.previewUrl
    }));
}

async function downloadGif(url: string): Promise<Blob> {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Download failed: ${response.statusText}`);
    }
    return await response.blob();
}

function extractEmojisFromTitle(title: string | undefined): string[] | null {
    if (!title) return null;
    
    const emojiRegex = /[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu;
    const emojis = title.match(emojiRegex);
    
    if (emojis && emojis.length > 0) {
        return emojis.slice(0, 3);
    }
    
    const lowerTitle = title.toLowerCase();
    const emojiMap: Record<string, string[]> = {
        'cat': ['🐱', '😻', '🐈'], 'dog': ['🐶', '🐕', '🦮'],
        'love': ['❤️', '💕', '😍'], 'happy': ['😊', '😁', '😄'],
        'sad': ['😢', '😭', '🥺'], 'funny': ['😂', '🤣', '😆'],
        'angry': ['😠', '😡', '💢'], 'food': ['🍔', '🍕', '🍩'],
        'party': ['🎉', '🥳', '🎊'], 'animal': ['🐾', '🦁', '🐻'],
        'cry': ['😭', '😢', '💧'], 'dance': ['💃', '🕺', '🎵'],
        'smile': ['😊', '😃', '😄'], 'laugh': ['😂', '🤣', '😆'],
        'think': ['🤔', '💭', '🧐'], 'cool': ['😎', '🕶️', '😏'],
        'heart': ['❤️', '💖', '💕'], 'fire': ['🔥', '🌟', '✨'],
        'star': ['⭐', '🌟', '✨'], 'music': ['🎵', '🎶', '🎧'],
        'sleep': ['😴', '💤', '🌙'], 'eat': ['🍽️', '😋', '🍕'],
        'kiss': ['😘', '💋', '😗']
    };
    
    for (const [keyword, emojis] of Object.entries(emojiMap)) {
        if (lowerTitle.includes(keyword)) {
            return emojis;
        }
    }
    
    return null;
}

export async function importStickers(options: StickerImportOptions): Promise<Sticker[]> {
    const { source, contentType = 'stickers', query, count, packId, onProgress } = options;
    
    const sourceName = source === 'giphy' ? 'Giphy' : 'Klipy';
    
    onProgress?.({
        current: 0,
        total: count,
        message: `${sourceName}'dan "${query}" aranıyor...`
    });

    try {
        const gifs = source === 'giphy' 
            ? await fetchFromGiphy(query, count, contentType)
            : await fetchFromKlipyAdapter(query, count);

        if (gifs.length === 0) {
            throw new Error(`Sonuç bulunamadı`);
        }

        onProgress?.({
            current: 0,
            total: gifs.length,
            message: `${gifs.length} sticker bulundu, işleniyor...`
        });

        const importedStickers: Sticker[] = [];
        
        for (let i = 0; i < gifs.length; i++) {
            const gif = gifs[i];
            
            try {
                onProgress?.({
                    current: i + 1,
                    total: gifs.length,
                    message: `İşleniyor: ${gif.title}`,
                    currentSticker: gif.previewUrl
                });

                const gifBlob = await downloadGif(gif.url);
                const gifFile = new File([gifBlob], `${gif.id}.gif`, { type: 'image/gif' });

                const webpBlob = await stickerProcessor.processAnimated(gifFile, (progress) => {
                    onProgress?.({
                        current: i + 1,
                        total: gifs.length,
                        message: `${gif.title}: ${progress.message}`,
                        currentSticker: gif.previewUrl
                    });
                });

                if (webpBlob.size > 500 * 1024) {
                    console.warn(`[IMPORT] Sticker çok büyük, atlanıyor: ${gif.title} (${Math.round(webpBlob.size / 1024)}KB)`);
                    continue;
                }

                const fileName = `${source}_${gif.id}_${Date.now()}.webp`;
                const storagePath = `stickers/${packId}/${fileName}`;
                const storageRef = ref(storage, storagePath);
                
                await uploadBytes(storageRef, webpBlob);
                const downloadURL = await getDownloadURL(storageRef);

                importedStickers.push({
                    image_file: fileName,
                    url: downloadURL,
                    emojis: extractEmojisFromTitle(gif.title) || ['😀']
                });

                onProgress?.({
                    current: i + 1,
                    total: gifs.length,
                    message: `✅ ${gif.title} eklendi (${Math.round(webpBlob.size / 1024)}KB)`
                });

            } catch (error: any) {
                console.error(`[IMPORT] Hata (${gif.title}):`, error);
                onProgress?.({
                    current: i + 1,
                    total: gifs.length,
                    message: `⚠️ ${gif.title} atlandı: ${error.message}`
                });
            }
        }

        return importedStickers;

    } catch (error: any) {
        console.error('[IMPORT] Genel hata:', error);
        throw new Error(`Import hatası: ${error.message}`);
    }
}

export const QUICK_CATEGORIES = [
    { id: 'trending', name: 'Trend Olanlar', emoji: '🔥', sources: ['giphy', 'klipy'] },
    { id: 'cats', name: 'Kediler', emoji: '🐱', sources: ['giphy', 'klipy'] },
    { id: 'dogs', name: 'Köpekler', emoji: '🐶', sources: ['giphy', 'klipy'] },
    { id: 'love', name: 'Aşk', emoji: '❤️', sources: ['giphy', 'klipy'] },
    { id: 'happy', name: 'Mutluluk', emoji: '😊', sources: ['giphy', 'klipy'] },
    { id: 'sad', name: 'Üzgün', emoji: '😢', sources: ['giphy', 'klipy'] },
    { id: 'funny', name: 'Komik', emoji: '😂', sources: ['giphy', 'klipy'] },
    { id: 'cute', name: 'Sevimli', emoji: '🥰', sources: ['giphy', 'klipy'] },
    { id: 'reactions', name: 'Tepkiler', emoji: '😮', sources: ['giphy', 'klipy'] },
    { id: 'memes', name: 'Memes', emoji: '🎭', sources: ['giphy', 'klipy'] },
    { id: 'anime', name: 'Anime', emoji: '⛩️', sources: ['giphy', 'klipy'] },
    { id: 'gaming', name: 'Oyun', emoji: '🎮', sources: ['giphy', 'klipy'] },
    { id: 'food', name: 'Yemek', emoji: '🍔', sources: ['giphy', 'klipy'] },
    { id: 'animals', name: 'Hayvanlar', emoji: '🐾', sources: ['giphy', 'klipy'] },
    { id: 'party', name: 'Parti', emoji: '🎉', sources: ['giphy', 'klipy'] },
    { id: 'congratulations', name: 'Tebrikler', emoji: '👏', sources: ['giphy', 'klipy'] },
    { id: 'good morning', name: 'Günaydın', emoji: '☀️', sources: ['giphy', 'klipy'] },
    { id: 'good night', name: 'İyi Geceler', emoji: '🌙', sources: ['giphy', 'klipy'] },
    { id: 'dance', name: 'Dans', emoji: '💃', sources: ['giphy', 'klipy'] },
    { id: 'music', name: 'Müzik', emoji: '🎵', sources: ['giphy', 'klipy'] },
    { id: 'sports', name: 'Spor', emoji: '⚽', sources: ['giphy', 'klipy'] },
    { id: 'movies', name: 'Film', emoji: '🎬', sources: ['giphy', 'klipy'] },
    { id: 'birthday', name: 'Doğum Günü', emoji: '🎂', sources: ['giphy', 'klipy'] },
    { id: 'christmas', name: 'Noel', emoji: '🎄', sources: ['giphy', 'klipy'] },
    { id: 'halloween', name: 'Cadılar Bayramı', emoji: '🎃', sources: ['giphy', 'klipy'] },
    { id: 'thanksgiving', name: 'Şükran Günü', emoji: '🦃', sources: ['giphy', 'klipy'] },
    { id: 'new year', name: 'Yeni Yıl', emoji: '🎆', sources: ['giphy', 'klipy'] },
    { id: 'valentines', name: 'Sevgililer Günü', emoji: '💝', sources: ['giphy', 'klipy'] },
    { id: 'crying', name: 'Ağlayan', emoji: '😭', sources: ['giphy', 'klipy'] },
    { id: 'thinking', name: 'Düşünen', emoji: '🤔', sources: ['giphy', 'klipy'] },
    { id: 'cool', name: 'Havalı', emoji: '😎', sources: ['giphy', 'klipy'] },
    { id: 'fire', name: 'Ateşli', emoji: '🔥', sources: ['giphy', 'klipy'] },
    { id: 'star', name: 'Yıldız', emoji: '⭐', sources: ['giphy', 'klipy'] },
    { id: 'heart', name: 'Kalp', emoji: '💖', sources: ['giphy', 'klipy'] },
];
