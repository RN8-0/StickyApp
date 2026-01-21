# Sticly - Yeni Nesil WhatsApp Sticker Yönetim Sistemi 🚀

WhatsApp için özel sticker paketleri oluşturan, bulut tabanlı çalışan profesyonel Android uygulaması ve Web Yönetim Paneli.

---

## 🌐 Canlı Yönetim Paneli
Artık terminal veya Python ile uğraşmanıza gerek yok! Paketinizi tarayıcı üzerinden yönetin:
👉 **[http://localhost:5173/StickyApp/](http://localhost:5173/StickyApp/)**
*(GitHub Pages linkiniz: `https://arain-0.github.io/StickyApp/`)*

---

## ✨ Özellikler

- **Web Admin v2.0:** Tamamen yenilenmiş, görsel ve hızlı yönetim paneli.
- **Güvenli Erişim:** Firebase Authentication ile şifreli yönetici girişi.
- **Gelişmiş İstatistikler:** Paket indirme, görüntülenme ve verimlilik analizleri.
- **Premium Sistem:** Ücretli/Ücretsiz paket ayrımı ve kategori yönetimi.
- **Otomatik İşleme:** MP4, GIF, PNG -> WebP dönüşümü (Android tarafında optimize).

---

## 🛠️ Hızlı Kurulum ve Otomasyon

Projenin bağımlılıklarını kontrol etmek, eksik dosyaları (Admin SDK vb.) tamamlamak ve sistemi başlatmak için yeni **stickers_setup.py** aracını kullanın:

```bash
python3 stickers_setup.py
```

**⚠️ ÖNEMLİ: Firebase Admin SDK Nasıl Alınır?**
Web Admin panelinin çalışması için `firebase-admin-sdk.json` dosyası gereklidir:
1. [Firebase Console](https://console.firebase.google.com) > Proje Ayarları > **Hizmet Hesapları** sekmesine gidin.
2. Merkezdeki **"Yeni Özel Anahtar Oluştur"** butonuna basın.
3. İnen dosyayı `firebase-admin-sdk.json` olarak adlandırıp ana dizine atın.

**☁️ Google Drive Notu:**
Eski sistemdeki Google Drive bağımlılığı tamamen kaldırılmıştır. Artık tüm işlemler doğrudan Firebase üzerinden daha güvenli ve hızlı yapılmaktadır.

---

## 📱 Android Uygulama Gereksinimleri

| Gereksinim | Açıklama |
|------------|----------|
| Android Studio | Uygulama geliştirme ve APK derleme |
| Firebase Hesabı | Veritabanı ve dosya depolama |
| google-services.json | `app/` klasörüne eklenmelidir |

---

## 📂 Proje Yapısı

```
StickyApp/
├── app/                      # Android Uygulama Kaynak Kodları (Kotlin)
├── sticker_admin_web/        # [YENİ] React tabanlı Web Yönetim Paneli
├── stickers_setup.py         # [YENİ] Sistem Kontrol ve Otomasyon Aracı
├── stickers_convert/         # Sticker Veri Deposu (Yedekler)
└── README.md                 # Bu Dosya
```

---

## 🚀 Yayına Alma (GitHub Pages)

Web panelinizi GitHub üzerinde yayınlamak için:
1. `cd sticker_admin_web`
2. `npm run deploy` 

Bu komut projeyi derler ve otomatik olarak GitHub Pages şubesinde yayınlar.

---

## 📄 Lisans ve Destek
Bu proje WhatsApp Sticker API'ları ile uyumlu geliştirilmiştir. Sorun yaşarsanız `stickers_setup.py` üzerinden bağımlılıkları kontrol edin veya Firebase Console loglarına göz atın.

**Son Güncelleme:** Ocak 2026 - v2.0 PRO
