import { FFmpeg } from '@ffmpeg/ffmpeg';
import { fetchFile, toBlobURL } from '@ffmpeg/util';
import { removeBackground } from '@imgly/background-removal';

export type StickerProgress = {
    message: string;
    percentage: number;
};

const STICKER_SIZE = 512;
const TRAY_SIZE = 512; // Artık 512x512 (App içi kalite için), Android tarafı WhatsApp için 96'ya düşürecek.
const MAX_DURATION = 3; // saniye

class StickerProcessor {
    private ffmpeg: FFmpeg | null = null;
    private isLoaded = false;

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
     * Statik görsel işleme: Arka plan silme ve 512x512 boyutlandırma
     */
    async processStatic(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        onProgress?.({ message: 'Arka plan siliniyor...', percentage: 20 });

        // Arka plan silme
        const removedBgBlob = await removeBackground(file, {
            progress: (message: string) => {
                onProgress?.({ message: `Arka plan siliniyor: ${message}`, percentage: 80 });
            }
        });

        onProgress?.({ message: 'Boyutlandırılıyor...', percentage: 90 });
        return this.resizeAndCenter(removedBgBlob);
    }

    /**
     * Hareketli görsel işleme (MP4/GIF): 3sn kırpma ve WebP dönüşümü
     * WhatsApp gereksinimleri:
     * - Format: Animated WebP
     * - Boyut: 512x512 piksel
     * - Süre: Max 3 saniye
     * - Dosya boyutu: Max 500KB
     * - FPS: 8-30 arası (önerilen: 10-20)
     */
    async processAnimated(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;
        const inputName = `input_${Date.now()}_${file.name.replace(/[^a-zA-Z0-9.]/g, '')}`;
        const outputName = 'output.webp';
        const MAX_SIZE = 500 * 1024; // 500KB WhatsApp limiti

        onProgress?.({ message: 'FFmpeg yükleniyor...', percentage: 10 });
        await ffmpeg.writeFile(inputName, await fetchFile(file));

        // Kalite ve FPS kombinasyonları dene
        let quality = 75;
        let fps = 10;
        let blob: Blob | null = null;

        // Önce kaliteyi düşür, sonra FPS'i düşür
        const attempts = [
            { q: 75, fps: 10 },
            { q: 60, fps: 10 },
            { q: 50, fps: 10 },
            { q: 40, fps: 10 },
            { q: 30, fps: 10 },
            { q: 20, fps: 10 },
            { q: 15, fps: 8 },
            { q: 10, fps: 8 },
            { q: 10, fps: 6 },
            { q: 5, fps: 6 },
        ];

        for (const attempt of attempts) {
            quality = attempt.q;
            fps = attempt.fps;

            onProgress?.({ message: `Dönüştürülüyor (kalite: ${quality}, fps: ${fps})...`, percentage: 30 + (75 - quality) / 2 });

            try {
                await ffmpeg.deleteFile(outputName);
            } catch { }

            // WhatsApp uyumlu animasyonlu WebP parametreleri
            await ffmpeg.exec([
                '-i', inputName,
                '-t', MAX_DURATION.toString(),
                // Video filtresi: 512x512 boyutlandır, şeffaf padding
                '-vf', `scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000,fps=${fps}`,
                // WebP codec ayarları
                '-c:v', 'libwebp',
                '-lossless', '0',
                '-compression_level', '6',
                '-q:v', quality.toString(),
                '-loop', '0',
                '-preset', 'default',
                '-an',
                '-vsync', '0',
                outputName
            ]);

            const data = await ffmpeg.readFile(outputName);
            const buffer = (data as Uint8Array).buffer as ArrayBuffer;
            blob = new Blob([buffer], { type: 'image/webp' });

            // Boyut kontrolü - 500KB altındaysa başarılı
            if (blob.size <= MAX_SIZE) {
                onProgress?.({ message: `Tamamlandı! (${Math.round(blob.size / 1024)}KB)`, percentage: 100 });
                break;
            }
        }

        // Temizlik
        await ffmpeg.deleteFile(inputName);
        try { await ffmpeg.deleteFile(outputName); } catch { }

        // Hala çok büyükse uyar ama yine de döndür (kullanıcı görsün)
        if (blob && blob.size > MAX_SIZE) {
            onProgress?.({ message: `Uyarı: Dosya hala ${Math.round(blob.size / 1024)}KB (limit: 500KB). Daha kısa veya basit bir animasyon deneyin.`, percentage: 100 });
        }

        return blob!;
    }

    /**
     * Tray (Kapak) görseli işleme - WhatsApp için 96x96 PNG formatında
     */
    async processTray(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        onProgress?.({ message: 'Boyutlandırılıyor (512x512 PNG)...', percentage: 50 });
        return this.resizeAndCenterTray(file);
    }

    /**
     * Tray için 96x96 PNG boyutlandırma
     */
    private async resizeAndCenterTray(blob: Blob): Promise<Blob> {
        return new Promise((resolve) => {
            const img = new Image();
            img.onload = () => {
                const canvas = document.createElement('canvas');
                canvas.width = TRAY_SIZE;
                canvas.height = TRAY_SIZE;
                const ctx = canvas.getContext('2d')!;

                const scale = Math.min(TRAY_SIZE / img.width, TRAY_SIZE / img.height);
                const nw = img.width * scale;
                const nh = img.height * scale;
                const nx = (TRAY_SIZE - nw) / 2;
                const ny = (TRAY_SIZE - nh) / 2;

                ctx.drawImage(img, nx, ny, nw, nh);
                canvas.toBlob((result) => {
                    resolve(result!);
                }, 'image/png'); // WhatsApp için PNG formatı
            };
            img.src = URL.createObjectURL(blob);
        });
    }

    private async resizeAndCenter(blob: Blob): Promise<Blob> {
        const MAX_STATIC_SIZE = 100 * 1024; // 100KB WhatsApp limit for static stickers

        return new Promise((resolve) => {
            const img = new Image();
            img.onload = async () => {
                const canvas = document.createElement('canvas');
                canvas.width = STICKER_SIZE;
                canvas.height = STICKER_SIZE;
                const ctx = canvas.getContext('2d')!;

                const scale = Math.min(STICKER_SIZE / img.width, STICKER_SIZE / img.height);
                const nw = img.width * scale;
                const nh = img.height * scale;
                const nx = (STICKER_SIZE - nw) / 2;
                const ny = (STICKER_SIZE - nh) / 2;

                ctx.drawImage(img, nx, ny, nw, nh);

                // Try with decreasing quality until under 100KB limit
                let quality = 0.9;
                let result: Blob | null = null;

                while (quality >= 0.1) {
                    result = await new Promise<Blob | null>(res => {
                        canvas.toBlob(res, 'image/webp', quality);
                    });

                    if (result && result.size <= MAX_STATIC_SIZE) {
                        break;
                    }
                    quality -= 0.1;
                }

                resolve(result || blob);
            };
            img.src = URL.createObjectURL(blob);
        });
    }
}

export const stickerProcessor = new StickerProcessor();
