# Sticly - WhatsApp Sticker Uygulamasi

WhatsApp icin ozel sticker paketleri olusturan ve yoneten Android uygulamasi.

---

## Ozellikler

- WhatsApp'a sticker paketi ekleme
- Firebase ile bulut senkronizasyonu
- Premium sticker sistemi (ucretli paketler)
- Hareketli (animasyonlu) ve sabit sticker destegi
- Otomatik format donusturme (MP4, GIF, PNG -> WebP)

---

## Gereksinimler

Projeyi calistirmak icin asagidakilerin kurulu olmasi gerekir:

| Gereksinim | Aciklama | Indirme |
|------------|----------|---------|
| Android Studio | Uygulama gelistirme ortami | https://developer.android.com/studio |
| Python 3 | Sticker donusturme scripti icin | https://www.python.org/downloads/ |
| FFmpeg | Video/GIF donusturme icin | Asagida kurulum var |
| Firebase Hesabi | Stickerleri depolamak icin | https://firebase.google.com |

### FFmpeg Kurulumu

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install ffmpeg
```

**macOS:**
```bash
brew install ffmpeg
```

**Windows:**
1. https://ffmpeg.org/download.html adresinden indir
2. ZIP'i cikar
3. `bin` klasorunu sistem PATH'ine ekle

---

## Ilk Kurulum (Adim Adim)

### Adim 1: Projeyi Indir

**GitHub'dan:**
```bash
git clone https://github.com/KULLANICI_ADIN/StickyApp.git
cd StickyApp
```

**Veya ZIP olarak:**
1. GitHub'dan "Download ZIP" tikla
2. Masaustune cikar

---

### Adim 2: Firebase Kurulumu

Bu adim ZORUNLUDUR. Firebase olmadan sticker paketleri yuklenmez.

#### 2.1 Firebase Projesi Olustur
1. https://console.firebase.google.com adresine git
2. "Proje Ekle" (Add Project) tikla
3. Proje adi gir: `sticly-app` (veya istedigin isim)
4. Google Analytics'i kapat (opsiyonel)
5. "Proje Olustur" tikla

#### 2.2 Android Uygulamasini Ekle
1. Firebase Console'da proje acilinca "Android" ikonuna tikla
2. Paket adi: `com.sticly`
3. Uygulama adi: `Sticly`
4. "Uygulamayi Kaydet" tikla

#### 2.3 google-services.json Indir
1. "google-services.json indir" butonuna tikla
2. Indirilen dosyayi `StickyApp/app/` klasorune kopyala

```
StickyApp/
├── app/
│   ├── google-services.json   <-- BURAYA KOPYALA
│   ├── build.gradle
│   └── src/
└── ...
```

#### 2.4 Firebase Admin SDK Anahtari Indir
Bu anahtar sticker yukleme scripti icin gerekli.

1. Firebase Console > Proje Ayarlari (dis ikon) > Hizmet Hesaplari
2. "Yeni Ozel Anahtar Olustur" tikla
3. Indirilen JSON dosyasini `StickyApp/stickers_convert/` klasorune kopyala

```
StickyApp/
├── stickers_convert/
│   ├── sticly-app-firebase-adminsdk-xxxxx.json   <-- BURAYA KOPYALA
│   ├── upload_stickers.py
│   └── stickers/
└── ...
```

#### 2.5 Firestore ve Storage Aktifle
1. Firebase Console > Firestore Database > "Veritabani Olustur"
   - "Uretim modunda basla" sec
   - Konum: `eur3` veya size yakin bir yer

2. Firebase Console > Storage > "Baslat"
   - Kurallari varsayilan birak

#### 2.6 Firestore Kurallari Guncelle
Firebase Console > Firestore Database > Kurallar sekmesi:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /sticker_packs/{document=**} {
      allow read: if true;
      allow write: if false;
    }
  }
}
```

"Yayinla" tikla.

#### 2.7 Storage Kurallari Guncelle
Firebase Console > Storage > Kurallar sekmesi:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /stickers/{allPaths=**} {
      allow read: if true;
      allow write: if false;
    }
  }
}
```

"Yayinla" tikla.

---

### Adim 3: Python Ortamini Kur

```bash
cd StickyApp/stickers_convert

# Virtual environment olustur
python3 -m venv venv

# Aktifle
source venv/bin/activate   # Linux/macOS
# veya
venv\Scripts\activate      # Windows

# Gereksinimleri kur
pip install firebase-admin
```

---

### Adim 4: Uygulamayi Derle

1. Android Studio'yu ac
2. File > Open > StickyApp klasorunu sec
3. Gradle sync bitmesini bekle (2-5 dakika)
4. Telefonu USB ile bagla (USB Debugging acik olmali)
5. Yesil "Run" butonuna tikla

---

## Sticker Ekleme

Sticker eklemek icin detayli rehber:

**Normal (ucretsiz) paketler icin:**
```
stickers_convert/README.md
```

**Premium (ucretli) paketler icin:**
```
stickers_convert/PREMIUM_README.md
```

### Hizli Ozet

1. Sticker dosyalarini hazirla (MP4, GIF, PNG, JPG)
2. `stickers_convert/stickers/paket-adi/` klasorune koy
3. Script'i calistir:
   ```bash
   cd stickers_convert
   source venv/bin/activate
   python3 upload_stickers.py
   ```
4. Uygulama otomatik guncellenir!

---

## Klasor Yapisi

```
StickyApp/
├── app/                              # Android uygulama kodu
│   ├── google-services.json          # Firebase config (sen ekleyeceksin)
│   ├── build.gradle                  # Uygulama ayarlari
│   └── src/main/
│       ├── java/com/sticly/          # Kotlin kodlari
│       ├── res/                      # Goruntuler, renkler, stringler
│       └── AndroidManifest.xml
│
├── stickers_convert/                 # Sticker yonetim araci
│   ├── stickers/                     # Normal sticker paketleri
│   ├── premium_stickers/             # Premium sticker paketleri
│   ├── upload_stickers.py            # Yukleme scripti
│   ├── *firebase-adminsdk*.json      # Firebase anahtari (sen ekleyeceksin)
│   ├── README.md                     # Detayli kullanim rehberi
│   └── PREMIUM_README.md             # Premium sistem rehberi
│
├── build.gradle.kts                  # Proje ayarlari
├── settings.gradle.kts
└── README.md                         # Bu dosya
```

---

## Baska Bilgisayara Tasima

### Gereken Dosyalar
Projeyi baska bilgisayara tasirken su dosyalari UNUTMA:

1. `app/google-services.json` - Firebase config
2. `stickers_convert/*firebase-adminsdk*.json` - Admin anahtari

Bu dosyalar GitHub'a YUKLENMEZ (guvenlik icin). Manuel kopyalaman gerekir.

### Bu Dosyalar Nereden Alinir?

#### google-services.json
1. https://console.firebase.google.com adresine git
2. Projenizi secin (ornek: sticly-app)
3. Sol ustte dis simgesine (Proje Ayarlari) tikla
4. Asagi kaydir, "Uygulamalariniz" bolumunde Android uygulamasini bul
5. "google-services.json" butonuna tikla ve indir
6. Indirilen dosyayi `StickyApp/app/` klasorune kopyala

#### firebase-adminsdk-xxx.json
1. https://console.firebase.google.com adresine git
2. Projenizi secin
3. Sol ustte dis simgesine (Proje Ayarlari) tikla
4. "Hizmet hesaplari" (Service accounts) sekmesine tikla
5. "Yeni ozel anahtar olustur" (Generate new private key) tikla
6. Indirilen JSON dosyasini `StickyApp/stickers_convert/` klasorune kopyala

> NOT: Bu dosyalar Firebase projenize ozel gizli anahtarlardir.
> Baskasıyla ASLA paylasmayın. Her Firebase projesi icin farklidir.

### Yeni Bilgisayarda Kurulum
1. Projeyi clone et veya kopyala
2. Yukaridaki 2 dosyayi Firebase Console'dan indirip yerine koy
3. Android Studio ve gereksinimlerini kur
4. Python ortamini yeniden olustur:
   ```bash
   cd stickers_convert
   python3 -m venv venv
   source venv/bin/activate
   pip install firebase-admin
   ```

---

## APK Olusturma

### Debug APK (Test icin)
```bash
cd StickyApp
./gradlew assembleDebug
```
APK konumu: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK (Play Store icin)
```bash
./gradlew assembleRelease
```
Not: Release APK icin imzalama anahtari gerekir. Play Store yuklemeden once ayarlanacak.

---

## Sorun Giderme

### "google-services.json bulunamadi" hatasi
- `app/google-services.json` dosyasini kontrol et
- Firebase Console'dan tekrar indir

### "Firebase Admin SDK bulunamadi" hatasi
- `stickers_convert/` klasorunde `*firebase-adminsdk*.json` dosyasi var mi kontrol et
- Firebase Console > Proje Ayarlari > Hizmet Hesaplari > Yeni anahtar olustur

### "FFmpeg bulunamadi" hatasi
```bash
# Ubuntu/Debian
sudo apt install ffmpeg

# macOS
brew install ffmpeg
```

### Gradle sync basarisiz
1. File > Invalidate Caches > Invalidate and Restart
2. Tekrar sync et

### Sticker'lar uygulamada gorunmuyor
1. Firebase Console'da Firestore'u kontrol et
2. `sticker_packs` koleksiyonu var mi?
3. Script'i tekrar calistir: `python3 upload_stickers.py`

---

## Guvenlik Notlari

**ASLA PAYLASMA:**
- `google-services.json` - Firebase API anahtari icerir
- `*firebase-adminsdk*.json` - Tam erisim anahtari
- `.env` dosyalari - Sifre/anahtar iceren dosyalar

Bu dosyalar `.gitignore`'da tanimli, GitHub'a yuklenmez.

---

## Faydali Komutlar

```bash
# Projeyi temizle
./gradlew clean

# Debug APK olustur
./gradlew assembleDebug

# Release APK olustur
./gradlew assembleRelease

# Sticker yukle
cd stickers_convert && source venv/bin/activate && python3 upload_stickers.py

# Cache temizle (tum stickerler yeniden islenir)
rm stickers_convert/cache.json
```

---

## Teknik Bilgiler

| Ozellik | Deger |
|---------|-------|
| Paket Adi | com.sticly |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Dil | Kotlin |
| Veritabani | Firebase Firestore |
| Depolama | Firebase Storage |

---

## Destek

Sorun yasarsan:
1. Bu README'yi tekrar oku
2. `stickers_convert/README.md` dosyasini oku
3. Firebase Console'da loglari kontrol et

---

*Son guncelleme: Ocak 2025*
