# Sticker Ekleme Rehberi (Yeni Baslayanlar Icin)

Bu rehber, hic programlama bilmeyen birinin bile kolayca sticker ekleyebilmesi icin hazirlanmistir.

---

## ADIM 1: Projeyi Bilgisayarina Indir

### Secnek A: GitHub'dan ZIP olarak indir (En kolay)
1. https://github.com/KULLANICI_ADI/StickyApp adresine git
2. Yesil **"Code"** butonuna tikla
3. **"Download ZIP"** tikla
4. Indirilen ZIP dosyasini masaustune cikar

### Secenek B: Git ile klonla
```bash
git clone https://github.com/KULLANICI_ADI/StickyApp.git
```

---

## ADIM 2: Gerekli Programlari Kur (Tek Seferlik)

### Windows Kullanicilar:

#### Python Kurulumu:
1. https://www.python.org/downloads/ adresine git
2. "Download Python" butonuna tikla
3. Indirilen dosyayi calistir
4. **ONEMLI:** "Add Python to PATH" kutucugunu MUTLAKA isaretle!
5. "Install Now" tikla

#### FFmpeg Kurulumu:
1. https://www.gyan.dev/ffmpeg/builds/ adresine git
2. "ffmpeg-release-essentials.zip" indir
3. ZIP'i cikar (ornek: C:\ffmpeg)
4. Sistem ortam degiskenlerine ekle:
   - Baslat > "ortam degiskenleri" ara > "Sistem ortam degiskenlerini duzenle"
   - "Ortam Degiskenleri" tikla
   - "Path" sec > "Duzenle" > "Yeni"
   - FFmpeg'in bin klasorunu ekle: `C:\ffmpeg\bin`
   - Tamam'a tikla

#### Git Kurulumu (GitHub sync icin):
1. https://git-scm.com/download/win adresine git
2. Indir ve kur (varsayilan ayarlarla)

---

### Linux Kullanicilar (Ubuntu/Debian):

Terminali ac ve su komutlari calistir:
```bash
sudo apt update
sudo apt install python3 python3-venv python3-pip ffmpeg git
```

---

### macOS Kullanicilar:

Terminali ac ve su komutlari calistir:
```bash
# Homebrew yoksa kur
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Gerekli programlari kur
brew install python ffmpeg git
```

---

## ADIM 3: Firebase Ayarlarini Yap (Tek Seferlik)

Firebase, stickerlerin depolandigi bulut servisidir. Bir kez ayarlamaniz gerekir.

### 3.1 Firebase Admin SDK Anahtarini Al

1. https://console.firebase.google.com adresine git
2. Google hesabinla giris yap
3. Projeyi sec (ornek: sticly-app)
4. Sol ustteki dis simgesine tikla (Proje Ayarlari)
5. "Hizmet hesaplari" (Service accounts) sekmesine tikla
6. "Yeni ozel anahtar olustur" (Generate new private key) tikla
7. JSON dosyasi indirilecek

### 3.2 Anahtar Dosyasini Yerlestir

Indirdigin JSON dosyasini su klasore kopyala:
```
StickyApp/stickers_convert/
```

Dosya adi soyle gorunmeli: `sticly-app-firebase-adminsdk-xxxxx.json`

---

## ADIM 4: GitHub Ayarlarini Yap (Tek Seferlik - Opsiyonel)

GitHub sync istiyorsan (kodlarin otomatik yedeklenmesi), bir kez ayarla:

### Secenek A: SSH Anahtari (Onerilen)

**Windows (Git Bash'te):**
```bash
ssh-keygen -t ed25519 -C "email@ornek.com"
cat ~/.ssh/id_ed25519.pub
```

**Linux/macOS:**
```bash
ssh-keygen -t ed25519 -C "email@ornek.com"
cat ~/.ssh/id_ed25519.pub
```

Cikan metni kopyala, sonra:
1. https://github.com/settings/keys adresine git
2. "New SSH Key" tikla
3. Kopyaladigin anahtari yapistir
4. "Add SSH Key" tikla

### Secenek B: HTTPS Token

1. https://github.com/settings/tokens adresine git
2. "Generate new token (classic)" tikla
3. "repo" iznini sec
4. Token olustur ve bir yere kaydet
5. Ilk git push'ta kullanici adi ve token iste

---

## ADIM 5: Sticker Dosyalarini Hazirla

### Desteklenen Formatlar:
| Format | Tur | Aciklama |
|--------|-----|----------|
| .gif | Animasyonlu | Hareketli sticker |
| .mp4, .mov, .webm | Video | Hareketli sticker (max 3 saniye) |
| .png, .jpg, .jpeg | Resim | Sabit sticker |

### Onerilen Boyutlar:
- Sticker: 512x512 piksel (otomatik duzeltilir)
- Dosya boyutu: Max 500KB (otomatik sikistirilir)

### Sticker Bulma Yerleri:
- https://giphy.com (GIF'ler)
- https://tenor.com (GIF'ler)
- Google Gorseller (PNG/JPG)
- Canva.com (Kendin tasarla)

---

## ADIM 6: Stickerleri Klasore Ekle

### Klasor Yapisi:

```
StickyApp/
└── stickers_convert/
    ├── stickers/                    <-- NORMAL (UCRETSIZ) PAKETLER
    │   ├── komik-kediler/           <-- Paket klasoru (istedigin isim)
    │   │   ├── tray.png             <-- Kapak resmi (opsiyonel)
    │   │   ├── kedi1.gif
    │   │   ├── kedi2.png
    │   │   └── kedi3.mp4
    │   │
    │   └── emoji-paketi/            <-- Baska bir paket
    │       ├── tray.jpg
    │       ├── emoji1.gif
    │       └── emoji2.png
    │
    └── premium_stickers/            <-- PREMIUM (UCRETLI) PAKETLER
        └── ozel-paket/
            ├── tray.png
            └── sticker1.gif
```

### Yeni Paket Ekleme:

1. `stickers_convert/stickers/` klasorunu ac
2. Yeni bir klasor olustur (ornek: `komik-memeler`)
   - Klasor adi = Paket adi (Turkce karakter kullanma!)
   - Ornek gecerli isimler: `komik-kediler`, `emoji-pack`, `breaking-bad`
3. Sticker dosyalarini bu klasore at
4. (Opsiyonel) Kapak resmi ekle: `tray.png` veya `tray.jpg`

### Kapak Resmi (Tray) Hakkinda:
- Dosya adi `tray` olmali (tray.png, tray.jpg, tray.gif)
- Yoksa ilk stickerdan otomatik olusturulur
- Arka plan otomatik silinir (seffaf olur)

---

## ADIM 7: Yukleyiciyi Calistir

### Windows:
1. `stickers_convert` klasorune git
2. `upload_stickers.bat` dosyasina cift tikla
3. Terminal penceresi acilacak ve islem baslayacak

### Linux/macOS:
1. Terminal ac
2. Su komutlari calistir:
```bash
cd ~/Desktop/StickyApp/stickers_convert
./upload_stickers.run
```

Veya dosya yoneticisinde `upload_stickers.run` dosyasina cift tikla.

---

## Ne Olacak?

Dosyayi calistirdiginizda:

1. **[Otomatik]** Gerekli Python paketleri kurulur (ilk seferde)
2. **[Otomatik]** Tray (kapak) resimlerinin arka plani silinir
3. **[Otomatik]** Stickerlar WebP formatina donusturulur
4. **[Otomatik]** Firebase'e yuklenir
5. **[Otomatik]** GitHub'a push edilir (ayarlandiysa)
6. **[Otomatik]** Uygulama guncellenir (Firebase'den ceker)

---

## Ornek: Adim Adim Yeni Paket Ekleme

Diyelim ki "Kedi GIF'leri" paketi eklemek istiyorsun:

### 1. Stickerleri indir
- giphy.com'dan 5-10 kedi GIF'i indir
- Masaustune kaydet

### 2. Klasor olustur
```
StickyApp/stickers_convert/stickers/kedi-gifleri/
```

### 3. Dosyalari tasi
Indirdigin GIF'leri `kedi-gifleri` klasorune tasi.

### 4. Kapak resmi ekle (opsiyonel)
En guzel kedi resmini `tray.png` olarak kaydet ve klasore koy.

### 5. Yukleyiciyi calistir
- Windows: `upload_stickers.bat` cift tikla
- Linux/macOS: `./upload_stickers.run` calistir

### 6. Bekle
Islem tamamlaninca uygulamayi ac, yeni paketi gor!

---

## Sik Sorulan Sorular

### S: Sticker ekledim ama uygulamada gorunmuyor?
C: Uygulamayi tamamen kapatip tekrar ac. Veya asagi cekerek yenile.

### S: "Firebase Admin SDK bulunamadi" hatasi aliorum?
C: ADIM 3'u takip et. JSON dosyasini dogru klasore koydugundan emin ol.

### S: "FFmpeg bulunamadi" hatasi aliorum?
C: ADIM 2'deki FFmpeg kurulumunu yap.

### S: GitHub'a push yapmıyor?
C: ADIM 4'u takip et. SSH anahtari veya token ayarla.

### S: Turkce karakter kullanabilir miyim?
C: Klasor adlarinda HAYIR. Dosya adlarinda kullanabilirsin ama onerilmez.

### S: Maksimum kac sticker ekleyebilirim?
C: Bir pakette 3-30 arasi sticker olmali (WhatsApp siniri).

### S: Video/GIF ne kadar uzun olabilir?
C: Maksimum 3 saniye. Daha uzunsa otomatik kesilir.

---

## Hizli Kontrol Listesi

Sticker eklemeden once kontrol et:

- [ ] Python kurulu mu? (Terminalde `python --version` yaz)
- [ ] FFmpeg kurulu mu? (Terminalde `ffmpeg -version` yaz)
- [ ] Firebase JSON dosyasi `stickers_convert` klasorunde mi?
- [ ] Sticker dosyalari dogru klasorde mi?
- [ ] Klasor adi Turkce karakter icermiyor mu?
- [ ] Pakette en az 3 sticker var mi?

---

## Yardim

Sorun yasarsan:
1. Bu rehberi tekrar oku
2. Hata mesajini Google'da ara
3. GitHub Issues'a yaz

---

*Son guncelleme: Ocak 2025*
