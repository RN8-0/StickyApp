"""
Sticly Manager - Tema Ayarlari
==============================
WhatsApp yesili (#25D366) bazli modern tema.
"""

import customtkinter as ctk


class SticlyTheme:
    """Sticly tema renkleri ve stilleri"""

    # Ana Renkler (WhatsApp temasli)
    PRIMARY = "#25D366"         # WhatsApp yesili
    PRIMARY_HOVER = "#1EBE5D"   # Hover durumu
    PRIMARY_DARK = "#128C7E"    # Koyu yesil

    SECONDARY = "#075E54"       # Koyu teal
    ACCENT = "#34B7F1"          # Acik mavi

    # Arka Plan Renkleri
    BG_DARK = "#111B21"         # Koyu mod arka plan
    BG_DARK_SECONDARY = "#1F2C34"
    BG_DARK_TERTIARY = "#2A3942"

    BG_LIGHT = "#FFFFFF"        # Acik mod arka plan
    BG_LIGHT_SECONDARY = "#F0F2F5"
    BG_LIGHT_TERTIARY = "#E9EDEF"

    # Metin Renkleri
    TEXT_DARK = "#E9EDEF"       # Koyu modda metin
    TEXT_DARK_SECONDARY = "#8696A0"

    TEXT_LIGHT = "#111B21"      # Acik modda metin
    TEXT_LIGHT_SECONDARY = "#667781"

    # Durum Renkleri
    SUCCESS = "#25D366"
    WARNING = "#FFB800"
    ERROR = "#EA4335"
    INFO = "#34B7F1"

    # Premium Renkleri
    PREMIUM_GOLD = "#FFD700"
    PREMIUM_GRADIENT_START = "#FFD700"
    PREMIUM_GRADIENT_END = "#FFA500"

    # Buton Stilleri
    BUTTON_RADIUS = 8
    CARD_RADIUS = 12

    # Font Boyutlari
    FONT_TITLE = ("Segoe UI", 24, "bold")
    FONT_HEADER = ("Segoe UI", 18, "bold")
    FONT_SUBHEADER = ("Segoe UI", 14, "bold")
    FONT_BODY = ("Segoe UI", 12)
    FONT_SMALL = ("Segoe UI", 10)
    FONT_MONO = ("Consolas", 11)

    @classmethod
    def setup(cls, appearance: str = "dark"):
        """CustomTkinter temasini ayarla"""
        ctk.set_appearance_mode(appearance)
        ctk.set_default_color_theme("green")

    @classmethod
    def get_colors(cls, is_dark: bool = True):
        """Mevcut temaya gore renkleri dondur"""
        if is_dark:
            return {
                "bg": cls.BG_DARK,
                "bg_secondary": cls.BG_DARK_SECONDARY,
                "bg_tertiary": cls.BG_DARK_TERTIARY,
                "text": cls.TEXT_DARK,
                "text_secondary": cls.TEXT_DARK_SECONDARY,
                "primary": cls.PRIMARY,
                "primary_hover": cls.PRIMARY_HOVER,
                "secondary": cls.SECONDARY,
                "accent": cls.ACCENT,
                "success": cls.SUCCESS,
                "warning": cls.WARNING,
                "error": cls.ERROR,
                "info": cls.INFO,
            }
        else:
            return {
                "bg": cls.BG_LIGHT,
                "bg_secondary": cls.BG_LIGHT_SECONDARY,
                "bg_tertiary": cls.BG_LIGHT_TERTIARY,
                "text": cls.TEXT_LIGHT,
                "text_secondary": cls.TEXT_LIGHT_SECONDARY,
                "primary": cls.PRIMARY,
                "primary_hover": cls.PRIMARY_HOVER,
                "secondary": cls.SECONDARY,
                "accent": cls.ACCENT,
                "success": cls.SUCCESS,
                "warning": cls.WARNING,
                "error": cls.ERROR,
                "info": cls.INFO,
            }

    @classmethod
    def create_button_style(cls, style: str = "primary"):
        """Buton stili olustur"""
        styles = {
            "primary": {
                "fg_color": cls.PRIMARY,
                "hover_color": cls.PRIMARY_HOVER,
                "text_color": "#FFFFFF",
                "corner_radius": cls.BUTTON_RADIUS
            },
            "secondary": {
                "fg_color": cls.SECONDARY,
                "hover_color": cls.PRIMARY_DARK,
                "text_color": "#FFFFFF",
                "corner_radius": cls.BUTTON_RADIUS
            },
            "outline": {
                "fg_color": "transparent",
                "hover_color": cls.BG_DARK_TERTIARY,
                "text_color": cls.PRIMARY,
                "border_width": 2,
                "border_color": cls.PRIMARY,
                "corner_radius": cls.BUTTON_RADIUS
            },
            "danger": {
                "fg_color": cls.ERROR,
                "hover_color": "#CC3333",
                "text_color": "#FFFFFF",
                "corner_radius": cls.BUTTON_RADIUS
            },
            "warning": {
                "fg_color": cls.WARNING,
                "hover_color": "#E6A600",
                "text_color": "#000000",
                "corner_radius": cls.BUTTON_RADIUS
            }
        }
        return styles.get(style, styles["primary"])

    @classmethod
    def create_card_style(cls, is_dark: bool = True):
        """Kart stili olustur"""
        colors = cls.get_colors(is_dark)
        return {
            "fg_color": colors["bg_secondary"],
            "corner_radius": cls.CARD_RADIUS,
        }

    @classmethod
    def get_status_color(cls, status: str) -> str:
        """Durum rengini dondur"""
        colors = {
            "success": cls.SUCCESS,
            "warning": cls.WARNING,
            "error": cls.ERROR,
            "info": cls.INFO,
            "pending": cls.TEXT_DARK_SECONDARY,
            "processing": cls.ACCENT
        }
        return colors.get(status.lower(), cls.TEXT_DARK_SECONDARY)


# Kategori Renkleri
CATEGORY_COLORS = {
    "komik": "#FF6B6B",
    "romantik": "#FF69B4",
    "spor": "#4CAF50",
    "dizi_film": "#9C27B0",
    "hayvanlar": "#FF9800",
    "memeler": "#00BCD4",
    "gunluk": "#607D8B",
    "ozel_gun": "#E91E63",
    "": "#8696A0"  # Kategorisiz
}

# Kategori Isimleri
CATEGORY_NAMES = {
    "": "Kategorisiz",
    "komik": "Komik",
    "romantik": "Romantik",
    "spor": "Spor",
    "dizi_film": "Dizi/Film",
    "hayvanlar": "Hayvanlar",
    "memeler": "Memeler",
    "gunluk": "Gunluk",
    "ozel_gun": "Ozel Gun"
}
