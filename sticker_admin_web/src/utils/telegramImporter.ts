// Telegram Sticker Pack Importer
// Downloads curated sticker packs from Telegram via Bot API and imports to Firebase

import { storage, db } from '../firebase';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { doc, setDoc, serverTimestamp, collection, query, where, getDocs } from 'firebase/firestore';
import { stickerProcessor } from './stickerProcessor';
import { deepseekService, autoDetectCategory } from './deepseekService';
import pako from 'pako';
import lottie from 'lottie-web';
import type { Sticker } from '../types';

const TELEGRAM_PROXY = 'https://us-central1-sticky-dcd20.cloudfunctions.net/telegramProxy';

// ========== TYPES ==========

export interface TelegramSticker {
    file_id: string;
    file_unique_id: string;
    type: string; // 'regular', 'mask', 'custom_emoji'
    width: number;
    height: number;
    is_animated: boolean;
    is_video: boolean;
    emoji?: string;
    set_name?: string;
    thumbnail?: { file_id: string; width: number; height: number };
}

export interface TelegramStickerSet {
    name: string;
    title: string;
    sticker_type: string;
    stickers: TelegramSticker[];
}

export interface TelegramImportProgress {
    currentPack: number;
    totalPacks: number;
    currentStep: string;
    packName?: string;
    stickerProgress?: { current: number; total: number };
    status: 'running' | 'done' | 'error';
    error?: string;
    completedPacks: TelegramCompletedPack[];
}

export interface TelegramCompletedPack {
    id: string;
    name: string;
    stickerCount: number;
    telegramName: string;
}

// ========== API FUNCTIONS ==========

async function getStickerSet(botToken: string, setName: string): Promise<TelegramStickerSet> {
    const url = `${TELEGRAM_PROXY}?token=${encodeURIComponent(botToken)}&method=getStickerSet&name=${encodeURIComponent(setName)}`;
    const response = await fetch(url);
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.description || `Telegram API error: ${response.status}`);
    }
    const data = await response.json();
    if (!data.ok) throw new Error(data.description || 'Failed to get sticker set');
    return data.result;
}

async function getFile(botToken: string, fileId: string): Promise<string> {
    const url = `${TELEGRAM_PROXY}?token=${encodeURIComponent(botToken)}&method=getFile&file_id=${encodeURIComponent(fileId)}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error(`getFile error: ${response.status}`);
    const data = await response.json();
    if (!data.ok) throw new Error(data.description || 'Failed to get file');
    return data.result.file_path;
}

async function downloadTelegramFile(botToken: string, filePath: string): Promise<Blob> {
    const url = `${TELEGRAM_PROXY}?token=${encodeURIComponent(botToken)}&file_path=${encodeURIComponent(filePath)}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 30000);
    try {
        const response = await fetch(url, { signal: controller.signal });
        clearTimeout(timeout);
        if (!response.ok) throw new Error(`Download error: ${response.status}`);
        return await response.blob();
    } catch (e: any) {
        clearTimeout(timeout);
        if (e.name === 'AbortError') throw new Error('File download timeout (30s)');
        throw e;
    }
}

// ========== PACK NAME EXTRACTION ==========

function extractSetName(input: string): string {
    // Handle various Telegram sticker URL/name formats
    // https://t.me/addstickers/PackName
    // https://telegram.me/addstickers/PackName
    const tgMatch = input.match(/(?:t\.me|telegram\.me)\/addstickers\/([A-Za-z0-9_]+)/);
    if (tgMatch) return tgMatch[1];

    // https://stickers.gg/packs/telegram/PackName
    const sggMatch = input.match(/stickers\.gg\/packs?\/telegram\/([A-Za-z0-9_]+)/i);
    if (sggMatch) return sggMatch[1];

    // https://tlgrm.eu/stickers/PackName
    const tlgrmMatch = input.match(/tlgrm\.eu\/stickers\/([A-Za-z0-9_]+)/i);
    if (tlgrmMatch) return tlgrmMatch[1];

    // Generic: extract last path segment from any URL
    const genericUrlMatch = input.match(/https?:\/\/[^/]+\/.*\/([A-Za-z0-9_]+)\/?$/);
    if (genericUrlMatch) return genericUrlMatch[1];

    // Clean name (just pack name, no URL)
    return input.trim().replace(/\s+/g, '');
}

// ========== THEME EMOJI DETECTION ==========

function autoDetectEmoji(text: string): string {
    const lower = text.toLowerCase();
    const emojiMap: [string[], string][] = [
        [['cat', 'kitty', 'kitten', 'meow', 'neko'], '🐱'],
        [['dog', 'puppy', 'doggy', 'woof', 'pup'], '🐶'],
        [['duck', 'quack'], '🦆'],
        [['bear', 'teddy'], '🐻'],
        [['rabbit', 'bunny', 'usagi'], '🐰'],
        [['fox'], '🦊'],
        [['panda'], '🐼'],
        [['frog', 'pepe'], '🐸'],
        [['bird', 'penguin'], '🐦'],
        [['pig', 'piggy'], '🐷'],
        [['monkey', 'ape'], '🐵'],
        [['love', 'heart', 'kiss', 'romance', 'valentine', 'couple'], '❤️'],
        [['happy', 'smile', 'joy', 'laugh', 'lol', 'haha'], '😄'],
        [['sad', 'cry', 'tear'], '😢'],
        [['angry', 'mad', 'rage'], '😠'],
        [['cool', 'swag', 'awesome'], '😎'],
        [['fire', 'hot', 'lit'], '🔥'],
        [['star', 'sparkle', 'shine'], '⭐'],
        [['food', 'eat', 'yummy', 'delicious'], '🍔'],
        [['coffee', 'tea', 'drink'], '☕'],
        [['game', 'gaming', 'play'], '🎮'],
        [['music', 'song', 'sing', 'dance'], '🎵'],
        [['sport', 'football', 'soccer', 'ball'], '⚽'],
        [['christmas', 'xmas', 'santa'], '🎄'],
        [['halloween', 'spooky', 'ghost'], '👻'],
        [['anime', 'manga', 'kawaii'], '✨'],
        [['flower', 'rose', 'blossom'], '🌸'],
        [['moon', 'night', 'sleep'], '🌙'],
        [['sun', 'morning', 'bright'], '☀️'],
        [['party', 'celebrate', 'birthday'], '🎉'],
    ];
    for (const [keywords, emoji] of emojiMap) {
        if (keywords.some(k => lower.includes(k))) return emoji;
    }
    return '✨';
}

// ========== DUPLICATE DETECTION ==========

// Render ALL frames of TGS (Lottie) animation → PNG blobs for FFmpeg encoding
async function renderTgsFrames(
    tgsBuffer: ArrayBuffer,
    onProgress?: (msg: string) => void
): Promise<{ frames: Blob[], fps: number }> {
    const data = new Uint8Array(tgsBuffer);
    const jsonString = pako.ungzip(data, { to: 'string' });
    const lottieData = JSON.parse(jsonString);

    const wrapper = document.createElement('div');
    wrapper.style.cssText = 'position:fixed;left:0;top:0;width:512px;height:512px;opacity:0;pointer-events:none;z-index:-9999;';
    document.body.appendChild(wrapper);

    try {
        const anim = lottie.loadAnimation({
            container: wrapper,
            renderer: 'canvas',
            loop: false,
            autoplay: false,
            animationData: lottieData,
            rendererSettings: {
                clearCanvas: true,
                progressiveLoad: false,
            }
        });

        await new Promise<void>((resolve, reject) => {
            const timeout = setTimeout(() => reject(new Error('Lottie DOMLoaded timeout')), 15000);
            anim.addEventListener('DOMLoaded', () => { clearTimeout(timeout); resolve(); });
        });

        const totalFrames = anim.totalFrames;
        const originalFps = lottieData.fr || 60;
        const targetFps = 15;
        const frameStep = Math.max(1, Math.round(originalFps / targetFps));

        // Use lottie's own canvas directly
        const lottieCanvas = wrapper.querySelector('canvas') as HTMLCanvasElement;
        if (!lottieCanvas) throw new Error('Lottie did not create canvas');

        const outCanvas = document.createElement('canvas');
        outCanvas.width = 512;
        outCanvas.height = 512;
        const outCtx = outCanvas.getContext('2d')!;

        const frames: Blob[] = [];
        const totalOutputFrames = Math.ceil(totalFrames / frameStep);

        for (let f = 0; f < totalFrames; f += frameStep) {
            anim.goToAndStop(f, true);
            onProgress?.(`Rendering frame ${frames.length + 1}/${totalOutputFrames}...`);

            // Wait for canvas render
            await new Promise(r => requestAnimationFrame(r));

            // Copy from lottie canvas to output canvas
            outCtx.clearRect(0, 0, 512, 512);
            outCtx.drawImage(lottieCanvas, 0, 0, 512, 512);

            const blob = await new Promise<Blob>((resolve, reject) => {
                outCanvas.toBlob(
                    b => b ? resolve(b) : reject(new Error('Frame export failed')),
                    'image/png'
                );
            });
            frames.push(blob);
        }

        anim.destroy();
        console.log(`[TELEGRAM] Rendered ${frames.length} frames from TGS (${totalFrames} total @ ${originalFps}fps → ${targetFps}fps)`);
        return { frames, fps: targetFps };
    } finally {
        document.body.removeChild(wrapper);
    }
}

// Thumbnail fallback: resize thumbnail to 512x512 static WebP (last resort)
// ========== DUPLICATE DETECTION ==========

async function checkDuplicatePack(setName: string): Promise<{ exists: boolean; location?: string }> {
    try {
        const stickersQuery = query(collection(db, 'stickers'), where('telegram_set_name', '==', setName));
        const draftsQuery = query(collection(db, 'draft_stickers'), where('telegram_set_name', '==', setName));
        const [stickersSnap, draftsSnap] = await Promise.all([getDocs(stickersQuery), getDocs(draftsQuery)]);
        if (!stickersSnap.empty) return { exists: true, location: 'published' };
        if (!draftsSnap.empty) return { exists: true, location: 'drafts' };
        return { exists: false };
    } catch {
        return { exists: false };
    }
}

// ========== STICKER PROCESSING ==========

async function processTelegramSticker(
    botToken: string,
    sticker: TelegramSticker,
    packId: string,
    index: number,
    onProgress?: (msg: string) => void,
    abortSignal?: AbortSignal
): Promise<Sticker | null> {
    if (abortSignal?.aborted) return null;
    // Per-sticker timeout: 90s for animated/video, 30s for static
    const timeoutMs = (sticker.is_animated || sticker.is_video) ? 90000 : 30000;
    return Promise.race([
        processTelegramStickerInner(botToken, sticker, packId, index, onProgress),
        new Promise<null>((resolve) => setTimeout(() => {
            console.warn(`[TELEGRAM] Sticker #${index + 1} timed out after ${timeoutMs / 1000}s`);
            onProgress?.(`⏱️ Sticker #${index + 1} timed out, skipping...`);
            resolve(null);
        }, timeoutMs)),
        // Abort signal race — resolves immediately when stop is pressed
        ...(abortSignal ? [new Promise<null>((resolve) => {
            if (abortSignal.aborted) { resolve(null); return; }
            abortSignal.addEventListener('abort', () => {
                console.log(`[TELEGRAM] Sticker #${index + 1} aborted by user`);
                resolve(null);
            }, { once: true });
        })] : [])
    ]);
}

async function processTelegramStickerInner(
    botToken: string,
    sticker: TelegramSticker,
    packId: string,
    index: number,
    onProgress?: (msg: string) => void
): Promise<Sticker | null> {
    try {
        let webpBlob: Blob | null = null;
        console.log(`[TELEGRAM] Sticker #${index + 1}: animated=${sticker.is_animated}, video=${sticker.is_video}, thumb=${!!sticker.thumbnail}`);

        if (sticker.is_animated) {
            // Animated TGS → render ALL frames via lottie-web SVG → FFmpeg animated WebP
            onProgress?.(`Rendering animated sticker #${index + 1}...`);
            let lastError: any = null;

            for (let attempt = 0; attempt < 2; attempt++) {
                try {
                    if (attempt > 0) {
                        onProgress?.(`Retrying animated #${index + 1} (reloading FFmpeg)...`);
                        await stickerProcessor.forceReload();
                    }
                    const filePath = await getFile(botToken, sticker.file_id);
                    const blob = await downloadTelegramFile(botToken, filePath);
                    const tgsBuffer = await blob.arrayBuffer();

                    const { frames, fps } = await renderTgsFrames(tgsBuffer, onProgress);
                    console.log(`[TELEGRAM] TGS #${index + 1}: ${frames.length} frames @ ${fps}fps`);

                    onProgress?.(`Encoding animated WebP #${index + 1} (${frames.length} frames)...`);
                    webpBlob = await stickerProcessor.processFromPngFrames(frames, fps, (p) => {
                        onProgress?.(`Sticker #${index + 1}: ${p.message}`);
                    });
                    console.log(`[TELEGRAM] ✓ Animated WebP for #${index + 1}: ${Math.round(webpBlob.size / 1024)}KB`);
                    lastError = null;
                    break;
                } catch (err: any) {
                    lastError = err;
                    console.warn(`[TELEGRAM] Animated attempt ${attempt + 1} failed for #${index + 1}:`, err.message);
                }
            }

            if (lastError) {
                console.error(`[TELEGRAM] Animated render failed for #${index + 1} after retries:`, lastError.message);
                onProgress?.(`⚠️ Animated #${index + 1} failed, skipping (no static fallback for animated packs)...`);
                return null;
            }
        } else if (sticker.is_video) {
            // Video WebM → FFmpeg → animated WebP
            onProgress?.(`Processing video sticker #${index + 1}...`);
            let lastError: any = null;

            for (let attempt = 0; attempt < 2; attempt++) {
                try {
                    if (attempt > 0) {
                        onProgress?.(`Retrying video #${index + 1} (reloading FFmpeg)...`);
                        await stickerProcessor.forceReload();
                    }
                    const filePath = await getFile(botToken, sticker.file_id);
                    const blob = await downloadTelegramFile(botToken, filePath);
                    const videoFile = new File([blob], `sticker_${index}.webm`, { type: 'video/webm' });
                    webpBlob = await stickerProcessor.processAnimated(videoFile, (p) => {
                        onProgress?.(`Sticker #${index + 1}: ${p.message}`);
                    });
                    console.log(`[TELEGRAM] ✓ Video→WebP for #${index + 1}: ${Math.round(webpBlob.size / 1024)}KB`);
                    lastError = null;
                    break;
                } catch (err: any) {
                    lastError = err;
                    console.warn(`[TELEGRAM] Video attempt ${attempt + 1} failed for #${index + 1}:`, err.message);
                }
            }

            if (lastError) {
                console.error(`[TELEGRAM] Video render failed for #${index + 1} after retries:`, lastError.message);
                onProgress?.(`⚠️ Video #${index + 1} failed, skipping (no static fallback for animated packs)...`);
                return null;
            }
        } else {
            // Static WebP — download directly
            onProgress?.(`Downloading sticker #${index + 1}...`);
            const filePath = await getFile(botToken, sticker.file_id);
            webpBlob = await downloadTelegramFile(botToken, filePath);

            if (webpBlob.size > 500 * 1024) {
                onProgress?.(`Optimizing sticker #${index + 1} (${Math.round(webpBlob.size / 1024)}KB)...`);
                try {
                    const file = new File([webpBlob], `sticker_${index}.webp`, { type: 'image/webp' });
                    webpBlob = await stickerProcessor.processStatic(file, (p) => {
                        onProgress?.(`Sticker #${index + 1}: ${p.message}`);
                    });
                } catch {
                    console.warn(`[TELEGRAM] Failed to optimize sticker #${index + 1}, skipping`);
                    return null;
                }
            }
        }

        // Check result
        if (!webpBlob) {
            console.warn(`[TELEGRAM] Sticker #${index + 1} produced no output`);
            return null;
        }

        // Size check
        if (webpBlob.size > 500 * 1024) {
            console.warn(`[TELEGRAM] Sticker #${index + 1} too large (${Math.round(webpBlob.size / 1024)}KB), skipping`);
            return null;
        }

        onProgress?.(`Uploading sticker #${index + 1}...`);
        const fileName = `tg_${sticker.file_unique_id}_${index}.webp`;
        const storagePath = `stickers/${packId}/${fileName}`;
        const storageRef = ref(storage, storagePath);

        await uploadBytes(storageRef, webpBlob);
        const downloadURL = await getDownloadURL(storageRef);

        return {
            image_file: fileName,
            url: downloadURL,
            emojis: [sticker.emoji || '😀']
        };
    } catch (error: any) {
        console.error(`[TELEGRAM] Sticker #${index + 1} processing error:`, error);
        onProgress?.(`❌ Sticker #${index + 1} failed: ${error.message}`);
        return null;
    }
}

// ========== TRAY IMAGE ==========

async function createTrayFromSticker(stickers: Sticker[], packId: string): Promise<{ trayUrl: string; trayFile: string }> {
    try {
        if (stickers.length === 0) return { trayUrl: '', trayFile: '' };
        const chosenSticker = stickers[0];

        const response = await fetch(chosenSticker.url);
        const blob = await response.blob();
        const tempFile = new File([blob], 'tray.webp', { type: 'image/webp' });
        const trayBlob = await stickerProcessor.processTray(tempFile, () => {});

        const trayFileName = `tray_${Date.now()}.png`;
        const trayStorageRef = ref(storage, `stickers/${packId}/${trayFileName}`);
        await uploadBytes(trayStorageRef, trayBlob);
        const trayUrl = await getDownloadURL(trayStorageRef);

        return { trayUrl, trayFile: trayFileName };
    } catch (error) {
        console.error('[TELEGRAM] Tray creation error:', error);
        return { trayUrl: '', trayFile: '' };
    }
}

// ========== MAIN IMPORT FUNCTION ==========

export async function importTelegramPacks(
    botToken: string,
    packInputs: string[],
    options: {
        useAiNaming?: boolean;
        useAiTranslation?: boolean;
        stickerLimit?: number;
        maxStickers?: number;
        splitPacks?: boolean;
        keepOriginalName?: boolean;
        abortSignal?: AbortSignal;
        onProgress?: (progress: TelegramImportProgress) => void;
    } = {}
): Promise<TelegramCompletedPack[]> {
    const {
        useAiNaming = false,
        stickerLimit = 30,
        maxStickers = 0,
        splitPacks = true,
        keepOriginalName = true,
        abortSignal,
        onProgress
    } = options;
    const completedPacks: TelegramCompletedPack[] = [];
    const errors: string[] = [];
    const batchProcessedNames = new Set<string>(); // In-memory dedup within this batch

    for (let i = 0; i < packInputs.length; i++) {
        // Check abort signal
        if (abortSignal?.aborted) {
            onProgress?.({
                currentPack: i,
                totalPacks: packInputs.length,
                currentStep: `⛔ Import stopped! ${completedPacks.length} packs completed.`,
                status: 'done',
                completedPacks
            });
            return completedPacks;
        }

        const input = packInputs[i].trim();
        if (!input) continue;

        try {
            const setName = extractSetName(input);
            console.log('[TELEGRAM] Processing:', setName, 'from input:', input);

            // In-memory dedup: skip if same Telegram set name already in this batch
            if (batchProcessedNames.has(setName.toLowerCase())) {
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: `⏭️ "${setName}" is a duplicate in this batch (same Telegram pack), skipping...`,
                    status: 'running',
                    completedPacks
                });
                continue;
            }

            // Step 1: Fetch sticker set from Telegram
            onProgress?.({
                currentPack: i + 1,
                totalPacks: packInputs.length,
                currentStep: `Fetching "${setName}" from Telegram...`,
                status: 'running',
                completedPacks
            });

            const stickerSet = await getStickerSet(botToken, setName);
            console.log('[TELEGRAM] Got set:', stickerSet.title, 'with', stickerSet.stickers.length, 'stickers');

            // Check for duplicates
            const dupCheck = await checkDuplicatePack(setName);
            console.log('[TELEGRAM] Duplicate check:', dupCheck);
            if (dupCheck.exists) {
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: `⏭️ "${stickerSet.title}" already exists in ${dupCheck.location}, skipping...`,
                    status: 'running',
                    completedPacks
                });
                continue;
            }

            const allStickers = stickerSet.stickers;
            const filteredByType = allStickers;

            const isAnimatedPack = filteredByType.some(s => s.is_animated || s.is_video);

            if (filteredByType.length === 0) {
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: `⚠️ "${stickerSet.title}" has no stickers, skipping...`,
                    status: 'running',
                    completedPacks
                });
                continue;
            }

            // Step 2: Generate pack name with emoji
            let baseNameText = stickerSet.title;
            let baseEmoji = '';

            // Extract theme emoji from sticker emojis if available
            const stickerEmojis = filteredByType.map(s => s.emoji).filter(Boolean);
            if (stickerEmojis.length > 0) {
                // Use the most common emoji from the pack
                const emojiCount = new Map<string, number>();
                stickerEmojis.forEach(e => emojiCount.set(e!, (emojiCount.get(e!) || 0) + 1));
                baseEmoji = [...emojiCount.entries()].sort((a, b) => b[1] - a[1])[0][0];
            }

            if (!keepOriginalName && useAiNaming && deepseekService.isConfigured()) {
                try {
                    const names = await deepseekService.generatePackNames(stickerSet.title, 3);
                    if (names.length > 0) {
                        baseNameText = names[0].name;
                        baseEmoji = names[0].emoji || baseEmoji || '✨';
                    }
                } catch {
                    // Keep original Telegram name
                }
            }

            // Ensure emoji always exists
            if (!baseEmoji) {
                baseEmoji = autoDetectEmoji(stickerSet.title + ' ' + setName);
            }

            // baseName for single pack: "Duck 🦆", for multi: will become "Duck 1 🦆", "Duck 2 🦆"
            const baseName = `${baseNameText} ${baseEmoji}`;

            // Step 3: Apply max stickers limit, then split or single-pack
            let stickersToProcess = maxStickers > 0 ? filteredByType.slice(0, maxStickers) : filteredByType;
            const chunks: TelegramSticker[][] = [];

            if (splitPacks) {
                // Split into multiple packs of stickerLimit each
                for (let c = 0; c < stickersToProcess.length; c += stickerLimit) {
                    chunks.push(stickersToProcess.slice(c, c + stickerLimit));
                }
            } else {
                // Single pack mode: cap at stickerLimit (max 30 for WhatsApp)
                const limit = Math.min(stickerLimit, 30);
                chunks.push(stickersToProcess.slice(0, limit));
            }
            const totalParts = chunks.length;

            // Only English name for drafts
            const translations: Record<string, string> = { name_en: baseName };

            const category = autoDetectCategory(stickerSet.title + ' ' + setName);

            // Process each chunk as a separate pack
            for (let partIdx = 0; partIdx < chunks.length; partIdx++) {
                const chunk = chunks[partIdx];
                const partNum = partIdx + 1;
                // Format: "Duck 🦆" for single, "Duck 1 🦆", "Duck 2 🦆" for multi
                const packName = totalParts > 1 ? `${baseNameText} ${partNum} ${baseEmoji}` : baseName;
                const packId = `tg_${setName.toLowerCase()}_${totalParts > 1 ? partNum + '_' : ''}${Date.now().toString(36)}`;

                // Add part number to translations if multi-part: "Name 2 emoji"
                const partTranslations: Record<string, string> = {};
                for (const [key, val] of Object.entries(translations)) {
                    if (totalParts > 1) {
                        // Insert part number before the emoji at the end
                        const emojiRegex = /(\p{Emoji_Presentation}|\p{Emoji}\uFE0F)+$/u;
                        const match = val.match(emojiRegex);
                        if (match) {
                            const textPart = val.slice(0, match.index).trimEnd();
                            partTranslations[key] = `${textPart} ${partNum} ${match[0]}`;
                        } else {
                            partTranslations[key] = `${val} ${partNum} ${baseEmoji}`;
                        }
                    } else {
                        partTranslations[key] = val;
                    }
                }

                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: totalParts > 1
                        ? `Importing "${packName}" (Part ${partNum}/${totalParts}, ${chunk.length} stickers)...`
                        : `Importing "${packName}" (${chunk.length} ${isAnimatedPack ? 'animated' : 'static'} stickers)...`,
                    packName,
                    status: 'running',
                    completedPacks
                });

                // Process stickers - parallel for static, sequential for animated (FFmpeg WASM is single-threaded)
                const processedStickers: Sticker[] = [];
                const processedIsAnimated: boolean[] = [];
                const hasAnimated = chunk.some(s => s.is_animated || s.is_video);
                const PARALLEL_BATCH = hasAnimated ? 1 : 3; // parallel for static packs

                for (let j = 0; j < chunk.length; j += PARALLEL_BATCH) {
                    if (abortSignal?.aborted) break;
                    const batchEnd = Math.min(j + PARALLEL_BATCH, chunk.length);
                    const batchSlice = chunk.slice(j, batchEnd);

                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `${totalParts > 1 ? `[Part ${partNum}/${totalParts}] ` : ''}Processing sticker${PARALLEL_BATCH > 1 ? 's' : ''} ${j + 1}${batchEnd > j + 1 ? `-${batchEnd}` : ''}/${chunk.length}`,
                        packName,
                        stickerProgress: { current: j, total: chunk.length },
                        status: 'running',
                        completedPacks
                    });

                    const results = await Promise.all(
                        batchSlice.map((s, bIdx) => {
                            const globalIdx = partIdx * stickerLimit + j + bIdx;
                            return processTelegramSticker(botToken, s, packId, globalIdx, (msg) => {
                                onProgress?.({
                                    currentPack: i + 1,
                                    totalPacks: packInputs.length,
                                    currentStep: msg,
                                    packName,
                                    stickerProgress: { current: j + bIdx, total: chunk.length },
                                    status: 'running',
                                    completedPacks
                                });
                            }, abortSignal);
                        })
                    );

                    results.forEach((sticker, bIdx) => {
                        if (sticker) {
                            processedStickers.push(sticker);
                            processedIsAnimated.push(batchSlice[bIdx].is_animated || batchSlice[bIdx].is_video);
                        }
                    });

                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `✓ ${processedStickers.length}/${chunk.length} stickers done`,
                        packName,
                        stickerProgress: { current: batchEnd, total: chunk.length },
                        status: 'running',
                        completedPacks
                    });
                }

                if (processedStickers.length === 0) {
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `⚠️ No stickers processed for "${packName}"`,
                        status: 'running',
                        completedPacks
                    });
                    continue;
                }

                // WhatsApp requires packs to be either ALL animated or ALL static
                // Determine actual pack type based on processed stickers
                const animatedCount = processedIsAnimated.filter(Boolean).length;
                const staticCount = processedStickers.length - animatedCount;

                // Build list of sub-packs to save (1 pack if pure, 2 if mixed)
                const subPacks: Array<{ stickers: any[]; isAnimated: boolean; suffix: string }> = [];

                if (animatedCount > 0 && staticCount > 0) {
                    // Mixed pack! Split into two separate packs
                    const animatedStickers = processedStickers.filter((_, idx) => processedIsAnimated[idx]);
                    const staticStickers = processedStickers.filter((_, idx) => !processedIsAnimated[idx]);
                    console.log(`[TELEGRAM] Mixed pack detected: splitting into ${animatedCount} animated + ${staticCount} static`);
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `🔀 Mixed pack detected: splitting into ${animatedCount} animated + ${staticCount} static packs`,
                        packName,
                        status: 'running',
                        completedPacks
                    });
                    if (animatedStickers.length > 0) subPacks.push({ stickers: animatedStickers, isAnimated: true, suffix: ' (Animated)' });
                    if (staticStickers.length > 0) subPacks.push({ stickers: staticStickers, isAnimated: false, suffix: ' (Static)' });
                } else {
                    subPacks.push({ stickers: processedStickers, isAnimated: animatedCount > 0, suffix: '' });
                }

                for (const subPack of subPacks) {
                    if (subPack.stickers.length === 0) continue;

                    const subPackName = packName + subPack.suffix;
                    const subPackId = subPack.suffix ? `${packId}_${subPack.isAnimated ? 'anim' : 'static'}` : packId;

                    // Create tray
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `Creating cover for "${subPackName}"...`,
                        packName: subPackName,
                        status: 'running',
                        completedPacks
                    });

                    const { trayUrl, trayFile } = await createTrayFromSticker(subPack.stickers, subPackId);

                    // Save to Firestore
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `Saving "${subPackName}" to database...`,
                        packName: subPackName,
                        status: 'running',
                        completedPacks
                    });

                    const packData: any = {
                        name: subPackName,
                        ...partTranslations,
                        publisher: 'Sticky Telegram',
                        publisher_email: 'contact@arain.digital',
                        privacy_policy_website: '',
                        license_agreement_website: '',
                        category,
                        is_premium: false,
                        is_animated: subPack.isAnimated,
                        download_count: 0,
                        fake_download_base: Math.floor(Math.random() * 7001) + 3000,
                        view_count: 0,
                        favorite_count: 0,
                        sticker_count: subPack.stickers.length,
                        image_data_version: Date.now().toString(),
                        is_active: true,
                        stickers: subPack.stickers,
                        tray_url: trayUrl,
                        tray_image_file: trayFile,
                        created_at: serverTimestamp(),
                        batch_generated: true,
                        batch_source: 'telegram',
                        batch_search_term: setName,
                        telegram_set_name: setName,
                        telegram_set_title: stickerSet.title,
                        ...(totalParts > 1 ? { telegram_part: partNum, telegram_total_parts: totalParts } : {})
                    };

                    await setDoc(doc(db, 'draft_stickers', subPackId), packData);

                    completedPacks.push({
                        id: subPackId,
                        name: subPackName,
                        stickerCount: subPack.stickers.length,
                        telegramName: setName
                    });

                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `✅ "${subPackName}" imported! (${subPack.stickers.length} ${subPack.isAnimated ? 'animated' : 'static'} stickers)`,
                        packName: subPackName,
                        status: 'running',
                        completedPacks
                    });

                    console.log(`[TELEGRAM] ✅ Imported: ${subPackName} (${subPack.stickers.length} ${subPack.isAnimated ? 'animated' : 'static'} stickers from @${setName})`);
                }
            }

            batchProcessedNames.add(setName.toLowerCase());

        } catch (error: any) {
            console.error(`[TELEGRAM] Import error (${input}):`, error);
            errors.push(error.message || 'Unknown error');
            onProgress?.({
                currentPack: i + 1,
                totalPacks: packInputs.length,
                currentStep: `❌ Error importing pack: ${error.message}`,
                status: 'running',
                error: error.message,
                completedPacks
            });
        }
    }

    const doneMsg = errors.length > 0
        ? `⚠️ Done! ${completedPacks.length}/${packInputs.length} imported. Errors: ${errors.join(' | ')}`
        : `🎉 Done! ${completedPacks.length}/${packInputs.length} packs imported successfully!`;

    onProgress?.({
        currentPack: packInputs.length,
        totalPacks: packInputs.length,
        currentStep: doneMsg,
        status: 'done',
        completedPacks
    });

    return completedPacks;
}

// ========== VALIDATE BOT TOKEN ==========

export async function validateBotToken(token: string): Promise<{ valid: boolean; botName?: string }> {
    try {
        const url = `${TELEGRAM_PROXY}?token=${encodeURIComponent(token)}&method=getMe`;
        const response = await fetch(url);
        if (!response.ok) return { valid: false };
        const data = await response.json();
        if (!data.ok) return { valid: false };
        return { valid: true, botName: data.result.username };
    } catch {
        return { valid: false };
    }
}

export const telegramImporter = {
    importPacks: importTelegramPacks,
    validateToken: validateBotToken,
    extractSetName
};
