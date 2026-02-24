#!/bin/bash
#
# Sticky - Play Store Screenshot Generator
# Tek komutla tum screenshot'lari olusturur
#

echo "=================================================="
echo "  Sticky - Play Store Screenshot Generator"
echo "=================================================="
echo ""

cd "$(dirname "$0")"

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js bulunamadi!"
    echo "   Lutfen Node.js yukleyin: https://nodejs.org"
    exit 1
fi

# Check if puppeteer is installed
if [ ! -d "node_modules/puppeteer" ]; then
    echo "📦 Puppeteer yukleniyor..."
    npm install puppeteer
    echo ""
fi

# Check for command line args
LANG_ARG=""
if [ ! -z "$1" ]; then
    LANG_ARG="--lang=$1"
    echo "🌍 Sadece '$1' dili icin olusturulacak"
else
    echo "🌍 Tum diller icin olusturulacak (17 dil)"
fi

echo ""
echo "📸 Screenshot'lar olusturuluyor..."
echo ""

# Run the generator
node generate-screenshots.js $LANG_ARG

echo ""
echo "=================================================="
echo "  Tamamlandi!"
echo "  Dosyalar: ./output/ klasorunde"
echo "=================================================="
