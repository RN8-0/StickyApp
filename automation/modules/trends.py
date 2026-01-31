import requests
from bs4 import BeautifulSoup
from pytrends.request import TrendReq
from typing import List, Dict
import time


def get_trending_topics(limit: int = 20) -> List[Dict]:
    """
    US ve TR trendlerini harmanlayarak global popüler konuları çeker
    """
    countries = ["US", "TR"]
    all_topics = []
    
    for country in countries:
        try:
            url = f"https://trends.google.com/trending/rss?geo={country}"
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
            }
            response = requests.get(url, headers=headers, timeout=10)
            
            if response.status_code == 200:
                soup = BeautifulSoup(response.content, 'xml')
                items = soup.find_all('item')
                
                for i, item in enumerate(items):
                    title = item.find('title').text
                    # Zaten eklenmişse (global çakışma) atla
                    if any(t['title'].lower() == title.lower() for t in all_topics):
                        continue
                        
                    all_topics.append({
                        "title": title,
                        "country": country,
                        "source": "google_trends_rss"
                    })
        except Exception as e:
            print(f"⚠️ {country} trend hatası: {e}")

    # Limit kadarını döndür
    return all_topics[:limit]

    # 2. Yöntem: Pytrends (Yedek)
    try:
        pytrends = TrendReq(hl='tr-TR', tz=180)
        # Pytrends ülke isimlerini farklı bekler
        geo_name = 'turkey' if country == 'TR' else 'united_states'
        trending = pytrends.trending_searches(pn=geo_name)
        
        topics = []
        for i, topic in enumerate(trending[0].tolist()[:limit]):
            topics.append({
                "title": topic,
                "rank": i + 1,
                "source": "pytrends"
            })
        
        return topics
        
    except Exception as e:
        print(f"⚠️ Google Trends kütüphane hatası: {e}")
        return []


def get_realtime_trends(country: str = "TR") -> List[Dict]:
    """
    Gerçek zamanlı trendleri çeker (daha güncel ama daha az güvenilir)
    """
    try:
        pytrends = TrendReq(hl='tr-TR', tz=180)
        
        # Realtime trends (son 24 saat)
        realtime = pytrends.realtime_trending_searches(pn='TR' if country == 'TR' else 'US')
        
        topics = []
        if not realtime.empty:
            for _, row in realtime.head(10).iterrows():
                topics.append({
                    "title": row.get('title', row.get('entityNames', ['Unknown'])[0] if isinstance(row.get('entityNames'), list) else 'Unknown'),
                    "source": "realtime"
                })
        
        return topics
        
    except Exception as e:
        print(f"⚠️ Realtime trends hatası: {e}")
        return []


def search_related_topics(keyword: str) -> List[str]:
    """
    Bir anahtar kelimeye ilişkin benzer konuları bulur
    """
    try:
        pytrends = TrendReq(hl='tr-TR', tz=180)
        pytrends.build_payload([keyword], timeframe='now 7-d')
        
        related = pytrends.related_queries()
        
        topics = []
        if keyword in related and related[keyword]['top'] is not None:
            for _, row in related[keyword]['top'].head(5).iterrows():
                topics.append(row['query'])
        
        return topics
        
    except Exception as e:
        print(f"⚠️ Related topics hatası: {e}")
        return []


# Test
if __name__ == "__main__":
    print("🔍 Google Trends Test")
    print("-" * 40)
    
    topics = get_trending_topics("TR", 5)
    for t in topics:
        print(f"  {t['rank']}. {t['title']}")
