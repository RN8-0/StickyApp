@echo off
chcp 65001 >nul
REM =============================================================================
REM STICKER YUKLEYICI - Otomatik Kurulum ve Calistirma (Windows)
REM =============================================================================
REM Bu script:
REM 1. Gerekli bagimliliklari kontrol eder ve kurar
REM 2. Python virtual environment olusturur
REM 3. Stickerleri Firebase'e ve GitHub'a yukler
REM =============================================================================

title Sticker Yukleyici

cd /d "%~dp0"

echo ============================================================
echo    STICKER YUKLEYICI - Otomatik Kurulum
echo ============================================================
echo.

REM Python kontrolu
echo [1/5] Python kontrol ediliyor...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo    X Python bulunamadi!
    echo.
    echo    Python'u indirin: https://www.python.org/downloads/
    echo    Kurulum sirasinda "Add Python to PATH" secenegini isaretleyin!
    echo.
    pause
    exit /b 1
)
for /f "tokens=*" %%i in ('python --version 2^>^&1') do echo    + %%i

REM FFmpeg kontrolu
echo [2/5] FFmpeg kontrol ediliyor...
ffmpeg -version >nul 2>&1
if %errorlevel% neq 0 (
    echo    ! FFmpeg bulunamadi!
    echo.
    echo    FFmpeg'i indirin: https://ffmpeg.org/download.html
    echo    veya winget ile: winget install ffmpeg
    echo.
    echo    Indirdikten sonra bin klasorunu PATH'e ekleyin.
    echo.
    pause
    exit /b 1
)
echo    + FFmpeg yuklu

REM Virtual environment kontrolu
echo [3/5] Python ortami hazirlaniyor...
if not exist "venv" (
    echo    Virtual environment olusturuluyor...
    python -m venv venv
    echo    + venv olusturuldu
) else (
    echo    + venv mevcut
)

REM Bagimliliklari yukle
echo [4/5] Bagimliliklar kontrol ediliyor...

REM Pip'i guncelle
venv\Scripts\pip install --upgrade pip -q 2>nul

REM Firebase admin kontrol et
venv\Scripts\python -c "import firebase_admin" 2>nul
if %errorlevel% neq 0 (
    echo    firebase-admin yukleniyor...
    venv\Scripts\pip install firebase-admin -q
    echo    + firebase-admin yuklendi
) else (
    echo    + firebase-admin mevcut
)

REM rembg kontrol et
venv\Scripts\python -c "import rembg" 2>nul
if %errorlevel% neq 0 (
    echo    rembg yukleniyor (bu biraz surebilir)...
    venv\Scripts\pip install "rembg[cpu]" -q
    echo    + rembg yuklendi
) else (
    echo    + rembg mevcut
)

REM Firebase Admin SDK key kontrolu
echo [5/5] Firebase yapilandirmasi kontrol ediliyor...
set "FIREBASE_KEY="
for %%f in (*firebase-adminsdk*.json) do set "FIREBASE_KEY=%%f"
if "%FIREBASE_KEY%"=="" (
    echo    X Firebase Admin SDK anahtari bulunamadi!
    echo.
    echo    Anahtar dosyasini bu klasore koyun.
    echo.
    echo    Dosyayi almak icin:
    echo    1. https://console.firebase.google.com adresine gidin
    echo    2. Proje Ayarlari - Hizmet Hesaplari
    echo    3. 'Yeni Ozel Anahtar Olustur' tiklayin
    echo    4. Indirilen JSON dosyasini bu klasore koyun
    echo.
    pause
    exit /b 1
) else (
    echo    + Firebase anahtari bulundu
)

echo.
echo ============================================================
echo    KURULUM TAMAMLANDI - Sticker yukleyici baslatiliyor...
echo ============================================================
echo.

REM Ana scripti calistir
venv\Scripts\python upload_stickers.py

echo.
echo Islem tamamlandi!
echo.
pause
