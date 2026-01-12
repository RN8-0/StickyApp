# VS CODE İLE ÇALIŞTIRMA

## 1. Gerekli Eklentiler

VS Code'da yükle:
- **Kotlin** (fwcd.kotlin)
- **Android for VS Code** (adelphes.android-dev-ext)
- **Gradle for Java** (vscjava.vscode-gradle)

## 2. Proje Aç

```bash
cd ~/Desktop
tar -xzf StickyApp.tar.gz
cd StickyApp
code .
```

## 3. Terminal'de Build

VS Code'un terminalinde:

```bash
# Android SDK path'i ayarla (kendi path'ini kullan)
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Build
./gradlew clean
./gradlew assembleDebug

# Cihaza yükle
./gradlew installDebug

# Başlat
adb shell am start -n com.sticly/.MainActivity
```

## 4. Sticker'ları Değiştir

```bash
# Kendi sticker'ını app/src/main/assets/stickers/pack1/ klasörüne at
# Squoosh.app kullan: 512x512, WebP

# Rebuild
./gradlew clean assembleDebug installDebug
```

## 5. Logları İzle

```bash
adb logcat | grep -E "Sticky|ERROR"
```

## Hızlı Komutlar

```bash
# Temizle ve build
./gradlew clean build

# Sadece yükle
./gradlew installDebug

# Debug logları
adb logcat -d | grep Sticky
```

Başarılar! 🚀
