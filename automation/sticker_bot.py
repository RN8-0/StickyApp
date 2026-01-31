#!/usr/bin/env python3
"""
🤖 Sticky Automation Bot
Trend bazlı otomatik sticker paketi oluşturma

Kullanım:
    python sticker_bot.py                    # Trend bazlı otomatik
    python sticker_bot.py --query "kedi"     # Manuel arama
    python sticker_bot.py --list             # Taslakları listele
    python sticker_bot.py --approve <id>     # Taslağı onayla
    python sticker_bot.py --delete <id>      # Taslağı sil
"""

import os
import sys
import json
import argparse
import shutil
import uuid
import time
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Optional

# Rich konsol çıktıları için
try:
    from rich.console import Console
    from rich.table import Table
    from rich.progress import Progress, SpinnerColumn, TextColumn
    from rich.panel import Panel
    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False
    print("💡 Daha güzel çıktı için: pip install rich")

# Modülleri import et
sys.path.insert(0, os.path.dirname(__file__))
from modules.trends import get_trending_topics
from modules.gif_fetcher import TenorFetcher, GiphyFetcher, MultiFetcher
from modules.direct_fetcher import DirectGifFetcher
from modules.gif_processor import GifProcessor
from modules.firebase_uploader import FirebaseUploader

# Çeviri ve AI
from deep_translator import GoogleTranslator
from google import genai
import random


class StickerBot:
    """Ana bot sınıfı"""
    
    # Yaratıcı isimler için sıfatlar
    ADJECTIVES = {
        "tr": ["Çılgın", "Komik", "Efsane", "Süper", "Havalı", "Minnoş", "Harika", "Tatlı", "Eğlenceli", "Yaramaz"],
        "en": ["Crazy", "Funny", "Epic", "Super", "Cool", "Cute", "Awesome", "Sweet", "Fun", "Naughty"]
    }
    
    # Desteklenen diller ve kodları
    LANGUAGES = {
        "tr": "turkish",
        "en": "english",
        "es": "spanish",
        "zh-CN": "chinese (simplified)",
        "ar": "arabic",
        "hi": "hindi",
        "pt": "portuguese"
    }
    
    # Emojiler için anahtar kelime haritası
    EMOJI_MAP = {
        "cat": ["🐱", "😺", "😸", "😻"],
        "kedi": ["🐱", "😺", "😹", "😻"],
        "dog": ["🐶", "🐕", "🦮", "🐩"],
        "köpek": ["🐶", "🐕", "🦴"],
        "funny": ["😂", "🤣", "😆", "😅"],
        "komik": ["😂", "🤣", "😜", "🤭"],
        "laugh": ["😂", "🤣", "😆"],
        "love": ["❤️", "😍", "🥰", "💕"],
        "aşk": ["❤️", "😍", "💘"],
        "angry": ["😡", "😠", "💢", "🤬"],
        "kızgın": ["😡", "😠", "😤"],
        "sad": ["😢", "😭", "😞", "😔"],
        "üzgün": ["😢", "😭", "💔"],
        "cool": ["😎", "💎", "✨"],
        "fire": ["🔥", "💥", "⚡"],
        "ateş": ["🔥", "🌋"],
        "dance": ["💃", "🕺", "👯", "🎶"],
        "dans": ["💃", "🕺", "🎵"],
        "meme": ["🤭", "😏", "🧐"],
        "wow": ["😱", "😲", "🤯"],
        "sleep": ["😴", "💤", "🛌"],
        "food": ["🍔", "🍕", "🍰", "😋"],
        "yemek": ["🍔", "🥘", "🥗"],
        "good morning": ["☀️", "🌅", "☕"],
        "günaydın": ["☀️", "🌅", "☕"],
        "good night": ["🌙", "😴", "✨"],
        "iyi geceler": ["🌙", "💤", "🌌"],
        "birthday": ["🎂", "🎉", "🎈", "🎁"],
        "doğum günü": ["🎂", "🎊", "🎈"]
    }

    def __init__(self, config_path: str = "config.json"):
        self.console = Console() if RICH_AVAILABLE else None
        self.config = self._load_config(config_path)
        self.output_dir = os.path.join(os.path.dirname(__file__), "output", "packs")
        
        self.gif_fetcher = MultiFetcher(self.config)
        self._print("📡 MultiFetcher Aktif (Giphy + Tenor + Reddit)", "info")
        
        # Gemini AI Başlat (Yeni google-genai SDK ile)
        self.gemini_enabled = False
        gemini_key = self.config.get("gemini_api_key")
        if gemini_key:
            try:
                self.gemini_client = genai.Client(api_key=gemini_key)
                self.gemini_model_id = 'gemini-1.5-flash-lite'
                self.gemini_enabled = True
                self._print("✨ Gemini AI Pro Aktif: Akıllı isimlendirme ve çeviri devrede.", "success")
            except Exception as e:
                self._print(f"⚠️ Gemini başlatılamadı: {e}", "warning")
        
        self.processor = GifProcessor()
        
        # Firebase (service account varsa)
        sa_path = os.path.join(os.path.dirname(__file__), self.config["firebase"]["service_account"])
        if os.path.exists(sa_path):
            self.firebase = FirebaseUploader(
                sa_path,
                self.config["firebase"]["storage_bucket"]
            )
        else:
            self.firebase = None
            self._print("⚠️ Service account bulunamadı. Firebase yüklemesi devre dışı.", "warning")
    
    def _load_config(self, path: str) -> dict:
        """Config dosyasını yükle"""
        config_path = os.path.join(os.path.dirname(__file__), path)
        with open(config_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    
    def _print(self, message: str, style: str = "default"):
        """Konsola yazdır"""
        if self.console:
            styles = {
                "default": "",
                "success": "green",
                "warning": "yellow",
                "error": "red",
                "info": "blue"
            }
            self.console.print(message, style=styles.get(style, ""))
        else:
            print(message)
    
    def _get_category(self, query: str) -> str:
        """Arama sorgusundan kategori belirle (Gelişmiş)"""
        query_lower = query.lower()
        categories_map = self.config.get("categories_map", {})
        
        # Tam eşleşme ara
        if query_lower in categories_map:
            return categories_map[query_lower]
            
        # Anahtar kelime ara
        for keyword, category in categories_map.items():
            if keyword in query_lower:
                return category
        
        return "other"

    def _get_emojis_for_gif(self, title: str, query: str, category: str = "other") -> List[str]:
        """GIF başlığı, sorgu ve kategoriye göre uygun emojileri bul"""
        text = (title + " " + query).lower()
        found_emojis = []
        
        # 1. Anahtar kelime eşleşmesi
        for keyword, emojis in self.EMOJI_MAP.items():
            if keyword in text:
                selected = random.sample(emojis, min(len(emojis), 2))
                found_emojis.extend(selected)
        
        # 2. Eğer eşleşme yoksa kategoriye özel emoji havuzundan rastgele seç
        if not found_emojis:
            category_defaults = {
                "animals": ["🐱", "🐶", "🦊", "🦁", "🐸", "🐻‍❄️", "🐼"],
                "funny": ["😂", "🤣", "😝", "😜", "🤭", "🙃"],
                "romantic": ["❤️", "💖", "😍", "🌹", "💋", "💌"],
                "gaming": ["🎮", "👾", "🕹️", "⚡", "🔥"],
                "reactions": ["😲", "🤔", "🧐", "😎", "🤩", "🤯"],
                "music": ["🎵", "🎶", "🎸", "🎧", "🎹"],
                "other": ["😀", "✨", "🌟", "🔥", "🌈", "🎈", "✌️"]
            }
            
            pool = category_defaults.get(category, category_defaults["other"])
            # Her sticker için farklı gelmesi için rastgele 1-2 tane seç
            found_emojis = random.sample(pool, min(len(pool), 2))
            
        # Benzersiz yap ve max 3 tane ver
        return list(set(found_emojis))[:3]
    
    def _generate_pack_id(self) -> str:
        """Benzersiz paket ID'si oluştur"""
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
        unique = uuid.uuid4().hex[:6]
        return f"auto_{timestamp}_{unique}"
    
    def _generate_pack_name(self, query: str) -> str:
        """AI ile yaratıcı paket adı oluştur"""
        if self.gemini_enabled:
            try:
                prompt = f"Write a creative, short (max 3 words) sticker pack title for the search query: '{query}'. Provide ONLY the title, no quotes or explanation."
                response = self.gemini_client.models.generate_content(
                    model=self.gemini_model_id,
                    contents=prompt
                )
                return response.text.strip().replace('"', '')
            except:
                pass
        
        # Fallback: Eski basit yöntem
        words = query.strip().split()
        return " ".join(word.capitalize() for word in words[:3])

    def _translate_names(self, name: str, source_lang: str = "auto") -> Dict[str, str]:
        """Tüm diller için çeviri ve yerelleştirilmiş isimleri oluştur"""
        self._print(f"🌍 İsim çevriliyor: {name}...", "info")
        localized = {}
        
        # 1. Gemini ile Akıllı Çeviri (Deneyelim)
        if self.gemini_enabled:
            try:
                langs_str = ", ".join(self.LANGUAGES.keys())
                prompt = (
                    f"Translate the sticker pack name '{name}' into these language codes: {langs_str}. "
                    "Make them sound natural and catchy for a sticker app. "
                    "For English ('en'), you can use the original or slightly improve it. "
                    "Return ONLY a JSON object where keys are language codes and values are the titles. "
                    "No markdown, just raw JSON."
                )
                response = self.gemini_client.models.generate_content(
                    model=self.gemini_model_id,
                    contents=prompt
                )
                # JSON temizle (bazen ```json ekleyebiliyor)
                json_str = response.text.strip()
                if "```json" in json_str:
                    json_str = json_str.split("```json")[1].split("```")[0].strip()
                elif "```" in json_str:
                    json_str = json_str.split("```")[1].split("```")[0].strip()
                
                ai_results = json.loads(json_str)
                for code, translated in ai_results.items():
                    clean_code = "zh" if code == "zh-CN" else code
                    field_name = "name" if clean_code == "en" else f"name_{clean_code}"
                    localized[field_name] = translated
                    self._print(f"   {clean_code.upper()}: {translated} (AI)")
                
                if localized: return localized
            except Exception as e:
                self._print(f"   ⚠️ Gemini çeviri hatası: {e}. Eski sisteme dönülüyor.", "warning")

        # 2. Fallback: Eski Manuel Sistem (Google Translator)
        actual_source = source_lang
        if source_lang == "auto" and all(ord(c) < 128 for c in name):
            actual_source = "en"

        for code, lang_name in self.LANGUAGES.items():
            try:
                translated = GoogleTranslator(source=actual_source, target=code).translate(name)
                if translated.lower() == name.lower() and code != actual_source:
                    translated = GoogleTranslator(source="auto", target=code).translate(name)

                # Dile özel sıfat ekle
                if code in self.ADJECTIVES:
                    chance = random.random()
                    if chance < 0.6: 
                        adj = random.choice(self.ADJECTIVES[code])
                        translated = f"{adj} {translated}"

                clean_code = "zh" if code == "zh-CN" else code
                field_name = "name" if clean_code == "en" else f"name_{clean_code}"
                localized[field_name] = translated
                self._print(f"   {clean_code.upper()}: {translated}")
            except Exception as e:
                self._print(f"   ⚠️ {code.upper()} çeviri hatası: {e}", "warning")
                localized[f"name_{code}" if code != "en" else "name"] = name
                
        return localized
    
    def create_pack_from_query(self, query: str, max_gifs: int = None) -> dict:
        """Arama sorgusundan sticker paketi oluştur"""
        # Eğer max_gifs belirtilmemişse 20-30 arası rastgele seç
        if max_gifs is None:
            max_gifs = random.randint(20, 30)
            self._print(f"🎲 Bu paket için hedef çıkartma sayısı: {max_gifs}", "info")
        
        self._print(f"\n🔍 Aranıyor: '{query}'", "info")
        
        # 1. GIF'leri ara
        gifs = self.gif_fetcher.search_gifs(query, limit=max_gifs + 5)
        
        if len(gifs) < self.config["settings"]["min_gifs_per_pack"]:
            self._print(f"⚠️ Yeterli GIF bulunamadı ({len(gifs)} adet)", "warning")
            return None
        
        self._print(f"✓ {len(gifs)} GIF bulundu", "success")
        
        # 2. Paket ID ve klasör oluştur
        pack_id = self._generate_pack_id()
        pack_name = self._generate_pack_name(query)
        category = self._get_category(query)
        
        # isimleri tüm dillere çevir
        user_lang = self.config["settings"].get("language", "tr")
        localized_names = self._translate_names(pack_name, source_lang=user_lang)
        # Ana isim İngilizce (Veya name alanı) olmalı
        main_name = localized_names.get("name", pack_name)
        
        pack_dir = os.path.join(self.output_dir, pack_id)
        gif_dir = os.path.join(pack_dir, "gifs")
        webp_dir = os.path.join(pack_dir, "webp")
        
        Path(gif_dir).mkdir(parents=True, exist_ok=True)
        Path(webp_dir).mkdir(parents=True, exist_ok=True)
        
        # 3. GIF'leri indir
        self._print(f"\n📥 GIF'ler indiriliyor (Hedef: {max_gifs} adet)...", "info")
        downloaded = self.gif_fetcher.download_multiple(gifs, gif_dir, max_count=max_gifs)
        
        if len(downloaded) < self.config["settings"]["min_gifs_per_pack"]:
            self._print(f"⚠️ Yeterli GIF indirilemedi ({len(downloaded)} adet)", "warning")
            shutil.rmtree(pack_dir, ignore_errors=True)
            return None
        
        self._print(f"✓ {len(downloaded)} GIF indirildi", "success")
        
        # 4. GIF'leri WebP'ye dönüştür
        self._print(f"\n🎬 WebP'ye dönüştürülüyor...", "info")
        webp_files, tray_file = self.processor.process_pack(downloaded, webp_dir, "stickers")
        
        if not webp_files:
            self._print("⚠️ WebP dönüşümü başarısız", "error")
            shutil.rmtree(pack_dir, ignore_errors=True)
            return None
        
        # WebP dosyalarını ana dizine taşı ve emoji ata
        final_stickers_metadata = []
        for i, webp in enumerate(webp_files):
            new_path = os.path.join(pack_dir, os.path.basename(webp))
            shutil.move(webp, new_path)
            
            # Bu sticker için uygun emojileri bul
            gif_info = gifs[i] if i < len(gifs) else {"title": ""}
            st_emojis = self._get_emojis_for_gif(gif_info.get("title", ""), query, category)
            
            final_stickers_metadata.append({
                "local_path": new_path,
                "emojis": st_emojis
            })
        
        # Tray'i taşı
        if tray_file:
            new_tray = os.path.join(pack_dir, "tray.png")
            shutil.move(tray_file, new_tray)
            tray_file = new_tray
        
        self._print(f"✓ {len(final_stickers_metadata)} sticker oluşturuldu", "success")
        
        # 5. Firebase'e yükle (varsa)
        pack_data = None
        if self.firebase:
            self._print(f"\n☁️ Firebase'e yükleniyor...", "info")
            pack_data = self.firebase.upload_pack_as_draft(
                pack_id=pack_id,
                pack_name=main_name,
                webp_files=[s["local_path"] for s in final_stickers_metadata],
                tray_file=tray_file,
                category=category,
                source_query=query,
                localized_names=localized_names,
                emojis_list=[s["emojis"] for s in final_stickers_metadata]
            )
            self._print(f"✓ Taslak olarak yüklendi", "success")
            
            # Yükleme bittiyse yerel klasörü tamamen temizle (Depolama tasarrufu)
            self._print(f"🧹 Yerel dosyalar temizleniyor...", "info")
            shutil.rmtree(pack_dir, ignore_errors=True)
        else:
            # Yerel bilgi dosyası oluştur
            pack_data = {
                "id": pack_id,
                "name": pack_name,
                "category": category,
                "sticker_count": len(final_stickers_metadata),
                "local_path": pack_dir,
                "source_query": query,
                "created_at": datetime.now().isoformat()
            }
            
            info_path = os.path.join(pack_dir, "pack_info.json")
            with open(info_path, 'w', encoding='utf-8') as f:
                json.dump(pack_data, f, ensure_ascii=False, indent=2)
            
            # Sadece geçici klasörleri temizle, asıl paketi bırak
            shutil.rmtree(gif_dir, ignore_errors=True)
            shutil.rmtree(webp_dir, ignore_errors=True)
        
        self._print(f"\n✅ İşlem tamamlandı: {pack_name}", "success")
        self._print(f"   ID: {pack_id}", "info")
        self._print(f"   Kategori: {category}", "info")
        self._print(f"   Sticker: {len(final_stickers_metadata)} adet", "info")
        
        return pack_data
    
    def _analyze_risk(self, query: str) -> Dict:
        """Sorgunun telif/politika riskini analiz et"""
        risk_keywords = {
            "high": [
                "vs", "-", "fc", "united", "city", "real", "madrid", "barcelona", "milan", "inter", 
                "spor", "beşiktaş", "fenerbahçe", "galatasaray", "iphone", "apple", "netflix", 
                "disney", "fifa", "uefa", "marvel", "dc comics", "warner", "hulk", "thor", 
                "wolverine", "spiderman", "batman", "superman", "iron man", "avengers", 
                "simpsons", "looney tunes", "harry potter", "star wars", "pokemon", "anime", 
                "naruto", "one piece", "dragon ball", "coca cola", "nike", "adidas",
                "rick and morty", "rick", "morty", "aquaman", "justice league", "morty smith",
                "rick sanchez", "adult swim"
            ],
            "celeb": [
                "cemre", "ersin", "orkun", "seha", "trump", "biden", "elon", "musk", 
                "putin", "zuckerberg", "messi", "ronaldo", "taylor swift", "rihanna"
            ]
        }
        
        query_lower = query.lower()
        risk_level = "safe"
        reason = "Güvenli görünüyor"
        
        # Spor ve Marka Analizi
        for kw in risk_keywords["high"]:
            if kw in query_lower:
                risk_level = "high"
                reason = "Spor takımı, etkinlik veya marka içerebilir (Telif Riski)"
                break
        
        # Ünlü Analizi
        if risk_level == "safe":
            for kw in risk_keywords["celeb"]:
                if kw in query_lower:
                    risk_level = "celeb"
                    reason = "Ünlü ismi veya politik figür içerebilir"
                    break
                    
        return {"level": risk_level, "reason": reason}

    def create_from_trends(self, count: int = 1):
        """Gerçek zamanlı Google Trends verilerini kullanarak dinamik öneriler sunar"""
        self._print("🔍 Global ve Yerel trendler analiz ediliyor...", "info")
        
        # 1. Gerçek Trendleri Çek (Google Trends)
        trends = []
        try:
            # HATA FİX: 'automation.modules' yerine doğrudan 'modules' kullan
            from modules.trends import get_trending_topics
            trend_data = get_trending_topics(limit=50)
            trends = [t['title'] for t in trend_data]
            if trends:
                self._print(f"✅ {len(trends)} adet güncel trend konu yakalandı.", "success")
        except Exception as e:
            self._print(f"⚠️ Trend çekme hatası: {e}. Yedek havuz kullanılacak.", "warning")

        # 2. Yedek/Statik Havuz
        backup_pool = [
            "Funny Cats", "Cute Dogs", "Baby Yoda", "Coding Life", "Gaming Humor",
            "Panda Bears", "Space Exploration", "Neon Cyberpunk", "Coffee Time", 
            "Monday Mood", "Friday Party", "Retro 80s", "Anime Aesthetic",
            "Kawaii Lifestyle", "Sarcastic Quotes", "Workout Motivation",
            "Pizza Lovers", "Street Food", "Winter Vibes", "Summer Sun",
            "Spooky Halloween", "Christmas Spirit", "Cyber Cat", "Skateboarding",
            "Pixel Art", "Nature Beauty", "Travel Life", "Movie Lovers",
            "Marvel Superheroes", "DC Comics", "Star Wars", "Harry Potter",
            "Rick and Morty", "The Simpsons", "Looney Tunes", "Tom and Jerry"
        ]
        
        # Trendleri ve yedeği harmanla
        suggestion_pool = list(set(trends + backup_pool))
        random.shuffle(suggestion_pool)
        
        # DİNAMİK ÖNERİ: Kaç paket isteniyorsa onun 2 katı kadar öneri göster (Min 20)
        suggestion_limit = max(20, count * 2)
        selected_suggestions = suggestion_pool[:suggestion_limit]
        
        self._print(f"\n✨ Sizin için seçilen {len(selected_suggestions)} çıkartma konusu önerisi:", "info")

        # Önerileri listele
        print("\n" + "="*80)
        print(f"{'#':<3} | {'Risk Durumu':<15} | {'Önerilen Konu'}")
        print("-" * 80)
        
        analyzed_suggestions = []
        for i, topic in enumerate(selected_suggestions):
            risk = self._analyze_risk(topic)
            analyzed_suggestions.append({"topic": topic, "risk": risk})
            
            risk_icon = "🛡️ SAFE" if risk['level'] == "safe" else "⚠️ RISK"
            color = "green" if risk['level'] == "safe" else "yellow"
            
            if self.console:
                self.console.print(f"{i+1:<3} | [{color}]{risk_icon:<15}[/] | {topic}")
            else:
                print(f"{i+1:<3} | {risk_icon:<15} | {topic}")
        print("="*80)

        # Kullanıcıdan seçim al
        print("\n💡 İPUCU: Google Play güvenliği için 'SAFE' olanları seçmenizi öneririm.")
        choice = input("\nPaket oluşturmak istediğiniz numaraları girin (Örn: 1,3,5 veya 'hepsi'): ").strip().lower()
        
        selected_indexes = []
        if choice == 'hepsi':
            selected_indexes = range(len(selected_suggestions))
        else:
            try:
                selected_indexes = [int(idx.strip()) - 1 for idx in choice.split(",") if idx.strip()]
            except:
                print("⚠️ Geçersiz seçim!")
                return

        for idx in selected_indexes:
            if 0 <= idx < len(selected_suggestions):
                data = analyzed_suggestions[idx]
                topic = data['topic']
                risk = data['risk']
                
                self._print(f"\n🚀 İşleniyor: {topic}", "info")
                if risk['level'] != "safe":
                    self._print(f"   🚩 UYARI: {risk['reason']}", "warning")
                    confirm = input("   Riskli içeriğe rağmen devam edilsin mi? (e/h): ").lower()
                    if confirm != 'e': continue
                
                self.create_pack_from_query(topic)
                time.sleep(1)
            else:
                self._print(f"⚠️ Geçersiz sıra numarası: {idx+1}", "warning")
    
    def list_drafts(self):
        """Taslakları listele"""
        if not self.firebase:
            self._print("⚠️ Firebase bağlantısı yok", "warning")
            return
        
        drafts = self.firebase.get_drafts()
        
        if not drafts:
            self._print("📭 Bekleyen taslak yok", "info")
            return
        
        if self.console:
            table = Table(title="📋 Bekleyen Taslaklar")
            table.add_column("ID", style="dim")
            table.add_column("Ad", style="cyan")
            table.add_column("Kategori")
            table.add_column("Sticker", justify="right")
            table.add_column("Tarih")
            
            for draft in drafts:
                table.add_row(
                    draft['id'][:20] + "...",
                    draft['name'],
                    draft.get('category', '-'),
                    str(draft.get('sticker_count', 0)),
                    draft.get('created_at', '-')
                )
            
            self.console.print(table)
        else:
            for draft in drafts:
                print(f"  • {draft['id']}: {draft['name']} ({draft.get('sticker_count', 0)} sticker)")
    
    def approve_draft(self, pack_id: str):
        """Taslağı onayla"""
        if not self.firebase:
            self._print("⚠️ Firebase bağlantısı yok", "warning")
            return
        
        if self.firebase.approve_draft(pack_id):
            self._print(f"✅ Paket yayına alındı: {pack_id}", "success")
        else:
            self._print(f"❌ Onaylama başarısız", "error")
    
    def delete_draft(self, pack_id: str):
        """Taslağı sil"""
        if not self.firebase:
            self._print("⚠️ Firebase bağlantısı yok", "warning")
            return
        
        if self.firebase.delete_draft(pack_id):
            self._print(f"🗑️ Taslak silindi: {pack_id}", "success")
        else:
            self._print(f"❌ Silme başarısız", "error")


def main():
    # Sticky V2 Pro Banner
    banner = """
    [bold cyan]
    ╔══════════════════════════════════════════════════════════╗
    ║  🤖 STICKY V2 PRO: GÜÇLENDİRİLMİŞ OTOMASYON SİSTEMİ      ║
    ║  🚀 MultiFetcher: Giphy + Tenor + Reddit Aktif!          ║
    ║  🌐 Profesyonel Çeviri & Akıllı Risk Analizi Devrede     ║
    ╚══════════════════════════════════════════════════════════╝
    [/bold cyan]
    """
    if RICH_AVAILABLE:
        Console().print(banner)
    else:
        print("\n" + "="*50)
        print("  🤖 STICKY V2 PRO: OTOMASYON SİSTEMİ")
        print("="*50)
    
    try:
        bot = StickerBot()
    except Exception as e:
        if "401" in str(e):
            print(f"\n🛑 GIPHY YETKİ HATASI (401): Girdiğiniz anahtar bir 'SDK' anahtarı olabilir.")
            print("💡 Lütfen Giphy Dashboard'da 'Create an API Key' dedikten sonra Platform olarak 'Web' seçin.")
            print("💡 Aldığınız anahtarın yanında 'API' yazmalı, 'SDK' yazmamalı.\n")
        else:
            print(f"⚠️ Bot başlatılamadı: {e}")
        sys.exit(1)

    while True:
        print("\n" + "═"*60)
        print("  📋 STICKY ANA YÖNETİM MERKEZİ")
        print("═"*60)
        print("  1- ✨ Akıllı Öneri Havuzu (Risk Analizli)")
        print("  2- 🎯 Doğrudan Kelime ile Paket Oluştur (Multi-API)")
        print("  3- 📋 Taslak Yönetimi ve Yayına Hazırlık")
        print("  0- ❌ Sistemden Çıkış")
        print("═"*60)
        
        choice = input("\nSeçiminiz [0-3]: ").strip()
        
        if choice == '1':
            count_str = input("Kaç farklı kategoride paket üretilsin? [Varsayılan: 1]: ").strip()
            count = int(count_str) if count_str.isdigit() else 1
            bot.create_from_trends(count=count)
            
        elif choice == '2':
            query = input("Arama sorgusunu girin (Örn: havalı arabalar): ").strip()
            if query:
                bot.create_pack_from_query(query)
            else:
                print("⚠️ Sorgu boş olamaz!")
                
        elif choice == '3':
            bot.list_drafts()
            print("\n  [A] - Taslağı Hazırla (Onayla)")
            print("  [D] - Taslağı Temizle (Sil)")
            print("  [B] - Geri Dön")
            
            sub_choice = input("\nSeçiminiz: ").strip().upper()
            if sub_choice == 'A':
                pack_id = input("Hazırlanacak Paket ID: ").strip()
                if pack_id: bot.approve_draft(pack_id)
            elif sub_choice == 'D':
                pack_id = input("Silinecek Paket ID: ").strip()
                if pack_id: bot.delete_draft(pack_id)
                
        elif choice == '0':
            print("\n🚀 Sistem Kapatılıyor... İyi Çalışmalar!")
            break
        else:
            print("⚠️ Hatalı işlem! Lütfen listedeki rakamlardan birini kullanın.")


if __name__ == "__main__":
    # Eğer parametre varsa eski usul çalıştır (Argparse desteği için)
    if len(sys.argv) > 1:
        parser = argparse.ArgumentParser()
        parser.add_argument("--query", "-q", type=str)
        parser.add_argument("--trends", "-t", type=int)
        parser.add_argument("--list", "-l", action="store_true")
        args = parser.parse_args()
        
        bot = StickerBot()
        if args.list: bot.list_drafts()
        elif args.query: bot.create_pack_from_query(args.query)
        elif args.trends: bot.create_from_trends(count=args.trends)
    else:
        # Parametre yoksa menüyü aç
        main()
