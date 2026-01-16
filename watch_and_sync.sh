#!/bin/bash

# =============================================================================
# WATCH AND SYNC - Dosya degisikliklerini izle ve otomatik GitHub'a yukle
# =============================================================================
# Bu script arka planda calisir ve dosya degisikliklerini izler.
# Degisiklik oldugunda otomatik olarak GitHub'a push yapar.
#
# Kullanim: ./watch_and_sync.sh [izlenecek_klasor]
# Ornek:    ./watch_and_sync.sh stickers_convert/stickers
# =============================================================================

# Renk kodlari
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Script'in bulundugu klasore git
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Izlenecek klasor (varsayilan: stickers_convert)
WATCH_DIR="${1:-stickers_convert}"

# Bekleme suresi (saniye) - cok sik commit yapmamak icin
DEBOUNCE_TIME=10

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   StickyApp - Watch & Sync${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${CYAN}Izlenen klasor:${NC} $WATCH_DIR"
echo -e "${CYAN}Bekleme suresi:${NC} ${DEBOUNCE_TIME}s"
echo ""

# inotifywait kontrolu
if ! command -v inotifywait &> /dev/null; then
    echo -e "${YELLOW}inotify-tools kurulu degil. Kuruluyor...${NC}"
    echo ""
    echo "Asagidaki komutu calistirin:"
    echo -e "${GREEN}sudo apt install inotify-tools${NC}"
    echo ""
    echo "Kurulumdan sonra bu scripti tekrar calistirin."
    exit 1
fi

# Klasor kontrolu
if [ ! -d "$WATCH_DIR" ]; then
    echo -e "${RED}HATA: '$WATCH_DIR' klasoru bulunamadi!${NC}"
    exit 1
fi

# Git kontrolu
if [ ! -d ".git" ]; then
    echo -e "${RED}HATA: Git reposu degil!${NC}"
    exit 1
fi

echo -e "${GREEN}Izleme baslatildi...${NC}"
echo -e "${YELLOW}Durdurmak icin: Ctrl+C${NC}"
echo ""
echo "-------------------------------------------"

# Dosya degisikliklerini izle
LAST_SYNC=0

sync_changes() {
    CURRENT_TIME=$(date +%s)
    TIME_DIFF=$((CURRENT_TIME - LAST_SYNC))

    if [ $TIME_DIFF -lt $DEBOUNCE_TIME ]; then
        return
    fi

    # Degisiklik var mi kontrol et
    CHANGES=$(git status --porcelain)
    if [ -z "$CHANGES" ]; then
        return
    fi

    echo ""
    echo -e "${YELLOW}[$(date '+%H:%M:%S')]${NC} Degisiklik algilandi!"

    # Kisa bekle (dosya yazimi tamamlansin)
    sleep 2

    # Auto sync calistir
    TIMESTAMP=$(date +"%Y-%m-%d %H:%M")

    git add -A
    git commit -m "Otomatik sync - $TIMESTAMP"

    if [ $? -eq 0 ]; then
        git push
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} GitHub guncellendi!"
        else
            echo -e "${RED}[$(date '+%H:%M:%S')]${NC} Push basarisiz!"
        fi
    fi

    LAST_SYNC=$(date +%s)
    echo "-------------------------------------------"
}

# SIGINT (Ctrl+C) yakalama
trap 'echo ""; echo -e "${YELLOW}Izleme durduruldu.${NC}"; exit 0' SIGINT

# Surekli izle
while true; do
    # inotifywait ile degisiklikleri bekle
    inotifywait -r -q -e modify,create,delete,move "$WATCH_DIR" --format '%w%f' 2>/dev/null

    # Debounce - birden fazla degisikligi tek commit'te topla
    sleep $DEBOUNCE_TIME

    sync_changes
done
