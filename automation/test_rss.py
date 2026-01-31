import requests
from bs4 import BeautifulSoup

def test_rss():
    # Yeni URL'yi dene
    url = "https://trends.google.com/trending/rss?geo=TR"
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    print(f"Fetching {url}...")
    response = requests.get(url, headers=headers, timeout=10)
    print(f"Status: {response.status_code}")
    if response.status_code == 200:
        soup = BeautifulSoup(response.content, 'xml')
        items = soup.find_all('item')
        print(f"Found {len(items)} items")
        for item in items[:5]:
            print(f"- {item.find('title').text}")
    else:
        print(response.text)

if __name__ == "__main__":
    test_rss()
