# GitHub Otomatik Senkronizasyon Rehberi

Bu rehber, bilgisayarinizdaki sticker klasorune dosya eklediginizde GitHub reposunun otomatik olarak guncellenmesini saglar.

---

## Icindekiler

1. [Hizli Baslangic](#hizli-baslangic)
2. [Manuel Sync (auto_sync.sh)](#manuel-sync)
3. [Otomatik Izleme (watch_and_sync.sh)](#otomatik-izleme)
4. [Systemd ile Surekli Calisma](#systemd-ile-surekli-calisma)
5. [Cron ile Zamanlanmis Sync](#cron-ile-zamanlanmis-sync)
6. [Sorun Giderme](#sorun-giderme)

---

## Hizli Baslangic

### Adim 1: Script'lere calistirma izni ver

```bash
cd ~/Desktop/StickyApp
chmod +x auto_sync.sh
chmod +x watch_and_sync.sh
```

### Adim 2: GitHub kimlik dogrulamasi ayarla

GitHub'a push yapabilmek icin kimlik dogrulamasi gerekir:

**SSH Anahtari (Onerilen):**
```bash
# SSH anahtari olustur
ssh-keygen -t ed25519 -C "email@ornek.com"

# Anahtari kopyala
cat ~/.ssh/id_ed25519.pub

# GitHub.com > Settings > SSH Keys > New SSH Key
# Kopyaladigin anahtari yapistir

# Remote'u SSH'a cevir
cd ~/Desktop/StickyApp
git remote set-url origin git@github.com:KULLANICI/StickyApp.git
```

**HTTPS + Token:**
```bash
# GitHub.com > Settings > Developer Settings > Personal Access Tokens
# Token olustur (repo yetkisi ile)

# Git credential helper kullan (sifreyi hatirlar)
git config --global credential.helper store

# Ilk push'ta kullanici adi ve token sor
git push
# Kullanici adi: GitHub kullanici adin
# Sifre: Olusturdugum token
```

### Adim 3: Test et

```bash
# Yeni bir dosya ekle
touch stickers_convert/stickers/test.txt

# Sync calistir
./auto_sync.sh

# Test dosyasini sil
rm stickers_convert/stickers/test.txt
./auto_sync.sh "Test dosyasi silindi"
```

---

## Manuel Sync

`auto_sync.sh` scripti tum degisiklikleri tek seferde GitHub'a yukler.

### Kullanim

```bash
# Otomatik commit mesaji ile
./auto_sync.sh

# Ozel commit mesaji ile
./auto_sync.sh "Yeni emoji paketi eklendi"
```

### Ne Yapar?

1. Degisiklikleri kontrol eder
2. Yeni/degisen/silinen dosyalari listeler
3. `git add -A` ile tum degisiklikleri ekler
4. `git commit` ile commit yapar
5. `git push` ile GitHub'a yukler

### Ornek Cikti

```
========================================
   StickyApp - GitHub Auto Sync
========================================

Remote: https://github.com/kullanici/StickyApp.git

Degisiklikler kontrol ediliyor...
?? stickers_convert/stickers/yeni-paket/

Degisiklik Ozeti:
  Yeni dosya:    5
  Degistirilmis: 0
  Silinen:       0

Commit mesaji: Yeni sticker eklendi - 2025-01-16 15:30

Dosyalar ekleniyor...
Commit yapiliyor...
GitHub'a yukleniyor...

========================================
   BASARILI! GitHub guncellendi.
========================================
```

---

## Otomatik Izleme

`watch_and_sync.sh` scripti klasoru surekli izler ve degisiklik oldugunda otomatik sync yapar.

### Gereksinim

```bash
# inotify-tools kurulumu
sudo apt install inotify-tools
```

### Kullanim

```bash
# Varsayilan klasoru izle (stickers_convert)
./watch_and_sync.sh

# Belirli bir klasoru izle
./watch_and_sync.sh stickers_convert/stickers

# Arka planda calistir
nohup ./watch_and_sync.sh > sync.log 2>&1 &
```

### Durdurmak Icin

- On planda calisiyorsa: `Ctrl+C`
- Arka planda calisiyorsa: `pkill -f watch_and_sync.sh`

---

## Systemd ile Surekli Calisma

Bilgisayar acildiginda otomatik baslayan bir servis olusturabilirsiniz.

### Adim 1: Servis dosyasi olustur

```bash
sudo nano /etc/systemd/system/sticker-sync.service
```

Asagidaki icerigi yapistirin (KULLANICI_ADI'ni degistirin):

```ini
[Unit]
Description=StickyApp GitHub Auto Sync
After=network.target

[Service]
Type=simple
User=KULLANICI_ADI
WorkingDirectory=/home/KULLANICI_ADI/Desktop/StickyApp
ExecStart=/home/KULLANICI_ADI/Desktop/StickyApp/watch_and_sync.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### Adim 2: Servisi aktifle

```bash
# Servisi yukle
sudo systemctl daemon-reload

# Baslat
sudo systemctl start sticker-sync

# Bilgisayar acildiginda otomatik baslat
sudo systemctl enable sticker-sync

# Durumu kontrol et
sudo systemctl status sticker-sync
```

### Servis Komutlari

```bash
# Durdur
sudo systemctl stop sticker-sync

# Yeniden baslat
sudo systemctl restart sticker-sync

# Loglari gor
journalctl -u sticker-sync -f
```

---

## Cron ile Zamanlanmis Sync

Surekli izleme yerine belirli aralıklarla sync yapmak isterseniz cron kullanabilirsiniz.

### Cron Ayarla

```bash
# Crontab duzenle
crontab -e
```

### Ornek Zamanlamalar

```bash
# Her 5 dakikada bir
*/5 * * * * cd /home/KULLANICI/Desktop/StickyApp && ./auto_sync.sh >> /tmp/sync.log 2>&1

# Her saat basinda
0 * * * * cd /home/KULLANICI/Desktop/StickyApp && ./auto_sync.sh >> /tmp/sync.log 2>&1

# Her gun saat 18:00'de
0 18 * * * cd /home/KULLANICI/Desktop/StickyApp && ./auto_sync.sh >> /tmp/sync.log 2>&1

# Her 30 dakikada (09:00-21:00 arasi)
*/30 9-21 * * * cd /home/KULLANICI/Desktop/StickyApp && ./auto_sync.sh >> /tmp/sync.log 2>&1
```

---

## GitHub Actions ile Otomatik Islemler (Bonus)

GitHub'a push yapildiginda otomatik islemler tanimlanabilir.

`.github/workflows/on-sticker-update.yml` dosyasi olusturun:

```yaml
name: Sticker Update

on:
  push:
    paths:
      - 'stickers_convert/stickers/**'
      - 'stickers_convert/premium_stickers/**'

jobs:
  notify:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Count stickers
        run: |
          echo "Toplam sticker paketi sayisi:"
          ls -d stickers_convert/stickers/*/ 2>/dev/null | wc -l
```

---

## Sorun Giderme

### "Permission denied" hatasi

```bash
chmod +x auto_sync.sh
chmod +x watch_and_sync.sh
```

### "inotifywait: command not found" hatasi

```bash
sudo apt install inotify-tools
```

### "Authentication failed" hatasi

GitHub kimlik dogrulamasi yapilamadi. SSH veya token ayarlayin (yukaridaki adimlara bakin).

### "rejected - non-fast-forward" hatasi

Remote'ta sizde olmayan degisiklikler var:

```bash
git pull --rebase
./auto_sync.sh
```

### Script calisiyor ama push yapmiyor

1. Internet baglantisini kontrol edin
2. `git push` komutunu manuel deneyin
3. SSH/token ayarlarini kontrol edin

### Cok fazla commit olusuyor

`watch_and_sync.sh` icindeki `DEBOUNCE_TIME` degerini artirin:

```bash
# Script icinde bu satiri bulun ve degistirin
DEBOUNCE_TIME=30  # 30 saniye bekle
```

---

## Onemli Notlar

1. **Buyuk dosyalar**: Git buyuk dosyalar icin uygun degildir. 100MB uzerindeki dosyalar icin Git LFS kullanin.

2. **Hassas dosyalar**: `google-services.json` ve Firebase anahtarlari `.gitignore`'da olmali (zaten eklendi).

3. **Conflict durumu**: Baska bir yerden de push yapiliyorsa conflict olusabilir. Bu durumda:
   ```bash
   git pull --rebase
   # Cakismalari coz
   git push
   ```

4. **Disk alani**: Git gecmisi buyuyebilir. Ara sira temizlik yapin:
   ```bash
   git gc --prune=now
   ```

---

## Hizli Komutlar Ozeti

```bash
# Manuel sync
./auto_sync.sh

# Ozel mesajla sync
./auto_sync.sh "Yeni sticker paketi"

# Otomatik izleme baslat
./watch_and_sync.sh

# Arka planda izleme
nohup ./watch_and_sync.sh > sync.log 2>&1 &

# Arka plan islemini durdur
pkill -f watch_and_sync.sh

# Degisiklikleri gor
git status

# Son commitleri gor
git log --oneline -5
```

---

*Son guncelleme: Ocak 2025*
