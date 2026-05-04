// Batch Pack Generator V3 - Robust batch sticker pack generation
// Guarantees exact pack count & sticker count, quality filtering, category balancing

import { pb, WORKER_URL, uploadFile } from '../pocketbase';
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

// ========== STICKER FETCHING ==========

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

const GIPHY_PROXY = `${WORKER_URL.replace(/\/$/, '')}/api/giphy`;
const KLIPY_PROXY = `${WORKER_URL.replace(/\/$/, '')}/api/klipy`;
const PAGE_SIZE = 50;

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

    if (!baseQuery.toLowerCase().includes('sticker')) {
        variations.push({ query: `${baseQuery} sticker`, match: 'exact' });
    }

    if (!baseQuery.toLowerCase().includes('gif')) {
        variations.push({ query: `${baseQuery} gif`, match: 'exact' });
    }

    // Add "animated" variation for better results
    if (!baseQuery.toLowerCase().includes('animated')) {
        variations.push({ query: `${baseQuery} animated`, match: 'variation' });
    }

    return variations;
}

// ========== QUALITY SCORING (Enhanced V3) ==========

function calculateQualityScore(gif: RawGif, searchTerm: string): number {
    let score = 0;

    // 1. API Rank bonus — top results are most relevant (max 40)
    if (gif.apiRank < 5) score += 40;
    else if (gif.apiRank < 10) score += 35;
    else if (gif.apiRank < 15) score += 30;
    else if (gif.apiRank < 25) score += 20;
    else if (gif.apiRank < 40) score += 10;
    else score += 3;

    // 2. Query match type (max 15)
    if (gif.queryMatch === 'exact') score += 15;

    // 3. Dimension quality — prefer 200+ pixels (max 25)
    const w = gif.width;
    const h = gif.height;
    if (w >= 300 && h >= 300) score += 25;
    else if (w >= 200 && h >= 200) score += 20;
    else if (w >= 100 && h >= 100) score += 10;
    else if (w > 0 && h > 0) score += 2;
    else score += 8;

    // 4. Aspect ratio — prefer square-ish (max 12)
    if (w > 0 && h > 0) {
        const ratio = Math.max(w, h) / Math.min(w, h);
        if (ratio <= 1.3) score += 12;
        else if (ratio <= 1.5) score += 10;
        else if (ratio <= 2.0) score += 5;
        else if (ratio <= 3.0) score += 1;
    } else {
        score += 5;
    }

    // 5. Title relevance (max 20)
    const titleLower = (gif.title || '').toLowerCase();
    const termLower = searchTerm.toLowerCase();
    const termWords = termLower.split(/\s+/);
    if (titleLower.includes(termLower)) {
        score += 20;
    } else {
        const matchedWords = termWords.filter(w => w.length > 2 && titleLower.includes(w));
        score += Math.min(15, matchedWords.length * 5);
    }

    // 6. Penalize generic/empty titles
    if (!gif.title || gif.title === 'Sticker' || gif.title.trim().length < 3) {
        score -= 8;
    }

    // 7. File size — penalize very large (likely to fail WebP conversion)
    if (gif.size > 0) {
        if (gif.size > 8 * 1024 * 1024) score -= 15;
        else if (gif.size > 5 * 1024 * 1024) score -= 8;
        else if (gif.size > 3 * 1024 * 1024) score -= 3;
        // Ideal size (100KB-2MB) gets bonus
        else if (gif.size >= 100 * 1024 && gif.size <= 2 * 1024 * 1024) score += 5;
    }

    // 8. Source bonus — Giphy stickers tend to be higher quality
    if (gif.source === 'giphy') score += 3;

    // 9. Rating bonus — 'g' rated content is generally cleaner
    if (gif.rating === 'g') score += 3;

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
    // Fetch 8x to ensure enough survive quality filtering + WebP conversion
    const neededTotal = targetCount * 8;

    const queries = generateQueryVariations(query);
    onStatus?.(`Searching with ${queries.length} query variations...`);

    for (const { query: q, match } of queries) {
        if (allResults.length >= neededTotal) break;

        // === GIPHY (paginated — up to 3 pages for exact, 2 for variation) ===
        if (source === 'giphy' || source === 'both') {
            let offset = 0;
            const maxPages = match === 'exact' ? 3 : 2;
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

    // Also try "stickers" content type if we used "gifs" and don't have enough
    if (contentType === 'gifs' && allResults.length < targetCount * 3) {
        onStatus?.(`Fetching additional sticker-type results...`);
        if (source === 'giphy' || source === 'both') {
            const extraResults = await fetchGiphyPage(query, PAGE_SIZE, 0, 'stickers', 'exact');
            for (const r of extraResults) {
                if (!seenIds.has(r.id)) {
                    seenIds.add(r.id);
                    allResults.push(r);
                }
            }
        }
    }

    // === QUALITY SCORING & SORTING ===
    onStatus?.(`Scoring ${allResults.length} results for quality...`);
    for (const gif of allResults) {
        gif.qualityScore = calculateQualityScore(gif, query);
    }

    allResults.sort((a, b) => (b.qualityScore || 0) - (a.qualityScore || 0));

    if (allResults.length > 0) {
        const top5 = allResults.slice(0, 5).map(g => `${g.title.substring(0, 30)}(${g.qualityScore})`);
        console.log(`[BATCH] Quality scores for "${query}": TOP=${top5.join(', ')}`);
    }

    // Filter out low quality (below 10 points)
    const minScore = 10;
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
        onProgress?.(`Downloading: ${gif.title}`);

        const gifBlob = await downloadGif(gif.url);
        const gifFile = new File([gifBlob], `${gif.id}.gif`, { type: 'image/gif' });

        onProgress?.(`Converting GIF → WebP: ${gif.title}`);

        const webpBlob = await stickerProcessor.processAnimated(gifFile, (p) => {
            onProgress?.(`${gif.title}: ${p.message}`);
        });

        // WhatsApp 500KB limit
        if (webpBlob.size > 500 * 1024) {
            console.warn(`[BATCH] Sticker too large, skipping: ${gif.title} (${Math.round(webpBlob.size / 1024)}KB)`);
            return null;
        }

        onProgress?.(`Uploading: ${gif.title}`);

        const fileName = `batch_${gif.id}_${Date.now()}_${index}.webp`;
        const downloadURL = await uploadFile('draft_stickers', packId, 'images', webpBlob, fileName);

        return {
            image_file: fileName,
            url: downloadURL,
            emojis: ['😀']
        };
    } catch (error: any) {
        console.error(`[BATCH] Sticker processing error (${gif.title}):`, error);
        return null;
    }
}

// ========== TRAY IMAGE ==========

async function createTrayImage(stickers: Sticker[], packId: string): Promise<{ trayUrl: string; trayFile: string }> {
    try {
        if (stickers.length === 0) return { trayUrl: '', trayFile: '' };

        // Pick the first sticker (highest quality since we sorted)
        const chosenSticker = stickers[0];

        const response = await fetch(chosenSticker.url);
        const blob = await response.blob();
        const tempFile = new File([blob], 'auto_tray.webp', { type: 'image/webp' });

        const trayBlob = await stickerProcessor.processTray(tempFile, () => { });

        const trayFileName = `tray_${Date.now()}.png`;
        const trayUrl = await uploadFile('draft_stickers', packId, 'tray_image', trayBlob, trayFileName);

        return { trayUrl, trayFile: trayFileName };
    } catch (error) {
        console.error('[BATCH] Tray creation error:', error);
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
            console.error('[BATCH] AI naming error:', error);
        }
    }

    const capitalized = searchTerm.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
    return { name: capitalized, emoji: '✨' };
}

// ========== TRANSLATION ==========

async function translatePack(name: string, useAi: boolean): Promise<Record<string, string>> {
    if (useAi && deepseekService.isConfigured()) {
        try {
            return await deepseekService.translatePackName(name);
        } catch (error) {
            console.error('[BATCH] AI translation error:', error);
        }
    }

    try {
        const { translateTextAllLanguages } = await import('./translator');
        return await translateTextAllLanguages(name);
    } catch (error) {
        console.error('[BATCH] Fallback translation error:', error);
        return { name_en: name };
    }
}

// ========== CATEGORY STATS ==========

export async function getCategoryStats(): Promise<Record<string, number>> {
    const stats: Record<string, number> = {};
    try {
        const [stickers, drafts] = await Promise.all([
            pb.collection('stickers').getFullList({ fields: 'category' }).catch(() => []),
            pb.collection('draft_stickers').getFullList({ fields: 'category' }).catch(() => []),
        ]);
        [...stickers, ...drafts].forEach((r: any) => {
            const cat = r.category || 'other';
            stats[cat] = (stats[cat] || 0) + 1;
        });
    } catch (error) {
        console.error('[BATCH] Failed to fetch category stats:', error);
    }
    return stats;
}

// ========== MAIN BATCH GENERATOR (V3 — guaranteed count) ==========

export async function generateBatchPacks(config: BatchPackConfig): Promise<CompletedPack[]> {
    const { searchTerms, source, contentType = 'stickers', stickersPerPack, useAiNaming, useAiTranslation, existingPackNames = [], onProgress } = config;
    const completedPacks: CompletedPack[] = [];
    const totalTarget = searchTerms.length;

    for (let i = 0; i < searchTerms.length; i++) {
        const searchTerm = searchTerms[i].trim();
        if (!searchTerm) continue;

        const MAX_RETRIES = 2;
        let retryCount = 0;
        let packCreated = false;

        while (!packCreated && retryCount <= MAX_RETRIES) {
            let draftRecordId = '';
            try {
                const retryLabel = retryCount > 0 ? ` (retry ${retryCount})` : '';

                // === STEP 1: Generate name ===
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: totalTarget,
                    currentStep: `Generating name for "${searchTerm}"${retryLabel}...`,
                    status: 'running',
                    completedPacks
                });

                let { name: packName } = await generatePackName(searchTerm, useAiNaming);

                // Duplicate check
                if (existingPackNames.some(existing => existing === packName.toLowerCase())) {
                    packName = packName + ' ' + (Date.now() % 1000);
                }

                // === STEP 2: Fetch stickers (aggressive fetching) ===
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: totalTarget,
                    currentStep: `Searching stickers for "${searchTerm}"${retryLabel}...`,
                    packName,
                    status: 'running',
                    completedPacks
                });

                let rawGifs = await fetchStickersAggregated(searchTerm, stickersPerPack, source, contentType, (msg) => {
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: totalTarget,
                        currentStep: msg,
                        packName,
                        status: 'running',
                        completedPacks
                    });
                });

                if (rawGifs.length === 0) {
                    console.warn(`[BATCH] No stickers found for "${searchTerm}", skipping.`);
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: totalTarget,
                        currentStep: `⚠️ No stickers found for "${searchTerm}", skipping...`,
                        packName,
                        status: 'running',
                        completedPacks
                    });
                    break; // Don't retry if no results at all
                }

                // === STEP 3: Create draft record before uploading files ===
                const category = autoDetectCategory(searchTerm);
                const draftRecord = await pb.collection('draft_stickers').create({
                    name: packName,
                    name_en: packName,
                    publisher: 'Sticky',
                    publisher_email: 'contact@arain.digital',
                    category,
                    is_premium: false,
                    is_animated: true,
                    is_active: false,
                    status: 'processing',
                    sticker_count: 0,
                    stickers: [],
                    tray_url: '',
                    tray_image_file: '',
                    image_data_version: Date.now().toString(),
                    batch_generated: true,
                    batch_search_term: searchTerm,
                    batch_source: source,
                    created_at: new Date().toISOString(),
                });
                draftRecordId = draftRecord.id;

                // === STEP 4: Process stickers (guaranteed count) ===
                const processedStickers: Sticker[] = [];
                const effectiveTarget = Math.min(stickersPerPack, 30); // WhatsApp max 30

                for (let j = 0; j < rawGifs.length && processedStickers.length < effectiveTarget; j++) {
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: totalTarget,
                        currentStep: `Processing sticker ${processedStickers.length + 1}/${effectiveTarget} (trying ${j + 1}/${rawGifs.length})`,
                        packName,
                        stickerProgress: { current: processedStickers.length, total: effectiveTarget },
                        status: 'running',
                        completedPacks
                    });

                    const sticker = await processAndUploadSticker(rawGifs[j], draftRecordId, j, (msg) => {
                        onProgress?.({
                            currentPack: i + 1,
                            totalPacks: totalTarget,
                            currentStep: msg,
                            packName,
                            stickerProgress: { current: processedStickers.length, total: effectiveTarget },
                            status: 'running',
                            completedPacks
                        });
                    });

                    if (sticker) {
                        processedStickers.push(sticker);
                    }
                }

                // If we didn't reach the target, log it but still create the pack if we have at least 3
                if (processedStickers.length < effectiveTarget) {
                    console.warn(`[BATCH] "${searchTerm}": only ${processedStickers.length}/${effectiveTarget} stickers processed`);
                }

                if (processedStickers.length < 3) {
                    console.warn(`[BATCH] "${searchTerm}": too few stickers (${processedStickers.length}), retrying...`);
                    await pb.collection('draft_stickers').delete(draftRecordId).catch(() => {});
                    draftRecordId = '';
                    retryCount++;
                    continue;
                }

                // === STEP 5: Tray image ===
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: totalTarget,
                    currentStep: 'Creating cover image...',
                    packName,
                    status: 'running',
                    completedPacks
                });

                const { trayUrl, trayFile } = await createTrayImage(processedStickers, draftRecordId);

                // === STEP 6: Translation ===
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: totalTarget,
                    currentStep: 'Translating to 33 languages...',
                    packName,
                    status: 'running',
                    completedPacks
                });

                const translations = await translatePack(packName, useAiTranslation);

                // === STEP 8: Save to PocketBase ===
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: totalTarget,
                    currentStep: 'Saving to database...',
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
                    status: 'draft',
                    stickers: processedStickers,
                    tray_url: trayUrl,
                    tray_image_file: trayFile,
                    created_at: new Date().toISOString(),
                    batch_generated: true,
                    batch_search_term: searchTerm,
                    batch_source: source
                };

                await pb.collection('draft_stickers').update(draftRecordId, packData);

                const completed: CompletedPack = {
                    id: draftRecordId,
                    name: packName,
                    stickerCount: processedStickers.length,
                    searchTerm,
                    source
                };

                completedPacks.push(completed);
                packCreated = true;

                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: totalTarget,
                    currentStep: `✅ "${packName}" created! (${processedStickers.length} stickers)`,
                    packName,
                    status: 'running',
                    completedPacks
                });

                console.log(`[BATCH] ✅ Pack created: ${packName} (${processedStickers.length} stickers)`);

            } catch (error: any) {
                console.error(`[BATCH] Pack creation error (${searchTerm}, retry ${retryCount}):`, error);
                if (draftRecordId) {
                    await pb.collection('draft_stickers').delete(draftRecordId).catch(() => {});
                }

                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: totalTarget,
                        currentStep: `⚠️ Error, retrying (${retryCount}/${MAX_RETRIES})...`,
                        status: 'running',
                        completedPacks
                    });
                } else {
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: totalTarget,
                        currentStep: `❌ Failed: ${error.message}`,
                        status: 'error',
                        error: error.message,
                        completedPacks
                    });
                    break;
                }
            }
        }
    }

    onProgress?.({
        currentPack: totalTarget,
        totalPacks: totalTarget,
        currentStep: `🎉 Done! ${completedPacks.length}/${totalTarget} packs created.`,
        status: 'done',
        completedPacks
    });

    return completedPacks;
}

export const batchGenerator = {
    generate: generateBatchPacks,
    isReady: () => deepseekService.isConfigured()
};
