// Batch Pack Generator V2 - Güçlendirilmiş toplu otomatik paket üretim motoru
// DeepSeek + Giphy + Klipy entegrasyonu ile tek seferde onlarca paket üretir
// V2: Multi-page fetching, Klipy re-enabled, query variations, deduplication

import { storage, db } from '../firebase';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { doc, setDoc, serverTimestamp } from 'firebase/firestore';
import { stickerProcessor } from './stickerProcessor';
import { deepseekService, autoDetectCategory } from './deepseekService';
import type { Sticker } from '../types';

// ========== TYPES ==========

export type BatchSource = 'giphy' | 'klipy' | 'both';

export interface BatchPackConfig {
    searchTerms: string[];
    source: BatchSource;
    contentType?: 'gifs' | 'stickers';
    stickersPerPack: number;
    useAiNaming: boolean;
    useAiTranslation: boolean;
    existingPackNames?: string[];
    onProgress?: (progress: BatchProgress) => void;
}

export interface BatchProgress {
    currentPack: number;
    totalPacks: number;
    currentStep: string;
    packName?: string;
    stickerProgress?: { current: number; total: number };
    status: 'running' | 'done' | 'error';
    error?: string;
    completedPacks: CompletedPack[];
}

export interface CompletedPack {
    id: string;
    name: string;
    stickerCount: number;
    searchTerm: string;
    source: string;
}

// ========== STICKER FETCHING (V2 - Multi-source, paginated) ==========

interface RawGif {
    id: string;
    title: string;
    url: string;
    previewUrl: string;
    source: string;
    width: number;
    height: number;
    size: number;
    rating: string;
    apiRank: number;
    queryMatch: 'exact' | 'variation';
    qualityScore?: number;
}

const GIPHY_PROXY = 'https://us-central1-sticky-dcd20.cloudfunctions.net/giphyProxy';
const KLIPY_PROXY = 'https://us-central1-sticky-dcd20.cloudfunctions.net/klipyProxy';
const PAGE_SIZE = 50; // Max per request

async function fetchGiphyPage(query: string, limit: number, offset: number, contentType: 'gifs' | 'stickers', queryMatch: 'exact' | 'variation' = 'exact'): Promise<RawGif[]> {
    try {
        const endpoint = query === 'trending' ? 'trending' : 'search';
        const proxyUrl = `${GIPHY_PROXY}?endpoint=${endpoint}&type=${contentType}&limit=${limit}&offset=${offset}${query !== 'trending' ? `&query=${encodeURIComponent(query)}` : ''}`;

        const response = await fetch(proxyUrl);
        if (!response.ok) throw new Error(`Giphy proxy error: ${response.status}`);

        const data = await response.json();
        if (!data.data || data.data.length === 0) return [];

        return data.data.map((gif: any, idx: number) => {
            const original = gif.images?.original;
            const w = Number(original?.width || gif.images?.fixed_height?.width || 0);
            const h = Number(original?.height || gif.images?.fixed_height?.height || 0);
            const s = Number(original?.size || gif.images?.downsized_medium?.size || 0);
            return {
                id: String(gif.id),
                title: gif.title || 'Sticker',
                url: original?.url || gif.images?.fixed_height?.url || gif.images?.downsized_medium?.url || '',
                previewUrl: gif.images?.downsized_medium?.url || gif.images?.fixed_height_small?.url || '',
                source: 'giphy',
                width: w,
                height: h,
                size: s,
                rating: gif.rating || 'g',
                apiRank: offset + idx,
                queryMatch
            };
        }).filter((g: RawGif) => g.url);
    } catch (error: any) {
        console.error(`[BATCH] Giphy page fetch error (${query}, offset=${offset}):`, error);
        return [];
    }
}

async function fetchKlipyPage(query: string, limit: number, contentType: 'gifs' | 'stickers' = 'stickers', queryMatch: 'exact' | 'variation' = 'exact'): Promise<RawGif[]> {
    try {
        const endpoint = query === 'trending' ? 'trending' : 'search';
        const proxyUrl = `${KLIPY_PROXY}?endpoint=${endpoint}&type=${contentType}&limit=${limit}${query !== 'trending' ? `&query=${encodeURIComponent(query)}` : ''}`;

        const response = await fetch(proxyUrl);
        if (!response.ok) throw new Error(`Klipy proxy error: ${response.status}`);

        const data = await response.json();
        if (!data.data || data.data.length === 0) return [];

        return data.data.map((item: any, idx: number) => {
            const original = item.images?.original;
            const w = Number(original?.width || item.images?.fixed_height?.width || 0);
            const h = Number(original?.height || item.images?.fixed_height?.height || 0);
            const s = Number(original?.size || 0);
            const url = original?.url || item.images?.fixed_height?.url || item.images?.downsized?.url
                || item.media_formats?.gif?.url || item.media_formats?.mediumgif?.url
                || item.url || item.gif_url || item.sticker_url || '';
            const preview = item.images?.preview_gif?.url || item.images?.fixed_height_small?.url || item.images?.downsized?.url
                || item.media_formats?.nanogif?.url || item.media_formats?.tinygif?.url
                || item.preview_url || url;
            return {
                id: item.id || String(Date.now() + Math.random()),
                title: item.title || item.content_description || 'Sticker',
                url,
                previewUrl: preview,
                source: 'klipy',
                width: w,
                height: h,
                size: s,
                rating: item.rating || 'g',
                apiRank: idx,
                queryMatch
            };
        }).filter((g: RawGif) => g.url);
    } catch (error: any) {
        console.error(`[BATCH] Klipy fetch error (${query}):`, error);
        return [];
    }
}

function generateQueryVariations(baseQuery: string): { query: string; match: 'exact' | 'variation' }[] {
    const variations: { query: string; match: 'exact' | 'variation' }[] = [
        { query: baseQuery, match: 'exact' }
    ];

    // Only add "sticker" suffix — this is the most relevant variation
    if (!baseQuery.toLowerCase().includes('sticker')) {
        variations.push({ query: `${baseQuery} sticker`, match: 'exact' });
    }

    // Add "gif" suffix for GIF content
    if (!baseQuery.toLowerCase().includes('gif')) {
        variations.push({ query: `${baseQuery} gif`, match: 'exact' });
    }

    // NO random adjectives, NO "reaction", NO "emoji" — these dilute relevance
    // NO individual word splits — they return completely unrelated results

    return variations;
}

// ========== QUALITY SCORING ==========

function calculateQualityScore(gif: RawGif, searchTerm: string): number {
    let score = 0;

    // 1. API Rank bonus (top results from API are most relevant) — max 40 points
    // First 10 results get highest bonus, drops off quickly
    if (gif.apiRank < 5) score += 40;
    else if (gif.apiRank < 10) score += 35;
    else if (gif.apiRank < 20) score += 25;
    else if (gif.apiRank < 30) score += 15;
    else score += 5;

    // 2. Query match type — exact queries are more relevant (+15)
    if (gif.queryMatch === 'exact') score += 15;

    // 3. Dimension quality — prefer reasonable sizes, not too small (+20 max)
    const w = gif.width;
    const h = gif.height;
    if (w >= 200 && h >= 200) score += 20;
    else if (w >= 100 && h >= 100) score += 10;
    else if (w > 0 && h > 0) score += 2;
    // If no dimension data, give neutral score
    else score += 8;

    // 4. Aspect ratio — prefer roughly square or reasonable ratios (+10 max)
    if (w > 0 && h > 0) {
        const ratio = Math.max(w, h) / Math.min(w, h);
        if (ratio <= 1.5) score += 10;       // Nearly square — ideal for stickers
        else if (ratio <= 2.0) score += 6;   // Acceptable
        else if (ratio <= 3.0) score += 2;   // Wide/tall — less ideal
        // ratio > 3 = very stretched, no bonus
    } else {
        score += 5; // neutral
    }

    // 5. Title relevance — does the title contain the search term? (+15 max)
    const titleLower = (gif.title || '').toLowerCase();
    const termLower = searchTerm.toLowerCase();
    const termWords = termLower.split(/\s+/);
    if (titleLower.includes(termLower)) {
        score += 15; // Full match
    } else {
        // Partial word match
        const matchedWords = termWords.filter(w => w.length > 2 && titleLower.includes(w));
        score += Math.min(10, matchedWords.length * 5);
    }

    // 6. Penalize very generic/empty titles
    if (!gif.title || gif.title === 'Sticker' || gif.title.trim().length < 3) {
        score -= 5;
    }

    // 7. File size penalty — very large files often fail WebP conversion
    if (gif.size > 0) {
        if (gif.size > 8 * 1024 * 1024) score -= 10;      // >8MB — likely to fail
        else if (gif.size > 5 * 1024 * 1024) score -= 5;  // >5MB — risky
    }

    return Math.max(0, score);
}

async function fetchStickersAggregated(
    query: string,
    targetCount: number,
    source: BatchSource,
    contentType: 'gifs' | 'stickers',
    onStatus?: (msg: string) => void
): Promise<RawGif[]> {
    const seenIds = new Set<string>();
    const allResults: RawGif[] = [];
    const neededTotal = targetCount * 5; // Fetch 5x to ensure enough stickers survive sizing limits

    const queries = generateQueryVariations(query);
    onStatus?.(`Searching with ${queries.length} query variations...`);

    for (const { query: q, match } of queries) {
        if (allResults.length >= neededTotal) break;

        // === GIPHY (paginated — only 2 pages for exact, 1 for variation) ===
        if (source === 'giphy' || source === 'both') {
            let offset = 0;
            const maxPages = match === 'exact' ? 2 : 1;
            for (let page = 0; page < maxPages; page++) {
                if (allResults.length >= neededTotal) break;
                const remaining = neededTotal - allResults.length;
                const fetchCount = Math.min(PAGE_SIZE, remaining);

                onStatus?.(`Giphy: "${q}" (page ${page + 1})...`);
                const results = await fetchGiphyPage(q, fetchCount, offset, contentType, match);

                if (results.length === 0) break;

                for (const r of results) {
                    if (!seenIds.has(r.id)) {
                        seenIds.add(r.id);
                        allResults.push(r);
                    }
                }

                offset += results.length;
                if (results.length < fetchCount) break;
            }
        }

        // === KLIPY ===
        if (source === 'klipy' || source === 'both') {
            if (allResults.length < neededTotal) {
                onStatus?.(`Klipy: "${q}"...`);
                const klipyResults = await fetchKlipyPage(q, Math.min(50, neededTotal - allResults.length), contentType, match);
                for (const r of klipyResults) {
                    const dedupKey = `klipy_${r.id}`;
                    if (!seenIds.has(dedupKey)) {
                        seenIds.add(dedupKey);
                        allResults.push(r);
                    }
                }
            }
        }
    }

    // === QUALITY SCORING & SORTING ===
    onStatus?.(`Scoring ${allResults.length} results for quality...`);
    for (const gif of allResults) {
        gif.qualityScore = calculateQualityScore(gif, query);
    }

    // Sort by quality score (highest first)
    allResults.sort((a, b) => (b.qualityScore || 0) - (a.qualityScore || 0));

    // Log top and bottom scores for debugging
    if (allResults.length > 0) {
        const top5 = allResults.slice(0, 5).map(g => `${g.title.substring(0, 30)}(${g.qualityScore})`);
        const bot5 = allResults.slice(-5).map(g => `${g.title.substring(0, 30)}(${g.qualityScore})`);
        console.log(`[BATCH] Quality scores for "${query}": TOP=${top5.join(', ')} | BOTTOM=${bot5.join(', ')}`);
    }

    // Filter out extremely low quality (below 5 points) so we don't drop viable 
    // stickers when the user requested a large amount and APIs return limited results.
    const minScore = 5;
    const filtered = allResults.filter(g => (g.qualityScore || 0) >= minScore);
    console.log(`[BATCH] Aggregated ${allResults.length} → filtered to ${filtered.length} quality results for "${query}" (target: ${targetCount})`);

    return filtered;
}

// ========== GIF DOWNLOAD & PROCESS ==========

async function downloadGif(url: string): Promise<Blob> {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`Download failed: ${response.statusText}`);
    return await response.blob();
}

async function processAndUploadSticker(
    gif: RawGif,
    packId: string,
    index: number,
    onProgress?: (msg: string) => void
): Promise<Sticker | null> {
    try {
        onProgress?.(`İndiriliyor: ${gif.title}`);

        const gifBlob = await downloadGif(gif.url);
        const gifFile = new File([gifBlob], `${gif.id}.gif`, { type: 'image/gif' });

        onProgress?.(`GIF → WebP dönüştürülüyor: ${gif.title}`);

        // GIF → WebP dönüşümü (arka plan silme YOK - sadece dönüşüm)
        const webpBlob = await stickerProcessor.processAnimated(gifFile, (p) => {
            onProgress?.(`${gif.title}: ${p.message}`);
        });

        // WhatsApp 500KB kontrolü
        if (webpBlob.size > 500 * 1024) {
            console.warn(`[BATCH] Sticker çok büyük, atlanıyor: ${gif.title} (${Math.round(webpBlob.size / 1024)}KB)`);
            return null;
        }

        onProgress?.(`Yükleniyor: ${gif.title}`);

        const fileName = `batch_${gif.id}_${Date.now()}_${index}.webp`;
        const storagePath = `stickers/${packId}/${fileName}`;
        const storageRef = ref(storage, storagePath);

        await uploadBytes(storageRef, webpBlob);
        const downloadURL = await getDownloadURL(storageRef);

        return {
            image_file: fileName,
            url: downloadURL,
            emojis: ['😀']
        };
    } catch (error: any) {
        console.error(`[BATCH] Sticker işleme hatası (${gif.title}):`, error);
        return null;
    }
}

// ========== TRAY IMAGE ==========

async function createTrayImage(stickers: Sticker[], packId: string): Promise<{ trayUrl: string; trayFile: string }> {
    try {
        if (stickers.length === 0) return { trayUrl: '', trayFile: '' };

        // Rastgele bir sticker seç
        const randomIdx = Math.floor(Math.random() * stickers.length);
        const chosenSticker = stickers[randomIdx];

        // Sticker'ı indir
        const response = await fetch(chosenSticker.url);
        const blob = await response.blob();
        const tempFile = new File([blob], 'auto_tray.webp', { type: 'image/webp' });

        // Tray olarak işle
        const trayBlob = await stickerProcessor.processTray(tempFile, () => { });

        const trayFileName = `tray_${Date.now()}.png`;
        const trayStorageRef = ref(storage, `stickers/${packId}/${trayFileName}`);

        await uploadBytes(trayStorageRef, trayBlob);
        const trayUrl = await getDownloadURL(trayStorageRef);

        return { trayUrl, trayFile: trayFileName };
    } catch (error) {
        console.error('[BATCH] Tray oluşturma hatası:', error);
        return { trayUrl: '', trayFile: '' };
    }
}

// ========== PACK NAME GENERATION ==========

async function generatePackName(searchTerm: string, useAi: boolean): Promise<{ name: string; emoji: string }> {
    if (useAi && deepseekService.isConfigured()) {
        try {
            const names = await deepseekService.generatePackNames(searchTerm, 3);
            if (names.length > 0) {
                return { name: `${names[0].name} ${names[0].emoji}`, emoji: names[0].emoji };
            }
        } catch (error) {
            console.error('[BATCH] AI isim üretim hatası:', error);
        }
    }

    // Fallback: basit isimlendirme
    const capitalized = searchTerm.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
    return { name: capitalized, emoji: '✨' };
}

// ========== TRANSLATION ==========

async function translatePack(name: string, useAi: boolean): Promise<Record<string, string>> {
    if (useAi && deepseekService.isConfigured()) {
        try {
            return await deepseekService.translatePackName(name);
        } catch (error) {
            console.error('[BATCH] AI çeviri hatası:', error);
        }
    }

    // Fallback: Google Translate (mevcut translator.ts)
    try {
        const { translateTextAllLanguages } = await import('./translator');
        return await translateTextAllLanguages(name);
    } catch (error) {
        console.error('[BATCH] Fallback çeviri hatası:', error);
        return { name_en: name };
    }
}

// ========== MAIN BATCH GENERATOR ==========

export async function generateBatchPacks(config: BatchPackConfig): Promise<CompletedPack[]> {
    const { searchTerms, source, contentType = 'stickers', stickersPerPack, useAiNaming, useAiTranslation, existingPackNames = [], onProgress } = config;
    const completedPacks: CompletedPack[] = [];

    for (let i = 0; i < searchTerms.length; i++) {
        const searchTerm = searchTerms[i].trim();
        if (!searchTerm) continue;

        try {
            // === STEP 1: İsim üret ===
            onProgress?.({
                currentPack: i + 1,
                totalPacks: searchTerms.length,
                currentStep: `"${searchTerm}" için isim üretiliyor...`,
                status: 'running',
                completedPacks
            });

            let { name: packName } = await generatePackName(searchTerm, useAiNaming);

            // Duplicate kontrolü - aynı isimli paket varsa atla
            if (existingPackNames.some(existing => existing === packName.toLowerCase())) {
                console.warn(`[BATCH] "${packName}" zaten mevcut, atlanıyor.`);
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: searchTerms.length,
                    currentStep: `⚠️ "${packName}" zaten mevcut, atlanıyor...`,
                    packName,
                    status: 'running',
                    completedPacks
                });
                continue;
            }

            // === STEP 2: Sticker'ları çek (V2 - multi-source, paginated, query variations) ===
            onProgress?.({
                currentPack: i + 1,
                totalPacks: searchTerms.length,
                currentStep: `"${searchTerm}" için GIF'ler aranıyor (multi-source)...`,
                packName,
                status: 'running',
                completedPacks
            });

            // V2: Fetch 3x more than needed via multiple queries, pages, and sources
            let rawGifs = await fetchStickersAggregated(searchTerm, stickersPerPack, source, contentType, (msg) => {
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: searchTerms.length,
                    currentStep: msg,
                    packName,
                    status: 'running',
                    completedPacks
                });
            });

            if (rawGifs.length === 0) {
                console.warn(`[BATCH] "${searchTerm}" için sticker bulunamadı, atlanıyor.`);
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: searchTerms.length,
                    currentStep: `⚠️ "${searchTerm}" için sticker bulunamadı, atlanıyor...`,
                    packName,
                    status: 'running',
                    completedPacks
                });
                continue;
            }

            // === STEP 3: Pack ID oluştur ===
            const packId = searchTerm.toLowerCase()
                .replace(/[^a-z0-9\s]/g, '')
                .replace(/\s+/g, '_')
                .substring(0, 40) + '_' + Date.now().toString(36);

            // === STEP 4: Sticker'ları işle ve yükle (garantili sayı) ===
            const processedStickers: Sticker[] = [];
            let processedCount = 0;

            for (let j = 0; j < rawGifs.length && processedStickers.length < stickersPerPack; j++) {
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: searchTerms.length,
                    currentStep: `GIF işleniyor: ${processedStickers.length + 1}/${stickersPerPack} (${j + 1}/${rawGifs.length} denendi)`,
                    packName,
                    stickerProgress: { current: processedStickers.length, total: stickersPerPack },
                    status: 'running',
                    completedPacks
                });

                const sticker = await processAndUploadSticker(rawGifs[j], packId, j, (msg) => {
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: searchTerms.length,
                        currentStep: msg,
                        packName,
                        stickerProgress: { current: processedStickers.length, total: stickersPerPack },
                        status: 'running',
                        completedPacks
                    });
                });

                if (sticker) {
                    processedStickers.push(sticker);
                }

                processedCount++;

                // WhatsApp max 30 sticker limiti
                if (processedStickers.length >= 30) break;
            }

            // Eğer yeterli sticker işlenemediyse ve hala GIF varsa devam et
            if (processedStickers.length < Math.min(stickersPerPack, 5) && processedStickers.length > 0) {
                console.warn(`[BATCH] "${searchTerm}" için sadece ${processedStickers.length} sticker işlenebildi (hedef: ${stickersPerPack})`);
            }

            if (processedStickers.length === 0) {
                console.warn(`[BATCH] "${searchTerm}" için hiçbir sticker işlenemedi.`);
                continue;
            }

            // === STEP 5: Tray image oluştur ===
            onProgress?.({
                currentPack: i + 1,
                totalPacks: searchTerms.length,
                currentStep: 'Kapak resmi oluşturuluyor...',
                packName,
                status: 'running',
                completedPacks
            });

            const { trayUrl, trayFile } = await createTrayImage(processedStickers, packId);

            // === STEP 6: Çeviri ===
            onProgress?.({
                currentPack: i + 1,
                totalPacks: searchTerms.length,
                currentStep: '33 dile çevriliyor...',
                packName,
                status: 'running',
                completedPacks
            });

            const translations = await translatePack(packName, useAiTranslation);

            // === STEP 7: Kategori ata ===
            const category = autoDetectCategory(searchTerm);

            // === STEP 8: Firestore'a kaydet ===
            onProgress?.({
                currentPack: i + 1,
                totalPacks: searchTerms.length,
                currentStep: 'Veritabanına kaydediliyor...',
                packName,
                status: 'running',
                completedPacks
            });

            const packData: any = {
                name: packName,
                ...translations,
                publisher: 'Sticky',
                publisher_email: 'contact@arain.digital',
                privacy_policy_website: '',
                license_agreement_website: '',
                category,
                is_premium: false,
                is_animated: true,
                download_count: 0,
                fake_download_base: Math.floor(Math.random() * 7001) + 3000,
                view_count: 0,
                favorite_count: 0,
                sticker_count: processedStickers.length,
                image_data_version: Date.now().toString(),
                is_active: true,
                stickers: processedStickers,
                tray_url: trayUrl,
                tray_image_file: trayFile,
                created_at: serverTimestamp(),
                batch_generated: true,
                batch_search_term: searchTerm,
                batch_source: source
            };

            await setDoc(doc(db, 'draft_stickers', packId), packData);

            const completed: CompletedPack = {
                id: packId,
                name: packName,
                stickerCount: processedStickers.length,
                searchTerm,
                source
            };

            completedPacks.push(completed);

            onProgress?.({
                currentPack: i + 1,
                totalPacks: searchTerms.length,
                currentStep: `✅ "${packName}" oluşturuldu! (${processedStickers.length} sticker)`,
                packName,
                status: 'running',
                completedPacks
            });

            console.log(`[BATCH] ✅ Paket oluşturuldu: ${packName} (${processedStickers.length} sticker)`);

        } catch (error: any) {
            console.error(`[BATCH] Paket oluşturma hatası (${searchTerm}):`, error);
            console.error('[BATCH] Hata detayı:', {
                message: error.message,
                stack: error.stack,
                searchTerm,
                source
            });
            onProgress?.({
                currentPack: i + 1,
                totalPacks: searchTerms.length,
                currentStep: `❌ Hata: ${error.message}`,
                status: 'error',
                error: error.message,
                completedPacks
            });
            // Hata olsa bile devam et
        }
    }

    // Tamamlandı
    onProgress?.({
        currentPack: searchTerms.length,
        totalPacks: searchTerms.length,
        currentStep: `🎉 Tamamlandı! ${completedPacks.length}/${searchTerms.length} paket oluşturuldu.`,
        status: 'done',
        completedPacks
    });

    return completedPacks;
}

export const batchGenerator = {
    generate: generateBatchPacks,
    isReady: () => deepseekService.isConfigured()
};
