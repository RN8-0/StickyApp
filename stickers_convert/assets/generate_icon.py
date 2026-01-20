#!/usr/bin/env python3
"""
Sticly Icon Generator
=====================
WhatsApp temasina uygun uygulama ikonu olusturur.
"""

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    print("PIL yuklu degil. Calistirin: pip install pillow")
    exit(1)

import os
from pathlib import Path


def create_icon():
    """256x256 boyutunda ikon olustur"""
    size = 256
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # WhatsApp yesili
    primary_color = (37, 211, 102)  # #25D366
    secondary_color = (18, 140, 126)  # #128C7E

    # Arka plan - yuvarlak kose dikdortgen
    padding = 20
    corner_radius = 50

    # Gradient benzeri efekt (basit)
    for i in range(size - 2 * padding):
        ratio = i / (size - 2 * padding)
        r = int(primary_color[0] * (1 - ratio * 0.3) + secondary_color[0] * (ratio * 0.3))
        g = int(primary_color[1] * (1 - ratio * 0.3) + secondary_color[1] * (ratio * 0.3))
        b = int(primary_color[2] * (1 - ratio * 0.3) + secondary_color[2] * (ratio * 0.3))
        draw.rectangle(
            [padding, padding + i, size - padding, padding + i + 1],
            fill=(r, g, b, 255)
        )

    # Yuvarlak maske uygula
    mask = Image.new('L', (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle(
        [padding, padding, size - padding, size - padding],
        radius=corner_radius,
        fill=255
    )
    img.putalpha(mask)

    # Sticker simgesi - basit emoji/sticker ikonu
    # Beyaz daire (sticker sembolü)
    sticker_size = 100
    sticker_x = (size - sticker_size) // 2
    sticker_y = (size - sticker_size) // 2 - 15

    # Sticker arka plan (beyaz daire)
    draw.ellipse(
        [sticker_x, sticker_y, sticker_x + sticker_size, sticker_y + sticker_size],
        fill=(255, 255, 255, 230)
    )

    # Sticker icinde basit bir gulus ifadesi
    eye_color = (37, 211, 102)
    eye_size = 12
    eye_y = sticker_y + 35

    # Sol goz
    draw.ellipse(
        [sticker_x + 28, eye_y, sticker_x + 28 + eye_size, eye_y + eye_size],
        fill=eye_color
    )

    # Sag goz
    draw.ellipse(
        [sticker_x + 60, eye_y, sticker_x + 60 + eye_size, eye_y + eye_size],
        fill=eye_color
    )

    # Gulus (yay)
    draw.arc(
        [sticker_x + 30, sticker_y + 40, sticker_x + 70, sticker_y + 75],
        start=0, end=180,
        fill=eye_color,
        width=4
    )

    # "S" harfi (Sticly)
    try:
        # Sistem fontu kullanmaya calis
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 36)
    except:
        font = ImageFont.load_default()

    text = "S"
    text_bbox = draw.textbbox((0, 0), text, font=font)
    text_width = text_bbox[2] - text_bbox[0]
    text_height = text_bbox[3] - text_bbox[1]
    text_x = (size - text_width) // 2
    text_y = size - padding - text_height - 25

    draw.text((text_x, text_y), text, fill=(255, 255, 255, 255), font=font)

    return img


def main():
    """Ikonu olustur ve kaydet"""
    script_dir = Path(__file__).parent

    # PNG ikon
    icon = create_icon()
    png_path = script_dir / "icon.png"
    icon.save(png_path, "PNG")
    print(f"PNG ikon olusturuldu: {png_path}")

    # ICO ikon (Windows icin)
    ico_path = script_dir / "icon.ico"
    # ICO icin farkli boyutlar olustur
    sizes = [(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    icons = []
    for s in sizes:
        resized = icon.resize(s, Image.Resampling.LANCZOS)
        icons.append(resized)

    icons[0].save(ico_path, format='ICO', sizes=[(i.width, i.height) for i in icons])
    print(f"ICO ikon olusturuldu: {ico_path}")


if __name__ == "__main__":
    main()
