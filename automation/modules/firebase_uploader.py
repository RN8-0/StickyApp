"""
Firebase Uploader - Dosyaları Firebase'e yükler ve Firestore'a kaydeder
"""

import os
import json
import uuid
from datetime import datetime
from typing import List, Dict, Optional
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore, storage


class FirebaseUploader:
    """Firebase Storage ve Firestore işlemleri"""
    
    def __init__(self, service_account_path: str, storage_bucket: str):
        """
        Firebase'i başlat
        
        Args:
            service_account_path: Service account JSON dosyası yolu
            storage_bucket: Storage bucket adı
        """
        self.bucket_name = storage_bucket
        
        # Firebase'i başlat (zaten başlatılmışsa hata vermez)
        if not firebase_admin._apps:
            cred = credentials.Certificate(service_account_path)
            firebase_admin.initialize_app(cred, {
                'storageBucket': storage_bucket
            })
        
        self.db = firestore.client()
        self.bucket = storage.bucket()
    
    def upload_file(self, local_path: str, remote_path: str) -> str:
        """
        Dosyayı Firebase Storage'a yükler
        
        Args:
            local_path: Yerel dosya yolu
            remote_path: Storage'daki hedef yol
            
        Returns:
            Dosyanın public URL'i
        """
        blob = self.bucket.blob(remote_path)
        
        # Content type belirle
        if local_path.endswith('.webp'):
            content_type = 'image/webp'
        elif local_path.endswith('.png'):
            content_type = 'image/png'
        elif local_path.endswith('.gif'):
            content_type = 'image/gif'
        else:
            content_type = 'application/octet-stream'
        
        blob.upload_from_filename(local_path, content_type=content_type)
        blob.make_public()
        
        return blob.public_url
    
    def upload_pack_as_draft(self, pack_id: str, pack_name: str, 
                             webp_files: List[str], tray_file: str,
                             category: str = "other",
                             source_query: str = "",
                             localized_names: Dict[str, str] = None,
                             emojis_list: List[List[str]] = None) -> Dict:
        """
        Paketi taslak olarak Firebase'e yükler
        """
        storage_path = f"automation_drafts/{pack_id}"
        stickers = []
        
        # Sticker'ları yükle
        print(f"  ☁️ Sticker'lar yükleniyor...")
        for i, webp_path in enumerate(webp_files):
            filename = os.path.basename(webp_path)
            remote_path = f"{storage_path}/{filename}"
            
            url = self.upload_file(webp_path, remote_path)
            
            # Bu sticker için emojileri al
            current_emojis = emojis_list[i] if emojis_list and i < len(emojis_list) else ["😀"]
            
            stickers.append({
                "image_file": filename,
                "url": url,
                "emojis": current_emojis
            })
            
            print(f"     ✓ {filename} ({', '.join(current_emojis)})")
        
        # Tray'i yükle
        print(f"  ☁️ Tray yükleniyor...")
        tray_filename = os.path.basename(tray_file)
        tray_remote_path = f"{storage_path}/{tray_filename}"
        tray_url = self.upload_file(tray_file, tray_remote_path)
        print(f"     ✓ {tray_filename}")
        
        # Firestore'a kaydet (automation_drafts koleksiyonuna)
        pack_data = {
            "id": pack_id,
            "name": pack_name,
            "publisher": "Sticky Bot",
            "tray_image_file": tray_filename,
            "tray_url": tray_url,
            "stickers": stickers,
            "sticker_count": len(stickers),
            "category": category,
            "is_premium": False,
            "is_active": False,  # Taslak - onay bekliyor
            "is_animated": True,
            "version": "1",
            "image_data_version": str(int(datetime.now().timestamp() * 1000)),
            "download_count": 0,
            "view_count": 0,
            "favorite_count": 0,
            "created_at": datetime.now().strftime("%Y-%m-%d"),
            "source_query": source_query,
            "automation_created": True,
            "storage_path": storage_path
        }
        
        # Yerelleştirilmiş isimleri ekle
        if localized_names:
            pack_data.update(localized_names)
        
        # Firestore'a yaz
        print(f"  📝 Firestore'a kaydediliyor...")
        self.db.collection("automation_drafts").document(pack_id).set(pack_data)
        print(f"     ✓ Kaydedildi: automation_drafts/{pack_id}")
        
        return pack_data
    
    def get_drafts(self) -> List[Dict]:
        """Tüm taslakları listele"""
        docs = self.db.collection("automation_drafts").stream()
        return [doc.to_dict() for doc in docs]
    
    def approve_draft(self, pack_id: str) -> bool:
        """
        Taslağı onayla ve ana koleksiyona taşı
        
        Args:
            pack_id: Paket ID'si
            
        Returns:
            Başarılı ise True
        """
        try:
            # Taslağı oku
            draft_ref = self.db.collection("automation_drafts").document(pack_id)
            draft = draft_ref.get()
            
            if not draft.exists:
                print(f"⚠️ Taslak bulunamadı: {pack_id}")
                return False
            
            pack_data = draft.to_dict()
            
            # Storage'daki dosyaları taşı
            old_path = pack_data.get("storage_path", f"automation_drafts/{pack_id}")
            new_path = f"stickers/{pack_id}"
            
            # Dosyaları yeni konuma kopyala
            blobs = list(self.bucket.list_blobs(prefix=old_path))
            for blob in blobs:
                new_blob_name = blob.name.replace(old_path, new_path)
                new_blob = self.bucket.blob(new_blob_name)
                
                # Kopyala
                self.bucket.copy_blob(blob, self.bucket, new_blob_name)
                new_blob.make_public()
                
                # Eskiyi sil
                blob.delete()
            
            # URL'leri güncelle
            for sticker in pack_data.get("stickers", []):
                sticker["url"] = sticker["url"].replace(old_path, new_path)
            pack_data["tray_url"] = pack_data["tray_url"].replace(old_path, new_path)
            
            # Durumu güncelle
            pack_data["is_active"] = True
            pack_data["storage_path"] = new_path
            del pack_data["automation_created"]  # Bu alanı kaldır
            
            # Ana koleksiyona ekle
            self.db.collection("stickers").document(pack_id).set(pack_data)
            
            # Taslağı sil
            draft_ref.delete()
            
            print(f"✓ Paket onaylandı ve yayına alındı: {pack_id}")
            return True
            
        except Exception as e:
            print(f"⚠️ Onaylama hatası: {e}")
            return False
    
    def delete_draft(self, pack_id: str) -> bool:
        """
        Taslağı ve dosyalarını sil
        
        Args:
            pack_id: Paket ID'si
            
        Returns:
            Başarılı ise True
        """
        try:
            # Taslağı oku
            draft_ref = self.db.collection("automation_drafts").document(pack_id)
            draft = draft_ref.get()
            
            if not draft.exists:
                print(f"⚠️ Taslak bulunamadı: {pack_id}")
                return False
            
            pack_data = draft.to_dict()
            storage_path = pack_data.get("storage_path", f"automation_drafts/{pack_id}")
            
            # Storage'daki dosyaları sil
            blobs = list(self.bucket.list_blobs(prefix=storage_path))
            for blob in blobs:
                blob.delete()
            
            # Firestore'dan sil
            draft_ref.delete()
            
            print(f"✓ Taslak silindi: {pack_id}")
            return True
            
        except Exception as e:
            print(f"⚠️ Silme hatası: {e}")
            return False


# Test
if __name__ == "__main__":
    print("☁️ Firebase Uploader Test")
    print("-" * 40)
    print("Bu modül doğrudan çalıştırılamaz.")
    print("Ana bot scripti üzerinden kullanın.")
