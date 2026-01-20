"""
Sticly Manager - Paket Yonetimi
===============================
Sticker paketlerini yonetme arayuzu.
"""

import customtkinter as ctk
from tkinter import filedialog
from pathlib import Path
from typing import Optional, List, Dict, Callable
import logging
import threading
import shutil

from .theme import SticlyTheme, CATEGORY_NAMES, CATEGORY_COLORS

logger = logging.getLogger("sticly")


class PackCard(ctk.CTkFrame):
    """Paket karti bileseni"""

    def __init__(
        self,
        master,
        pack_data: Dict,
        on_edit: Optional[Callable] = None,
        on_delete: Optional[Callable] = None,
        on_sync: Optional[Callable] = None,
        **kwargs
    ):
        super().__init__(master, **kwargs)

        self.pack_data = pack_data
        self.on_edit = on_edit
        self.on_delete = on_delete
        self.on_sync = on_sync

        colors = SticlyTheme.get_colors(True)
        self.configure(
            fg_color=colors["bg_secondary"],
            corner_radius=12
        )

        # Premium gostergesi
        if pack_data.get("is_premium"):
            self.premium_bar = ctk.CTkFrame(
                self,
                width=4,
                corner_radius=0,
                fg_color=SticlyTheme.PREMIUM_GOLD
            )
            self.premium_bar.pack(side="left", fill="y")

        # Ana icerik
        self.content = ctk.CTkFrame(self, fg_color="transparent")
        self.content.pack(fill="both", expand=True, padx=15, pady=12)

        # Ust satir: Baslik ve etiketler
        self.header = ctk.CTkFrame(self.content, fg_color="transparent")
        self.header.pack(fill="x")

        self.name_label = ctk.CTkLabel(
            self.header,
            text=pack_data.get("name", "?"),
            font=SticlyTheme.FONT_SUBHEADER,
            text_color=colors["text"]
        )
        self.name_label.pack(side="left")

        # Etiketler
        tags_frame = ctk.CTkFrame(self.header, fg_color="transparent")
        tags_frame.pack(side="right")

        if pack_data.get("is_premium"):
            premium_tag = ctk.CTkLabel(
                tags_frame,
                text="PREMIUM",
                font=("Segoe UI", 9, "bold"),
                text_color=SticlyTheme.PREMIUM_GOLD,
                fg_color=colors["bg_tertiary"],
                corner_radius=4,
                padx=6,
                pady=2
            )
            premium_tag.pack(side="left", padx=2)

        if pack_data.get("animated"):
            anim_tag = ctk.CTkLabel(
                tags_frame,
                text="Animated",
                font=("Segoe UI", 9),
                text_color=SticlyTheme.ACCENT,
                fg_color=colors["bg_tertiary"],
                corner_radius=4,
                padx=6,
                pady=2
            )
            anim_tag.pack(side="left", padx=2)

        # Kategori
        category = pack_data.get("category", "")
        cat_name = CATEGORY_NAMES.get(category, category or "Kategorisiz")
        cat_color = CATEGORY_COLORS.get(category, colors["text_secondary"])

        self.category_label = ctk.CTkLabel(
            self.content,
            text=cat_name,
            font=SticlyTheme.FONT_SMALL,
            text_color=cat_color
        )
        self.category_label.pack(anchor="w", pady=(5, 0))

        # Istatistikler
        self.stats_frame = ctk.CTkFrame(self.content, fg_color="transparent")
        self.stats_frame.pack(fill="x", pady=(10, 0))

        stats = [
            (f"{pack_data.get('sticker_count', 0)} sticker", colors["text_secondary"]),
            (f"{pack_data.get('downloads', 0)} indirme", SticlyTheme.SUCCESS),
            (f"{pack_data.get('views', 0)} goru.", SticlyTheme.ACCENT),
        ]

        for text, color in stats:
            stat_label = ctk.CTkLabel(
                self.stats_frame,
                text=text,
                font=SticlyTheme.FONT_SMALL,
                text_color=color
            )
            stat_label.pack(side="left", padx=(0, 15))

        # Aksiyonlar
        self.actions_frame = ctk.CTkFrame(self.content, fg_color="transparent")
        self.actions_frame.pack(fill="x", pady=(10, 0))

        if on_sync:
            self.sync_btn = ctk.CTkButton(
                self.actions_frame,
                text="Senkronize",
                width=80,
                height=28,
                font=SticlyTheme.FONT_SMALL,
                fg_color=SticlyTheme.PRIMARY,
                hover_color=SticlyTheme.PRIMARY_HOVER,
                command=lambda: on_sync(pack_data)
            )
            self.sync_btn.pack(side="left", padx=(0, 5))

        if on_edit:
            self.edit_btn = ctk.CTkButton(
                self.actions_frame,
                text="Duzenle",
                width=70,
                height=28,
                font=SticlyTheme.FONT_SMALL,
                fg_color=SticlyTheme.SECONDARY,
                hover_color=SticlyTheme.PRIMARY_DARK,
                command=lambda: on_edit(pack_data)
            )
            self.edit_btn.pack(side="left", padx=(0, 5))

        if on_delete:
            self.delete_btn = ctk.CTkButton(
                self.actions_frame,
                text="Sil",
                width=50,
                height=28,
                font=SticlyTheme.FONT_SMALL,
                fg_color=SticlyTheme.ERROR,
                hover_color="#CC3333",
                command=lambda: on_delete(pack_data)
            )
            self.delete_btn.pack(side="left")


class PackManagerFrame(ctk.CTkFrame):
    """Paket yonetimi ana frame"""

    def __init__(self, master, app_controller=None, **kwargs):
        super().__init__(master, **kwargs)

        self.app = app_controller
        self.colors = SticlyTheme.get_colors(True)
        self.configure(fg_color="transparent")

        self.packs_data: List[Dict] = []
        self.filter_premium = None  # None=Tum, True=Premium, False=Normal
        self.search_text = ""

        # Grid yapilandirmasi
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        self._create_header()
        self._create_content()

    def _create_header(self):
        """Baslik ve filtreler"""
        self.header = ctk.CTkFrame(self, fg_color="transparent")
        self.header.grid(row=0, column=0, sticky="ew", pady=(0, 15))

        # Sol: Baslik
        self.title = ctk.CTkLabel(
            self.header,
            text="Paket Yonetimi",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        self.title.pack(side="left")

        # Sag: Aksiyonlar
        self.actions = ctk.CTkFrame(self.header, fg_color="transparent")
        self.actions.pack(side="right")

        # Arama
        self.search_entry = ctk.CTkEntry(
            self.actions,
            placeholder_text="Ara...",
            width=200,
            height=35,
            font=SticlyTheme.FONT_BODY
        )
        self.search_entry.pack(side="left", padx=5)
        self.search_entry.bind("<KeyRelease>", self._on_search)

        # Filtre
        self.filter_menu = ctk.CTkOptionMenu(
            self.actions,
            values=["Tum Paketler", "Normal", "Premium"],
            width=130,
            height=35,
            font=SticlyTheme.FONT_BODY,
            fg_color=self.colors["bg_secondary"],
            button_color=self.colors["bg_tertiary"],
            command=self._on_filter_change
        )
        self.filter_menu.pack(side="left", padx=5)

        # Yeni paket ekle
        self.add_btn = ctk.CTkButton(
            self.actions,
            text="+ Yeni Paket",
            width=120,
            height=35,
            font=SticlyTheme.FONT_BODY,
            fg_color=SticlyTheme.PRIMARY,
            hover_color=SticlyTheme.PRIMARY_HOVER,
            command=self._on_add_pack
        )
        self.add_btn.pack(side="left", padx=5)

        # Yenile
        self.refresh_btn = ctk.CTkButton(
            self.actions,
            text="Yenile",
            width=80,
            height=35,
            font=SticlyTheme.FONT_BODY,
            fg_color=self.colors["bg_secondary"],
            hover_color=self.colors["bg_tertiary"],
            command=self._load_packs
        )
        self.refresh_btn.pack(side="left", padx=5)

    def _create_content(self):
        """Paket listesi"""
        self.content = ctk.CTkScrollableFrame(
            self,
            fg_color="transparent"
        )
        self.content.grid(row=1, column=0, sticky="nsew")

        # Grid yapilandirmasi (3 sutun)
        for i in range(3):
            self.content.grid_columnconfigure(i, weight=1)

        # Placeholder
        self.placeholder = ctk.CTkLabel(
            self.content,
            text="Paketler yukleniyor...",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text_secondary"]
        )
        self.placeholder.grid(row=0, column=0, columnspan=3, pady=50)

    def _on_search(self, event=None):
        """Arama degistiginde"""
        self.search_text = self.search_entry.get().lower()
        self._update_pack_list()

    def _on_filter_change(self, value: str):
        """Filtre degistiginde"""
        if value == "Tum Paketler":
            self.filter_premium = None
        elif value == "Premium":
            self.filter_premium = True
        else:
            self.filter_premium = False
        self._update_pack_list()

    def _on_add_pack(self):
        """Yeni paket ekleme dialog"""
        dialog = AddPackDialog(self, app_controller=self.app)
        self.wait_window(dialog)

        if dialog.result:
            self._load_packs()

    def _on_edit_pack(self, pack_data: Dict):
        """Paket duzenleme"""
        dialog = EditPackDialog(self, pack_data, app_controller=self.app)
        self.wait_window(dialog)

        if dialog.result:
            self._load_packs()

    def _on_delete_pack(self, pack_data: Dict):
        """Paket silme"""
        from tkinter import messagebox

        pack_name = pack_data.get("name", "?")
        confirm = messagebox.askyesno(
            "Paket Sil",
            f"'{pack_name}' paketini silmek istediginize emin misiniz?\n\n"
            "Bu islem geri alinamaz!"
        )

        if confirm:
            if self.app:
                self.app.delete_pack(pack_data)
                self._load_packs()

    def _on_sync_pack(self, pack_data: Dict):
        """Tek paket senkronize"""
        if self.app:
            self.app.sync_single_pack(pack_data)

    def _load_packs(self):
        """Paketleri yukle"""
        self.refresh_btn.configure(state="disabled", text="Yukleniyor...")

        def fetch():
            try:
                packs = []

                # Yerel paketleri tara
                if self.app:
                    from core.utils import STICKERS_DIR, PREMIUM_STICKERS_DIR, get_sticker_files

                    # Normal paketler
                    if STICKERS_DIR.exists():
                        for pack_dir in STICKERS_DIR.iterdir():
                            if pack_dir.is_dir():
                                files = get_sticker_files(pack_dir)
                                packs.append({
                                    "id": pack_dir.name.lower().replace(" ", "_").replace("-", "_"),
                                    "name": pack_dir.name,
                                    "path": str(pack_dir),
                                    "sticker_count": len(files),
                                    "is_premium": False,
                                    "is_local": True,
                                    "downloads": 0,
                                    "views": 0,
                                    "category": "",
                                    "animated": False
                                })

                    # Premium paketler
                    if PREMIUM_STICKERS_DIR.exists():
                        for pack_dir in PREMIUM_STICKERS_DIR.iterdir():
                            if pack_dir.is_dir():
                                files = get_sticker_files(pack_dir)
                                packs.append({
                                    "id": pack_dir.name.lower().replace(" ", "_").replace("-", "_"),
                                    "name": pack_dir.name,
                                    "path": str(pack_dir),
                                    "sticker_count": len(files),
                                    "is_premium": True,
                                    "is_local": True,
                                    "downloads": 0,
                                    "views": 0,
                                    "category": "",
                                    "animated": False
                                })

                    # Firebase'den mevcut verileri al
                    if self.app.firebase and self.app.firebase.is_initialized:
                        firebase_packs = self.app.firebase.get_all_packs()

                        for fp in firebase_packs:
                            pack_id = fp.get("_id")
                            # Yerel paketi bul ve Firebase verileriyle guncelle
                            for p in packs:
                                if p["id"] == pack_id:
                                    p["downloads"] = fp.get("download_count", 0)
                                    p["views"] = fp.get("view_count", 0)
                                    p["category"] = fp.get("category", "")
                                    p["animated"] = fp.get("animated_sticker_pack", False)
                                    break

                self.packs_data = packs
                self.after(0, self._update_pack_list)

            except Exception as e:
                logger.error(f"Paketler yuklenemedi: {e}")
            finally:
                self.after(0, lambda: self.refresh_btn.configure(
                    state="normal", text="Yenile"
                ))

        thread = threading.Thread(target=fetch, daemon=True)
        thread.start()

    def _update_pack_list(self):
        """Paket listesini guncelle"""
        # Mevcut kartlari temizle
        for widget in self.content.winfo_children():
            widget.destroy()

        # Filtreleme
        filtered = self.packs_data

        if self.filter_premium is not None:
            filtered = [p for p in filtered if p.get("is_premium") == self.filter_premium]

        if self.search_text:
            filtered = [p for p in filtered
                       if self.search_text in p.get("name", "").lower()]

        if not filtered:
            placeholder = ctk.CTkLabel(
                self.content,
                text="Paket bulunamadi",
                font=SticlyTheme.FONT_BODY,
                text_color=self.colors["text_secondary"]
            )
            placeholder.grid(row=0, column=0, columnspan=3, pady=50)
            return

        # Kartlari olustur
        for i, pack in enumerate(filtered):
            row = i // 3
            col = i % 3

            card = PackCard(
                self.content,
                pack,
                on_edit=self._on_edit_pack,
                on_delete=self._on_delete_pack,
                on_sync=self._on_sync_pack
            )
            card.grid(row=row, column=col, padx=8, pady=8, sticky="nsew")

    def refresh(self):
        """Sayfa yenilendiginde"""
        self._load_packs()


class AddPackDialog(ctk.CTkToplevel):
    """Yeni paket ekleme dialog"""

    def __init__(self, parent, app_controller=None, **kwargs):
        super().__init__(parent, **kwargs)

        self.app = app_controller
        self.result = False
        self.colors = SticlyTheme.get_colors(True)

        self.title("Yeni Paket Ekle")
        self.geometry("500x400")
        self.resizable(False, False)

        # Modal yap
        self.transient(parent)
        self.grab_set()

        self._create_ui()

    def _create_ui(self):
        """Arayuz olustur"""
        # Baslik
        title = ctk.CTkLabel(
            self,
            text="Yeni Sticker Paketi Ekle",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        title.pack(pady=(20, 10))

        # Form
        form = ctk.CTkFrame(self, fg_color="transparent")
        form.pack(fill="both", expand=True, padx=30, pady=10)

        # Paket adi
        name_label = ctk.CTkLabel(
            form,
            text="Paket Adi:",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text"]
        )
        name_label.pack(anchor="w", pady=(10, 5))

        self.name_entry = ctk.CTkEntry(
            form,
            placeholder_text="ornek: Komik Kediler",
            height=40,
            font=SticlyTheme.FONT_BODY
        )
        self.name_entry.pack(fill="x")

        # Paket tipi
        type_label = ctk.CTkLabel(
            form,
            text="Paket Tipi:",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text"]
        )
        type_label.pack(anchor="w", pady=(15, 5))

        self.type_var = ctk.StringVar(value="normal")
        type_frame = ctk.CTkFrame(form, fg_color="transparent")
        type_frame.pack(fill="x")

        normal_radio = ctk.CTkRadioButton(
            type_frame,
            text="Normal (Ucretsiz)",
            variable=self.type_var,
            value="normal",
            font=SticlyTheme.FONT_BODY
        )
        normal_radio.pack(side="left", padx=(0, 20))

        premium_radio = ctk.CTkRadioButton(
            type_frame,
            text="Premium (Ucretli)",
            variable=self.type_var,
            value="premium",
            font=SticlyTheme.FONT_BODY
        )
        premium_radio.pack(side="left")

        # Dosya secme
        files_label = ctk.CTkLabel(
            form,
            text="Sticker Dosyalari:",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text"]
        )
        files_label.pack(anchor="w", pady=(15, 5))

        files_frame = ctk.CTkFrame(form, fg_color="transparent")
        files_frame.pack(fill="x")

        self.files_label = ctk.CTkLabel(
            files_frame,
            text="Dosya secilmedi",
            font=SticlyTheme.FONT_SMALL,
            text_color=self.colors["text_secondary"]
        )
        self.files_label.pack(side="left", fill="x", expand=True)

        self.browse_btn = ctk.CTkButton(
            files_frame,
            text="Dosya Sec",
            width=100,
            height=35,
            command=self._browse_files
        )
        self.browse_btn.pack(side="right")

        self.selected_files: List[Path] = []

        # Butonlar
        buttons = ctk.CTkFrame(self, fg_color="transparent")
        buttons.pack(fill="x", padx=30, pady=20)

        cancel_btn = ctk.CTkButton(
            buttons,
            text="Iptal",
            width=100,
            height=40,
            fg_color=self.colors["bg_secondary"],
            hover_color=self.colors["bg_tertiary"],
            command=self.destroy
        )
        cancel_btn.pack(side="left")

        create_btn = ctk.CTkButton(
            buttons,
            text="Olustur",
            width=100,
            height=40,
            fg_color=SticlyTheme.PRIMARY,
            hover_color=SticlyTheme.PRIMARY_HOVER,
            command=self._create_pack
        )
        create_btn.pack(side="right")

    def _browse_files(self):
        """Dosya sec"""
        from core.utils import ALL_EXTENSIONS

        filetypes = [
            ("Resim/Video", " ".join(f"*{ext}" for ext in ALL_EXTENSIONS)),
            ("Tum Dosyalar", "*.*")
        ]

        files = filedialog.askopenfilenames(
            title="Sticker dosyalarini secin",
            filetypes=filetypes
        )

        if files:
            self.selected_files = [Path(f) for f in files]
            self.files_label.configure(
                text=f"{len(self.selected_files)} dosya secildi"
            )

    def _create_pack(self):
        """Paketi olustur"""
        name = self.name_entry.get().strip()
        if not name:
            from tkinter import messagebox
            messagebox.showerror("Hata", "Paket adi giriniz!")
            return

        if len(self.selected_files) < 3:
            from tkinter import messagebox
            messagebox.showerror("Hata", "En az 3 sticker dosyasi secmelisiniz!")
            return

        is_premium = self.type_var.get() == "premium"

        from core.utils import STICKERS_DIR, PREMIUM_STICKERS_DIR

        # Klasor olustur
        target_dir = PREMIUM_STICKERS_DIR if is_premium else STICKERS_DIR
        pack_dir = target_dir / name

        if pack_dir.exists():
            from tkinter import messagebox
            messagebox.showerror("Hata", "Bu isimde bir paket zaten var!")
            return

        try:
            pack_dir.mkdir(parents=True)

            # Dosyalari kopyala
            for f in self.selected_files:
                shutil.copy2(f, pack_dir / f.name)

            self.result = True
            self.destroy()

        except Exception as e:
            from tkinter import messagebox
            messagebox.showerror("Hata", f"Paket olusturulamadi: {e}")


class EditPackDialog(ctk.CTkToplevel):
    """Paket duzenleme dialog"""

    def __init__(self, parent, pack_data: Dict, app_controller=None, **kwargs):
        super().__init__(parent, **kwargs)

        self.app = app_controller
        self.pack_data = pack_data
        self.result = False
        self.colors = SticlyTheme.get_colors(True)

        self.title("Paket Duzenle")
        self.geometry("500x350")
        self.resizable(False, False)

        self.transient(parent)
        self.grab_set()

        self._create_ui()

    def _create_ui(self):
        """Arayuz olustur"""
        title = ctk.CTkLabel(
            self,
            text=f"Duzenle: {self.pack_data.get('name', '?')}",
            font=SticlyTheme.FONT_HEADER,
            text_color=self.colors["text"]
        )
        title.pack(pady=(20, 10))

        form = ctk.CTkFrame(self, fg_color="transparent")
        form.pack(fill="both", expand=True, padx=30, pady=10)

        # Paket adi
        name_label = ctk.CTkLabel(
            form,
            text="Paket Adi:",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text"]
        )
        name_label.pack(anchor="w", pady=(10, 5))

        self.name_entry = ctk.CTkEntry(
            form,
            height=40,
            font=SticlyTheme.FONT_BODY
        )
        self.name_entry.pack(fill="x")
        self.name_entry.insert(0, self.pack_data.get("name", ""))

        # Kategori
        cat_label = ctk.CTkLabel(
            form,
            text="Kategori:",
            font=SticlyTheme.FONT_BODY,
            text_color=self.colors["text"]
        )
        cat_label.pack(anchor="w", pady=(15, 5))

        self.category_menu = ctk.CTkOptionMenu(
            form,
            values=list(CATEGORY_NAMES.values()),
            width=200,
            height=40,
            font=SticlyTheme.FONT_BODY
        )
        self.category_menu.pack(anchor="w")

        current_cat = self.pack_data.get("category", "")
        current_cat_name = CATEGORY_NAMES.get(current_cat, "Kategorisiz")
        self.category_menu.set(current_cat_name)

        # Butonlar
        buttons = ctk.CTkFrame(self, fg_color="transparent")
        buttons.pack(fill="x", padx=30, pady=20)

        cancel_btn = ctk.CTkButton(
            buttons,
            text="Iptal",
            width=100,
            height=40,
            fg_color=self.colors["bg_secondary"],
            hover_color=self.colors["bg_tertiary"],
            command=self.destroy
        )
        cancel_btn.pack(side="left")

        save_btn = ctk.CTkButton(
            buttons,
            text="Kaydet",
            width=100,
            height=40,
            fg_color=SticlyTheme.PRIMARY,
            hover_color=SticlyTheme.PRIMARY_HOVER,
            command=self._save_changes
        )
        save_btn.pack(side="right")

    def _save_changes(self):
        """Degisiklikleri kaydet"""
        new_name = self.name_entry.get().strip()
        cat_name = self.category_menu.get()

        # Kategori ID bul
        cat_id = ""
        for cid, cname in CATEGORY_NAMES.items():
            if cname == cat_name:
                cat_id = cid
                break

        if self.app and self.app.firebase and self.app.firebase.is_initialized:
            pack_id = self.pack_data.get("id")
            is_premium = self.pack_data.get("is_premium", False)

            self.app.firebase.update_pack(
                pack_id,
                {"name": new_name, "category": cat_id},
                is_premium
            )

        self.result = True
        self.destroy()
