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
    const response = await fetch(url);
    if (!response.ok) throw new Error(`Download failed: ${response.status}`);
    return await response.blob();
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
            renderer: 'svg',
            loop: false,
            autoplay: false,
            animationData: lottieData,
        });

        await new Promise<void>((resolve, reject) => {
            const timeout = setTimeout(() => reject(new Error('Lottie DOMLoaded timeout')), 15000);
            anim.addEventListener('DOMLoaded', () => { clearTimeout(timeout); resolve(); });
        });

        const totalFrames = anim.totalFrames;
        const originalFps = lottieData.fr || 60;
        const targetFps = 15;
        const frameStep = Math.max(1, Math.round(originalFps / targetFps));

        const w = lottieData.w || 512;
        const h = lottieData.h || 512;

        const svg = wrapper.querySelector('svg');
        if (!svg) throw new Error('Lottie SVG not found');
        svg.setAttribute('width', '512');
        svg.setAttribute('height', '512');
        if (!svg.getAttribute('viewBox')) {
            svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
        }

        const frames: Blob[] = [];
        const canvas = document.createElement('canvas');
        canvas.width = 512;
        canvas.height = 512;
        const ctx = canvas.getContext('2d')!;
        const totalOutputFrames = Math.ceil(totalFrames / frameStep);

        for (let f = 0; f < totalFrames; f += frameStep) {
            anim.goToAndStop(f, true);
            onProgress?.(`Rendering frame ${frames.length + 1}/${totalOutputFrames}...`);

            const svgData = new XMLSerializer().serializeToString(svg);
            const img = new Image();
            img.width = 512;
            img.height = 512;

            await new Promise<void>((resolve, reject) => {
                img.onload = () => resolve();
                img.onerror = () => reject(new Error('SVG rasterize failed'));
                img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgData);
            });

            ctx.clearRect(0, 0, 512, 512);
            ctx.drawImage(img, 0, 0, 512, 512);

            const blob = await new Promise<Blob>((resolve, reject) => {
                canvas.toBlob(
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
async function thumbnailFallback(
    botToken: string, sticker: TelegramSticker, index: number,
    onProgress?: (msg: string) => void
): Promise<Blob | null> {
    if (!sticker.thumbnail) return null;
    onProgress?.(`Fallback: thumbnail for #${index + 1}...`);
    const thumbPath = await getFile(botToken, sticker.thumbnail.file_id);
    const thumbBlob = await downloadTelegramFile(botToken, thumbPath);
    const img = new Image();
    const thumbUrl = URL.createObjectURL(thumbBlob);
    await new Promise<void>((resolve, reject) => {
        img.onload = () => resolve();
        img.onerror = () => reject(new Error('Image load failed'));
        img.src = thumbUrl;
    });
    const c = document.createElement('canvas');
    c.width = 512; c.height = 512;
    c.getContext('2d')!.drawImage(img, 0, 0, 512, 512);
    URL.revokeObjectURL(thumbUrl);
    return new Promise<Blob>((resolve, reject) => {
        c.toBlob(b => b ? resolve(b) : reject(new Error('Export failed')), 'image/webp', 0.95);
    });
}

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
    onProgress?: (msg: string) => void
): Promise<Sticker | null> {
    try {
        let webpBlob: Blob;
        console.log(`[TELEGRAM] Sticker #${index + 1}: animated=${sticker.is_animated}, video=${sticker.is_video}, thumb=${!!sticker.thumbnail}`);

        if (sticker.is_animated) {
            // Animated TGS → render ALL frames via lottie-web SVG → FFmpeg animated WebP
            onProgress?.(`Rendering animated sticker #${index + 1}...`);
            try {
                const filePath = await getFile(botToken, sticker.file_id);
                const blob = await downloadTelegramFile(botToken, filePath);
                const tgsBuffer = await blob.arrayBuffer();

                // Render all Lottie frames to PNG blobs
                const { frames, fps } = await renderTgsFrames(tgsBuffer, onProgress);
                console.log(`[TELEGRAM] TGS #${index + 1}: ${frames.length} frames @ ${fps}fps`);

                // Encode frames → animated WebP via FFmpeg
                onProgress?.(`Encoding animated WebP #${index + 1} (${frames.length} frames)...`);
                webpBlob = await stickerProcessor.processFromPngFrames(frames, fps, (p) => {
                    onProgress?.(`Sticker #${index + 1}: ${p.message}`);
                });
                console.log(`[TELEGRAM] ✓ Animated WebP for #${index + 1}: ${Math.round(webpBlob.size / 1024)}KB`);
            } catch (err: any) {
                console.warn(`[TELEGRAM] Animated render failed for #${index + 1}:`, err.message, '→ thumbnail fallback');
                const fb = await thumbnailFallback(botToken, sticker, index, onProgress);
                if (!fb) return null;
                webpBlob = fb;
            }
        } else if (sticker.is_video) {
            // Video WebM → FFmpeg → animated WebP
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
                console.warn(`[TELEGRAM] Video render failed for #${index + 1}:`, err.message, '→ thumbnail fallback');
                const fb = await thumbnailFallback(botToken, sticker, index, onProgress);
                if (!fb) return null;
                webpBlob = fb;
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
        splitPacks?: boolean;
        keepOriginalName?: boolean;
        onProgress?: (progress: TelegramImportProgress) => void;
    } = {}
): Promise<TelegramCompletedPack[]> {
    const {
        useAiNaming = false,
        useAiTranslation = false,
        stickerLimit = 30,
        splitPacks = true,
        keepOriginalName = true,
        onProgress
    } = options;
    const completedPacks: TelegramCompletedPack[] = [];
    const errors: string[] = [];
    const batchProcessedNames = new Set<string>(); // In-memory dedup within this batch

    for (let i = 0; i < packInputs.length; i++) {
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
            const isAnimatedPack = allStickers.some(s => s.is_animated || s.is_video);

            if (allStickers.length === 0) {
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: `⚠️ "${stickerSet.title}" has no stickers, skipping...`,
                    status: 'running',
                    completedPacks
                });
                continue;
            }

            // Step 2: Generate pack name
            let baseName = stickerSet.title;
            if (!keepOriginalName && useAiNaming && deepseekService.isConfigured()) {
                try {
                    const names = await deepseekService.generatePackNames(stickerSet.title, 3);
                    if (names.length > 0) {
                        baseName = `${names[0].name} ${names[0].emoji}`;
                    }
                } catch {
                    // Keep original Telegram name
                }
            }

            // Step 3: Split or truncate based on settings
            let stickersToProcess = allStickers;
            const chunks: TelegramSticker[][] = [];

            if (splitPacks) {
                // Split into multiple packs
                for (let c = 0; c < stickersToProcess.length; c += stickerLimit) {
                    chunks.push(stickersToProcess.slice(c, c + stickerLimit));
                }
            } else {
                // Truncate to limit
                chunks.push(stickersToProcess.slice(0, stickerLimit));
            }
            const totalParts = chunks.length;

            // Pre-translate once for the base name
            let translations: Record<string, string> = { name_en: baseName };
            if (useAiTranslation && deepseekService.isConfigured()) {
                try {
                    translations = await deepseekService.translatePackName(baseName);
                } catch {
                    translations = { name_en: baseName };
                }
            }

            const category = autoDetectCategory(stickerSet.title + ' ' + setName);

            // Process each chunk as a separate pack
            for (let partIdx = 0; partIdx < chunks.length; partIdx++) {
                const chunk = chunks[partIdx];
                const partNum = partIdx + 1;
                const packName = totalParts > 1 ? `${baseName} ${partNum}` : baseName;
                const packId = `tg_${setName.toLowerCase()}_${totalParts > 1 ? partNum + '_' : ''}${Date.now().toString(36)}`;

                // Add part suffix to translations if multi-part
                const partTranslations: Record<string, string> = {};
                for (const [key, val] of Object.entries(translations)) {
                    partTranslations[key] = totalParts > 1 ? `${val} ${partNum}` : val;
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

                // Process stickers in this chunk
                const processedStickers: Sticker[] = [];

                for (let j = 0; j < chunk.length; j++) {
                    onProgress?.({
                        currentPack: i + 1,
                        totalPacks: packInputs.length,
                        currentStep: `${totalParts > 1 ? `[Part ${partNum}/${totalParts}] ` : ''}Processing sticker ${j + 1}/${chunk.length}`,
                        packName,
                        stickerProgress: { current: j, total: chunk.length },
                        status: 'running',
                        completedPacks
                    });

                    const globalIdx = partIdx * stickerLimit + j;
                    const sticker = await processTelegramSticker(botToken, chunk[j], packId, globalIdx, (msg) => {
                        onProgress?.({
                            currentPack: i + 1,
                            totalPacks: packInputs.length,
                            currentStep: msg,
                            packName,
                            stickerProgress: { current: j, total: chunk.length },
                            status: 'running',
                            completedPacks
                        });
                    });

                    if (sticker) {
                        processedStickers.push(sticker);
                        onProgress?.({
                            currentPack: i + 1,
                            totalPacks: packInputs.length,
                            currentStep: `✓ Sticker ${processedStickers.length}/${chunk.length} uploaded`,
                            packName,
                            stickerProgress: { current: processedStickers.length, total: chunk.length },
                            status: 'running',
                            completedPacks
                        });
                    }
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

                // Create tray
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: `Creating cover for "${packName}"...`,
                    packName,
                    status: 'running',
                    completedPacks
                });

                const { trayUrl, trayFile } = await createTrayFromSticker(processedStickers, packId);

                // Save to Firestore
                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: 'Saving to database...',
                    packName,
                    status: 'running',
                    completedPacks
                });

                const packData: any = {
                    name: packName,
                    ...partTranslations,
                    publisher: 'Sticky Telegram',
                    publisher_email: 'contact@arain.digital',
                    privacy_policy_website: '',
                    license_agreement_website: '',
                    category,
                    is_premium: false,
                    is_animated: isAnimatedPack,
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
                    batch_source: 'telegram',
                    batch_search_term: setName,
                    telegram_set_name: setName,
                    telegram_set_title: stickerSet.title,
                    telegram_part: totalParts > 1 ? partNum : undefined,
                    telegram_total_parts: totalParts > 1 ? totalParts : undefined
                };

                await setDoc(doc(db, 'draft_stickers', packId), packData);

                completedPacks.push({
                    id: packId,
                    name: packName,
                    stickerCount: processedStickers.length,
                    telegramName: setName
                });

                onProgress?.({
                    currentPack: i + 1,
                    totalPacks: packInputs.length,
                    currentStep: `✅ "${packName}" imported! (${processedStickers.length} stickers)`,
                    packName,
                    status: 'running',
                    completedPacks
                });

                console.log(`[TELEGRAM] ✅ Imported: ${packName} (${processedStickers.length} stickers from @${setName})`);
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
