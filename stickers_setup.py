import os
import sys
import subprocess
import json
import shutil
import venv

# --- OTOMATİK VENV YÖNETİMİ ---
VENV_DIR = ".venv"

def get_python_executable():
    """Ventral environment içindeki python yolunu döner."""
    if os.name == "nt":
        return os.path.join(VENV_DIR, "Scripts", "python.exe")
    return os.path.join(VENV_DIR, "bin", "python")

def is_venv():
    """Script şu an bir venv içinden mi çalışıyor?"""
    return hasattr(sys, 'real_prefix') or (func := getattr(sys, 'base_prefix', sys.prefix)) != sys.prefix

def setup_environment():
    """Venv oluşturur ve script'i venv içinden yeniden başlatır."""
    if not is_venv():
        if not os.path.exists(VENV_DIR):
            print(f"🚀 İlk kurulum başlatılıyor... Sanal ortam oluşturuluyor ({VENV_DIR})...")
            venv.create(VENV_DIR, with_pip=True)
            
        print("🔄 Sanal ortam aktive ediliyor ve script yeniden başlatılıyor...")
        python_exe = get_python_executable()
        
        # Bağımlılıkları venv içine kur
        subprocess.check_call([python_exe, "-m", "pip", "install", "--upgrade", "pip"])
        subprocess.check_call([python_exe, "-m", "pip", "install", "firebase-admin"])
        
        # Script'i venv içindeki python ile yeniden başlat
        result = subprocess.run([python_exe, *sys.argv])
        sys.exit(result.returncode)

# --- ANA PROGRAM ---

def clear_screen():
    os.system('cls' if os.name == 'nt' else 'clear')

def print_banner():
    print("""
    ==================================================
    🚀 STICKY APP PRO - HEPSİ BİR ARADA KURULUM ARACI
    ==================================================
    """)

def check_credentials():
    print("\n🔑 Firebase Dosyaları Kontrol Ediliyor...")
    
    # Android App için
    google_json = os.path.join("app", "google-services.json")
    if os.path.exists(google_json):
        print(f"✅ Android Config: {google_json} bulundu.")
    else:
        print(f"❌ EKSİK: {google_json}")
        print("   -> Firebase Console > Proje Ayarları > google-services.json indirip 'app/' içine koyun.")

    # Admin SDK için
    creds_path = "firebase-admin-sdk.json"
    if os.path.exists(creds_path):
        print(f"✅ Admin SDK: {creds_path} bulundu.")
    else:
        print(f"❌ EKSİK: {creds_path}")
        print("\n   ADMİN ANAHTARI NASIL ALINIR?")
        print("   1. https://console.firebase.google.com adresine gidin.")
        print("   2. Sol üstteki Çark simgesine (Proje Ayarları) tıklayın.")
        print("   3. Üstten 'Hizmet Hesapları' (Service Accounts) sekmesine tıklayın.")
        print("   4. Sayfanın ortasındaki 'Yeni Özel Anahtar Oluştur' (Generate New Private Key) butonuna basın.")
        print("   5. İnen .json dosyasının adını 'firebase-admin-sdk.json' olarak değiştirin.")
        print(f"   6. Dosyayı şu an bulunduğunuz klasöre ({os.getcwd()}) yapıştırın.\n")

def check_web_dependencies():
    print("\n🌐 Web Admin Bağımlılıkları Kontrol Ediliyor...")
    web_dir = "sticker_admin_web"
    if os.path.exists(web_dir):
        # NPM Check
        npm_cmd = "npm.cmd" if os.name == "nt" else "npm"
        if not shutil.which(npm_cmd) and not shutil.which("npm"):
            print("❌ HATA: 'npm' (Node.js) sisteminizde bulunamadı!")
            print("   Lütfen https://nodejs.org/ adresinden Node.js indirip kurun.")
            return

        node_modules = os.path.join(web_dir, "node_modules")
        if not os.path.exists(node_modules):
            print("⏳ 'node_modules' eksik, paketler yükleniyor (npm install)... Bu işlem birkaç dakika sürebilir.")
            try:
                subprocess.check_call([npm_cmd, "install"], cwd=web_dir, shell=(os.name == 'nt'))
                print("✅ Web paketleri başarıyla yüklendi.")
            except Exception as e:
                print(f"❌ NPM hatası: {e}.")
        else:
            print("✅ Web paketleri (node_modules) mevcut.")
    else:
        print("❌ 'sticker_admin_web' dizini bulunamadı!")

def github_sync():
    print("\n🌐 GİTHUB YAYINLAMA")
    msg = input("Güncelleme özeti (Örn: Yeni paketler eklendi): ") or "Otomatik Güncelleme"
    try:
        subprocess.run(["git", "add", "."], check=True)
        subprocess.run(["git", "commit", "-m", msg], check=True)
        subprocess.run(["git", "push"], check=True)
        print("✅ Tüm değişiklikler GitHub'a gönderildi.")
    except Exception as e:
        print(f"❌ GitHub hatası: {e}")

def run_web_admin():
    print("\n🌐 Web Admin Başlatılıyor...")
    web_dir = "sticker_admin_web"
    if os.name == 'nt':
        # Windows için yeni pencerede başlat
        subprocess.Popen(["start", "cmd", "/k", "npm run dev"], cwd=web_dir, shell=True)
    else:
        # Mac/Linux için arka planda başlat
        subprocess.Popen(["npm", "run", "dev"], cwd=web_dir)
        
    print("✅ Web server başlatıldı! Link: http://localhost:5173/StickyApp/")

def cleanup():
    print("\n🧹 Gereksiz Dosyalar Temizleniyor...")
    files_to_remove = [
        "upload_stickers.py", "sticker_manager.py", "upload_stickers.sh", 
        "upload_stickers.bat", "requirements.txt", "token.pickle", "cache.json",
        "credentials.json", "client_secrets.json", "token.json"
    ]
    for f in files_to_remove:
        path = os.path.join("stickers_convert", f)
        if os.path.exists(path):
            os.remove(path)
            print(f"🗑️  Silindi: {f}")
        elif os.path.exists(f): # Ana dizindeyse
            os.remove(f)
            print(f"🗑️  Silindi: {f}")
    print("✅ Temizlik tamamlandı.")

def main():
    setup_environment() # İlk kurulumu ve venv'i otomatik halleder
    
    while True:
        clear_screen()
        print_banner()
        print("1. 🛠️  Sistem Kontrolü (Dosyalar & Bağımlılıklar)")
        print("2. 🌐 Web Admin Panelini Başlat")
        print("3. ⬆️  Yapılan Değişiklikleri GitHub'a Gönder")
        print("4. 🧹 Gereksiz Arka Plan Dosyalarını Temizle")
        print("0. 🚪 Çıkış")
        
        choice = input("\nSeçiminiz: ")
        
        if choice == '1':
            check_credentials()
            check_web_dependencies()
            input("\nDevam etmek için ENTER'a basın...")
        elif choice == '2':
            run_web_admin()
            input("\nPanel komut penceresinde çalışıyor. Devam etmek için ENTER'a basın...")
        elif choice == '3':
            github_sync()
            input("\nDevam etmek için ENTER'a basın...")
        elif choice == '4':
            cleanup()
            input("\nDevam etmek için ENTER'a basın...")
        elif choice == '0':
            print("Görüşmek üzere!")
            break

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nÇıkış yapıldı.")
