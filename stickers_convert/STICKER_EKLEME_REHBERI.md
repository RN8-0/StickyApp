# Sticker Ekleme Rehberi (Yeni Baslayanlar Icin)

Bu rehber, hic programlama bilmeyen birinin bile kolayca sticker ekleyebilmesi icin hazirlanmistir.

---

## ONEMLI: v2.0 Degisiklikleri

Artik sticker sistemi **menu tabanli** ve **Google Drive destekli**:

- Stickerlar GitHub'da degil, **Google Drive'da** yedekleniyor
- Her islem ayri ayri secilebiliyor (hizli calisma)
- **Guvenlik kontrolleri** eklendi (yanlislikla silme engellendi)

---

## ADIM 1: Projeyi Bilgisayarina Indir

### Secenek A: Git ile klonla (Onerilen)
```bash
git clone https://github.com/KULLANICI_ADI/StickyApp.git
cd StickyApp/stickers_convert
```

### Secenek B: GitHub'dan ZIP olarak indir
1. GitHub'a git
2. Yesil **"Code"** butonuna tikla
3. **"Download ZIP"** tikla
4. Indirilen ZIP dosyasini cikar

---

## ADIM 2: Gerekli Programlari Kur (Tek Seferlik)

### Linux (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install python3 python3-pip ffmpeg git
pip install firebase-admin rembg pillow
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

### Windows:
1. **Python:** https://python.org (PATH'e ekle!)
2. **FFmpeg:** https://ffmpeg.org (PATH'e ekle!)
3. **Git:** https://git-scm.com
4. Komut satirinda:
```bash
pip install firebase-admin rembg pillow
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

### macOS:
```bash
brew install python ffmpeg git
pip install firebase-admin rembg pillow
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

---

## ADIM 3: Firebase Ayari (Tek Seferlik)

1. https://console.firebase.google.com adresine git
2. Projeyi sec
3. Project Settings > Service Accounts
4. "Generate New Private Key" tikla
5. Indirilen JSON'u `stickers_convert/` klasorune koy

---

## ADIM 4: Google Drive Ayari (Tek Seferlik)

Stickerlar artik Drive'da yedekleniyor. Bir kez ayarla:

1. https://console.cloud.google.com adresine git
2. Yeni proje olustur veya mevcut projeyi sec
3. Sol menuden **APIs & Services > Enable APIs**
4. **Google Drive API** ara ve etkinlestir
5. Sol menuden **APIs & Services > Credentials**
6. **Create Credentials > OAuth client ID**
7. Application type: **Desktop app**
8. JSON indir
9. Dosyayi `stickers_convert/credentials.json` olarak kaydet

**Ilk calistirmada:**
- Tarayici acilir
- Google hesabinla giris yap
- Izin ver
- Artik otomatik calisir

---

## ADIM 5: Scripti Calistir

```bash
cd stickers_convert
python upload_stickers.py
```

Karsina su menu gelecek:

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

## YENI STICKER EKLEME (Adim Adim)

### 1. Menu 8 sec: Klasor Olustur

```
 STICKER EKLEME REHBERI
 ----------------------

 Ne yapmak istiyorsunuz?
   [1] Normal paket klasoru olustur
   [2] Premium paket klasoru olustur
   [3] Mevcut paketleri listele
   [0] Geri don

 Seciminiz: 1
 Paket adi (klasor adi): komik-kediler
 [OK] Klasor olusturuldu: stickers/komik-kediler
```

### 2. Stickerlari Klasore Koy

Olusturulan klasore git ve sticker dosyalarini ekle:

```
stickers_convert/stickers/komik-kediler/
├── tray.png          <- Kapak resmi (opsiyonel)
├── kedi1.gif
├── kedi2.mp4
├── kedi3.png
└── ...
```

**Kurallar:**
- En az 3, en fazla 30 sticker
- Desteklenen formatlar: MP4, GIF, PNG, JPG, WEBP
- Kapak icin `tray.png` ekle (yoksa ilk sticker kullanilir)

### 3. Menu 2 sec: Firebase'e Yukle

```
============================================================
  STICKERLARI GUNCELLE
============================================================
 [*] 1 normal, 0 premium paket bulundu

 [NORMAL] komik-kediler
   5 yeni, 0 atlandi
 [OK] 1/1 paket basariyla islendi
```

### 4. Menu 7 sec: Drive'a Yedekle

```
============================================================
  YEREL'DEN DRIVE'A YUKLE
============================================================
 [OK] Ana klasor: SticlyStickers

 [NORMAL] komik-kediler
   kedi1.gif OK
   kedi2.mp4 OK
   kedi3.png OK
   tray.png OK

 [OK] 4 dosya yuklendi
```

### 5. Menu 4 sec: GitHub'a Push Et (Opsiyonel)

```
============================================================
  GITHUB SENKRONIZASYONU
============================================================
 Degisiklikler:
 M upload_stickers.py

 Commit mesaji (bos birak = otomatik): Yeni paket eklendi
 [*] Push yapiliyor...
 [OK] GitHub'a basariyla yuklendi!
```

---

## STICKER SILME

### 1. Menu 9 sec

```
============================================================
  STICKER PAKETI SIL
============================================================
[!] DIKKAT: Bu islem geri alinamaz!

 Silme islemleri:
   - Yerel klasorden siler
   - Firebase'den siler (Storage + Firestore)
   - Drive'dan siler (opsiyonel)
   - Cache'den siler

 MEVCUT PAKETLER:
--------------------------------------------------
   1. [N] Komik Kediler (yerel)
   2. [P] Premium Paket (yerel)
   3. [N] Eski Paket (firebase)

 Silmek istediginiz paketin numarasini girin (0=iptal): 1

 'Komik Kediler' paketini silmek istediginizden emin misiniz?
 Bu islem GERI ALINAMAZ!

 Onaylamak icin 'SIL' yazin: SIL

 Drive'dan da silmek istiyor musunuz? (e/h): e

 [OK] 'Komik Kediler' paketi silindi!
   Silinen yerler: Yerel, Output, Firestore, Storage, Cache, Drive
```

---

## YENI CIHAZDA KURULUM

Farkli bir bilgisayarda calismak istersen:

```bash
# 1. Repo'yu klonla
git clone https://github.com/KULLANICI/StickyApp.git
cd StickyApp/stickers_convert

# 2. Paketleri kur
pip install firebase-admin rembg pillow
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib

# 3. Gizli dosyalari koy
#    - Firebase JSON key
#    - credentials.json (Google Drive)

# 4. Scripti calistir
python upload_stickers.py

# 5. Menu 6 sec -> Tum stickerlar Drive'dan indirilir
```

**Neden Drive'dan indirmek gerekiyor?**
- Stickerlar artik GitHub'da yok (cok buyuk)
- Drive ana yedek deposu
- Her cihazda ayni stickerlara erisebilirsin

---

## GUVENLIK KONTROLLERI

### Yerel Klasor Bosken

Eger `stickers/` klasoru bossa ve Menu 2'yi secersen:

```
[!] Yerel klasorde paket bulunamadi!

   GUVENLIK: Firebase'deki veriler korunuyor.
   Stickerlar silinmedi, sadece yerel klasor bos.

   Yapmaniz gerekenler:
   1. Once Drive'dan stickerlari indirin (Menu 6)
   2. Veya yeni paket ekleyin (Menu 8)
```

**Firebase'deki veriler SILINMEZ!**

### Paket Sayisi Uyumsuzlugu

```
[!] DIKKAT: Yerelde 5 paket, Firebase'de 20 paket var!

   Bu islem sadece yereldeki paketleri gunceller.
   Firebase'deki fazla paketler SILINMEYECEK.

   Devam etmek istiyor musunuz? (e/h):
```

---

## KLASOR YAPISI

```
stickers_convert/
├── stickers/                    <- NORMAL PAKETLER
│   ├── komik-kediler/
│   │   ├── tray.png
│   │   ├── kedi1.gif
│   │   └── kedi2.mp4
│   └── emoji-paketi/
│
├── premium_stickers/            <- PREMIUM PAKETLER
│   └── ozel-paket/
│
├── output/                      <- Donusturulen dosyalar (otomatik)
├── cache.json                   <- Islem takibi (otomatik)
├── credentials.json             <- Google Drive OAuth (GIZLI!)
├── token.pickle                 <- Drive token (GIZLI!)
├── *firebase-adminsdk*.json     <- Firebase key (GIZLI!)
└── upload_stickers.py           <- Ana script
```

---

## DESTEKLENEN FORMATLAR

| Format | Tur | Aciklama |
|--------|-----|----------|
| .gif | Animasyonlu | Hareketli sticker |
| .mp4, .mov, .webm | Video | Hareketli sticker (max 3 saniye) |
| .png, .jpg, .jpeg | Resim | Sabit sticker |

---

## SIK SORULAN SORULAR

### S: Sticker ekledim ama uygulamada gorunmuyor?
C: Uygulamayi tamamen kapatip tekrar ac. Veya Firebase Console'dan kontrol et.

### S: "credentials.json bulunamadi" hatasi aliorum?
C: Google Cloud Console'dan OAuth client ID olustur ve JSON'u indir.

### S: Drive'a baglanamiyorum?
C: Google Drive API etkin mi? credentials.json dogru yerde mi?

### S: Yanlislikla bir paketi sildim, geri getirebilir miyim?
C: Eger Drive'dan silmeyi secmediysen, Menu 6 ile geri yukleyebilirsin.

### S: Turkce karakter kullanabilir miyim?
C: Klasor adlarinda HAYIR. Dosya adlarinda kullanabilirsin.

### S: Maksimum kac sticker ekleyebilirim?
C: Bir pakette 3-30 arasi (WhatsApp siniri).

---

## VERI AKISI

```
┌─────────────┐
│   GOOGLE    │
│   DRIVE     │  <- Ana yedek (stickerlar burada)
└──────┬──────┘
       │
  Menu 6 (indir) / Menu 7 (yukle)
       │
┌──────▼──────┐
│   YEREL     │
│   KLASOR    │  <- Calisma alani
└──────┬──────┘
       │
  Menu 2 (guncelle)
       │
┌──────▼──────┐
│  FIREBASE   │  <- Uygulama buradan okur
└─────────────┘
```

---

## HIZLI KOMUTLAR

| Islem | Menu |
|-------|------|
| Yeni paket ekle | 8 |
| Firebase'e yukle | 2 |
| Drive'a yedekle | 7 |
| Drive'dan indir | 6 |
| Paket sil | 9 |
| Istatistikler | 5 |
| GitHub push | 4 |
| Tam sync | F |

---

*Son guncelleme: Ocak 2026*
