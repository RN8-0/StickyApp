#!/usr/bin/env python3
"""
Sticly - Sticker Yonetim Paneli v3.0
=====================================
Google Drive entegrasyonlu, menu tabanli sticker yonetim araci.

Ozellikler:
- Google Drive'dan sticker cekme/yukleme
- Firebase Storage ve Firestore senkronizasyonu
- Tray (kapak) resmi guncelleme ve arka plan silme
- Istatistik goruntuleme
- GitHub senkronizasyonu
"""

import os
import sys
import subprocess
from pathlib import Path

# ============================================================================
# OTOMATIK KURULUM - VENV VE PAKET KONTROLU
# ============================================================================

def setup_environment():
    """Virtual environment ve gerekli paketleri kontrol et/kur"""
    script_dir = Path(__file__).parent
    venv_dir = script_dir / "venv"

    # Windows/Linux uyumu
    if sys.platform == "win32":
        python_path = venv_dir / "Scripts" / "python.exe"
        pip_path = venv_dir / "Scripts" / "pip.exe"
    else:
        python_path = venv_dir / "bin" / "python"
        pip_path = venv_dir / "bin" / "pip"

    # Eger zaten venv icinden calisiyorsak, devam et
    if hasattr(sys, 'real_prefix') or (hasattr(sys, 'base_prefix') and sys.base_prefix != sys.prefix):
        return True

    # Venv var mi kontrol et
    if not venv_dir.exists():
        print("\n [*] Ilk kurulum yapiliyor...")
        print(" [*] Virtual environment olusturuluyor...")
        try:
            subprocess.run([sys.executable, "-m", "venv", str(venv_dir)], check=True)
            print(" [OK] Virtual environment olusturuldu")
        except subprocess.CalledProcessError as e:
            print(f" [HATA] Venv olusturulamadi: {e}")
            return False

    # Gerekli paketler
    required_packages = [
        "firebase-admin",
        "google-api-python-client",
        "google-auth-httplib2",
        "google-auth-oauthlib",
        "rembg",
        "pillow"
    ]

    # Paketleri kontrol et ve kur
    print("\n [*] Paketler kontrol ediliyor...")

    try:
        # Pip upgrade
        subprocess.run(
            [str(pip_path), "install", "--upgrade", "pip"],
            capture_output=True, check=True
        )

        # Paketleri yükle (zaten varsa atlar)
        for pkg in required_packages:
            result = subprocess.run(
                [str(pip_path), "show", pkg],
                capture_output=True
            )
            if result.returncode != 0:
                print(f" [*] {pkg} yukleniyor...")
                subprocess.run(
                    [str(pip_path), "install", pkg],
                    capture_output=True, check=True
                )
                print(f" [OK] {pkg} yuklendi")

        print(" [OK] Tum paketler hazir")

    except subprocess.CalledProcessError as e:
        print(f" [HATA] Paket yuklenemedi: {e}")
        return False

    # Scripti venv python'u ile yeniden calistir
    print("\n [*] Venv ile yeniden baslatiliyor...\n")
    os.execv(str(python_path), [str(python_path), __file__] + sys.argv[1:])

# Kurulum kontrolu
if __name__ == "__main__":
    # Sadece ana modul olarak calistiginda kontrol et
    script_dir = Path(__file__).parent
    venv_python = script_dir / "venv" / ("Scripts" if sys.platform == "win32" else "bin") / "python"

    # Venv disinda calisiyorsak kurulumu yap
    if not (hasattr(sys, 'real_prefix') or (hasattr(sys, 'base_prefix') and sys.base_prefix != sys.prefix)):
        if venv_python.exists():
            # Venv var, onunla calistir
            os.execv(str(venv_python), [str(venv_python), __file__] + sys.argv[1:])
        else:
            # Venv yok, kur
            setup_environment()

# ============================================================================
# IMPORTLAR (venv icinden calistiginda yuklenecek)
# ============================================================================

import json
import hashlib
import tempfile
import shutil
import random
from datetime import datetime
from io import BytesIO
import pickle

# ============================================================================
# LOGO VE ARAYUZ
# ============================================================================

LOGO = """
███████╗████████╗██╗ ██████╗██╗  ██╗   ██╗
██╔════╝╚══██╔══╝██║██╔════╝██║  ╚██╗ ██╔╝
███████╗   ██║   ██║██║     ██║   ╚████╔╝
╚════██║   ██║   ██║██║     ██║    ╚██╔╝
███████║   ██║   ██║╚██████╗███████╗██║
╚══════╝   ╚═╝   ╚═╝ ╚═════╝╚══════╝╚═╝
        Sticker Yonetim Paneli v3.0
"""

MENU = """
╔════════════════════════════════════════════════════════════╗
║                      ANA MENU                              ║
╠════════════════════════════════════════════════════════════╣
║  [1] Tray (Kapak) Fotograflarini Guncelle                  ║
║  [2] Stickerlari Guncelle (Yerel -> Firebase)              ║
║  [3] Sticker Paket Adlarini Guncelle                       ║
║  [4] GitHub Reposunu Guncelle                              ║
║  [5] Istatistik Ekrani                                     ║
╠════════════════════════════════════════════════════════════╣
║  [6] Drive'dan Yerel'e Stickerlari Indir                   ║
║  [7] Yerel'den Drive'a Stickerlari Yukle                   ║
╠════════════════════════════════════════════════════════════╣
║  [8] Yeni Sticker Paketi Ekle                              ║
║  [9] Sticker Paketi Sil                                    ║
╠════════════════════════════════════════════════════════════╣
║  [C] Paket Kategorisini Degistir                           ║
║  [R] Indirme Sayilarini Sifirla                            ║
╠════════════════════════════════════════════════════════════╣
║  [F] Tam Senkronizasyon (Tum islemler)                     ║
║  [0] Cikis                                                 ║
╚════════════════════════════════════════════════════════════╝
"""

# ============================================================================
# YAPILANDIRMA
# ============================================================================

SCRIPT_DIR = Path(__file__).parent
STICKERS_DIR = SCRIPT_DIR / "stickers"
PREMIUM_STICKERS_DIR = SCRIPT_DIR / "premium_stickers"
OUTPUT_DIR = SCRIPT_DIR / "output"
CACHE_FILE = SCRIPT_DIR / "cache.json"
CREDENTIALS_FILE = SCRIPT_DIR / "credentials.json"
TOKEN_FILE = SCRIPT_DIR / "token.pickle"

# Google Drive klasor adi
DRIVE_FOLDER_NAME = "SticlyStickers"

# Firebase ayarlari
SERVICE_ACCOUNT_KEY = None
for f in SCRIPT_DIR.glob("*.json"):
    if "firebase-adminsdk" in f.name:
        SERVICE_ACCOUNT_KEY = f
        break

STORAGE_BUCKET = "sticky-dcd20.firebasestorage.app"

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

# Global degiskenler
_firebase_initialized = False
_bucket = None
_db = None
_drive_service = None
_rembg_session = None

# ============================================================================
# YARDIMCI FONKSIYONLAR
# ============================================================================

def clear_screen():
    """Ekrani temizle"""
    os.system('cls' if os.name == 'nt' else 'clear')

def print_header(title):
    """Baslik yazdir"""
    print("\n" + "=" * 60)
    print(f"  {title}")
    print("=" * 60)

def print_success(msg):
    print(f" [OK] {msg}")

def print_error(msg):
    print(f" [HATA] {msg}")

def print_warning(msg):
    print(f" [!] {msg}")

def print_info(msg):
    print(f" [*] {msg}")

def print_progress(current, total, prefix=""):
    """Ilerleme goster"""
    percent = int((current / total) * 100) if total > 0 else 0
    bar_length = 30
    filled = int(bar_length * current / total) if total > 0 else 0
    bar = "█" * filled + "░" * (bar_length - filled)
    print(f"\r {prefix}[{bar}] {percent}% ({current}/{total})", end="", flush=True)

def wait_enter():
    """Enter'a basilmasini bekle"""
    input("\n Devam etmek icin Enter'a basin...")

def load_cache() -> dict:
    """Cache dosyasini yukle"""
    if CACHE_FILE.exists():
        try:
            with open(CACHE_FILE, "r") as f:
                return json.load(f)
        except:
            pass
    return {"converted": {}, "uploaded": {}, "tray": {}}

def save_cache(cache: dict):
    """Cache dosyasini kaydet"""
    with open(CACHE_FILE, "w") as f:
        json.dump(cache, f, indent=2, ensure_ascii=False)

def get_file_hash(file_path: Path) -> str:
    """Dosyanin MD5 hash'ini al"""
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def check_ffmpeg():
    """FFmpeg kurulu mu kontrol et"""
    try:
        subprocess.run(["ffmpeg", "-version"], capture_output=True, check=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False

def get_rembg_session():
    """rembg oturumunu al veya olustur"""
    global _rembg_session
    if _rembg_session is not None:
        return _rembg_session
    try:
        from rembg import new_session
        _rembg_session = new_session("u2net")
        return _rembg_session
    except ImportError:
        return None
    except Exception as e:
        print_warning(f"rembg oturumu olusturulamadi: {e}")
        return None

def check_rembg():
    """rembg kurulu mu kontrol et"""
    try:
        from rembg import remove as remove_bg
        from PIL import Image
        return True
    except ImportError:
        return False

# ============================================================================
# GOOGLE DRIVE ENTEGRASYONU
# ============================================================================

def init_drive():
    """Google Drive API'yi baslat"""
    global _drive_service

    if _drive_service is not None:
        return _drive_service

    try:
        from google.oauth2.credentials import Credentials
        from google_auth_oauthlib.flow import InstalledAppFlow
        from google.auth.transport.requests import Request
        from googleapiclient.discovery import build
    except ImportError:
        print_error("Google API kutuphaneleri yuklu degil!")
        print("   Calistir: pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib")
        return None

    SCOPES = ['https://www.googleapis.com/auth/drive']
    creds = None

    # Token var mi kontrol et
    if TOKEN_FILE.exists():
        with open(TOKEN_FILE, 'rb') as token:
            creds = pickle.load(token)

    # Token gecersiz veya yok
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not CREDENTIALS_FILE.exists():
                print_error(f"credentials.json bulunamadi!")
                print(f"   1. Google Cloud Console'a git")
                print(f"   2. APIs & Services > Credentials")
                print(f"   3. OAuth 2.0 Client ID olustur (Desktop app)")
                print(f"   4. JSON indir ve su konuma koy:")
                print(f"      {CREDENTIALS_FILE}")
                return None

            flow = InstalledAppFlow.from_client_secrets_file(
                str(CREDENTIALS_FILE), SCOPES)
            creds = flow.run_local_server(port=0)

        # Token'i kaydet
        with open(TOKEN_FILE, 'wb') as token:
            pickle.dump(creds, token)

    _drive_service = build('drive', 'v3', credentials=creds)
    return _drive_service

def get_or_create_drive_folder(service, folder_name, parent_id=None):
    """Drive'da klasor bul veya olustur"""
    query = f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder' and trashed=false"
    if parent_id:
        query += f" and '{parent_id}' in parents"

    results = service.files().list(q=query, spaces='drive', fields='files(id, name)').execute()
    folders = results.get('files', [])

    if folders:
        return folders[0]['id']

    # Klasor olustur
    file_metadata = {
        'name': folder_name,
        'mimeType': 'application/vnd.google-apps.folder'
    }
    if parent_id:
        file_metadata['parents'] = [parent_id]

    folder = service.files().create(body=file_metadata, fields='id').execute()
    return folder.get('id')

def upload_file_to_drive(service, file_path: Path, parent_id: str, file_name: str = None):
    """Dosyayi Drive'a yukle"""
    from googleapiclient.http import MediaFileUpload

    if file_name is None:
        file_name = file_path.name

    # Ayni isimde dosya var mi kontrol et
    query = f"name='{file_name}' and '{parent_id}' in parents and trashed=false"
    results = service.files().list(q=query, spaces='drive', fields='files(id)').execute()
    existing = results.get('files', [])

    media = MediaFileUpload(str(file_path), resumable=True)

    if existing:
        # Guncelle
        file = service.files().update(fileId=existing[0]['id'], media_body=media).execute()
    else:
        # Yeni yukle
        file_metadata = {'name': file_name, 'parents': [parent_id]}
        file = service.files().create(body=file_metadata, media_body=media, fields='id').execute()

    return file.get('id')

def download_file_from_drive(service, file_id: str, dest_path: Path):
    """Drive'dan dosya indir"""
    from googleapiclient.http import MediaIoBaseDownload
    import io

    request = service.files().get_media(fileId=file_id)
    fh = io.BytesIO()
    downloader = MediaIoBaseDownload(fh, request)

    done = False
    while not done:
        status, done = downloader.next_chunk()

    dest_path.parent.mkdir(parents=True, exist_ok=True)
    with open(dest_path, 'wb') as f:
        fh.seek(0)
        f.write(fh.read())

def list_drive_folder(service, folder_id: str):
    """Klasordeki dosyalari listele"""
    results = service.files().list(
        q=f"'{folder_id}' in parents and trashed=false",
        spaces='drive',
        fields='files(id, name, mimeType, size)'
    ).execute()
    return results.get('files', [])

# ============================================================================
# FIREBASE ENTEGRASYONU
# ============================================================================

def init_firebase():
    """Firebase'i baslat"""
    global _firebase_initialized, _bucket, _db

    if _firebase_initialized:
        return _bucket, _db

    try:
        import firebase_admin
        from firebase_admin import credentials, storage, firestore
    except ImportError:
        print_error("Firebase Admin SDK yuklu degil!")
        print("   Calistir: pip install firebase-admin")
        return None, None

    if SERVICE_ACCOUNT_KEY is None or not SERVICE_ACCOUNT_KEY.exists():
        print_error("Service Account Key bulunamadi!")
        print(f"   Bu klasore firebase-adminsdk iceren JSON dosyasi koy:")
        print(f"   {SCRIPT_DIR}/")
        return None, None

    cred = credentials.Certificate(str(SERVICE_ACCOUNT_KEY))
    firebase_admin.initialize_app(cred, {'storageBucket': STORAGE_BUCKET})

    _bucket = storage.bucket()
    _db = firestore.client()
    _firebase_initialized = True

    return _bucket, _db

def upload_to_storage(bucket, local_path: Path, remote_path: str, force_refresh: bool = False) -> str:
    """Firebase Storage'a yukle ve URL dondur"""
    blob = bucket.blob(remote_path)

    if force_refresh:
        try:
            blob.delete()
        except:
            pass
        blob = bucket.blob(remote_path)

    blob.cache_control = "no-cache, no-store, must-revalidate"
    blob.upload_from_filename(str(local_path), content_type="image/webp")
    blob.make_public()

    import time
    timestamp = int(time.time())
    return f"{blob.public_url}?v={timestamp}"

def delete_from_storage(bucket, remote_path: str):
    """Firebase Storage'dan sil"""
    try:
        blob = bucket.blob(remote_path)
        blob.delete()
        return True
    except:
        return False

# ============================================================================
# ARKA PLAN SILME FONKSIYONLARI
# ============================================================================

def has_transparent_background(img) -> bool:
    """Resmin zaten seffaf arka plani olup olmadigini kontrol et"""
    try:
        if img.mode != 'RGBA':
            return False

        # Alfa kanalini al
        alpha = img.split()[-1]
        pixels = list(alpha.getdata())
        total_pixels = len(pixels)

        # Tamamen seffaf pikselleri say (alfa = 0)
        fully_transparent = sum(1 for p in pixels if p == 0)
        fully_transparent_ratio = fully_transparent / total_pixels

        # Yari seffaf pikselleri say (alfa < 200)
        semi_transparent = sum(1 for p in pixels if p < 200)
        semi_transparent_ratio = semi_transparent / total_pixels

        # %3'ten fazla tamamen seffaf piksel VEYA %8'den fazla yari seffaf piksel varsa
        # arka plan zaten silinmis demektir
        if fully_transparent_ratio > 0.03:
            return True
        if semi_transparent_ratio > 0.08:
            return True

        return False
    except:
        return False

def remove_background_from_image(input_path: Path, force: bool = False) -> Path:
    """Resimden arka plani sil (eger zaten silinmemisse)

    Args:
        input_path: Girdi dosyasi yolu
        force: True ise arka plan zaten silinmis olsa bile tekrar sil

    Returns:
        Islenmis dosyanin yolu (gecici dosya veya orijinal)
    """
    if not check_rembg() or not REMOVE_BACKGROUND:
        return input_path

    try:
        from rembg import remove as remove_bg
        from PIL import Image

        with Image.open(input_path) as img:
            # RGBA'ya cevir
            if img.mode != 'RGBA':
                img = img.convert('RGBA')

            # Arka plan zaten silinmis mi kontrol et (force degilse)
            if not force and has_transparent_background(img):
                print_info(f"Arka plan zaten silinmis: {input_path.name}")
                return input_path

            # Arka plani sil
            print_info(f"Arka plan siliniyor: {input_path.name}")
            session = get_rembg_session()
            if session:
                output = remove_bg(img, session=session)
            else:
                output = remove_bg(img)

            # Gecici dosyaya kaydet
            temp_file = tempfile.NamedTemporaryFile(suffix='.png', delete=False)
            output.save(temp_file.name, 'PNG')
            return Path(temp_file.name)

    except Exception as e:
        print_warning(f"Arka plan silme hatasi: {e}")
        return input_path

# ============================================================================
# DONUSTURME FONKSIYONLARI
# ============================================================================

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

def get_file_type(file_path: Path) -> str:
    """Dosya tipini belirle"""
    ext = file_path.suffix.lower()
    if ext in VIDEO_EXTENSIONS:
        return 'video'
    elif ext == GIF_EXTENSION:
        return 'animated_gif' if is_animated_gif(file_path) else 'image'
    elif ext in IMAGE_EXTENSIONS:
        return 'image'
    return None

def is_tray_file(file_path: Path) -> bool:
    """Dosya tray dosyasi mi kontrol et"""
    return file_path.stem.lower() == 'tray'

def get_video_dimensions(file_path: Path) -> tuple:
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

def get_image_dimensions(file_path: Path) -> tuple:
    """Resim boyutlarini dondur"""
    try:
        from PIL import Image
        with Image.open(file_path) as img:
            return img.size
    except:
        return (0, 0)

def convert_video_to_sticker(input_path: Path, output_path: Path) -> bool:
    """Video/GIF'i WebP'ye donustur"""
    try:
        width, height = get_video_dimensions(input_path)
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
    except subprocess.CalledProcessError:
        return False

def convert_image_to_sticker(input_path: Path, output_path: Path) -> bool:
    """Resmi WebP'ye donustur"""
    temp_path = None
    try:
        width, height = get_image_dimensions(input_path)
        is_correct_size = (width == STICKER_SIZE and height == STICKER_SIZE)

        processed_path = remove_background_from_image(input_path)
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

        size_kb = output_path.stat().st_size / 1024
        if size_kb > MAX_FILE_SIZE_KB:
            for quality in [70, 50, 35]:
                cmd[cmd.index("-quality") + 1] = str(quality)
                subprocess.run(cmd, capture_output=True, check=True)
                size_kb = output_path.stat().st_size / 1024
                if size_kb <= MAX_FILE_SIZE_KB:
                    break

        return True
    except subprocess.CalledProcessError:
        return False
    finally:
        if temp_path and temp_path.exists():
            try:
                temp_path.unlink()
            except:
                pass

def convert_to_sticker(input_path: Path, output_path: Path) -> tuple:
    """Dosyayi sticker'a donustur"""
    file_type = get_file_type(input_path)

    if file_type in ['video', 'animated_gif']:
        success = convert_video_to_sticker(input_path, output_path)
        return success, True
    elif file_type == 'image':
        success = convert_image_to_sticker(input_path, output_path)
        return success, False

    return False, False

def create_tray_image(input_path: Path, output_path: Path, force_bg_removal: bool = True) -> bool:
    """Tray image olustur (96x96, arka plan silinmis)

    Args:
        input_path: Kaynak dosya yolu
        output_path: Hedef dosya yolu
        force_bg_removal: True ise arka plan mutlaka silinir
    """
    temp_path = None
    try:
        from PIL import Image

        file_type = get_file_type(input_path)
        processed_path = input_path

        # Resim dosyasi ise arka plan sil
        if file_type == 'image':
            processed_path = remove_background_from_image(input_path, force=force_bg_removal)
            if processed_path != input_path:
                temp_path = processed_path

        # Video/GIF ise ilk kareyi al ve arka plan sil
        elif file_type in ['video', 'animated_gif']:
            # Ilk kareyi PNG olarak cikart
            frame_temp = tempfile.NamedTemporaryFile(suffix='.png', delete=False)
            cmd = [
                "ffmpeg", "-y", "-i", str(input_path),
                "-vframes", "1",
                "-vf", "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                str(frame_temp.name)
            ]
            subprocess.run(cmd, capture_output=True, check=True)

            # Arka plani sil
            processed_path = remove_background_from_image(Path(frame_temp.name), force=force_bg_removal)
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
        print_warning(f"Tray olusturma hatasi: {e}")
        return False
    finally:
        if temp_path and temp_path.exists():
            try:
                temp_path.unlink()
            except:
                pass

def find_custom_tray(pack_dir: Path) -> Path:
    """Ozel tray dosyasi bul"""
    for f in pack_dir.iterdir():
        if f.is_file() and is_tray_file(f) and f.suffix.lower() in (IMAGE_EXTENSIONS | {GIF_EXTENSION}):
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

# ============================================================================
# MENU FONKSIYONLARI
# ============================================================================

def menu_update_trays():
    """Tray fotograflarini guncelle - ARKA PLAN SILME ILE"""
    print_header("TRAY FOTOGRAFLARINI GUNCELLE")

    if not check_rembg():
        print_warning("rembg yuklu degil - arka plan silme devre disi")
        print_info("Kurmak icin: pip install rembg")

    if not check_ffmpeg():
        print_error("FFmpeg yuklu degil!")
        print("   Calistir: sudo apt install ffmpeg")
        wait_enter()
        return

    bucket, db = init_firebase()
    if not bucket:
        wait_enter()
        return

    cache = load_cache()

    # Klasorleri tara
    all_pack_dirs = []
    if STICKERS_DIR.exists():
        all_pack_dirs.extend([(d, False) for d in STICKERS_DIR.iterdir() if d.is_dir()])
    if PREMIUM_STICKERS_DIR.exists():
        all_pack_dirs.extend([(d, True) for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()])

    if not all_pack_dirs:
        print_warning("Paket klasoru bulunamadi!")
        wait_enter()
        return

    print(f"\n {len(all_pack_dirs)} paket bulundu.")
    print("\n Secenekler:")
    print("   [1] Sadece degisenleri guncelle (Hizli)")
    print("   [2] Tum tray'leri yeniden olustur (Yavas ama kesin)")
    print("\n Seciminiz (varsayilan=1): ", end="")

    choice = input().strip()
    force_all = (choice == "2")

    if force_all:
        print_warning("TUM tray'ler yeniden olusturulacak!")

    updated = 0
    skipped = 0
    failed = 0

    for idx, (pack_dir, is_premium) in enumerate(all_pack_dirs, 1):
        pack_name = pack_dir.name
        pack_id = pack_name.lower().replace(" ", "_").replace("-", "_")
        storage_folder = "premium_stickers" if is_premium else "stickers"

        # Tray kaynagini bul
        custom_tray = find_custom_tray(pack_dir)
        if not custom_tray:
            # Ilk sticker'i kullan
            files = get_sticker_files(pack_dir)
            if files:
                custom_tray = files[0]

        if not custom_tray:
            print(f"   {pack_name}: ATLANDI (dosya yok)")
            skipped += 1
            continue

        pack_output = OUTPUT_DIR / pack_id
        pack_output.mkdir(parents=True, exist_ok=True)
        tray_path = pack_output / "tray.webp"

        file_hash = get_file_hash(custom_tray)
        tray_cache_key = f"{pack_id}_tray"
        cached = cache.get("tray", {}).get(tray_cache_key, {})

        if isinstance(cached, str):
            cached = {"url": cached, "hash": ""}

        # Cache kontrolu (force_all degilse)
        if not force_all and cached.get("hash") == file_hash and tray_path.exists():
            print(f"   {pack_name}: ATLANDI (degismemis)")
            skipped += 1
            continue

        print(f"\n [{idx}/{len(all_pack_dirs)}] {pack_name}:")
        print(f"      Kaynak: {custom_tray.name}")
        print(f"      Arka plan siliniyor ve tray olusturuluyor...", end=" ")

        if create_tray_image(custom_tray, tray_path, force_bg_removal=True):
            remote_path = f"{storage_folder}/{pack_id}/tray.webp"
            tray_url = upload_to_storage(bucket, tray_path, remote_path, force_refresh=True)

            if "tray" not in cache:
                cache["tray"] = {}
            cache["tray"][tray_cache_key] = {"url": tray_url, "hash": file_hash}

            # Firestore'u guncelle
            collection = "premium_stickers" if is_premium else "stickers"
            try:
                db.collection(collection).document(pack_id).update({
                    "tray_url": tray_url
                })
            except:
                pass

            print("OK")
            print(f"      URL: {tray_url[:60]}...")
            updated += 1
        else:
            print("HATA")
            failed += 1

    save_cache(cache)

    print("\n" + "=" * 50)
    print(f" SONUC: {updated} guncellendi, {skipped} atlandi, {failed} hata")
    print("=" * 50)

    wait_enter()

def menu_update_stickers():
    """Stickerlari guncelle (Yerel -> Firebase)"""
    print_header("STICKERLARI GUNCELLE")

    if not check_ffmpeg():
        print_error("FFmpeg yuklu degil!")
        print("   Calistir: sudo apt install ffmpeg")
        wait_enter()
        return

    bucket, db = init_firebase()
    if not bucket:
        wait_enter()
        return

    cache = load_cache()
    OUTPUT_DIR.mkdir(exist_ok=True)
    STICKERS_DIR.mkdir(exist_ok=True)
    PREMIUM_STICKERS_DIR.mkdir(exist_ok=True)

    # Paketleri bul
    normal_packs = [d for d in STICKERS_DIR.iterdir() if d.is_dir()] if STICKERS_DIR.exists() else []
    premium_packs = [d for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()] if PREMIUM_STICKERS_DIR.exists() else []

    total = len(normal_packs) + len(premium_packs)

    # GUVENLIK KONTROLU: Yerel klasorde paket yoksa Firebase'e dokunma!
    if total == 0:
        print_warning("Yerel klasorde paket bulunamadi!")
        print("\n   GUVENLIK: Firebase'deki veriler korunuyor.")
        print("   Stickerlar silinmedi, sadece yerel klasor bos.")
        print("\n   Yapmaniz gerekenler:")
        print("   1. Once Drive'dan stickerlari indirin (Menu 6)")
        print("   2. Veya yeni paket ekleyin (Menu 8)")
        wait_enter()
        return

    # Firebase'deki paket sayisini kontrol et
    firebase_pack_count = 0
    try:
        for doc in db.collection("stickers").stream():
            firebase_pack_count += 1
        for doc in db.collection("premium_stickers").stream():
            firebase_pack_count += 1
    except:
        pass

    # Eger Firebase'de cok fazla paket var ama yerelde az varsa uyar
    if firebase_pack_count > 0 and total < firebase_pack_count:
        print_warning(f"DIKKAT: Yerelde {total} paket, Firebase'de {firebase_pack_count} paket var!")
        print("\n   Bu islem sadece yereldeki paketleri gunceller.")
        print("   Firebase'deki fazla paketler SILINMEYECEK.")
        print("\n   Devam etmek istiyor musunuz? (e/h): ", end="")
        confirm = input().strip().lower()
        if confirm != 'e':
            print_info("Islem iptal edildi.")
            wait_enter()
            return

    print_info(f"{len(normal_packs)} normal, {len(premium_packs)} premium paket bulundu")

    successful = 0

    # Normal paketler
    for pack_dir in sorted(normal_packs):
        result = process_single_pack(pack_dir, bucket, db, cache, is_premium=False)
        if result:
            successful += 1

    # Premium paketler
    for pack_dir in sorted(premium_packs):
        result = process_single_pack(pack_dir, bucket, db, cache, is_premium=True)
        if result:
            successful += 1

    save_cache(cache)
    print_success(f"{successful}/{total} paket basariyla islendi")
    wait_enter()

def process_single_pack(pack_dir: Path, bucket, db, cache: dict, is_premium: bool = False) -> dict:
    """Tek bir paketi isle"""
    pack_name = pack_dir.name
    pack_id = pack_name.lower().replace(" ", "_").replace("-", "_")
    storage_folder = "premium_stickers" if is_premium else "stickers"
    pack_type = "PREMIUM" if is_premium else "NORMAL"

    print(f"\n [{pack_type}] {pack_name}")

    files = get_sticker_files(pack_dir)

    if not files:
        print("   Dosya bulunamadi")
        return None

    if len(files) < 3:
        print("   En az 3 sticker gerekli")
        return None

    if len(files) > 30:
        files = files[:30]

    pack_output = OUTPUT_DIR / pack_id
    pack_output.mkdir(parents=True, exist_ok=True)

    if pack_id not in cache["converted"]:
        cache["converted"][pack_id] = {}
    if pack_id not in cache["uploaded"]:
        cache["uploaded"][pack_id] = {}

    stickers = []
    has_animated = False
    new_count = 0
    skip_count = 0

    for i, file in enumerate(files, 1):
        sticker_name = f"sticker_{i:02d}.webp"
        sticker_path = pack_output / sticker_name
        file_hash = get_file_hash(file)
        file_key = file.name

        cached = cache["converted"][pack_id].get(file_key)
        if cached and cached.get("hash") == file_hash and sticker_path.exists():
            skip_count += 1
            if cached.get("animated"):
                has_animated = True

            if file_key in cache["uploaded"][pack_id]:
                url = cache["uploaded"][pack_id][file_key]
            else:
                remote_path = f"{storage_folder}/{pack_id}/{sticker_name}"
                url = upload_to_storage(bucket, sticker_path, remote_path)
                cache["uploaded"][pack_id][file_key] = url

            stickers.append({"image_file": sticker_name, "emojis": [""], "url": url})
            continue

        success, is_animated = convert_to_sticker(file, sticker_path)

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
            url = upload_to_storage(bucket, sticker_path, remote_path)
            cache["uploaded"][pack_id][file_key] = url

            stickers.append({"image_file": sticker_name, "emojis": [""], "url": url})

    if not stickers:
        print("   Hicbir sticker donusturulemedi")
        return None

    print(f"   {new_count} yeni, {skip_count} atlandi")

    # Tray
    tray_path = pack_output / "tray.webp"
    custom_tray = find_custom_tray(pack_dir)
    tray_source = custom_tray if custom_tray else files[0]

    tray_cache_key = f"{pack_id}_tray"
    source_hash = get_file_hash(tray_source)
    cached_tray = cache.get("tray", {}).get(tray_cache_key, {})

    if isinstance(cached_tray, str):
        cached_tray = {"url": cached_tray, "hash": ""}

    if cached_tray.get("hash") == source_hash and tray_path.exists():
        tray_url = cached_tray.get("url", "")
    else:
        if create_tray_image(tray_source, tray_path, force_bg_removal=True):
            remote_path = f"{storage_folder}/{pack_id}/tray.webp"
            tray_url = upload_to_storage(bucket, tray_path, remote_path, force_refresh=True)
            if "tray" not in cache:
                cache["tray"] = {}
            cache["tray"][tray_cache_key] = {"url": tray_url, "hash": source_hash}
        else:
            tray_url = stickers[0]["url"]

    # Firestore'a kaydet
    collection_name = "premium_stickers" if is_premium else "stickers"

    existing_doc = db.collection(collection_name).document(pack_id).get()
    existing_data = existing_doc.to_dict() if existing_doc.exists else {}

    # Yeni paket icin display_base olustur (500-600 arasi rastgele)
    display_base = existing_data.get("display_base")
    if display_base is None:
        display_base = random.randint(500, 600)

    pack_data = {
        "name": pack_name.replace("_", " ").replace("-", " ").title(),
        "publisher": PUBLISHER,
        "publisher_email": PUBLISHER_EMAIL,
        "privacy_policy_website": PRIVACY_POLICY,
        "license_agreement_website": LICENSE,
        "image_data_version": "1",
        "avoid_cache": False,
        "tray_image_file": "tray.webp",
        "tray_url": tray_url,
        "stickers": stickers,
        "isPremium": is_premium,
        "storagePath": storage_folder,
        "animated_sticker_pack": has_animated,
        "created_at": existing_data.get("created_at", datetime.now().strftime("%Y-%m-%d")),
        "sticker_count": len(stickers),
        "category": existing_data.get("category", ""),
        "download_count": existing_data.get("download_count", 0),  # Gercek indirme sayisi
        "display_base": display_base,  # Goruntuleme icin taban deger (500-600)
        "view_count": existing_data.get("view_count", 0),
        "favorite_count": existing_data.get("favorite_count", 0)
    }

    db.collection(collection_name).document(pack_id).set(pack_data)
    save_cache(cache)

    return pack_data

def menu_update_pack_names():
    """Paket adlarini guncelle"""
    print_header("PAKET ADLARINI GUNCELLE")

    bucket, db = init_firebase()
    if not db:
        wait_enter()
        return

    print("\n Mevcut paketler:")
    print("-" * 40)

    packs = []

    # Normal paketler
    for doc in db.collection("stickers").stream():
        data = doc.to_dict()
        packs.append((doc.id, data.get("name", doc.id), "stickers"))

    # Premium paketler
    for doc in db.collection("premium_stickers").stream():
        data = doc.to_dict()
        packs.append((doc.id, data.get("name", doc.id), "premium_stickers"))

    if not packs:
        print_warning("Paket bulunamadi!")
        wait_enter()
        return

    for i, (pack_id, name, collection) in enumerate(packs, 1):
        tag = "[P]" if collection == "premium_stickers" else "[N]"
        print(f"   {i}. {tag} {name} ({pack_id})")

    print("\n Degistirmek istediginiz paketin numarasini girin (0=iptal): ", end="")

    try:
        choice = int(input())
        if choice == 0:
            return
        if choice < 1 or choice > len(packs):
            print_error("Gecersiz secim!")
            wait_enter()
            return

        pack_id, old_name, collection = packs[choice - 1]
        print(f"\n Mevcut ad: {old_name}")
        print(" Yeni ad: ", end="")
        new_name = input().strip()

        if not new_name:
            print_error("Isim bos olamaz!")
            wait_enter()
            return

        db.collection(collection).document(pack_id).update({"name": new_name})
        print_success(f"Paket adi guncellendi: {old_name} -> {new_name}")

    except ValueError:
        print_error("Gecersiz giris!")

    wait_enter()

def menu_change_category():
    """Paket kategorisini degistir"""
    print_header("PAKET KATEGORISINI DEGISTIR")

    bucket, db = init_firebase()
    if not db:
        wait_enter()
        return

    categories = [
        ("", "Kategorisiz"),
        ("komik", "Komik"),
        ("romantik", "Romantik"),
        ("spor", "Spor"),
        ("dizi_film", "Dizi/Film"),
        ("hayvanlar", "Hayvanlar"),
        ("memeler", "Memeler"),
        ("gunluk", "Gunluk"),
        ("ozel_gun", "Ozel Gun"),
    ]

    print("\n Mevcut paketler:")
    print("-" * 50)

    packs = []

    # Normal paketler
    for doc in db.collection("stickers").stream():
        data = doc.to_dict()
        packs.append((doc.id, data.get("name", doc.id), data.get("category", ""), "stickers"))

    # Premium paketler
    for doc in db.collection("premium_stickers").stream():
        data = doc.to_dict()
        packs.append((doc.id, data.get("name", doc.id), data.get("category", ""), "premium_stickers"))

    if not packs:
        print_warning("Paket bulunamadi!")
        wait_enter()
        return

    for i, (pack_id, name, cat, collection) in enumerate(packs, 1):
        tag = "[P]" if collection == "premium_stickers" else "[N]"
        cat_name = next((c[1] for c in categories if c[0] == cat), cat or "Yok")
        print(f"   {i}. {tag} {name} - Kategori: {cat_name}")

    print("\n Degistirmek istediginiz paketin numarasini girin (0=iptal): ", end="")

    try:
        choice = int(input())
        if choice == 0:
            return
        if choice < 1 or choice > len(packs):
            print_error("Gecersiz secim!")
            wait_enter()
            return

        pack_id, name, old_cat, collection = packs[choice - 1]

        print(f"\n Paket: {name}")
        print("\n Kategoriler:")
        for i, (cat_id, cat_name) in enumerate(categories, 1):
            marker = " <--" if cat_id == old_cat else ""
            print(f"   {i}. {cat_name}{marker}")

        print("\n Yeni kategori numarasi (0=iptal): ", end="")
        cat_choice = int(input())

        if cat_choice == 0:
            return
        if cat_choice < 1 or cat_choice > len(categories):
            print_error("Gecersiz secim!")
            wait_enter()
            return

        new_cat = categories[cat_choice - 1][0]
        db.collection(collection).document(pack_id).update({"category": new_cat})
        print_success(f"Kategori guncellendi: {categories[cat_choice - 1][1]}")

    except ValueError:
        print_error("Gecersiz giris!")

    wait_enter()

def menu_reset_download_counts():
    """Indirme sayilarini sifirla"""
    print_header("INDIRME SAYILARINI SIFIRLA")

    print_warning("DIKKAT: Bu islem tum indirme sayilarini sifirlar!")
    print("\n Secenekler:")
    print("   [1] Sadece download_count'u sifirla (Gercek sayilar)")
    print("   [2] display_base degerlerini yeniden olustur (500-600)")
    print("   [3] Her ikisini de yap")
    print("   [0] Iptal")
    print("\n Seciminiz: ", end="")

    choice = input().strip()

    if choice == "0":
        return

    if choice not in ["1", "2", "3"]:
        print_error("Gecersiz secim!")
        wait_enter()
        return

    print("\n Onaylamak icin 'SIFIRLA' yazin: ", end="")
    confirm = input().strip()

    if confirm != "SIFIRLA":
        print_info("Islem iptal edildi.")
        wait_enter()
        return

    bucket, db = init_firebase()
    if not db:
        wait_enter()
        return

    updated = 0

    for collection_name in ["stickers", "premium_stickers"]:
        for doc in db.collection(collection_name).stream():
            updates = {}

            if choice in ["1", "3"]:
                updates["download_count"] = 0

            if choice in ["2", "3"]:
                updates["display_base"] = random.randint(500, 600)

            if updates:
                db.collection(collection_name).document(doc.id).update(updates)
                updated += 1
                print(f"   {doc.id}: Guncellendi")

    print_success(f"{updated} paket guncellendi")
    wait_enter()

def menu_github_sync():
    """GitHub reposunu guncelle"""
    print_header("GITHUB SENKRONIZASYONU")

    git_root = SCRIPT_DIR.parent

    if not (git_root / ".git").exists():
        print_error("Git repo bulunamadi!")
        wait_enter()
        return

    try:
        # Durum kontrol
        result = subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=git_root, capture_output=True, text=True
        )

        if not result.stdout.strip():
            print_success("Degisiklik yok")
            wait_enter()
            return

        print(" Degisiklikler:")
        print(result.stdout)

        # Commit mesaji
        print("\n Commit mesaji (bos birak = otomatik): ", end="")
        msg = input().strip()
        if not msg:
            msg = f"Sticker guncelleme - {datetime.now().strftime('%Y-%m-%d %H:%M')}"

        # Add, commit, push
        subprocess.run(["git", "add", "-A"], cwd=git_root, check=True, capture_output=True)
        subprocess.run(["git", "commit", "-m", msg], cwd=git_root, check=True, capture_output=True)

        print_info("Push yapiliyor...")
        result = subprocess.run(["git", "push"], cwd=git_root, capture_output=True, text=True)

        if result.returncode == 0:
            print_success("GitHub'a basariyla yuklendi!")
        else:
            print_error(f"Push hatasi: {result.stderr}")

    except subprocess.CalledProcessError as e:
        print_error(f"Git hatasi: {e}")

    wait_enter()

def menu_statistics():
    """Istatistik ekrani"""
    print_header("ISTATISTIK EKRANI")

    bucket, db = init_firebase()
    if not db:
        wait_enter()
        return

    # Istatistikleri topla
    total_packs = 0
    total_stickers = 0
    total_downloads = 0
    total_views = 0
    total_favorites = 0
    animated_packs = 0

    pack_stats = []

    for collection in ["stickers", "premium_stickers"]:
        for doc in db.collection(collection).stream():
            data = doc.to_dict()
            total_packs += 1
            sticker_count = data.get("sticker_count", len(data.get("stickers", [])))
            total_stickers += sticker_count
            downloads = data.get("download_count", 0)
            total_downloads += downloads
            views = data.get("view_count", 0)
            total_views += views
            favorites = data.get("favorite_count", 0)
            total_favorites += favorites
            display_base = data.get("display_base", 0)

            if data.get("animated_sticker_pack"):
                animated_packs += 1

            pack_stats.append({
                "id": doc.id,
                "name": data.get("name", doc.id),
                "downloads": downloads,
                "display_base": display_base,
                "display_total": downloads + display_base,
                "views": views,
                "favorites": favorites,
                "stickers": sticker_count,
                "premium": collection == "premium_stickers",
                "category": data.get("category", "")
            })

    # Genel istatistikler
    print("\n GENEL ISTATISTIKLER")
    print("-" * 40)
    print(f"   Toplam Paket       : {total_packs}")
    print(f"   Toplam Sticker     : {total_stickers}")
    print(f"   Animasyonlu Paket  : {animated_packs}")
    print(f"   Toplam Indirme     : {total_downloads} (gercek)")
    print(f"   Toplam Goruntulenme: {total_views}")
    print(f"   Toplam Favori      : {total_favorites}")

    # En populer paketler
    print("\n EN COK INDIRILEN PAKETLER (Gercek)")
    print("-" * 40)
    top_downloads = sorted(pack_stats, key=lambda x: x["downloads"], reverse=True)[:5]
    for i, p in enumerate(top_downloads, 1):
        tag = "[P]" if p["premium"] else "[N]"
        print(f"   {i}. {tag} {p['name']}: {p['downloads']} indirme")

    print("\n EN COK GORUNTULENEN PAKETLER")
    print("-" * 40)
    top_views = sorted(pack_stats, key=lambda x: x["views"], reverse=True)[:5]
    for i, p in enumerate(top_views, 1):
        tag = "[P]" if p["premium"] else "[N]"
        print(f"   {i}. {tag} {p['name']}: {p['views']} goruntulenme")

    # Detayli tablo
    print("\n TUM PAKETLER (Detayli)")
    print("=" * 95)
    print(f" {'Paket Adi':<25} {'Tip':>4} {'Kat':>6} {'Sticker':>8} {'Gercek':>8} {'Gosterim':>9} {'Goru.':>8}")
    print("=" * 95)

    # Sıralama seçeneği
    print("\n Siralama: [1] Ada gore  [2] Gercek indirmeye gore  [3] Goruntulenmeye gore")
    print(" Seciminiz (varsayilan=2): ", end="")
    sort_choice = input().strip()

    if sort_choice == "1":
        sorted_packs = sorted(pack_stats, key=lambda x: x["name"].lower())
    elif sort_choice == "3":
        sorted_packs = sorted(pack_stats, key=lambda x: x["views"], reverse=True)
    else:
        sorted_packs = sorted(pack_stats, key=lambda x: x["downloads"], reverse=True)

    print("\n" + "=" * 95)
    print(f" {'Paket Adi':<25} {'Tip':>4} {'Kat':>6} {'Sticker':>8} {'Gercek':>8} {'Gosterim':>9} {'Goru.':>8}")
    print("-" * 95)

    for p in sorted_packs:
        tag = "P" if p["premium"] else "N"
        name = p['name'][:23]
        cat = p['category'][:5] if p['category'] else "-"
        print(f" {name:<25} [{tag}] {cat:>6} {p['stickers']:>8} {p['downloads']:>8} {p['display_total']:>9} {p['views']:>8}")

    print("=" * 95)

    # Aciklamalar
    print("\n ACIKLAMALAR")
    print("-" * 40)
    print("   [N] = Normal (ucretsiz) paket")
    print("   [P] = Premium (ucretli) paket")
    print("   Gercek = Firebase'deki gercek indirme sayisi")
    print("   Gosterim = Uygulamada gosterilen sayi (Gercek + Taban)")
    print("   Goru. = Paket detay sayfasi goruntulenme")

    wait_enter()

def menu_download_from_drive():
    """Drive'dan stickerlari indir"""
    print_header("DRIVE'DAN STICKERLARI INDIR")

    service = init_drive()
    if not service:
        wait_enter()
        return

    print_info("Drive klasoru araniyor...")

    # Ana klasoru bul
    main_folder_id = get_or_create_drive_folder(service, DRIVE_FOLDER_NAME)
    print_success(f"Ana klasor: {DRIVE_FOLDER_NAME}")

    # Alt klasorleri listele
    items = list_drive_folder(service, main_folder_id)

    folders = [f for f in items if f['mimeType'] == 'application/vnd.google-apps.folder']

    if not folders:
        print_warning("Drive'da sticker klasoru bulunamadi!")
        print("   Oncelikle 'Yerel'den Drive'a Yukle' secenegini kullanin")
        wait_enter()
        return

    print(f"\n {len(folders)} klasor bulundu:")
    for folder in folders:
        print(f"   - {folder['name']}")

    print("\n Indiriliyor...")

    downloaded = 0
    for folder in folders:
        folder_name = folder['name']

        # Premium mi normal mi belirle
        if folder_name.startswith("premium_"):
            local_dir = PREMIUM_STICKERS_DIR / folder_name[8:]
            folder_name_clean = folder_name[8:]
        else:
            local_dir = STICKERS_DIR / folder_name
            folder_name_clean = folder_name

        local_dir.mkdir(parents=True, exist_ok=True)

        # Klasordeki dosyalari indir
        files = list_drive_folder(service, folder['id'])

        for file in files:
            if file['mimeType'] == 'application/vnd.google-apps.folder':
                continue

            dest_path = local_dir / file['name']

            if dest_path.exists():
                continue

            print(f"   {folder_name_clean}/{file['name']}", end=" ")
            try:
                download_file_from_drive(service, file['id'], dest_path)
                print("OK")
                downloaded += 1
            except Exception as e:
                print(f"HATA: {e}")

    print_success(f"{downloaded} dosya indirildi")
    wait_enter()

def check_folder_exists_in_drive(service, folder_name: str, parent_id: str) -> bool:
    """Drive'da klasor var mi kontrol et"""
    query = f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder' and '{parent_id}' in parents and trashed=false"
    results = service.files().list(q=query, spaces='drive', fields='files(id)').execute()
    return len(results.get('files', [])) > 0

def menu_upload_to_drive():
    """Yerel'den Drive'a yukle"""
    print_header("YEREL'DEN DRIVE'A YUKLE")

    service = init_drive()
    if not service:
        wait_enter()
        return

    # Ana klasoru bul/olustur
    main_folder_id = get_or_create_drive_folder(service, DRIVE_FOLDER_NAME)
    print_success(f"Ana klasor: {DRIVE_FOLDER_NAME}")

    uploaded_packs = 0
    skipped_packs = 0

    # Normal stickerlar
    if STICKERS_DIR.exists():
        for pack_dir in STICKERS_DIR.iterdir():
            if not pack_dir.is_dir():
                continue

            print(f"\n [NORMAL] {pack_dir.name}", end=" ")

            # Klasor Drive'da mevcut mu kontrol et
            if check_folder_exists_in_drive(service, pack_dir.name, main_folder_id):
                print("- ATLANDI (mevcut)")
                skipped_packs += 1
                continue

            print("- Yukleniyor...")
            folder_id = get_or_create_drive_folder(service, pack_dir.name, main_folder_id)

            files = [f for f in pack_dir.iterdir() if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS]

            for file in files:
                print(f"   {file.name}", end=" ")
                try:
                    upload_file_to_drive(service, file, folder_id)
                    print("OK")
                except Exception as e:
                    print(f"HATA: {e}")

            uploaded_packs += 1

    # Premium stickerlar
    if PREMIUM_STICKERS_DIR.exists():
        for pack_dir in PREMIUM_STICKERS_DIR.iterdir():
            if not pack_dir.is_dir():
                continue

            premium_folder_name = f"premium_{pack_dir.name}"
            print(f"\n [PREMIUM] {pack_dir.name}", end=" ")

            # Klasor Drive'da mevcut mu kontrol et
            if check_folder_exists_in_drive(service, premium_folder_name, main_folder_id):
                print("- ATLANDI (mevcut)")
                skipped_packs += 1
                continue

            print("- Yukleniyor...")
            folder_id = get_or_create_drive_folder(service, premium_folder_name, main_folder_id)

            files = [f for f in pack_dir.iterdir() if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS]

            for file in files:
                print(f"   {file.name}", end=" ")
                try:
                    upload_file_to_drive(service, file, folder_id)
                    print("OK")
                except Exception as e:
                    print(f"HATA: {e}")

            uploaded_packs += 1

    print_success(f"{uploaded_packs} paket yuklendi, {skipped_packs} paket atlandi (zaten mevcut)")
    wait_enter()

def menu_add_new_pack():
    """Yeni sticker paketi ekle"""
    print_header("YENI STICKER PAKETI EKLE")

    print("""
 STICKER EKLEME REHBERI
 ----------------------

 1. PAKET KLASORU OLUSTUR:
    - Normal paket icin: stickers/<paket-adi>/
    - Premium paket icin: premium_stickers/<paket-adi>/

 2. STICKER DOSYALARINI EKLE:
    - Desteklenen formatlar: MP4, GIF, PNG, JPG, WEBP
    - En az 3, en fazla 30 sticker
    - Dosyalar otomatik 512x512'ye donusturulur

 3. KAPAK RESMI (OPSIYONEL):
    - Klasore "tray.png" veya "tray.jpg" ekle
    - Yoksa ilk sticker kapak olarak kullanilir
    - Arka plan otomatik silinir

 4. ISLEM SIRASI:
    a) Stickerlari yerel klasore koy
    b) Menu 2: Firebase'e yukle
    c) Menu 7: Drive'a yedekle
    d) Menu 4: GitHub'a push et
""")

    print(" Ne yapmak istiyorsunuz?")
    print("   [1] Normal paket klasoru olustur")
    print("   [2] Premium paket klasoru olustur")
    print("   [3] Mevcut paketleri listele")
    print("   [0] Geri don")
    print("\n Seciminiz: ", end="")

    choice = input().strip()

    if choice == "1":
        print("\n Paket adi (klasor adi): ", end="")
        pack_name = input().strip()
        if not pack_name:
            print_error("Paket adi bos olamaz!")
            wait_enter()
            return

        pack_dir = STICKERS_DIR / pack_name
        if pack_dir.exists():
            print_warning(f"Bu paket zaten mevcut: {pack_dir}")
        else:
            pack_dir.mkdir(parents=True)
            print_success(f"Klasor olusturuldu: {pack_dir}")
            print("\n   Simdi bu klasore en az 3 sticker dosyasi koyun.")
            print("   Sonra Menu 2 ile Firebase'e yukleyin.")

    elif choice == "2":
        print("\n Premium paket adi (klasor adi): ", end="")
        pack_name = input().strip()
        if not pack_name:
            print_error("Paket adi bos olamaz!")
            wait_enter()
            return

        pack_dir = PREMIUM_STICKERS_DIR / pack_name
        if pack_dir.exists():
            print_warning(f"Bu paket zaten mevcut: {pack_dir}")
        else:
            pack_dir.mkdir(parents=True)
            print_success(f"Klasor olusturuldu: {pack_dir}")
            print("\n   Simdi bu klasore en az 3 sticker dosyasi koyun.")
            print("   Sonra Menu 2 ile Firebase'e yukleyin.")

    elif choice == "3":
        print("\n MEVCUT PAKETLER")
        print("-" * 50)

        # Normal paketler
        if STICKERS_DIR.exists():
            normal_packs = [d for d in STICKERS_DIR.iterdir() if d.is_dir()]
            if normal_packs:
                print("\n Normal Paketler:")
                for pack in sorted(normal_packs):
                    files = get_sticker_files(pack)
                    tray = "+" if find_custom_tray(pack) else "-"
                    print(f"   [{tray}] {pack.name} ({len(files)} sticker)")

        # Premium paketler
        if PREMIUM_STICKERS_DIR.exists():
            premium_packs = [d for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()]
            if premium_packs:
                print("\n Premium Paketler:")
                for pack in sorted(premium_packs):
                    files = get_sticker_files(pack)
                    tray = "+" if find_custom_tray(pack) else "-"
                    print(f"   [{tray}] {pack.name} ({len(files)} sticker)")

        print("\n   [+] = Ozel tray var, [-] = Otomatik tray")

    wait_enter()

def menu_delete_pack():
    """Sticker paketi sil"""
    print_header("STICKER PAKETI SIL")

    print_warning("DIKKAT: Bu islem geri alinamaz!")
    print("\n Silme islemleri:")
    print("   - Yerel klasorden siler")
    print("   - Firebase'den siler (Storage + Firestore)")
    print("   - Drive'dan siler (opsiyonel)")
    print("   - Cache'den siler")

    # Tum paketleri listele
    all_packs = []

    # Yerel paketler
    if STICKERS_DIR.exists():
        for d in STICKERS_DIR.iterdir():
            if d.is_dir():
                all_packs.append({"name": d.name, "path": d, "premium": False, "source": "yerel"})

    if PREMIUM_STICKERS_DIR.exists():
        for d in PREMIUM_STICKERS_DIR.iterdir():
            if d.is_dir():
                all_packs.append({"name": d.name, "path": d, "premium": True, "source": "yerel"})

    # Firebase'deki paketleri de kontrol et
    bucket, db = init_firebase()
    if db:
        try:
            for doc in db.collection("stickers").stream():
                pack_id = doc.id
                found = False
                for p in all_packs:
                    if p["name"].lower().replace(" ", "_").replace("-", "_") == pack_id:
                        found = True
                        break
                if not found:
                    data = doc.to_dict()
                    all_packs.append({"name": data.get("name", pack_id), "pack_id": pack_id, "premium": False, "source": "firebase"})

            for doc in db.collection("premium_stickers").stream():
                pack_id = doc.id
                found = False
                for p in all_packs:
                    if p["name"].lower().replace(" ", "_").replace("-", "_") == pack_id:
                        found = True
                        break
                if not found:
                    data = doc.to_dict()
                    all_packs.append({"name": data.get("name", pack_id), "pack_id": pack_id, "premium": True, "source": "firebase"})
        except:
            pass

    if not all_packs:
        print_warning("Silinecek paket bulunamadi!")
        wait_enter()
        return

    print("\n MEVCUT PAKETLER:")
    print("-" * 50)
    for i, p in enumerate(all_packs, 1):
        tag = "[P]" if p["premium"] else "[N]"
        src = f"({p['source']})"
        print(f"   {i}. {tag} {p['name']} {src}")

    print("\n Silmek istediginiz paketin numarasini girin (0=iptal): ", end="")

    try:
        choice = int(input())
        if choice == 0:
            return
        if choice < 1 or choice > len(all_packs):
            print_error("Gecersiz secim!")
            wait_enter()
            return

        pack = all_packs[choice - 1]
        pack_name = pack["name"]
        pack_id = pack.get("pack_id", pack_name.lower().replace(" ", "_").replace("-", "_"))
        is_premium = pack["premium"]

        print(f"\n '{pack_name}' paketini silmek istediginizden emin misiniz?")
        print(" Bu islem GERI ALINAMAZ!")
        print("\n Onaylamak icin 'SIL' yazin: ", end="")

        confirm = input().strip()
        if confirm != "SIL":
            print_info("Islem iptal edildi.")
            wait_enter()
            return

        deleted_from = []

        # 1. Yerel klasorden sil
        if "path" in pack and pack["path"].exists():
            shutil.rmtree(pack["path"])
            deleted_from.append("Yerel")

        # 2. Output klasorunden sil
        output_dir = OUTPUT_DIR / pack_id
        if output_dir.exists():
            shutil.rmtree(output_dir)
            deleted_from.append("Output")

        # 3. Firebase'den sil
        if bucket and db:
            collection = "premium_stickers" if is_premium else "stickers"
            storage_folder = "premium_stickers" if is_premium else "stickers"

            # Firestore'dan sil
            try:
                db.collection(collection).document(pack_id).delete()
                deleted_from.append("Firestore")
            except Exception as e:
                print_warning(f"Firestore silme hatasi: {e}")

            # Storage'dan sil
            try:
                blobs = bucket.list_blobs(prefix=f"{storage_folder}/{pack_id}/")
                for blob in blobs:
                    blob.delete()
                deleted_from.append("Storage")
            except Exception as e:
                print_warning(f"Storage silme hatasi: {e}")

        # 4. Cache'den sil
        cache = load_cache()
        if pack_id in cache.get("converted", {}):
            del cache["converted"][pack_id]
        if pack_id in cache.get("uploaded", {}):
            del cache["uploaded"][pack_id]
        tray_key = f"{pack_id}_tray"
        if tray_key in cache.get("tray", {}):
            del cache["tray"][tray_key]
        save_cache(cache)
        deleted_from.append("Cache")

        # 5. Drive'dan sil (opsiyonel)
        print("\n Drive'dan da silmek istiyor musunuz? (e/h): ", end="")
        delete_drive = input().strip().lower() == 'e'

        if delete_drive:
            service = init_drive()
            if service:
                try:
                    main_folder_id = get_or_create_drive_folder(service, DRIVE_FOLDER_NAME)
                    drive_folder_name = f"premium_{pack_name}" if is_premium else pack_name

                    # Klasoru bul
                    query = f"name='{drive_folder_name}' and '{main_folder_id}' in parents and trashed=false"
                    results = service.files().list(q=query, spaces='drive', fields='files(id)').execute()
                    folders = results.get('files', [])

                    if folders:
                        service.files().delete(fileId=folders[0]['id']).execute()
                        deleted_from.append("Drive")
                except Exception as e:
                    print_warning(f"Drive silme hatasi: {e}")

        print_success(f"'{pack_name}' paketi silindi!")
        print(f"   Silinen yerler: {', '.join(deleted_from)}")

    except ValueError:
        print_error("Gecersiz giris!")

    wait_enter()

def menu_full_sync():
    """Tam senkronizasyon"""
    print_header("TAM SENKRONIZASYON")

    print_info("1/4 - Drive'dan indiriliyor...")
    menu_download_from_drive()

    print_info("2/4 - Tray'ler guncelleniyor...")
    menu_update_trays()

    print_info("3/4 - Stickerlar Firebase'e yukleniyor...")
    menu_update_stickers()

    print_info("4/4 - GitHub senkronize ediliyor...")
    menu_github_sync()

    print_success("Tam senkronizasyon tamamlandi!")
    wait_enter()

# ============================================================================
# ANA PROGRAM
# ============================================================================

def main():
    """Ana program"""
    while True:
        clear_screen()
        print(LOGO)
        print(MENU)

        print(" Seciminiz: ", end="")

        try:
            choice = input().strip().upper()

            if choice == "0":
                clear_screen()
                print("\n Gule gule!\n")
                break
            elif choice == "1":
                menu_update_trays()
            elif choice == "2":
                menu_update_stickers()
            elif choice == "3":
                menu_update_pack_names()
            elif choice == "4":
                menu_github_sync()
            elif choice == "5":
                menu_statistics()
            elif choice == "6":
                menu_download_from_drive()
            elif choice == "7":
                menu_upload_to_drive()
            elif choice == "8":
                menu_add_new_pack()
            elif choice == "9":
                menu_delete_pack()
            elif choice == "C":
                menu_change_category()
            elif choice == "R":
                menu_reset_download_counts()
            elif choice == "F":
                menu_full_sync()
            else:
                print_error("Gecersiz secim!")
                wait_enter()

        except KeyboardInterrupt:
            clear_screen()
            print("\n Gule gule!\n")
            break
        except Exception as e:
            print_error(f"Beklenmeyen hata: {e}")
            import traceback
            traceback.print_exc()
            wait_enter()

if __name__ == "__main__":
    main()
