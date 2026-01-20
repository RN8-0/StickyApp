"""
Sticly Manager - Firebase Yonetici
==================================
Firebase Storage ve Firestore islemleri.
"""

import time
import random
import logging
from pathlib import Path
from datetime import datetime
from typing import Optional, Dict, List, Tuple, Callable

from .utils import (
    SCRIPT_DIR, STORAGE_BUCKET, PUBLISHER, PUBLISHER_EMAIL,
    PRIVACY_POLICY, LICENSE, SERVICE_ACCOUNT_KEY
)

logger = logging.getLogger("sticly")


class FirebaseManager:
    """Firebase islemlerini yoneten sinif"""

    def __init__(self):
        self._initialized = False
        self._bucket = None
        self._db = None

    @property
    def is_initialized(self) -> bool:
        return self._initialized

    @property
    def bucket(self):
        return self._bucket

    @property
    def db(self):
        return self._db

    def initialize(self) -> Tuple[bool, str]:
        """Firebase'i baslat"""
        if self._initialized:
            return True, "Firebase zaten baslatilmis"

        try:
            import firebase_admin
            from firebase_admin import credentials, storage, firestore
        except ImportError:
            return False, "Firebase Admin SDK yuklu degil! pip install firebase-admin"

        if SERVICE_ACCOUNT_KEY is None or not SERVICE_ACCOUNT_KEY.exists():
            return False, f"Service Account Key bulunamadi! {SCRIPT_DIR}/ klasorune firebase-adminsdk JSON dosyasi koyun."

        try:
            cred = credentials.Certificate(str(SERVICE_ACCOUNT_KEY))
            firebase_admin.initialize_app(cred, {'storageBucket': STORAGE_BUCKET})

            self._bucket = storage.bucket()
            self._db = firestore.client()
            self._initialized = True

            logger.info("Firebase basariyla baslatildi")
            return True, "Firebase baslatildi"

        except Exception as e:
            logger.error(f"Firebase baslatilamadi: {e}")
            return False, f"Firebase hatasi: {str(e)}"

    def upload_to_storage(
        self,
        local_path: Path,
        remote_path: str,
        force_refresh: bool = False,
        content_type: str = "image/webp"
    ) -> Optional[str]:
        """Firebase Storage'a yukle ve URL dondur"""
        if not self._bucket:
            logger.error("Firebase baslatilmamis")
            return None

        try:
            blob = self._bucket.blob(remote_path)

            if force_refresh:
                try:
                    blob.delete()
                except:
                    pass
                blob = self._bucket.blob(remote_path)

            blob.cache_control = "no-cache, no-store, must-revalidate"
            blob.upload_from_filename(str(local_path), content_type=content_type)
            blob.make_public()

            timestamp = int(time.time())
            url = f"{blob.public_url}?v={timestamp}"

            logger.debug(f"Yuklendi: {remote_path}")
            return url

        except Exception as e:
            logger.error(f"Storage yukleme hatasi: {e}")
            return None

    def delete_from_storage(self, remote_path: str) -> bool:
        """Firebase Storage'dan sil"""
        if not self._bucket:
            return False

        try:
            blob = self._bucket.blob(remote_path)
            blob.delete()
            logger.debug(f"Silindi: {remote_path}")
            return True
        except Exception as e:
            logger.warning(f"Silme hatasi: {e}")
            return False

    def delete_pack_from_storage(self, pack_id: str, is_premium: bool = False) -> int:
        """Paket klasorundeki tum dosyalari sil"""
        if not self._bucket:
            return 0

        storage_folder = "premium_stickers" if is_premium else "stickers"
        prefix = f"{storage_folder}/{pack_id}/"

        deleted = 0
        try:
            blobs = self._bucket.list_blobs(prefix=prefix)
            for blob in blobs:
                blob.delete()
                deleted += 1
            logger.info(f"{deleted} dosya silindi: {prefix}")
        except Exception as e:
            logger.error(f"Storage silme hatasi: {e}")

        return deleted

    def get_pack(self, pack_id: str, is_premium: bool = False) -> Optional[Dict]:
        """Firestore'dan paket verisini al"""
        if not self._db:
            return None

        collection = "premium_stickers" if is_premium else "stickers"
        try:
            doc = self._db.collection(collection).document(pack_id).get()
            if doc.exists:
                return doc.to_dict()
        except Exception as e:
            logger.error(f"Paket alinamadi: {e}")

        return None

    def save_pack(
        self,
        pack_id: str,
        pack_data: Dict,
        is_premium: bool = False
    ) -> bool:
        """Firestore'a paket kaydet"""
        if not self._db:
            return False

        collection = "premium_stickers" if is_premium else "stickers"
        try:
            self._db.collection(collection).document(pack_id).set(pack_data)
            logger.info(f"Paket kaydedildi: {pack_id}")
            return True
        except Exception as e:
            logger.error(f"Paket kaydedilemedi: {e}")
            return False

    def update_pack(
        self,
        pack_id: str,
        updates: Dict,
        is_premium: bool = False
    ) -> bool:
        """Firestore'da paket guncelle"""
        if not self._db:
            return False

        collection = "premium_stickers" if is_premium else "stickers"
        try:
            self._db.collection(collection).document(pack_id).update(updates)
            logger.debug(f"Paket guncellendi: {pack_id}")
            return True
        except Exception as e:
            logger.error(f"Paket guncellenemedi: {e}")
            return False

    def delete_pack(self, pack_id: str, is_premium: bool = False) -> bool:
        """Firestore'dan paket sil"""
        if not self._db:
            return False

        collection = "premium_stickers" if is_premium else "stickers"
        try:
            self._db.collection(collection).document(pack_id).delete()
            logger.info(f"Paket silindi: {pack_id}")
            return True
        except Exception as e:
            logger.error(f"Paket silinemedi: {e}")
            return False

    def get_all_packs(self, progress_callback: Optional[Callable] = None) -> List[Dict]:
        """Tum paketleri getir"""
        if not self._db:
            return []

        packs = []

        try:
            # Normal paketler
            for doc in self._db.collection("stickers").stream():
                data = doc.to_dict()
                data["_id"] = doc.id
                data["_collection"] = "stickers"
                data["_is_premium"] = False
                packs.append(data)
                if progress_callback:
                    progress_callback(f"Normal: {doc.id}")

            # Premium paketler
            for doc in self._db.collection("premium_stickers").stream():
                data = doc.to_dict()
                data["_id"] = doc.id
                data["_collection"] = "premium_stickers"
                data["_is_premium"] = True
                packs.append(data)
                if progress_callback:
                    progress_callback(f"Premium: {doc.id}")

        except Exception as e:
            logger.error(f"Paketler alinamadi: {e}")

        return packs

    def get_statistics(self) -> Dict:
        """Istatistikleri topla"""
        stats = {
            "total_packs": 0,
            "normal_packs": 0,
            "premium_packs": 0,
            "total_stickers": 0,
            "animated_packs": 0,
            "total_downloads": 0,
            "total_views": 0,
            "total_favorites": 0,
            "categories": {},
            "top_downloaded": [],
            "top_viewed": [],
            "pack_details": []
        }

        if not self._db:
            return stats

        try:
            for collection in ["stickers", "premium_stickers"]:
                is_premium = collection == "premium_stickers"

                for doc in self._db.collection(collection).stream():
                    data = doc.to_dict()
                    stats["total_packs"] += 1

                    if is_premium:
                        stats["premium_packs"] += 1
                    else:
                        stats["normal_packs"] += 1

                    sticker_count = data.get("sticker_count", len(data.get("stickers", [])))
                    stats["total_stickers"] += sticker_count

                    if data.get("animated_sticker_pack"):
                        stats["animated_packs"] += 1

                    downloads = data.get("download_count", 0)
                    views = data.get("view_count", 0)
                    favorites = data.get("favorite_count", 0)
                    display_base = data.get("display_base", 0)

                    stats["total_downloads"] += downloads
                    stats["total_views"] += views
                    stats["total_favorites"] += favorites

                    # Kategori istatistikleri
                    category = data.get("category", "")
                    if category:
                        stats["categories"][category] = stats["categories"].get(category, 0) + 1

                    # Paket detaylari
                    pack_info = {
                        "id": doc.id,
                        "name": data.get("name", doc.id),
                        "downloads": downloads,
                        "views": views,
                        "favorites": favorites,
                        "display_total": downloads + display_base,
                        "sticker_count": sticker_count,
                        "is_premium": is_premium,
                        "category": category,
                        "animated": data.get("animated_sticker_pack", False),
                        "created_at": data.get("created_at", "")
                    }
                    stats["pack_details"].append(pack_info)

            # En cok indirilenler
            stats["top_downloaded"] = sorted(
                stats["pack_details"],
                key=lambda x: x["downloads"],
                reverse=True
            )[:10]

            # En cok goruntulenenlersort
            stats["top_viewed"] = sorted(
                stats["pack_details"],
                key=lambda x: x["views"],
                reverse=True
            )[:10]

        except Exception as e:
            logger.error(f"Istatistik alinamadi: {e}")

        return stats

    def create_pack_data(
        self,
        pack_id: str,
        pack_name: str,
        stickers: List[Dict],
        tray_url: str,
        has_animated: bool,
        is_premium: bool = False,
        existing_data: Optional[Dict] = None
    ) -> Dict:
        """Yeni paket verisi olustur"""
        if existing_data is None:
            existing_data = {}

        storage_folder = "premium_stickers" if is_premium else "stickers"

        # Yeni paket icin display_base olustur
        display_base = existing_data.get("display_base")
        if display_base is None:
            display_base = random.randint(500, 600)

        return {
            "name": pack_name.replace("_", " ").replace("-", " ").title(),
            "publisher": PUBLISHER,
            "publisher_email": PUBLISHER_EMAIL,
            "privacy_policy_website": PRIVACY_POLICY,
            "license_agreement_website": LICENSE,
            "image_data_version": "1",
            "avoid_cache": False,
            "tray_image_file": "tray.webp",
            "tray_url": tray_url,
            "stickers": stickers,
            "isPremium": is_premium,
            "storagePath": storage_folder,
            "animated_sticker_pack": has_animated,
            "created_at": existing_data.get("created_at", datetime.now().strftime("%Y-%m-%d")),
            "sticker_count": len(stickers),
            "category": existing_data.get("category", ""),
            "download_count": existing_data.get("download_count", 0),
            "display_base": display_base,
            "view_count": existing_data.get("view_count", 0),
            "favorite_count": existing_data.get("favorite_count", 0)
        }

    def reset_download_counts(
        self,
        reset_real: bool = False,
        reset_base: bool = False
    ) -> int:
        """Indirme sayilarini sifirla"""
        if not self._db:
            return 0

        updated = 0

        for collection in ["stickers", "premium_stickers"]:
            try:
                for doc in self._db.collection(collection).stream():
                    updates = {}

                    if reset_real:
                        updates["download_count"] = 0

                    if reset_base:
                        updates["display_base"] = random.randint(500, 600)

                    if updates:
                        self._db.collection(collection).document(doc.id).update(updates)
                        updated += 1

            except Exception as e:
                logger.error(f"Sifirlama hatasi: {e}")

        return updated
