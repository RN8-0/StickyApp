#!/usr/bin/env python3
"""
Sticker Otomatik Dönüştürücü ve Firebase Yükleyici
===================================================
Desteklenen formatlar:
- Video: MP4, MOV, AVI, MKV, WEBM, MPEG, GIF (animasyonlu)
- Resim: PNG, JPG, JPEG, WEBP, BMP (statik sticker)

Özellikler:
- Otomatik format dönüşümü
- Özel tray (kapak) resmi desteği
- Silinen dosyaları Firebase'den otomatik temizleme
- Cache sistemi (aynı dosya tekrar işlenmez)
- Otomatik arka plan silme (SADECE resimler için - video ve GIF'lere uygulanmaz)

Kullanım: python3 upload_stickers.py
"""

import os
import sys
import subprocess
import json
import hashlib
import tempfile
from pathlib import Path
from datetime import datetime
from io import BytesIO

# Firebase Admin SDK
try:
    import firebase_admin
    from firebase_admin import credentials, storage, firestore
except ImportError:
    print("Firebase Admin SDK yüklü değil!")
    print("   Çalıştır: pip install firebase-admin")
    sys.exit(1)

# Arka plan silme için rembg
try:
    from rembg import remove as remove_bg
    from PIL import Image
    REMBG_AVAILABLE = True
except ImportError:
    REMBG_AVAILABLE = False
    print("⚠️  rembg yüklü değil - arka plan silme devre dışı")
    print("   Yüklemek için: pip install rembg")

# Ayarlar
SCRIPT_DIR = Path(__file__).parent
STICKERS_DIR = SCRIPT_DIR / "stickers"
PREMIUM_STICKERS_DIR = SCRIPT_DIR / "premium_stickers"
OUTPUT_DIR = SCRIPT_DIR / "output"
CACHE_FILE = SCRIPT_DIR / "cache.json"

# Service Account Key'i otomatik bul
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

# Arka plan silme ayarı (True = aktif, False = devre dışı)
REMOVE_BACKGROUND = True

# Desteklenen formatlar
VIDEO_EXTENSIONS = {'.mp4', '.mov', '.avi', '.mkv', '.webm', '.mpeg', '.mpg', '.m4v'}
IMAGE_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}
GIF_EXTENSION = '.gif'
ALL_EXTENSIONS = VIDEO_EXTENSIONS | IMAGE_EXTENSIONS | {GIF_EXTENSION}


def load_cache() -> dict:
    """Cache dosyasını yükle"""
    if CACHE_FILE.exists():
        try:
            with open(CACHE_FILE, "r") as f:
                return json.load(f)
        except:
            pass
    return {"converted": {}, "uploaded": {}, "tray": {}}


def save_cache(cache: dict):
    """Cache dosyasını kaydet"""
    with open(CACHE_FILE, "w") as f:
        json.dump(cache, f, indent=2, ensure_ascii=False)


def get_file_hash(file_path: Path) -> str:
    """Dosyanın MD5 hash'ini al"""
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


def is_animated_gif(file_path: Path) -> bool:
    """GIF'in animasyonlu olup olmadığını kontrol et"""
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
    """Dosya tipini belirle: 'video', 'image', 'animated_gif'"""
    ext = file_path.suffix.lower()

    if ext in VIDEO_EXTENSIONS:
        return 'video'
    elif ext == GIF_EXTENSION:
        return 'animated_gif' if is_animated_gif(file_path) else 'image'
    elif ext in IMAGE_EXTENSIONS:
        return 'image'
    return None


def is_tray_file(file_path: Path) -> bool:
    """Dosya tray dosyası mı kontrol et"""
    name = file_path.stem.lower()
    return name == 'tray'


def get_image_dimensions(file_path: Path) -> tuple:
    """Resim dosyasının boyutlarını döndür (width, height)"""
    try:
        from PIL import Image
        with Image.open(file_path) as img:
            return img.size
    except:
        return (0, 0)


def get_video_dimensions(file_path: Path) -> tuple:
    """Video/GIF dosyasının boyutlarını döndür (width, height)"""
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


def is_already_correct_size(file_path: Path, file_type: str) -> bool:
    """Dosya zaten 512x512 mi kontrol et"""
    if file_type in ['video', 'animated_gif']:
        width, height = get_video_dimensions(file_path)
    else:
        width, height = get_image_dimensions(file_path)

    return width == STICKER_SIZE and height == STICKER_SIZE


def remove_background_from_image(input_path: Path) -> Path:
    """Resimden arka planı sil, geçici dosya döndür"""
    if not REMBG_AVAILABLE or not REMOVE_BACKGROUND:
        return input_path

    try:
        # Resmi aç
        with Image.open(input_path) as img:
            # RGBA'ya çevir (şeffaflık için)
            if img.mode != 'RGBA':
                img = img.convert('RGBA')

            # Arka planı sil
            output = remove_bg(img)

            # Geçici dosyaya kaydet
            temp_file = tempfile.NamedTemporaryFile(suffix='.png', delete=False)
            output.save(temp_file.name, 'PNG')
            return Path(temp_file.name)
    except Exception as e:
        print(f"Arka plan silme hatası: {e}")
        return input_path


def remove_background_from_gif(input_path: Path) -> Path:
    """Animasyonlu GIF'in her karesinden arka planı sil"""
    if not REMBG_AVAILABLE or not REMOVE_BACKGROUND:
        return input_path

    try:
        with Image.open(input_path) as gif:
            # GIF mi kontrol et
            if not hasattr(gif, 'n_frames') or gif.n_frames <= 1:
                return remove_background_from_image(input_path)

            frames = []
            durations = []

            # Her kareyi işle
            for frame_idx in range(min(gif.n_frames, 30)):  # Max 30 kare
                gif.seek(frame_idx)

                # Kare süresini al
                duration = gif.info.get('duration', 100)
                durations.append(duration)

                # Kareyi RGBA'ya çevir
                frame = gif.convert('RGBA')

                # Arka planı sil
                frame_no_bg = remove_bg(frame)
                frames.append(frame_no_bg)

            if not frames:
                return input_path

            # Yeni GIF olarak kaydet
            temp_file = tempfile.NamedTemporaryFile(suffix='.gif', delete=False)
            frames[0].save(
                temp_file.name,
                save_all=True,
                append_images=frames[1:],
                duration=durations,
                loop=0,
                disposal=2  # Her kareyi temizle (şeffaflık için önemli)
            )
            return Path(temp_file.name)

    except Exception as e:
        print(f"GIF arka plan silme hatası: {e}")
        return input_path


def convert_video_to_sticker(input_path: Path, output_path: Path) -> bool:
    """Video/Animated GIF'i animated WebP'ye dönüştür (arka plan silme yok - sadece dönüştürme)"""
    try:
        processed_path = input_path

        # Boyut kontrolü: Eğer zaten 512x512 ise, resize yapma
        width, height = get_video_dimensions(input_path)
        is_correct_size = (width == STICKER_SIZE and height == STICKER_SIZE)

        # Video/GIF için arka plan silme yapılmıyor - direkt dönüştürülüyor

        # 512x512 ise resize filtresi kullanma
        if is_correct_size:
            cmd = [
                "ffmpeg", "-y", "-i", str(processed_path),
                "-t", str(MAX_DURATION),
                "-vf", f"fps={FPS}",
                "-loop", "0",
                "-c:v", "libwebp",
                "-lossless", "0",
                "-quality", "70",
                "-an",
                str(output_path)
            ]
        else:
            # Normal resize işlemi
            cmd = [
                "ffmpeg", "-y", "-i", str(processed_path),
                "-t", str(MAX_DURATION),
                "-vf", f"scale={STICKER_SIZE}:{STICKER_SIZE}:force_original_aspect_ratio=decrease,"
                       f"pad={STICKER_SIZE}:{STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000,"
                       f"fps={FPS}",
                "-loop", "0",
                "-c:v", "libwebp",
                "-lossless", "0",
                "-quality", "70",
                "-an",
                str(output_path)
            ]
        subprocess.run(cmd, capture_output=True, check=True)

        # Boyut kontrolü ve kalite düşürme
        size_kb = output_path.stat().st_size / 1024
        if size_kb > MAX_FILE_SIZE_KB:
            for quality, fps in [(50, 8), (35, 6), (25, 5)]:
                if is_correct_size:
                    cmd = [
                        "ffmpeg", "-y", "-i", str(processed_path),
                        "-t", str(MAX_DURATION),
                        "-vf", f"fps={fps}",
                        "-loop", "0",
                        "-c:v", "libwebp",
                        "-lossless", "0",
                        "-quality", str(quality),
                        "-an",
                        str(output_path)
                    ]
                else:
                    cmd = [
                        "ffmpeg", "-y", "-i", str(processed_path),
                        "-t", str(MAX_DURATION),
                        "-vf", f"scale={STICKER_SIZE}:{STICKER_SIZE}:force_original_aspect_ratio=decrease,"
                               f"pad={STICKER_SIZE}:{STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000,"
                               f"fps={fps}",
                        "-loop", "0",
                        "-c:v", "libwebp",
                        "-lossless", "0",
                        "-quality", str(quality),
                        "-an",
                        str(output_path)
                    ]
                subprocess.run(cmd, capture_output=True, check=True)
                size_kb = output_path.stat().st_size / 1024
                if size_kb <= MAX_FILE_SIZE_KB:
                    break

        return True
    except subprocess.CalledProcessError:
        return False


def convert_image_to_sticker(input_path: Path, output_path: Path) -> bool:
    """Resmi statik WebP'ye dönüştür (arka plan silme dahil)"""
    temp_path = None
    try:
        # Boyut kontrolü: Eğer zaten 512x512 ise ve WebP ise, sadece kopyala
        width, height = get_image_dimensions(input_path)
        is_correct_size = (width == STICKER_SIZE and height == STICKER_SIZE)
        is_webp = input_path.suffix.lower() == '.webp'

        # Eğer zaten 512x512 WebP ve arka plan silme kapalıysa, direkt kopyala
        if is_correct_size and is_webp and not REMOVE_BACKGROUND:
            import shutil
            shutil.copy2(input_path, output_path)
            return True

        # Arka planı sil (aktifse)
        processed_path = remove_background_from_image(input_path)
        if processed_path != input_path:
            temp_path = processed_path  # Temizlenmesi gereken geçici dosya

        # Eğer 512x512 ise ama format dönüşümü gerekiyorsa, resize etme
        if is_correct_size:
            cmd = [
                "ffmpeg", "-y", "-i", str(processed_path),
                "-c:v", "libwebp",
                "-lossless", "0",
                "-quality", "90",
                str(output_path)
            ]
        else:
            # Normal resize işlemi
            cmd = [
                "ffmpeg", "-y", "-i", str(processed_path),
                "-vf", f"scale={STICKER_SIZE}:{STICKER_SIZE}:force_original_aspect_ratio=decrease,"
                       f"pad={STICKER_SIZE}:{STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                "-c:v", "libwebp",
                "-lossless", "0",
                "-quality", "90",
                str(output_path)
            ]
        subprocess.run(cmd, capture_output=True, check=True)

        # Boyut kontrolü
        size_kb = output_path.stat().st_size / 1024
        if size_kb > MAX_FILE_SIZE_KB:
            for quality in [70, 50, 35]:
                if is_correct_size:
                    cmd = [
                        "ffmpeg", "-y", "-i", str(processed_path),
                        "-c:v", "libwebp",
                        "-lossless", "0",
                        "-quality", str(quality),
                        str(output_path)
                    ]
                else:
                    cmd = [
                        "ffmpeg", "-y", "-i", str(processed_path),
                        "-vf", f"scale={STICKER_SIZE}:{STICKER_SIZE}:force_original_aspect_ratio=decrease,"
                               f"pad={STICKER_SIZE}:{STICKER_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                        "-c:v", "libwebp",
                        "-lossless", "0",
                        "-quality", str(quality),
                        str(output_path)
                    ]
                subprocess.run(cmd, capture_output=True, check=True)
                size_kb = output_path.stat().st_size / 1024
                if size_kb <= MAX_FILE_SIZE_KB:
                    break

        return True
    except subprocess.CalledProcessError:
        return False
    finally:
        # Geçici dosyayı temizle
        if temp_path and temp_path.exists():
            try:
                temp_path.unlink()
            except:
                pass


def convert_to_sticker(input_path: Path, output_path: Path) -> tuple:
    """Dosyayı sticker'a dönüştür, (success, is_animated) döner"""
    file_type = get_file_type(input_path)

    if file_type == 'video' or file_type == 'animated_gif':
        success = convert_video_to_sticker(input_path, output_path)
        return success, True
    elif file_type == 'image':
        success = convert_image_to_sticker(input_path, output_path)
        return success, False

    return False, False


def create_tray_image(input_path: Path, output_path: Path) -> bool:
    """Tray image (96x96 paket ikonu) oluştur (arka plan silme dahil)"""
    temp_path = None
    try:
        file_type = get_file_type(input_path)
        processed_path = input_path

        # Resim ise arka planı sil
        if file_type == 'image':
            processed_path = remove_background_from_image(input_path)
            if processed_path != input_path:
                temp_path = processed_path

        if file_type in ['video', 'animated_gif']:
            cmd = [
                "ffmpeg", "-y", "-i", str(processed_path),
                "-vf", f"scale={TRAY_SIZE}:{TRAY_SIZE}:force_original_aspect_ratio=decrease,"
                       f"pad={TRAY_SIZE}:{TRAY_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                "-frames:v", "1",
                "-c:v", "libwebp",
                str(output_path)
            ]
        else:
            cmd = [
                "ffmpeg", "-y", "-i", str(processed_path),
                "-vf", f"scale={TRAY_SIZE}:{TRAY_SIZE}:force_original_aspect_ratio=decrease,"
                       f"pad={TRAY_SIZE}:{TRAY_SIZE}:(ow-iw)/2:(oh-ih)/2:color=0x00000000",
                "-c:v", "libwebp",
                str(output_path)
            ]

        subprocess.run(cmd, capture_output=True, check=True)
        return True
    except subprocess.CalledProcessError:
        return False
    finally:
        # Geçici dosyayı temizle
        if temp_path and temp_path.exists():
            try:
                temp_path.unlink()
            except:
                pass


def find_custom_tray(pack_dir: Path) -> Path:
    """Özel tray dosyası var mı kontrol et (tray.png, tray.jpg vs.)"""
    for f in pack_dir.iterdir():
        if f.is_file() and is_tray_file(f) and f.suffix.lower() in (IMAGE_EXTENSIONS | {GIF_EXTENSION}):
            return f
    return None


def init_firebase():
    """Firebase'i başlat"""
    if SERVICE_ACCOUNT_KEY is None or not SERVICE_ACCOUNT_KEY.exists():
        print("Service Account Key bulunamadı!")
        print(f"   Bu klasöre firebase-adminsdk içeren JSON dosyası koy:")
        print(f"   {SCRIPT_DIR}/")
        print(f"\n   Firebase Console > Project Settings > Service Accounts")
        print(f"   > Generate New Private Key")
        sys.exit(1)

    cred = credentials.Certificate(str(SERVICE_ACCOUNT_KEY))
    firebase_admin.initialize_app(cred, {
        'storageBucket': STORAGE_BUCKET
    })

    return storage.bucket(), firestore.client()


def upload_to_storage(bucket, local_path: Path, remote_path: str, force_refresh: bool = False) -> str:
    """Firebase Storage'a yükle ve URL döndür"""
    blob = bucket.blob(remote_path)

    # Eğer force_refresh ise, önce blob'u sil (cache temizlemek için)
    if force_refresh:
        try:
            blob.delete()
        except:
            pass
        blob = bucket.blob(remote_path)

    # Cache-control header'ı ekle - browser cache'ini engelle
    blob.cache_control = "no-cache, no-store, must-revalidate"
    blob.upload_from_filename(str(local_path), content_type="image/webp")
    blob.make_public()

    # Cache busting için timestamp ekle
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


def get_sticker_files(pack_dir: Path) -> list:
    """Paketteki sticker dosyalarını bul (tray hariç)"""
    files = []
    for f in pack_dir.iterdir():
        if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS:
            if not is_tray_file(f):
                files.append(f)

    return sorted(files, key=lambda x: x.name.lower())


def get_local_pack_ids() -> set:
    """Lokaldeki paket ID'lerini al (hem normal hem premium)"""
    pack_ids = set()

    # Normal paketler
    if STICKERS_DIR.exists():
        for d in STICKERS_DIR.iterdir():
            if d.is_dir():
                pack_id = d.name.lower().replace(" ", "_").replace("-", "_")
                pack_ids.add(pack_id)

    # Premium paketler
    if PREMIUM_STICKERS_DIR.exists():
        for d in PREMIUM_STICKERS_DIR.iterdir():
            if d.is_dir():
                pack_id = d.name.lower().replace(" ", "_").replace("-", "_")
                pack_ids.add(pack_id)

    return pack_ids


def cleanup_deleted_packs(bucket, db, cache: dict, local_pack_ids: set):
    """Silinen paketleri Firebase'den temizle"""
    print("\n Silinen paketler kontrol ediliyor...")

    # Her iki koleksiyondaki paketleri al
    firebase_pack_ids = set()
    try:
        # Normal paketler
        docs = db.collection("stickers").stream()
        for doc in docs:
            firebase_pack_ids.add(doc.id)
        # Premium paketler
        docs = db.collection("premium_stickers").stream()
        for doc in docs:
            firebase_pack_ids.add(doc.id)
        # Eski koleksiyon (geriye uyumluluk)
        docs = db.collection("sticker_packs").stream()
        for doc in docs:
            firebase_pack_ids.add(doc.id)
    except:
        pass

    # Silinen paketleri bul
    deleted_packs = firebase_pack_ids - local_pack_ids

    if not deleted_packs:
        print("   Silinen paket yok")
        return

    for pack_id in deleted_packs:
        print(f"   Siliniyor: {pack_id}...", end=" ")

        # Firestore'dan sil (her üç koleksiyondan)
        for collection in ["stickers", "premium_stickers", "sticker_packs"]:
            try:
                db.collection(collection).document(pack_id).delete()
            except:
                pass

        # Storage'dan sil (hem stickers hem premium_stickers klasörlerinden)
        for folder in ["stickers", "premium_stickers"]:
            try:
                blobs = bucket.list_blobs(prefix=f"{folder}/{pack_id}/")
                for blob in blobs:
                    blob.delete()
            except:
                pass

        # Cache'den sil
        if pack_id in cache.get("converted", {}):
            del cache["converted"][pack_id]
        if pack_id in cache.get("uploaded", {}):
            del cache["uploaded"][pack_id]

        # Tray cache'den sil
        tray_key = f"{pack_id}_tray"
        if tray_key in cache.get("tray", {}):
            del cache["tray"][tray_key]

        # Output klasöründen sil
        output_dir = OUTPUT_DIR / pack_id
        if output_dir.exists():
            import shutil
            shutil.rmtree(output_dir)

        print("Silindi")

    save_cache(cache)


def process_pack(pack_dir: Path, bucket, db, cache: dict, is_premium: bool = False):
    """Bir paket klasörünü işle"""
    pack_name = pack_dir.name
    pack_id = pack_name.lower().replace(" ", "_").replace("-", "_")
    pack_type = "🌟 PREMIUM" if is_premium else "📦 NORMAL"

    # Firebase Storage klasör yolu - premium için ayrı klasör
    storage_folder = "premium_stickers" if is_premium else "stickers"

    print(f"\n {pack_type} Paket: {pack_name}")

    # Sticker dosyalarını bul
    files = get_sticker_files(pack_dir)

    if not files:
        print(f"   Dosya bulunamadı, atlanıyor")
        return None

    if len(files) < 3:
        print(f"   En az 3 sticker gerekli (WhatsApp kuralı), atlanıyor")
        return None

    if len(files) > 30:
        print(f"   Maksimum 30 sticker, ilk 30 alınacak")
        files = files[:30]

    # Çıktı klasörü
    pack_output = OUTPUT_DIR / pack_id
    pack_output.mkdir(parents=True, exist_ok=True)

    # Pack cache'i başlat
    if pack_id not in cache["converted"]:
        cache["converted"][pack_id] = {}
    if pack_id not in cache["uploaded"]:
        cache["uploaded"][pack_id] = {}

    # Silinen stickerları tespit et
    current_file_names = {f.name for f in files}
    cached_file_names = set(cache["converted"].get(pack_id, {}).keys())
    deleted_files = cached_file_names - current_file_names

    if deleted_files:
        print(f"   {len(deleted_files)} sticker silindi, temizleniyor...")
        for file_name in deleted_files:
            del cache["converted"][pack_id][file_name]
            if file_name in cache["uploaded"].get(pack_id, {}):
                del cache["uploaded"][pack_id][file_name]

    stickers = []
    new_conversions = 0
    skipped = 0
    has_animated = False

    for i, file in enumerate(files, 1):
        sticker_name = f"sticker_{i:02d}.webp"
        sticker_path = pack_output / sticker_name
        file_hash = get_file_hash(file)
        file_key = file.name

        print(f"   [{i}/{len(files)}] {file.name}", end=" ")

        # Cache kontrolü
        cached = cache["converted"][pack_id].get(file_key)
        if cached and cached.get("hash") == file_hash and sticker_path.exists():
            size_kb = sticker_path.stat().st_size / 1024
            file_type = "animated" if cached.get("animated") else "static"
            print(f"Atlandı ({size_kb:.0f}KB, {file_type})")
            skipped += 1

            if cached.get("animated"):
                has_animated = True

            if file_key in cache["uploaded"][pack_id]:
                url = cache["uploaded"][pack_id][file_key]
            else:
                remote_path = f"{storage_folder}/{pack_id}/{sticker_name}"
                url = upload_to_storage(bucket, sticker_path, remote_path)
                cache["uploaded"][pack_id][file_key] = url

            stickers.append({
                "image_file": sticker_name,
                "emojis": ["😀"],
                "url": url
            })
            continue

        # Dönüştür
        success, is_animated = convert_to_sticker(file, sticker_path)

        if success:
            size_kb = sticker_path.stat().st_size / 1024
            file_type = "animated" if is_animated else "static"
            print(f"OK ({size_kb:.0f}KB, {file_type})")
            new_conversions += 1

            if is_animated:
                has_animated = True

            cache["converted"][pack_id][file_key] = {
                "hash": file_hash,
                "output": sticker_name,
                "size_kb": size_kb,
                "animated": is_animated
            }

            remote_path = f"{storage_folder}/{pack_id}/{sticker_name}"
            url = upload_to_storage(bucket, sticker_path, remote_path)
            cache["uploaded"][pack_id][file_key] = url

            stickers.append({
                "image_file": sticker_name,
                "emojis": ["😀"],
                "url": url
            })
        else:
            print("HATA")

    if not stickers:
        print(f"   Hiçbir sticker dönüştürülemedi")
        return None

    print(f"   Sonuç: {new_conversions} yeni, {skipped} atlandı")

    # Tray image
    tray_name = "tray.webp"
    tray_path = pack_output / tray_name
    tray_cache_key = f"{pack_id}_tray"

    custom_tray = find_custom_tray(pack_dir)

    if custom_tray:
        custom_tray_hash = get_file_hash(custom_tray)
        cached_tray = cache.get("tray", {}).get(tray_cache_key)

        # Eski cache formatını kontrol et
        if isinstance(cached_tray, str):
            cached_tray = {"url": cached_tray, "hash": ""}
        elif cached_tray is None:
            cached_tray = {}

        if cached_tray.get("hash") == custom_tray_hash and tray_path.exists():
            print(f"   Tray: Atlandı (özel)")
            tray_url = cached_tray.get("url", "")
        else:
            print(f"   Tray: {custom_tray.name} kullanılıyor (GÜNCELLEME)...", end=" ")
            if create_tray_image(custom_tray, tray_path):
                print("OK")
                remote_path = f"{storage_folder}/{pack_id}/{tray_name}"
                # Tray değişti - force_refresh=True ile yükle (CDN cache temizle)
                tray_url = upload_to_storage(bucket, tray_path, remote_path, force_refresh=True)
                if "tray" not in cache:
                    cache["tray"] = {}
                cache["tray"][tray_cache_key] = {"url": tray_url, "hash": custom_tray_hash}
            else:
                print("HATA")
                tray_url = stickers[0]["url"]
                tray_name = stickers[0]["image_file"]
    else:
        cached_tray = cache.get("tray", {}).get(tray_cache_key)
        if isinstance(cached_tray, str):
            cached_tray = {"url": cached_tray, "hash": "auto"}

        # Otomatik tray için de kaynak dosyanın hash'ini kontrol et
        first_file_hash = get_file_hash(files[0]) if files else "auto"
        if cached_tray and cached_tray.get("hash") == first_file_hash and tray_path.exists():
            print(f"   Tray: Atlandı (otomatik)")
            tray_url = cached_tray.get("url", "")
        else:
            print(f"   Tray: Otomatik oluşturuluyor...", end=" ")
            if create_tray_image(files[0], tray_path):
                print("OK")
                remote_path = f"{storage_folder}/{pack_id}/{tray_name}"
                # Tray değişti - force_refresh=True ile yükle (CDN cache temizle)
                tray_url = upload_to_storage(bucket, tray_path, remote_path, force_refresh=True)
                if "tray" not in cache:
                    cache["tray"] = {}
                cache["tray"][tray_cache_key] = {"url": tray_url, "hash": first_file_hash}
            else:
                print("HATA")
                tray_url = stickers[0]["url"]
                tray_name = stickers[0]["image_file"]

    # Firestore'a kaydet - premium ve normal için ayrı koleksiyonlar
    # Mevcut created_at'ı koru (varsa)
    existing_doc = db.collection("premium_stickers" if is_premium else "stickers").document(pack_id).get()
    existing_created_at = ""
    existing_download_count = 0
    existing_category = ""
    if existing_doc.exists:
        data = existing_doc.to_dict()
        existing_created_at = data.get("created_at", "")
        existing_download_count = data.get("download_count", 0)
        existing_category = data.get("category", "")

    pack_data = {
        "name": pack_name.replace("_", " ").replace("-", " ").title(),
        "publisher": PUBLISHER,
        "publisher_email": PUBLISHER_EMAIL,
        "privacy_policy_website": PRIVACY_POLICY,
        "license_agreement_website": LICENSE,
        "image_data_version": "1",
        "avoid_cache": False,
        "tray_image_file": tray_name,
        "tray_url": tray_url,
        "stickers": stickers,
        "isPremium": is_premium,
        "storagePath": storage_folder,
        "animated_sticker_pack": has_animated,
        "created_at": existing_created_at if existing_created_at else datetime.now().strftime("%Y-%m-%d"),
        "sticker_count": len(stickers),
        "category": existing_category,  # Kategori manuel ayarlanmalı (Firebase Console'dan)
        "download_count": existing_download_count  # İndirme sayısı korunur
    }

    # Koleksiyon adını belirle - premium için ayrı koleksiyon
    collection_name = "premium_stickers" if is_premium else "stickers"

    print(f"   Firebase'e kaydediliyor ({collection_name})...", end=" ")
    db.collection(collection_name).document(pack_id).set(pack_data)
    print("OK")

    save_cache(cache)
    return pack_data


def main():
    print("=" * 60)
    print("   STICKER YÜKLEYICI - Otomatik Dönüştürme & Firebase")
    print("=" * 60)

    if not check_ffmpeg():
        print("\n FFmpeg yüklü değil!")
        print("   Çalıştır: sudo apt install ffmpeg")
        sys.exit(1)

    cache = load_cache()
    print(f"\n Cache: {len(cache.get('converted', {}))} paket kayıtlı")

    print(f" Firebase'e bağlanılıyor...")
    bucket, db = init_firebase()
    print(f" Bağlantı başarılı")

    OUTPUT_DIR.mkdir(exist_ok=True)
    STICKERS_DIR.mkdir(exist_ok=True)
    PREMIUM_STICKERS_DIR.mkdir(exist_ok=True)

    # Lokaldeki paketleri bul
    local_pack_ids = get_local_pack_ids()

    # Silinen paketleri temizle
    cleanup_deleted_packs(bucket, db, cache, local_pack_ids)

    # Normal ve premium paket klasörlerini bul
    normal_pack_dirs = [d for d in STICKERS_DIR.iterdir() if d.is_dir()]
    premium_pack_dirs = [d for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()]

    total_packs = len(normal_pack_dirs) + len(premium_pack_dirs)

    if total_packs == 0:
        print(f"\n ❌ Paket klasörü bulunamadı!")
        print(f"\n   Klasör yapısı:")
        print(f"   stickers/              ← Normal paketler")
        print(f"   ├── kategori-adi/")
        print(f"   │   ├── tray.png   (opsiyonel - kapak resmi)")
        print(f"   │   ├── 01.mp4")
        print(f"   │   ├── 02.gif")
        print(f"   │   └── 03.png")
        print(f"   └── diger-kategori/")
        print(f"\n   premium_stickers/      ← Premium paketler")
        print(f"   ├── premium-kategori/")
        print(f"   │   ├── tray.png")
        print(f"   │   └── stickers...")
        sys.exit(1)

    print(f"\n 📦 {len(normal_pack_dirs)} normal paket bulundu")
    print(f" 🌟 {len(premium_pack_dirs)} premium paket bulundu")
    print(f" 📊 Toplam: {total_packs} paket")

    successful = 0

    # Normal paketleri işle
    if normal_pack_dirs:
        print(f"\n{'='*60}")
        print(f" NORMAL PAKETLER İŞLENİYOR")
        print(f"{'='*60}")
        for pack_dir in sorted(normal_pack_dirs):
            result = process_pack(pack_dir, bucket, db, cache, is_premium=False)
            if result:
                successful += 1

    # Premium paketleri işle
    if premium_pack_dirs:
        print(f"\n{'='*60}")
        print(f" 🌟 PREMIUM PAKETLER İŞLENİYOR 🌟")
        print(f"{'='*60}")
        for pack_dir in sorted(premium_pack_dirs):
            result = process_pack(pack_dir, bucket, db, cache, is_premium=True)
            if result:
                successful += 1

    save_cache(cache)

    print("\n" + "=" * 60)
    print(f" ✅ TAMAMLANDI: {successful}/{total_packs} paket başarılı")
    print(f" 📦 Normal: {len(normal_pack_dirs)} paket")
    print(f" 🌟 Premium: {len(premium_pack_dirs)} paket")
    print(f" 📁 Çıktı: {OUTPUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    main()
