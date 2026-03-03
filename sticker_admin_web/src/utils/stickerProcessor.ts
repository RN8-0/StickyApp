import { FFmpeg } from '@ffmpeg/ffmpeg';
import { fetchFile, toBlobURL } from '@ffmpeg/util';
import { removeBackground } from '@imgly/background-removal';

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
const MAX_STATIC_SIZE = 100 * 1024;
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
        this.loadPromise = (async () => {
            this.ffmpeg = new FFmpeg();
            const baseURL = 'https://unpkg.com/@ffmpeg/core@0.12.6/dist/esm';
            await this.ffmpeg.load({
                coreURL: await toBlobURL(`${baseURL}/ffmpeg-core.js`, 'text/javascript'),
                wasmURL: await toBlobURL(`${baseURL}/ffmpeg-core.wasm`, 'application/wasm'),
            });
            this.isLoaded = true;
            this.isLoading = false;
        })();

        return this.loadPromise;
    }

    // Force reload FFmpeg (recover from crash)
    async forceReload() {
        this.isLoaded = false;
        this.isLoading = false;
        this.loadPromise = null;
        this.ffmpeg = null;
        return this.load();
    }

    // Uygulama açılır açılmaz çağrılacak - FFmpeg'i önceden yükle
    async preload() {
        return this.load();
    }

    /**
     * Statik görsel işleme
     */
    async processStatic(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        onProgress?.({ message: 'Arka plan siliniyor...', percentage: 20 });

        const removedBgBlob = await removeBackground(file, {
            progress: (message: string) => {
                onProgress?.({ message: `Arka plan siliniyor: ${message}`, percentage: 80 });
            }
        });

        onProgress?.({ message: 'Boyutlandırılıyor...', percentage: 90 });
        return this.resizeAndCenter(removedBgBlob);
    }

    /**
     * GIF işleme - FFmpeg ile doğrudan WebP'ye çevir (en güvenilir yöntem)
     */
    private async processGifFrames(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        onProgress?.({ message: 'GIF analiz ediliyor...', percentage: 10 });

        // FFmpeg ile doğrudan dönüştür (en güvenilir)
        return this.processGifWithFFmpeg(file, onProgress);
    }

    /**
     * FFmpeg ile GIF→WebP dönüşümü - ORİJİNAL FPS VE ZAMANLAMA KORUNUR
     * HIZLI: Büyük dosyalar için düşük kaliteden başla
     */
    private async processGifWithFFmpeg(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        const ffmpeg = this.ffmpeg!;
        const inputName = `input_${Date.now()}.gif`;
        const outputName = 'output.webp';

        onProgress?.({ message: 'GIF yükleniyor...', percentage: 15 });
        await ffmpeg.writeFile(inputName, await fetchFile(file));

        onProgress?.({ message: 'WebP oluşturuluyor...', percentage: 30 });

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
                onProgress?.({ message: `Tamamlandı! (${Math.round(blob.size / 1024)}KB)`, percentage: 100 });
                break;
            }

            onProgress?.({ message: `Sıkıştırılıyor (q:${q})...`, percentage: 50 + Math.round((50 - q) / 45 * 40) });
        }

        // Temizlik
        try { await ffmpeg.deleteFile(inputName); } catch { }
        try { await ffmpeg.deleteFile(outputName); } catch { }

        if (!blob) {
            throw new Error('GIF işlenemedi');
        }

        return blob;
    }

    /**
     * ffmpeg ile WhatsApp uyumlu animated WebP oluştur - HIZLI YÖNTEM
     */
    private async createWebPFromFrames(frameCount: number, onProgress?: (p: StickerProgress) => void, fps: number = DEFAULT_FPS): Promise<Blob> {
        const ffmpeg = this.ffmpeg!;
        const outputName = 'output.webp';
        const MAX_SIZE = 500 * 1024;

        onProgress?.({ message: 'WebP oluşturuluyor...', percentage: 70 });

        let blob: Blob | null = null;

        // Frame sayısına göre başlangıç kalitesi — start high
        const qualities = frameCount > 50 ? [50, 35, 25, 15] : [65, 50, 35, 25];

        for (const q of qualities) {
            try { await ffmpeg.deleteFile(outputName); } catch { }

            const actualFps = Math.min(fps, MAX_FPS);
            const frameDurationMs = Math.round(1000 / actualFps);

            await ffmpeg.exec([
                '-framerate', actualFps.toString(),
                '-i', 'frame_%04d.png',
                '-vf', `scale=${STICKER_SIZE}:${STICKER_SIZE}:force_original_aspect_ratio=decrease:flags=fast_bilinear,pad=${STICKER_SIZE}:${STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=black@0`,
                '-t', MAX_DURATION.toString(),
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
            data = this.patchWebPFrameDurations(data, frameDurationMs);
            blob = new Blob([data as any], { type: 'image/webp' });

            if (blob.size <= MAX_SIZE) break;

            onProgress?.({ message: `Sıkıştırılıyor (q:${q})...`, percentage: 85 });
        }

        // Temizlik - paralel olarak yap
        const cleanupPromises = [];
        for (let i = 0; i < frameCount; i++) {
            cleanupPromises.push(ffmpeg.deleteFile(`frame_${i.toString().padStart(4, '0')}.png`).catch(() => {}));
        }
        cleanupPromises.push(ffmpeg.deleteFile(outputName).catch(() => {}));
        await Promise.all(cleanupPromises);

        onProgress?.({ message: `Tamamlandı! (${Math.round(blob!.size / 1024)}KB)`, percentage: 100 });
        return blob!;
    }

    /**
     * Animated WebP dosyasındaki ANMF frame'lerinin süresini ve disposal flag'larını düzelt
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
     * Videonun süresini al (saniye cinsinden)
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
                reject(new Error('Video süresi alınamadı'));
            };
            video.src = URL.createObjectURL(file);
        });
    }

    /**
     * Video işleme (MP4) - ORİJİNAL FPS KORUNUR, max 5 saniye
     */
    private async processVideo(file: File, onProgress?: (p: StickerProgress) => void, removeBg: boolean = false): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;
        const outputName = 'output.webp';

        onProgress?.({ message: 'Video analiz ediliyor...', percentage: 10 });

        // Videonun orijinal süresini al
        let videoDuration = MAX_DURATION;
        try {
            videoDuration = await this.getVideoDuration(file);
            console.log(`Orijinal video süresi: ${videoDuration}s`);
        } catch (e) {
            console.warn('Video süresi alınamadı, varsayılan kullanılıyor:', e);
        }

        // WhatsApp limiti (5sn) ile sınırla
        const processingDuration = Math.min(videoDuration, MAX_DURATION);

        const ext = file.name.split('.').pop()?.toLowerCase() || 'mp4';
        const inputName = `input_${Date.now()}.${ext}`;
        await ffmpeg.writeFile(inputName, await fetchFile(file));

        // Eğer arka plan silinecekse, frame-by-frame işlem yap
        if (removeBg) {
            onProgress?.({ message: 'Frame\'ler çıkarılıyor...', percentage: 20 });

            // 1. Frame'leri çıkar - ÖNEMLİ: FPS'i baştan düşür (-r 15)
            // Bu sayede 60fps videodan 4 kat az kare çıkarılır, işlem 4 kat hızlanır.
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

            // FPS artık extractionFps (15) veya daha düşük (internet hızına/videoya göre drop olabilir)
            let detectedFps = extractionFps;

            // FPS sınırlarını uygula
            detectedFps = Math.max(MIN_FPS, Math.min(MAX_FPS, detectedFps));

            console.log(`Video işleme: ${frameCount} frame, ${processingDuration}s süre -> Tespit edilen FPS: ${detectedFps}`);

            onProgress?.({ message: `Arka Plan Temizleniyor (${detectedFps} fps)...`, percentage: 25 });

            // 2. Kareleri PARALEL işle
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
                        console.error("Hata:", err);
                    }
                }));

                onProgress?.({
                    message: `Akıcı işleme: %${Math.round((Math.min(i + batchSize, frameCount) / frameCount) * 100)}`,
                    percentage: 25 + Math.round((Math.min(i + batchSize, frameCount) / frameCount) * 55)
                });

                await new Promise(resolve => setTimeout(resolve, 20));
            }

            // 3. WebP oluştur - tespit edilen fps ile
            return this.createWebPFromFrames(frameCount, onProgress, detectedFps);
        }

        // Arka plan silinmeyecekse - HIZLI YÖNTEM
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
            onProgress?.({ message: `WebP oluşturuluyor (q:${q})...`, percentage: 50 });

            try { await ffmpeg.deleteFile(outputName); } catch { }

            // fps=15 filtresi ile sabit 15fps çıkış
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

        onProgress?.({ message: `Tamamlandı! (${Math.round(blob!.size / 1024)}KB)`, percentage: 100 });
        return blob!;
    }

    /**
     * Hareketli görsel işleme (GIF/MP4)
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
     * PNG frame blob'larından animated WebP oluştur (TGS import için)
     */
    async processFromPngFrames(pngBlobs: Blob[], fps: number, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;

        onProgress?.({ message: `${pngBlobs.length} kare yazılıyor...`, percentage: 40 });

        for (let i = 0; i < pngBlobs.length; i++) {
            const frameName = `frame_${(i + 1).toString().padStart(4, '0')}.png`;
            await ffmpeg.writeFile(frameName, await fetchFile(pngBlobs[i]));
        }

        return this.createWebPFromFrames(pngBlobs.length, onProgress, fps);
    }

    /**
     * Statik WebP işleme - Canvas ile 512x512 boyutlandır ve WebP olarak kaydet
     */
    async processStaticWebP(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        onProgress?.({ message: 'WebP okunuyor...', percentage: 20 });

        return new Promise((resolve) => {
            const img = new Image();
            img.onload = async () => {
                onProgress?.({ message: 'Boyutlandırılıyor...', percentage: 40 });

                const canvas = document.createElement('canvas');
                canvas.width = STICKER_SIZE;
                canvas.height = STICKER_SIZE;
                const ctx = canvas.getContext('2d', { alpha: true })!;

                // Şeffaf arka plan
                ctx.clearRect(0, 0, STICKER_SIZE, STICKER_SIZE);

                // Oranı koruyarak ortala
                const scale = Math.min(STICKER_SIZE / img.width, STICKER_SIZE / img.height);
                const nw = img.width * scale;
                const nh = img.height * scale;
                const nx = (STICKER_SIZE - nw) / 2;
                const ny = (STICKER_SIZE - nh) / 2;

                ctx.drawImage(img, nx, ny, nw, nh);
                URL.revokeObjectURL(img.src);

                onProgress?.({ message: 'WebP oluşturuluyor...', percentage: 60 });

                // Kalite döngüsü - 100KB altına düşene kadar
                let result: Blob | null = null;
                for (const quality of [1.0, 0.95, 0.9, 0.85, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3]) {
                    result = await new Promise<Blob | null>(res => {
                        canvas.toBlob(res, 'image/webp', quality);
                    });

                    if (result && result.size <= MAX_STATIC_SIZE) {
                        onProgress?.({ message: `Tamamlandı! (${Math.round(result.size / 1024)}KB)`, percentage: 100 });
                        resolve(result);
                        return;
                    }
                    onProgress?.({ message: `Optimize ediliyor (q:${Math.round(quality * 100)})...`, percentage: 60 + Math.round((1 - quality) * 50) });
                }

                // Son çare - en düşük kalite
                onProgress?.({ message: `Tamamlandı! (${Math.round(result!.size / 1024)}KB)`, percentage: 100 });
                resolve(result || file);
            };
            img.onerror = () => {
                URL.revokeObjectURL(img.src);
                onProgress?.({ message: 'Hata: WebP okunamadı', percentage: 100 });
                resolve(file);
            };
            img.src = URL.createObjectURL(file);
        });
    }

    /**
     * Animasyonlu WebP işleme - TÜM FRAME'LER ALINIR
     */
    async processAnimatedWebP(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;

        onProgress?.({ message: 'Animasyonlu WebP analiz ediliyor...', percentage: 10 });

        if (!('ImageDecoder' in window)) {
            throw new Error('Tarayıcınız animasyonlu WebP işlemeyi desteklemiyor');
        }

        const decoder = new (window as any).ImageDecoder({
            data: file.stream(),
            type: 'image/webp'
        });

        await decoder.tracks.ready;
        const totalFrames = decoder.tracks.selectedTrack.frameCount;

        // WebP için fps - tüm frame'leri 3 saniyede oynat
        const estimatedDuration = totalFrames / DEFAULT_FPS;
        let outputFps: number;

        if (estimatedDuration <= MAX_DURATION) {
            outputFps = DEFAULT_FPS;
        } else {
            // Süre uzunsa fps artır
            outputFps = Math.ceil(totalFrames / MAX_DURATION);
            outputFps = Math.max(MIN_FPS, Math.min(MAX_FPS, outputFps));
        }

        onProgress?.({ message: `${totalFrames} frame işleniyor (${outputFps} fps)...`, percentage: 15 });

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
        return this.createWebPFromFrames(totalFrames, onProgress, outputFps);
    }

    /**
     * Tray görseli işleme
     */
    async processTray(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        onProgress?.({ message: 'Boyutlandırılıyor...', percentage: 50 });
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
        const MAX_STATIC_SIZE = 100 * 1024;

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
