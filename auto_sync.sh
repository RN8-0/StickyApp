#!/bin/bash

# =============================================================================
# AUTO SYNC SCRIPT - Dosya degisikliklerini GitHub'a otomatik yukler
# =============================================================================
# Kullanim: ./auto_sync.sh [commit mesaji]
# Ornek:    ./auto_sync.sh "Yeni sticker paketi eklendi"
# =============================================================================

# Renk kodlari
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # Renk sifirla

# Script'in bulundugu klasore git
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   StickyApp - GitHub Auto Sync${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Git repo kontrolu
if [ ! -d ".git" ]; then
    echo -e "${RED}HATA: Bu klasor bir git reposu degil!${NC}"
    echo "Lutfen once 'git init' calistirin."
    exit 1
fi

# Remote kontrolu
REMOTE_URL=$(git remote get-url origin 2>/dev/null)
if [ -z "$REMOTE_URL" ]; then
    echo -e "${RED}HATA: Git remote (origin) ayarlanmamis!${NC}"
    echo "Lutfen once remote ekleyin:"
    echo "  git remote add origin https://github.com/KULLANICI/REPO.git"
    exit 1
fi

echo -e "${GREEN}Remote:${NC} $REMOTE_URL"
echo ""

# Degisiklikleri kontrol et
echo -e "${YELLOW}Degisiklikler kontrol ediliyor...${NC}"
git status --short

CHANGES=$(git status --porcelain)
if [ -z "$CHANGES" ]; then
    echo ""
    echo -e "${GREEN}Degisiklik yok! Her sey guncel.${NC}"
    exit 0
fi

# Degisiklik sayisini goster
ADDED=$(echo "$CHANGES" | grep -c "^??" || true)
MODIFIED=$(echo "$CHANGES" | grep -c "^ M\|^M " || true)
DELETED=$(echo "$CHANGES" | grep -c "^ D\|^D " || true)

echo ""
echo -e "${BLUE}Degisiklik Ozeti:${NC}"
echo -e "  Yeni dosya:    ${GREEN}$ADDED${NC}"
echo -e "  Degistirilmis: ${YELLOW}$MODIFIED${NC}"
echo -e "  Silinen:       ${RED}$DELETED${NC}"
echo ""

# Commit mesaji
if [ -n "$1" ]; then
    COMMIT_MSG="$1"
else
    # Otomatik commit mesaji olustur
    TIMESTAMP=$(date +"%Y-%m-%d %H:%M")
    if [ "$ADDED" -gt 0 ] && [ "$MODIFIED" -eq 0 ]; then
        COMMIT_MSG="Yeni sticker eklendi - $TIMESTAMP"
    elif [ "$MODIFIED" -gt 0 ] && [ "$ADDED" -eq 0 ]; then
        COMMIT_MSG="Sticker guncellendi - $TIMESTAMP"
    else
        COMMIT_MSG="Sticker paketi guncellendi - $TIMESTAMP"
    fi
fi

echo -e "${YELLOW}Commit mesaji:${NC} $COMMIT_MSG"
echo ""

# Tum degisiklikleri ekle
echo -e "${YELLOW}Dosyalar ekleniyor...${NC}"
git add -A

# Commit yap
echo -e "${YELLOW}Commit yapiliyor...${NC}"
git commit -m "$COMMIT_MSG"

if [ $? -ne 0 ]; then
    echo -e "${RED}HATA: Commit basarisiz!${NC}"
    exit 1
fi

# Push yap
echo ""
echo -e "${YELLOW}GitHub'a yukleniyor...${NC}"
git push

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}   BASARILI! GitHub guncellendi.${NC}"
    echo -e "${GREEN}========================================${NC}"
else
    echo ""
    echo -e "${RED}HATA: Push basarisiz!${NC}"
    echo "Muhtemel sebepler:"
    echo "  1. Internet baglantisi yok"
    echo "  2. GitHub kimlik dogrulamasi gerekli"
    echo "  3. Remote branch'te degisiklik var (git pull gerekebilir)"
    exit 1
fi
