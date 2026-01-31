"""
GIF Processor - GIF'leri WhatsApp uyumlu WebP'ye dönüştürür
"""

import subprocess
import os
import shutil
from pathlib import Path
from typing import List, Optional, Tuple
from PIL import Image
import tempfile


class GifProcessor:
    """GIF dosyalarını WhatsApp sticker formatına dönüştürür"""
    
    # WhatsApp Sticker Limitleri
    MAX_STATIC_SIZE = 100 * 1024  # 100KB
    MAX_ANIMATED_SIZE = 500 * 1024  # 500KB
    STICKER_SIZE = 512  # 512x512 piksel
    TRAY_SIZE = 96  # 96x96 piksel
    
    def __init__(self, ffmpeg_path: str = "ffmpeg"):
        self.ffmpeg_path = ffmpeg_path
        self._check_ffmpeg()
    
    def _check_ffmpeg(self):
        """FFmpeg kurulu mu kontrol et"""
        try:
            result = subprocess.run(
                [self.ffmpeg_path, "-version"],
                capture_output=True,
                text=True
            )
            if result.returncode != 0:
                raise RuntimeError("FFmpeg çalıştırılamadı")
        except FileNotFoundError:
            raise RuntimeError(
                "FFmpeg bulunamadı! Lütfen kurun:\n"
                "  Ubuntu/Debian: sudo apt install ffmpeg\n"
                "  MacOS: brew install ffmpeg\n"
                "  Windows: https://ffmpeg.org/download.html"
            )
    
    def gif_to_webp(self, input_path: str, output_path: str, 
                    quality: int = 80, max_size_kb: int = 500) -> bool:
        """
        GIF'i animasyonlu WebP'ye dönüştürür
        
        Args:
            input_path: GIF dosya yolu
            output_path: WebP çıktı yolu
            quality: WebP kalitesi (0-100)
            max_size_kb: Maksimum dosya boyutu (KB)
            
        Returns:
            Başarılı ise True
        """
        try:
            # Çıktı klasörünü oluştur
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            
            # FFmpeg komutu - 512x512'ye scale et ve WebP'ye dönüştür
            cmd = [
                self.ffmpeg_path,
                "-y",  # Üzerine yaz
                "-i", input_path,
                "-vf", f"scale={self.STICKER_SIZE}:{self.STICKER_SIZE}:force_original_aspect_ratio=decrease,"
                       f"pad={self.STICKER_SIZE}:{self.STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000,"
                       "fps=15",  # FPS limit
                "-loop", "0",  # Sonsuz döngü
                "-compression_level", "6",
                "-quality", str(quality),
                "-preset", "default",
                "-an",  # Ses yok
                output_path
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True)
            
            if result.returncode != 0:
                print(f"  ⚠️ FFmpeg hatası: {result.stderr[:200]}")
                return False
            
            # Boyut kontrolü
            file_size = os.path.getsize(output_path)
            if file_size > max_size_kb * 1024:
                # Kaliteyi düşürerek tekrar dene
                return self._reduce_quality(input_path, output_path, max_size_kb)
            
            return True
            
        except Exception as e:
            print(f"  ⚠️ Dönüştürme hatası: {e}")
            return False
    
    def _reduce_quality(self, input_path: str, output_path: str, 
                        max_size_kb: int, min_quality: int = 20) -> bool:
        """Dosya boyutunu küçültmek için kaliteyi, FPS'i ve çözünürlüğü kademeli düşür"""
        
        # Deneme kademeleri: (Quality, FPS, Size)
        steps = [
            (60, 12, 512),
            (40, 10, 512),
            (30, 8, 400),
            (20, 6, 320),
            (10, 5, 256)
        ]
        
        last_size = 0
        for quality, fps, size in steps:
            cmd = [
                self.ffmpeg_path,
                "-y",
                "-i", input_path,
                "-vf", f"scale={size}:{size}:force_original_aspect_ratio=decrease,"
                       f"pad={size}:{size}:(ow-iw)/2:(oh-ih)/2:color=0x00000000,"
                       f"fps={fps}",
                "-loop", "0",
                "-compression_level", "6",
                "-quality", str(quality),
                "-preset", "default",
                "-an",
                output_path
            ]
            
            subprocess.run(cmd, capture_output=True)
            
            file_size = os.path.getsize(output_path)
            last_size = file_size
            
            if file_size <= max_size_kb * 1024:
                return True
        
        print(f"  ⚠️ Dosya hala çok büyük: {last_size / 1024:.1f}KB (Limit: {max_size_kb}KB)")
        return False
    
    def create_tray_image(self, input_path: str, output_path: str) -> bool:
        """
        96x96 PNG tray görseli oluşturur (ilk frame'den)
        
        Args:
            input_path: Kaynak dosya (GIF veya WebP)
            output_path: PNG çıktı yolu
            
        Returns:
            Başarılı ise True
        """
        try:
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            
            # İlk frame'i çıkar
            with Image.open(input_path) as img:
                # İlk frame'i al
                img.seek(0)
                frame = img.convert("RGBA")
                
                # 96x96'ya resize (aspect ratio koruyarak)
                frame.thumbnail((self.TRAY_SIZE, self.TRAY_SIZE), Image.Resampling.LANCZOS)
                
                # Ortala
                new_img = Image.new("RGBA", (self.TRAY_SIZE, self.TRAY_SIZE), (0, 0, 0, 0))
                x = (self.TRAY_SIZE - frame.width) // 2
                y = (self.TRAY_SIZE - frame.height) // 2
                new_img.paste(frame, (x, y))
                
                # Kaydet
                new_img.save(output_path, "PNG")
            
            return True
            
        except Exception as e:
            print(f"  ⚠️ Tray oluşturma hatası: {e}")
            return False
    
    def process_pack(self, gif_files: List[str], output_dir: str, 
                     pack_id: str) -> Tuple[List[str], Optional[str]]:
        """
        Tüm GIF'leri işle ve tray oluştur
        
        Args:
            gif_files: GIF dosya yolları listesi
            output_dir: Çıktı klasörü
            pack_id: Paket ID'si
            
        Returns:
            (webp_dosyaları, tray_dosyası)
        """
        pack_dir = os.path.join(output_dir, pack_id)
        Path(pack_dir).mkdir(parents=True, exist_ok=True)
        
        webp_files = []
        tray_file = None
        
        for i, gif_path in enumerate(gif_files):
            webp_name = f"sticker_{i+1:02d}.webp"
            webp_path = os.path.join(pack_dir, webp_name)
            
            print(f"  🎬 İşleniyor: {os.path.basename(gif_path)} → {webp_name}")
            
            if self.gif_to_webp(gif_path, webp_path):
                webp_files.append(webp_path)
                
                # İlk başarılı dosyadan tray oluştur
                if tray_file is None:
                    tray_path = os.path.join(pack_dir, "tray.png")
                    if self.create_tray_image(gif_path, tray_path):
                        tray_file = tray_path
                        print(f"     ✓ Tray oluşturuldu")
        
        return webp_files, tray_file


# Test
if __name__ == "__main__":
    print("🎬 GIF Processor Test")
    print("-" * 40)
    
    try:
        processor = GifProcessor()
        print("✓ FFmpeg bulundu")
    except RuntimeError as e:
        print(f"✗ {e}")
