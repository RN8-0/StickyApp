"""
Sticly Manager - Dashboard
==========================
Ana sayfa istatistik ve hizli erisim paneli.
"""

import customtkinter as ctk
from typing import Optional, Dict, Callable
import logging
import threading

from .theme import SticlyTheme, CATEGORY_NAMES, CATEGORY_COLORS

logger = logging.getLogger("sticly")


class StatCard(ctk.CTkFrame):
    """Istatistik karti bilesei"""

    def __init__(
        self,
        master,
        title: str,
        value: str = "0",
        subtitle: str = "",
        icon: str = "",
        color: str = None,
        **kwargs
    ):
        super().__init__(master, **kwargs)

        colors = SticlyTheme.get_colors(True)
        self.configure(
            fg_color=colors["bg_secondary"],
            corner_radius=12
        )

        # Icon/Renk gostergesi
        if color:
            self.indicator = ctk.CTkFrame(
                self,
                width=4,
                corner_radius=2,
                fg_color=color
            )
            self.indicator.pack(side="left", fill="y", padx=(0, 10), pady=10)

        # Icerik
        self.content = ctk.CTkFrame(self, fg_color="transparent")
        self.content.pack(fill="both", expand=True, padx=15, pady=15)

        # Baslik
        self.title_label = ctk.CTkLabel(
            self.content,
            text=title,
            font=SticlyTheme.FONT_SMALL,
            text_color=colors["text_secondary"]
        )
        self.title_label.pack(anchor="w")

        # Deger
        self.value_label = ctk.CTkLabel(
            self.content,
            text=value,
            font=SticlyTheme.FONT_TITLE,
            text_color=colors["text"]
        )
        self.value_label.pack(anchor="w", pady=(5, 0))

        # Alt baslik
        if subtitle:
            self.subtitle_label = ctk.CTkLabel(
                self.content,
                text=subtitle,
                font=SticlyTheme.FONT_SMALL,
                text_color=colors["text_secondary"]
            )
            self.subtitle_label.pack(anchor="w", pady=(5, 0))

    def set_value(self, value: str):
        """Degeri guncelle"""
        self.value_label.configure(text=value)

    def set_subtitle(self, text: str):
        """Alt basligi guncelle"""
        if hasattr(self, 'subtitle_label'):
            self.subtitle_label.configure(text=text)


class QuickActionButton(ctk.CTkButton):
    """Hizli islem butonu"""

    def __init__(
        self,
        master,
        text: str,
        description: str = "",
        icon: str = "",
        **kwargs
    ):
        super().__init__(
            master,
            text=text,
            height=60,
            anchor="w",
            corner_radius=10,
            font=SticlyTheme.FONT_BODY,
            **kwargs
        )


class DashboardFrame(ctk.CTkFrame):
    """Dashboard ana frame"""

    def __init__(self, master, app_controller=None, **kwargs):
        super().__init__(master, **kwargs)

        self.app = app_controller
        self.colors = SticlyTheme.get_colors(True)
        self.configure(fg_color="transparent")

        # Grid yapilandirmasi
        self.grid_columnconfigure(0, weight=1)
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(2, weight=1)

        self._create_header()
        self._create_stats_section()
        self._create_quick_actions()
        self._create_recent_activity()

    def _create_header(self):
        """Baslik bolumu"""
        self.header = ctk.CTkFrame(self, fg_color="transparent")
        self.header.grid(row=0, column=0, columnspan=2, sticky="ew", pady=(0, 20))

        self.title = ctk.CTkLabel(
            self.header,
            text="Dashboard",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        self.title.pack(side="left")

        # Yenile butonu
        self.refresh_btn = ctk.CTkButton(
            self.header,
            text="Yenile",
            width=100,
            height=32,
            font=SticlyTheme.FONT_SMALL,
            fg_color=SticlyTheme.PRIMARY,
            hover_color=SticlyTheme.PRIMARY_HOVER,
            command=self._refresh_stats
        )
        self.refresh_btn.pack(side="right")

    def _create_stats_section(self):
        """Istatistik kartlari"""
        self.stats_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.stats_frame.grid(row=1, column=0, columnspan=2, sticky="ew", pady=(0, 20))

        # Grid yapilandirmasi
        for i in range(4):
            self.stats_frame.grid_columnconfigure(i, weight=1)

        # Istatistik kartlari
        self.total_packs_card = StatCard(
            self.stats_frame,
            title="Toplam Paket",
            value="--",
            color=SticlyTheme.PRIMARY
        )
        self.total_packs_card.grid(row=0, column=0, padx=5, pady=5, sticky="nsew")

        self.total_stickers_card = StatCard(
            self.stats_frame,
            title="Toplam Sticker",
            value="--",
            color=SticlyTheme.ACCENT
        )
        self.total_stickers_card.grid(row=0, column=1, padx=5, pady=5, sticky="nsew")

        self.total_downloads_card = StatCard(
            self.stats_frame,
            title="Toplam Indirme",
            value="--",
            subtitle="Gercek sayi",
            color=SticlyTheme.SUCCESS
        )
        self.total_downloads_card.grid(row=0, column=2, padx=5, pady=5, sticky="nsew")

        self.total_views_card = StatCard(
            self.stats_frame,
            title="Goruntulenme",
            value="--",
            color=SticlyTheme.WARNING
        )
        self.total_views_card.grid(row=0, column=3, padx=5, pady=5, sticky="nsew")

    def _create_quick_actions(self):
        """Hizli islem butonlari"""
        self.actions_frame = ctk.CTkFrame(
            self,
            fg_color=self.colors["bg_secondary"],
            corner_radius=12
        )
        self.actions_frame.grid(row=2, column=0, sticky="nsew", padx=(0, 10), pady=5)

        self.actions_title = ctk.CTkLabel(
            self.actions_frame,
            text="Hizli Islemler",
            font=SticlyTheme.FONT_SUBHEADER,
            text_color=self.colors["text"]
        )
        self.actions_title.pack(anchor="w", padx=15, pady=(15, 10))

        # Butonlar
        actions = [
            ("Tray Guncelle", self._on_update_trays, SticlyTheme.PRIMARY),
            ("Firebase Senkronize", self._on_sync_firebase, SticlyTheme.ACCENT),
            ("Drive Yedekle", self._on_backup_drive, SticlyTheme.SUCCESS),
            ("GitHub Push", self._on_github_push, SticlyTheme.SECONDARY),
            ("Tam Senkronizasyon", self._on_full_sync, SticlyTheme.WARNING),
        ]

        for text, command, color in actions:
            btn = ctk.CTkButton(
                self.actions_frame,
                text=text,
                height=45,
                font=SticlyTheme.FONT_BODY,
                fg_color=color,
                hover_color=SticlyTheme.PRIMARY_HOVER,
                command=command
            )
            btn.pack(fill="x", padx=15, pady=5)

        # Alt bosluk
        ctk.CTkFrame(self.actions_frame, height=10, fg_color="transparent").pack()

    def _create_recent_activity(self):
        """Son aktiviteler"""
        self.activity_frame = ctk.CTkFrame(
            self,
            fg_color=self.colors["bg_secondary"],
            corner_radius=12
        )
        self.activity_frame.grid(row=2, column=1, sticky="nsew", padx=(10, 0), pady=5)

        self.activity_title = ctk.CTkLabel(
            self.activity_frame,
            text="En Populer Paketler",
            font=SticlyTheme.FONT_SUBHEADER,
            text_color=self.colors["text"]
        )
        self.activity_title.pack(anchor="w", padx=15, pady=(15, 10))

        # Liste alani
        self.activity_list = ctk.CTkScrollableFrame(
            self.activity_frame,
            fg_color="transparent"
        )
        self.activity_list.pack(fill="both", expand=True, padx=10, pady=(0, 10))

        # Placeholder
        self.activity_placeholder = ctk.CTkLabel(
            self.activity_list,
            text="Yukleniyor...",
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        self.activity_placeholder.pack(pady=20)

    def _refresh_stats(self):
        """Istatistikleri yenile"""
        if self.app and hasattr(self.app, 'firebase'):
            # Arayuzu guncelle
            self.refresh_btn.configure(state="disabled", text="Yukleniyor...")

            def fetch():
                try:
                    stats = self.app.firebase.get_statistics()
                    self.after(0, lambda: self._update_stats_ui(stats))
                except Exception as e:
                    logger.error(f"Istatistik alinamadi: {e}")
                finally:
                    self.after(0, lambda: self.refresh_btn.configure(
                        state="normal", text="Yenile"
                    ))

            thread = threading.Thread(target=fetch, daemon=True)
            thread.start()

    def _update_stats_ui(self, stats: Dict):
        """Istatistik arayuzunu guncelle"""
        self.total_packs_card.set_value(str(stats.get("total_packs", 0)))
        self.total_stickers_card.set_value(str(stats.get("total_stickers", 0)))
        self.total_downloads_card.set_value(str(stats.get("total_downloads", 0)))
        self.total_views_card.set_value(str(stats.get("total_views", 0)))

        # En populer paketleri goster
        self._update_popular_packs(stats.get("top_downloaded", []))

    def _update_popular_packs(self, packs: list):
        """Populer paketleri guncelle"""
        # Mevcut icerik temizle
        for widget in self.activity_list.winfo_children():
            widget.destroy()

        if not packs:
            label = ctk.CTkLabel(
                self.activity_list,
                text="Veri bulunamadi",
                font=SticlyTheme.FONT_SMALL,
                text_color=self.colors["text_secondary"]
            )
            label.pack(pady=20)
            return

        for i, pack in enumerate(packs[:10], 1):
            item_frame = ctk.CTkFrame(
                self.activity_list,
                fg_color=self.colors["bg_tertiary"],
                corner_radius=8,
                height=50
            )
            item_frame.pack(fill="x", pady=3)
            item_frame.pack_propagate(False)

            # Siralama
            rank_label = ctk.CTkLabel(
                item_frame,
                text=f"#{i}",
                font=SticlyTheme.FONT_SMALL,
                text_color=SticlyTheme.PRIMARY,
                width=30
            )
            rank_label.pack(side="left", padx=10)

            # Paket adi
            name_label = ctk.CTkLabel(
                item_frame,
                text=pack.get("name", "?")[:25],
                font=SticlyTheme.FONT_BODY,
                text_color=self.colors["text"],
                anchor="w"
            )
            name_label.pack(side="left", fill="x", expand=True)

            # Premium etiketi
            if pack.get("is_premium"):
                premium_label = ctk.CTkLabel(
                    item_frame,
                    text="P",
                    font=SticlyTheme.FONT_SMALL,
                    text_color=SticlyTheme.PREMIUM_GOLD,
                    width=20
                )
                premium_label.pack(side="right", padx=5)

            # Indirme sayisi
            download_label = ctk.CTkLabel(
                item_frame,
                text=str(pack.get("downloads", 0)),
                font=SticlyTheme.FONT_SMALL,
                text_color=SticlyTheme.SUCCESS
            )
            download_label.pack(side="right", padx=10)

    def _on_update_trays(self):
        """Tray guncelleme"""
        if self.app:
            self.app.run_tray_update()

    def _on_sync_firebase(self):
        """Firebase senkronizasyon"""
        if self.app:
            self.app.run_firebase_sync()

    def _on_backup_drive(self):
        """Drive yedekleme"""
        if self.app:
            self.app.run_drive_backup()

    def _on_github_push(self):
        """GitHub push"""
        if self.app:
            self.app.run_github_sync()

    def _on_full_sync(self):
        """Tam senkronizasyon"""
        if self.app:
            self.app.run_full_sync()

    def refresh(self):
        """Sayfa yenilendiginde cagrilir"""
        self._refresh_stats()
