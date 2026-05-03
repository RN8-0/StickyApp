// Klipy API Service - Tenor replacement for sticker/GIF fetching
// Docs: https://docs.klipy.com/stickers-api
// Migration: https://docs.klipy.com/migrate-from-tenor

import { WORKER_URL } from '../pocketbase';

const KLIPY_API_KEY = 'YRtbKLSbrPqeEcPPpIpdoCqlLqZ9LlfbwQhXefbv3FQucQTjREFmkVLExf9JnI8k';

interface KlipySticker {
    id: string;
    title: string;
    url: string;
    previewUrl: string;
}

/**
 * Klipy Sticker Search API (native)
 * Endpoint: GET /v1/stickers/search
 */
export async function searchKlipyStickers(query: string, count: number = 20): Promise<KlipySticker[]> {
    // Worker proxy kullan (CORS bypass)
    const proxyUrl = `${WORKER_URL.replace(/\/$/, '')}/api/klipy?endpoint=search&query=${encodeURIComponent(query)}&limit=${count}`;
    
    console.log('[Klipy] Fetching via Worker:', proxyUrl);
    
    const response = await fetch(proxyUrl);
    
    if (!response.ok) {
        throw new Error(`Klipy Worker error (${response.status}): ${response.statusText}`);
    }

    const data = await response.json();
    
    if (!data.data || data.data.length === 0) {
        return [];
    }

    return data.data.map((item: any) => ({
        id: item.id || String(Date.now()),
        title: item.title || item.content_description || 'Sticker',
        url: extractBestUrl(item),
        previewUrl: extractPreviewUrl(item)
    })).filter((s: KlipySticker) => s.url);
}

/**
 * Klipy Sticker Trending API (native)
 * Endpoint: GET /v1/stickers/trending
 */
export async function trendingKlipyStickers(count: number = 20): Promise<KlipySticker[]> {
    // Worker proxy kullan (CORS bypass)
    const proxyUrl = `${WORKER_URL.replace(/\/$/, '')}/api/klipy?endpoint=trending&limit=${count}`;
    
    console.log('[Klipy] Fetching trending via Worker:', proxyUrl);
    
    const response = await fetch(proxyUrl);
    
    if (!response.ok) {
        throw new Error(`Klipy Worker error (${response.status}): ${response.statusText}`);
    }

    const data = await response.json();
    
    if (!data.data || data.data.length === 0) {
        return [];
    }

    return data.data.map((item: any) => ({
        id: item.id || String(Date.now()),
        title: item.title || item.content_description || 'Sticker',
        url: extractBestUrl(item),
        previewUrl: extractPreviewUrl(item)
    })).filter((s: KlipySticker) => s.url);
}

// Tenor-compatible fallback functions removed - using Cloud Function proxy instead

/**
 * URL extraction helpers for Klipy native API responses
 */
function extractBestUrl(item: any): string {
    // Klipy native sticker format
    if (item.images) {
        return item.images?.original?.url 
            || item.images?.fixed_height?.url 
            || item.images?.downsized?.url 
            || '';
    }
    // Klipy media_formats (Tenor-like)
    if (item.media_formats) {
        return item.media_formats?.gif?.url 
            || item.media_formats?.mediumgif?.url 
            || '';
    }
    // Direct URL
    if (item.url) return item.url;
    if (item.gif_url) return item.gif_url;
    if (item.sticker_url) return item.sticker_url;
    
    return '';
}

function extractPreviewUrl(item: any): string {
    if (item.images) {
        return item.images?.preview_gif?.url 
            || item.images?.fixed_height_small?.url 
            || item.images?.downsized?.url 
            || '';
    }
    if (item.media_formats) {
        return item.media_formats?.nanogif?.url 
            || item.media_formats?.tinygif?.url 
            || item.media_formats?.gif?.url 
            || '';
    }
    if (item.preview_url) return item.preview_url;
    
    return extractBestUrl(item);
}

/**
 * Unified fetch function for stickerImporter.ts integration
 */
export async function fetchFromKlipy(query: string, count: number): Promise<KlipySticker[]> {
    try {
        if (query === 'trending') {
            return await trendingKlipyStickers(count);
        }
        
        return await searchKlipyStickers(query, count);
    } catch (error: any) {
        console.error('[Klipy] Fetch hatası:', error);
        throw new Error(`Klipy hatası: ${error.message}`);
    }
}

export const klipyService = {
    search: searchKlipyStickers,
    trending: trendingKlipyStickers,
    fetch: fetchFromKlipy,
    isConfigured: () => !!KLIPY_API_KEY
};
