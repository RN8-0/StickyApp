"""
GIF Fetcher - Tenor ve GIPHY API'lerinden GIF'leri indirir
"""

import requests
import os
import time
from typing import List, Dict, Optional
from pathlib import Path


class GiphyFetcher:
    """GIPHY API ile GIF arama ve indirme"""
    
    BASE_URL = "https://api.giphy.com/v1/gifs"
    
    def __init__(self, api_key: str):
        self.api_key = api_key
        self.session = requests.Session()
    
    def search_gifs(self, query: str, limit: int = 15, rating: str = "g") -> List[Dict]:
        """
        GIPHY'de GIF arar
        
        Args:
            query: Arama sorgusu
            limit: Maksimum sonuç sayısı (max 50)
            rating: İçerik filtresi (g, pg, pg-13, r)
            
        Returns:
            [{"id": "...", "url": "...", "preview": "...", "size": ...}, ...]
        """
        try:
            url = f"{self.BASE_URL}/search"
            params = {
                "api_key": self.api_key,
                "q": query,
                "limit": min(limit, 50),
                "rating": rating,
                "lang": "tr"
            }
            
            response = self.session.get(url, params=params, timeout=30)
            response.raise_for_status()
            data = response.json()
            
            gifs = []
            for result in data.get("data", []):
                images = result.get("images", {})
                
                # Orijinal GIF'i al
                original = images.get("original", {})
                downsized = images.get("downsized", {})
                preview = images.get("preview_gif", {})
                
                gif_url = downsized.get("url") or original.get("url")
                
                if gif_url:
                    gifs.append({
                        "id": result.get("id"),
                        "url": gif_url,
                        "size": int(downsized.get("size", 0) or original.get("size", 0)),
                        "width": int(original.get("width", 0)),
                        "height": int(original.get("height", 0)),
                        "preview": preview.get("url", gif_url)
                    })
            
            return gifs
            
        except Exception as e:
            if "401" in str(e):
                print(f"\n🛑 GIPHY YETKİ HATASI (401): Anahtarınız geçersiz veya SDK anahtarı.")
                print("💡 Çözüm: Giphy Dashboard'da 'Web API' anahtarı oluşturun.\n")
            else:
                print(f"⚠️ GIPHY arama hatası: {e}")
            return []
    
    def download_gif(self, url: str, output_path: str, max_size_mb: float = 5.0) -> bool:
        """
        GIF dosyasını indirir
        
        Args:
            url: GIF URL'i
            output_path: Kayıt yolu
            max_size_mb: Maksimum dosya boyutu (MB)
            
        Returns:
            Başarılı ise True
        """
        try:
            # İndir
            response = self.session.get(url, timeout=60, stream=True)
            response.raise_for_status()
            
            # Klasörü oluştur
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            
            # Dosyaya yaz
            with open(output_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            
            # Boyut kontrolü
            file_size = os.path.getsize(output_path)
            if file_size > max_size_mb * 1024 * 1024:
                print(f"  ⚠️ Dosya çok büyük: {file_size / 1024 / 1024:.1f}MB, atlanıyor")
                os.remove(output_path)
                return False
            
            return True
            
        except Exception as e:
            print(f"  ⚠️ İndirme hatası: {e}")
            return False
    
    def download_multiple(self, gifs: List[Dict], output_dir: str, 
                          max_count: int = 15, delay: float = 0.3) -> List[str]:
        """
        Birden fazla GIF indirir
        
        Args:
            gifs: GIF listesi (search_gifs çıktısı)
            output_dir: Kayıt klasörü
            max_count: Maksimum indirme sayısı
            delay: İndirmeler arası bekleme (saniye)
            
        Returns:
            Başarılı indirilen dosya yolları
        """
        downloaded = []
        
        for i, gif in enumerate(gifs[:max_count + 5]):  # Birkaç fazla dene
            if len(downloaded) >= max_count:
                break
                
            filename = f"sticker_{len(downloaded)+1:02d}.gif"
            output_path = os.path.join(output_dir, filename)
            
            print(f"  📥 İndiriliyor: {filename}...")
            
            if self.download_gif(gif["url"], output_path):
                downloaded.append(output_path)
                print(f"     ✓ Başarılı ({os.path.getsize(output_path) / 1024:.1f}KB)")
            
            time.sleep(delay)
        
        return downloaded


class TenorFetcher:
    """Tenor API ile GIF arama ve indirme"""
    
    BASE_URL = "https://tenor.googleapis.com/v2"
    
    def __init__(self, api_key: str):
        self.api_key = api_key
        self.session = requests.Session()
    
    def search_gifs(self, query: str, limit: int = 15, content_filter: str = "medium") -> List[Dict]:
        """
        Tenor'da GIF arar
        """
        try:
            url = f"{self.BASE_URL}/search"
            params = {
                "key": self.api_key,
                "q": query,
                "limit": min(limit, 50),
                "contentfilter": content_filter,
                "media_filter": "gif,tinygif",
                "client_key": "sticky_automation"
            }
            
            response = self.session.get(url, params=params, timeout=30)
            response.raise_for_status()
            data = response.json()
            
            gifs = []
            for result in data.get("results", []):
                media = result.get("media_formats", {})
                gif_data = media.get("gif") or media.get("tinygif") or media.get("mediumgif")
                
                if gif_data:
                    gifs.append({
                        "id": result.get("id"),
                        "url": gif_data.get("url"),
                        "size": gif_data.get("size", 0),
                        "dims": gif_data.get("dims", [0, 0]),
                        "preview": media.get("tinygif", {}).get("url", gif_data.get("url"))
                    })
            
            return gifs
            
        except Exception as e:
            print(f"⚠️ Tenor arama hatası: {e}")
            return []
    
    def download_gif(self, url: str, output_path: str, max_size_mb: float = 5.0) -> bool:
        """GIF dosyasını indirir"""
        try:
            response = self.session.get(url, timeout=60, stream=True)
            response.raise_for_status()
            
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            
            with open(output_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            
            return True
            
        except Exception as e:
            print(f"  ⚠️ İndirme hatası: {e}")
            return False
    
    def download_multiple(self, gifs: List[Dict], output_dir: str, 
                          max_count: int = 15, delay: float = 0.5) -> List[str]:
        """Birden fazla GIF indirir"""
        downloaded = []
        
        for i, gif in enumerate(gifs[:max_count]):
            filename = f"sticker_{i+1:02d}.gif"
            output_path = os.path.join(output_dir, filename)
            
            print(f"  📥 İndiriliyor: {filename}...")
            
            if self.download_gif(gif["url"], output_path):
                downloaded.append(output_path)
                print(f"     ✓ Başarılı ({os.path.getsize(output_path) / 1024:.1f}KB)")
            
            time.sleep(delay)
        
        return downloaded


from .direct_fetcher import DirectGifFetcher
import random


class MultiFetcher:
    """
    Giphy, Tenor ve Reddit API'lerini birleştirerek en iyi sonuçları sunar.
    Birden fazla API anahtarını yönetir ve sonuçları karma bir şekilde döndürür.
    """
    
    def __init__(self, config: Dict):
        self.config = config
        
        # API Anahtarlarını hazırla
        self.giphy_keys = config.get("giphy_api_keys", [])
        if not self.giphy_keys and config.get("giphy_api_key"):
            self.giphy_keys = [config["giphy_api_key"]]
            
        self.tenor_keys = config.get("tenor_api_keys", [])
        if not self.tenor_keys and config.get("tenor_api_key"):
            self.tenor_keys = [config["tenor_api_key"]]
            
        # Anahtarlar boşsa varsayılan (test) anahtarı ekle
        if not self.giphy_keys:
            self.giphy_keys = ["dc6zaTOxFJmzC"] # Giphy Public Beta Key
            
        # Fetcher örneklerini oluştur
        self.giphy_fetchers = [GiphyFetcher(key) for key in self.giphy_keys]
        self.tenor_fetchers = [TenorFetcher(key) for key in self.tenor_keys]
        self.direct_fetcher = DirectGifFetcher()
        
    def search_gifs(self, query: str, limit: int = 35) -> List[Dict]:
        """
        Tüm kaynaklardan (Tenor, Giphy, Google, Reddit) karma arama yapar.
        Limit dolana kadar farklı sorgu varyasyonları dener.
        """
        all_results = []
        unique_urls = set()
        
        # Sorgu varyasyonları (Eğer ilk turda yetmezse sırayla dene)
        queries = [
            query,
            f"{query} sticker",
            f"{query} funny gif",
            f"{query} animated"
        ]
        
        print(f"🔍 MultiFetcher: '{query}' için en iyi çıkartmalar toplanıyor...")
        
        for q in queries:
            if len(all_results) >= limit:
                break
                
            current_limit = limit - len(all_results)
            search_limit = max(current_limit, 15)
            
            # 1. Tenor'dan ara
            for i, fetcher in enumerate(self.tenor_fetchers):
                if len(all_results) >= limit: break
                print(f"   📡 Tenor API-{i+1} taranıyor: '{q}'...")
                results = fetcher.search_gifs(q, limit=search_limit)
                for r in results:
                    if r['url'] not in unique_urls:
                        unique_urls.add(r['url'])
                        r["priority"] = 1
                        all_results.append(r)
            
            # 2. Giphy'den ara
            for i, fetcher in enumerate(self.giphy_fetchers):
                if len(all_results) >= limit: break
                print(f"   📡 GIPHY API-{i+1} taranıyor: '{q}'...")
                results = fetcher.search_gifs(q, limit=search_limit)
                for r in results:
                    if r['url'] not in unique_urls:
                        unique_urls.add(r['url'])
                        r["priority"] = 2
                        all_results.append(r)
            
            # 3. Google ve Reddit (DirectFetcher)
            if len(all_results) < limit:
                print(f"   📡 Google & Reddit taranıyor: '{q}'...")
                results = self.direct_fetcher.search_gifs(q, limit=search_limit)
                for r in results:
                    if r['url'] not in unique_urls:
                        unique_urls.add(r['url'])
                        r["priority"] = 3
                        all_results.append(r)
                        
        # Kalite önceliğine göre sırala
        all_results.sort(key=lambda x: x.get("priority", 99))
        
        print(f"✨ Toplam {len(all_results)} potansiyel çıkartma bulundu.")
        return all_results[:limit]

    def download_multiple(self, gifs: List[Dict], output_dir: str, 
                          max_count: int = 25, delay: float = 0.2) -> List[str]:
        """İndirme işlemini en uygun fetcher ile yapar"""
        # MultiFetcher ortak indirme mantığı
        # GiphyFetcher'daki indirme mantığını kullanabiliriz (en genel olanı)
        fetcher = self.giphy_fetchers[0] if self.giphy_fetchers else GiphyFetcher("dc6zaTOxFJmzC")
        return fetcher.download_multiple(gifs, output_dir, max_count=max_count, delay=delay)


def search_sticker_gifs(api_key: str, query: str, limit: int = 15, use_giphy: bool = True, giphy_key: str = None) -> List[Dict]:
    """Geriye dönük uyumluluk için"""
    fetcher = GiphyFetcher(giphy_key or api_key)
    return fetcher.search_gifs(query, limit=limit)
