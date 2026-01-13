#!/bin/bash

# WhatsApp Sticker Dönüştürücü
# Kullanım: ./convert.sh video.mp4 output.webp

INPUT=$1
OUTPUT=${2:-"sticker_$(date +%s).webp"}

if [ -z "$INPUT" ]; then
    echo "Kullanım: ./convert.sh <video.mp4> [output.webp]"
    echo ""
    echo "Örnek:"
    echo "  ./convert.sh recep.mp4"
    echo "  ./convert.sh recep.mp4 recep_sticker.webp"
    exit 1
fi

if [ ! -f "$INPUT" ]; then
    echo "Hata: $INPUT dosyası bulunamadı!"
    exit 1
fi

echo "🎬 Dönüştürülüyor: $INPUT -> $OUTPUT"
echo ""

# Videonun süresini al
DURATION=$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$INPUT" 2>/dev/null)

if (( $(echo "$DURATION > 10" | bc -l) )); then
    echo "⚠️  Video 10 saniyeden uzun. İlk 5 saniye alınacak."
    TIME_FILTER="-t 5"
else
    TIME_FILTER=""
fi

# Dönüştür
ffmpeg -y -i "$INPUT" $TIME_FILTER \
    -vf "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=white,fps=12" \
    -loop 0 \
    -c:v libwebp \
    -lossless 0 \
    -quality 60 \
    -an \
    "$OUTPUT" 2>/dev/null

if [ $? -eq 0 ]; then
    SIZE=$(du -h "$OUTPUT" | cut -f1)
    echo "✅ Başarılı! Oluşturulan: $OUTPUT ($SIZE)"

    # Boyut kontrolü
    SIZE_KB=$(du -k "$OUTPUT" | cut -f1)
    if [ "$SIZE_KB" -gt 500 ]; then
        echo "⚠️  Dosya 500KB'dan büyük. Kalite düşürülüyor..."
        ffmpeg -y -i "$INPUT" $TIME_FILTER \
            -vf "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=white,fps=8" \
            -loop 0 \
            -c:v libwebp \
            -lossless 0 \
            -quality 40 \
            -an \
            "$OUTPUT" 2>/dev/null
        SIZE=$(du -h "$OUTPUT" | cut -f1)
        echo "✅ Yeniden oluşturuldu: $OUTPUT ($SIZE)"
    fi
else
    echo "❌ Hata oluştu!"
    exit 1
fi
