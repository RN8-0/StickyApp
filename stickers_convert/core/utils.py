"""
Sticly Manager - Yardimci Fonksiyonlar
======================================
Genel araclar ve yapilandirma sabitleri.
"""

import os
import json
import hashlib
import subprocess
from pathlib import Path
from typing import Dict, Optional, Callable
import logging

# Logger olustur
logger = logging.getLogger("sticly")

# ============================================================================
# YAPILANDIRMA SABITLERI
# ============================================================================

SCRIPT_DIR = Path(__file__).parent.parent
STICKERS_DIR = SCRIPT_DIR / "stickers"
PREMIUM_STICKERS_DIR = SCRIPT_DIR / "premium_stickers"
OUTPUT_DIR = SCRIPT_DIR / "output"
CACHE_FILE = SCRIPT_DIR / "cache.json"
CREDENTIALS_FILE = SCRIPT_DIR / "credentials.json"
TOKEN_FILE = SCRIPT_DIR / "token.pickle"

# Google Drive klasor adi
DRIVE_FOLDER_NAME = "SticlyStickers"

# Firebase ayarlari
STORAGE_BUCKET = "sticky-dcd20.firebasestorage.app"

# Service account key'i bul
SERVICE_ACCOUNT_KEY = None
for f in SCRIPT_DIR.glob("*.json"):
    if "firebase-adminsdk" in f.name:
        SERVICE_ACCOUNT_KEY = f
        break

# WhatsApp Sticker gereksinimleri
STICKER_SIZE = 512
TRAY_SIZE = 96
MAX_FILE_SIZE_KB = 500
MAX_DURATION = 3
FPS = 10

# Publisher bilgileri
PUBLISHER = "Sticly"
PUBLISHER_EMAIL = "contact@sticly.com"
PRIVACY_POLICY = ""
LICENSE = ""

# Arka plan silme ayari
REMOVE_BACKGROUND = True

# Desteklenen formatlar
VIDEO_EXTENSIONS = {'.mp4', '.mov', '.avi', '.mkv', '.webm', '.mpeg', '.mpg', '.m4v'}
IMAGE_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}
GIF_EXTENSION = '.gif'
ALL_EXTENSIONS = VIDEO_EXTENSIONS | IMAGE_EXTENSIONS | {GIF_EXTENSION}

# Cache versiyonu (tutarsizlik sorunlari icin)
CACHE_VERSION = 2

# ============================================================================
# CACHE FONKSIYONLARI
# ============================================================================

def load_cache() -> Dict:
    """Cache dosyasini yukle"""
    default_cache = {
        "version": CACHE_VERSION,
        "converted": {},
        "uploaded": {},
        "tray": {}
    }

    if CACHE_FILE.exists():
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                cache = json.load(f)

            # Versiyon kontrolu
            if cache.get("version", 1) < CACHE_VERSION:
                logger.warning("Cache versiyonu eski, sifirlaniyor...")
                return default_cache

            return cache
        except Exception as e:
            logger.error(f"Cache yuklenemedi: {e}")

    return default_cache


def save_cache(cache: Dict):
    """Cache dosyasini kaydet"""
    cache["version"] = CACHE_VERSION
    try:
        with open(CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(cache, f, indent=2, ensure_ascii=False)
    except Exception as e:
        logger.error(f"Cache kaydedilemedi: {e}")


def get_file_hash(file_path: Path) -> str:
    """Dosyanin MD5 hash'ini al"""
    hash_md5 = hashlib.md5()
    try:
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()
    except Exception as e:
        logger.error(f"Hash alinamadi: {e}")
        return ""


# ============================================================================
# SISTEM KONTROLLERI
# ============================================================================

def check_ffmpeg() -> bool:
    """FFmpeg kurulu mu kontrol et"""
    try:
        result = subprocess.run(
            ["ffmpeg", "-version"],
            capture_output=True,
            check=True
        )
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def check_rembg() -> bool:
    """rembg kurulu mu kontrol et"""
    try:
        from rembg import remove as remove_bg
        from PIL import Image
        return True
    except ImportError:
        return False


def get_rembg_session():
    """rembg oturumunu al veya olustur"""
    try:
        from rembg import new_session
        return new_session("u2net")
    except ImportError:
        return None
    except Exception as e:
        logger.warning(f"rembg oturumu olusturulamadi: {e}")
        return None


# ============================================================================
# DOSYA YARDIMCILARI
# ============================================================================

def is_tray_file(file_path: Path) -> bool:
    """Dosya tray dosyasi mi kontrol et"""
    return file_path.stem.lower() == 'tray'


def get_file_type(file_path: Path) -> Optional[str]:
    """Dosya tipini belirle"""
    ext = file_path.suffix.lower()
    if ext in VIDEO_EXTENSIONS:
        return 'video'
    elif ext == GIF_EXTENSION:
        return 'animated_gif' if is_animated_gif(file_path) else 'image'
    elif ext in IMAGE_EXTENSIONS:
        return 'image'
    return None


def is_animated_gif(file_path: Path) -> bool:
    """GIF'in animasyonlu olup olmadigini kontrol et"""
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-select_streams", "v:0",
             "-count_packets", "-show_entries", "stream=nb_read_packets",
             "-of", "csv=p=0", str(file_path)],
            capture_output=True, text=True
        )
        frame_count = int(result.stdout.strip())
        return frame_count > 1
    except:
        return False


def find_custom_tray(pack_dir: Path) -> Optional[Path]:
    """Ozel tray dosyasi bul (buyuk/kucuk harf duyarsiz)"""
    for f in pack_dir.iterdir():
        if f.is_file() and f.stem.lower() == 'tray':
            if f.suffix.lower() in (IMAGE_EXTENSIONS | {GIF_EXTENSION}):
                return f
    return None


def get_sticker_files(pack_dir: Path) -> list:
    """Paketteki sticker dosyalarini bul"""
    files = []
    for f in pack_dir.iterdir():
        if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS:
            if not is_tray_file(f):
                files.append(f)
    return sorted(files, key=lambda x: x.name.lower())


def get_pack_id(pack_name: str) -> str:
    """Paket adindan ID olustur"""
    return pack_name.lower().replace(" ", "_").replace("-", "_")


def format_size(size_bytes: int) -> str:
    """Bayt boyutunu okunabilir formata cevir"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.1f} TB"


def ensure_directories():
    """Gerekli klasorlerin varligini kontrol et ve olustur"""
    STICKERS_DIR.mkdir(parents=True, exist_ok=True)
    PREMIUM_STICKERS_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# ============================================================================
# LOGGING YAPISI
# ============================================================================

class LogHandler(logging.Handler):
    """GUI icin ozel log handler"""

    def __init__(self, callback: Optional[Callable] = None):
        super().__init__()
        self.callback = callback
        self.setFormatter(logging.Formatter(
            '%(asctime)s [%(levelname)s] %(message)s',
            datefmt='%H:%M:%S'
        ))

    def emit(self, record):
        if self.callback:
            msg = self.format(record)
            self.callback(msg, record.levelname)


def setup_logger(callback: Optional[Callable] = None) -> logging.Logger:
    """Logger'i yapilandir"""
    logger = logging.getLogger("sticly")
    logger.setLevel(logging.DEBUG)

    # Konsol handler
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(logging.Formatter(
        '%(asctime)s [%(levelname)s] %(message)s',
        datefmt='%H:%M:%S'
    ))
    logger.addHandler(console_handler)

    # GUI callback handler
    if callback:
        gui_handler = LogHandler(callback)
        gui_handler.setLevel(logging.DEBUG)
        logger.addHandler(gui_handler)

    return logger
