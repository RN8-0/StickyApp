import os
import sys
import subprocess
import json
import shutil

def clear_screen():
    os.system('cls' if os.name == 'nt' else 'clear')

def print_banner():
    print("""
    =========================================
    🚀 STICKY APP - PROJE YÖNETİM SİSTEMİ
    =========================================
    """)

def check_dependencies():
    print("🔍 Bağımlılıklar kontrol ediliyor...")
    try:
        import firebase_admin
        print("✅ firebase-admin yüklü.")
    except ImportError:
        print("❌ firebase-admin eksik. Yükleniyor...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "firebase-admin"])

def check_credentials():
    print("\n🔑 Kimlik bilgileri kontrol ediliyor...")
    creds_path = "firebase-admin-sdk.json"
    if os.path.exists(creds_path):
        print(f"✅ {creds_path} bulundu.")
    else:
        print(f"⚠️  DİKKAT: {creds_path} dosyası bulunamadı!")
        print("   Bu dosya olmadan bazı işlemler (toplu silme vb.) kısıtlı olabilir.")
        print("   Nasıl Alınır?")
        print("   1. Firebase Console > Project Settings > Service Accounts sekmesine gidin.")
        print("   2. 'Generate new private key' butonuna basın.")
        print(f"   3. İnen dosyayı bu proje klasörüne '{creds_path}' adıyla kaydedin.\n")

def github_sync():
    print("\n🌐 GİTHUB SENKRONİZASYONU")
    commit_msg = input("Commit mesajı girin (Boş bırakırsanız 'Otomatik Güncelleme'): ") or "Otomatik Güncelleme"
    
    try:
        subprocess.run(["git", "add", "."], check=True)
        subprocess.run(["git", "commit", "-m", commit_msg], check=True)
        subprocess.run(["git", "push"], check=True)
        print("✅ Değişiklikler başarıyla GitHub'a gönderildi.")
    except Exception as e:
        print(f"❌ GitHub hatası: {e}")

def run_web_admin():
    print("\n🌐 Web Admin Başlatılıyor...")
    web_path = os.path.join(os.getcwd(), "sticker_admin_web")
    if os.path.exists(web_path):
        subprocess.Popen(["npm", "run", "dev"], cwd=web_path, shell=(os.name == 'nt'))
        print("✅ Web server arka planda başlatıldı: http://localhost:5173/StickyApp/")
    else:
        print("❌ 'sticker_admin_web' klasörü bulunamadı!")

def main():
    while True:
        clear_screen()
        print_banner()
        print("1. Bağımlılıkları ve Dosyaları Kontrol Et")
        print("2. Web Admin Panelini Başlat")
        print("3. Yapılan Değişiklikleri GitHub'a Gönder")
        print("4. Gereksiz Dosyaları Temizle")
        print("0. Çıkış")
        
        choice = input("\nSeçiminiz: ")
        
        if choice == '1':
            check_dependencies()
            check_credentials()
            input("\nDevam etmek için ENTER'a basın...")
        elif choice == '2':
            run_web_admin()
            input("\nDevam etmek için ENTER'a basın...")
        elif choice == '3':
            github_sync()
            input("\nDevam etmek için ENTER'a basın...")
        elif choice == '4':
            print("🧹 Temizlik yapılıyor...")
            # Silinecek dosyalar
            to_remove = ["upload_stickers.py", "sticker_manager.py", ".pickle", "token.json"]
            for target in to_remove:
                if os.path.exists(target):
                    os.remove(target)
            print("✅ Gereksiz dosyalar temizlendi.")
            input("\nDevam etmek için ENTER'a basın...")
        elif choice == '0':
            print("Hoşçakalın!")
            break

if __name__ == "__main__":
    main()
