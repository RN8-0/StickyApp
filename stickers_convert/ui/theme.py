"""
Sticly Manager - Tema Ayarlari
"""

import customtkinter as ctk

class SticlyTheme:
    PRIMARY = "#25D366"
    PRIMARY_HOVER = "#1EBE5D"
    PRIMARY_DARK = "#128C7E"
    SECONDARY = "#075E54"
    ACCENT = "#34B7F1"

    BG_DARK = "#111B21"
    BG_SECONDARY = "#1F2C34"
    BG_TERTIARY = "#2A3942"

    TEXT_DARK = "#E9EDEF"
    TEXT_SECONDARY = "#8696A0"

    SUCCESS = "#25D366"
    WARNING = "#FFB800"
    ERROR = "#EA4335"
    INFO = "#34B7F1"

    PREMIUM_GOLD = "#FFD700"

    FONT_TITLE = ("Segoe UI", 24, "bold")
    FONT_HEADER = ("Segoe UI", 18, "bold")
    FONT_SUBHEADER = ("Segoe UI", 14, "bold")
    FONT_BODY = ("Segoe UI", 12)
    FONT_SMALL = ("Segoe UI", 10)
    FONT_MONO = ("Consolas", 11)

    @classmethod
    def setup(cls):
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("green")

CATEGORY_COLORS = {
    "komik": "#FF6B6B",
    "romantik": "#FF69B4",
    "spor": "#4CAF50",
    "dizi_film": "#9C27B0",
    "hayvanlar": "#FF9800",
    "memeler": "#00BCD4",
    "gunluk": "#607D8B",
    "ozel_gun": "#E91E63",
    "": "#8696A0"
}

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
