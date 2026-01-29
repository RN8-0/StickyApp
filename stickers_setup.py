#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
StickyApp Setup & Management Tool v2.2
Enhanced version with GitHub automation and comprehensive system checks.
"""

import os
import sys
import subprocess
import shutil
import platform
import json
import time

# --- CONFIGURATION ---
VENV_DIR = ".venv"
GITHUB_TOKEN = "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"
REPO_URL = f"https://{GITHUB_TOKEN}@github.com/arain-0/StickyApp.git"

REQUIRED_FILES = {
    "firebase-admin-sdk.json": {
        "description": "Firebase Admin SDK Key",
        "path": ".",
        "instructions": {
            "all": [
                "1. Firebase Console'u açın: https://console.firebase.google.com",
                "2. Projenizi seçin (StickyApp).",
                "3. Sol menüde, 'Project Overview' yanındaki ÇARK simgesine tıklayın.",
                "4. 'Project settings' (Proje Ayarları) seçeneğine basın.",
                "5. Üstteki sekmelerden 'Service accounts' sekmesine gidin.",
                "6. 'Generate new private key' butonuna basın.",
                "7. İndirilen dosyayı 'firebase-admin-sdk.json' adıyla proje ana dizinine koyun."
            ]
        }
    },
    "app/google-services.json": {
        "description": "Android Firebase Config",
        "path": "app/",
        "instructions": {
            "all": [
                "1. Firebase Console > Proje Ayarları > General sekmesine gidin.",
                "2. Android uygulamanızı seçin ve 'google-services.json' dosyasını indirin.",
                "3. Dosyayı 'app/' klasörüne kopyalayın."
            ]
        }
    }
}

# --- STYLING & UI ---

def get_os_info():
    system = platform.system().lower()
    if system == "darwin": return "mac", "macOS"
    if system == "windows": return "windows", "Windows"
    return "linux", f"Linux ({platform.freedesktop_os_release().get('NAME', 'Generic')})" if hasattr(platform, 'freedesktop_os_release') else "Linux"

def clear_screen():
    os.system('cls' if platform.system().lower() == "windows" else 'clear')

def print_colored(text, color="white", bold=False):
    colors = {
        "red": "\033[91m",
        "green": "\033[92m",
        "yellow": "\033[93m",
        "blue": "\033[94m",
        "magenta": "\033[95m",
        "cyan": "\033[96m",
        "white": "\033[97m",
        "reset": "\033[0m"
    }
    prefix = "\033[1m" if bold else ""
    print(f"{prefix}{colors.get(color, '')}{text}{colors['reset']}")

def print_banner():
    logo = """
          \033[92m.oooooo.\033[0m
        \033[92m.oo'    'oo.\033[0m
       \033[92m.oo        oo.\033[0m
       \033[92m.oo   \033[97m●\033[92m    oo.\033[0m
        \033[92m'oo.    .oo'\033[0m
          \033[92m'oooooo'\033[0m
    """
    print(logo)
    print_colored("    S T I C K Y  -  M A N A G E R", "white", bold=True)
    print_colored("    " + "="*29, "green")
    os_type, os_name = get_os_info()
    print_colored(f"    Platform: {os_name}", "cyan")
    print_colored(f"    Python: {sys.version.split()[0]}", "cyan")
    print()

# --- UTILITY ---

def run_command(cmd, cwd=None, shell=False, interactive=False):
    try:
        if interactive:
            result = subprocess.run(cmd, cwd=cwd, shell=shell)
            return result.returncode == 0, "", ""
        
        result = subprocess.run(cmd, cwd=cwd, shell=shell, capture_output=True, text=True)
        return result.returncode == 0, result.stdout, result.stderr
    except Exception as e:
        return False, "", str(e)

def check_command(cmd):
    return shutil.which(cmd) is not None

# --- WORKFLOWS ---

def check_dependencies():
    clear_screen()
    print_banner()
    print_colored("--- SİSTEM GEREKSİNİMLERİ KONTROLÜ ---", "yellow", bold=True)
    
    deps = [
        {"name": "Git", "cmd": "git", "req": True},
        {"name": "Node.js", "cmd": "node", "req": True},
        {"name": "NPM", "cmd": "npm", "req": True},
        {"name": "Java (JDK)", "cmd": "java", "req": True},
        {"name": "Firebase CLI", "cmd": "firebase", "req": False, "install": "npm install -g firebase-tools"},
    ]
    
    all_ok = True
    for dep in deps:
        if check_command(dep['cmd']):
            _, out, _ = run_command([dep['cmd'], "--version"] if dep['cmd'] != "firebase" else [dep['cmd'], "-V"])
            print_colored(f"[✓] {dep['name']}: {out.strip().split()[-1]}", "green")
        else:
            print_colored(f"[✗] {dep['name']} eksik!", "red")
            if dep.get('install'):
                print(f"    Yüklemek için: {dep['install']}")
            if dep['req']: all_ok = False

    print("\n--- DOSYA KONTROLÜ ---")
    files_ok = True
    for filename, info in REQUIRED_FILES.items():
        if os.path.exists(filename):
            print_colored(f"[✓] {info['description']} bulundu.", "green")
        else:
            print_colored(f"[✗] {info['description']} EKSİK!", "red")
            files_ok = False
            for step in info['instructions']['all']:
                print(f"    {step}")
    
    print("\n--- OS ÖZEL TALİMATLAR ---")
    os_type, _ = get_os_info()
    if os_type == "mac":
        print("Mac: 'brew install node git openjdk@17 firebase-cli'")
    elif os_type == "windows":
        print("Win: Node.js portalından yükleyin, JDK 17 yükleyin, 'npm install -g firebase-tools' çalıştırın.")
    else:
        print("Linux: 'sudo apt update && sudo apt install nodejs npm git openjdk-17-jdk'")

    # Try to install Web dependencies if node is there
    if check_command("npm") and os.path.exists("sticker_admin_web"):
        print_colored("\n[*] Web admin bağımlılıkları kontrol ediliyor...", "blue")
        if not os.path.exists("sticker_admin_web/node_modules"):
            print_colored("[!] node_modules eksik, yükleniyor...", "yellow")
            success, _, err = run_command("npm install", cwd="sticker_admin_web", shell=True)
            if success: print_colored("[✓] Web bağımlılıkları yüklendi.", "green")
            else: print_colored(f"[✗] Hata: {err}", "red")
        else:
            print_colored("[✓] Web bağımlılıkları zaten yüklü.", "green")

    input("\nDevam etmek için ENTER'a basın...")

def github_sync():
    clear_screen()
    print_banner()
    print_colored("--- GITHUB OTOMASYONU ---", "magenta", bold=True)
    
    if not check_command("git"):
        print_colored("[✗] Git bulunamadı!", "red")
        input()
        return

    # Check status
    print_colored("[*] Değişiklikler kontrol ediliyor...", "blue")
    run_command("git status -s", interactive=True)
    
    msg = input("\nCommit mesajı (İptal için boş bırakın): ").strip()
    if not msg: return

    print_colored("[*] İşlem başlatılıyor...", "blue")
    
    # 1. Ensure remote is correct with token
    run_command(["git", "remote", "set-url", "origin", REPO_URL])
    
    # Detect branch
    success, branch, _ = run_command(["git", "branch", "--show-current"])
    branch = branch.strip() or "main"

    # 2. Add, Commit, Push
    steps = [
        (["git", "add", "."], "Dosyalar ekleniyor..."),
        (["git", "commit", "-m", msg], "Commit oluşturuluyor..."),
        (["git", "push", "origin", branch], f"GitHub'a gönderiliyor ({branch})...")
    ]
    
    for cmd, desc in steps:
        print_colored(f"[*] {desc}", "white")
        success, out, err = run_command(cmd)
        if not success:
            if "nothing to commit" in out or "nothing to commit" in err:
                print_colored("[!] Değişiklik yok.", "yellow")
                break
            if "push" in cmd[1] and "rejected" in err:
                print_colored("[!] HATA: Uzak sunucuda değişiklikler var. Önce 'git pull' yapın.", "red")
            else:
                print_colored(f"[✗] Hata: {err or out}", "red")
            input("\nDevam etmek için ENTER'a basın...")
            return

    print_colored("\n[✓] Başarıyla tamamlandı!", "green")
    input("\nDevam etmek için ENTER'a basın...")

def start_dev_server():
    clear_screen()
    print_banner()
    print_colored("--- GELİŞTİRME SUNUCUSU ---", "blue", bold=True)
    
    if not os.path.exists("sticker_admin_web"):
        print_colored("[✗] 'sticker_admin_web' klasörü bulunamadı!", "red")
        input()
        return

    # Check for node_modules
    if not os.path.exists("sticker_admin_web/node_modules"):
        print_colored("[!] Bağımlılıklar eksik. Yükleniyor...", "yellow")
        success, _, err = run_command("npm install", cwd="sticker_admin_web", shell=True)
        if not success:
            print_colored(f"[✗] Yükleme hatası: {err}", "red")
            input()
            return

    import webbrowser
    print_colored("[*] Lokal sunucu başlatılıyor ve tarayıcı açılıyor...", "green")
    print_colored("[!] Durdurmak için CTRL+C tuşuna basın.\n", "yellow")
    
    # Start vite with open flag
    try:
        # 'npm run dev -- --open' passes --open to the underlying vite command
        subprocess.run("npm run dev -- --open", cwd="sticker_admin_web", shell=True)
    except KeyboardInterrupt:
        print("\n[i] Sunucu durduruldu.")
    
    input("\nMenüye dönmek için ENTER'a basın...")

def open_live_panel():
    import webbrowser
    url = "https://sticky-dcd20.web.app"
    print_colored(f"[*] Canlı panel açılıyor: {url}", "cyan")
    webbrowser.open(url)
    time.sleep(1)

def firebase_deploy():
    clear_screen()
    print_banner()
    print_colored("--- FIREBASE DEPLOY ---", "yellow", bold=True)

    if not check_command("firebase"):
        print_colored("[✗] Firebase CLI eksik!", "red")
        input()
        return

    print_colored("\nDeploy seçenekleri:", "cyan")
    print("  [1] Sadece Admin Panel")
    print("  [2] Sadece Privacy/Legal Sayfası")
    print("  [3] Her İkisi de (Admin + Privacy) (Önerilen)")
    print("  [0] İptal")

    choice = input("\nSeçiminiz [varsayılan: 3]: ").strip()

    if choice == "0":
        return

    # Varsayılan olarak her ikisini de deploy et
    if choice == "":
        choice = "3"

    # Admin panel deploy
    if choice in ["1", "3"]:
        print_colored("\n[*] Admin panel derleniyor...", "blue")
        success, _, err = run_command("npm run build", cwd="sticker_admin_web", shell=True)
        if not success:
            print_colored(f"[✗] Derleme hatası: {err}", "red")
            input()
            return

        print_colored("[*] Admin panel Firebase'e gönderiliyor...", "blue")
        subprocess.run("firebase deploy --only hosting:admin", shell=True)

    # Privacy page deploy
    if choice in ["2", "3"]:
        print_colored("\n[*] Privacy sayfası Firebase'e gönderiliyor...", "blue")
        subprocess.run("firebase deploy --only hosting:privacy", shell=True)

    print_colored("\n[✓] Deploy işlemi tamamlandı!", "green")
    input("\nDevam etmek için ENTER'a basın...")

def clean_project():
    clear_screen()
    print_banner()
    print_colored("--- PROJE TEMİZLİĞİ ---", "red", bold=True)
    
    targets = [
        "sticker_admin_web/dist",
        "sticker_admin_web/node_modules",
        "app/build",
        ".venv",
        "__pycache__"
    ]
    
    print_colored("[!] Bu işlem seçilen klasörleri silecektir.", "yellow")
    confirm = input("Temizlik yapmak istiyor musunuz? (e/h): ").lower()
    if confirm != 'e': return

    for target in targets:
        if os.path.exists(target):
            print(f"[*] Siliniyor: {target}...")
            try:
                if os.path.isfile(target): os.remove(target)
                else: shutil.rmtree(target)
            except Exception as e:
                print(f"[✗] Hata: {e}")
    
    print_colored("\n[✓] Temizlik tamamlandı.", "green")
    input("\nDevam etmek için ENTER'a basın...")

def main_menu():
    while True:
        clear_screen()
        print_banner()
        
        options = [
            ("1", "Sistem Kontrolü & Kurulum", "Gereksinimleri ve eksik dosyaları denetler"),
            ("2", "GitHub'a Push Et", "Değişiklikleri otomatik olarak commit ve push yapar"),
            ("3", "Web Admin Panelini Başlat", "Lokal geliştirme sunucusunu (Vite) açar"),
            ("4", "Canlı Paneli Aç (Web)", "Yayınlanmış olan paneli tarayıcıda açar"),
            ("5", "Firebase'e Yayınla (Deploy)", "Web panelini canlıya alır"),
            ("6", "Proje Temizliği (Clean)", "Geçici ve derleme dosyalarını siler"),
            ("0", "Çıkış", "")
        ]
        
        for key, title, desc in options:
            print(f"  \033[92m[{key}]\033[0m \033[1m{title.ljust(30)}\033[0m \033[90m{desc}\033[0m")
        
        choice = input("\n  Seçiminiz: ").strip()
        
        if choice == "1": check_dependencies()
        elif choice == "2": github_sync()
        elif choice == "3": start_dev_server()
        elif choice == "4": open_live_panel()
        elif choice == "5": firebase_deploy()
        elif choice == "6": clean_project()
        elif choice == "0": break
        else:
            print_colored("\n  [!] Geçersiz seçim!", "yellow")
            time.sleep(1)

if __name__ == "__main__":
    try:
        main_menu()
    except KeyboardInterrupt:
        print("\n\n  Güle güle!")
        sys.exit(0)
