"""
Sticly Manager - Google Drive Yonetici
======================================
Google Drive API islemleri.
"""

import io
import pickle
import logging
from pathlib import Path
from typing import Optional, Dict, List, Tuple, Callable

from .utils import (
    SCRIPT_DIR, CREDENTIALS_FILE, TOKEN_FILE,
    DRIVE_FOLDER_NAME, ALL_EXTENSIONS
)

logger = logging.getLogger("sticly")


class DriveManager:
    """Google Drive islemlerini yoneten sinif"""

    SCOPES = ['https://www.googleapis.com/auth/drive']

    def __init__(self):
        self._service = None
        self._main_folder_id = None

    @property
    def is_initialized(self) -> bool:
        return self._service is not None

    def initialize(self) -> Tuple[bool, str]:
        """Google Drive API'yi baslat"""
        if self._service is not None:
            return True, "Drive zaten baslatilmis"

        try:
            from google.oauth2.credentials import Credentials
            from google_auth_oauthlib.flow import InstalledAppFlow
            from google.auth.transport.requests import Request
            from googleapiclient.discovery import build
        except ImportError:
            return False, "Google API kutuphaneleri yuklu degil! pip install google-api-python-client google-auth-oauthlib"

        creds = None

        # Token var mi kontrol et
        if TOKEN_FILE.exists():
            try:
                with open(TOKEN_FILE, 'rb') as token:
                    creds = pickle.load(token)
            except Exception as e:
                logger.warning(f"Token okunamadi: {e}")

        # Token gecersiz veya yok
        if not creds or not creds.valid:
            if creds and creds.expired and creds.refresh_token:
                try:
                    creds.refresh(Request())
                except Exception as e:
                    logger.warning(f"Token yenilenemedi: {e}")
                    creds = None

            if not creds:
                if not CREDENTIALS_FILE.exists():
                    return False, f"credentials.json bulunamadi! Google Cloud Console'dan OAuth 2.0 Client ID olusturun ve {CREDENTIALS_FILE} konumuna kaydedin."

                try:
                    flow = InstalledAppFlow.from_client_secrets_file(
                        str(CREDENTIALS_FILE), self.SCOPES
                    )
                    creds = flow.run_local_server(port=0)
                except Exception as e:
                    return False, f"Yetkilendirme hatasi: {str(e)}"

            # Token'i kaydet
            try:
                with open(TOKEN_FILE, 'wb') as token:
                    pickle.dump(creds, token)
            except Exception as e:
                logger.warning(f"Token kaydedilemedi: {e}")

        try:
            self._service = build('drive', 'v3', credentials=creds)
            logger.info("Google Drive basariyla baslatildi")
            return True, "Drive baslatildi"
        except Exception as e:
            return False, f"Drive servisi olusturulamadi: {str(e)}"

    def get_or_create_folder(
        self,
        folder_name: str,
        parent_id: Optional[str] = None
    ) -> Optional[str]:
        """Drive'da klasor bul veya olustur"""
        if not self._service:
            return None

        try:
            query = f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder' and trashed=false"
            if parent_id:
                query += f" and '{parent_id}' in parents"

            results = self._service.files().list(
                q=query,
                spaces='drive',
                fields='files(id, name)'
            ).execute()

            folders = results.get('files', [])

            if folders:
                return folders[0]['id']

            # Klasor olustur
            file_metadata = {
                'name': folder_name,
                'mimeType': 'application/vnd.google-apps.folder'
            }
            if parent_id:
                file_metadata['parents'] = [parent_id]

            folder = self._service.files().create(
                body=file_metadata,
                fields='id'
            ).execute()

            logger.info(f"Klasor olusturuldu: {folder_name}")
            return folder.get('id')

        except Exception as e:
            logger.error(f"Klasor islem hatasi: {e}")
            return None

    def get_main_folder_id(self) -> Optional[str]:
        """Ana SticlyStickers klasorunu al veya olustur"""
        if self._main_folder_id:
            return self._main_folder_id

        self._main_folder_id = self.get_or_create_folder(DRIVE_FOLDER_NAME)
        return self._main_folder_id

    def folder_exists(self, folder_name: str, parent_id: str) -> bool:
        """Klasor var mi kontrol et"""
        if not self._service:
            return False

        try:
            query = f"name='{folder_name}' and mimeType='application/vnd.google-apps.folder' and '{parent_id}' in parents and trashed=false"
            results = self._service.files().list(
                q=query,
                spaces='drive',
                fields='files(id)'
            ).execute()
            return len(results.get('files', [])) > 0
        except:
            return False

    def list_folder(self, folder_id: str) -> List[Dict]:
        """Klasordeki dosyalari listele"""
        if not self._service:
            return []

        try:
            results = self._service.files().list(
                q=f"'{folder_id}' in parents and trashed=false",
                spaces='drive',
                fields='files(id, name, mimeType, size)'
            ).execute()
            return results.get('files', [])
        except Exception as e:
            logger.error(f"Klasor listelenemedi: {e}")
            return []

    def upload_file(
        self,
        file_path: Path,
        parent_id: str,
        file_name: Optional[str] = None
    ) -> Optional[str]:
        """Dosyayi Drive'a yukle"""
        if not self._service:
            return None

        from googleapiclient.http import MediaFileUpload

        if file_name is None:
            file_name = file_path.name

        try:
            # Ayni isimde dosya var mi kontrol et
            query = f"name='{file_name}' and '{parent_id}' in parents and trashed=false"
            results = self._service.files().list(
                q=query,
                spaces='drive',
                fields='files(id)'
            ).execute()
            existing = results.get('files', [])

            media = MediaFileUpload(str(file_path), resumable=True)

            if existing:
                # Guncelle
                file = self._service.files().update(
                    fileId=existing[0]['id'],
                    media_body=media
                ).execute()
            else:
                # Yeni yukle
                file_metadata = {'name': file_name, 'parents': [parent_id]}
                file = self._service.files().create(
                    body=file_metadata,
                    media_body=media,
                    fields='id'
                ).execute()

            logger.debug(f"Yuklendi: {file_name}")
            return file.get('id')

        except Exception as e:
            logger.error(f"Yukleme hatasi: {e}")
            return None

    def download_file(self, file_id: str, dest_path: Path) -> bool:
        """Drive'dan dosya indir"""
        if not self._service:
            return False

        from googleapiclient.http import MediaIoBaseDownload

        try:
            request = self._service.files().get_media(fileId=file_id)
            fh = io.BytesIO()
            downloader = MediaIoBaseDownload(fh, request)

            done = False
            while not done:
                status, done = downloader.next_chunk()

            dest_path.parent.mkdir(parents=True, exist_ok=True)
            with open(dest_path, 'wb') as f:
                fh.seek(0)
                f.write(fh.read())

            logger.debug(f"Indirildi: {dest_path.name}")
            return True

        except Exception as e:
            logger.error(f"Indirme hatasi: {e}")
            return False

    def delete_folder(self, folder_id: str) -> bool:
        """Klasoru sil"""
        if not self._service:
            return False

        try:
            self._service.files().delete(fileId=folder_id).execute()
            logger.info(f"Klasor silindi: {folder_id}")
            return True
        except Exception as e:
            logger.error(f"Silme hatasi: {e}")
            return False

    def find_folder(self, folder_name: str, parent_id: str) -> Optional[str]:
        """Klasor ID'sini bul"""
        if not self._service:
            return None

        try:
            query = f"name='{folder_name}' and '{parent_id}' in parents and trashed=false"
            results = self._service.files().list(
                q=query,
                spaces='drive',
                fields='files(id)'
            ).execute()
            folders = results.get('files', [])
            return folders[0]['id'] if folders else None
        except:
            return None

    def get_pack_folders(self) -> List[Dict]:
        """Tum paket klasorlerini listele"""
        main_folder_id = self.get_main_folder_id()
        if not main_folder_id:
            return []

        items = self.list_folder(main_folder_id)
        return [f for f in items if f['mimeType'] == 'application/vnd.google-apps.folder']

    def download_all_packs(
        self,
        stickers_dir: Path,
        premium_stickers_dir: Path,
        progress_callback: Optional[Callable] = None
    ) -> Tuple[int, int]:
        """Tum paketleri indir"""
        main_folder_id = self.get_main_folder_id()
        if not main_folder_id:
            return 0, 0

        folders = self.get_pack_folders()
        downloaded = 0
        skipped = 0

        for folder in folders:
            folder_name = folder['name']

            # Premium mi normal mi belirle
            if folder_name.startswith("premium_"):
                local_dir = premium_stickers_dir / folder_name[8:]
                display_name = folder_name[8:]
            else:
                local_dir = stickers_dir / folder_name
                display_name = folder_name

            local_dir.mkdir(parents=True, exist_ok=True)

            if progress_callback:
                progress_callback(f"Indiriliyor: {display_name}")

            # Klasordeki dosyalari indir
            files = self.list_folder(folder['id'])

            for file in files:
                if file['mimeType'] == 'application/vnd.google-apps.folder':
                    continue

                dest_path = local_dir / file['name']

                if dest_path.exists():
                    skipped += 1
                    continue

                if self.download_file(file['id'], dest_path):
                    downloaded += 1

        logger.info(f"Indirme tamamlandi: {downloaded} indirildi, {skipped} atlandi")
        return downloaded, skipped

    def upload_all_packs(
        self,
        stickers_dir: Path,
        premium_stickers_dir: Path,
        progress_callback: Optional[Callable] = None
    ) -> Tuple[int, int]:
        """Tum paketleri yukle"""
        main_folder_id = self.get_main_folder_id()
        if not main_folder_id:
            return 0, 0

        uploaded = 0
        skipped = 0

        # Normal stickerlar
        if stickers_dir.exists():
            for pack_dir in stickers_dir.iterdir():
                if not pack_dir.is_dir():
                    continue

                if progress_callback:
                    progress_callback(f"Normal: {pack_dir.name}")

                # Klasor Drive'da mevcut mu?
                if self.folder_exists(pack_dir.name, main_folder_id):
                    skipped += 1
                    continue

                folder_id = self.get_or_create_folder(pack_dir.name, main_folder_id)
                if not folder_id:
                    continue

                files = [f for f in pack_dir.iterdir()
                        if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS]

                for file in files:
                    self.upload_file(file, folder_id)

                uploaded += 1

        # Premium stickerlar
        if premium_stickers_dir.exists():
            for pack_dir in premium_stickers_dir.iterdir():
                if not pack_dir.is_dir():
                    continue

                premium_folder_name = f"premium_{pack_dir.name}"

                if progress_callback:
                    progress_callback(f"Premium: {pack_dir.name}")

                # Klasor Drive'da mevcut mu?
                if self.folder_exists(premium_folder_name, main_folder_id):
                    skipped += 1
                    continue

                folder_id = self.get_or_create_folder(premium_folder_name, main_folder_id)
                if not folder_id:
                    continue

                files = [f for f in pack_dir.iterdir()
                        if f.is_file() and f.suffix.lower() in ALL_EXTENSIONS]

                for file in files:
                    self.upload_file(file, folder_id)

                uploaded += 1

        logger.info(f"Yukleme tamamlandi: {uploaded} yuklendi, {skipped} atlandi")
        return uploaded, skipped
