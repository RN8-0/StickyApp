import { FFmpeg } from '@ffmpeg/ffmpeg';
import { fetchFile, toBlobURL } from '@ffmpeg/util';
import { removeBackground } from '@imgly/background-removal';
import { parseGIF, decompressFrames } from 'gifuct-js';

export type StickerProgress = {
    message: string;
    percentage: number;
};

const STICKER_SIZE = 512;
const TRAY_SIZE = 512;
const MAX_DURATION = 3;
const MAX_FRAMES = 30;
const MAX_STATIC_SIZE = 100 * 1024;

class StickerProcessor {
    private ffmpeg: FFmpeg | null = null;
    private isLoaded = false;

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
        this.ffmpeg = new FFmpeg();
        const baseURL = 'https://unpkg.com/@ffmpeg/core@0.12.6/dist/esm';
        await this.ffmpeg.load({
            coreURL: await toBlobURL(`${baseURL}/ffmpeg-core.js`, 'text/javascript'),
            wasmURL: await toBlobURL(`${baseURL}/ffmpeg-core.wasm`, 'application/wasm'),
        });
        this.isLoaded = true;
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
     * GIF işleme - Frame'leri doğru şekilde çıkar, ffmpeg ile WhatsApp uyumlu WebP oluştur,
     * sonra ANMF disposal flag'larını patch'le (ghosting önleme)
     */
    private async processGifFrames(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        onProgress?.({ message: 'GIF analiz ediliyor...', percentage: 10 });

        // ImageDecoder API'yi kullan (modern tarayıcılarda mevcut)
        if ('ImageDecoder' in window) {
            return this.processGifWithImageDecoder(file, onProgress);
        }

        // Fallback: gifuct-js ile GIF frame yakalama
        return this.processGifWithCanvasCapture(file, onProgress);
    }

    /**
     * Modern ImageDecoder API ile GIF işleme
     */
    private async processGifWithImageDecoder(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        const ffmpeg = this.ffmpeg!;

        const decoder = new (window as any).ImageDecoder({
            data: file.stream(),
            type: 'image/gif'
        });

        await decoder.tracks.ready;
        const totalFrames = decoder.tracks.selectedTrack.frameCount;
        const frameCount = Math.min(totalFrames, MAX_FRAMES);

        // Minimum frame kontrolü - 3'ten az frame varsa bu GIF gerçekten animasyonlu değil
        if (totalFrames < 3) {
            decoder.close();
            throw new Error(`GIF dosyası yeterli frame içermiyor (${totalFrames} frame). Animasyonlu çıkartmalar için en az 3 frame gerekli. Bu dosya statik bir görsel olabilir.`);
        }

        onProgress?.({ message: `${frameCount} frame işleniyor...`, percentage: 15 });

        const outCanvas = document.createElement('canvas');
        outCanvas.width = STICKER_SIZE;
        outCanvas.height = STICKER_SIZE;
        const outCtx = outCanvas.getContext('2d', { alpha: true })!;

        for (let i = 0; i < frameCount; i++) {
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
            onProgress?.({ message: `Frame ${i + 1}/${frameCount}...`, percentage: 15 + Math.round((i / frameCount) * 50) });
        }

        decoder.close();
        return this.createWebPFromFrames(frameCount, onProgress);
    }

    /**
     * Fallback: gifuct-js ile GIF işleme (eski tarayıcılar için)
     */
    private async processGifWithCanvasCapture(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        const ffmpeg = this.ffmpeg!;

        const buffer = await file.arrayBuffer();
        const gif = parseGIF(buffer);
        const frames = decompressFrames(gif, true);

        // Minimum frame kontrolü - 3'ten az frame varsa bu GIF gerçekten animasyonlu değil
        if (frames.length < 3) {
            throw new Error(`GIF dosyası yeterli frame içermiyor (${frames.length} frame). Animasyonlu çıkartmalar için en az 3 frame gerekli. Bu dosya statik bir görsel olabilir.`);
        }

        const gifWidth = gif.lsd.width;
        const gifHeight = gif.lsd.height;
        const frameCount = Math.min(frames.length, MAX_FRAMES);

        const scale = Math.min(STICKER_SIZE / gifWidth, STICKER_SIZE / gifHeight);
        const scaledW = Math.round(gifWidth * scale);
        const scaledH = Math.round(gifHeight * scale);
        const offsetX = Math.round((STICKER_SIZE - scaledW) / 2);
        const offsetY = Math.round((STICKER_SIZE - scaledH) / 2);

        const compCanvas = document.createElement('canvas');
        compCanvas.width = gifWidth;
        compCanvas.height = gifHeight;
        const compCtx = compCanvas.getContext('2d', { alpha: true })!;

        const outCanvas = document.createElement('canvas');
        outCanvas.width = STICKER_SIZE;
        outCanvas.height = STICKER_SIZE;
        const outCtx = outCanvas.getContext('2d', { alpha: true })!;

        const backupCanvas = document.createElement('canvas');
        backupCanvas.width = gifWidth;
        backupCanvas.height = gifHeight;
        const backupCtx = backupCanvas.getContext('2d', { alpha: true })!;

        onProgress?.({ message: `${frameCount} frame işleniyor...`, percentage: 15 });

        for (let i = 0; i < frameCount; i++) {
            const frame = frames[i];
            const { width, height, left, top } = frame.dims;

            if (frame.disposalType === 3) {
                backupCtx.clearRect(0, 0, gifWidth, gifHeight);
                backupCtx.drawImage(compCanvas, 0, 0);
            }

            const frameCanvas = document.createElement('canvas');
            frameCanvas.width = width;
            frameCanvas.height = height;
            const frameCtx = frameCanvas.getContext('2d', { alpha: true })!;
            const imgData = new ImageData(new Uint8ClampedArray(frame.patch), width, height);
            frameCtx.putImageData(imgData, 0, 0);

            compCtx.drawImage(frameCanvas, left, top);

            outCtx.clearRect(0, 0, STICKER_SIZE, STICKER_SIZE);
            outCtx.drawImage(compCanvas, 0, 0, gifWidth, gifHeight, offsetX, offsetY, scaledW, scaledH);

            const pngBlob = await new Promise<Blob>((res) => outCanvas.toBlob((b) => res(b!), 'image/png'));
            await ffmpeg.writeFile(`frame_${i.toString().padStart(4, '0')}.png`, await fetchFile(pngBlob));

            if (frame.disposalType === 2) {
                compCtx.clearRect(left, top, width, height);
            } else if (frame.disposalType === 3) {
                compCtx.clearRect(0, 0, gifWidth, gifHeight);
                compCtx.drawImage(backupCanvas, 0, 0);
            }

            onProgress?.({ message: `Frame ${i + 1}/${frameCount}...`, percentage: 15 + Math.round((i / frameCount) * 50) });
        }

        return this.createWebPFromFrames(frameCount, onProgress);
    }

    /**
     * ffmpeg ile WhatsApp uyumlu animated WebP oluştur, sonra ghosting'i önlemek için
     * ANMF disposal flag'larını "dispose to background" olarak patch'le
     */
    private async createWebPFromFrames(frameCount: number, onProgress?: (p: StickerProgress) => void, fps: number = 10): Promise<Blob> {
        const ffmpeg = this.ffmpeg!;
        const outputName = 'output.webp';
        const MAX_SIZE = 500 * 1024;

        onProgress?.({ message: 'WebP oluşturuluyor...', percentage: 70 });

        let blob: Blob | null = null;

        // Lossy mode + yuva420p: WhatsApp uyumlu ve şeffaflık destekli
        for (const q of [90, 80, 70, 60, 50, 40, 30, 20]) {
            try { await ffmpeg.deleteFile(outputName); } catch { }

            await ffmpeg.exec([
                '-framerate', fps.toString(),
                '-i', 'frame_%04d.png',
                '-vf', `scale=${STICKER_SIZE}:${STICKER_SIZE}:force_original_aspect_ratio=decrease,pad=${STICKER_SIZE}:${STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=black@0`,
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

            // Ghosting düzeltme: her ANMF frame'in disposal flag'ını
            // "dispose to background" olarak ayarla
            data = this.patchWebPDisposalFlags(data);

            blob = new Blob([data as any], { type: 'image/webp' });

            if (blob.size <= MAX_SIZE) break;

            onProgress?.({ message: `Boyut optimize ediliyor (q:${q})...`, percentage: 85 });
        }

        // Temizlik
        for (let i = 0; i < frameCount; i++) {
            try { await ffmpeg.deleteFile(`frame_${i.toString().padStart(4, '0')}.png`); } catch { }
        }
        try { await ffmpeg.deleteFile(outputName); } catch { }

        onProgress?.({ message: `Tamamlandı! (${Math.round(blob!.size / 1024)}KB)`, percentage: 100 });
        return blob!;
    }

    /**
     * Animated WebP dosyasındaki ANMF frame'lerinin disposal flag'larını patch'le
     * Ghosting'i önlemek için her frame'e "dispose to background" ayarı yapılır
     * Dosya boyutu ve yapısı değişmez - sadece 1 bit/frame değiştirilir
     */
    private patchWebPDisposalFlags(webpBytes: Uint8Array): Uint8Array {
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
                // ANMF payload: x(3) + y(3) + w(3) + h(3) + dur(3) + flags(1) = 16 bytes
                // Flags byte offset: chunk header(8) + 15
                const flagsOffset = offset + 8 + 15;
                if (flagsOffset < result.length) {
                    // bit 0 = disposal: 1 = dispose to background (şeffaf temizle)
                    // bit 1 = blending: 0 = alpha blend (varsayılan, en uyumlu)
                    // Disposal aktif + blending varsayılan = ghosting yok + max uyumluluk
                    result[flagsOffset] = (result[flagsOffset] & 0xFC) | 0x01;
                }
            }

            offset += 8 + chunkSize + (chunkSize % 2);
        }

        return result;
    }

    /**
     * Video işleme (MP4)
     */
    private async processVideo(file: File, onProgress?: (p: StickerProgress) => void, removeBg: boolean = false): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;
        const outputName = 'output.webp';
        const MAX_SIZE = 500 * 1024;

        onProgress?.({ message: 'Video analiz ediliyor...', percentage: 10 });

        const inputName = `input_${Date.now()}.mp4`;
        await ffmpeg.writeFile(inputName, await fetchFile(file));

        // Eğer arka plan silinecekse, frame-by-frame işlem yap
        if (removeBg) {
            onProgress?.({ message: 'Frame\'ler çıkarılıyor...', percentage: 20 });

            // 1. Frame'leri çıkar (10 fps, 512px - ULTRA YÜKSEK KALİTE)
            await ffmpeg.exec([
                '-i', inputName,
                '-t', '2.5',
                '-vf', 'fps=10,scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=black@0',
                'frame_%04d.png'
            ]);

            const files = await ffmpeg.listDir('.');
            const frames = files.filter(f => f.name.startsWith('frame_') && f.name.endsWith('.png'))
                .sort((a, b) => a.name.localeCompare(b.name));

            // 2.5 saniye x 10 fps = 25 kare
            const frameCount = Math.min(frames.length, 25);

            onProgress?.({ message: 'Arka Plan Hassas Temizleniyor (Yüksek Kalite)...', percentage: 25 });

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
                            model: 'isnet', // En kaliteli model
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

            // Fazlalık frame'leri temizle
            for (let i = frameCount; i < frames.length; i++) {
                try { await ffmpeg.deleteFile(frames[i].name); } catch { }
            }

            // 3. WebP oluştur (Yeni FPS: 10)
            return this.createWebPFromFrames(frameCount, onProgress, 10);
        }

        // Arka plan silinmeyecekse standart hızlı dönüşüm
        let blob: Blob | null = null;

        for (const q of [75, 60, 50, 40, 30, 20]) {
            onProgress?.({ message: `WebP oluşturuluyor (q:${q})...`, percentage: 50 });

            try { await ffmpeg.deleteFile(outputName); } catch { }

            await ffmpeg.exec([
                '-i', inputName,
                '-t', MAX_DURATION.toString(),
                '-vf', 'fps=10,format=rgba,scale=512:512:force_original_aspect_ratio=decrease:flags=lanczos,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=black@0',
                '-c:v', 'libwebp',
                '-lossless', '0',
                '-q:v', q.toString(),
                '-pix_fmt', 'yuva420p',
                '-compression_level', '6',
                '-loop', '0',
                '-an',
                outputName
            ]);

            const data = await ffmpeg.readFile(outputName);
            const arrBuffer = (data as Uint8Array).buffer as ArrayBuffer;
            blob = new Blob([arrBuffer], { type: 'image/webp' });

            if (blob.size <= MAX_SIZE) break;
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
     * Animasyonlu WebP işleme - frame'leri çıkar, boyutlandır, WhatsApp uyumlu hale getir
     */
    /**
     * Animasyonlu WebP işleme - ImageDecoder ile frame'leri çıkar, GIF gibi işle
     */
    async processAnimatedWebP(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;

        onProgress?.({ message: 'Animasyonlu WebP analiz ediliyor...', percentage: 10 });

        // ImageDecoder API ile frame'leri çıkar (GIF ile aynı mantık)
        if (!('ImageDecoder' in window)) {
            throw new Error('Tarayıcınız animasyonlu WebP işlemeyi desteklemiyor');
        }

        const decoder = new (window as any).ImageDecoder({
            data: file.stream(),
            type: 'image/webp'
        });

        await decoder.tracks.ready;
        const frameCount = Math.min(decoder.tracks.selectedTrack.frameCount, MAX_FRAMES);

        onProgress?.({ message: `${frameCount} frame işleniyor...`, percentage: 15 });

        const outCanvas = document.createElement('canvas');
        outCanvas.width = STICKER_SIZE;
        outCanvas.height = STICKER_SIZE;
        const outCtx = outCanvas.getContext('2d', { alpha: true })!;

        for (let i = 0; i < frameCount; i++) {
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
            onProgress?.({ message: `Frame ${i + 1}/${frameCount}...`, percentage: 15 + Math.round((i / frameCount) * 50) });
        }

        decoder.close();

        // GIF ile aynı createWebPFromFrames fonksiyonunu kullan
        return this.createWebPFromFrames(frameCount, onProgress);
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
