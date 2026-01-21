@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

REM =============================================================================
REM STICLY STICKER MANAGER v6.0 - Otomatik Kurulum ve Calistirma (Windows)
REM =============================================================================
REM Bu script:
REM 1. Gerekli sistem bagimliliklerini kontrol eder
REM 2. Python virtual environment olusturur/aktive eder
REM 3. Python bagimliliklerini kurar
REM 4. GUI uygulamasini baslatir
REM =============================================================================

title Sticly Sticker Manager v6.0

cd /d "%~dp0"

cls
echo.
echo ============================================================
echo    STICLY STICKER MANAGER v6.0
echo    Professional WhatsApp Sticker Toolkit
echo ============================================================
echo.

REM Parametre kontrolu
set "MODE=gui"
if "%1"=="--cli" set "MODE=cli"
if "%1"=="-c" set "MODE=cli"
if "%1"=="--help" goto :help
if "%1"=="-h" goto :help

goto :start

:help
echo Kullanim: upload_stickers.bat [secenek]
echo.
echo Secenekler:
echo   --gui, -g    GUI modunda baslat (varsayilan)
echo   --cli, -c    CLI modunda baslat
echo   --help, -h   Bu yardim mesajini goster
echo.
exit /b 0

:start

REM ============================================================================
REM SISTEM KONTROLLERI
REM ============================================================================

echo [1/6] Python kontrol ediliyor...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo    X Python bulunamadi!
    echo.
    echo    Kurulum:
    echo    1. Python'u indirin: https://www.python.org/downloads/
    echo    2. Kurulum sirasinda "Add Python to PATH" secenegini isaretleyin!
    echo    3. Bilgisayari yeniden baslatin
    echo.
    pause
    exit /b 1
)
for /f "tokens=*" %%i in ('python --version 2^>^&1') do echo    + %%i

echo [2/6] FFmpeg kontrol ediliyor...
ffmpeg -version >nul 2>&1
if %errorlevel% neq 0 (
    echo    ! FFmpeg bulunamadi
    echo    Video donusturme calismayacak.
    echo    Kurulum: winget install ffmpeg
    echo.
) else (
    echo    + FFmpeg yuklu
)

echo [3/6] Git kontrol ediliyor...
git --version >nul 2>&1
if %errorlevel% neq 0 (
    echo    ! Git bulunamadi
    echo    GitHub sync calismayacak.
) else (
    echo    + Git yuklu
)

REM ============================================================================
REM VIRTUAL ENVIRONMENT
REM ============================================================================

echo [4/6] Python ortami hazirlaniyor...
if not exist "venv" (
    echo    Virtual environment olusturuluyor...
    python -m venv venv
    if %errorlevel% neq 0 (
        echo    X venv olusturulamadi!
        pause
        exit /b 1
    )
    echo    + venv olusturuldu
) else (
    echo    + venv mevcut
)

REM ============================================================================
REM BAGIMLILIKLAR
REM ============================================================================

echo [5/6] Bagimliliklar kontrol ediliyor...

REM Pip'i guncelle
venv\Scripts\pip install --upgrade pip -q 2>nul

REM requirements.txt varsa kullan
if exist "requirements.txt" (
    echo    requirements.txt'den yukleniyor...
    venv\Scripts\pip install -r requirements.txt -q 2>nul
    echo    + Bagimliliklar yuklendi
) else (
    echo    Temel bagimliliklar yukleniyor...

    REM Temel paketler
    venv\Scripts\pip install "customtkinter>=5.2.0" -q 2>nul
    venv\Scripts\pip install "firebase-admin>=6.2.0" -q 2>nul
    venv\Scripts\pip install "google-api-python-client>=2.100.0" -q 2>nul
    venv\Scripts\pip install "google-auth-httplib2>=0.1.1" -q 2>nul
    venv\Scripts\pip install "google-auth-oauthlib>=1.1.0" -q 2>nul
    venv\Scripts\pip install "pillow>=10.0.0" -q 2>nul
    venv\Scripts\pip install "requests>=2.31.0" -q 2>nul

    REM Rembg (opsiyonel, buyuk)
    echo    rembg yukleniyor (arka plan silme icin)...
    venv\Scripts\pip install "rembg[cpu]" -q 2>nul

    echo    + Bagimliliklar yuklendi
)

REM ============================================================================
REM YAPILANDIRMA KONTROLLERI
REM ============================================================================

echo [6/6] Yapilandirma kontrol ediliyor...

REM Firebase Admin SDK key kontrolu
set "FIREBASE_KEY="
for %%f in (*firebase-adminsdk*.json) do set "FIREBASE_KEY=%%f"
if "%FIREBASE_KEY%"=="" (
    echo    ! Firebase Admin SDK anahtari bulunamadi
    echo.
    echo    Firebase islemleri icin:
    echo    1. Firebase Console ^> Project Settings ^> Service Accounts
    echo    2. 'Generate new private key' tiklayin
    echo    3. JSON dosyasini bu klasore koyun
    echo.
) else (
    echo    + Firebase key: %FIREBASE_KEY%
)

REM Google credentials kontrolu
if not exist "credentials.json" (
    echo    ! Google credentials.json bulunamadi
    echo.
    echo    Drive sync icin:
    echo    1. Google Cloud Console ^> APIs ^& Services ^> Credentials
    echo    2. OAuth 2.0 Client ID olusturun ^(Desktop app^)
    echo    3. JSON'u 'credentials.json' olarak kaydedin
    echo.
) else (
    echo    + Google credentials mevcut
)

REM ============================================================================
REM UYGULAMA BASLATMA
REM ============================================================================

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
    REM GUI modu - sticker_manager.py tercih et
    if exist "sticker_manager.py" (
        venv\Scripts\python sticker_manager.py
    ) else if exist "sticly_pro.py" (
        venv\Scripts\python sticly_pro.py
    ) else (
        echo    ! GUI dosyasi bulunamadi, CLI baslatiliyor...
        venv\Scripts\python upload_stickers.py
    )
) else (
    REM CLI modu
    venv\Scripts\python upload_stickers.py
)

echo.
echo Islem tamamlandi!
echo.
pause
