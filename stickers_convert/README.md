# Sticly - Sticker Yonetim Paneli v2.0

Stickerleri Google Drive'da yedekleyen, Firebase'e yukleyen ve menuyle yoneten arac.

---

## Yenilikler (v2.0)

- **Google Drive Entegrasyonu** - Stickerlar artık Drive'da yedekleniyor
- **Menu Tabanli Arayuz** - Tek tek islemler secebilirsin
- **Guvenlik Kontrolleri** - Yanlislikla silme engellendi
- **Hizli Calisma** - Sadece secilen islem calisir, tum taramalar yapilmaz

---

## Ana Menu

```
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
║  [F] Tam Senkronizasyon (Tum islemler)                     ║
║  [0] Cikis                                                 ║
╚════════════════════════════════════════════════════════════╝
```

---

## Ilk Kurulum

### 1. Gerekli Programlar

| Program | Linux | Windows | macOS |
|---------|-------|---------|-------|
| Python 3 | `sudo apt install python3` | [python.org](https://python.org) | `brew install python` |
| FFmpeg | `sudo apt install ffmpeg` | [ffmpeg.org](https://ffmpeg.org) | `brew install ffmpeg` |
| Git | `sudo apt install git` | [git-scm.com](https://git-scm.com) | `brew install git` |

### 2. Python Paketleri

```bash
pip install firebase-admin rembg pillow
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

### 3. Firebase Ayari

1. [Firebase Console](https://console.firebase.google.com) > Project Settings > Service Accounts
2. "Generate New Private Key" tikla
3. Indirilen JSON'u `stickers_convert/` klasorune koy

### 4. Google Drive Ayari (OAuth 2.0)

1. [Google Cloud Console](https://console.cloud.google.com) > Yeni proje olustur
2. APIs & Services > Enable APIs > **Google Drive API** etkinlestir
3. APIs & Services > Credentials > Create Credentials > **OAuth client ID**
4. Application type: **Desktop app**
5. JSON indir ve `stickers_convert/credentials.json` olarak kaydet
6. Ilk calistirmada tarayici acilir, Google hesabinla giris yap

---

## Kullanim Senaryolari

### Yeni Cihazda Baslangic

```bash
# 1. Repo'yu klonla
git clone https://github.com/KULLANICI/StickyApp.git
cd StickyApp/stickers_convert

# 2. credentials.json ve firebase key'i koy

# 3. Scripti calistir
python upload_stickers.py

# 4. Menu 6 sec -> Tum stickerlar Drive'dan indirilir
# 5. Menu 2 sec -> Firebase guncellenir
```

### Yeni Sticker Paketi Ekleme

1. **Menu 8** sec -> Klasor olustur
2. Klasore en az 3 sticker dosyasi koy (MP4, GIF, PNG, JPG...)
3. (Opsiyonel) `tray.png` kapak resmi ekle
4. **Menu 2** sec -> Firebase'e yukle
5. **Menu 7** sec -> Drive'a yedekle
6. **Menu 4** sec -> GitHub'a push et

### Sticker Paketi Silme

1. **Menu 9** sec
2. Silinecek paketi sec
3. Onay icin `SIL` yaz
4. Paket su yerlerden silinir:
   - Yerel klasor
   - Firebase Storage + Firestore
   - Cache
   - Drive (opsiyonel - sorar)

### Mevcut Stickerlari Guncelleme

1. Yerel klasordeki dosyalari degistir/ekle/cikar
2. **Menu 2** sec -> Sadece degisen dosyalar islenir
3. **Menu 7** sec -> Drive'a yedekle

---

## Guvenlik Kontrolleri

### Yerel Klasor Bosken

```
[!] Yerel klasorde paket bulunamadi!

   GUVENLIK: Firebase'deki veriler korunuyor.
   Stickerlar silinmedi, sadece yerel klasor bos.

   Yapmaniz gerekenler:
   1. Once Drive'dan stickerlari indirin (Menu 6)
   2. Veya yeni paket ekleyin (Menu 8)
```

**Neden?** Bos klasor Firebase'deki verileri silmez. Once Drive'dan indirmelisin.

### Firebase'de Daha Fazla Paket Varken

```
[!] DIKKAT: Yerelde 5 paket, Firebase'de 20 paket var!

   Bu islem sadece yereldeki paketleri gunceller.
   Firebase'deki fazla paketler SILINMEYECEK.

   Devam etmek istiyor musunuz? (e/h):
```

**Neden?** Yanlislikla tum verilerin silinmesini engeller.

### Paket Silme Onayi

```
'Komik Kediler' paketini silmek istediginizden emin misiniz?
Bu islem GERI ALINAMAZ!

Onaylamak icin 'SIL' yazin:
```

**Neden?** Yanlislikla silmeyi engeller.

---

## Klasor Yapisi

```
stickers_convert/
├── stickers/                    <- NORMAL PAKETLER
│   ├── komik-videolar/
│   │   ├── tray.png            <- Kapak resmi (opsiyonel)
│   │   ├── video1.mp4
│   │   └── video2.gif
│   └── sevimli-hayvanlar/
│
├── premium_stickers/            <- PREMIUM PAKETLER
│   └── ozel-paket/
│
├── output/                      <- Donusturulen dosyalar
├── cache.json                   <- Islem takibi
├── credentials.json             <- Google Drive OAuth (GIZLI)
├── token.pickle                 <- Drive token (GIZLI)
├── *firebase-adminsdk*.json     <- Firebase key (GIZLI)
└── upload_stickers.py           <- Ana script
```

---

## Desteklenen Formatlar

| Tur | Formatlar | Sonuc |
|-----|-----------|-------|
| Video | MP4, MOV, AVI, MKV, WEBM | Hareketli sticker |
| Animasyonlu GIF | GIF (cok kareli) | Hareketli sticker |
| Resim | PNG, JPG, JPEG, WEBP, BMP | Sabit sticker |
| Statik GIF | GIF (tek kare) | Sabit sticker |

---

## WhatsApp Gereksinimleri

| Ozellik | Deger |
|---------|-------|
| Sticker boyutu | 512x512 piksel (otomatik) |
| Tray boyutu | 96x96 piksel (otomatik) |
| Maksimum dosya | 500 KB (otomatik sıkıştırma) |
| Video suresi | Max 3 saniye (otomatik kesme) |
| Sticker sayisi | 3-30 arasi |

---

## Sorun Giderme

### "credentials.json bulunamadi"
Google Cloud Console'dan OAuth client ID olustur ve JSON'u indir.

### "Firebase Admin SDK bulunamadi"
Firebase Console > Service Accounts > Generate New Private Key

### "FFmpeg yuklu degil"
```bash
sudo apt install ffmpeg  # Linux
brew install ffmpeg      # macOS
```

### Drive'a baglanamiyor
- credentials.json dogru konumda mi?
- Google Drive API etkin mi?
- Ilk giriste tarayicida onay verdin mi?

### Cache sorunlari
```bash
rm cache.json
python upload_stickers.py
```

---

## Guvenlik Notlari

Su dosyalar **ASLA PAYLASILMAMALI**:
- `*firebase-adminsdk*.json` - Firebase erisim anahtari
- `credentials.json` - Google OAuth istemci sifresi
- `token.pickle` - Google oturum token'i
- `client_secret*.json` - Google client secret

Bu dosyalar `.gitignore`'da ve GitHub'a yuklenmez.

---

## Veri Akisi

```
                    ┌─────────────┐
                    │   GOOGLE    │
                    │   DRIVE     │  <- Ana yedek
                    └──────┬──────┘
                           │
            Menu 6 ↓       │      ↑ Menu 7
                           │
                    ┌──────▼──────┐
                    │   YEREL     │
                    │   KLASOR    │  <- Calisma alani
                    └──────┬──────┘
                           │
            Menu 2 ↓       │
                           │
              ┌────────────▼────────────┐
              │        FIREBASE         │
              │  Storage + Firestore    │  <- Uygulama verisi
              └─────────────────────────┘
```

---

*Son guncelleme: Ocak 2026*
