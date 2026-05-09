// Telegram Sticker Pack Importer
// Downloads curated sticker packs from Telegram via Bot API and imports to PocketBase

import { pb, WORKER_URL, uploadFile } from '../pocketbase';
import { stickerProcessor } from './stickerProcessor';
import { deepseekService, autoDetectCategory } from './deepseekService';
import pako from 'pako';
import lottie from 'lottie-web';
import type { Sticker } from '../types';

const TELEGRAM_PROXY = `${WORKER_URL.replace(/\/$/, '')}/api/telegram`;

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

function normalizeBotToken(token: string): string {
    return token
        .trim()
        .replace(/^bot/i, '')
        .replace(/[\u200B-\u200D\uFEFF\s]/g, '');
}

async function telegramApiPost(botToken: string, method: string, params: Record<string, string> = {}): Promise<any> {
    const cleanToken = normalizeBotToken(botToken);
    const response = await fetch(TELEGRAM_PROXY, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: cleanToken, method, params }),
    });
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.description || errorData.message || `Telegram API error: ${response.status}`);
    }
    const data = await response.json();
    if (!data.ok) throw new Error(data.description || 'Failed to get sticker set');
    return data;
}

async function telegramFilePost(botToken: string, filePath: string): Promise<Blob> {
    const cleanToken = normalizeBotToken(botToken);
    const response = await fetch(TELEGRAM_PROXY, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: cleanToken, file_path: filePath }),
    });
    if (!response.ok) throw new Error(`Telegram file download error: ${response.status}`);
    return await response.blob();
}

async function getStickerSet(botToken: string, setName: string): Promise<TelegramStickerSet> {
    const data = await telegramApiPost(botToken, 'getStickerSet', { name: setName });
    return data.result;
}

async function getFile(botToken: string, fileId: string): Promise<string> {
    const data = await telegramApiPost(botToken, 'getFile', { file_id: fileId });
    return data.result.file_path;
}

async function downloadTelegramFile(botToken: string, filePath: string): Promise<Blob> {
    return await telegramFilePost(botToken, filePath);
}

// ========== PACK NAME EXTRACTION ==========

function extractSetName(input: string): string {
    // Handle various Telegram sticker URL/name formats
    // https://t.me/addstickers/PackName
    // https://telegram.me/addstickers/PackName
    const tgMatch = input.match(/(?:t\.me|telegram\.me)\/addstickers\/([A-Za-z0-9_]+)/);
    if (tgMatch) return tgMatch[1];

    // https://stickers.gg/packs/telegram/PackName  OR  https://stickers.gg/packs/PackName
    const sggMatch = input.match(/stickers\.gg\/packs?\/(?:telegram\/)?([A-Za-z0-9_]+)/i);
    if (sggMatch) return sggMatch[1];

    // https://tlgrm.eu/stickers/PackName
    const tlgrmMatch = input.match(/tlgrm\.eu\/stickers\/([A-Za-z0-9_]+)/i);
    if (tlgrmMatch) return tlgrmMatch[1];

    // https://fullyst.com/en/stickers/PackName  OR  https://fullyst.com/stickers/PackName
    const fullystMatch = input.match(/fullyst\.com\/(?:[a-z]{2}\/)?stickers\/([A-Za-z0-9_]+)/i);
    if (fullystMatch) return fullystMatch[1];

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
        // Lower target FPS + cap output frames so encoding (FFmpeg WASM) doesn't take >60s per sticker.
        // 30 frames at 12fps = 2.5s of animation, which is enough for short looping stickers.
        const MAX_OUTPUT_FRAMES = 30;
        const targetFps = 12;
        const fpsStep = Math.max(1, Math.round(originalFps / targetFps));
        const frameStep = Math.max(fpsStep, Math.ceil(totalFrames / MAX_OUTPUT_FRAMES));

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

function escapePbText(value: string): string {
    return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

async function checkDuplicatePack(setName: string): Promise<{ exists: boolean; location?: string }> {
    try {
        const filter = `telegram_set_name = "${escapePbText(setName)}"`;
        const [stickersRes, premiumRes, draftsRes] = await Promise.all([
            pb.collection('stickers').getList(1, 1, { filter }).catch(() => ({ totalItems: 0 })),
            pb.collection('premium_stickers').getList(1, 1, { filter }).catch(() => ({ totalItems: 0 })),
            pb.collection('draft_stickers').getList(1, 10, { filter, sort: '-created_at' }).catch(() => ({ totalItems: 0, items: [] })),
        ]);
        if (stickersRes.totalItems > 0) return { exists: true, location: 'published (free)' };
        if (premiumRes.totalItems > 0) return { exists: true, location: 'published (premium)' };
        const draftItems = Array.isArray((draftsRes as any).items) ? (draftsRes as any).items : [];
        const hasUsableDraft = draftItems.some((draft: any) => {
            const stickers = Array.isArray(draft.stickers) ? draft.stickers.length : 0;
            const images = Array.isArray(draft.images) ? draft.images.length : 0;
            return draft.status === 'draft' && (Number(draft.sticker_count || 0) > 0 || stickers > 0 || images > 0);
        });
        if (hasUsableDraft) return { exists: true, location: 'drafts' };
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
    // Animated/video need more time: rendering frames + (mutex-serialised) FFmpeg encoding.
    // 4 minutes per sticker covers worst case (45 frames @ low CPU) without false timeouts.
    const timeoutMs = (sticker.is_animated || sticker.is_video) ? 240000 : 30000;
    let timeoutId: ReturnType<typeof setTimeout>;
    const result = await Promise.race([
        processTelegramStickerInner(botToken, sticker, packId, index, onProgress),
        new Promise<null>((resolve) => {
            timeoutId = setTimeout(() => {
                console.warn(`[TELEGRAM] Sticker #${index + 1} timed out after ${timeoutMs / 1000}s`);
                onProgress?.(`⏱️ Sticker #${index + 1} timed out, skipping...`);
                resolve(null);
            }, timeoutMs);
        }),
        ...(abortSignal ? [new Promise<null>((resolve) => {
            if (abortSignal.aborted) { resolve(null); return; }
            abortSignal.addEventListener('abort', () => {
                console.log(`[TELEGRAM] Sticker #${index + 1} aborted by user`);
                resolve(null);
            }, { once: true });
        })] : [])
    ]);
    clearTimeout(timeoutId!);
    return result;
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
            // Animated TGS → render frames via lottie-web canvas → FFmpeg animated WebP.
            // No retry/forceReload here: with mutex-serialised FFmpeg, a forceReload from one
            // sticker would terminate the WASM instance another sticker is mid-encoding on,
            // cascading every concurrent sticker into failure.
            onProgress?.(`Rendering animated sticker #${index + 1}...`);
            try {
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
            } catch (err: any) {
                console.error(`[TELEGRAM] Animated render failed for #${index + 1}:`, err.message);
                onProgress?.(`⚠️ Animated #${index + 1} failed, skipping...`);
                return null;
            }
        } else if (sticker.is_video) {
            onProgress?.(`Processing video sticker #${index + 1}...`);
            try {
                const filePath = await getFile(botToken, sticker.file_id);
                const blob = await downloadTelegramFile(botToken, filePath);
                const videoFile = new File([blob], `sticker_${index}.webm`, { type: 'video/webm' });
                webpBlob = await stickerProcessor.processAnimated(videoFile, (p) => {
                    onProgress?.(`Sticker #${index + 1}: ${p.message}`);
                });
                console.log(`[TELEGRAM] ✓ Video→WebP for #${index + 1}: ${Math.round(webpBlob.size / 1024)}KB`);
            } catch (err: any) {
                console.error(`[TELEGRAM] Video render failed for #${index + 1}:`, err.message);
                onProgress?.(`⚠️ Video #${index + 1} failed, skipping...`);
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
        const downloadURL = await uploadFile('draft_stickers', packId, 'images', webpBlob, fileName);

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
        const trayUrl = await uploadFile('draft_stickers', packId, 'tray_image', trayBlob, trayFileName);

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
    const cleanBotToken = normalizeBotToken(botToken);
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

            const stickerSet = await getStickerSet(cleanBotToken, setName);
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

                const animatedSource = chunk.filter(s => s.is_animated || s.is_video);
                const staticSource = chunk.filter(s => !s.is_animated && !s.is_video);
                const sourceSubPacks: Array<{ sourceStickers: TelegramSticker[]; isAnimated: boolean; suffix: string }> =
                    animatedSource.length > 0 && staticSource.length > 0
                        ? [
                            { sourceStickers: animatedSource, isAnimated: true, suffix: ' (Animated)' },
                            { sourceStickers: staticSource, isAnimated: false, suffix: ' (Static)' }
                        ]
                        : [{ sourceStickers: chunk, isAnimated: chunk.some(s => s.is_animated || s.is_video), suffix: '' }];

                if (sourceSubPacks.length > 1) {
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `🔀 Mixed pack detected: splitting into animated/static drafts`,
                        packName,
                        status: 'running',
                        completedPacks
                    });
                }

                for (const sourceSubPack of sourceSubPacks) {
                    if (sourceSubPack.sourceStickers.length === 0) continue;

                    const subPackName = packName + sourceSubPack.suffix;
                    const localizedNames = { ...partTranslations, name_en: subPackName };
                    const createdAt = new Date().toISOString();
                    const initialPackData: any = {
                        name: subPackName,
                        ...localizedNames,
                        publisher: 'Sticky',
                        publisher_email: 'contact@arain.digital',
                        privacy_policy_website: '',
                        license_agreement_website: '',
                        category,
                        is_premium: false,
                        is_animated: sourceSubPack.isAnimated,
                        download_count: 0,
                        fake_download_base: Math.floor(Math.random() * 7001) + 3000,
                        view_count: 0,
                        favorite_count: 0,
                        sticker_count: 0,
                        image_data_version: Date.now().toString(),
                        is_active: false,
                        stickers: [],
                        tray_url: '',
                        tray_image_file: '',
                        created_at: createdAt,
                        status: 'processing',
                        batch_generated: true,
                        batch_source: 'telegram',
                        batch_search_term: setName,
                        telegram_set_name: setName,
                        telegram_set_title: stickerSet.title,
                        ...(totalParts > 1 ? { telegram_part: partNum, telegram_total_parts: totalParts } : {})
                    };

                    const draftRecord = await pb.collection('draft_stickers').create(initialPackData);
                    const draftRecordId = draftRecord.id;

                    const processedStickers: Sticker[] = [];
                    const sourceList = sourceSubPack.sourceStickers;
                    // Animated stickers share a single mutex-serialised FFmpeg instance, so
                    // running them in parallel only piles them up in the encode queue while the
                    // per-sticker timeout keeps ticking — all of them time out. Serialise to 1.
                    const PARALLEL_BATCH = sourceSubPack.isAnimated ? 1 : 3;

                    for (let j = 0; j < sourceList.length; j += PARALLEL_BATCH) {
                        if (abortSignal?.aborted) break;
                        const batchEnd = Math.min(j + PARALLEL_BATCH, sourceList.length);
                        const batchSlice = sourceList.slice(j, batchEnd);

                        onProgress?.({
                            currentPack: i + 1,
                            totalPacks: packInputs.length,
                            currentStep: `${totalParts > 1 ? `[Part ${partNum}/${totalParts}] ` : ''}Processing sticker${PARALLEL_BATCH > 1 ? 's' : ''} ${j + 1}${batchEnd > j + 1 ? `-${batchEnd}` : ''}/${sourceList.length}`,
                            packName: subPackName,
                            stickerProgress: { current: j, total: sourceList.length },
                            status: 'running',
                            completedPacks
                        });

                        const results = await Promise.all(
                            batchSlice.map((s, bIdx) => {
                                const globalIdx = partIdx * stickerLimit + j + bIdx;
                                return processTelegramSticker(cleanBotToken, s, draftRecordId, globalIdx, (msg) => {
                                    onProgress?.({
                                        currentPack: i + 1,
                                        totalPacks: packInputs.length,
                                        currentStep: msg,
                                        packName: subPackName,
                                        stickerProgress: { current: j + bIdx, total: sourceList.length },
                                        status: 'running',
                                        completedPacks
                                    });
                                }, abortSignal);
                            })
                        );

                        results.forEach((sticker) => {
                            if (sticker) processedStickers.push(sticker);
                        });

                        onProgress?.({
                            currentPack: i + 1,
                            totalPacks: packInputs.length,
                            currentStep: `✓ ${processedStickers.length}/${sourceList.length} stickers done`,
                            packName: subPackName,
                            stickerProgress: { current: batchEnd, total: sourceList.length },
                            status: 'running',
                            completedPacks
                        });
                    }

                    if (processedStickers.length === 0) {
                        await pb.collection('draft_stickers').delete(draftRecordId).catch(() => {});
                        onProgress?.({
                            currentPack: i + 1,
                            totalPacks: packInputs.length,
                            currentStep: `⚠️ No stickers processed for "${subPackName}"`,
                            status: 'running',
                            completedPacks
                        });
                        continue;
                    }

                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `Creating cover for "${subPackName}"...`,
                        packName: subPackName,
                        status: 'running',
                        completedPacks
                    });

                    const { trayUrl, trayFile } = await createTrayFromSticker(processedStickers, draftRecordId);

                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `Saving "${subPackName}" to drafts...`,
                        packName: subPackName,
                        status: 'running',
                        completedPacks
                    });

                    const finalPackData: any = {
                        ...initialPackData,
                        is_active: true,
                        status: 'draft',
                        sticker_count: processedStickers.length,
                        image_data_version: Date.now().toString(),
                        stickers: processedStickers,
                        tray_url: trayUrl,
                        tray_image_file: trayFile
                    };

                    await pb.collection('draft_stickers').update(draftRecordId, finalPackData);

                    completedPacks.push({
                        id: draftRecordId,
                        name: subPackName,
                        stickerCount: processedStickers.length,
                        telegramName: setName
                    });

                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `✅ "${subPackName}" imported to drafts! (${processedStickers.length} ${sourceSubPack.isAnimated ? 'animated' : 'static'} stickers)`,
                        packName: subPackName,
                        status: 'running',
                        completedPacks
                    });

                    console.log(`[TELEGRAM] ✅ Imported to drafts: ${subPackName} (${processedStickers.length} ${sourceSubPack.isAnimated ? 'animated' : 'static'} stickers from @${setName})`);
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

    const setsCount = packInputs.length;
    const doneMsg = errors.length > 0
        ? `⚠️ Done! ${completedPacks.length} packs from ${setsCount} set(s) imported. Errors: ${errors.join(' | ')}`
        : `🎉 Done! ${completedPacks.length} packs from ${setsCount} set(s) imported successfully!`;

    onProgress?.({
        currentPack: setsCount,
        totalPacks: setsCount,
        currentStep: doneMsg,
        status: 'done',
        completedPacks
    });

    return completedPacks;
}

// ========== VALIDATE BOT TOKEN ==========

export async function validateBotToken(token: string): Promise<{ valid: boolean; botName?: string }> {
    try {
        const cleanToken = normalizeBotToken(token);
        if (!/^\d{6,}:[A-Za-z0-9_-]{20,}$/.test(cleanToken)) return { valid: false };
        const url = `${TELEGRAM_PROXY}?token=${encodeURIComponent(cleanToken)}&method=getMe`;
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
