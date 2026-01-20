"""
Sticly Manager - Ana Pencere
============================
Sidebar navigasyonlu ana uygulama penceresi.
"""

import customtkinter as ctk
from typing import Optional, Callable
import logging

from .theme import SticlyTheme

logger = logging.getLogger("sticly")


class MainWindow(ctk.CTk):
    """Ana uygulama penceresi"""

    def __init__(self):
        super().__init__()

        # Tema ayarla
        SticlyTheme.setup("dark")

        # Pencere ayarlari
        self.title("Sticly Sticker Manager")
        self.geometry("1200x800")
        self.minsize(1000, 700)

        # Renkler
        self.colors = SticlyTheme.get_colors(True)

        # Arka plan rengi
        self.configure(fg_color=self.colors["bg"])

        # Grid yapilandirmasi
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        # Mevcut frame
        self.current_frame: Optional[ctk.CTkFrame] = None
        self.current_page = "dashboard"

        # Frames dict
        self.frames = {}

        # Callback'ler
        self.on_sync_callback: Optional[Callable] = None
        self.on_settings_callback: Optional[Callable] = None

        # Sidebar olustur
        self._create_sidebar()

        # Status bar olustur
        self._create_status_bar()

        # Content area olustur
        self._create_content_area()

    def _create_sidebar(self):
        """Sol sidebar olustur"""
        self.sidebar = ctk.CTkFrame(
            self,
            width=220,
            corner_radius=0,
            fg_color=self.colors["bg_secondary"]
        )
        self.sidebar.grid(row=0, column=0, rowspan=2, sticky="nsew")
        self.sidebar.grid_propagate(False)

        # Logo / Baslik
        self.logo_frame = ctk.CTkFrame(
            self.sidebar,
            fg_color="transparent"
        )
        self.logo_frame.pack(fill="x", padx=15, pady=(20, 10))

        self.logo_label = ctk.CTkLabel(
            self.logo_frame,
            text="Sticly",
            font=SticlyTheme.FONT_TITLE,
            text_color=SticlyTheme.PRIMARY
        )
        self.logo_label.pack(anchor="w")

        self.subtitle_label = ctk.CTkLabel(
            self.logo_frame,
            text="Sticker Manager",
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        self.subtitle_label.pack(anchor="w")

        # Ayirici cizgi
        self.separator = ctk.CTkFrame(
            self.sidebar,
            height=1,
            fg_color=self.colors["bg_tertiary"]
        )
        self.separator.pack(fill="x", padx=15, pady=15)

        # Menu butonlari
        self.nav_buttons = {}
        nav_items = [
            ("dashboard", "Dashboard", self._show_dashboard),
            ("packs", "Paketler", self._show_packs),
            ("sync", "Senkronize", self._show_sync),
            ("stats", "Istatistik", self._show_stats),
            ("settings", "Ayarlar", self._show_settings),
        ]

        for key, text, command in nav_items:
            btn = ctk.CTkButton(
                self.sidebar,
                text=f"  {text}",
                font=SticlyTheme.FONT_BODY,
                height=40,
                anchor="w",
                corner_radius=8,
                fg_color="transparent",
                text_color=self.colors["text"],
                hover_color=self.colors["bg_tertiary"],
                command=command
            )
            btn.pack(fill="x", padx=10, pady=2)
            self.nav_buttons[key] = btn

        # Alt kisim
        self.sidebar_bottom = ctk.CTkFrame(
            self.sidebar,
            fg_color="transparent"
        )
        self.sidebar_bottom.pack(side="bottom", fill="x", padx=15, pady=15)

        # Tema degistirme
        self.theme_switch = ctk.CTkSwitch(
            self.sidebar_bottom,
            text="Koyu Tema",
            font=SticlyTheme.FONT_SMALL,
            command=self._toggle_theme,
            progress_color=SticlyTheme.PRIMARY
        )
        self.theme_switch.pack(anchor="w", pady=5)
        self.theme_switch.select()  # Default koyu tema

        # Versiyon
        self.version_label = ctk.CTkLabel(
            self.sidebar_bottom,
            text="v4.0.0 - GUI Edition",
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        self.version_label.pack(anchor="w", pady=(10, 0))

        # Default secili
        self._select_nav_button("dashboard")

    def _create_status_bar(self):
        """Alt durum cubugu olustur"""
        self.status_bar = ctk.CTkFrame(
            self,
            height=35,
            corner_radius=0,
            fg_color=self.colors["bg_secondary"]
        )
        self.status_bar.grid(row=1, column=1, sticky="ew")

        # Status label
        self.status_label = ctk.CTkLabel(
            self.status_bar,
            text="Hazir",
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        self.status_label.pack(side="left", padx=15, pady=5)

        # Progress bar (gizli)
        self.progress_bar = ctk.CTkProgressBar(
            self.status_bar,
            width=200,
            height=8,
            progress_color=SticlyTheme.PRIMARY
        )
        self.progress_bar.set(0)
        # Baslangicta gizli

        # Firebase durumu
        self.firebase_status = ctk.CTkLabel(
            self.status_bar,
            text="Firebase: --",
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        self.firebase_status.pack(side="right", padx=15, pady=5)

        # Drive durumu
        self.drive_status = ctk.CTkLabel(
            self.status_bar,
            text="Drive: --",
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        self.drive_status.pack(side="right", padx=15, pady=5)

    def _create_content_area(self):
        """Ana icerik alani olustur"""
        self.content_frame = ctk.CTkFrame(
            self,
            corner_radius=0,
            fg_color=self.colors["bg"]
        )
        self.content_frame.grid(row=0, column=1, sticky="nsew")
        self.content_frame.grid_columnconfigure(0, weight=1)
        self.content_frame.grid_rowconfigure(0, weight=1)

    def _select_nav_button(self, key: str):
        """Navigasyon butonunu sec"""
        for btn_key, btn in self.nav_buttons.items():
            if btn_key == key:
                btn.configure(
                    fg_color=SticlyTheme.PRIMARY,
                    text_color="#FFFFFF"
                )
            else:
                btn.configure(
                    fg_color="transparent",
                    text_color=self.colors["text"]
                )
        self.current_page = key

    def _show_dashboard(self):
        """Dashboard sayfasini goster"""
        self._select_nav_button("dashboard")
        self._show_frame("dashboard")

    def _show_packs(self):
        """Paketler sayfasini goster"""
        self._select_nav_button("packs")
        self._show_frame("packs")

    def _show_sync(self):
        """Senkronize sayfasini goster"""
        self._select_nav_button("sync")
        self._show_frame("sync")

    def _show_stats(self):
        """Istatistik sayfasini goster"""
        self._select_nav_button("stats")
        self._show_frame("stats")

    def _show_settings(self):
        """Ayarlar sayfasini goster"""
        self._select_nav_button("settings")
        self._show_frame("settings")

    def _show_frame(self, frame_key: str):
        """Frame goster"""
        # Mevcut frame'i gizle
        if self.current_frame:
            self.current_frame.grid_forget()

        # Yeni frame'i goster
        if frame_key in self.frames:
            frame = self.frames[frame_key]
            frame.grid(row=0, column=0, sticky="nsew", padx=20, pady=20)
            self.current_frame = frame

            # Frame'e refresh cagir
            if hasattr(frame, 'refresh'):
                frame.refresh()

    def _toggle_theme(self):
        """Tema degistir"""
        is_dark = self.theme_switch.get()
        appearance = "dark" if is_dark else "light"
        ctk.set_appearance_mode(appearance)
        self.colors = SticlyTheme.get_colors(is_dark)

        # Arka plan renklerini guncelle
        self.configure(fg_color=self.colors["bg"])
        self.sidebar.configure(fg_color=self.colors["bg_secondary"])
        self.content_frame.configure(fg_color=self.colors["bg"])
        self.status_bar.configure(fg_color=self.colors["bg_secondary"])

    def register_frame(self, key: str, frame: ctk.CTkFrame):
        """Frame kaydet"""
        self.frames[key] = frame

    def set_status(self, text: str, status_type: str = "info"):
        """Durum metnini ayarla"""
        color = SticlyTheme.get_status_color(status_type)
        self.status_label.configure(text=text, text_color=color)

    def show_progress(self, show: bool = True):
        """Progress bar goster/gizle"""
        if show:
            self.progress_bar.pack(side="left", padx=15, pady=5)
        else:
            self.progress_bar.pack_forget()
            self.progress_bar.set(0)

    def set_progress(self, value: float):
        """Progress degerini ayarla (0-1)"""
        self.progress_bar.set(value)

    def update_firebase_status(self, connected: bool):
        """Firebase durumunu guncelle"""
        if connected:
            self.firebase_status.configure(
                text="Firebase: Bagli",
                text_color=SticlyTheme.SUCCESS
            )
        else:
            self.firebase_status.configure(
                text="Firebase: Bagli Degil",
                text_color=SticlyTheme.ERROR
            )

    def update_drive_status(self, connected: bool):
        """Drive durumunu guncelle"""
        if connected:
            self.drive_status.configure(
                text="Drive: Bagli",
                text_color=SticlyTheme.SUCCESS
            )
        else:
            self.drive_status.configure(
                text="Drive: Bagli Degil",
                text_color=SticlyTheme.ERROR
            )

    def show_message(self, title: str, message: str, msg_type: str = "info"):
        """Mesaj dialog goster"""
        from tkinter import messagebox

        if msg_type == "error":
            messagebox.showerror(title, message)
        elif msg_type == "warning":
            messagebox.showwarning(title, message)
        else:
            messagebox.showinfo(title, message)

    def ask_confirmation(self, title: str, message: str) -> bool:
        """Onay dialog goster"""
        from tkinter import messagebox
        return messagebox.askyesno(title, message)
