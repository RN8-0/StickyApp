"""
Sticly Manager - Sticker Donusturucu
====================================
Resim ve video dosyalarini WhatsApp sticker formatina donusturme.
"""

import os
import subprocess
import tempfile
import logging
from pathlib import Path
from typing import Optional, Tuple, Callable

from .utils import (
    STICKER_SIZE, TRAY_SIZE, MAX_FILE_SIZE_KB, MAX_DURATION, FPS,
    VIDEO_EXTENSIONS, IMAGE_EXTENSIONS, GIF_EXTENSION, ALL_EXTENSIONS,
    REMOVE_BACKGROUND, get_file_type, is_tray_file, get_rembg_session,
    check_rembg
)

logger = logging.getLogger("sticly")


class StickerConverter:
    """Sticker donusturme islemlerini yoneten sinif"""

    def __init__(self):
        self._rembg_session = None

    def get_rembg_session(self):
        """rembg oturumunu al veya olustur"""
        if self._rembg_session is not None:
            return self._rembg_session
        self._rembg_session = get_rembg_session()
        return self._rembg_session

    @staticmethod
    def get_video_dimensions(file_path: Path) -> Tuple[int, int]:
        """Video/GIF boyutlarini dondur"""
        try:
            result = subprocess.run(
                ["ffprobe", "-v", "error", "-select_streams", "v:0",
                 "-show_entries", "stream=width,height",
                 "-of", "csv=s=x:p=0", str(file_path)],
                capture_output=True, text=True
            )
            if result.stdout.strip():
                width, height = map(int, result.stdout.strip().split('x'))
                return (width, height)
        except:
            pass
        return (0, 0)

    @staticmethod
    def get_image_dimensions(file_path: Path) -> Tuple[int, int]:
        """Resim boyutlarini dondur"""
        try:
            from PIL import Image
            with Image.open(file_path) as img:
                return img.size
        except:
            return (0, 0)

    @staticmethod
    def has_transparent_background(img) -> bool:
        """Resmin zaten seffaf arka plani olup olmadigini kontrol et"""
        try:
            if img.mode != 'RGBA':
                return False

            alpha = img.split()[-1]
            pixels = list(alpha.getdata())
            total_pixels = len(pixels)

            fully_transparent = sum(1 for p in pixels if p == 0)
            fully_transparent_ratio = fully_transparent / total_pixels

            semi_transparent = sum(1 for p in pixels if p < 200)
            semi_transparent_ratio = semi_transparent / total_pixels

            if fully_transparent_ratio > 0.03:
                return True
            if semi_transparent_ratio > 0.08:
                return True

            return False
        except:
            return False

    def remove_background(
        self,
        input_path: Path,
        force: bool = False
    ) -> Path:
        """Resimden arka plani sil"""
        if not check_rembg() or not REMOVE_BACKGROUND:
            return input_path

        try:
            from rembg import remove as remove_bg
            from PIL import Image

            with Image.open(input_path) as img:
                if img.mode != 'RGBA':
                    img = img.convert('RGBA')

                if not force and self.has_transparent_background(img):
                    logger.debug(f"Arka plan zaten silinmis: {input_path.name}")
                    return input_path

                logger.info(f"Arka plan siliniyor: {input_path.name}")
                session = self.get_rembg_session()
                if session:
                    output = remove_bg(img, session=session)
                else:
                    output = remove_bg(img)

                temp_file = tempfile.NamedTemporaryFile(suffix='.png', delete=False)
                output.save(temp_file.name, 'PNG')
                return Path(temp_file.name)

        except Exception as e:
            logger.warning(f"Arka plan silme hatasi: {e}")
            return input_path

    def convert_video_to_sticker(
        self,
        input_path: Path,
        output_path: Path
    ) -> bool:
        """Video/GIF'i WebP'ye donustur"""
        try:
            width, height = self.get_video_dimensions(input_path)
            is_correct_size = (width == STICKER_SIZE and height == STICKER_SIZE)

            if is_correct_size:
                cmd = [
                    "ffmpeg", "-y", "-i", str(input_path),
                    "-t", str(MAX_DURATION),
                    "-vf", f"fps={FPS}",
                    "-loop", "0", "-c:v", "libwebp",
                    "-lossless", "0", "-quality", "70", "-an",
                    str(output_path)
                ]
            else:
                cmd = [
                    "ffmpeg", "-y", "-i", str(input_path),
                    "-t", str(MAX_DURATION),
                    "-vf", f"scale={STICKER_SIZE}:{STICKER_SIZE}:force_original_aspect_ratio=decrease,"
                           f"pad={STICKER_SIZE}:{STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000,"
                           f"fps={FPS}",
                    "-loop", "0", "-c:v", "libwebp",
                    "-lossless", "0", "-quality", "70", "-an",
                    str(output_path)
                ]

            subprocess.run(cmd, capture_output=True, check=True)

            # Boyut kontrolu
            size_kb = output_path.stat().st_size / 1024
            if size_kb > MAX_FILE_SIZE_KB:
                for quality, fps in [(50, 8), (35, 6), (25, 5)]:
                    cmd[cmd.index("-quality") + 1] = str(quality)
                    if "-vf" in cmd:
                        vf_idx = cmd.index("-vf")
                        vf = cmd[vf_idx + 1]
                        cmd[vf_idx + 1] = vf.replace(f"fps={FPS}", f"fps={fps}")
                    subprocess.run(cmd, capture_output=True, check=True)
                    size_kb = output_path.stat().st_size / 1024
                    if size_kb <= MAX_FILE_SIZE_KB:
                        break

            return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Video donusturme hatasi: {e}")
            return False

    def convert_image_to_sticker(
        self,
        input_path: Path,
        output_path: Path
    ) -> bool:
        """Resmi WebP'ye donustur"""
        temp_path = None
        try:
            width, height = self.get_image_dimensions(input_path)
            is_correct_size = (width == STICKER_SIZE and height == STICKER_SIZE)

            processed_path = self.remove_background(input_path)
            if processed_path != input_path:
                temp_path = processed_path

            if is_correct_size:
                cmd = [
                    "ffmpeg", "-y", "-i", str(processed_path),
                    "-c:v", "libwebp", "-lossless", "0", "-quality", "90",
                    str(output_path)
                ]
            else:
                cmd = [
                    "ffmpeg", "-y", "-i", str(processed_path),
                    "-vf", f"scale={STICKER_SIZE}:{STICKER_SIZE}:force_original_aspect_ratio=decrease,"
                           f"pad={STICKER_SIZE}:{STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                    "-c:v", "libwebp", "-lossless", "0", "-quality", "90",
                    str(output_path)
                ]

            subprocess.run(cmd, capture_output=True, check=True)

            # Boyut kontrolu
            size_kb = output_path.stat().st_size / 1024
            if size_kb > MAX_FILE_SIZE_KB:
                for quality in [70, 50, 35]:
                    cmd[cmd.index("-quality") + 1] = str(quality)
                    subprocess.run(cmd, capture_output=True, check=True)
                    size_kb = output_path.stat().st_size / 1024
                    if size_kb <= MAX_FILE_SIZE_KB:
                        break

            return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Resim donusturme hatasi: {e}")
            return False
        finally:
            if temp_path and temp_path.exists():
                try:
                    temp_path.unlink()
                except:
                    pass

    def convert_to_sticker(
        self,
        input_path: Path,
        output_path: Path
    ) -> Tuple[bool, bool]:
        """Dosyayi sticker'a donustur

        Returns:
            (success, is_animated)
        """
        file_type = get_file_type(input_path)

        if file_type in ['video', 'animated_gif']:
            success = self.convert_video_to_sticker(input_path, output_path)
            return success, True
        elif file_type == 'image':
            success = self.convert_image_to_sticker(input_path, output_path)
            return success, False

        return False, False

    def create_tray_image(
        self,
        input_path: Path,
        output_path: Path,
        force_bg_removal: bool = True
    ) -> bool:
        """Tray image olustur (96x96, arka plan silinmis)"""
        temp_path = None
        try:
            file_type = get_file_type(input_path)
            processed_path = input_path

            # Resim dosyasi ise arka plan sil
            if file_type == 'image':
                processed_path = self.remove_background(input_path, force=force_bg_removal)
                if processed_path != input_path:
                    temp_path = processed_path

            # Video/GIF ise ilk kareyi al ve arka plan sil
            elif file_type in ['video', 'animated_gif']:
                frame_temp = tempfile.NamedTemporaryFile(suffix='.png', delete=False)
                cmd = [
                    "ffmpeg", "-y", "-i", str(input_path),
                    "-vframes", "1",
                    "-vf", "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                    str(frame_temp.name)
                ]
                subprocess.run(cmd, capture_output=True, check=True)

                processed_path = self.remove_background(Path(frame_temp.name), force=force_bg_removal)
                if processed_path != Path(frame_temp.name):
                    temp_path = processed_path
                    os.unlink(frame_temp.name)
                else:
                    temp_path = Path(frame_temp.name)
                    processed_path = temp_path

            # 96x96'ya kucult ve WebP olarak kaydet
            cmd = [
                "ffmpeg", "-y", "-i", str(processed_path),
                "-vf", f"scale={TRAY_SIZE}:{TRAY_SIZE}:force_original_aspect_ratio=decrease,"
                       f"pad={TRAY_SIZE}:{TRAY_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                "-c:v", "libwebp",
                str(output_path)
            ]
            subprocess.run(cmd, capture_output=True, check=True)
            return True

        except Exception as e:
            logger.warning(f"Tray olusturma hatasi: {e}")
            return False
        finally:
            if temp_path and temp_path.exists():
                try:
                    temp_path.unlink()
                except:
                    pass

    def find_tray_source(self, pack_dir: Path) -> Optional[Path]:
        """Tray kaynagi bul (buyuk/kucuk harf duyarsiz)

        1. Oncelikle 'tray' adli dosya aranir
        2. Bulunamazsa ilk resim dosyasi kullanilir
        """
        # 1. 'tray' adli dosya ara
        for f in pack_dir.iterdir():
            if f.is_file() and f.stem.lower() == 'tray':
                if f.suffix.lower() in (IMAGE_EXTENSIONS | {GIF_EXTENSION}):
                    return f

        # 2. Ilk resim dosyasini kullan
        from .utils import get_sticker_files
        files = get_sticker_files(pack_dir)
        return files[0] if files else None

    def process_pack(
        self,
        pack_dir: Path,
        output_dir: Path,
        cache: dict,
        firebase_manager,
        is_premium: bool = False,
        progress_callback: Optional[Callable] = None
    ) -> Optional[dict]:
        """Tek bir paketi isle"""
        from .utils import get_sticker_files, get_file_hash, get_pack_id

        pack_name = pack_dir.name
        pack_id = get_pack_id(pack_name)
        storage_folder = "premium_stickers" if is_premium else "stickers"

        files = get_sticker_files(pack_dir)

        if not files:
            logger.warning(f"{pack_name}: Dosya bulunamadi")
            return None

        if len(files) < 3:
            logger.warning(f"{pack_name}: En az 3 sticker gerekli")
            return None

        if len(files) > 30:
            files = files[:30]

        pack_output = output_dir / pack_id
        pack_output.mkdir(parents=True, exist_ok=True)

        if pack_id not in cache["converted"]:
            cache["converted"][pack_id] = {}
        if pack_id not in cache["uploaded"]:
            cache["uploaded"][pack_id] = {}

        stickers = []
        has_animated = False
        new_count = 0
        skip_count = 0

        total_files = len(files)

        for i, file in enumerate(files, 1):
            sticker_name = f"sticker_{i:02d}.webp"
            sticker_path = pack_output / sticker_name
            file_hash = get_file_hash(file)
            file_key = file.name

            if progress_callback:
                progress_callback(f"{pack_name}: {i}/{total_files}")

            cached = cache["converted"][pack_id].get(file_key)
            if cached and cached.get("hash") == file_hash and sticker_path.exists():
                skip_count += 1
                if cached.get("animated"):
                    has_animated = True

                if file_key in cache["uploaded"][pack_id]:
                    url = cache["uploaded"][pack_id][file_key]
                else:
                    remote_path = f"{storage_folder}/{pack_id}/{sticker_name}"
                    url = firebase_manager.upload_to_storage(sticker_path, remote_path)
                    if url:
                        cache["uploaded"][pack_id][file_key] = url

                stickers.append({"image_file": sticker_name, "emojis": [""], "url": url})
                continue

            success, is_animated = self.convert_to_sticker(file, sticker_path)

            if success:
                new_count += 1
                if is_animated:
                    has_animated = True

                size_kb = sticker_path.stat().st_size / 1024
                cache["converted"][pack_id][file_key] = {
                    "hash": file_hash,
                    "output": sticker_name,
                    "size_kb": size_kb,
                    "animated": is_animated
                }

                remote_path = f"{storage_folder}/{pack_id}/{sticker_name}"
                url = firebase_manager.upload_to_storage(sticker_path, remote_path)
                if url:
                    cache["uploaded"][pack_id][file_key] = url

                stickers.append({"image_file": sticker_name, "emojis": [""], "url": url})

        if not stickers:
            logger.error(f"{pack_name}: Hicbir sticker donusturulemedi")
            return None

        logger.info(f"{pack_name}: {new_count} yeni, {skip_count} atlandi")

        # Tray olustur
        tray_path = pack_output / "tray.webp"
        tray_source = self.find_tray_source(pack_dir)

        if tray_source:
            tray_cache_key = f"{pack_id}_tray"
            source_hash = get_file_hash(tray_source)
            cached_tray = cache.get("tray", {}).get(tray_cache_key, {})

            if isinstance(cached_tray, str):
                cached_tray = {"url": cached_tray, "hash": ""}

            if cached_tray.get("hash") == source_hash and tray_path.exists():
                tray_url = cached_tray.get("url", "")
            else:
                if self.create_tray_image(tray_source, tray_path, force_bg_removal=True):
                    remote_path = f"{storage_folder}/{pack_id}/tray.webp"
                    tray_url = firebase_manager.upload_to_storage(
                        tray_path, remote_path, force_refresh=True
                    )
                    if "tray" not in cache:
                        cache["tray"] = {}
                    cache["tray"][tray_cache_key] = {"url": tray_url, "hash": source_hash}
                else:
                    tray_url = stickers[0]["url"] if stickers else ""
        else:
            tray_url = stickers[0]["url"] if stickers else ""

        # Mevcut veriyi al
        existing_data = firebase_manager.get_pack(pack_id, is_premium)

        # Paket verisini olustur
        pack_data = firebase_manager.create_pack_data(
            pack_id=pack_id,
            pack_name=pack_name,
            stickers=stickers,
            tray_url=tray_url,
            has_animated=has_animated,
            is_premium=is_premium,
            existing_data=existing_data
        )

        # Firestore'a kaydet
        firebase_manager.save_pack(pack_id, pack_data, is_premium)

        return pack_data
