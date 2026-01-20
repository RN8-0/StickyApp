"""
Sticly Manager - Debug Konsolu
==============================
Gercek zamanli log ve hata ayiklama.
"""

import customtkinter as ctk
from datetime import datetime
from typing import List, Optional
import logging

from .theme import SticlyTheme

logger = logging.getLogger("sticly")


class DebugConsoleFrame(ctk.CTkFrame):
    """Debug konsolu frame"""

    def __init__(self, master, app_controller=None, **kwargs):
        super().__init__(master, **kwargs)

        self.app = app_controller
        self.colors = SticlyTheme.get_colors(True)
        self.configure(fg_color="transparent")

        self.log_entries: List[dict] = []
        self.filter_level = "ALL"  # ALL, INFO, WARNING, ERROR

        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        self._create_header()
        self._create_console()
        self._create_status_panel()

    def _create_header(self):
        """Baslik ve kontroller"""
        self.header = ctk.CTkFrame(self, fg_color="transparent")
        self.header.grid(row=0, column=0, sticky="ew", pady=(0, 10))

        self.title = ctk.CTkLabel(
            self.header,
            text="Debug Konsolu",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        self.title.pack(side="left")

        # Kontroller
        controls = ctk.CTkFrame(self.header, fg_color="transparent")
        controls.pack(side="right")

        # Filtre
        self.filter_menu = ctk.CTkOptionMenu(
            controls,
            values=["Tum Loglar", "INFO", "WARNING", "ERROR"],
            width=120,
            height=32,
            font=SticlyTheme.FONT_SMALL,
            fg_color=self.colors["bg_secondary"],
            command=self._on_filter_change
        )
        self.filter_menu.pack(side="left", padx=5)

        # Temizle
        self.clear_btn = ctk.CTkButton(
            controls,
            text="Temizle",
            width=80,
            height=32,
            font=SticlyTheme.FONT_SMALL,
            fg_color=self.colors["bg_secondary"],
            hover_color=self.colors["bg_tertiary"],
            command=self._clear_logs
        )
        self.clear_btn.pack(side="left", padx=5)

        # Dosyaya kaydet
        self.save_btn = ctk.CTkButton(
            controls,
            text="Kaydet",
            width=80,
            height=32,
            font=SticlyTheme.FONT_SMALL,
            fg_color=SticlyTheme.PRIMARY,
            hover_color=SticlyTheme.PRIMARY_HOVER,
            command=self._save_logs
        )
        self.save_btn.pack(side="left", padx=5)

    def _create_console(self):
        """Konsol alani"""
        self.console_frame = ctk.CTkFrame(
            self,
            fg_color=self.colors["bg_secondary"],
            corner_radius=8
        )
        self.console_frame.grid(row=1, column=0, sticky="nsew", pady=(0, 10))
        self.console_frame.grid_columnconfigure(0, weight=1)
        self.console_frame.grid_rowconfigure(0, weight=1)

        # Text widget
        self.console_text = ctk.CTkTextbox(
            self.console_frame,
            font=SticlyTheme.FONT_MONO,
            fg_color=self.colors["bg_secondary"],
            text_color=self.colors["text"],
            state="disabled",
            wrap="word"
        )
        self.console_text.grid(row=0, column=0, sticky="nsew", padx=5, pady=5)

        # Tag renkleri ayarla
        self.console_text._textbox.tag_config("INFO", foreground=SticlyTheme.ACCENT)
        self.console_text._textbox.tag_config("WARNING", foreground=SticlyTheme.WARNING)
        self.console_text._textbox.tag_config("ERROR", foreground=SticlyTheme.ERROR)
        self.console_text._textbox.tag_config("DEBUG", foreground=self.colors["text_secondary"])
        self.console_text._textbox.tag_config("SUCCESS", foreground=SticlyTheme.SUCCESS)
        self.console_text._textbox.tag_config("TIME", foreground=self.colors["text_secondary"])

    def _create_status_panel(self):
        """Durum paneli"""
        self.status_panel = ctk.CTkFrame(
            self,
            fg_color=self.colors["bg_secondary"],
            corner_radius=8,
            height=100
        )
        self.status_panel.grid(row=2, column=0, sticky="ew")
        self.status_panel.grid_propagate(False)

        # Grid yapilandirmasi
        self.status_panel.grid_columnconfigure(0, weight=1)
        self.status_panel.grid_columnconfigure(1, weight=1)
        self.status_panel.grid_columnconfigure(2, weight=1)

        # Firebase durumu
        self.firebase_card = self._create_status_card(
            self.status_panel, "Firebase", "Baglaniyor...", 0
        )

        # Drive durumu
        self.drive_card = self._create_status_card(
            self.status_panel, "Google Drive", "Baglaniyor...", 1
        )

        # Git durumu
        self.git_card = self._create_status_card(
            self.status_panel, "GitHub", "Kontrol ediliyor...", 2
        )

    def _create_status_card(
        self,
        parent,
        title: str,
        status: str,
        column: int
    ) -> dict:
        """Durum karti olustur"""
        frame = ctk.CTkFrame(parent, fg_color="transparent")
        frame.grid(row=0, column=column, padx=15, pady=15, sticky="nsew")

        title_label = ctk.CTkLabel(
            frame,
            text=title,
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        title_label.pack(anchor="w")

        status_label = ctk.CTkLabel(
            frame,
            text=status,
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text"]
        )
        status_label.pack(anchor="w", pady=(5, 0))

        indicator = ctk.CTkFrame(
            frame,
            width=10,
            height=10,
            corner_radius=5,
            fg_color=self.colors["text_secondary"]
        )
        indicator.place(relx=1.0, rely=0, anchor="ne")

        return {
            "frame": frame,
            "status_label": status_label,
            "indicator": indicator
        }

    def _on_filter_change(self, value: str):
        """Filtre degistiginde"""
        if value == "Tum Loglar":
            self.filter_level = "ALL"
        else:
            self.filter_level = value
        self._refresh_console()

    def _clear_logs(self):
        """Loglari temizle"""
        self.log_entries.clear()
        self.console_text.configure(state="normal")
        self.console_text.delete("1.0", "end")
        self.console_text.configure(state="disabled")

    def _save_logs(self):
        """Loglari dosyaya kaydet"""
        from tkinter import filedialog

        filepath = filedialog.asksaveasfilename(
            defaultextension=".log",
            filetypes=[("Log dosyasi", "*.log"), ("Text dosyasi", "*.txt")],
            initialfile=f"sticly_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
        )

        if filepath:
            try:
                with open(filepath, "w", encoding="utf-8") as f:
                    for entry in self.log_entries:
                        f.write(f"[{entry['time']}] [{entry['level']}] {entry['message']}\n")
                self.log("INFO", f"Loglar kaydedildi: {filepath}")
            except Exception as e:
                self.log("ERROR", f"Log kaydetme hatasi: {e}")

    def _refresh_console(self):
        """Konsolu filtreye gore yenile"""
        self.console_text.configure(state="normal")
        self.console_text.delete("1.0", "end")

        for entry in self.log_entries:
            if self.filter_level == "ALL" or entry["level"] == self.filter_level:
                self._insert_log_entry(entry)

        self.console_text.configure(state="disabled")
        self.console_text.see("end")

    def _insert_log_entry(self, entry: dict):
        """Log girisini konsola ekle"""
        time_str = f"[{entry['time']}] "
        level_str = f"[{entry['level']}] "
        message = entry['message'] + "\n"

        self.console_text._textbox.insert("end", time_str, "TIME")
        self.console_text._textbox.insert("end", level_str, entry['level'])
        self.console_text._textbox.insert("end", message)

    def log(self, level: str, message: str):
        """Log ekle"""
        entry = {
            "time": datetime.now().strftime("%H:%M:%S"),
            "level": level.upper(),
            "message": message
        }
        self.log_entries.append(entry)

        # Filtreye uygunsa konsola ekle
        if self.filter_level == "ALL" or entry["level"] == self.filter_level:
            self.console_text.configure(state="normal")
            self._insert_log_entry(entry)
            self.console_text.configure(state="disabled")
            self.console_text.see("end")

    def update_firebase_status(self, connected: bool, message: str = None):
        """Firebase durumunu guncelle"""
        if connected:
            self.firebase_card["status_label"].configure(
                text=message or "Bagli",
                text_color=SticlyTheme.SUCCESS
            )
            self.firebase_card["indicator"].configure(fg_color=SticlyTheme.SUCCESS)
        else:
            self.firebase_card["status_label"].configure(
                text=message or "Bagli Degil",
                text_color=SticlyTheme.ERROR
            )
            self.firebase_card["indicator"].configure(fg_color=SticlyTheme.ERROR)

    def update_drive_status(self, connected: bool, message: str = None):
        """Drive durumunu guncelle"""
        if connected:
            self.drive_card["status_label"].configure(
                text=message or "Bagli",
                text_color=SticlyTheme.SUCCESS
            )
            self.drive_card["indicator"].configure(fg_color=SticlyTheme.SUCCESS)
        else:
            self.drive_card["status_label"].configure(
                text=message or "Bagli Degil",
                text_color=SticlyTheme.ERROR
            )
            self.drive_card["indicator"].configure(fg_color=SticlyTheme.ERROR)

    def update_git_status(self, has_changes: bool, message: str = None):
        """Git durumunu guncelle"""
        if has_changes:
            self.git_card["status_label"].configure(
                text=message or "Degisiklik var",
                text_color=SticlyTheme.WARNING
            )
            self.git_card["indicator"].configure(fg_color=SticlyTheme.WARNING)
        else:
            self.git_card["status_label"].configure(
                text=message or "Guncel",
                text_color=SticlyTheme.SUCCESS
            )
            self.git_card["indicator"].configure(fg_color=SticlyTheme.SUCCESS)

    def refresh(self):
        """Sayfa yenilendiginde"""
        if self.app:
            # Durumlari kontrol et
            if hasattr(self.app, 'firebase') and self.app.firebase:
                self.update_firebase_status(self.app.firebase.is_initialized)

            if hasattr(self.app, 'drive') and self.app.drive:
                self.update_drive_status(self.app.drive.is_initialized)

            if hasattr(self.app, 'github') and self.app.github:
                has_changes, _, _ = self.app.github.get_status()
                self.update_git_status(has_changes)


class SyncFrame(ctk.CTkFrame):
    """Senkronizasyon frame"""

    def __init__(self, master, app_controller=None, **kwargs):
        super().__init__(master, **kwargs)

        self.app = app_controller
        self.colors = SticlyTheme.get_colors(True)
        self.configure(fg_color="transparent")

        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        self._create_header()
        self._create_content()

    def _create_header(self):
        """Baslik"""
        self.header = ctk.CTkFrame(self, fg_color="transparent")
        self.header.grid(row=0, column=0, sticky="ew", pady=(0, 15))

        self.title = ctk.CTkLabel(
            self.header,
            text="Senkronizasyon",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        self.title.pack(side="left")

    def _create_content(self):
        """Icerik"""
        self.content = ctk.CTkFrame(self, fg_color="transparent")
        self.content.grid(row=1, column=0, sticky="nsew")

        self.content.grid_columnconfigure(0, weight=1)
        self.content.grid_columnconfigure(1, weight=1)

        # Sol: Senkronizasyon secenekleri
        self.options_frame = ctk.CTkFrame(
            self.content,
            fg_color=self.colors["bg_secondary"],
            corner_radius=12
        )
        self.options_frame.grid(row=0, column=0, sticky="nsew", padx=(0, 10), pady=5)

        options_title = ctk.CTkLabel(
            self.options_frame,
            text="Islem Sec",
            font=SticlyTheme.FONT_SUBHEADER,
            text_color=self.colors["text"]
        )
        options_title.pack(anchor="w", padx=15, pady=(15, 10))

        sync_options = [
            ("Tray Guncelle", "Tum paketlerin tray resimlerini guncelle", self._sync_trays),
            ("Firebase Senkronize", "Yerel paketleri Firebase'e yukle", self._sync_firebase),
            ("Drive'a Yedekle", "Paketleri Google Drive'a yedekle", self._sync_drive_upload),
            ("Drive'dan Indir", "Paketleri Google Drive'dan indir", self._sync_drive_download),
            ("GitHub Push", "Degisiklikleri GitHub'a gonder", self._sync_github),
            ("Tam Senkronizasyon", "Tum islemleri sirayla yap", self._full_sync),
        ]

        for text, desc, command in sync_options:
            btn_frame = ctk.CTkFrame(self.options_frame, fg_color="transparent")
            btn_frame.pack(fill="x", padx=15, pady=5)

            btn = ctk.CTkButton(
                btn_frame,
                text=text,
                height=45,
                font=SticlyTheme.FONT_BODY,
                fg_color=SticlyTheme.PRIMARY,
                hover_color=SticlyTheme.PRIMARY_HOVER,
                command=command
            )
            btn.pack(fill="x")

            desc_label = ctk.CTkLabel(
                btn_frame,
                text=desc,
                font=SticlyTheme.FONT_SMALL,
                text_color=self.colors["text_secondary"]
            )
            desc_label.pack(anchor="w", pady=(2, 0))

        # Sag: Log/Durum
        self.log_frame = ctk.CTkFrame(
            self.content,
            fg_color=self.colors["bg_secondary"],
            corner_radius=12
        )
        self.log_frame.grid(row=0, column=1, sticky="nsew", padx=(10, 0), pady=5)

        log_title = ctk.CTkLabel(
            self.log_frame,
            text="Islem Durumu",
            font=SticlyTheme.FONT_SUBHEADER,
            text_color=self.colors["text"]
        )
        log_title.pack(anchor="w", padx=15, pady=(15, 10))

        # Progress
        self.progress_frame = ctk.CTkFrame(self.log_frame, fg_color="transparent")
        self.progress_frame.pack(fill="x", padx=15, pady=5)

        self.progress_label = ctk.CTkLabel(
            self.progress_frame,
            text="Hazir",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text"]
        )
        self.progress_label.pack(anchor="w")

        self.progress_bar = ctk.CTkProgressBar(
            self.progress_frame,
            height=8,
            progress_color=SticlyTheme.PRIMARY
        )
        self.progress_bar.pack(fill="x", pady=(5, 0))
        self.progress_bar.set(0)

        # Log text
        self.log_text = ctk.CTkTextbox(
            self.log_frame,
            font=SticlyTheme.FONT_MONO,
            fg_color=self.colors["bg_tertiary"],
            text_color=self.colors["text"],
            state="disabled"
        )
        self.log_text.pack(fill="both", expand=True, padx=15, pady=(10, 15))

    def _log(self, message: str):
        """Log ekle"""
        self.log_text.configure(state="normal")
        time_str = datetime.now().strftime("%H:%M:%S")
        self.log_text.insert("end", f"[{time_str}] {message}\n")
        self.log_text.configure(state="disabled")
        self.log_text.see("end")

    def _set_progress(self, text: str, value: float = 0):
        """Progress guncelle"""
        self.progress_label.configure(text=text)
        self.progress_bar.set(value)

    def _sync_trays(self):
        """Tray senkronizasyonu"""
        if self.app:
            self._log("Tray guncelleme baslatiliyor...")
            self.app.run_tray_update(progress_callback=self._log)

    def _sync_firebase(self):
        """Firebase senkronizasyonu"""
        if self.app:
            self._log("Firebase senkronizasyonu baslatiliyor...")
            self.app.run_firebase_sync(progress_callback=self._log)

    def _sync_drive_upload(self):
        """Drive yukleme"""
        if self.app:
            self._log("Drive yukleme baslatiliyor...")
            self.app.run_drive_backup(progress_callback=self._log)

    def _sync_drive_download(self):
        """Drive indirme"""
        if self.app:
            self._log("Drive'dan indirme baslatiliyor...")
            self.app.run_drive_download(progress_callback=self._log)

    def _sync_github(self):
        """GitHub senkronizasyonu"""
        if self.app:
            self._log("GitHub senkronizasyonu baslatiliyor...")
            self.app.run_github_sync(progress_callback=self._log)

    def _full_sync(self):
        """Tam senkronizasyon"""
        if self.app:
            self._log("Tam senkronizasyon baslatiliyor...")
            self.app.run_full_sync(progress_callback=self._log)

    def refresh(self):
        """Sayfa yenilendiginde"""
        pass


class StatsFrame(ctk.CTkFrame):
    """Istatistik frame"""

    def __init__(self, master, app_controller=None, **kwargs):
        super().__init__(master, **kwargs)

        self.app = app_controller
        self.colors = SticlyTheme.get_colors(True)
        self.configure(fg_color="transparent")

        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        self._create_header()
        self._create_content()

    def _create_header(self):
        """Baslik"""
        self.header = ctk.CTkFrame(self, fg_color="transparent")
        self.header.grid(row=0, column=0, sticky="ew", pady=(0, 15))

        self.title = ctk.CTkLabel(
            self.header,
            text="Detayli Istatistikler",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        self.title.pack(side="left")

        # Yenile
        self.refresh_btn = ctk.CTkButton(
            self.header,
            text="Yenile",
            width=100,
            height=32,
            font=SticlyTheme.FONT_SMALL,
            fg_color=SticlyTheme.PRIMARY,
            hover_color=SticlyTheme.PRIMARY_HOVER,
            command=self._load_stats
        )
        self.refresh_btn.pack(side="right")

        # CSV Export
        self.export_btn = ctk.CTkButton(
            self.header,
            text="CSV Aktar",
            width=100,
            height=32,
            font=SticlyTheme.FONT_SMALL,
            fg_color=self.colors["bg_secondary"],
            hover_color=self.colors["bg_tertiary"],
            command=self._export_csv
        )
        self.export_btn.pack(side="right", padx=10)

    def _create_content(self):
        """Icerik"""
        self.content = ctk.CTkScrollableFrame(
            self,
            fg_color="transparent"
        )
        self.content.grid(row=1, column=0, sticky="nsew")

        # Tablo basligi
        self.table_header = ctk.CTkFrame(
            self.content,
            fg_color=self.colors["bg_secondary"],
            corner_radius=8,
            height=40
        )
        self.table_header.pack(fill="x", pady=(0, 5))
        self.table_header.pack_propagate(False)

        columns = [
            ("Paket Adi", 0.25),
            ("Tip", 0.08),
            ("Kategori", 0.12),
            ("Sticker", 0.1),
            ("Indirme", 0.1),
            ("Gosterim", 0.1),
            ("Goruntulenme", 0.1),
            ("Favori", 0.1),
        ]

        for text, weight in columns:
            label = ctk.CTkLabel(
                self.table_header,
                text=text,
                font=SticlyTheme.FONT_SMALL,
                text_color=self.colors["text_secondary"]
            )
            label.pack(side="left", padx=10, fill="x", expand=True)

        # Placeholder
        self.placeholder = ctk.CTkLabel(
            self.content,
            text="Veriler yukleniyor...",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text_secondary"]
        )
        self.placeholder.pack(pady=50)

        self.rows: List[ctk.CTkFrame] = []

    def _load_stats(self):
        """Istatistikleri yukle"""
        import threading

        self.refresh_btn.configure(state="disabled", text="Yukleniyor...")

        def fetch():
            try:
                if self.app and self.app.firebase and self.app.firebase.is_initialized:
                    stats = self.app.firebase.get_statistics()
                    self.after(0, lambda: self._update_table(stats))
            except Exception as e:
                logger.error(f"Istatistik yuklenemedi: {e}")
            finally:
                self.after(0, lambda: self.refresh_btn.configure(
                    state="normal", text="Yenile"
                ))

        thread = threading.Thread(target=fetch, daemon=True)
        thread.start()

    def _update_table(self, stats: dict):
        """Tabloyu guncelle"""
        # Mevcut satirlari temizle
        for row in self.rows:
            row.destroy()
        self.rows.clear()

        if self.placeholder:
            self.placeholder.destroy()
            self.placeholder = None

        packs = stats.get("pack_details", [])
        packs = sorted(packs, key=lambda x: x.get("downloads", 0), reverse=True)

        for pack in packs:
            row = ctk.CTkFrame(
                self.content,
                fg_color=self.colors["bg_secondary"],
                corner_radius=6,
                height=40
            )
            row.pack(fill="x", pady=2)
            row.pack_propagate(False)

            # Paket adi
            name = pack.get("name", "?")[:30]
            name_label = ctk.CTkLabel(
                row,
                text=name,
                font=SticlyTheme.FONT_SMALL,
                text_color=self.colors["text"],
                anchor="w"
            )
            name_label.pack(side="left", padx=10, fill="x", expand=True)

            # Tip
            tip = "P" if pack.get("is_premium") else "N"
            tip_color = SticlyTheme.PREMIUM_GOLD if pack.get("is_premium") else self.colors["text_secondary"]
            tip_label = ctk.CTkLabel(
                row,
                text=tip,
                font=SticlyTheme.FONT_SMALL,
                text_color=tip_color,
                width=50
            )
            tip_label.pack(side="left", padx=5)

            # Kategori
            cat = CATEGORY_NAMES.get(pack.get("category", ""), "-")[:10]
            cat_label = ctk.CTkLabel(
                row,
                text=cat,
                font=SticlyTheme.FONT_SMALL,
                text_color=self.colors["text_secondary"],
                width=80
            )
            cat_label.pack(side="left", padx=5)

            # Sayilar
            values = [
                str(pack.get("sticker_count", 0)),
                str(pack.get("downloads", 0)),
                str(pack.get("display_total", 0)),
                str(pack.get("views", 0)),
                str(pack.get("favorites", 0)),
            ]

            for val in values:
                val_label = ctk.CTkLabel(
                    row,
                    text=val,
                    font=SticlyTheme.FONT_SMALL,
                    text_color=self.colors["text"],
                    width=60
                )
                val_label.pack(side="left", padx=5)

            self.rows.append(row)

    def _export_csv(self):
        """CSV olarak aktar"""
        from tkinter import filedialog

        if not self.app or not self.app.firebase:
            return

        filepath = filedialog.asksaveasfilename(
            defaultextension=".csv",
            filetypes=[("CSV dosyasi", "*.csv")],
            initialfile=f"sticly_stats_{datetime.now().strftime('%Y%m%d')}.csv"
        )

        if filepath:
            try:
                stats = self.app.firebase.get_statistics()
                packs = stats.get("pack_details", [])

                with open(filepath, "w", encoding="utf-8") as f:
                    f.write("Paket Adi,Tip,Kategori,Sticker,Indirme,Gosterim,Goruntulenme,Favori\n")

                    for p in packs:
                        line = f"{p.get('name', '')},{('Premium' if p.get('is_premium') else 'Normal')},"
                        line += f"{CATEGORY_NAMES.get(p.get('category', ''), '-')},"
                        line += f"{p.get('sticker_count', 0)},{p.get('downloads', 0)},"
                        line += f"{p.get('display_total', 0)},{p.get('views', 0)},"
                        line += f"{p.get('favorites', 0)}\n"
                        f.write(line)

                from tkinter import messagebox
                messagebox.showinfo("Basarili", f"CSV kaydedildi: {filepath}")

            except Exception as e:
                from tkinter import messagebox
                messagebox.showerror("Hata", f"CSV kaydedilemedi: {e}")

    def refresh(self):
        """Sayfa yenilendiginde"""
        self._load_stats()


class SettingsFrame(ctk.CTkFrame):
    """Ayarlar frame"""

    def __init__(self, master, app_controller=None, **kwargs):
        super().__init__(master, **kwargs)

        self.app = app_controller
        self.colors = SticlyTheme.get_colors(True)
        self.configure(fg_color="transparent")

        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        self._create_header()
        self._create_content()

    def _create_header(self):
        """Baslik"""
        self.header = ctk.CTkFrame(self, fg_color="transparent")
        self.header.grid(row=0, column=0, sticky="ew", pady=(0, 15))

        self.title = ctk.CTkLabel(
            self.header,
            text="Ayarlar",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        self.title.pack(side="left")

    def _create_content(self):
        """Icerik"""
        self.content = ctk.CTkScrollableFrame(
            self,
            fg_color="transparent"
        )
        self.content.grid(row=1, column=0, sticky="nsew")

        # Genel ayarlar
        general_card = self._create_settings_card(
            "Genel Ayarlar",
            [
                ("Tema", "appearance", "select", ["Koyu", "Acik", "Sistem"]),
                ("Arka Plan Sil", "remove_bg", "switch", True),
            ]
        )
        general_card.pack(fill="x", pady=5)

        # Firebase ayarlari
        firebase_card = self._create_settings_card(
            "Firebase",
            [
                ("Service Account Key", "firebase_key", "file", "*.json"),
                ("Storage Bucket", "storage_bucket", "entry", ""),
            ]
        )
        firebase_card.pack(fill="x", pady=5)

        # Drive ayarlari
        drive_card = self._create_settings_card(
            "Google Drive",
            [
                ("Credentials", "drive_creds", "file", "*.json"),
                ("Klasor Adi", "drive_folder", "entry", "SticlyStickers"),
            ]
        )
        drive_card.pack(fill="x", pady=5)

        # Debug konsolu
        debug_card = ctk.CTkFrame(
            self.content,
            fg_color=self.colors["bg_secondary"],
            corner_radius=12
        )
        debug_card.pack(fill="x", pady=5)

        debug_title = ctk.CTkLabel(
            debug_card,
            text="Debug Konsolu",
            font=SticlyTheme.FONT_SUBHEADER,
            text_color=self.colors["text"]
        )
        debug_title.pack(anchor="w", padx=15, pady=(15, 10))

        open_debug_btn = ctk.CTkButton(
            debug_card,
            text="Debug Konsolunu Ac",
            height=40,
            font=SticlyTheme.FONT_BODY,
            fg_color=SticlyTheme.SECONDARY,
            command=self._open_debug_console
        )
        open_debug_btn.pack(fill="x", padx=15, pady=(0, 15))

    def _create_settings_card(self, title: str, settings: list) -> ctk.CTkFrame:
        """Ayar karti olustur"""
        card = ctk.CTkFrame(
            self.content,
            fg_color=self.colors["bg_secondary"],
            corner_radius=12
        )

        title_label = ctk.CTkLabel(
            card,
            text=title,
            font=SticlyTheme.FONT_SUBHEADER,
            text_color=self.colors["text"]
        )
        title_label.pack(anchor="w", padx=15, pady=(15, 10))

        for label, key, type_, default in settings:
            row = ctk.CTkFrame(card, fg_color="transparent")
            row.pack(fill="x", padx=15, pady=5)

            label_widget = ctk.CTkLabel(
                row,
                text=label,
                font=SticlyTheme.FONT_BODY,
                text_color=self.colors["text"]
            )
            label_widget.pack(side="left")

            if type_ == "switch":
                switch = ctk.CTkSwitch(
                    row,
                    text="",
                    progress_color=SticlyTheme.PRIMARY
                )
                switch.pack(side="right")
                if default:
                    switch.select()

            elif type_ == "select":
                menu = ctk.CTkOptionMenu(
                    row,
                    values=default,
                    width=150,
                    fg_color=self.colors["bg_tertiary"]
                )
                menu.pack(side="right")

            elif type_ == "entry":
                entry = ctk.CTkEntry(
                    row,
                    width=200,
                    placeholder_text=default
                )
                entry.pack(side="right")

            elif type_ == "file":
                file_frame = ctk.CTkFrame(row, fg_color="transparent")
                file_frame.pack(side="right")

                file_btn = ctk.CTkButton(
                    file_frame,
                    text="Sec...",
                    width=60,
                    height=28,
                    fg_color=self.colors["bg_tertiary"]
                )
                file_btn.pack(side="right")

        # Alt bosluk
        ctk.CTkFrame(card, height=10, fg_color="transparent").pack()

        return card

    def _open_debug_console(self):
        """Debug konsolunu ac"""
        if self.app and hasattr(self.app, 'window'):
            self.app.window._show_settings()  # Debug console is in settings

    def refresh(self):
        """Sayfa yenilendiginde"""
        pass
