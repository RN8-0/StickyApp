import { FFmpeg } from '@ffmpeg/ffmpeg';
import { fetchFile, toBlobURL } from '@ffmpeg/util';
import { removeBackground } from '@imgly/background-removal';

export type StickerProgress = {
    message: string;
    percentage: number;
};

const STICKER_SIZE = 512;
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
     */
    async processAnimated(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        await this.load();
        const ffmpeg = this.ffmpeg!;
        const inputName = `input_${Date.now()}_${file.name.replace(/[^a-zA-Z0-9.]/g, '')}`;
        const outputName = 'output.webp';

        onProgress?.({ message: 'FFmpeg yükleniyor...', percentage: 10 });
        await ffmpeg.writeFile(inputName, await fetchFile(file));

        onProgress?.({ message: 'Dönüştürülüyor (MP4/GIF -> WebP)...', percentage: 30 });

        await ffmpeg.exec([
            '-i', inputName,
            '-t', MAX_DURATION.toString(),
            '-vf', `scale='if(gt(iw,ih),512,-1)':'if(gt(iw,ih),-1,512)',pad=512:512:(512-iw)/2:(512-ih)/2:color=black@0,fps=20`,
            '-lossless', '0',
            '-compression_level', '4',
            '-qscale', '75',
            '-loop', '0',
            outputName
        ]);

        const data = await ffmpeg.readFile(outputName);
        onProgress?.({ message: 'Tamamlandı!', percentage: 100 });

        // Temizlik
        await ffmpeg.deleteFile(inputName);
        await ffmpeg.deleteFile(outputName);

        // buffer cast to ArrayBuffer to avoid SharedArrayBuffer issues in some environments
        const buffer = (data as Uint8Array).buffer as ArrayBuffer;
        return new Blob([buffer], { type: 'image/webp' });
    }

    /**
     * Tray (Kapak) görseli işleme
     */
    async processTray(file: File, onProgress?: (p: StickerProgress) => void): Promise<Blob> {
        return this.processStatic(file, onProgress);
    }

    private async resizeAndCenter(blob: Blob): Promise<Blob> {
        return new Promise((resolve) => {
            const img = new Image();
            img.onload = () => {
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
                canvas.toBlob((result) => {
                    resolve(result!);
                }, 'image/webp', 0.8);
            };
            img.src = URL.createObjectURL(blob);
        });
    }
}

export const stickerProcessor = new StickerProcessor();
