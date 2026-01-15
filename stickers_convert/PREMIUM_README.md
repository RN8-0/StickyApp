# 🌟 Premium Sticker Paketi Ekleme Rehberi

## Premium Sistem Özellikleri

### Kullanıcı Deneyimi
- **Taç Simgesi**: Premium paketlerin sağında sarı taç simgesi görünür
- **Görsel Kilit**: Kilitli sticker'ların üzerinde kilit simgesi ve soluk görünüm
- **Reklamsız**: Premium üyeler reklamsız deneyim yaşar

### Premium Avantajları
1. ✅ Tüm premium sticker paketlerine erişim
2. ✅ Reklamsız uygulama deneyimi
3. ✅ Tek seferlik ödeme (lifetime)

---

## Klasör Yapısı

```
stickers_convert/
├── stickers/                  ← Normal (ücretsiz) paketler
│   ├── emoji-pack/
│   │   ├── tray.png
│   │   ├── 01.png
│   │   └── ...
│   └── fun-pack/
│       └── ...
│
├── premium_stickers/          ← Premium (ücretli) paketler
│   ├── premium-animals/
│   │   ├── tray.png
│   │   ├── 01.png
│   │   └── ...
│   └── vip-collection/
│       └── ...
│
├── upload_stickers.py         ← Her iki klasörü de işler
└── PREMIUM_README.md          ← Bu dosya
```

---

## 📦 Premium Sticker Paketi Nasıl Eklenir?

### Adım 1: Sticker'ları Hazırla
Desteklenen formatlar:
- **Video**: MP4, MOV, AVI, MKV, WEBM (animasyonlu)
- **Resim**: PNG, JPG, JPEG, WEBP, BMP (statik)
- **GIF**: Animasyonlu veya statik

### Adım 2: Klasör Oluştur
```bash
cd stickers_convert/premium_stickers
mkdir "Premium Hayvanlar"
```

### Adım 3: Dosyaları Ekle
```
premium_stickers/
└── Premium Hayvanlar/
    ├── tray.png          # Kapak resmi (opsiyonel)
    ├── 01.png            # Minimum 3 sticker
    ├── 02.png
    ├── 03.png
    └── ... (maks 30)
```

**Önemli:**
- Minimum 3, maksimum 30 sticker olmalı
- `tray.png` eklenmezse ilk sticker'dan otomatik oluşturulur
- Dosya isimleri önemli değil (01.png, sticker1.png, vs. hepsi çalışır)

### Adım 4: Script'i Çalıştır
```bash
cd stickers_convert
source venv/bin/activate   # Linux/macOS
python3 upload_stickers.py
```

Script otomatik olarak:
1. ✅ Her iki klasörü tarar (`stickers/` ve `premium_stickers/`)
2. ✅ Sticker'ları WebP formatına dönüştürür
3. ✅ Arka planları siler (aktifse)
4. ✅ Firebase Storage'a yükler
5. ✅ Firebase Firestore'a kaydeder
6. ✅ **Premium paketleri `isPremium: true` olarak işaretler**

---

## 🎯 Örnek Kullanım

### Senaryo: "VIP Emojiler" Premium Paketi Eklemek

```bash
# 1. Premium klasörde paket oluştur
mkdir -p premium_stickers/"VIP Emojiler"

# 2. Sticker'ları ekle
cp emoji1.png premium_stickers/"VIP Emojiler"/01.png
cp emoji2.png premium_stickers/"VIP Emojiler"/02.png
cp emoji3.png premium_stickers/"VIP Emojiler"/03.png
# ... daha fazla

# 3. Tray (kapak) ekle (opsiyonel)
cp cover.png premium_stickers/"VIP Emojiler"/tray.png

# 4. Yükle
python3 upload_stickers.py
```

### Çıktı Örneği:
```
============================================================
   STICKER YÜKLEYICI - Otomatik Dönüştürme & Firebase
============================================================

 📦 2 normal paket bulundu
 🌟 1 premium paket bulundu
 📊 Toplam: 3 paket

============================================================
 NORMAL PAKETLER İŞLENİYOR
============================================================

 📦 NORMAL Paket: Emoji Pack
   [1/4] 01.png OK (45KB, static)
   ...

============================================================
 🌟 PREMIUM PAKETLER İŞLENİYOR 🌟
============================================================

 🌟 PREMIUM Paket: VIP Emojiler
   [1/5] 01.png OK (52KB, static)
   [2/5] 02.png OK (48KB, static)
   ...
   Firebase'e kaydediliyor... OK

============================================================
 ✅ TAMAMLANDI: 3/3 paket başarılı
 📦 Normal: 2 paket
 🌟 Premium: 1 paket
============================================================
```

---

## 🔄 Güncelleme ve Silme

### Premium Paketi Güncelleme
```bash
# Yeni sticker ekle
cp new_sticker.png premium_stickers/"VIP Emojiler"/

# Script'i tekrar çalıştır (sadece yeni dosyalar işlenir - cache)
python3 upload_stickers.py
```

### Premium Paketi Silme
```bash
# Klasörü sil
rm -rf premium_stickers/"VIP Emojiler"

# Script çalıştır (Firebase'den otomatik temizlenir)
python3 upload_stickers.py
```

---

## 📱 Uygulamada Görünüm

### Ana Ekranda
```
┌─────────────────────────────────────┐
│ [🔷 Icon]  VIP Emojiler        👑  │  ← Sarı taç simgesi
│            Sticly                   │
│            5 stickers     [PREMIUM] │  ← Premium badge
└─────────────────────────────────────┘
```

### Detay Ekranında (Premium Olmayan Kullanıcı)
```
┌───────┬───────┬───────┐
│   ✓   │   ✓   │   ✓   │  ← İlk 3 görünür
├───────┼───────┼───────┤
│   🔒  │   🔒  │   🔒  │  ← Geri kalanlar kilitli
└───────┴───────┴───────┘
```

### Premium Kullanıcı İçin
```
┌───────┬───────┬───────┐
│   ✓   │   ✓   │   ✓   │  ← Tümü görünür
├───────┼───────┼───────┤
│   ✓   │   ✓   │   ✓   │  ← Kilit yok
└───────┴───────┴───────┘
```

---

## ⚙️ Teknik Detaylar

### Firebase Veri Yapısı

**Normal Paket:**
```json
{
  "name": "Emoji Pack",
  "isPremium": false,
  "stickers": [...]
}
```

**Premium Paket:**
```json
{
  "name": "VIP Emojiler",
  "isPremium": true,    ← Premium işareti
  "stickers": [...]
}
```

### Uygulama Kodu Davranışı

**PackAdapter** (Ana Liste):
```kotlin
// Taç simgesi - premium pakette göster
h.crownIcon.visibility = if (isPremiumPack) View.VISIBLE else View.GONE

// Premium badge - kullanıcı premium değilse göster
h.premiumBadge.visibility =
    if (isPremiumPack && !isUserPremium) View.VISIBLE else View.GONE
```

**StickerAdapter** (Detay Ekranı):
```kotlin
// İlk 3'ten sonrakiler kilitli (premium pakette ve user premium değilse)
val isLocked = isPackPremium && !isUserPremium && pos >= 3

h.lockIcon.visibility = if (isLocked) View.VISIBLE else View.GONE
h.img.alpha = if (isLocked) 0.3f else 1f  // Soluk görünüm
```

---

## ✅ Checklist

Yeni premium paket eklemeden önce:

- [ ] Minimum 3 sticker hazır
- [ ] Dosyalar `premium_stickers/paket-adi/` klasöründe
- [ ] Tray (kapak) resmi eklendi veya otomatik oluşturacak
- [ ] Firebase ayarları doğru (`firebase-adminsdk-*.json` mevcut)
- [ ] Script çalıştırıldı: `python3 upload_stickers.py`
- [ ] Firebase Console'da paket görünüyor
- [ ] Uygulamada taç simgesi görünüyor
- [ ] Ücretsiz kullanıcıda ilk 3 sticker görünüyor, geri kalanlar kilitli

---

## 🎨 İpuçları

### Premium İçerik Stratejisi
1. **Kaliteli İçerik**: Premium paketler özel, yüksek kaliteli olmalı
2. **Sayı Dengesi**: 5-10 premium, 20-30 normal paket dengesi iyi
3. **Önizleme**: İlk 3 sticker en çekici olanlar olmalı (satış için)
4. **Tema**: Premium paketleri tematik grupla (VIP Hayvanlar, Lüks Emojiler vs.)

### Performans
- Cache sistemi sayesinde aynı dosya tekrar işlenmez
- Değişmeyen paketler hızlıca atlanır
- Sadece yeni/değişen içerik Firebase'e yüklenir

---

## 🆘 Sorun Giderme

### Premium paket normal görünüyor
```bash
# Paketi kontrol et
grep -r "paket-adi" premium_stickers/

# Script'i tekrar çalıştır
python3 upload_stickers.py

# Firebase Console'da isPremium: true olmalı
```

### Taç simgesi görünmüyor
- Uygulamayı yeniden derleyin: `./gradlew assembleDebug`
- Cache'i temizleyin
- Firebase'den veri çekildiğinden emin olun

### Kilit çalışmıyor
- PreferencesHelper.isPremium() false dönüyor olmalı
- StickerAdapter'da isPackPremium true olmalı
- İlk 3 sticker visible, 4+ kilitli olmalı

---

## 📚 Ek Kaynaklar

- **Ana README**: `../README.md`
- **Firebase Console**: https://console.firebase.google.com
- **Google Play Console**: Ürün ID'sini buradan alın

---

**🎉 Hazırsınız! Premium içerik eklemeye başlayabilirsiniz!**
