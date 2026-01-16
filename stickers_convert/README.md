# Sticly - Sticker Yonetim Sistemi

Bu arac, stickerleri otomatik olarak WhatsApp formatina donusturur ve Firebase'e yukler.

---

## Hizli Baslangic (Tek Tikla Calistir)

Hic komut yazmadan sticker yuklemek icin:

| Isletim Sistemi | Dosya | Nasil Calistirilir |
|-----------------|-------|-------------------|
| **Windows** | `upload_stickers.bat` | Cift tikla |
| **Linux/macOS** | `upload_stickers.run` | Cift tikla veya `./upload_stickers.run` |

Bu dosyalar:
- Gerekli bagimliliklari otomatik kurar (ilk seferde)
- Tray (kapak) resimlerinin arka planini siler
- Stickerleri Firebase'e yukler
- Degisiklikleri GitHub'a push eder

> **Ilk Kullanim Icin:** [STICKER_EKLEME_REHBERI.md](STICKER_EKLEME_REHBERI.md) dosyasini oku!

---

## Gereksinimler

### Otomatik Kurulanlar (Script halleder):
- Python paketleri (firebase-admin, rembg)

### Manuel Kurulmasi Gerekenler:

| Program | Windows | Linux | macOS |
|---------|---------|-------|-------|
| Python 3 | [python.org](https://python.org) | `sudo apt install python3` | `brew install python` |
| FFmpeg | [ffmpeg.org](https://ffmpeg.org) | `sudo apt install ffmpeg` | `brew install ffmpeg` |
| Git | [git-scm.com](https://git-scm.com) | `sudo apt install git` | `brew install git` |

### Tek Seferlik Ayarlar:
- **Firebase Admin SDK Key** - Firebase Console'dan indir ve bu klasore koy
- **GitHub Authentication** - SSH key veya token ayarla (opsiyonel)

---

## Desteklenen Dosya Formatlari

| Tur | Formatlar | Sonuc |
|-----|-----------|-------|
| Video | MP4, MOV, AVI, MKV, WEBM, MPEG | Hareketli sticker |
| Animasyonlu GIF | GIF | Hareketli sticker |
| Resim | PNG, JPG, JPEG, WEBP, BMP | Sabit sticker |
| Statik GIF | GIF (tek kare) | Sabit sticker |

---

## Klasor Yapisi

```
stickers_convert/
├── stickers/                          <- NORMAL (UCRETSIZ) PAKETLER
│   ├── komik-videolar/                <- Kategori 1
│   │   ├── tray.png                   <- Kapak resmi (opsiyonel)
│   │   ├── video1.mp4
│   │   ├── video2.gif
│   │   └── resim3.png
│   │
│   └── sevimli-hayvanlar/             <- Kategori 2
│       └── ...
│
├── premium_stickers/                  <- PREMIUM (UCRETLI) PAKETLER
│   └── ozel-paket/
│       └── ...
│
├── output/                            <- Donusturulen dosyalar (otomatik)
├── venv/                              <- Python ortami (otomatik)
├── cache.json                         <- Islem takibi (otomatik)
├── upload_stickers.py                 <- Ana script
├── upload_stickers.run                <- Linux/macOS calistirici
├── upload_stickers.bat                <- Windows calistirici
├── *firebase-adminsdk*.json           <- Firebase anahtari (SEN EKLE!)
├── STICKER_EKLEME_REHBERI.md          <- Detayli rehber
└── README.md                          <- Bu dosya
```

---

## Sticker Ekleme (3 Adim)

### 1. Klasor Olustur
```
stickers/yeni-paket-adi/
```

### 2. Dosyalari Ekle
- Minimum **3 dosya** gerekli
- Maksimum **30 dosya**
- Kapak icin `tray.png` ekle (opsiyonel)

### 3. Calistir
- **Windows:** `upload_stickers.bat` cift tikla
- **Linux/macOS:** `./upload_stickers.run` calistir

---

## Yeni Ozellikler

### Otomatik Tray Arka Plan Silme
- Tray (kapak) resimlerinin arka plani otomatik silinir
- Her calistirmada kontrol edilir
- Sadece islenmemis dosyalar islenir

### GitHub Otomatik Sync
- Her calistirmada degisiklikler GitHub'a push edilir
- Commit mesaji otomatik olusturulur
- SSH veya token ile calisir

### Tek Tikla Kurulum
- `.run` ve `.bat` dosyalari tum bagimliliklari otomatik kurar
- Ilk calistirmada biraz bekleyin (paketler indiriliyor)

---

## Sik Kullanilan Komutlar

```bash
# Tek tikla calistir (onerilen)
./upload_stickers.run          # Linux/macOS
upload_stickers.bat            # Windows

# Manuel calistir
source venv/bin/activate
python3 upload_stickers.py

# Cache'i sifirla (tum dosyalari yeniden isle)
rm cache.json
./upload_stickers.run

# Yeni kategori ekle
mkdir stickers/yeni-kategori
# Dosyalari kopyala, sonra calistir
```

---

## WhatsApp Gereksinimleri

| Ozellik | Deger |
|---------|-------|
| Sticker boyutu | 512x512 piksel |
| Tray boyutu | 96x96 piksel |
| Maksimum dosya boyutu | 500 KB |
| Maksimum video suresi | 3 saniye |
| Minimum sticker sayisi | 3 |
| Maksimum sticker sayisi | 30 |

> Script bu gereksinimleri otomatik olarak karsilar.

---

## Sorun Giderme

### "Firebase Admin SDK bulunamadi" hatasi
1. Firebase Console > Project Settings > Service Accounts
2. "Generate New Private Key" tikla
3. Indirilen JSON dosyasini `stickers_convert/` klasorune kopyala

### "FFmpeg bulunamadi" hatasi
```bash
# Linux
sudo apt install ffmpeg

# macOS
brew install ffmpeg

# Windows: ffmpeg.org'dan indir ve PATH'e ekle
```

### GitHub push yapmiyor
- SSH key veya token ayarla
- Detaylar icin: [STICKER_EKLEME_REHBERI.md](STICKER_EKLEME_REHBERI.md)

### Cache sorunlari
```bash
rm cache.json
./upload_stickers.run
```

---

## Guvenlik Notlari

- `*firebase-adminsdk*.json` dosyasini **asla paylasma**
- Bu dosya `.gitignore`'da, GitHub'a yuklenmez
- Her kullanici kendi Firebase anahtarini kullanmali

---

## Dokumanlar

| Dosya | Aciklama |
|-------|----------|
| [STICKER_EKLEME_REHBERI.md](STICKER_EKLEME_REHBERI.md) | Yeni baslayanlar icin detayli rehber |
| [PREMIUM_README.md](PREMIUM_README.md) | Premium sticker sistemi |

---

*Son guncelleme: Ocak 2025*
