"""
GIF Scraper - API gerektirmeden GIF indirme
Reddit, Imgur gibi sitelerden GIF toplar
"""

import requests
import os
import time
import re
import random
from typing import List, Dict
from pathlib import Path


class GoogleGifFetcher:
    """Google Görseller üzerinden GIF arama (Scraper)"""
    
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        })

    def search_gifs(self, query: str, limit: int = 30) -> List[Dict]:
        """Google'da GIF arar ve URL'leri ayıklar"""
        try:
            # Google Görseller GIF arama URL'si
            # tbs=itp:animated -> Sadece hareketli (GIF) olanlar
            search_url = f"https://www.google.com/search?q={query}+gif&tbm=isch&tbs=itp:animated"
            response = self.session.get(search_url, timeout=15)
            response.raise_for_status()
            
            # Basit regex ile GIF URL'lerini bul
            # Google'ın modern yapısında URL'ler genellikle ["https://...", height, width] formatında dizilerde saklanır
            html = response.text
            
            # Daha temiz bir yöntem: ["https://....gif", ...] desenini yakala
            # Genelde [0,"https://...gif",...] şeklinde geçer
            potential_urls = re.findall(r'https?://[^\s"<>]+?\.(?:gif|webp)', html)
            
            gifs = []
            seen_urls = set()
            
            for url in potential_urls:
                # Bazı geçersiz veya çok küçük kaynakları atlat
                if "encrypted-tbn" in url or "gstatic" in url: continue
                if url in seen_urls: continue
                
                # Sadece gif'leri al (webp bazen sorun çıkarabilir)
                if url.lower().endswith('.gif'):
                    seen_urls.add(url)
                    gifs.append({
                        "id": f"google_{hash(url)}",
                        "url": url,
                        "title": f"{query} Sticker",
                        "source": "google_images"
                    })
                
                if len(gifs) >= limit:
                    break
            
            return gifs
            
        except Exception as e:
            print(f"  ⚠️ Google arama hatası: {e}")
            return []


class DirectGifFetcher:
    """API gerektirmeden doğrudan GIF indirme"""
    
    # Popüler GIF kaynakları
    SOURCES = {
        "reddit": "https://www.reddit.com/r/{subreddit}/hot.json?limit=50",
        "imgur_search": "https://api.imgur.com/3/gallery/search/?q={query}",
    }
    
    # Sticker için uygun subreddit'ler
    SUBREDDITS = {
        "kedi": ["cats", "catgifs", "StartledCats", "CatSlaps"],
        "köpek": ["dogs", "doggifs", "WhatsWrongWithYourDog"],
        "komik": ["funny", "gifs", "reactiongifs", "MemeGifs"],
        "cute": ["aww", "Eyebleach", "AnimalsBeingDerps"],
        "anime": ["animegifs", "animegifsound"],
        "meme": ["MemeGifs", "reactiongifs", "HighQualityGifs"],
        "cat": ["cats", "catgifs", "StartledCats"],
        "dog": ["dogs", "doggifs"],
        "funny": ["funny", "gifs", "reactiongifs"],
        "love": ["wholesomegifs", "MadeMeSmile"],
        "default": ["gifs", "reactiongifs", "HighQualityGifs"]
    }
    
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        })
        self.google_fetcher = GoogleGifFetcher()
    
    def _get_subreddits_for_query(self, query: str) -> List[str]:
        """Arama sorgusuna uygun subreddit'leri bul"""
        query_lower = query.lower()
        
        for keyword, subreddits in self.SUBREDDITS.items():
            if keyword in query_lower:
                return subreddits
        
        return self.SUBREDDITS["default"]
    
    def search_gifs(self, query: str, limit: int = 20) -> List[Dict]:
        """
        Reddit ve Google'dan GIF'leri ara
        """
        gifs = []
        
        # Önce Google'dan dene (Daha güncel ve bol sonuç)
        print(f"   📡 Google Görseller taranıyor...")
        google_results = self.google_fetcher.search_gifs(query, limit=limit)
        gifs.extend(google_results)
        
        if len(gifs) >= limit:
            return gifs[:limit]

        # Yetmezse Reddit'ten destek al
        print(f"   📡 Reddit taranıyor...")
        subreddits = self._get_subreddits_for_query(query)
        
        for subreddit in subreddits[:2]:  # İlk 2 subreddit'ten al
            try:
                url = f"https://www.reddit.com/r/{subreddit}/hot.json?limit=100"
                response = self.session.get(url, timeout=15)
                
                if response.status_code != 200:
                    continue
                
                data = response.json()
                posts = data.get("data", {}).get("children", [])
                
                for post in posts:
                    post_data = post.get("data", {})
                    post_url = post_data.get("url", "")
                    
                    # GIF URL'lerini filtrele
                    if self._is_gif_url(post_url):
                        # Gifv'yi gif'e çevir
                        if post_url.endswith(".gifv"):
                            post_url = post_url.replace(".gifv", ".gif")
                        
                        gifs.append({
                            "id": post_data.get("id", ""),
                            "url": post_url,
                            "title": post_data.get("title", ""),
                            "size": 0,  # Bilinmiyor
                            "source": f"reddit/{subreddit}"
                        })
                        
                        if len(gifs) >= limit:
                            break
                
                time.sleep(0.5)  # Rate limiting
                
            except Exception as e:
                print(f"  ⚠️ Reddit hatası ({subreddit}): {e}")
                continue
        
        return gifs[:limit]
    
    def _is_gif_url(self, url: str) -> bool:
        """URL'nin GIF olup olmadığını kontrol et"""
        gif_patterns = [
            r"\.gif$",
            r"\.gifv$",
            r"i\.imgur\.com.*\.gif",
            r"i\.redd\.it.*\.gif",
            r"media\.giphy\.com",
            r"gfycat\.com",
        ]
        
        for pattern in gif_patterns:
            if re.search(pattern, url, re.IGNORECASE):
                return True
        
        return False
    
    def download_gif(self, url: str, output_path: str, max_size_mb: float = 8.0) -> bool:
        """GIF indir"""
        try:
            response = self.session.get(url, timeout=60, stream=True)
            response.raise_for_status()
            
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            
            with open(output_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            
            # Boyut kontrolü
            file_size = os.path.getsize(output_path)
            if file_size > max_size_mb * 1024 * 1024:
                os.remove(output_path)
                return False
            
            # Minimum boyut kontrolü (çok küçük dosyalar muhtemelen hatalı)
            if file_size < 10 * 1024:  # 10KB'dan küçükse
                os.remove(output_path)
                return False
            
            return True
            
        except Exception as e:
            print(f"  ⚠️ İndirme hatası: {e}")
            if os.path.exists(output_path):
                os.remove(output_path)
            return False
    
    def download_multiple(self, gifs: List[Dict], output_dir: str, 
                          max_count: int = 15, delay: float = 0.5) -> List[str]:
        """Birden fazla GIF indir"""
        downloaded = []
        
        # GIF'leri karıştır (çeşitlilik için)
        shuffled_gifs = gifs.copy()
        random.shuffle(shuffled_gifs)
        
        for gif in shuffled_gifs:
            if len(downloaded) >= max_count:
                break
            
            filename = f"sticker_{len(downloaded)+1:02d}.gif"
            output_path = os.path.join(output_dir, filename)
            
            print(f"  📥 İndiriliyor: {filename}...")
            
            if self.download_gif(gif["url"], output_path):
                file_size = os.path.getsize(output_path)
                downloaded.append(output_path)
                print(f"     ✓ Başarılı ({file_size / 1024:.1f}KB)")
            
            time.sleep(delay)
        
        return downloaded


# Test
if __name__ == "__main__":
    print("🔍 Direct GIF Fetcher Test")
    print("-" * 40)
    
    fetcher = DirectGifFetcher()
    gifs = fetcher.search_gifs("cat funny", limit=5)
    
    for gif in gifs:
        print(f"  ID: {gif['id']}")
        print(f"  URL: {gif['url'][:60]}...")
        print(f"  Source: {gif['source']}")
        print()
