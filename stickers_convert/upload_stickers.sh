#!/bin/bash
# =============================================================================
# STICLY STICKER MANAGER - Otomatik Kurulum ve Calistirma (Linux/Mac)
# =============================================================================
# Bu script:
# 1. Gerekli bagimliliklari kontrol eder ve kurar
# 2. Python virtual environment olusturur
# 3. GUI uygulamasini baslatir (veya CLI'yi --cli parametresiyle)
# =============================================================================

# Renkler
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Calisma dizinine git
cd "$(dirname "$0")"

echo "============================================================"
echo "   STICLY STICKER MANAGER - Otomatik Kurulum"
echo "============================================================"
echo ""

# Parametre kontrolu
MODE="gui"
if [ "$1" == "--cli" ] || [ "$1" == "-c" ]; then
    MODE="cli"
fi

# Python kontrolu
echo "[1/5] Python kontrol ediliyor..."
if ! command -v python3 &> /dev/null; then
    echo -e "   ${RED}X Python3 bulunamadi!${NC}"
    echo ""
    echo "   Ubuntu/Debian: sudo apt install python3 python3-venv python3-pip"
    echo "   macOS: brew install python3"
    echo ""
    exit 1
fi
PYTHON_VERSION=$(python3 --version 2>&1)
echo -e "   ${GREEN}+ $PYTHON_VERSION${NC}"

# FFmpeg kontrolu
echo "[2/5] FFmpeg kontrol ediliyor..."
if ! command -v ffmpeg &> /dev/null; then
    echo -e "   ${YELLOW}! FFmpeg bulunamadi!${NC}"
    echo ""
    echo "   Ubuntu/Debian: sudo apt install ffmpeg"
    echo "   macOS: brew install ffmpeg"
    echo ""
    exit 1
fi
echo -e "   ${GREEN}+ FFmpeg yuklu${NC}"

# Virtual environment kontrolu
echo "[3/5] Python ortami hazirlaniyor..."
if [ ! -d "venv" ]; then
    echo "   Virtual environment olusturuluyor..."
    python3 -m venv venv
    echo -e "   ${GREEN}+ venv olusturuldu${NC}"
else
    echo -e "   ${GREEN}+ venv mevcut${NC}"
fi

# Virtual environment'i aktive et
source venv/bin/activate

# Bagimliliklari yukle
echo "[4/5] Bagimliliklar kontrol ediliyor..."

# Pip'i guncelle
pip install --upgrade pip -q 2>/dev/null

# requirements.txt varsa onu kullan
if [ -f "requirements.txt" ]; then
    echo "   requirements.txt'den yukluyor..."
    pip install -r requirements.txt -q 2>/dev/null
    echo -e "   ${GREEN}+ Bagimliliklar yuklendi${NC}"
else
    # Manuel kurulum
    python3 -c "import firebase_admin" 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "   firebase-admin yukleniyor..."
        pip install firebase-admin -q
        echo -e "   ${GREEN}+ firebase-admin yuklendi${NC}"
    else
        echo -e "   ${GREEN}+ firebase-admin mevcut${NC}"
    fi

    python3 -c "import rembg" 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "   rembg yukleniyor (bu biraz surebilir)..."
        pip install "rembg[cpu]" -q
        echo -e "   ${GREEN}+ rembg yuklendi${NC}"
    else
        echo -e "   ${GREEN}+ rembg mevcut${NC}"
    fi

    python3 -c "import customtkinter" 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "   customtkinter yukleniyor..."
        pip install customtkinter -q
        echo -e "   ${GREEN}+ customtkinter yuklendi${NC}"
    else
        echo -e "   ${GREEN}+ customtkinter mevcut${NC}"
    fi
fi

# Firebase Admin SDK key kontrolu
echo "[5/5] Firebase yapilandirmasi kontrol ediliyor..."
FIREBASE_KEY=$(ls *firebase-adminsdk*.json 2>/dev/null | head -1)
if [ -z "$FIREBASE_KEY" ]; then
    echo -e "   ${YELLOW}! Firebase Admin SDK anahtari bulunamadi!${NC}"
    echo ""
    echo "   GUI baslatilacak ama Firebase islemleri calismayacak."
    echo "   Anahtar dosyasini bu klasore koyun."
    echo ""
else
    echo -e "   ${GREEN}+ Firebase anahtari bulundu${NC}"
fi

echo ""
echo "============================================================"
if [ "$MODE" == "gui" ]; then
    echo "   STICLY GUI BASLATILIYOR..."
else
    echo "   STICLY CLI BASLATILIYOR..."
fi
echo "============================================================"
echo ""

# Uygulamayi calistir
if [ "$MODE" == "gui" ]; then
    if [ -f "sticly_gui.py" ]; then
        python3 sticly_gui.py
    else
        echo -e "   ${YELLOW}! GUI dosyasi bulunamadi, CLI baslatiliyor...${NC}"
        python3 upload_stickers.py
    fi
else
    python3 upload_stickers.py
fi

echo ""
echo "Islem tamamlandi!"
