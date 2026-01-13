#!/bin/bash

# SÜPER HIZLI STİCKER DÖNÜŞTÜRÜCÜ
# Kullanım: Videoları "videos" klasörüne at, scripti çalıştır, bitti!

cd "$(dirname "$0")"
mkdir -p videos output

# Paralel işlem sayısı (CPU çekirdek sayısı)
JOBS=$(nproc)

echo "⚡ Hızlı Sticker Dönüştürücü"
echo "📁 Klasör: $(pwd)/videos/"
echo "🔧 Paralel işlem: $JOBS"
echo ""

# Dosya sayısını kontrol et
COUNT=$(find videos -type f \( -iname "*.mp4" -o -iname "*.gif" -o -iname "*.mov" -o -iname "*.avi" -o -iname "*.mkv" -o -iname "*.webm" \) 2>/dev/null | wc -l)

if [ "$COUNT" -eq 0 ]; then
    echo "❌ 'videos' klasöründe video bulunamadı!"
    echo ""
    echo "📌 Şu formatlar desteklenir: mp4, gif, mov, avi, mkv, webm"
    echo "📌 Videoları buraya koyun: $(pwd)/videos/"
    exit 1
fi

echo "📊 $COUNT video bulundu. Dönüştürülüyor..."
echo ""

# Dönüştürme fonksiyonu
convert_video() {
    file="$1"
    BASENAME=$(basename "$file" | sed 's/\.[^.]*$//')
    OUTPUT="output/${BASENAME}.webp"

    ffmpeg -y -i "$file" -t 3 \
        -vf "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=white,fps=10" \
        -loop 0 -c:v libwebp -quality 50 -an \
        "$OUTPUT" 2>/dev/null

    if [ $? -eq 0 ]; then
        # Boyut kontrolü - 500KB üstüyse tekrar dene
        SIZE_KB=$(du -k "$OUTPUT" | cut -f1)
        if [ "$SIZE_KB" -gt 500 ]; then
            ffmpeg -y -i "$file" -t 2 \
                -vf "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=white,fps=8" \
                -loop 0 -c:v libwebp -quality 35 -an \
                "$OUTPUT" 2>/dev/null
        fi
        echo "✅ $BASENAME"
    else
        echo "❌ $BASENAME (hata)"
    fi
}

export -f convert_video

# Paralel dönüşüm (çok hızlı!)
find videos -type f \( -iname "*.mp4" -o -iname "*.gif" -o -iname "*.mov" -o -iname "*.avi" -o -iname "*.mkv" -o -iname "*.webm" \) -print0 | \
    xargs -0 -P "$JOBS" -I {} bash -c 'convert_video "$@"' _ {}

echo ""
echo "✨ Tamamlandı!"
echo "📁 Çıktılar: $(pwd)/output/"
echo ""
ls -lh output/ | head -20
