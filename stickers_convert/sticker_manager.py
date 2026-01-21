#!/usr/bin/env python3
"""
Sticly Sticker Manager v6.0 - Professional Toolkit
===================================================
WhatsApp sticker paketleri icin profesyonel yonetim araci.

Ozellikler:
- Otomatik bagimlillik kontrolu ve kurulum
- Gerekli dosyalarin kontrolu (Firebase, credentials vs.)
- Dashboard ile istatistik goruntuleme
- Terminal konsol - tum islem loglari
- Paket yonetimi (ekleme, silme, kapak guncelleme)
- Tekli sticker islemleri (ekleme, silme)
- Senkronizasyon merkezi (Firebase, Drive, GitHub)
- Isletim sistemi bagimsiz calisma
"""

import os
import sys
import subprocess
import shutil
import platform
from pathlib import Path
from datetime import datetime

# ============================================================================
# OTOMATIK KURULUM VE DEPENDENCY KONTROLU
# ============================================================================

SCRIPT_DIR = Path(__file__).parent
VENV_DIR = SCRIPT_DIR / "venv"

def get_python_paths():
    """Platform bazli python yollarini dondur"""
    if sys.platform == "win32":
        return (
            VENV_DIR / "Scripts" / "python.exe",
            VENV_DIR / "Scripts" / "pip.exe"
        )
    return (
        VENV_DIR / "bin" / "python",
        VENV_DIR / "bin" / "pip"
    )

def is_in_venv():
    """Venv icinde mi kontrol et"""
    return hasattr(sys, 'real_prefix') or (
        hasattr(sys, 'base_prefix') and sys.base_prefix != sys.prefix
    )

def setup_environment():
    """Virtual environment ve gerekli paketleri kontrol et/kur"""
    python_path, pip_path = get_python_paths()

    if is_in_venv():
        return True

    print("\n" + "=" * 60)
    print("  STICLY STICKER MANAGER - Otomatik Kurulum")
    print("=" * 60)

    # Venv yoksa olustur
    if not VENV_DIR.exists():
        print(f"\n [*] Virtual environment olusturuluyor...")
        try:
            subprocess.run([sys.executable, "-m", "venv", str(VENV_DIR)], check=True)
            print(" [OK] Virtual environment olusturuldu")
        except subprocess.CalledProcessError as e:
            print(f" [HATA] Venv olusturulamadi: {e}")
            return False

    # Gerekli paketler
    required_packages = [
        "customtkinter>=5.2.0",
        "firebase-admin>=6.2.0",
        "google-api-python-client>=2.100.0",
        "google-auth-httplib2>=0.1.1",
        "google-auth-oauthlib>=1.1.0",
        "rembg>=2.0.50",
        "pillow>=10.0.0",
        "requests>=2.31.0"
    ]

    print("\n [*] Bagimliliklar kontrol ediliyor...")

    # Pip upgrade
    try:
        subprocess.run([str(pip_path), "install", "--upgrade", "pip"],
                      capture_output=True, check=True)
    except:
        pass

    # requirements.txt varsa kullan
    req_file = SCRIPT_DIR / "requirements.txt"
    if req_file.exists():
        print(" [*] requirements.txt'den yukleniyor...")
        try:
            subprocess.run([str(pip_path), "install", "-r", str(req_file)],
                          capture_output=True, check=True)
        except:
            pass
    else:
        # Manuel kurulum
        for pkg in required_packages:
            pkg_name = pkg.split(">=")[0].split("[")[0]
            print(f" [*] Kontrol: {pkg_name}...", end="\r")
            try:
                subprocess.run([str(pip_path), "install", pkg],
                              capture_output=True, check=True)
            except subprocess.CalledProcessError:
                print(f"\n [UYARI] {pkg_name} yuklenemedi")

    print("\n [OK] Tum bagimliliklar hazir")

    # Scripti venv python'u ile yeniden baslat
    print("\n [*] Uygulama baslatiliyor...\n")
    if sys.platform == "win32":
        subprocess.run([str(python_path), __file__] + sys.argv[1:])
        sys.exit(0)
    else:
        os.execv(str(python_path), [str(python_path), __file__] + sys.argv[1:])

# Kurulum kontrolu - sadece main modul olarak calistiginda
if __name__ == "__main__":
    if not is_in_venv():
        python_path, _ = get_python_paths()
        if python_path.exists():
            if sys.platform == "win32":
                subprocess.run([str(python_path), __file__] + sys.argv[1:])
                sys.exit(0)
            else:
                os.execv(str(python_path), [str(python_path), __file__] + sys.argv[1:])
        else:
            setup_environment()

# ============================================================================
# GUI UYGULAMASI - IMPORTLAR
# ============================================================================

import customtkinter as ctk
from tkinter import filedialog, messagebox
import threading
import logging
import json
import hashlib
from PIL import Image
import io

# Core imports
sys.path.insert(0, str(SCRIPT_DIR / 'core'))

# Loglama
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("SticlyManager")

# ============================================================================
# TEMA VE RENKLER
# ============================================================================

ctk.set_appearance_mode("Dark")
ctk.set_default_color_theme("green")

COLORS = {
    "bg_dark": "#0B141A",
    "bg_card": "#1F2C34",
    "bg_hover": "#2A3942",
    "bg_input": "#2A3942",
    "primary": "#00A884",
    "primary_hover": "#008F6F",
    "secondary": "#075E54",
    "accent": "#34B7F1",
    "text_main": "#E9EDEF",
    "text_sec": "#8696A0",
    "danger": "#F15C6D",
    "warning": "#FFD279",
    "success": "#00A884",
    "gold": "#FFD700",
    "terminal_bg": "#0D1117",
    "terminal_text": "#58A6FF"
}

# ============================================================================
# YAPILANDIRMA SABITLERI
# ============================================================================

STICKERS_DIR = SCRIPT_DIR / "stickers"
PREMIUM_STICKERS_DIR = SCRIPT_DIR / "premium_stickers"
OUTPUT_DIR = SCRIPT_DIR / "output"
CACHE_FILE = SCRIPT_DIR / "cache.json"
CREDENTIALS_FILE = SCRIPT_DIR / "credentials.json"
TOKEN_FILE = SCRIPT_DIR / "token.pickle"

VIDEO_EXTENSIONS = {'.mp4', '.mov', '.avi', '.mkv', '.webm', '.mpeg', '.mpg', '.m4v'}
IMAGE_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}
GIF_EXTENSION = '.gif'
ALL_EXTENSIONS = VIDEO_EXTENSIONS | IMAGE_EXTENSIONS | {GIF_EXTENSION}

CATEGORIES = {
    "": "Kategorisiz", "komik": "Komik", "romantik": "Romantik",
    "spor": "Spor", "dizi_film": "Dizi/Film", "hayvanlar": "Hayvanlar",
    "memeler": "Memeler", "gunluk": "Gunluk", "ozel_gun": "Ozel Gun"
}

# ============================================================================
# YARDIMCI FONKSIYONLAR
# ============================================================================

def load_cache() -> dict:
    """Cache dosyasini yukle"""
    if CACHE_FILE.exists():
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except:
            pass
    return {"converted": {}, "uploaded": {}, "tray": {}}

def save_cache(cache: dict):
    """Cache dosyasini kaydet"""
    with open(CACHE_FILE, "w", encoding="utf-8") as f:
        json.dump(cache, f, indent=2, ensure_ascii=False)

def get_file_hash(file_path: Path) -> str:
    """Dosyanin MD5 hash'ini al"""
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def get_pack_id(name: str) -> str:
    """Paket ID'si olustur"""
    return name.lower().replace(" ", "_").replace("-", "_")

def get_sticker_files(pack_dir: Path) -> list:
    """Paketteki sticker dosyalarini bul"""
    files = []
    if pack_dir.exists():
        for f in pack_dir.iterdir():
            if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS:
                if f.stem.lower() != 'tray':
                    files.append(f)
    return sorted(files, key=lambda x: x.name.lower())

def find_tray_source(pack_dir: Path) -> Path:
    """Tray kaynagini bul"""
    for f in pack_dir.iterdir():
        if f.is_file() and f.stem.lower() == 'tray':
            if f.suffix.lower() in (IMAGE_EXTENSIONS | {GIF_EXTENSION}):
                return f
    # Tray yoksa ilk sticker'i kullan
    files = get_sticker_files(pack_dir)
    return files[0] if files else None

def check_ffmpeg() -> bool:
    """FFmpeg kurulu mu kontrol et"""
    try:
        subprocess.run(["ffmpeg", "-version"], capture_output=True, check=True)
        return True
    except:
        return False

def check_git() -> bool:
    """Git kurulu mu kontrol et"""
    try:
        subprocess.run(["git", "--version"], capture_output=True, check=True)
        return True
    except:
        return False

def find_firebase_key() -> Path:
    """Firebase Admin SDK anahtarini bul"""
    for f in SCRIPT_DIR.glob("*.json"):
        if "firebase-adminsdk" in f.name:
            return f
    return None

# ============================================================================
# ANA UYGULAMA SINIFI
# ============================================================================

class SticlyToolkit(ctk.CTk):
    """Sticly Sticker Manager - Professional Toolkit"""

    def __init__(self):
        super().__init__()

        self.title("Sticly Sticker Manager v6.0")
        self.geometry("1400x900")
        self.minsize(1200, 800)
        self.configure(fg_color=COLORS["bg_dark"])

        # State
        self.firebase = None
        self.drive = None
        self.current_page = "dashboard"
        self.services_ready = False
        self.dependency_status = {}
        self.stats_data = {}
        self.packs_data = []
        self.selected_pack = None

        # İşlem kontrolü
        self.cancel_operation = False
        self.current_thread = None
        self.operation_running = False

        # UI Kurulum
        self._setup_grid()
        self._create_sidebar()
        self._create_main_area()
        self._create_terminal()
        self._create_statusbar()

        # Baslangic kontrolleri
        self.after(100, self._startup_checks)

    # ========================================================================
    # UI KURULUM
    # ========================================================================

    def _setup_grid(self):
        """Grid yapisini ayarla"""
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

    def _create_sidebar(self):
        """Sol menu"""
        self.sidebar = ctk.CTkFrame(self, width=220, corner_radius=0,
                                    fg_color=COLORS["bg_card"])
        self.sidebar.grid(row=0, column=0, rowspan=2, sticky="nsew")
        self.sidebar.grid_propagate(False)

        # Logo
        logo_frame = ctk.CTkFrame(self.sidebar, fg_color="transparent")
        logo_frame.pack(fill="x", padx=15, pady=(20, 5))

        ctk.CTkLabel(logo_frame, text="Sticly",
                    font=ctk.CTkFont(size=28, weight="bold"),
                    text_color=COLORS["primary"]).pack(anchor="w")
        ctk.CTkLabel(logo_frame, text="Sticker Manager v6.0",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text_sec"]).pack(anchor="w")

        ctk.CTkFrame(self.sidebar, height=1,
                    fg_color=COLORS["bg_hover"]).pack(fill="x", padx=15, pady=15)

        # Navigation
        self.nav_btns = {}
        nav_items = [
            ("dashboard", "Dashboard", "Ana istatistikler"),
            ("packs", "Paketler", "Paket yonetimi"),
            ("sync", "Senkronizasyon", "Firebase, Drive, Git"),
            ("stats", "Istatistikler", "Detayli analizler"),
            ("settings", "Ayarlar", "Yapilandirma"),
        ]

        for key, text, desc in nav_items:
            btn_frame = ctk.CTkFrame(self.sidebar, fg_color="transparent")
            btn_frame.pack(fill="x", padx=10, pady=2)

            btn = ctk.CTkButton(
                btn_frame, text=text, anchor="w",
                font=ctk.CTkFont(size=13),
                height=40, corner_radius=8,
                fg_color="transparent",
                text_color=COLORS["text_main"],
                hover_color=COLORS["bg_hover"],
                command=lambda k=key: self._show_page(k)
            )
            btn.pack(fill="x")
            self.nav_btns[key] = btn

        # Spacer
        ctk.CTkFrame(self.sidebar, fg_color="transparent").pack(fill="both", expand=True)

        # Servis durumu
        status_frame = ctk.CTkFrame(self.sidebar, fg_color=COLORS["bg_hover"],
                                    corner_radius=8)
        status_frame.pack(fill="x", padx=10, pady=10)

        ctk.CTkLabel(status_frame, text="Servis Durumu",
                    font=ctk.CTkFont(size=11, weight="bold"),
                    text_color=COLORS["text_sec"]).pack(anchor="w", padx=10, pady=(10,5))

        self.lbl_firebase = ctk.CTkLabel(status_frame, text="Firebase: Kontrol ediliyor...",
                                         font=ctk.CTkFont(size=10),
                                         text_color=COLORS["text_sec"])
        self.lbl_firebase.pack(anchor="w", padx=10)

        self.lbl_drive = ctk.CTkLabel(status_frame, text="Drive: Kontrol ediliyor...",
                                      font=ctk.CTkFont(size=10),
                                      text_color=COLORS["text_sec"])
        self.lbl_drive.pack(anchor="w", padx=10)

        self.lbl_ffmpeg = ctk.CTkLabel(status_frame, text="FFmpeg: Kontrol ediliyor...",
                                       font=ctk.CTkFont(size=10),
                                       text_color=COLORS["text_sec"])
        self.lbl_ffmpeg.pack(anchor="w", padx=10, pady=(0,10))

    def _create_main_area(self):
        """Ana icerik alani"""
        self.main_container = ctk.CTkFrame(self, fg_color=COLORS["bg_dark"],
                                           corner_radius=0)
        self.main_container.grid(row=0, column=1, sticky="nsew")
        self.main_container.grid_columnconfigure(0, weight=1)
        self.main_container.grid_rowconfigure(0, weight=1)

        # Sayfalar
        self.pages = {}
        self._create_dashboard_page()
        self._create_packs_page()
        self._create_sync_page()
        self._create_stats_page()
        self._create_settings_page()

        # Varsayilan sayfa
        self._show_page("dashboard")

    def _create_terminal(self):
        """Terminal konsol"""
        self.terminal_frame = ctk.CTkFrame(self, height=200, corner_radius=0,
                                           fg_color=COLORS["terminal_bg"])
        self.terminal_frame.grid(row=1, column=1, sticky="ew")
        self.terminal_frame.grid_propagate(False)

        # Terminal header
        header = ctk.CTkFrame(self.terminal_frame, height=30,
                             fg_color=COLORS["bg_card"])
        header.pack(fill="x")
        header.pack_propagate(False)

        ctk.CTkLabel(header, text=" Terminal",
                    font=ctk.CTkFont(size=11, weight="bold"),
                    text_color=COLORS["text_sec"]).pack(side="left", padx=10, pady=5)

        ctk.CTkButton(header, text="Temizle", width=60, height=22,
                     font=ctk.CTkFont(size=10),
                     fg_color=COLORS["bg_hover"],
                     command=self._clear_terminal).pack(side="right", padx=5, pady=4)

        # Durdur butonu
        self.stop_button = ctk.CTkButton(header, text="Durdur", width=70, height=22,
                     font=ctk.CTkFont(size=10, weight="bold"),
                     fg_color=COLORS["danger"],
                     hover_color="#D64550",
                     command=self._stop_operation,
                     state="disabled")
        self.stop_button.pack(side="right", padx=5, pady=4)

        # Terminal text
        self.terminal = ctk.CTkTextbox(
            self.terminal_frame,
            font=ctk.CTkFont(family="Consolas", size=11),
            fg_color=COLORS["terminal_bg"],
            text_color=COLORS["terminal_text"],
            wrap="word"
        )
        self.terminal.pack(fill="both", expand=True, padx=5, pady=5)

        # Baslangic mesaji
        self._log("Sticly Sticker Manager v6.0 baslatildi", "INFO")
        self._log(f"Calisma dizini: {SCRIPT_DIR}", "INFO")

    def _create_statusbar(self):
        """Alt durum cubugu"""
        self.statusbar = ctk.CTkFrame(self, height=25, corner_radius=0,
                                      fg_color=COLORS["bg_card"])
        self.statusbar.grid(row=2, column=0, columnspan=2, sticky="ew")

        self.status_label = ctk.CTkLabel(self.statusbar, text="Hazir",
                                         font=ctk.CTkFont(size=10),
                                         text_color=COLORS["text_sec"])
        self.status_label.pack(side="left", padx=10)

        # Platform bilgisi
        platform_info = f"{platform.system()} {platform.release()}"
        ctk.CTkLabel(self.statusbar, text=platform_info,
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["text_sec"]).pack(side="right", padx=10)

    # ========================================================================
    # SAYFA OLUSTURMA
    # ========================================================================

    def _create_dashboard_page(self):
        """Dashboard sayfasi"""
        page = ctk.CTkScrollableFrame(self.main_container, fg_color="transparent")
        self.pages["dashboard"] = page

        # Header
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.pack(fill="x", padx=20, pady=(20,10))

        ctk.CTkLabel(header, text="Dashboard",
                    font=ctk.CTkFont(size=24, weight="bold"),
                    text_color=COLORS["text_main"]).pack(side="left")

        ctk.CTkButton(header, text="Yenile", width=80, height=32,
                     fg_color=COLORS["primary"],
                     command=self._refresh_dashboard).pack(side="right")

        # Stat kartlari
        stats_frame = ctk.CTkFrame(page, fg_color="transparent")
        stats_frame.pack(fill="x", padx=20, pady=10)

        for i in range(5):
            stats_frame.grid_columnconfigure(i, weight=1)

        self.stat_cards = {}
        card_configs = [
            ("packs", "Toplam Paket", "0", COLORS["primary"]),
            ("stickers", "Toplam Sticker", "0", COLORS["accent"]),
            ("downloads", "Indirme", "0", COLORS["success"]),
            ("views", "Goruntulenme", "0", COLORS["warning"]),
            ("favorites", "Favori", "0", COLORS["gold"]),
        ]

        for i, (key, title, value, color) in enumerate(card_configs):
            card = self._create_stat_card(stats_frame, title, value, color)
            card.grid(row=0, column=i, padx=5, pady=5, sticky="nsew")
            self.stat_cards[key] = card

        # Icerik alani
        content = ctk.CTkFrame(page, fg_color="transparent")
        content.pack(fill="both", expand=True, padx=20, pady=10)
        content.grid_columnconfigure(0, weight=1)
        content.grid_columnconfigure(1, weight=1)
        content.grid_rowconfigure(0, weight=1)

        # Hizli islemler
        actions_frame = ctk.CTkFrame(content, fg_color=COLORS["bg_card"],
                                     corner_radius=12)
        actions_frame.grid(row=0, column=0, sticky="nsew", padx=(0,10), pady=5)

        ctk.CTkLabel(actions_frame, text="Hizli Islemler",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        quick_actions = [
            ("Tam Senkronizasyon", self._full_sync, COLORS["primary"]),
            ("Firebase Sync", self._sync_firebase, COLORS["accent"]),
            ("Drive Yedekle", self._backup_drive, COLORS["success"]),
            ("GitHub Push", self._github_push, COLORS["secondary"]),
            ("Tum Trayleri Guncelle", self._update_all_trays, COLORS["warning"]),
        ]

        for text, cmd, color in quick_actions:
            ctk.CTkButton(actions_frame, text=text, height=45,
                         font=ctk.CTkFont(size=12),
                         fg_color=color, hover_color=COLORS["primary_hover"],
                         command=cmd).pack(fill="x", padx=15, pady=5)

        ctk.CTkFrame(actions_frame, height=15, fg_color="transparent").pack()

        # En populer paketler
        top_frame = ctk.CTkFrame(content, fg_color=COLORS["bg_card"],
                                 corner_radius=12)
        top_frame.grid(row=0, column=1, sticky="nsew", padx=(10,0), pady=5)

        ctk.CTkLabel(top_frame, text="En Populer Paketler",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        self.top_packs_list = ctk.CTkScrollableFrame(top_frame, fg_color="transparent")
        self.top_packs_list.pack(fill="both", expand=True, padx=10, pady=(0,10))

        ctk.CTkLabel(self.top_packs_list, text="Yukleniyor...",
                    text_color=COLORS["text_sec"]).pack(pady=20)

    def _create_packs_page(self):
        """Paket yonetimi sayfasi"""
        page = ctk.CTkFrame(self.main_container, fg_color="transparent")
        self.pages["packs"] = page
        page.grid_columnconfigure(0, weight=1)
        page.grid_rowconfigure(1, weight=1)

        # Header
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", padx=20, pady=(20,10))

        ctk.CTkLabel(header, text="Paket Yonetimi",
                    font=ctk.CTkFont(size=24, weight="bold"),
                    text_color=COLORS["text_main"]).pack(side="left")

        # Kontroller
        controls = ctk.CTkFrame(header, fg_color="transparent")
        controls.pack(side="right")

        self.pack_search = ctk.CTkEntry(controls, placeholder_text="Ara...",
                                        width=150, height=32)
        self.pack_search.pack(side="left", padx=5)
        self.pack_search.bind("<KeyRelease>", lambda e: self._filter_packs())

        self.pack_filter = ctk.CTkOptionMenu(controls,
                                             values=["Tumu", "Normal", "Premium"],
                                             width=100, height=32,
                                             fg_color=COLORS["bg_card"],
                                             command=lambda v: self._filter_packs())
        self.pack_filter.pack(side="left", padx=5)

        ctk.CTkButton(controls, text="+ Yeni Paket", width=110, height=32,
                     fg_color=COLORS["primary"],
                     command=self._add_new_pack).pack(side="left", padx=5)

        ctk.CTkButton(controls, text="Yenile", width=70, height=32,
                     fg_color=COLORS["bg_card"],
                     command=self._load_packs).pack(side="left", padx=5)

        # Icerik - iki bolum
        content = ctk.CTkFrame(page, fg_color="transparent")
        content.grid(row=1, column=0, sticky="nsew", padx=20, pady=10)
        content.grid_columnconfigure(0, weight=2)
        content.grid_columnconfigure(1, weight=1)
        content.grid_rowconfigure(0, weight=1)

        # Sol - Paket listesi
        self.packs_scroll = ctk.CTkScrollableFrame(content, fg_color="transparent")
        self.packs_scroll.grid(row=0, column=0, sticky="nsew", padx=(0,10))

        for i in range(3):
            self.packs_scroll.grid_columnconfigure(i, weight=1)

        # Sag - Paket detay
        self.pack_detail_frame = ctk.CTkFrame(content, fg_color=COLORS["bg_card"],
                                              corner_radius=12, width=350)
        self.pack_detail_frame.grid(row=0, column=1, sticky="nsew")
        self.pack_detail_frame.grid_propagate(False)

        self._create_pack_detail_panel()

    def _create_pack_detail_panel(self):
        """Paket detay paneli"""
        # Secili stickerlar
        self.selected_stickers = {}

        # Baslik
        ctk.CTkLabel(self.pack_detail_frame, text="Paket Detayi",
                    font=ctk.CTkFont(size=16, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,5))

        # Placeholder
        self.detail_placeholder = ctk.CTkLabel(self.pack_detail_frame,
                                               text="Bir paket secin",
                                               text_color=COLORS["text_sec"])
        self.detail_placeholder.pack(pady=50)

        # Detay container (gizli)
        self.detail_container = ctk.CTkFrame(self.pack_detail_frame,
                                             fg_color="transparent")

        # Ust kisim - kapak ve bilgi
        top_section = ctk.CTkFrame(self.detail_container, fg_color="transparent")
        top_section.pack(fill="x", padx=10, pady=5)

        # Cover image
        self.detail_cover_frame = ctk.CTkFrame(top_section,
                                               width=100, height=100,
                                               fg_color=COLORS["bg_hover"],
                                               corner_radius=8)
        self.detail_cover_frame.pack(side="left", padx=5)
        self.detail_cover_frame.pack_propagate(False)

        self.detail_cover_label = ctk.CTkLabel(self.detail_cover_frame, text="")
        self.detail_cover_label.place(relx=0.5, rely=0.5, anchor="center")

        # Bilgi kismi
        info_frame = ctk.CTkFrame(top_section, fg_color="transparent")
        info_frame.pack(side="left", fill="both", expand=True, padx=10)

        self.detail_name = ctk.CTkLabel(info_frame, text="",
                                        font=ctk.CTkFont(size=13, weight="bold"),
                                        text_color=COLORS["text_main"])
        self.detail_name.pack(anchor="w")

        self.detail_stats = ctk.CTkLabel(info_frame, text="",
                                         font=ctk.CTkFont(size=10),
                                         text_color=COLORS["text_sec"])
        self.detail_stats.pack(anchor="w", pady=2)

        # Hizli butonlar
        quick_btns = ctk.CTkFrame(info_frame, fg_color="transparent")
        quick_btns.pack(anchor="w", pady=5)

        ctk.CTkButton(quick_btns, text="Kapak", width=55, height=25,
                     font=ctk.CTkFont(size=9),
                     fg_color=COLORS["primary"],
                     command=self._update_pack_cover).pack(side="left", padx=2)

        ctk.CTkButton(quick_btns, text="+ Ekle", width=55, height=25,
                     font=ctk.CTkFont(size=9),
                     fg_color=COLORS["accent"],
                     command=self._add_sticker_to_pack).pack(side="left", padx=2)

        ctk.CTkButton(quick_btns, text="Sync", width=50, height=25,
                     font=ctk.CTkFont(size=9),
                     fg_color=COLORS["success"],
                     command=self._sync_selected_pack).pack(side="left", padx=2)

        # Ayirici
        ctk.CTkFrame(self.detail_container, height=1,
                    fg_color=COLORS["bg_hover"]).pack(fill="x", padx=10, pady=5)

        # Sticker listesi header
        sticker_header = ctk.CTkFrame(self.detail_container, fg_color="transparent")
        sticker_header.pack(fill="x", padx=10)

        ctk.CTkLabel(sticker_header, text="Stickerlar",
                    font=ctk.CTkFont(size=11, weight="bold"),
                    text_color=COLORS["text_sec"]).pack(side="left")

        # Secili sil butonu
        self.btn_delete_selected = ctk.CTkButton(sticker_header,
                                                  text="Secilileri Sil",
                                                  width=80, height=24,
                                                  font=ctk.CTkFont(size=9),
                                                  fg_color=COLORS["danger"],
                                                  state="disabled",
                                                  command=self._delete_selected_stickers)
        self.btn_delete_selected.pack(side="right")

        # Tumu sec checkbox
        self.select_all_var = ctk.BooleanVar(value=False)
        self.chk_select_all = ctk.CTkCheckBox(sticker_header, text="Tumu",
                                              variable=self.select_all_var,
                                              width=50, height=20,
                                              font=ctk.CTkFont(size=9),
                                              command=self._toggle_select_all)
        self.chk_select_all.pack(side="right", padx=10)

        # Sticker listesi
        self.sticker_list_frame = ctk.CTkScrollableFrame(self.detail_container,
                                                         fg_color=COLORS["bg_hover"],
                                                         corner_radius=6)
        self.sticker_list_frame.pack(fill="both", expand=True, padx=10, pady=5)

        # Alt butonlar
        bottom_btns = ctk.CTkFrame(self.detail_container, fg_color="transparent")
        bottom_btns.pack(fill="x", padx=10, pady=5)

        ctk.CTkButton(bottom_btns, text="Drive Yedekle", height=30,
                     font=ctk.CTkFont(size=10),
                     fg_color=COLORS["secondary"],
                     command=self._backup_selected_pack).pack(side="left", fill="x", expand=True, padx=2)

        ctk.CTkButton(bottom_btns, text="Paketi Sil", height=30,
                     font=ctk.CTkFont(size=10),
                     fg_color=COLORS["danger"],
                     command=self._delete_selected_pack).pack(side="left", fill="x", expand=True, padx=2)

    def _create_sync_page(self):
        """Senkronizasyon sayfasi"""
        page = ctk.CTkFrame(self.main_container, fg_color="transparent")
        self.pages["sync"] = page
        page.grid_columnconfigure(0, weight=1)
        page.grid_columnconfigure(1, weight=1)
        page.grid_rowconfigure(1, weight=1)

        # Header
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, columnspan=2, sticky="ew", padx=20, pady=(20,10))

        ctk.CTkLabel(header, text="Senkronizasyon Merkezi",
                    font=ctk.CTkFont(size=24, weight="bold"),
                    text_color=COLORS["text_main"]).pack(side="left")

        # Sol - Islemler
        actions_frame = ctk.CTkFrame(page, fg_color=COLORS["bg_card"],
                                     corner_radius=12)
        actions_frame.grid(row=1, column=0, sticky="nsew", padx=(20,10), pady=10)

        ctk.CTkLabel(actions_frame, text="Islemler",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        sync_actions = [
            ("Tam Senkronizasyon", "Tum islemleri sirayla yap", self._full_sync, COLORS["warning"]),
            ("Tray Guncelle", "Tum paketlerin kapak fotograflarini guncelle", self._update_all_trays, COLORS["primary"]),
            ("Firebase Sync", "Yerel paketleri Firebase'e yukle", self._sync_firebase, COLORS["accent"]),
            ("Drive'a Yedekle", "Paketleri Google Drive'a yedekle", self._backup_drive, COLORS["success"]),
            ("Drive'dan Indir", "Paketleri Google Drive'dan indir", self._download_drive, COLORS["secondary"]),
            ("GitHub Push", "Degisiklikleri GitHub'a gonder", self._github_push, COLORS["bg_hover"]),
        ]

        for text, desc, cmd, color in sync_actions:
            btn_frame = ctk.CTkFrame(actions_frame, fg_color="transparent")
            btn_frame.pack(fill="x", padx=15, pady=5)

            ctk.CTkButton(btn_frame, text=text, height=45,
                         fg_color=color, hover_color=COLORS["primary_hover"],
                         command=cmd).pack(fill="x")
            ctk.CTkLabel(btn_frame, text=desc, font=ctk.CTkFont(size=10),
                        text_color=COLORS["text_sec"]).pack(anchor="w", pady=(2,0))

        # Sag - Progress ve log
        progress_frame = ctk.CTkFrame(page, fg_color=COLORS["bg_card"],
                                      corner_radius=12)
        progress_frame.grid(row=1, column=1, sticky="nsew", padx=(10,20), pady=10)

        ctk.CTkLabel(progress_frame, text="Islem Durumu",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        # Progress bar
        self.sync_progress = ctk.CTkProgressBar(progress_frame,
                                                progress_color=COLORS["primary"])
        self.sync_progress.pack(fill="x", padx=15, pady=10)
        self.sync_progress.set(0)

        self.sync_status = ctk.CTkLabel(progress_frame, text="Hazir",
                                        font=ctk.CTkFont(size=11),
                                        text_color=COLORS["text_sec"])
        self.sync_status.pack(anchor="w", padx=15)

        # Sync log
        self.sync_log = ctk.CTkTextbox(progress_frame,
                                       font=ctk.CTkFont(family="Consolas", size=10),
                                       fg_color=COLORS["terminal_bg"],
                                       text_color=COLORS["terminal_text"])
        self.sync_log.pack(fill="both", expand=True, padx=15, pady=(10,15))

    def _create_stats_page(self):
        """Detayli istatistikler sayfasi"""
        page = ctk.CTkFrame(self.main_container, fg_color="transparent")
        self.pages["stats"] = page
        page.grid_columnconfigure(0, weight=1)
        page.grid_rowconfigure(1, weight=1)

        # Header
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", padx=20, pady=(20,10))

        ctk.CTkLabel(header, text="Detayli Istatistikler",
                    font=ctk.CTkFont(size=24, weight="bold"),
                    text_color=COLORS["text_main"]).pack(side="left")

        ctk.CTkButton(header, text="CSV Aktar", width=90, height=32,
                     fg_color=COLORS["bg_card"],
                     command=self._export_stats_csv).pack(side="right", padx=5)

        ctk.CTkButton(header, text="Yenile", width=70, height=32,
                     fg_color=COLORS["primary"],
                     command=self._load_stats).pack(side="right", padx=5)

        # Tablo header
        table_header = ctk.CTkFrame(page, fg_color=COLORS["bg_card"],
                                    corner_radius=8, height=40)
        table_header.grid(row=1, column=0, sticky="new", padx=20, pady=(10,0))
        table_header.grid_propagate(False)

        columns = ["Paket Adi", "Tip", "Kategori", "Sticker", "Indirme", "Gosterim", "Favori"]
        for col in columns:
            ctk.CTkLabel(table_header, text=col,
                        font=ctk.CTkFont(size=11, weight="bold"),
                        text_color=COLORS["text_sec"]).pack(side="left", padx=15, fill="x", expand=True)

        # Tablo icerigi
        self.stats_scroll = ctk.CTkScrollableFrame(page, fg_color="transparent")
        self.stats_scroll.grid(row=1, column=0, sticky="nsew", padx=20, pady=(45,10))

    def _create_settings_page(self):
        """Ayarlar sayfasi"""
        page = ctk.CTkScrollableFrame(self.main_container, fg_color="transparent")
        self.pages["settings"] = page

        # Header
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.pack(fill="x", padx=20, pady=(20,10))

        ctk.CTkLabel(header, text="Ayarlar ve Sistem Durumu",
                    font=ctk.CTkFont(size=24, weight="bold"),
                    text_color=COLORS["text_main"]).pack(side="left")

        # Sistem gereksinimleri
        req_frame = ctk.CTkFrame(page, fg_color=COLORS["bg_card"], corner_radius=12)
        req_frame.pack(fill="x", padx=20, pady=10)

        ctk.CTkLabel(req_frame, text="Sistem Gereksinimleri",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        self.req_labels = {}
        requirements = [
            ("python", "Python 3.8+"),
            ("ffmpeg", "FFmpeg (Video donusturme)"),
            ("git", "Git (GitHub sync)"),
            ("firebase_key", "Firebase Admin SDK Key"),
            ("credentials", "Google OAuth Credentials"),
            ("rembg", "Rembg (Arka plan silme)"),
        ]

        for key, text in requirements:
            row = ctk.CTkFrame(req_frame, fg_color="transparent")
            row.pack(fill="x", padx=15, pady=3)

            self.req_labels[key] = ctk.CTkLabel(row, text="[ ? ]",
                                                font=ctk.CTkFont(size=11),
                                                width=50)
            self.req_labels[key].pack(side="left")

            ctk.CTkLabel(row, text=text, font=ctk.CTkFont(size=11),
                        text_color=COLORS["text_main"]).pack(side="left", padx=10)

        ctk.CTkFrame(req_frame, height=15, fg_color="transparent").pack()

        # Firebase ayarlari
        fb_frame = ctk.CTkFrame(page, fg_color=COLORS["bg_card"], corner_radius=12)
        fb_frame.pack(fill="x", padx=20, pady=10)

        ctk.CTkLabel(fb_frame, text="Firebase Yapilandirmasi",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        self.fb_key_status = ctk.CTkLabel(fb_frame, text="Firebase key kontrol ediliyor...",
                                          font=ctk.CTkFont(size=11),
                                          text_color=COLORS["text_sec"])
        self.fb_key_status.pack(anchor="w", padx=15)

        ctk.CTkButton(fb_frame, text="Firebase Key Sec", height=35,
                     fg_color=COLORS["primary"],
                     command=self._select_firebase_key).pack(padx=15, pady=10, anchor="w")

        # Google Drive ayarlari
        drive_frame = ctk.CTkFrame(page, fg_color=COLORS["bg_card"], corner_radius=12)
        drive_frame.pack(fill="x", padx=20, pady=10)

        ctk.CTkLabel(drive_frame, text="Google Drive Yapilandirmasi",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        self.drive_status = ctk.CTkLabel(drive_frame, text="Credentials kontrol ediliyor...",
                                         font=ctk.CTkFont(size=11),
                                         text_color=COLORS["text_sec"])
        self.drive_status.pack(anchor="w", padx=15)

        ctk.CTkButton(drive_frame, text="Credentials.json Sec", height=35,
                     fg_color=COLORS["primary"],
                     command=self._select_credentials).pack(padx=15, pady=10, anchor="w")

        # Yardim
        help_frame = ctk.CTkFrame(page, fg_color=COLORS["bg_card"], corner_radius=12)
        help_frame.pack(fill="x", padx=20, pady=10)

        ctk.CTkLabel(help_frame, text="Kurulum Rehberi",
                    font=ctk.CTkFont(size=14, weight="bold"),
                    text_color=COLORS["text_main"]).pack(anchor="w", padx=15, pady=(15,10))

        help_text = """
1. FIREBASE ADMIN SDK:
   - Firebase Console'a gidin
   - Project Settings > Service Accounts
   - "Generate new private key" tiklayin
   - JSON dosyasini bu klasore koyun

2. GOOGLE DRIVE CREDENTIALS:
   - Google Cloud Console'a gidin
   - APIs & Services > Credentials
   - OAuth 2.0 Client ID olusturun (Desktop app)
   - JSON'u indirip "credentials.json" olarak kaydedin

3. FFMPEG:
   - Windows: winget install ffmpeg
   - macOS: brew install ffmpeg
   - Linux: sudo apt install ffmpeg

4. GIT:
   - Windows: https://git-scm.com/download/win
   - macOS: brew install git
   - Linux: sudo apt install git
"""

        ctk.CTkLabel(help_frame, text=help_text,
                    font=ctk.CTkFont(family="Consolas", size=10),
                    text_color=COLORS["text_sec"],
                    justify="left").pack(anchor="w", padx=15, pady=(0,15))

    # ========================================================================
    # YARDIMCI UI METOTLARI
    # ========================================================================

    def _create_stat_card(self, parent, title, value, color):
        """Istatistik karti olustur"""
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)

        indicator = ctk.CTkFrame(card, width=4, corner_radius=2, fg_color=color)
        indicator.pack(side="left", fill="y", padx=(0,10), pady=10)

        content = ctk.CTkFrame(card, fg_color="transparent")
        content.pack(fill="both", expand=True, padx=15, pady=15)

        ctk.CTkLabel(content, text=title, font=ctk.CTkFont(size=10),
                    text_color=COLORS["text_sec"]).pack(anchor="w")

        value_label = ctk.CTkLabel(content, text=value,
                                   font=ctk.CTkFont(size=24, weight="bold"),
                                   text_color=COLORS["text_main"])
        value_label.pack(anchor="w", pady=(5,0))

        card.value_label = value_label
        return card

    def _show_page(self, page_name):
        """Sayfa goster"""
        # Nav butonlarini guncelle
        for key, btn in self.nav_btns.items():
            if key == page_name:
                btn.configure(fg_color=COLORS["primary"], text_color="#FFFFFF")
            else:
                btn.configure(fg_color="transparent", text_color=COLORS["text_main"])

        # Tum sayfalari gizle
        for page in self.pages.values():
            page.grid_forget()

        # Secilen sayfayi goster
        self.pages[page_name].grid(row=0, column=0, sticky="nsew")
        self.current_page = page_name

        # Sayfa acildiginda refresh
        if page_name == "dashboard":
            self._refresh_dashboard()
        elif page_name == "packs":
            self._load_packs()
        elif page_name == "stats":
            self._load_stats()
        elif page_name == "settings":
            self._check_requirements()

    def _log(self, message, level="INFO"):
        """Terminal'e log yaz (renkli)"""
        timestamp = datetime.now().strftime("%H:%M:%S")

        # Seviyeye gore renk ve simge
        level_config = {
            "INFO": ("[*]", COLORS["terminal_text"]),
            "SUCCESS": ("[+]", COLORS["success"]),
            "WARNING": ("[!]", COLORS["warning"]),
            "ERROR": ("[X]", COLORS["danger"]),
        }
        symbol, color = level_config.get(level, ("[*]", COLORS["terminal_text"]))

        log_line = f"[{timestamp}] {symbol} {message}\n"

        if hasattr(self, 'terminal') and self.terminal:
            # Renk tag'i olustur
            tag_name = f"tag_{level}"
            self.terminal._textbox.tag_configure(tag_name, foreground=color)

            # Mesaji renkli ekle
            self.terminal._textbox.insert("end", log_line, tag_name)
            self.terminal.see("end")

        # Status bar guncelle
        if hasattr(self, 'status_label') and self.status_label:
            status_colors = {
                "SUCCESS": COLORS["success"],
                "WARNING": COLORS["warning"],
                "ERROR": COLORS["danger"],
            }
            self.status_label.configure(
                text=f"{message[:60]}",
                text_color=status_colors.get(level, COLORS["text_sec"])
            )

        # Logger'a da yaz
        if level == "ERROR":
            logger.error(message)
        elif level == "WARNING":
            logger.warning(message)
        else:
            logger.info(message)

    def _sync_log_write(self, message):
        """Sync log'a yaz"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        if hasattr(self, 'sync_log') and self.sync_log:
            self.sync_log.insert("end", f"[{timestamp}] {message}\n")
            self.sync_log.see("end")
        if hasattr(self, 'sync_status') and self.sync_status:
            self.sync_status.configure(text=message[:50])

    def _clear_terminal(self):
        """Terminal'i temizle"""
        self.terminal.delete("1.0", "end")
        self._log("Terminal temizlendi", "INFO")

    def _stop_operation(self):
        """Calistirilan islemi durdur"""
        if self.operation_running:
            self.cancel_operation = True
            self._log("Islem durduruluyor...", "WARNING")
            self.stop_button.configure(state="disabled", text="Durduruluyor...")

    def _start_operation(self, operation_name: str):
        """Islem baslatildiginda cagrilir"""
        self.cancel_operation = False
        self.operation_running = True
        self.after(0, lambda: self.stop_button.configure(state="normal", text="Durdur"))
        self._log(f"{operation_name} baslatiliyor...", "INFO")

    def _end_operation(self, success: bool = True, message: str = ""):
        """Islem bittiginde cagrilir"""
        self.operation_running = False
        self.cancel_operation = False
        self.after(0, lambda: self.stop_button.configure(state="disabled", text="Durdur"))
        if message:
            level = "SUCCESS" if success else "ERROR"
            if self.cancel_operation or "iptal" in message.lower() or "durduruldu" in message.lower():
                level = "WARNING"
            self._log(message, level)

    def _check_cancelled(self) -> bool:
        """Islem iptal edildi mi kontrol et"""
        if self.cancel_operation:
            self._log("Islem kullanici tarafindan iptal edildi", "WARNING")
            self._end_operation(False, "Islem durduruldu")
            return True
        return False

    # ========================================================================
    # BASLANGIC KONTROLLERI
    # ========================================================================

    def _startup_checks(self):
        """Baslangic kontrollerini yap"""
        self._log("Sistem kontrolleri baslatiliyor...", "INFO")
        threading.Thread(target=self._run_startup_checks, daemon=True).start()

    def _run_startup_checks(self):
        """Arka planda baslangic kontrolleri"""
        # FFmpeg kontrol
        ffmpeg_ok = check_ffmpeg()
        self.dependency_status["ffmpeg"] = ffmpeg_ok
        self.after(0, lambda: self._update_service_status(
            "ffmpeg", ffmpeg_ok, "FFmpeg: Yuklu" if ffmpeg_ok else "FFmpeg: BULUNAMADI"
        ))

        if not ffmpeg_ok:
            self.after(0, lambda: self._log(
                "UYARI: FFmpeg yuklu degil! Video donusturme calismayacak.", "WARNING"
            ))
            self.after(0, lambda: self._log(
                "Kurulum: sudo apt install ffmpeg (Linux) | brew install ffmpeg (Mac) | winget install ffmpeg (Win)", "INFO"
            ))

        # Git kontrol
        git_ok = check_git()
        self.dependency_status["git"] = git_ok

        if not git_ok:
            self.after(0, lambda: self._log(
                "UYARI: Git yuklu degil! GitHub sync calismayacak.", "WARNING"
            ))

        # Firebase kontrol
        firebase_key = find_firebase_key()
        if firebase_key:
            self.after(0, lambda: self._log(f"Firebase key bulundu: {firebase_key.name}", "SUCCESS"))
            self._init_firebase()
        else:
            self.after(0, lambda: self._log(
                "UYARI: Firebase Admin SDK key bulunamadi!", "WARNING"
            ))
            self.after(0, lambda: self._log(
                "Ayarlar sayfasindan Firebase key dosyasini secin.", "INFO"
            ))
            self.after(0, lambda: self._update_service_status(
                "firebase", False, "Firebase: Key bulunamadi"
            ))

        # Credentials kontrol
        if CREDENTIALS_FILE.exists():
            self.after(0, lambda: self._log("Google credentials.json bulundu", "SUCCESS"))
            self._init_drive()
        else:
            self.after(0, lambda: self._log(
                "UYARI: credentials.json bulunamadi! Drive sync calismayacak.", "WARNING"
            ))
            self.after(0, lambda: self._log(
                "Ayarlar sayfasindan credentials.json dosyasini secin.", "INFO"
            ))
            self.after(0, lambda: self._update_service_status(
                "drive", False, "Drive: Credentials yok"
            ))

        # Klasor kontrolleri
        STICKERS_DIR.mkdir(exist_ok=True)
        PREMIUM_STICKERS_DIR.mkdir(exist_ok=True)
        OUTPUT_DIR.mkdir(exist_ok=True)

        self.after(0, lambda: self._log("Sistem kontrolleri tamamlandi", "SUCCESS"))

    def _update_service_status(self, service, status, text):
        """Servis durumunu guncelle"""
        color = COLORS["success"] if status else COLORS["danger"]

        if service == "firebase":
            self.lbl_firebase.configure(text=text, text_color=color)
        elif service == "drive":
            self.lbl_drive.configure(text=text, text_color=color)
        elif service == "ffmpeg":
            self.lbl_ffmpeg.configure(text=text, text_color=color)

    def _init_firebase(self):
        """Firebase'i baslat"""
        try:
            from core.firebase_manager import FirebaseManager
            self.firebase = FirebaseManager()
            ok, msg = self.firebase.initialize()

            self.after(0, lambda: self._update_service_status(
                "firebase", ok, f"Firebase: {'Bagli' if ok else 'Hata'}"
            ))

            if ok:
                self.services_ready = True
                self.after(0, self._refresh_dashboard)
            else:
                self.after(0, lambda: self._log(f"Firebase hatasi: {msg}", "ERROR"))

        except Exception as e:
            self.after(0, lambda: self._log(f"Firebase import hatasi: {e}", "ERROR"))
            self.after(0, lambda: self._update_service_status(
                "firebase", False, "Firebase: Import hatasi"
            ))

    def _init_drive(self):
        """Drive'i baslat"""
        try:
            from core.drive_manager import DriveManager
            self.drive = DriveManager()
            ok, msg = self.drive.initialize()

            self.after(0, lambda: self._update_service_status(
                "drive", ok, f"Drive: {'Bagli' if ok else 'Hata'}"
            ))

            if not ok:
                self.after(0, lambda: self._log(f"Drive hatasi: {msg}", "WARNING"))

        except Exception as e:
            self.after(0, lambda: self._log(f"Drive import hatasi: {e}", "ERROR"))
            self.after(0, lambda: self._update_service_status(
                "drive", False, "Drive: Import hatasi"
            ))

    def _check_requirements(self):
        """Gereksinim kontrollerini guncelle"""
        # Python
        py_version = sys.version.split()[0]
        py_ok = tuple(map(int, py_version.split('.')[:2])) >= (3, 8)
        self._update_req_label("python", py_ok, f"[{'OK' if py_ok else 'X'}]")

        # FFmpeg
        ffmpeg_ok = check_ffmpeg()
        self._update_req_label("ffmpeg", ffmpeg_ok, f"[{'OK' if ffmpeg_ok else 'X'}]")

        # Git
        git_ok = check_git()
        self._update_req_label("git", git_ok, f"[{'OK' if git_ok else 'X'}]")

        # Firebase key
        fb_key = find_firebase_key()
        fb_ok = fb_key is not None
        self._update_req_label("firebase_key", fb_ok, f"[{'OK' if fb_ok else 'X'}]")

        if fb_ok:
            self.fb_key_status.configure(text=f"Key: {fb_key.name}",
                                         text_color=COLORS["success"])
        else:
            self.fb_key_status.configure(text="Firebase key bulunamadi!",
                                         text_color=COLORS["danger"])

        # Credentials
        cred_ok = CREDENTIALS_FILE.exists()
        self._update_req_label("credentials", cred_ok, f"[{'OK' if cred_ok else 'X'}]")

        if cred_ok:
            self.drive_status.configure(text="credentials.json mevcut",
                                        text_color=COLORS["success"])
        else:
            self.drive_status.configure(text="credentials.json bulunamadi!",
                                        text_color=COLORS["danger"])

        # Rembg
        try:
            import rembg
            rembg_ok = True
        except:
            rembg_ok = False
        self._update_req_label("rembg", rembg_ok, f"[{'OK' if rembg_ok else 'X'}]")

    def _update_req_label(self, key, status, text):
        """Gereksinim etiketini guncelle"""
        if key in self.req_labels:
            color = COLORS["success"] if status else COLORS["danger"]
            self.req_labels[key].configure(text=text, text_color=color)

    # ========================================================================
    # DASHBOARD ISLEMLERI
    # ========================================================================

    def _refresh_dashboard(self):
        """Dashboard'u yenile"""
        if not self.firebase or not self.firebase.is_initialized:
            return

        def fetch():
            try:
                stats = self.firebase.get_statistics()
                self.stats_data = stats
                self.after(0, lambda: self._update_dashboard_ui(stats))
            except Exception as e:
                self.after(0, lambda: self._log(f"Dashboard hatasi: {e}", "ERROR"))

        threading.Thread(target=fetch, daemon=True).start()

    def _update_dashboard_ui(self, stats):
        """Dashboard UI'i guncelle"""
        # Stat kartlari
        self.stat_cards["packs"].value_label.configure(
            text=str(stats.get("total_packs", 0)))
        self.stat_cards["stickers"].value_label.configure(
            text=str(stats.get("total_stickers", 0)))
        self.stat_cards["downloads"].value_label.configure(
            text=str(stats.get("total_downloads", 0)))
        self.stat_cards["views"].value_label.configure(
            text=str(stats.get("total_views", 0)))
        self.stat_cards["favorites"].value_label.configure(
            text=str(stats.get("total_favorites", 0)))

        # Top paketler
        for w in self.top_packs_list.winfo_children():
            w.destroy()

        top = stats.get("top_downloaded", [])[:10]
        for i, p in enumerate(top, 1):
            row = ctk.CTkFrame(self.top_packs_list, fg_color=COLORS["bg_hover"],
                              corner_radius=6, height=40)
            row.pack(fill="x", pady=2)
            row.pack_propagate(False)

            ctk.CTkLabel(row, text=f"#{i}", font=ctk.CTkFont(size=10),
                        text_color=COLORS["primary"], width=30).pack(side="left", padx=10)

            ctk.CTkLabel(row, text=p.get("name", "?")[:25],
                        font=ctk.CTkFont(size=11),
                        text_color=COLORS["text_main"]).pack(side="left", fill="x", expand=True)

            if p.get("is_premium"):
                ctk.CTkLabel(row, text="P", font=ctk.CTkFont(size=9),
                            text_color=COLORS["gold"]).pack(side="right", padx=5)

            ctk.CTkLabel(row, text=str(p.get("downloads", 0)),
                        font=ctk.CTkFont(size=10),
                        text_color=COLORS["success"]).pack(side="right", padx=10)

    # ========================================================================
    # PAKET YONETIMI
    # ========================================================================

    def _load_packs(self):
        """Paketleri yukle"""
        def fetch():
            packs = []

            # Normal paketler
            if STICKERS_DIR.exists():
                for d in STICKERS_DIR.iterdir():
                    if d.is_dir():
                        files = get_sticker_files(d)
                        packs.append({
                            "id": get_pack_id(d.name),
                            "name": d.name,
                            "path": d,
                            "sticker_count": len(files),
                            "is_premium": False,
                            "downloads": 0,
                            "views": 0
                        })

            # Premium paketler
            if PREMIUM_STICKERS_DIR.exists():
                for d in PREMIUM_STICKERS_DIR.iterdir():
                    if d.is_dir():
                        files = get_sticker_files(d)
                        packs.append({
                            "id": get_pack_id(d.name),
                            "name": d.name,
                            "path": d,
                            "sticker_count": len(files),
                            "is_premium": True,
                            "downloads": 0,
                            "views": 0
                        })

            # Firebase'den istatistikleri al
            if self.firebase and self.firebase.is_initialized:
                try:
                    fb_packs = self.firebase.get_all_packs()
                    for fp in fb_packs:
                        for p in packs:
                            if p["id"] == fp.get("_id"):
                                p["downloads"] = fp.get("download_count", 0)
                                p["views"] = fp.get("view_count", 0)
                except:
                    pass

            self.packs_data = packs
            self.after(0, self._render_packs)

        threading.Thread(target=fetch, daemon=True).start()

    def _render_packs(self):
        """Paketleri render et"""
        for w in self.packs_scroll.winfo_children():
            w.destroy()

        filtered = self.packs_data

        # Filtre
        filter_val = self.pack_filter.get()
        if filter_val == "Premium":
            filtered = [p for p in filtered if p.get("is_premium")]
        elif filter_val == "Normal":
            filtered = [p for p in filtered if not p.get("is_premium")]

        # Arama
        search = self.pack_search.get().lower()
        if search:
            filtered = [p for p in filtered if search in p.get("name", "").lower()]

        for i, pack in enumerate(filtered):
            row = i // 3
            col = i % 3

            card = self._create_pack_card(self.packs_scroll, pack)
            card.grid(row=row, column=col, padx=5, pady=5, sticky="nsew")

    def _create_pack_card(self, parent, pack):
        """Paket karti olustur"""
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=10)

        # Premium indicator
        if pack.get("is_premium"):
            ctk.CTkFrame(card, width=4, fg_color=COLORS["gold"]).pack(side="left", fill="y")

        content = ctk.CTkFrame(card, fg_color="transparent")
        content.pack(fill="both", expand=True, padx=12, pady=10)

        # Baslik
        header = ctk.CTkFrame(content, fg_color="transparent")
        header.pack(fill="x")

        name = pack.get("name", "?")[:20]
        ctk.CTkLabel(header, text=name,
                    font=ctk.CTkFont(size=13, weight="bold"),
                    text_color=COLORS["text_main"]).pack(side="left")

        if pack.get("is_premium"):
            ctk.CTkLabel(header, text="PREMIUM",
                        font=ctk.CTkFont(size=8, weight="bold"),
                        text_color=COLORS["gold"]).pack(side="right")

        # Istatistikler
        stats = ctk.CTkFrame(content, fg_color="transparent")
        stats.pack(fill="x", pady=(8,0))

        ctk.CTkLabel(stats, text=f"{pack.get('sticker_count', 0)} sticker",
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["text_sec"]).pack(side="left", padx=(0,10))

        ctk.CTkLabel(stats, text=f"{pack.get('downloads', 0)} indirme",
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["success"]).pack(side="left")

        # Sec butonu
        ctk.CTkButton(content, text="Sec", width=50, height=25,
                     font=ctk.CTkFont(size=10),
                     fg_color=COLORS["primary"],
                     command=lambda p=pack: self._select_pack(p)).pack(side="right", pady=(8,0))

        return card

    def _filter_packs(self):
        """Paketleri filtrele"""
        self._render_packs()

    def _select_pack(self, pack):
        """Paket sec"""
        self.selected_pack = pack

        # Placeholder'i gizle, detay container'i goster
        self.detail_placeholder.pack_forget()
        self.detail_container.pack(fill="both", expand=True)

        # Bilgileri guncelle
        self.detail_name.configure(text=pack["name"])
        self.detail_stats.configure(
            text=f"{pack['sticker_count']} sticker | {pack['downloads']} indirme"
        )

        # Tray yukle
        self._load_pack_cover(pack)

        # Stickerları listele
        self._load_pack_stickers(pack)

    def _load_pack_cover(self, pack):
        """Paket kapagini yukle"""
        self.detail_cover_label.configure(image=None, text="Yukleniyor...")

        def load():
            tray_source = find_tray_source(pack["path"])
            if tray_source:
                try:
                    img = Image.open(tray_source)
                    img.thumbnail((140, 140))
                    ctk_img = ctk.CTkImage(light_image=img, dark_image=img, size=img.size)
                    self.after(0, lambda: self.detail_cover_label.configure(
                        image=ctk_img, text=""
                    ))
                    self._pack_cover_img = ctk_img  # GC engelle
                except:
                    self.after(0, lambda: self.detail_cover_label.configure(text="Hata"))
            else:
                self.after(0, lambda: self.detail_cover_label.configure(text="Kapak yok"))

        threading.Thread(target=load, daemon=True).start()

    def _load_pack_stickers(self, pack):
        """Paketteki stickerları listele"""
        # Onceki secimi temizle
        self.selected_stickers = {}
        self.select_all_var.set(False)
        self._update_delete_button()

        # Onceki widget'lari temizle
        for w in self.sticker_list_frame.winfo_children():
            w.destroy()

        files = get_sticker_files(pack["path"])

        if not files:
            ctk.CTkLabel(self.sticker_list_frame,
                        text="Bu pakette sticker yok",
                        font=ctk.CTkFont(size=11),
                        text_color=COLORS["text_sec"]).pack(pady=20)
            return

        # Thumbnail cache
        self._sticker_thumbs = {}

        for i, f in enumerate(files):
            row = ctk.CTkFrame(self.sticker_list_frame, fg_color=COLORS["bg_card"],
                              corner_radius=6, height=50)
            row.pack(fill="x", pady=2, padx=2)
            row.pack_propagate(False)

            # Checkbox
            var = ctk.BooleanVar(value=False)
            self.selected_stickers[str(f)] = var

            chk = ctk.CTkCheckBox(row, text="", variable=var,
                                  width=20, height=20,
                                  command=self._update_delete_button)
            chk.pack(side="left", padx=8)

            # Thumbnail placeholder
            thumb_frame = ctk.CTkFrame(row, width=40, height=40,
                                       fg_color=COLORS["bg_hover"],
                                       corner_radius=4)
            thumb_frame.pack(side="left", padx=5)
            thumb_frame.pack_propagate(False)

            thumb_label = ctk.CTkLabel(thumb_frame, text="...",
                                       font=ctk.CTkFont(size=8),
                                       text_color=COLORS["text_sec"])
            thumb_label.place(relx=0.5, rely=0.5, anchor="center")

            # Thumbnail yukle (async)
            self._load_sticker_thumbnail(f, thumb_label, i)

            # Dosya bilgisi
            info_frame = ctk.CTkFrame(row, fg_color="transparent")
            info_frame.pack(side="left", fill="both", expand=True, padx=5)

            # Dosya adi
            name_text = f.name[:22] + "..." if len(f.name) > 25 else f.name
            ctk.CTkLabel(info_frame, text=name_text,
                        font=ctk.CTkFont(size=10),
                        text_color=COLORS["text_main"]).pack(anchor="w")

            # Dosya tipi ve boyut
            try:
                size_kb = f.stat().st_size / 1024
                ext = f.suffix.upper()[1:]
                info_text = f"{ext} - {size_kb:.1f} KB"
            except:
                info_text = f.suffix.upper()[1:]

            ctk.CTkLabel(info_frame, text=info_text,
                        font=ctk.CTkFont(size=8),
                        text_color=COLORS["text_sec"]).pack(anchor="w")

            # Sil butonu
            ctk.CTkButton(row, text="X", width=30, height=30,
                         font=ctk.CTkFont(size=10),
                         fg_color=COLORS["danger"],
                         hover_color="#D94452",
                         command=lambda file=f, pk=pack: self._delete_sticker(file, pk)
                         ).pack(side="right", padx=8)

    def _load_sticker_thumbnail(self, file_path, label, index):
        """Sticker thumbnail'ini yukle"""
        def load():
            try:
                ext = file_path.suffix.lower()
                if ext in IMAGE_EXTENSIONS:
                    img = Image.open(file_path)
                    img.thumbnail((36, 36))
                    ctk_img = ctk.CTkImage(light_image=img, dark_image=img, size=(36, 36))
                    self._sticker_thumbs[index] = ctk_img
                    self.after(0, lambda: label.configure(image=ctk_img, text=""))
                elif ext == '.gif':
                    img = Image.open(file_path)
                    img.seek(0)
                    img.thumbnail((36, 36))
                    ctk_img = ctk.CTkImage(light_image=img, dark_image=img, size=(36, 36))
                    self._sticker_thumbs[index] = ctk_img
                    self.after(0, lambda: label.configure(image=ctk_img, text=""))
                elif ext in VIDEO_EXTENSIONS:
                    self.after(0, lambda: label.configure(text="VID"))
                else:
                    self.after(0, lambda: label.configure(text=ext[1:3].upper()))
            except:
                self.after(0, lambda: label.configure(text="?"))

        threading.Thread(target=load, daemon=True).start()

    def _toggle_select_all(self):
        """Tum stickerlari sec/kaldir"""
        select = self.select_all_var.get()
        for var in self.selected_stickers.values():
            var.set(select)
        self._update_delete_button()

    def _update_delete_button(self):
        """Secili sil butonunu guncelle"""
        count = sum(1 for var in self.selected_stickers.values() if var.get())
        if count > 0:
            self.btn_delete_selected.configure(
                state="normal",
                text=f"Sil ({count})"
            )
        else:
            self.btn_delete_selected.configure(
                state="disabled",
                text="Secilileri Sil"
            )

    def _delete_selected_stickers(self):
        """Secili stickerlari sil"""
        to_delete = [Path(p) for p, var in self.selected_stickers.items() if var.get()]

        if not to_delete:
            return

        if not messagebox.askyesno("Onayla",
            f"{len(to_delete)} sticker silinecek.\nDevam edilsin mi?"):
            return

        deleted = 0
        for f in to_delete:
            try:
                if f.exists():
                    f.unlink()
                    deleted += 1
            except Exception as e:
                self._log(f"Silinemedi: {f.name} - {e}", "ERROR")

        self._log(f"{deleted} sticker silindi", "SUCCESS")
        self._load_pack_stickers(self.selected_pack)
        self._load_packs()

    def _add_new_pack(self):
        """Yeni paket ekle"""
        dialog = ctk.CTkToplevel(self)
        dialog.title("Yeni Paket")
        dialog.geometry("400x300")
        dialog.transient(self)
        dialog.grab_set()

        ctk.CTkLabel(dialog, text="Yeni Sticker Paketi",
                    font=ctk.CTkFont(size=18, weight="bold")).pack(pady=20)

        ctk.CTkLabel(dialog, text="Paket Adi:").pack(anchor="w", padx=30)
        name_entry = ctk.CTkEntry(dialog, width=300)
        name_entry.pack(padx=30, pady=5)

        ctk.CTkLabel(dialog, text="Tip:").pack(anchor="w", padx=30, pady=(15,0))
        type_var = ctk.StringVar(value="normal")
        ctk.CTkRadioButton(dialog, text="Normal (Ucretsiz)",
                          variable=type_var, value="normal").pack(anchor="w", padx=40)
        ctk.CTkRadioButton(dialog, text="Premium (Ucretli)",
                          variable=type_var, value="premium").pack(anchor="w", padx=40)

        def create():
            name = name_entry.get().strip()
            if not name:
                messagebox.showerror("Hata", "Paket adi girin!")
                return

            target = PREMIUM_STICKERS_DIR if type_var.get() == "premium" else STICKERS_DIR
            pack_dir = target / name

            if pack_dir.exists():
                messagebox.showerror("Hata", "Bu paket zaten var!")
                return

            pack_dir.mkdir(parents=True)
            self._log(f"Yeni paket olusturuldu: {name}", "SUCCESS")
            messagebox.showinfo("Basarili",
                f"Klasor olusturuldu: {pack_dir}\n\nSimdi bu klasore sticker dosyalarini koyun.")
            dialog.destroy()
            self._load_packs()

        ctk.CTkButton(dialog, text="Olustur", fg_color=COLORS["primary"],
                     command=create).pack(pady=20)

    def _update_pack_cover(self):
        """Seçili paketin kapagini guncelle"""
        if not self.selected_pack:
            return

        file_path = filedialog.askopenfilename(
            title="Kapak Fotografi Sec",
            filetypes=[("Images", "*.png *.jpg *.jpeg *.webp")]
        )

        if file_path:
            def process():
                try:
                    from core.converter import StickerConverter

                    converter = StickerConverter()
                    dest = self.selected_pack["path"] / "tray.webp"

                    self._log(f"Kapak guncelleniyor: {self.selected_pack['name']}", "INFO")

                    if converter.create_tray_image(Path(file_path), dest, force_bg_removal=True):
                        self.after(0, lambda: self._log("Kapak guncellendi (yerel)", "SUCCESS"))
                        self.after(0, lambda: self._load_pack_cover(self.selected_pack))
                        self.after(0, lambda: messagebox.showinfo("Basarili",
                            "Kapak guncellendi.\nFirebase'e yuklemek icin 'Firebase Sync' butonunu kullanin."))
                    else:
                        self.after(0, lambda: self._log("Kapak guncellenemedi", "ERROR"))
                except Exception as e:
                    self.after(0, lambda: self._log(f"Hata: {e}", "ERROR"))

            threading.Thread(target=process, daemon=True).start()

    def _add_sticker_to_pack(self):
        """Pakete sticker ekle"""
        if not self.selected_pack:
            return

        files = filedialog.askopenfilenames(
            title="Sticker Dosyalarini Sec",
            filetypes=[
                ("All Supported", "*.png *.jpg *.jpeg *.webp *.gif *.mp4 *.mov"),
                ("Images", "*.png *.jpg *.jpeg *.webp"),
                ("GIF", "*.gif"),
                ("Videos", "*.mp4 *.mov")
            ]
        )

        if files:
            added = 0
            for f in files:
                src = Path(f)
                dest = self.selected_pack["path"] / src.name

                if not dest.exists():
                    shutil.copy(src, dest)
                    added += 1

            self._log(f"{added} sticker eklendi: {self.selected_pack['name']}", "SUCCESS")
            self._load_pack_stickers(self.selected_pack)
            self._load_packs()  # Sayilari guncelle

    def _delete_sticker(self, file_path, pack):
        """Sticker sil"""
        if messagebox.askyesno("Onayla", f"'{file_path.name}' silinsin mi?"):
            try:
                file_path.unlink()
                self._log(f"Sticker silindi: {file_path.name}", "SUCCESS")
                self._load_pack_stickers(pack)
                self._load_packs()
            except Exception as e:
                self._log(f"Silme hatasi: {e}", "ERROR")

    def _sync_selected_pack(self):
        """Secili paketi Firebase'e senkronize et"""
        if not self.selected_pack:
            return

        if not self.firebase or not self.firebase.is_initialized:
            messagebox.showerror("Hata", "Firebase bagli degil!")
            return

        self._log(f"Firebase sync baslatiliyor: {self.selected_pack['name']}", "INFO")

        def process():
            try:
                from core.converter import StickerConverter

                converter = StickerConverter()
                cache = load_cache()

                converter.process_pack(
                    self.selected_pack["path"],
                    OUTPUT_DIR,
                    cache,
                    self.firebase,
                    is_premium=self.selected_pack["is_premium"]
                )

                save_cache(cache)
                self.after(0, lambda: self._log(
                    f"Firebase sync tamamlandi: {self.selected_pack['name']}", "SUCCESS"
                ))
                self.after(0, self._load_packs)

            except Exception as e:
                self.after(0, lambda: self._log(f"Sync hatasi: {e}", "ERROR"))

        threading.Thread(target=process, daemon=True).start()

    def _backup_selected_pack(self):
        """Secili paketi Drive'a yedekle"""
        if not self.selected_pack:
            return

        if not self.drive or not self.drive.is_initialized:
            messagebox.showerror("Hata", "Drive bagli degil!")
            return

        self._log(f"Drive yedekleme baslatiliyor: {self.selected_pack['name']}", "INFO")

        def process():
            try:
                main_folder_id = self.drive.get_main_folder_id()

                pack_name = self.selected_pack["name"]
                folder_name = f"premium_{pack_name}" if self.selected_pack["is_premium"] else pack_name

                folder_id = self.drive.get_or_create_folder(folder_name, main_folder_id)

                if folder_id:
                    files = [f for f in self.selected_pack["path"].iterdir()
                            if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS]

                    for f in files:
                        self.drive.upload_file(f, folder_id)

                    self.after(0, lambda: self._log(
                        f"Drive yedekleme tamamlandi: {len(files)} dosya", "SUCCESS"
                    ))
                else:
                    self.after(0, lambda: self._log("Drive klasoru olusturulamadi", "ERROR"))

            except Exception as e:
                self.after(0, lambda: self._log(f"Drive hatasi: {e}", "ERROR"))

        threading.Thread(target=process, daemon=True).start()

    def _delete_selected_pack(self):
        """Secili paketi sil"""
        if not self.selected_pack:
            return

        if not messagebox.askyesno("Onayla",
            f"'{self.selected_pack['name']}' paketi silinsin mi?\nBu islem geri alinamaz!"):
            return

        pack = self.selected_pack

        try:
            # Yerel klasor
            if pack["path"].exists():
                shutil.rmtree(pack["path"])

            # Output klasoru
            output_dir = OUTPUT_DIR / pack["id"]
            if output_dir.exists():
                shutil.rmtree(output_dir)

            # Firebase
            if self.firebase and self.firebase.is_initialized:
                self.firebase.delete_pack(pack["id"], pack["is_premium"])
                self.firebase.delete_pack_from_storage(pack["id"], pack["is_premium"])

            # Cache
            cache = load_cache()
            cache.get("converted", {}).pop(pack["id"], None)
            cache.get("uploaded", {}).pop(pack["id"], None)
            cache.get("tray", {}).pop(f"{pack['id']}_tray", None)
            save_cache(cache)

            self._log(f"Paket silindi: {pack['name']}", "SUCCESS")

            # UI reset
            self.selected_pack = None
            self.detail_container.pack_forget()
            self.detail_placeholder.pack(pady=50)
            self._load_packs()

        except Exception as e:
            self._log(f"Silme hatasi: {e}", "ERROR")

    # ========================================================================
    # SENKRONIZASYON ISLEMLERI
    # ========================================================================

    def _full_sync(self):
        """Tam senkronizasyon"""
        if self.operation_running:
            messagebox.showwarning("Uyari", "Baska bir islem devam ediyor!")
            return

        if not messagebox.askyesno("Onayla",
            "Tam senkronizasyon yapilacak:\n1. Drive'dan indir\n2. Trayleri guncelle\n3. Firebase sync\n4. GitHub push\n\nDevam edilsin mi?"):
            return

        self._start_operation("Tam Senkronizasyon")
        self.sync_progress.set(0)

        def process():
            try:
                # 1. Drive'dan indir
                if self._check_cancelled():
                    return
                self._sync_log_write("1/4 - Drive'dan indiriliyor...")
                self._log("Drive'dan paketler indiriliyor...", "INFO")
                self.after(0, lambda: self.sync_progress.set(0.1))

                if self.drive and self.drive.is_initialized:
                    down, skip = self.drive.download_all_packs(
                        STICKERS_DIR, PREMIUM_STICKERS_DIR,
                        progress_callback=lambda m: self._sync_log_write(m)
                    )
                    self._log(f"Drive indirme: {down} indirildi, {skip} atlandi", "INFO")
                else:
                    self._log("Drive bagli degil, atlaniyor", "WARNING")

                # 2. Tray guncelle
                if self._check_cancelled():
                    return
                self._sync_log_write("2/4 - Trayler guncelleniyor...")
                self._log("Tray resimleri guncelleniyor...", "INFO")
                self.after(0, lambda: self.sync_progress.set(0.3))
                self._do_update_all_trays(show_end_message=False)

                # 3. Firebase sync
                if self._check_cancelled():
                    return
                self._sync_log_write("3/4 - Firebase sync...")
                self._log("Firebase senkronizasyonu yapiliyor...", "INFO")
                self.after(0, lambda: self.sync_progress.set(0.6))
                self._do_sync_firebase(show_end_message=False)

                # 4. GitHub push
                if self._check_cancelled():
                    return
                self._sync_log_write("4/4 - GitHub push...")
                self._log("GitHub'a gonderiliyor...", "INFO")
                self.after(0, lambda: self.sync_progress.set(0.9))
                self._do_github_push(show_end_message=False)

                self.after(0, lambda: self.sync_progress.set(1.0))
                self._sync_log_write("Tam senkronizasyon tamamlandi!")
                self._end_operation(True, "Tam senkronizasyon basariyla tamamlandi")
                self.after(0, self._refresh_dashboard)

            except Exception as e:
                self._sync_log_write(f"HATA: {e}")
                self._end_operation(False, f"Sync hatasi: {e}")

        self.current_thread = threading.Thread(target=process, daemon=True)
        self.current_thread.start()

    def _update_all_trays(self):
        """Tum trayleri guncelle"""
        if self.operation_running:
            messagebox.showwarning("Uyari", "Baska bir islem devam ediyor!")
            return

        self._start_operation("Tray Guncelleme")

        def process():
            self._do_update_all_trays(show_end_message=True)

        self.current_thread = threading.Thread(target=process, daemon=True)
        self.current_thread.start()

    def _do_update_all_trays(self, show_end_message: bool = True):
        """Tray guncelleme (thread)"""
        try:
            from core.converter import StickerConverter

            converter = StickerConverter()
            cache = load_cache()

            all_packs = []
            if STICKERS_DIR.exists():
                all_packs.extend([(d, False) for d in STICKERS_DIR.iterdir() if d.is_dir()])
            if PREMIUM_STICKERS_DIR.exists():
                all_packs.extend([(d, True) for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()])

            self._log(f"Toplam {len(all_packs)} paket isleniyor...", "INFO")
            updated_count = 0

            for i, (pack_dir, is_premium) in enumerate(all_packs, 1):
                if self.cancel_operation:
                    self._log(f"Tray guncelleme iptal edildi ({i-1}/{len(all_packs)})", "WARNING")
                    if show_end_message:
                        self._end_operation(False, "Tray guncelleme iptal edildi")
                    return

                pack_name = pack_dir.name
                pack_id = get_pack_id(pack_name)

                self._sync_log_write(f"[{i}/{len(all_packs)}] {pack_name}")
                self._log(f"Tray: [{i}/{len(all_packs)}] {pack_name}", "INFO")

                tray_source = find_tray_source(pack_dir)
                if not tray_source:
                    self._log(f"  -> Tray kaynagi bulunamadi, atlandi", "WARNING")
                    continue

                pack_output = OUTPUT_DIR / pack_id
                pack_output.mkdir(parents=True, exist_ok=True)
                tray_path = pack_output / "tray.webp"

                if converter.create_tray_image(tray_source, tray_path, force_bg_removal=True):
                    updated_count += 1
                    if self.firebase and self.firebase.is_initialized:
                        storage = "premium_stickers" if is_premium else "stickers"
                        url = self.firebase.upload_to_storage(
                            tray_path, f"{storage}/{pack_id}/tray.webp", force_refresh=True
                        )
                        if url:
                            self.firebase.update_pack(pack_id, {"tray_url": url}, is_premium)
                            self._log(f"  -> Firebase'e yuklendi", "SUCCESS")

            save_cache(cache)
            if show_end_message:
                self._end_operation(True, f"Tray guncelleme tamamlandi ({updated_count}/{len(all_packs)} guncellendi)")

        except Exception as e:
            if show_end_message:
                self._end_operation(False, f"Tray hatasi: {e}")

    def _sync_firebase(self):
        """Firebase sync"""
        if self.operation_running:
            messagebox.showwarning("Uyari", "Baska bir islem devam ediyor!")
            return

        if not self.firebase or not self.firebase.is_initialized:
            messagebox.showerror("Hata", "Firebase bagli degil!")
            return

        self._start_operation("Firebase Sync")

        def process():
            self._do_sync_firebase(show_end_message=True)

        self.current_thread = threading.Thread(target=process, daemon=True)
        self.current_thread.start()

    def _do_sync_firebase(self, show_end_message: bool = True):
        """Firebase sync (thread)"""
        try:
            from core.converter import StickerConverter

            converter = StickerConverter()
            cache = load_cache()

            packs = []
            if STICKERS_DIR.exists():
                packs.extend([(d, False) for d in STICKERS_DIR.iterdir() if d.is_dir()])
            if PREMIUM_STICKERS_DIR.exists():
                packs.extend([(d, True) for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()])

            self._log(f"Toplam {len(packs)} paket Firebase'e senkronize edilecek", "INFO")
            synced_count = 0

            for i, (pack_dir, is_premium) in enumerate(packs, 1):
                if self.cancel_operation:
                    self._log(f"Firebase sync iptal edildi ({i-1}/{len(packs)})", "WARNING")
                    if show_end_message:
                        self._end_operation(False, "Firebase sync iptal edildi")
                    return

                self._sync_log_write(f"[{i}/{len(packs)}] {pack_dir.name}")
                self._log(f"Firebase: [{i}/{len(packs)}] {pack_dir.name}", "INFO")

                try:
                    converter.process_pack(pack_dir, OUTPUT_DIR, cache, self.firebase, is_premium=is_premium)
                    synced_count += 1
                    self._log(f"  -> Senkronize edildi", "SUCCESS")
                except Exception as pack_err:
                    self._log(f"  -> Hata: {pack_err}", "ERROR")

            save_cache(cache)
            if show_end_message:
                self._end_operation(True, f"Firebase sync tamamlandi ({synced_count}/{len(packs)})")
            self.after(0, self._refresh_dashboard)

        except Exception as e:
            if show_end_message:
                self._end_operation(False, f"Firebase hatasi: {e}")

    def _backup_drive(self):
        """Drive'a yedekle"""
        if self.operation_running:
            messagebox.showwarning("Uyari", "Baska bir islem devam ediyor!")
            return

        if not self.drive or not self.drive.is_initialized:
            messagebox.showerror("Hata", "Drive bagli degil!")
            return

        self._start_operation("Drive Yedekleme")

        def process():
            try:
                self._log("Drive'a paketler yukleniyor...", "INFO")
                up, skip = self.drive.upload_all_packs(
                    STICKERS_DIR, PREMIUM_STICKERS_DIR,
                    progress_callback=lambda m: (self._sync_log_write(m), self._log(f"Drive: {m}", "INFO"))
                )
                self._end_operation(True, f"Drive yedekleme tamamlandi: {up} yuklendi, {skip} atlandi")
            except Exception as e:
                self._end_operation(False, f"Drive hatasi: {e}")

        self.current_thread = threading.Thread(target=process, daemon=True)
        self.current_thread.start()

    def _download_drive(self):
        """Drive'dan indir"""
        if self.operation_running:
            messagebox.showwarning("Uyari", "Baska bir islem devam ediyor!")
            return

        if not self.drive or not self.drive.is_initialized:
            messagebox.showerror("Hata", "Drive bagli degil!")
            return

        self._start_operation("Drive Indirme")

        def process():
            try:
                self._log("Drive'dan paketler indiriliyor...", "INFO")
                down, skip = self.drive.download_all_packs(
                    STICKERS_DIR, PREMIUM_STICKERS_DIR,
                    progress_callback=lambda m: (self._sync_log_write(m), self._log(f"Drive: {m}", "INFO"))
                )
                self._end_operation(True, f"Drive indirme tamamlandi: {down} indirildi, {skip} atlandi")
                self.after(0, self._load_packs)
            except Exception as e:
                self._end_operation(False, f"Drive hatasi: {e}")

        self.current_thread = threading.Thread(target=process, daemon=True)
        self.current_thread.start()

    def _github_push(self):
        """GitHub push"""
        if self.operation_running:
            messagebox.showwarning("Uyari", "Baska bir islem devam ediyor!")
            return

        if not check_git():
            messagebox.showerror("Hata", "Git yuklu degil!")
            return

        self._start_operation("GitHub Push")

        def process():
            self._do_github_push(show_end_message=True)

        self.current_thread = threading.Thread(target=process, daemon=True)
        self.current_thread.start()

    def _do_github_push(self, show_end_message: bool = True):
        """GitHub push (thread)"""
        try:
            from core.github_sync import GitHubSync

            self._log("Git durumu kontrol ediliyor...", "INFO")
            github = GitHubSync()

            if github.is_git_repo:
                self._log("Degisiklikler commit ediliyor...", "INFO")
                ok, msg = github.sync()
                self._log(f"GitHub: {msg}", "SUCCESS" if ok else "WARNING")
                if show_end_message:
                    self._end_operation(ok, f"GitHub push: {msg}")
            else:
                self._log("Git repo bulunamadi", "WARNING")
                if show_end_message:
                    self._end_operation(False, "Git repo bulunamadi")
        except Exception as e:
            self._log(f"GitHub hatasi: {e}", "ERROR")
            if show_end_message:
                self._end_operation(False, f"GitHub hatasi: {e}")

    # ========================================================================
    # ISTATISTIK ISLEMLERI
    # ========================================================================

    def _load_stats(self):
        """Detayli istatistikleri yukle"""
        if not self.firebase or not self.firebase.is_initialized:
            return

        def fetch():
            try:
                stats = self.firebase.get_statistics()
                self.after(0, lambda: self._render_stats_table(stats))
            except Exception as e:
                self.after(0, lambda: self._log(f"Istatistik hatasi: {e}", "ERROR"))

        threading.Thread(target=fetch, daemon=True).start()

    def _render_stats_table(self, stats):
        """Istatistik tablosunu render et"""
        for w in self.stats_scroll.winfo_children():
            w.destroy()

        packs = sorted(stats.get("pack_details", []),
                      key=lambda x: x.get("downloads", 0), reverse=True)

        for p in packs:
            row = ctk.CTkFrame(self.stats_scroll, fg_color=COLORS["bg_card"],
                              corner_radius=6, height=35)
            row.pack(fill="x", pady=2)
            row.pack_propagate(False)

            # Paket adi
            ctk.CTkLabel(row, text=p.get("name", "?")[:25],
                        font=ctk.CTkFont(size=10),
                        text_color=COLORS["text_main"],
                        anchor="w").pack(side="left", padx=15, fill="x", expand=True)

            # Tip
            tip = "P" if p.get("is_premium") else "N"
            tip_color = COLORS["gold"] if p.get("is_premium") else COLORS["text_sec"]
            ctk.CTkLabel(row, text=tip, font=ctk.CTkFont(size=10),
                        text_color=tip_color, width=40).pack(side="left", padx=5)

            # Kategori
            cat = CATEGORIES.get(p.get("category", ""), "-")[:8]
            ctk.CTkLabel(row, text=cat, font=ctk.CTkFont(size=10),
                        text_color=COLORS["text_sec"], width=60).pack(side="left", padx=5)

            # Sayilar
            for val in [p.get("sticker_count", 0), p.get("downloads", 0),
                       p.get("display_total", 0), p.get("favorites", 0)]:
                ctk.CTkLabel(row, text=str(val), font=ctk.CTkFont(size=10),
                            text_color=COLORS["text_main"], width=50).pack(side="left", padx=5)

    def _export_stats_csv(self):
        """Istatistikleri CSV'ye aktar"""
        filepath = filedialog.asksaveasfilename(
            defaultextension=".csv",
            filetypes=[("CSV", "*.csv")],
            initialfile=f"sticly_stats_{datetime.now().strftime('%Y%m%d')}.csv"
        )

        if filepath and self.stats_data:
            try:
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write("Paket,Tip,Kategori,Sticker,Indirme,Gosterim,Favori\n")
                    for p in self.stats_data.get("pack_details", []):
                        tip = "Premium" if p.get("is_premium") else "Normal"
                        cat = CATEGORIES.get(p.get("category", ""), "-")
                        f.write(f"{p.get('name', '')},{tip},{cat},{p.get('sticker_count', 0)},"
                               f"{p.get('downloads', 0)},{p.get('display_total', 0)},"
                               f"{p.get('favorites', 0)}\n")

                self._log(f"CSV kaydedildi: {filepath}", "SUCCESS")
                messagebox.showinfo("Basarili", f"Kaydedildi: {filepath}")
            except Exception as e:
                self._log(f"CSV hatasi: {e}", "ERROR")

    # ========================================================================
    # AYARLAR ISLEMLERI
    # ========================================================================

    def _select_firebase_key(self):
        """Firebase key dosyasi sec"""
        file = filedialog.askopenfilename(
            title="Firebase Admin SDK Key Sec",
            filetypes=[("JSON", "*.json")]
        )

        if file:
            src = Path(file)
            dest = SCRIPT_DIR / src.name
            shutil.copy(src, dest)
            self._log(f"Firebase key kopyalandi: {src.name}", "SUCCESS")
            self._init_firebase()
            self._check_requirements()

    def _select_credentials(self):
        """Credentials dosyasi sec"""
        file = filedialog.askopenfilename(
            title="Google OAuth Credentials Sec",
            filetypes=[("JSON", "*.json")]
        )

        if file:
            src = Path(file)
            dest = CREDENTIALS_FILE
            shutil.copy(src, dest)
            self._log("credentials.json kopyalandi", "SUCCESS")
            self._init_drive()
            self._check_requirements()


# ============================================================================
# ANA GIRIS NOKTASI
# ============================================================================

def main():
    """Uygulamayi baslat"""
    app = SticlyToolkit()
    app.mainloop()


if __name__ == "__main__":
    main()
