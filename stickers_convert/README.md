# Sticly - Sticker Yönetim Sistemi

Bu araç, stickerleri otomatik olarak WhatsApp formatına dönüştürür ve Firebase'e yükler.

---

## Desteklenen Dosya Formatları

| Tür | Formatlar | Sonuç |
|-----|-----------|-------|
| Video | MP4, MOV, AVI, MKV, WEBM, MPEG | Hareketli sticker |
| Animasyonlu GIF | GIF | Hareketli sticker |
| Resim | PNG, JPG, JPEG, WEBP, BMP | Sabit sticker |
| Statik GIF | GIF (tek kare) | Sabit sticker |

---

## Klasör Yapısı

```
stickers_convert/
├── stickers/                          <- TÜM KATEGORİLER BURAYA
│   ├── komik-videolar/                <- Kategori 1
│   │   ├── tray.png                   <- Kapak resmi (opsiyonel)
│   │   ├── video1.mp4
│   │   ├── video2.gif
│   │   └── resim3.png
│   │
│   ├── sevimli-hayvanlar/             <- Kategori 2
│   │   ├── tray.jpg                   <- Kapak resmi (opsiyonel)
│   │   ├── kedi.mp4
│   │   ├── kopek.gif
│   │   └── tavsan.png
│   │
│   └── yeni-kategori/                 <- İstediğin kadar ekle
│       └── ...
│
├── output/                            <- Dönüştürülen dosyalar (otomatik)
├── venv/                              <- Python ortamı
├── cache.json                         <- İşlem takibi (otomatik)
├── upload_stickers.py                 <- Ana script
├── *firebase-adminsdk*.json           <- Firebase anahtarı (GİZLİ!)
└── README.md                          <- Bu dosya
```

---

## Sticker Ekleme (Adım Adım)

### 1. Yeni Kategori Oluştur
```bash
mkdir stickers/kategori-adi
```

### 2. Dosyaları Ekle
- Minimum **3 dosya** gerekli (WhatsApp kuralı)
- Maksimum **30 dosya** (WhatsApp kuralı)
- Dosyaları `stickers/kategori-adi/` klasörüne kopyala

### 3. Script'i Çalıştır
```bash
cd /home/arain/Desktop/StickyApp/stickers_convert
source venv/bin/activate
python3 upload_stickers.py
```

### 4. Bitti!
- Dosyalar otomatik dönüştürülür (512x512 WebP)
- Firebase'e yüklenir
- Uygulama otomatik güncellenir

---

## Kategori Kapak Resmi (Tray) Değiştirme

Kategori klasörüne `tray` adında bir resim ekle:

```
stickers/kategori-adi/
├── tray.png      <- veya tray.jpg, tray.jpeg, tray.webp
├── sticker1.mp4
├── sticker2.gif
└── ...
```

- Dosya adı **tray** olmalı (uzantı farklı olabilir)
- Resim otomatik 96x96 boyutuna dönüştürülür
- Tray dosyası yoksa ilk sticker'dan otomatik oluşturulur

---

## Sticker veya Kategori Silme

### Tek Sticker Silmek:
1. Dosyayı `stickers/kategori-adi/` klasöründen sil
2. Script'i çalıştır: `python3 upload_stickers.py`
3. Firebase'den otomatik silinir

### Tüm Kategoriyi Silmek:
1. Kategori klasörünü sil: `rm -rf stickers/kategori-adi`
2. Script'i çalıştır: `python3 upload_stickers.py`
3. Firebase'den otomatik silinir

---

## Yeni Bilgisayara Kurulum

### 1. Gerekli Dosyaları Kopyala
```
stickers_convert/
├── stickers/                  <- Kategoriler
├── upload_stickers.py         <- Script
├── *firebase-adminsdk*.json   <- Firebase anahtarı
└── README.md
```

### 2. Sistem Gereksinimlerini Kur
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install python3 python3-venv ffmpeg

# macOS
brew install python3 ffmpeg
```

### 3. Python Ortamını Oluştur
```bash
cd stickers_convert
python3 -m venv venv
source venv/bin/activate
pip install firebase-admin
```

### 4. Test Et
```bash
python3 upload_stickers.py
```

---

## Sık Kullanılan Komutlar

```bash
# Ortamı aktifleştir
source venv/bin/activate

# Stickerleri yükle
python3 upload_stickers.py

# Cache'i sıfırla (tüm dosyaları yeniden işle)
rm cache.json
python3 upload_stickers.py

# Yeni kategori ekle
mkdir stickers/yeni-kategori
# Dosyaları kopyala
python3 upload_stickers.py

# Kategori sil
rm -rf stickers/eski-kategori
python3 upload_stickers.py
```

---

## WhatsApp Gereksinimleri

| Özellik | Değer |
|---------|-------|
| Sticker boyutu | 512x512 piksel |
| Tray boyutu | 96x96 piksel |
| Maksimum dosya boyutu | 500 KB |
| Maksimum video süresi | 3 saniye |
| Minimum sticker sayısı | 3 |
| Maksimum sticker sayısı | 30 |

> Script bu gereksinimleri otomatik olarak karşılar. Boyut büyükse kalite düşürür.

---

## Sorun Giderme

### "FFmpeg yüklü değil" hatası
```bash
sudo apt install ffmpeg
```

### "Firebase Admin SDK yüklü değil" hatası
```bash
source venv/bin/activate
pip install firebase-admin
```

### "Service Account Key bulunamadı" hatası
1. Firebase Console > Project Settings > Service Accounts
2. "Generate New Private Key" tıkla
3. İndirilen JSON dosyasını `stickers_convert/` klasörüne kopyala

### Cache sorunları
```bash
rm cache.json
python3 upload_stickers.py
```

---

## Güvenlik Notları

- `*firebase-adminsdk*.json` dosyasını **asla paylaşma**
- Bu dosya sadece senin bilgisayarında olmalı
- APK içinde bu dosya **bulunmaz**
- Kullanıcılar sadece stickerleri görebilir, ekleyemez

---

## Hızlı Başvuru

| İşlem | Komut |
|-------|-------|
| Sticker ekle | Dosyaları `stickers/kategori/` klasörüne koy |
| Kapak değiştir | `tray.png` dosyası ekle |
| Kategori sil | Klasörü sil |
| Yükle | `python3 upload_stickers.py` |

---

*Son güncelleme: Ocak 2025*
