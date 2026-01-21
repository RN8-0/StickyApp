#!/bin/bash
# =============================================================================
# STICLY STICKER MANAGER v6.0 - Otomatik Kurulum ve Calistirma (Linux/Mac)
# =============================================================================
# Bu script:
# 1. Gerekli sistem bagimliliklerini kontrol eder
# 2. Python virtual environment olusturur/aktive eder
# 3. Python bagimliliklerini kurar
# 4. GUI uygulamasini baslatir
# =============================================================================

# Renkler
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Calisma dizinine git
cd "$(dirname "$0")"

clear
echo ""
echo "============================================================"
echo "   ${BLUE}STICLY STICKER MANAGER v6.0${NC}"
echo "   Professional WhatsApp Sticker Toolkit"
echo "============================================================"
echo ""

# Parametre kontrolu
MODE="gui"
if [ "$1" == "--cli" ] || [ "$1" == "-c" ]; then
    MODE="cli"
fi
if [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    echo "Kullanim: ./upload_stickers.sh [secenek]"
    echo ""
    echo "Secenekler:"
    echo "  --gui, -g    GUI modunda baslat (varsayilan)"
    echo "  --cli, -c    CLI modunda baslat"
    echo "  --help, -h   Bu yardim mesajini goster"
    echo ""
    exit 0
fi

# ============================================================================
# SISTEM KONTROLLERI
# ============================================================================

echo "[1/6] Python kontrol ediliyor..."
if ! command -v python3 &> /dev/null; then
    echo -e "   ${RED}X Python3 bulunamadi!${NC}"
    echo ""
    echo "   Kurulum:"
    echo "   Ubuntu/Debian: sudo apt install python3 python3-venv python3-pip"
    echo "   macOS: brew install python3"
    echo "   Fedora: sudo dnf install python3"
    echo ""
    exit 1
fi
PYTHON_VERSION=$(python3 --version 2>&1)
echo -e "   ${GREEN}+ $PYTHON_VERSION${NC}"

echo "[2/6] FFmpeg kontrol ediliyor..."
if ! command -v ffmpeg &> /dev/null; then
    echo -e "   ${YELLOW}! FFmpeg bulunamadi${NC}"
    echo "   Video donusturme calismayacak."
    echo "   Kurulum:"
    echo "   Ubuntu/Debian: sudo apt install ffmpeg"
    echo "   macOS: brew install ffmpeg"
    echo ""
else
    echo -e "   ${GREEN}+ FFmpeg yuklu${NC}"
fi

echo "[3/6] Git kontrol ediliyor..."
if ! command -v git &> /dev/null; then
    echo -e "   ${YELLOW}! Git bulunamadi${NC}"
    echo "   GitHub sync calismayacak."
else
    echo -e "   ${GREEN}+ Git yuklu${NC}"
fi

# ============================================================================
# VIRTUAL ENVIRONMENT
# ============================================================================

echo "[4/6] Python ortami hazirlaniyor..."
if [ ! -d "venv" ]; then
    echo "   Virtual environment olusturuluyor..."
    python3 -m venv venv
    if [ $? -ne 0 ]; then
        echo -e "   ${RED}X venv olusturulamadi!${NC}"
        echo "   Deneyebilirsiniz: sudo apt install python3-venv"
        exit 1
    fi
    echo -e "   ${GREEN}+ venv olusturuldu${NC}"
else
    echo -e "   ${GREEN}+ venv mevcut${NC}"
fi

# Virtual environment'i aktive et
source venv/bin/activate

# ============================================================================
# BAGIMLILIKLAR
# ============================================================================

echo "[5/6] Bagimliliklar kontrol ediliyor..."

# Pip'i guncelle (sessiz)
pip install --upgrade pip -q 2>/dev/null

# requirements.txt varsa kullan
if [ -f "requirements.txt" ]; then
    echo "   requirements.txt'den yukleniyor..."
    pip install -r requirements.txt -q 2>/dev/null
    echo -e "   ${GREEN}+ Bagimliliklar yuklendi${NC}"
else
    echo "   Temel bagimliliklar yukleniyor..."

    # Temel paketler
    PACKAGES=(
        "customtkinter>=5.2.0"
        "firebase-admin>=6.2.0"
        "google-api-python-client>=2.100.0"
        "google-auth-httplib2>=0.1.1"
        "google-auth-oauthlib>=1.1.0"
        "pillow>=10.0.0"
        "requests>=2.31.0"
    )

    for pkg in "${PACKAGES[@]}"; do
        pip install "$pkg" -q 2>/dev/null
    done

    # Rembg (opsiyonel, buyuk)
    echo "   rembg yukleniyor (arka plan silme icin)..."
    pip install "rembg[cpu]" -q 2>/dev/null

    echo -e "   ${GREEN}+ Bagimliliklar yuklendi${NC}"
fi

# ============================================================================
# YAPILANDIRMA KONTROLLERI
# ============================================================================

echo "[6/6] Yapilandirma kontrol ediliyor..."

# Firebase Admin SDK key kontrolu
FIREBASE_KEY=$(ls *firebase-adminsdk*.json 2>/dev/null | head -1)
if [ -z "$FIREBASE_KEY" ]; then
    echo -e "   ${YELLOW}! Firebase Admin SDK anahtari bulunamadi${NC}"
    echo ""
    echo "   Firebase islemleri icin:"
    echo "   1. Firebase Console > Project Settings > Service Accounts"
    echo "   2. 'Generate new private key' tiklayin"
    echo "   3. JSON dosyasini bu klasore koyun"
    echo ""
else
    echo -e "   ${GREEN}+ Firebase key: $FIREBASE_KEY${NC}"
fi

# Google credentials kontrolu
if [ ! -f "credentials.json" ]; then
    echo -e "   ${YELLOW}! Google credentials.json bulunamadi${NC}"
    echo ""
    echo "   Drive sync icin:"
    echo "   1. Google Cloud Console > APIs & Services > Credentials"
    echo "   2. OAuth 2.0 Client ID olusturun (Desktop app)"
    echo "   3. JSON'u 'credentials.json' olarak kaydedin"
    echo ""
else
    echo -e "   ${GREEN}+ Google credentials mevcut${NC}"
fi

# ============================================================================
# UYGULAMA BASLATMA
# ============================================================================

echo ""
echo "============================================================"
if [ "$MODE" == "gui" ]; then
    echo "   ${GREEN}STICLY GUI BASLATILIYOR...${NC}"
else
    echo "   ${GREEN}STICLY CLI BASLATILIYOR...${NC}"
fi
echo "============================================================"
echo ""

# Uygulamayi calistir
if [ "$MODE" == "gui" ]; then
    # GUI modu - sticker_manager.py tercih et
    if [ -f "sticker_manager.py" ]; then
        python3 sticker_manager.py
    elif [ -f "sticly_pro.py" ]; then
        python3 sticly_pro.py
    else
        echo -e "${YELLOW}GUI dosyasi bulunamadi, CLI baslatiliyor...${NC}"
        python3 upload_stickers.py
    fi
else
    # CLI modu
    python3 upload_stickers.py
fi

echo ""
echo "Islem tamamlandi!"
