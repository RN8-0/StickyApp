import { FFmpeg } from '@ffmpeg/ffmpeg';
import { fetchFile, toBlobURL } from '@ffmpeg/util';
import { removeBackground } from '@imgly/background-removal';

// Blob URLs cached for the entire session — WASM is downloaded only once, all instances share it.
type FFmpegBlobURLs = { coreURL: string; wasmURL: string; classWorkerURL: string };
let _ffmpegBlobURLs: FFmpegBlobURLs | null = null;
let _ffmpegBlobLoadPromise: Promise<FFmpegBlobURLs> | null = null;

// Validated fetch: a 200 OK from the SPA fallback returns index.html, and toBlobURL would
// happily wrap that HTML in an "application/wasm" blob — FFmpeg.load() then silently hangs
// trying to JIT-compile HTML as WebAssembly. We sniff the bytes and reject anything that
// is not actual WebAssembly (magic header 0x00 0x61 0x73 0x6d) or JavaScript.
async function fetchValidatedBlob(url: string, kind: 'wasm' | 'js'): Promise<string> {
    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`HTTP ${resp.status} for ${url}`);
    const buffer = await resp.arrayBuffer();
    const bytes = new Uint8Array(buffer);
    if (kind === 'wasm') {
        if (bytes.length < 4 || bytes[0] !== 0x00 || bytes[1] !== 0x61 || bytes[2] !== 0x73 || bytes[3] !== 0x6d) {
            throw new Error(`Not a WASM file (got ${bytes.length}B starting with ${Array.from(bytes.slice(0, 4)).map(b => b.toString(16)).join(' ')})`);
        }
        return URL.createObjectURL(new Blob([buffer], { type: 'application/wasm' }));
    } else {
        // JS: reject HTML (starts with '<')
        if (bytes.length === 0 || bytes[0] === 0x3c /* '<' */) {
            throw new Error(`Not a JS file (got ${bytes.length}B, looks like HTML)`);
        }
        return URL.createObjectURL(new Blob([buffer], { type: 'text/javascript' }));
    }
}

function getFFmpegBlobURLs(): Promise<FFmpegBlobURLs> {
    if (_ffmpegBlobURLs) return Promise.resolve(_ffmpegBlobURLs);
    if (!_ffmpegBlobLoadPromise) {
        _ffmpegBlobLoadPromise = (async () => {
            // Local source removed: ffmpeg-core.wasm is not in public/, and the SPA
            // catch-all returns index.html with HTTP 200, fooling toBlobURL into wrapping
            // HTML as WASM. CDN-only is reliable.
            void toBlobURL; // keep import shape; we use fetchValidatedBlob below
            // Per-source timeout deliberately short (25s): the user's previous failure mode was
            // both CDNs hanging for the full window before falling through, accumulating ~130s
            // of dead time per import. Real CDN responses arrive in <5s when reachable; if 25s
            // isn't enough, the source is effectively unreachable and another won't help.
            //
            // classWorkerURL is passed as the *raw CDN URL* (not a blob), because the FFmpeg
            // worker.js does relative imports of "./const.js" and "./errors.js" — those only
            // resolve when the worker is loaded from its real origin. Loading worker.js from
            // a blob URL silently breaks those imports, the worker never posts READY, and
            // ffmpeg.load() hangs until our timeout fires (the symptom user hit: every animated
            // sticker fails with "FFmpeg init timeout").
            const sources = [
                { core: 'https://cdn.jsdelivr.net/npm/@ffmpeg/core@0.12.6/dist/esm/ffmpeg-core.js', wasm: 'https://cdn.jsdelivr.net/npm/@ffmpeg/core@0.12.6/dist/esm/ffmpeg-core.wasm', worker: 'https://cdn.jsdelivr.net/npm/@ffmpeg/ffmpeg@0.12.15/dist/esm/worker.js', timeout: 25000 },
                { core: 'https://unpkg.com/@ffmpeg/core@0.12.6/dist/esm/ffmpeg-core.js', wasm: 'https://unpkg.com/@ffmpeg/core@0.12.6/dist/esm/ffmpeg-core.wasm', worker: 'https://unpkg.com/@ffmpeg/ffmpeg@0.12.15/dist/esm/worker.js', timeout: 25000 },
            ];
            let lastError: Error | null = null;
            for (const { core, wasm, worker, timeout } of sources) {
                try {
                    const [coreURL, wasmURL] = await Promise.race([
                        Promise.all([
                            fetchValidatedBlob(core, 'js'),
                            fetchValidatedBlob(wasm, 'wasm'),
                        ]),
                        new Promise<never>((_, reject) => setTimeout(() => reject(new Error('fetch timeout')), timeout)),
                    ]);
                    _ffmpegBlobURLs = { coreURL, wasmURL, classWorkerURL: worker };
                    console.log('[FFmpeg] WASM ready from:', core, '(worker:', worker, ')');
                    return _ffmpegBlobURLs;
                } catch (err) {
                    lastError = err instanceof Error ? err : new Error(String(err));
                    console.warn('[FFmpeg] Failed from', core, ':', lastError.message);
                }
            }
            throw lastError || new Error('FFmpeg: all sources failed');
        })().catch(err => {
            _ffmpegBlobLoadPromise = null; // allow retry on next call
            throw err;
        });
    }
    return _ffmpegBlobLoadPromise;
}

async function loadFFmpegInstance(ffmpeg: FFmpeg): Promise<void> {
    const { coreURL, wasmURL, classWorkerURL } = await getFFmpegBlobURLs();
    // Blob URL already downloaded; this step is only WASM JIT compilation. Real init is <5s
    // on a healthy worker; 60s is a generous ceiling. Anything longer almost always means
    // the FFmpeg worker silently crashed (no error event), so retrying with a fresh instance
    // is more productive than waiting another two minutes on a dead worker.
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
        await Promise.race([
            ffmpeg.load({ coreURL, wasmURL, classWorkerURL }),
            new Promise<never>((_, reject) => {
                timer = setTimeout(() => reject(new Error('FFmpeg init timeout')), 60000);
            }),
        ]);
    } finally {
        if (timer) clearTimeout(timer);
    }
}

// NOTE: previously this file maintained a *second* FFmpeg WASM instance (`_tgsFFmpegReady`)
// behind a promise-chain mutex (`_tgsMutex`) just for TGS encoding. With PARALLEL_BATCH=1
// in the Telegram importer there is no concurrent encoding to protect against, and the
// dual-instance setup forced TGS imports to pay the WASM JIT compilation cost a *second*
// time on top of the StickerProcessor's main instance — which on the user's machine looked
// indistinguishable from a hang. We now reuse the single `this.ffmpeg` instance everywhere,
// matching the pre-migration behaviour that was confirmed working in commit da19c71.

export type StickerProgress = {
    message: string;
    percentage: number;
};

const STICKER_SIZE = 512;
const TRAY_SIZE = 512;
const MAX_DURATION = 5; // WhatsApp max 5 saniye
const MIN_FPS = 8; // Minimum fps
const MAX_FPS = 30; // WhatsApp max desteklenen fps
const DEFAULT_FPS = 15; // Varsayılan fps (fps belirlenemezse)
const MAX_STATIC_SIZE = 300 * 1024;
const MAX_ANIMATED_SIZE = 500 * 1024;

class StickerProcessor {
    private ffmpeg: FFmpeg | null = null;
    private isLoaded = false;
    private isLoading = false;
    private loadPromise: Promise<void> | null = null;

    /**
     * WebP dosyasının animasyonlu olup olmadığını kontrol et
     * VP8X chunk'ındaki animation flag veya ANIM/ANMF chunk varlığı kontrol edilir
     */
    async isAnimatedWebP(file: File): Promise<boolean> {
        const buffer = await file.arrayBuffer();
        const bytes = new Uint8Array(buffer);

        // RIFF header kontrolü
        if (bytes.length < 12) return false;
        const riff = String.fromCharCode(bytes[0], bytes[1], bytes[2], bytes[3]);
        const webp = String.fromCharCode(bytes[8], bytes[9], bytes[10], bytes[11]);
        if (riff !== 'RIFF' || webp !== 'WEBP') return false;

        // Chunk'ları tara
        let offset = 12;
        while (offset + 8 <= bytes.length) {
            const fourCC = String.fromCharCode(
                bytes[offset], bytes[offset + 1],
                bytes[offset + 2], bytes[offset + 3]
            );

            const chunkSize = bytes[offset + 4] |
                (bytes[offset + 5] << 8) |
                (bytes[offset + 6] << 16) |
                (bytes[offset + 7] << 24);

            // VP8X extended format - animation flag kontrolü (bit 1)
            if (fourCC === 'VP8X' && offset + 8 < bytes.length) {
                const flags = bytes[offset + 8];
                if (flags & 0x02) { // Animation flag
                    return true;
                }
            }

            // ANIM veya ANMF chunk'ı varsa animasyonludur
            if (fourCC === 'ANIM' || fourCC === 'ANMF') {
                return true;
            }

            offset += 8 + chunkSize + (chunkSize % 2);
        }

        return false;
    }

    async load() {
        if (this.isLoaded) return;
        if (this.isLoading && this.loadPromise) return this.loadPromise;
        this.isLoading = true;
        // Hold a reference to the in-flight instance outside the IIFE so the catch handler
        // can terminate it on failure. Without this, a timed-out ffmpeg.load() leaves the
        // underlying Worker running and we accumulate one orphan per failed sticker — at 15
        // stickers per pack the browser starves on workers and every retry fails fast.
        let fresh: FFmpeg | null = null;
        this.loadPromise = (async () => {
            fresh = new FFmpeg();
            await loadFFmpegInstance(fresh);
            this.ffmpeg = fresh;
            this.isLoaded = true;
            this.isLoading = false;
        })().catch((err) => {
            // Critical: a rejected loadPromise was being cached forever, so once the first
            // sticker hit a CDN timeout every subsequent sticker awaited the same rejection
            // and "failed" instantly. Reset the state machine so the *next* sticker can try
            // again with a clean instance.
            this.isLoaded = false;
            this.isLoading = false;
            this.loadPromise = null;
            try { fresh?.terminate(); } catch {}
            try { this.ffmpeg?.terminate(); } catch {}
            this.ffmpeg = null;
            throw err;
        });
        return this.loadPromise;
    }

    // Force reload FFmpeg (recover from crash)
    async forceReload() {
        this.isLoaded = false;
        this.isLoading = false;
        this.loadPromise = null;
        try { this.ffmpeg?.terminate(); } catch {}
        this.ffmpeg = null;
        return this.load();
    }

    // Uygulama açılır açılmaz çağrılacak - FFmpeg'i önceden yükle
    async preload() {
        await this.load();
    }

    /**
     * Static image processing
     */
    async processStatic(file: File, onProgress?: (p: StickerProgress) => void, removeBg: boolean = false): Promise<Blob> {
        let source: Blob = file;
        if (removeBg) {
            onProgress?.({ message: 'Removing background...', percentage: 20 });
            source = await removeBackground(file, {
                progress: (message: string) => {
                    onProgress?.({ message: `Removing background: ${message}`, percentage: 80 });
                }
            });
        } else {
            onProgress?.({ message: 'Optimizing sticker...', percentage: 70 });
        }

        onProgress?.({ message: 'Resizing...', percentage: 90 });
        return this.resizeAndCenter(source);
    }

    /**
     * GIF processing - FFmpeg direct WebP conversion
     */
    private async processGifFrames(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        onProgress?.({ message: 'Analyzing GIF...', percentage: 10 });

        return this.processGifWithFFmpeg(file, onProgress);
    }

    /**
     * FFmpeg GIF→WebP conversion - preserves original FPS and timing
     */
    private async processGifWithFFmpeg(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        const ffmpeg = this.ffmpeg!;
        const inputName = `input_${Date.now()}.gif`;
        const outputName = 'output.webp';

        onProgress?.({ message: 'Loading GIF...', percentage: 15 });
        await ffmpeg.writeFile(inputName, await fetchFile(file));

        onProgress?.({ message: 'Creating WebP...', percentage: 30 });

        let blob: Blob | null = null;

        // Dosya boyutuna göre kalite seçimi - BÜYÜK dosyalar için düşük kaliteden başla
        const fileSizeKB = file.size / 1024;
        let qualities: number[];

        if (fileSizeKB > 2000) {
            qualities = [15, 10, 5];
        } else if (fileSizeKB > 1000) {
            qualities = [25, 15, 10, 5];
        } else if (fileSizeKB > 500) {
            qualities = [35, 25, 15, 10];
        } else {
            qualities = [50, 35, 25, 15];
        }

        for (const q of qualities) {
            try { await ffmpeg.deleteFile(outputName); } catch { }

            // -vsync 0: Frame timing'i koru
            // -r 15: Çıkış framerate'i sabitle (Android uyumluluğu için)
            await ffmpeg.exec([
                '-i', inputName,
                '-t', MAX_DURATION.toString(),
                '-vf', `scale=${STICKER_SIZE}:${STICKER_SIZE}:force_original_aspect_ratio=decrease:flags=fast_bilinear,pad=${STICKER_SIZE}:${STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=black@0,fps=15`,
                '-c:v', 'libwebp',
                '-lossless', '0',
                '-q:v', q.toString(),
                '-pix_fmt', 'yuva420p',
                '-compression_level', '4',
                '-loop', '0',
                '-an',
                outputName
            ]);

            let data = await ffmpeg.readFile(outputName) as Uint8Array;
            data = this.patchWebPFrameDurations(data, 67); // 15fps = 67ms per frame
            blob = new Blob([new Uint8Array(data)], { type: 'image/webp' });

            if (blob.size <= MAX_ANIMATED_SIZE) {
                onProgress?.({ message: `Done! (${Math.round(blob.size / 1024)}KB)`, percentage: 100 });
                break;
            }

            onProgress?.({ message: `Compressing (q:${q})...`, percentage: 50 + Math.round((50 - q) / 45 * 40) });
        }

        // Cleanup
        try { await ffmpeg.deleteFile(inputName); } catch { }
        try { await ffmpeg.deleteFile(outputName); } catch { }

        if (!blob) {
            throw new Error('GIF processing failed');
        }

        return blob;
    }

    /**
     * Create WhatsApp-compatible animated WebP from frames
     */
    private async createWebPFromFrames(ffmpeg: FFmpeg, frameCount: number, onProgress?: (p: StickerProgress) => void, fps: number = DEFAULT_FPS): Promise<Blob> {
        const outputName = 'output.webp';
        const MAX_SIZE = 500 * 1024;

        onProgress?.({ message: 'Creating WebP...', percentage: 70 });

        let blob: Blob | null = null;

        // Single fast pass first; only retry at lower quality if the result is too big.
        // 4-pass quality loops at compression_level 4 made libwebp WASM run for >2 minutes per
        // sticker on the user's machine, blowing past the 240s outer timeout. compression_level 0
        // is dramatically faster and the size delta is small for short stickers.
        const qualities = frameCount > 50 ? [35, 20] : [55, 30];

        for (let qi = 0; qi < qualities.length; qi++) {
            const q = qualities[qi];
            try { await ffmpeg.deleteFile(outputName); } catch { }

            const actualFps = Math.min(fps, MAX_FPS);
            const frameDurationMs = Math.round(1000 / actualFps);

            onProgress?.({ message: `Encoding pass ${qi + 1}/${qualities.length} (q:${q})...`, percentage: 75 + qi * 10 });

            await ffmpeg.exec([
                '-framerate', actualFps.toString(),
                '-i', 'frame_%04d.png',
                '-vf', `scale=${STICKER_SIZE}:${STICKER_SIZE}:force_original_aspect_ratio=decrease:flags=fast_bilinear,pad=${STICKER_SIZE}:${STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=black@0`,
                '-t', MAX_DURATION.toString(),
                '-c:v', 'libwebp',
                '-lossless', '0',
                '-q:v', q.toString(),
                '-pix_fmt', 'yuva420p',
                '-compression_level', '0',
                '-loop', '0',
                '-an',
                outputName
            ]);

            let data = await ffmpeg.readFile(outputName) as Uint8Array;
            data = this.patchWebPFrameDurations(data, frameDurationMs);
            blob = new Blob([data as any], { type: 'image/webp' });

            if (blob.size <= MAX_SIZE) break;
        }

        onProgress?.({ message: `Done! (${Math.round(blob!.size / 1024)}KB)`, percentage: 100 });
        return blob!;
    }

    /**
     * Patch ANMF frame durations and disposal flags in animated WebP
     * ANMF yapısı: x(3) + y(3) + w(3) + h(3) + duration(3) + flags(1) = 16 bytes header
     * Duration 3 byte little-endian, milisaniye cinsindendir
     */
    private patchWebPFrameDurations(webpBytes: Uint8Array, targetDurationMs: number): Uint8Array {
        const result = new Uint8Array(webpBytes);
        let offset = 12; // RIFF header'ı atla

        while (offset + 8 <= result.length) {
            const fourCC = String.fromCharCode(
                result[offset], result[offset + 1],
                result[offset + 2], result[offset + 3]
            );
            const chunkSize = result[offset + 4] |
                (result[offset + 5] << 8) |
                (result[offset + 6] << 16) |
                (result[offset + 7] << 24);

            if (fourCC === 'ANMF') {
                const payloadOffset = offset + 8;

                // Duration offset: x(3) + y(3) + w(3) + h(3) = 12 bytes
                const durationOffset = payloadOffset + 12;
                if (durationOffset + 3 <= result.length) {
                    // Duration'ı 3 byte little-endian olarak yaz
                    result[durationOffset] = targetDurationMs & 0xFF;
                    result[durationOffset + 1] = (targetDurationMs >> 8) & 0xFF;
                    result[durationOffset + 2] = (targetDurationMs >> 16) & 0xFF;
                }

                // Flags offset: duration'dan sonra (12 + 3 = 15)
                const flagsOffset = payloadOffset + 15;
                if (flagsOffset < result.length) {
                    // Disposal: 1 (dispose to background), Blending: 0 (alpha blend)
                    result[flagsOffset] = (result[flagsOffset] & 0xFC) | 0x01;
                }
            }

            offset += 8 + chunkSize + (chunkSize % 2);
        }

        return result;
    }

    /**
     * Get video duration in seconds
     */
    private getVideoDuration(file: File): Promise<number> {
        return new Promise((resolve, reject) => {
            const video = document.createElement('video');
            video.preload = 'metadata';
            video.onloadedmetadata = () => {
                URL.revokeObjectURL(video.src);
                resolve(video.duration);
            };
            video.onerror = () => {
                URL.revokeObjectURL(video.src);
                reject(new Error('Could not get video duration'));
            };
            video.src = URL.createObjectURL(file);
        });
    }

    /**
     * Video processing - preserves original FPS, max 5 seconds
     */
    private async processVideo(file: File, onProgress?: (p: StickerProgress) => void, removeBg: boolean = false): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;
        const outputName = 'output.webp';

        onProgress?.({ message: 'Analyzing video...', percentage: 10 });

        // Get original video duration
        let videoDuration = MAX_DURATION;
        try {
            videoDuration = await this.getVideoDuration(file);
            console.log(`Original video duration: ${videoDuration}s`);
        } catch (e) {
            console.warn('Could not get video duration, using default:', e);
        }

        // WhatsApp limiti (5sn) ile sınırla
        const processingDuration = Math.min(videoDuration, MAX_DURATION);

        const ext = file.name.split('.').pop()?.toLowerCase() || 'mp4';
        const inputName = `input_${Date.now()}.${ext}`;
        await ffmpeg.writeFile(inputName, await fetchFile(file));

        // If background removal is needed, process frame-by-frame
        if (removeBg) {
            onProgress?.({ message: 'Extracting frames...', percentage: 20 });

            // Extract frames at reduced FPS for speed
            const extractionFps = 15;

            await ffmpeg.exec([
                '-i', inputName,
                '-t', processingDuration.toString(),
                '-r', extractionFps.toString(), // FPS DÜŞÜRME (HIZ İÇİN)
                '-vf', 'scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=black@0',
                'frame_%04d.png'
            ]);

            const files = await ffmpeg.listDir('.');
            const frames = files.filter(f => f.name.startsWith('frame_') && f.name.endsWith('.png'))
                .sort((a, b) => a.name.localeCompare(b.name));

            const frameCount = frames.length;

            // FPS is now extractionFps (15)
            let detectedFps = extractionFps;

            // Apply FPS limits
            detectedFps = Math.max(MIN_FPS, Math.min(MAX_FPS, detectedFps));

            console.log(`Video processing: ${frameCount} frames, ${processingDuration}s duration -> Detected FPS: ${detectedFps}`);

            onProgress?.({ message: `Removing background (${detectedFps} fps)...`, percentage: 25 });

            // Process frames in parallel batches
            const batchSize = 2;
            for (let i = 0; i < frameCount; i += batchSize) {
                const batch = frames.slice(i, i + batchSize);

                await Promise.all(batch.map(async (frame) => {
                    const frameName = frame.name;
                    const frameData = await ffmpeg.readFile(frameName);
                    const frameBlob = new Blob([frameData as any], { type: 'image/png' });

                    try {
                        const processedBlob = await removeBackground(frameBlob, {
                            model: 'isnet',
                            progress: () => { }
                        });
                        await ffmpeg.writeFile(frameName, await fetchFile(processedBlob));
                    } catch (err) {
                        console.error("Error:", err);
                    }
                }));

                onProgress?.({
                    message: `Processing: ${Math.round((Math.min(i + batchSize, frameCount) / frameCount) * 100)}%`,
                    percentage: 25 + Math.round((Math.min(i + batchSize, frameCount) / frameCount) * 55)
                });

                await new Promise(resolve => setTimeout(resolve, 20));
            }

            // 3. Create WebP with detected fps
            return this.createWebPFromFrames(ffmpeg, frameCount, onProgress, detectedFps);
        }

        // No background removal - FAST method
        let blob: Blob | null = null;

        // Dosya boyutuna göre kalite seçimi — start high for best quality
        const fileSizeKB = file.size / 1024;
        let qualities: number[];

        if (fileSizeKB > 2000) {
            qualities = [30, 20, 15, 10];
        } else if (fileSizeKB > 1000) {
            qualities = [50, 35, 25, 15];
        } else if (fileSizeKB > 500) {
            qualities = [65, 50, 35, 25];
        } else {
            qualities = [75, 65, 50, 35];
        }

        for (const q of qualities) {
            onProgress?.({ message: `Creating WebP (q:${q})...`, percentage: 50 });

            try { await ffmpeg.deleteFile(outputName); } catch { }

            // Fixed 15fps output
            await ffmpeg.exec([
                '-i', inputName,
                '-t', processingDuration.toString(),
                '-vf', `format=rgba,scale=${STICKER_SIZE}:${STICKER_SIZE}:force_original_aspect_ratio=decrease:flags=fast_bilinear,pad=${STICKER_SIZE}:${STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=black@0,fps=15`,
                '-c:v', 'libwebp',
                '-lossless', '0',
                '-q:v', q.toString(),
                '-pix_fmt', 'yuva420p',
                '-compression_level', '4',
                '-loop', '0',
                '-an',
                outputName
            ]);

            let data = await ffmpeg.readFile(outputName) as Uint8Array;
            data = this.patchWebPFrameDurations(data, 67); // 15fps = 67ms
            blob = new Blob([new Uint8Array(data)], { type: 'image/webp' });

            if (blob.size <= MAX_ANIMATED_SIZE) break;
        }

        try { await ffmpeg.deleteFile(outputName); } catch { }
        try { await ffmpeg.deleteFile(inputName); } catch { }

        onProgress?.({ message: `Done! (${Math.round(blob!.size / 1024)}KB)`, percentage: 100 });
        return blob!;
    }

    /**
     * Animated image processing (GIF/MP4)
     */
    async processAnimated(file: File, onProgress?: (p: StickerProgress) => void, removeBg: boolean = false): Promise<Blob> {
        const isGif = file.type === 'image/gif' || file.name.toLowerCase().endsWith('.gif');

        if (isGif && !removeBg) {
            // GIF: Arka plan silinmeyecekse hızlı canvas işlemi
            return this.processGifFrames(file, onProgress);
        } else {
            // Video veya Arka planı silinecek GIF
            return this.processVideo(file, onProgress, removeBg);
        }
    }

    /**
     * Create animated WebP from PNG frame blobs (for TGS import)
     */
    async processFromPngFrames(pngBlobs: Blob[], fps: number, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;
        onProgress?.({ message: `Writing ${pngBlobs.length} frames...`, percentage: 40 });

        for (let i = 0; i < pngBlobs.length; i++) {
            const frameName = `frame_${i.toString().padStart(4, '0')}.png`;
            await ffmpeg.writeFile(frameName, await fetchFile(pngBlobs[i]));
        }

        try {
            return await this.createWebPFromFrames(ffmpeg, pngBlobs.length, onProgress, fps);
        } finally {
            try {
                const files = await ffmpeg.listDir('.');
                await Promise.all(
                    files
                        .filter((f: { name: string }) => f.name.startsWith('frame_') || f.name === 'output.webp')
                        .map((f: { name: string }) => ffmpeg.deleteFile(f.name).catch(() => {}))
                );
            } catch {}
        }
    }

    /**
     * Static WebP processing - resize to 512x512 with Canvas
     */
    async processStaticWebP(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        onProgress?.({ message: 'Reading WebP...', percentage: 20 });

        return new Promise((resolve) => {
            const img = new Image();
            img.onload = async () => {
                onProgress?.({ message: 'Resizing...', percentage: 40 });

                const canvas = document.createElement('canvas');
                canvas.width = STICKER_SIZE;
                canvas.height = STICKER_SIZE;
                const ctx = canvas.getContext('2d', { alpha: true })!;

                // Transparent background
                ctx.clearRect(0, 0, STICKER_SIZE, STICKER_SIZE);

                // Center while preserving aspect ratio
                const scale = Math.min(STICKER_SIZE / img.width, STICKER_SIZE / img.height);
                const nw = img.width * scale;
                const nh = img.height * scale;
                const nx = (STICKER_SIZE - nw) / 2;
                const ny = (STICKER_SIZE - nh) / 2;

                ctx.drawImage(img, nx, ny, nw, nh);
                URL.revokeObjectURL(img.src);

                onProgress?.({ message: 'Creating WebP...', percentage: 60 });

                // Quality loop - try until under 100KB
                let result: Blob | null = null;
                for (const quality of [1.0, 0.95, 0.9, 0.85, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3]) {
                    result = await new Promise<Blob | null>(res => {
                        canvas.toBlob(res, 'image/webp', quality);
                    });

                    if (result && result.size <= MAX_STATIC_SIZE) {
                        onProgress?.({ message: `Done! (${Math.round(result.size / 1024)}KB)`, percentage: 100 });
                        resolve(result);
                        return;
                    }
                    onProgress?.({ message: `Optimizing (q:${Math.round(quality * 100)})...`, percentage: 60 + Math.round((1 - quality) * 50) });
                }

                // Last resort - lowest quality
                onProgress?.({ message: `Done! (${Math.round(result!.size / 1024)}KB)`, percentage: 100 });
                resolve(result || file);
            };
            img.onerror = () => {
                URL.revokeObjectURL(img.src);
                onProgress?.({ message: 'Error: Could not read WebP', percentage: 100 });
                resolve(file);
            };
            img.src = URL.createObjectURL(file);
        });
    }

    /**
     * Animated WebP processing - extracts all frames
     */
    async processAnimatedWebP(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;

        onProgress?.({ message: 'Analyzing animated WebP...', percentage: 10 });

        if (!('ImageDecoder' in window)) {
            throw new Error('Your browser does not support animated WebP processing');
        }

        const decoder = new (window as any).ImageDecoder({
            data: file.stream(),
            type: 'image/webp'
        });

        await decoder.tracks.ready;
        const totalFrames = decoder.tracks.selectedTrack.frameCount;

        // WebP fps - play all frames within duration
        const estimatedDuration = totalFrames / DEFAULT_FPS;
        let outputFps: number;

        if (estimatedDuration <= MAX_DURATION) {
            outputFps = DEFAULT_FPS;
        } else {
            // Increase fps if duration is too long
            outputFps = Math.ceil(totalFrames / MAX_DURATION);
            outputFps = Math.max(MIN_FPS, Math.min(MAX_FPS, outputFps));
        }

        onProgress?.({ message: `Processing ${totalFrames} frames (${outputFps} fps)...`, percentage: 15 });

        const outCanvas = document.createElement('canvas');
        outCanvas.width = STICKER_SIZE;
        outCanvas.height = STICKER_SIZE;
        const outCtx = outCanvas.getContext('2d', { alpha: true })!;

        for (let i = 0; i < totalFrames; i++) {
            const result = await decoder.decode({ frameIndex: i });
            const frame = result.image;

            const scale = Math.min(STICKER_SIZE / frame.displayWidth, STICKER_SIZE / frame.displayHeight);
            const scaledW = Math.round(frame.displayWidth * scale);
            const scaledH = Math.round(frame.displayHeight * scale);
            const offsetX = Math.round((STICKER_SIZE - scaledW) / 2);
            const offsetY = Math.round((STICKER_SIZE - scaledH) / 2);

            outCtx.clearRect(0, 0, STICKER_SIZE, STICKER_SIZE);
            outCtx.drawImage(frame, offsetX, offsetY, scaledW, scaledH);

            const pngBlob = await new Promise<Blob>((res) => outCanvas.toBlob((b) => res(b!), 'image/png'));
            await ffmpeg.writeFile(`frame_${i.toString().padStart(4, '0')}.png`, await fetchFile(pngBlob));

            frame.close();
            onProgress?.({ message: `Frame ${i + 1}/${totalFrames}...`, percentage: 15 + Math.round(((i + 1) / totalFrames) * 50) });
        }

        decoder.close();
        return this.createWebPFromFrames(ffmpeg, totalFrames, onProgress, outputFps);
    }

    /**
     * Tray image processing
     */
    async processTray(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        onProgress?.({ message: 'Resizing...', percentage: 50 });
        return this.resizeAndCenterTray(file);
    }

    private async resizeAndCenterTray(blob: Blob): Promise<Blob> {
        return new Promise((resolve) => {
            const img = new Image();
            img.onload = () => {
                const canvas = document.createElement('canvas');
                canvas.width = TRAY_SIZE;
                canvas.height = TRAY_SIZE;
                const ctx = canvas.getContext('2d', { alpha: true })!;
                ctx.clearRect(0, 0, TRAY_SIZE, TRAY_SIZE);

                const scale = Math.min(TRAY_SIZE / img.width, TRAY_SIZE / img.height);
                const nw = img.width * scale;
                const nh = img.height * scale;
                const nx = (TRAY_SIZE - nw) / 2;
                const ny = (TRAY_SIZE - nh) / 2;

                ctx.drawImage(img, nx, ny, nw, nh);
                canvas.toBlob((result) => resolve(result!), 'image/png');
            };
            img.src = URL.createObjectURL(blob);
        });
    }

    private async resizeAndCenter(blob: Blob): Promise<Blob> {
        const MAX_STATIC_SIZE = 300 * 1024;

        return new Promise((resolve) => {
            const img = new Image();
            img.onload = async () => {
                const canvas = document.createElement('canvas');
                canvas.width = STICKER_SIZE;
                canvas.height = STICKER_SIZE;
                const ctx = canvas.getContext('2d', { alpha: true })!;
                ctx.clearRect(0, 0, STICKER_SIZE, STICKER_SIZE);

                const scale = Math.min(STICKER_SIZE / img.width, STICKER_SIZE / img.height);
                const nw = img.width * scale;
                const nh = img.height * scale;
                const nx = (STICKER_SIZE - nw) / 2;
                const ny = (STICKER_SIZE - nh) / 2;

                ctx.drawImage(img, nx, ny, nw, nh);

                let result = await new Promise<Blob | null>(res => {
                    canvas.toBlob(res, 'image/webp', 1.0);
                });

                if (result && result.size <= MAX_STATIC_SIZE) {
                    resolve(result);
                    return;
                }

                let quality = 0.9;
                while (quality >= 0.3) {
                    result = await new Promise<Blob | null>(res => {
                        canvas.toBlob(res, 'image/webp', quality);
                    });
                    if (result && result.size <= MAX_STATIC_SIZE) break;
                    quality -= 0.1;
                }

                resolve(result || blob);
            };
            img.src = URL.createObjectURL(blob);
        });
    }
}

export const stickerProcessor = new StickerProcessor();
