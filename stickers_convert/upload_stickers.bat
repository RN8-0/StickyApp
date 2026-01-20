@echo off
chcp 65001 >nul
REM =============================================================================
REM STICLY STICKER MANAGER - Otomatik Kurulum ve Calistirma (Windows)
REM =============================================================================
REM Bu script:
REM 1. Gerekli bagimliliklari kontrol eder ve kurar
REM 2. Python virtual environment olusturur
REM 3. GUI uygulamasini baslatir (veya CLI'yi --cli parametresiyle)
REM =============================================================================

title Sticly Sticker Manager

cd /d "%~dp0"

echo ============================================================
echo    STICLY STICKER MANAGER - Otomatik Kurulum
echo ============================================================
echo.

REM Parametre kontrolu
set "MODE=gui"
if "%1"=="--cli" set "MODE=cli"
if "%1"=="-c" set "MODE=cli"

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

REM requirements.txt varsa onu kullan
if exist "requirements.txt" (
    echo    requirements.txt'den yukluyor...
    venv\Scripts\pip install -r requirements.txt -q 2>nul
    echo    + Bagimliliklar yuklendi
) else (
    REM Manuel kurulum (eski uyumluluk icin)
    venv\Scripts\python -c "import firebase_admin" 2>nul
    if %errorlevel% neq 0 (
        echo    firebase-admin yukleniyor...
        venv\Scripts\pip install firebase-admin -q
        echo    + firebase-admin yuklendi
    ) else (
        echo    + firebase-admin mevcut
    )

    venv\Scripts\python -c "import rembg" 2>nul
    if %errorlevel% neq 0 (
        echo    rembg yukleniyor (bu biraz surebilir)...
        venv\Scripts\pip install "rembg[cpu]" -q
        echo    + rembg yuklendi
    ) else (
        echo    + rembg mevcut
    )

    venv\Scripts\python -c "import customtkinter" 2>nul
    if %errorlevel% neq 0 (
        echo    customtkinter yukleniyor...
        venv\Scripts\pip install customtkinter -q
        echo    + customtkinter yuklendi
    ) else (
        echo    + customtkinter mevcut
    )
)

REM Firebase Admin SDK key kontrolu
echo [5/5] Firebase yapilandirmasi kontrol ediliyor...
set "FIREBASE_KEY="
for %%f in (*firebase-adminsdk*.json) do set "FIREBASE_KEY=%%f"
if "%FIREBASE_KEY%"=="" (
    echo    ! Firebase Admin SDK anahtari bulunamadi!
    echo.
    echo    GUI baslatilacak ama Firebase islemleri calismayacak.
    echo    Anahtar dosyasini bu klasore koyun.
    echo.
) else (
    echo    + Firebase anahtari bulundu
)

echo.
echo ============================================================
if "%MODE%"=="gui" (
    echo    STICLY GUI BASLATILIYOR...
) else (
    echo    STICLY CLI BASLATILIYOR...
)
echo ============================================================
echo.

REM Uygulamayi calistir
if "%MODE%"=="gui" (
    if exist "sticly_gui.py" (
        venv\Scripts\python sticly_gui.py
    ) else (
        echo    ! GUI dosyasi bulunamadi, CLI baslatiliyor...
        venv\Scripts\python upload_stickers.py
    )
) else (
    venv\Scripts\python upload_stickers.py
)

echo.
echo Islem tamamlandi!
echo.
pause
