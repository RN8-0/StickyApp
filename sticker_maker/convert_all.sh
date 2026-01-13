#!/bin/bash

# Tüm MP4 ve GIF dosyalarını WebP'ye dönüştür
# Kullanım: ./convert_all.sh

echo "🚀 Toplu Sticker Dönüştürücü"
echo "============================"
echo ""

# videos klasöründeki tüm dosyaları dönüştür
mkdir -p videos output

if [ -z "$(ls -A videos/ 2>/dev/null)" ]; then
    echo "📁 'videos' klasörüne MP4 veya GIF dosyalarınızı koyun"
    echo "   Örnek: videos/recep1.mp4, videos/recep2.gif"
    echo ""
    echo "Sonra bu scripti tekrar çalıştırın."
    exit 0
fi

COUNT=0
for file in videos/*.{mp4,MP4,gif,GIF,mov,MOV,avi,AVI} 2>/dev/null; do
    if [ -f "$file" ]; then
        BASENAME=$(basename "$file" | sed 's/\.[^.]*$//')
        OUTPUT="output/${BASENAME}.webp"

        echo "📽️  İşleniyor: $file"

        ffmpeg -y -i "$file" -t 5 \
            -vf "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=white,fps=12" \
            -loop 0 \
            -c:v libwebp \
            -lossless 0 \
            -quality 60 \
            -an \
            "$OUTPUT" 2>/dev/null

        if [ $? -eq 0 ]; then
            SIZE=$(du -h "$OUTPUT" | cut -f1)

            # Boyut kontrolü
            SIZE_KB=$(du -k "$OUTPUT" | cut -f1)
            if [ "$SIZE_KB" -gt 500 ]; then
                ffmpeg -y -i "$file" -t 4 \
                    -vf "scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=white,fps=8" \
                    -loop 0 \
                    -c:v libwebp \
                    -lossless 0 \
                    -quality 40 \
                    -an \
                    "$OUTPUT" 2>/dev/null
                SIZE=$(du -h "$OUTPUT" | cut -f1)
            fi

            echo "   ✅ -> $OUTPUT ($SIZE)"
            ((COUNT++))
        else
            echo "   ❌ Hata!"
        fi
        echo ""
    fi
done

echo "============================"
echo "✨ Toplam $COUNT sticker oluşturuldu!"
echo "📁 Çıktılar: $(pwd)/output/"
