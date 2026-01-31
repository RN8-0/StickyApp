# 🤖 Sticky Automation Bot

Otomatik sticker paketi oluşturmak için Python otomasyon botu.

## 📋 Özellikler

- ✅ Google Trends'den popüler konuları bulma
- ✅ Tenor API ile GIF arama ve indirme
- ✅ GIF → WebP dönüşümü (WhatsApp uyumlu)
- ✅ Firebase'e taslak olarak yükleme
- ✅ Admin panelden onaylama/silme

## 🚀 Kurulum

### 1. Bağımlılıkları Yükle

```bash
cd automation
pip install -r requirements.txt
```

### 2. FFmpeg Kur

```bash
# Ubuntu/Debian
sudo apt install ffmpeg

# MacOS
brew install ffmpeg

# Windows
# https://ffmpeg.org/download.html adresinden indir
```

### 3. Firebase Service Account Key'i İndir

1. [Firebase Console](https://console.firebase.google.com/) → Projen (sticky-dcd20)
2. ⚙️ Proje Ayarları → Hizmet Hesapları
3. "Yeni özel anahtar oluştur" butonuna tıkla
4. İndirilen `.json` dosyasını `automation/` klasörüne `serviceAccountKey.json` adıyla kopyala

```bash
mv ~/Downloads/sticky-dcd20-firebase-adminsdk-*.json automation/serviceAccountKey.json
```

## 📖 Kullanım

### Trend'den Paket Oluştur (Varsayılan)

```bash
python sticker_bot.py
```

### Birden Fazla Trend Paketi

```bash
python sticker_bot.py --trends 3
```

### Manuel Arama ile Paket

```bash
python sticker_bot.py --query "komik kedi"
```

### Taslakları Listele

```bash
python sticker_bot.py --list
```

### Taslağı Onayla (Yayına Al)

```bash
python sticker_bot.py --approve <paket_id>
```

### Taslağı Sil

```bash
python sticker_bot.py --delete <paket_id>
```

## 🎛️ Yapılandırma

`config.json` dosyasından ayarları değiştirebilirsin:

```json
{
    "settings": {
        "max_gifs_per_pack": 15,    // Paket başına maksimum GIF
        "min_gifs_per_pack": 5,     // Minimum GIF (altında paket oluşturulmaz)
        "webp_quality": 80,         // WebP kalitesi (1-100)
        "max_file_size_kb": 500     // Maksimum dosya boyutu (KB)
    }
}
```

## 📁 Dosya Yapısı

```
automation/
├── sticker_bot.py          # Ana program
├── config.json             # Ayarlar
├── requirements.txt        # Python bağımlılıkları
├── serviceAccountKey.json  # Firebase key (SEN EKLE!)
├── modules/
│   ├── __init__.py
│   ├── trends.py           # Google Trends
│   ├── gif_fetcher.py      # Tenor API
│   ├── gif_processor.py    # FFmpeg işleme
│   └── firebase_uploader.py # Firebase yükleme
└── output/
    └── packs/              # Geçici dosyalar
```

## 🔧 Sorun Giderme

### "Service account bulunamadı" Hatası

`serviceAccountKey.json` dosyasını `automation/` klasörüne kopyaladığından emin ol.

### "FFmpeg bulunamadı" Hatası

FFmpeg'in kurulu ve PATH'te olduğundan emin ol:
```bash
ffmpeg -version
```

### "Yeterli GIF bulunamadı" Uyarısı

- Farklı bir arama terimi dene
- Trend konusu çok yeni olabilir, henüz GIF yok

## 🌐 Admin Panel Entegrasyonu

Bot tarafından oluşturulan paketler otomatik olarak Admin Panel'deki 
**"Otomasyon"** sekmesinde görünür. Buradan:

- 👁️ Paketleri önizleyebilirsin
- ✅ "Yayınla" ile uygulamaya ekleyebilirsin  
- 🗑️ Beğenmediklerini silebilirsin

## 📝 Notlar

- Kapak fotoğrafları otomatik olarak ilk sticker'dan oluşturulur (96x96)
- Daha sonra Admin Panel'den değiştirebilirsin
- Paketler `automation_drafts` koleksiyonuna taslak olarak yüklenir
- Onaylandığında `stickers` koleksiyonuna taşınır
