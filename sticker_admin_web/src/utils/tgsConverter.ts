// TGS (Telegram Animated Sticker) and WebM converter to Animated WebP
// Handles: TGS (gzipped Lottie) → Animated WebP, WebM → Animated WebP

import pako from 'pako';
import lottie from 'lottie-web';

const STICKER_SIZE = 512;
const MAX_ANIMATED_SIZE = 500 * 1024; // 500KB WhatsApp limit
const MAX_FRAMES = 60;

// ========== TGS DECOMPRESSION ==========

export function decompressTgs(buffer: ArrayBuffer): object {
    const data = new Uint8Array(buffer);
    const jsonString = pako.ungzip(data, { to: 'string' });
    return JSON.parse(jsonString);
}

// ========== LOTTIE FRAME RENDERING ==========

export async function renderLottieFrames(
    lottieData: object,
    size: number = STICKER_SIZE,
    fps: number = 12,
    quality: number = 0.75
): Promise<ArrayBuffer[]> {
    const wrapper = document.createElement('div');
    wrapper.style.cssText = `position:fixed;left:-9999px;top:-9999px;width:${size}px;height:${size}px;overflow:hidden;`;
    document.body.appendChild(wrapper);

    const anim = lottie.loadAnimation({
        container: wrapper,
        renderer: 'canvas',
        loop: false,
        autoplay: false,
        animationData: lottieData,
    });

    await new Promise<void>((resolve) => {
        anim.addEventListener('DOMLoaded', () => resolve());
    });

    // Use lottie's own canvas
    const lottieCanvas = wrapper.querySelector('canvas') as HTMLCanvasElement;
    if (!lottieCanvas) throw new Error('Lottie did not create canvas');

    const totalFrames = anim.totalFrames;
    const originalFps = anim.frameRate || 30;
    const duration = totalFrames / originalFps;
    const outputFrameCount = Math.min(Math.ceil(duration * fps), MAX_FRAMES);

    // Output canvas at exact sticker size
    const outCanvas = document.createElement('canvas');
    outCanvas.width = size;
    outCanvas.height = size;
    const outCtx = outCanvas.getContext('2d')!;

    const frames: ArrayBuffer[] = [];

    for (let i = 0; i < outputFrameCount; i++) {
        const frameNum = Math.floor((i / outputFrameCount) * totalFrames);
        anim.goToAndStop(frameNum, true);

        await new Promise(r => requestAnimationFrame(r));

        // Copy lottie's rendered frame to output canvas
        outCtx.clearRect(0, 0, size, size);
        outCtx.drawImage(lottieCanvas, 0, 0, size, size);

        const blob = await new Promise<Blob>((resolve, reject) => {
            outCanvas.toBlob(
                (b) => b ? resolve(b) : reject(new Error('toBlob failed')),
                'image/webp',
                quality
            );
        });
        frames.push(await blob.arrayBuffer());
    }

    anim.destroy();
    document.body.removeChild(wrapper);

    return frames;
}

// ========== VIDEO FRAME RENDERING ==========

export async function renderVideoFrames(
    videoBlob: Blob,
    size: number = STICKER_SIZE,
    fps: number = 12,
    quality: number = 0.75
): Promise<ArrayBuffer[]> {
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;

    const url = URL.createObjectURL(videoBlob);
    video.src = url;

    await new Promise<void>((resolve, reject) => {
        video.onloadeddata = () => resolve();
        video.onerror = () => reject(new Error('Failed to load video'));
        video.load();
    });

    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;

    const duration = video.duration;
    const frameCount = Math.min(Math.ceil(duration * fps), MAX_FRAMES);

    const frames: ArrayBuffer[] = [];

    for (let i = 0; i < frameCount; i++) {
        const time = i / fps;
        if (time > duration) break;

        video.currentTime = time;
        await new Promise<void>(resolve => { video.onseeked = () => resolve(); });

        ctx.clearRect(0, 0, size, size);
        const scale = Math.min(size / video.videoWidth, size / video.videoHeight);
        const w = video.videoWidth * scale;
        const h = video.videoHeight * scale;
        ctx.drawImage(video, (size - w) / 2, (size - h) / 2, w, h);

        const blob = await new Promise<Blob>((resolve, reject) => {
            canvas.toBlob(
                (b) => b ? resolve(b) : reject(new Error('toBlob failed')),
                'image/webp',
                quality
            );
        });
        frames.push(await blob.arrayBuffer());
    }

    URL.revokeObjectURL(url);
    return frames;
}

// ========== WEBP FRAME DATA EXTRACTION ==========

function extractFrameChunks(webpBuffer: ArrayBuffer): Uint8Array {
    const data = new Uint8Array(webpBuffer);
    const view = new DataView(webpBuffer);

    if (data.length < 12) throw new Error('WebP too small');
    const riff = String.fromCharCode(data[0], data[1], data[2], data[3]);
    const webp = String.fromCharCode(data[8], data[9], data[10], data[11]);
    if (riff !== 'RIFF' || webp !== 'WEBP') throw new Error('Not a WebP file');

    let offset = 12;
    const chunks: Uint8Array[] = [];

    while (offset + 8 <= data.length) {
        const fourcc = String.fromCharCode(data[offset], data[offset + 1], data[offset + 2], data[offset + 3]);
        const chunkSize = view.getUint32(offset + 4, true);
        const chunkEnd = offset + 8 + chunkSize;

        if (chunkEnd > data.length) break;

        // Only keep frame-relevant chunks
        if (fourcc === 'VP8 ' || fourcc === 'VP8L' || fourcc === 'ALPH') {
            chunks.push(data.slice(offset, chunkEnd));
        }

        offset = chunkEnd + (chunkSize % 2); // Pad to even boundary
    }

    if (chunks.length === 0) throw new Error('No VP8/VP8L frame data found');

    const totalLen = chunks.reduce((s, c) => s + c.length, 0);
    const result = new Uint8Array(totalLen);
    let pos = 0;
    for (const c of chunks) { result.set(c, pos); pos += c.length; }
    return result;
}

// ========== ANIMATED WEBP MUXER ==========

function write24LE(bytes: Uint8Array, offset: number, value: number): void {
    bytes[offset] = value & 0xFF;
    bytes[offset + 1] = (value >> 8) & 0xFF;
    bytes[offset + 2] = (value >> 16) & 0xFF;
}

function writeStr(bytes: Uint8Array, offset: number, str: string): void {
    for (let i = 0; i < str.length; i++) bytes[offset + i] = str.charCodeAt(i);
}

export function muxAnimatedWebp(
    frameBuffers: ArrayBuffer[],
    width: number,
    height: number,
    fps: number
): ArrayBuffer {
    const frameDurationMs = Math.round(1000 / fps);
    const frameDataList = frameBuffers.map(buf => extractFrameChunks(buf));

    // Calculate total ANMF size
    let anmfTotalSize = 0;
    for (const fd of frameDataList) {
        const payloadSize = 16 + fd.length;
        anmfTotalSize += 8 + payloadSize + (payloadSize % 2);
    }

    // VP8X: 4+4+10=18, ANIM: 4+4+6=14
    const fileContentSize = 4 + 18 + 14 + anmfTotalSize;
    const totalSize = 8 + fileContentSize;

    const buffer = new ArrayBuffer(totalSize);
    const view = new DataView(buffer);
    const bytes = new Uint8Array(buffer);
    let off = 0;

    // RIFF header
    writeStr(bytes, off, 'RIFF'); off += 4;
    view.setUint32(off, fileContentSize, true); off += 4;
    writeStr(bytes, off, 'WEBP'); off += 4;

    // VP8X chunk (animation + alpha)
    writeStr(bytes, off, 'VP8X'); off += 4;
    view.setUint32(off, 10, true); off += 4;
    bytes[off] = 0x12; off++; // flags: animation(bit1) + alpha(bit4)
    off += 3; // reserved
    write24LE(bytes, off, width - 1); off += 3;
    write24LE(bytes, off, height - 1); off += 3;

    // ANIM chunk
    writeStr(bytes, off, 'ANIM'); off += 4;
    view.setUint32(off, 6, true); off += 4;
    view.setUint32(off, 0, true); off += 4; // bg color (transparent)
    view.setUint16(off, 0, true); off += 2; // loop count 0 = infinite

    // ANMF chunks
    for (const frameData of frameDataList) {
        writeStr(bytes, off, 'ANMF'); off += 4;
        const payloadSize = 16 + frameData.length;
        view.setUint32(off, payloadSize, true); off += 4;

        write24LE(bytes, off, 0); off += 3; // X/2
        write24LE(bytes, off, 0); off += 3; // Y/2
        write24LE(bytes, off, width - 1); off += 3;
        write24LE(bytes, off, height - 1); off += 3;
        write24LE(bytes, off, frameDurationMs); off += 3;
        bytes[off] = 0x02; off++; // B=1(no blend/overwrite), D=0(no dispose)

        bytes.set(frameData, off); off += frameData.length;
        if (payloadSize % 2 !== 0) { bytes[off] = 0; off++; }
    }

    return buffer;
}

// ========== MAIN CONVERSION FUNCTIONS ==========

export async function convertTgsToAnimatedWebp(tgsBuffer: ArrayBuffer): Promise<Blob> {
    const lottieData = decompressTgs(tgsBuffer);

    const attempts: Array<{ fps: number; quality: number }> = [
        { fps: 12, quality: 0.75 },
        { fps: 10, quality: 0.65 },
        { fps: 10, quality: 0.45 },
        { fps: 8, quality: 0.35 },
    ];

    for (const { fps, quality } of attempts) {
        const frames = await renderLottieFrames(lottieData, STICKER_SIZE, fps, quality);
        if (frames.length === 0) throw new Error('No frames rendered');
        const webpBuffer = muxAnimatedWebp(frames, STICKER_SIZE, STICKER_SIZE, fps);

        if (webpBuffer.byteLength <= MAX_ANIMATED_SIZE) {
            return new Blob([webpBuffer], { type: 'image/webp' });
        }
        console.log(`[TGS] ${Math.round(webpBuffer.byteLength / 1024)}KB > 500KB, retrying lower quality...`);
    }

    // Last resort
    const frames = await renderLottieFrames(lottieData, STICKER_SIZE, 6, 0.25);
    const webpBuffer = muxAnimatedWebp(frames, STICKER_SIZE, STICKER_SIZE, 6);
    return new Blob([webpBuffer], { type: 'image/webp' });
}

export async function convertWebmToAnimatedWebp(webmBlob: Blob): Promise<Blob> {
    const attempts: Array<{ fps: number; quality: number }> = [
        { fps: 12, quality: 0.75 },
        { fps: 10, quality: 0.65 },
        { fps: 10, quality: 0.45 },
        { fps: 8, quality: 0.35 },
    ];

    for (const { fps, quality } of attempts) {
        const frames = await renderVideoFrames(webmBlob, STICKER_SIZE, fps, quality);
        if (frames.length === 0) throw new Error('No frames rendered');
        const webpBuffer = muxAnimatedWebp(frames, STICKER_SIZE, STICKER_SIZE, fps);

        if (webpBuffer.byteLength <= MAX_ANIMATED_SIZE) {
            return new Blob([webpBuffer], { type: 'image/webp' });
        }
        console.log(`[WebM] ${Math.round(webpBuffer.byteLength / 1024)}KB > 500KB, retrying lower quality...`);
    }

    const frames = await renderVideoFrames(webmBlob, STICKER_SIZE, 6, 0.25);
    const webpBuffer = muxAnimatedWebp(frames, STICKER_SIZE, STICKER_SIZE, 6);
    return new Blob([webpBuffer], { type: 'image/webp' });
}
