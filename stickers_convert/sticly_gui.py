#!/usr/bin/env python3
"""
Sticly Sticker Manager - Basit GUI
==================================
"""

import sys
import os
from pathlib import Path

# Calisma dizinini ayarla
os.chdir(Path(__file__).parent)

import customtkinter as ctk
from tkinter import messagebox, filedialog
import threading
import logging

# Tema ayarlari
ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("green")

# Renkler
PRIMARY = "#25D366"
BG_DARK = "#111B21"
BG_SECONDARY = "#1F2C34"

# Logger
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger("sticly")


class SticlyGUI(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("Sticly Sticker Manager")
        self.geometry("900x600")
        self.configure(fg_color=BG_DARK)

        # Managers
        self.firebase = None
        self.drive = None

        self._create_ui()
        self._init_services()

    def _create_ui(self):
        # Ana frame
        self.main_frame = ctk.CTkFrame(self, fg_color=BG_DARK)
        self.main_frame.pack(fill="both", expand=True, padx=20, pady=20)

        # Baslik
        title = ctk.CTkLabel(
            self.main_frame,
            text="Sticly Sticker Manager",
            font=("Segoe UI", 28, "bold"),
            text_color=PRIMARY
        )
        title.pack(pady=(0, 20))

        # Butonlar frame
        buttons_frame = ctk.CTkFrame(self.main_frame, fg_color=BG_SECONDARY, corner_radius=15)
        buttons_frame.pack(fill="x", pady=10)

        # Butonlar
        btn_style = {
            "height": 50,
            "font": ("Segoe UI", 14),
            "fg_color": PRIMARY,
            "hover_color": "#1EBE5D",
            "corner_radius": 10
        }

        buttons = [
            ("Tray Fotograflarini Guncelle", self._update_trays),
            ("Firebase'e Yukle", self._sync_firebase),
            ("Drive'a Yedekle", self._backup_drive),
            ("Drive'dan Indir", self._download_drive),
            ("GitHub Push", self._github_push),
            ("Tam Senkronizasyon", self._full_sync),
        ]

        for i, (text, cmd) in enumerate(buttons):
            btn = ctk.CTkButton(buttons_frame, text=text, command=cmd, **btn_style)
            btn.pack(fill="x", padx=15, pady=8)

        # Durum alani
        self.status_frame = ctk.CTkFrame(self.main_frame, fg_color=BG_SECONDARY, corner_radius=15)
        self.status_frame.pack(fill="both", expand=True, pady=10)

        status_title = ctk.CTkLabel(
            self.status_frame,
            text="Durum",
            font=("Segoe UI", 16, "bold"),
            text_color="#FFFFFF"
        )
        status_title.pack(anchor="w", padx=15, pady=(15, 5))

        self.log_text = ctk.CTkTextbox(
            self.status_frame,
            font=("Consolas", 11),
            fg_color="#0D1418",
            text_color="#E9EDEF",
            corner_radius=10
        )
        self.log_text.pack(fill="both", expand=True, padx=15, pady=(0, 15))

        # Alt durum cubugu
        self.status_bar = ctk.CTkFrame(self.main_frame, fg_color=BG_SECONDARY, height=40, corner_radius=10)
        self.status_bar.pack(fill="x", pady=(10, 0))
        self.status_bar.pack_propagate(False)

        self.firebase_label = ctk.CTkLabel(
            self.status_bar,
            text="Firebase: Baglaniyor...",
            font=("Segoe UI", 11),
            text_color="#8696A0"
        )
        self.firebase_label.pack(side="left", padx=15)

        self.drive_label = ctk.CTkLabel(
            self.status_bar,
            text="Drive: Baglaniyor...",
            font=("Segoe UI", 11),
            text_color="#8696A0"
        )
        self.drive_label.pack(side="left", padx=15)

    def _log(self, msg):
        self.log_text.insert("end", f"{msg}\n")
        self.log_text.see("end")
        logger.info(msg)

    def _init_services(self):
        def init():
            try:
                from core.firebase_manager import FirebaseManager
                from core.drive_manager import DriveManager

                self.firebase = FirebaseManager()
                success, msg = self.firebase.initialize()
                self.after(0, lambda: self._update_firebase_status(success))

                self.drive = DriveManager()
                success, msg = self.drive.initialize()
                self.after(0, lambda: self._update_drive_status(success))

                self.after(0, lambda: self._log("Servisler hazir!"))
            except Exception as e:
                self.after(0, lambda: self._log(f"Hata: {e}"))

        threading.Thread(target=init, daemon=True).start()

    def _update_firebase_status(self, connected):
        if connected:
            self.firebase_label.configure(text="Firebase: Bagli", text_color="#25D366")
        else:
            self.firebase_label.configure(text="Firebase: Hata", text_color="#EA4335")

    def _update_drive_status(self, connected):
        if connected:
            self.drive_label.configure(text="Drive: Bagli", text_color="#25D366")
        else:
            self.drive_label.configure(text="Drive: Hata", text_color="#EA4335")

    def _update_trays(self):
        self._log("Tray guncelleme baslatiliyor...")
        def task():
            try:
                from core.utils import STICKERS_DIR, PREMIUM_STICKERS_DIR, OUTPUT_DIR, load_cache, save_cache, get_pack_id
                from core.converter import StickerConverter

                converter = StickerConverter()
                cache = load_cache()

                all_packs = []
                if STICKERS_DIR.exists():
                    all_packs.extend([(d, False) for d in STICKERS_DIR.iterdir() if d.is_dir()])
                if PREMIUM_STICKERS_DIR.exists():
                    all_packs.extend([(d, True) for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()])

                for i, (pack_dir, is_premium) in enumerate(all_packs, 1):
                    pack_name = pack_dir.name
                    pack_id = get_pack_id(pack_name)
                    self.after(0, lambda n=pack_name, i=i, t=len(all_packs): self._log(f"[{i}/{t}] {n}"))

                    tray_source = converter.find_tray_source(pack_dir)
                    if not tray_source:
                        continue

                    pack_output = OUTPUT_DIR / pack_id
                    pack_output.mkdir(parents=True, exist_ok=True)
                    tray_path = pack_output / "tray.webp"

                    if converter.create_tray_image(tray_source, tray_path, force_bg_removal=True):
                        if self.firebase and self.firebase.is_initialized:
                            storage_folder = "premium_stickers" if is_premium else "stickers"
                            remote_path = f"{storage_folder}/{pack_id}/tray.webp"
                            tray_url = self.firebase.upload_to_storage(tray_path, remote_path, force_refresh=True)
                            if tray_url:
                                self.firebase.update_pack(pack_id, {"tray_url": tray_url}, is_premium)

                save_cache(cache)
                self.after(0, lambda: self._log("Tray guncelleme tamamlandi!"))
            except Exception as e:
                self.after(0, lambda: self._log(f"Hata: {e}"))

        threading.Thread(target=task, daemon=True).start()

    def _sync_firebase(self):
        self._log("Firebase senkronizasyonu baslatiliyor...")
        def task():
            try:
                from core.utils import STICKERS_DIR, PREMIUM_STICKERS_DIR, OUTPUT_DIR, load_cache, save_cache
                from core.converter import StickerConverter

                converter = StickerConverter()
                cache = load_cache()

                normal_packs = [d for d in STICKERS_DIR.iterdir() if d.is_dir()] if STICKERS_DIR.exists() else []
                premium_packs = [d for d in PREMIUM_STICKERS_DIR.iterdir() if d.is_dir()] if PREMIUM_STICKERS_DIR.exists() else []

                for pack_dir in sorted(normal_packs):
                    self.after(0, lambda n=pack_dir.name: self._log(f"Isleniyor: {n}"))
                    converter.process_pack(pack_dir, OUTPUT_DIR, cache, self.firebase, is_premium=False)

                for pack_dir in sorted(premium_packs):
                    self.after(0, lambda n=pack_dir.name: self._log(f"Isleniyor: {n} (Premium)"))
                    converter.process_pack(pack_dir, OUTPUT_DIR, cache, self.firebase, is_premium=True)

                save_cache(cache)
                self.after(0, lambda: self._log("Firebase senkronizasyonu tamamlandi!"))
            except Exception as e:
                self.after(0, lambda: self._log(f"Hata: {e}"))

        threading.Thread(target=task, daemon=True).start()

    def _backup_drive(self):
        self._log("Drive yedekleme baslatiliyor...")
        def task():
            try:
                from core.utils import STICKERS_DIR, PREMIUM_STICKERS_DIR

                if self.drive and self.drive.is_initialized:
                    uploaded, skipped = self.drive.upload_all_packs(STICKERS_DIR, PREMIUM_STICKERS_DIR)
                    self.after(0, lambda: self._log(f"Drive: {uploaded} yuklendi, {skipped} atlandi"))
                else:
                    self.after(0, lambda: self._log("Drive bagli degil!"))
            except Exception as e:
                self.after(0, lambda: self._log(f"Hata: {e}"))

        threading.Thread(target=task, daemon=True).start()

    def _download_drive(self):
        self._log("Drive'dan indirme baslatiliyor...")
        def task():
            try:
                from core.utils import STICKERS_DIR, PREMIUM_STICKERS_DIR

                if self.drive and self.drive.is_initialized:
                    downloaded, skipped = self.drive.download_all_packs(STICKERS_DIR, PREMIUM_STICKERS_DIR)
                    self.after(0, lambda: self._log(f"Drive: {downloaded} indirildi, {skipped} atlandi"))
                else:
                    self.after(0, lambda: self._log("Drive bagli degil!"))
            except Exception as e:
                self.after(0, lambda: self._log(f"Hata: {e}"))

        threading.Thread(target=task, daemon=True).start()

    def _github_push(self):
        self._log("GitHub push baslatiliyor...")
        def task():
            try:
                from core.github_sync import GitHubSync

                github = GitHubSync()
                if github.is_git_repo:
                    success, msg = github.sync()
                    self.after(0, lambda: self._log(f"GitHub: {msg}"))
                else:
                    self.after(0, lambda: self._log("Git repo bulunamadi!"))
            except Exception as e:
                self.after(0, lambda: self._log(f"Hata: {e}"))

        threading.Thread(target=task, daemon=True).start()

    def _full_sync(self):
        self._log("Tam senkronizasyon baslatiliyor...")
        messagebox.showinfo("Bilgi", "Tam senkronizasyon baslatildi. Bu islem uzun surebilir.")

        def task():
            self._download_drive()
            self.after(2000, self._update_trays)
            self.after(10000, self._sync_firebase)
            self.after(60000, self._github_push)

        threading.Thread(target=task, daemon=True).start()


def main():
    app = SticlyGUI()
    app.mainloop()


if __name__ == "__main__":
    main()
