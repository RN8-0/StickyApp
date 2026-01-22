#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
StickyApp Setup Tool
Cross-platform setup script for Mac, Windows, and Linux
"""

import os
import sys
import subprocess
import shutil
import platform

# --- CONFIGURATION ---
VENV_DIR = ".venv"
REQUIRED_FILES = {
    "firebase-admin-sdk.json": {
        "description": "Firebase Admin SDK Key",
        "instructions": [
            "1. Firebase Console'u acin: https://console.firebase.google.com",
            "2. Projenizi secin (StickyApp).",
            "3. Sol menude, 'Project Overview' yanindaki CARK simgesine tiklayin.",
            "4. 'Project settings' (Proje Ayarlari) secenegine basin.",
            "5. Ustteki sekmelerden 'Service accounts' sekmesine gidin.",
            "6. 'Firebase Admin SDK' secili oldugundan emin olun.",
            "7. 'Generate new private key' butonuna basin.",
            "8. Indirilen .json dosyasini proje ana dizinine koyun.",
            "9. Dosya adini 'firebase-admin-sdk.json' yapin."
        ]
    },
    "app/google-services.json": {
        "description": "Android Firebase Config",
        "instructions": [
            "1. Firebase Console'u acin: https://console.firebase.google.com",
            "2. Projenizi secin.",
            "3. 'Project settings' > 'General' sekmesine gidin.",
            "4. 'Your apps' bolumunde Android uygulamanizi bulun.",
            "5. 'google-services.json' indirin.",
            "6. Dosyayi 'app/' klasorune koyun."
        ]
    }
}

# --- UTILITY FUNCTIONS ---

def get_os_info():
    """Returns OS type and details."""
    system = platform.system().lower()
    if system == "darwin":
        return "mac", "macOS"
    elif system == "windows":
        return "windows", "Windows"
    else:
        return "linux", "Linux"

def clear_screen():
    """Clear terminal screen."""
    os_type, _ = get_os_info()
    if os_type == "windows":
        os.system('cls')
    else:
        os.system('clear')

def print_banner():
    """Print application banner."""
    os_type, os_name = get_os_info()
    logo = """
    ███████╗████████╗██╗ ██████╗██╗  ██╗██╗   ██╗ █████╗ ██████╗ ██████╗ 
    ██╔════╝╚══██╔══╝██║██╔════╝██║ ██╔╝╚██╗ ██╔╝██╔══██╗██╔══██╗██╔══██╗
    ███████╗   ██║   ██║██║     █████╔╝  ╚████╔╝ ███████║██████╔╝██████╔╝
    ╚════██║   ██║   ██║██║     ██╔═██╗   ╚██╔╝  ██╔══██║██╔═══╝ ██╔═══╝ 
    ███████║   ██║   ██║╚██████╗██║  ██╗   ██║   ██║  ██║██║     ██║     
    ╚══════╝   ╚═╝   ╚═╝ ╚═════╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝╚═╝     ╚═╝     
    """
    print_colored(logo, "blue")
    
    print("""
    ╔══════════════════════════════════════════════════════════╗
    ║           STICKYAPP - KURULUM VE YAYIN ARACI             ║
    ║                                                          ║
    ║  Cross-Platform: Mac, Windows, Linux                     ║
    ╚══════════════════════════════════════════════════════════╝
    """)
    print(f"    Platform: {os_name} ({platform.machine()})")
    print(f"    Python: {sys.version.split()[0]}")
    print(f"    Dizin: {os.getcwd()}")
    print()

def print_colored(text, color="white"):
    """Print with color (cross-platform)."""
    colors = {
        "red": "\033[91m",
        "green": "\033[92m",
        "yellow": "\033[93m",
        "blue": "\033[94m",
        "white": "\033[0m",
        "reset": "\033[0m"
    }
    # Windows'ta renk desteği
    os_type, _ = get_os_info()
    if os_type == "windows":
        try:
            import ctypes
            kernel32 = ctypes.windll.kernel32
            kernel32.SetConsoleMode(kernel32.GetStdHandle(-11), 7)
        except:
            pass

    print(f"{colors.get(color, '')}{text}{colors['reset']}")

def run_command(cmd, cwd=None, shell=False, interactive=False):
    """Run a command and return success status."""
    os_type, _ = get_os_info()
    try:
        if interactive:
            # Interactive commands shouldn't capture output
            if shell:
                if isinstance(cmd, list): cmd = " ".join(cmd)
                result = subprocess.run(cmd, cwd=cwd, shell=True)
            else:
                if isinstance(cmd, str):
                    import shlex
                    cmd = shlex.split(cmd)
                result = subprocess.run(cmd, cwd=cwd, shell=False)
            return result.returncode == 0, "", ""
            
        if shell:
            # If shell=True, cmd should be a string
            if isinstance(cmd, list):
                cmd = " ".join(cmd)
            result = subprocess.run(cmd, cwd=cwd, shell=True, capture_output=True, text=True)
        else:
            # If shell=False, cmd should be a list
            if isinstance(cmd, str):
                import shlex
                cmd = shlex.split(cmd)
            result = subprocess.run(cmd, cwd=cwd, shell=False, capture_output=True, text=True)
        return result.returncode == 0, result.stdout, result.stderr
    except Exception as e:
        return False, "", str(e)

def check_command_exists(cmd):
    """Check if a command exists on the system."""
    return shutil.which(cmd) is not None

# --- VENV MANAGEMENT ---

def get_python_executable():
    """Get python executable path inside venv."""
    os_type, _ = get_os_info()
    if os_type == "windows":
        return os.path.join(VENV_DIR, "Scripts", "python.exe")
    return os.path.join(VENV_DIR, "bin", "python")

def get_pip_executable():
    """Get pip executable path inside venv."""
    os_type, _ = get_os_info()
    if os_type == "windows":
        return os.path.join(VENV_DIR, "Scripts", "pip.exe")
    return os.path.join(VENV_DIR, "bin", "pip")

def is_in_venv():
    """Check if currently running inside a virtual environment."""
    return (hasattr(sys, 'real_prefix') or
            (hasattr(sys, 'base_prefix') and sys.base_prefix != sys.prefix))

def setup_venv():
    """Create and setup virtual environment."""
    if is_in_venv():
        return True

    if not os.path.exists(VENV_DIR):
        print_colored("\n[*] Sanal ortam olusturuluyor...", "blue")
        try:
            import venv
            venv.create(VENV_DIR, with_pip=True)
            print_colored("[+] Sanal ortam olusturuldu.", "green")
        except Exception as e:
            print_colored(f"[!] Sanal ortam olusturulamadi: {e}", "red")
            return False

    # Install dependencies
    pip_exe = get_pip_executable()
    if os.path.exists(pip_exe):
        print_colored("[*] Gerekli paketler yukleniyor...", "blue")
        os_type, _ = get_os_info()
        try:
            # Upgrade pip first
            run_command([pip_exe, "install", "--upgrade", "pip", "-q"], shell=(os_type == "windows"))
            
            # Install requirements
            requirements = ["firebase-admin", "shlex; python_version < '3.8'"]
            for req in requirements:
                success, _, err = run_command([pip_exe, "install", req, "-q"], shell=(os_type == "windows"))
                if not success:
                    print_colored(f"[!] {req} yukleme hatasi: {err}", "yellow")
            
            print_colored("[+] Paketler yuklendi.", "green")
        except Exception as e:
            print_colored(f"[!] Paket yukleme hatasi: {e}", "red")

    # Restart script in venv
    python_exe = get_python_executable()
    if os.path.exists(python_exe):
        print_colored("[*] Script sanal ortamda yeniden baslatiliyor...\n", "yellow")
        try:
            # We use subprocess.call to properly pass through to the new process
            process = subprocess.Popen([python_exe] + sys.argv)
            process.wait()
            sys.exit(process.returncode)
        except Exception as e:
            print_colored(f"[!] Sanal ortamda baslatma hatasi: {e}", "red")
            return False

    return True

# --- CHECK FUNCTIONS ---

def check_required_files():
    """Check all required files and show instructions for missing ones."""
    print("\n" + "="*60)
    print("  GEREKLI DOSYALAR KONTROLU")
    print("="*60)

    all_found = True

    for filepath, info in REQUIRED_FILES.items():
        if os.path.exists(filepath):
            print_colored(f"\n[+] {info['description']}", "green")
            print(f"    Dosya: {filepath}")
        else:
            all_found = False
            print_colored(f"\n[!] EKSIK: {info['description']}", "red")
            print(f"    Dosya: {filepath}")
            print_colored("\n    NASIL ALINIR:", "yellow")
            for step in info['instructions']:
                print(f"    {step}")

    return all_found

def check_node_npm():
    """Check Node.js and npm installation."""
    print("\n" + "="*60)
    print("  NODE.JS ve NPM KONTROLU")
    print("="*60)

    # Check Node.js
    node_cmd = "node"
    if check_command_exists(node_cmd):
        success, stdout, _ = run_command([node_cmd, "--version"])
        if success:
            print_colored(f"\n[+] Node.js: {stdout.strip()}", "green")
        else:
            print_colored("\n[!] Node.js bulunamadi", "red")
            return False
    else:
        print_colored("\n[!] Node.js yuklu degil!", "red")
        print("    Indirme: https://nodejs.org/")
        return False

    # Check npm
    npm_cmd = "npm"
    if check_command_exists(npm_cmd):
        success, stdout, _ = run_command([npm_cmd, "--version"])
        if success:
            print_colored(f"[+] npm: {stdout.strip()}", "green")
        else:
            print_colored("[!] npm bulunamadi", "red")
            return False
    else:
        print_colored("[!] npm yuklu degil!", "red")
        return False

    return True

def check_git():
    """Check Git installation."""
    print("\n" + "="*60)
    print("  GIT KONTROLU")
    print("="*60)

    if check_command_exists("git"):
        success, stdout, _ = run_command(["git", "--version"])
        if success:
            print_colored(f"\n[+] {stdout.strip()}", "green")

            # Check if in git repo
            success, _, _ = run_command(["git", "rev-parse", "--git-dir"])
            if success:
                print_colored("[+] Git repository algilandi", "green")

                # Get remote info
                success, stdout, _ = run_command(["git", "remote", "-v"])
                if success and stdout.strip():
                    print(f"    Remote: {stdout.strip().split()[1]}")
            else:
                print_colored("[!] Bu dizin bir git repository degil", "yellow")
            return True

    print_colored("\n[!] Git yuklu degil!", "red")
    print("    Indirme: https://git-scm.com/")
    return False

def check_android_sdk():
    """Check Android SDK (optional)."""
    print("\n" + "="*60)
    print("  ANDROID SDK KONTROLU (Opsiyonel)")
    print("="*60)

    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")

    if android_home and os.path.exists(android_home):
        print_colored(f"\n[+] Android SDK: {android_home}", "green")
        return True
    else:
        print_colored("\n[i] Android SDK bulunamadi (Sadece Android build icin gerekli)", "yellow")
        print("    Android Studio kuruluysa otomatik olarak ayarlanir.")
        return False

def install_web_dependencies():
    """Install web admin panel dependencies."""
    print("\n" + "="*60)
    print("  WEB ADMIN PANEL BAGIMLILIKLARI")
    print("="*60)

    web_dir = "sticker_admin_web"

    if not os.path.exists(web_dir):
        print_colored(f"\n[!] '{web_dir}' klasoru bulunamadi!", "red")
        return False

    node_modules = os.path.join(web_dir, "node_modules")

    if os.path.exists(node_modules):
        print_colored("\n[+] Web paketleri zaten yuklu (node_modules mevcut)", "green")
        return True

    print("\n[*] Web paketleri yukleniyor (npm install)...")
    print("    Bu islem birkaç dakika surebilir...")

    os_type, _ = get_os_info()
    npm_cmd = "npm"

    try:
        if os_type == "windows":
            result = subprocess.run(f"cd {web_dir} && npm install", shell=True)
        else:
            result = subprocess.run([npm_cmd, "install"], cwd=web_dir)

        if result.returncode == 0:
            print_colored("[+] Web paketleri basariyla yuklendi!", "green")
            return True
        else:
            print_colored("[!] npm install hatasi", "red")
            return False
    except Exception as e:
        print_colored(f"[!] Hata: {e}", "red")
        return False

# --- MAIN FUNCTIONS ---

def option_check_and_setup():
    """Option 1: Check system and install dependencies."""
    clear_screen()
    print_banner()
    print_colored("  SISTEM KONTROLU VE KURULUM", "blue")
    print("="*60)

    # 1. Check required files
    files_ok = check_required_files()

    # 2. Check Node/npm
    node_ok = check_node_npm()

    # 3. Check Git
    git_ok = check_git()

    # 4. Check Android SDK
    check_android_sdk()

    # 5. Install web dependencies if Node is available
    if node_ok:
        install_web_dependencies()

    # Summary
    print("\n" + "="*60)
    print("  OZET")
    print("="*60)

    if files_ok and node_ok and git_ok:
        print_colored("\n[+] Tum gereksinimler karsilandi!", "green")
        print("    Projeyi gelistirmeye baslayabilirsiniz.")
    else:
        print_colored("\n[!] Bazi gereksinimler eksik.", "yellow")
        print("    Yukaridaki talimatlari takip edin.")

    print("\n")
    input("Devam etmek icin ENTER'a basin...")

def option_github_push():
    """Option 2: Push changes to GitHub."""
    clear_screen()
    print_banner()
    print_colored("  GITHUB'A GUNCELLEME GONDER", "blue")
    print("="*60)

    # Check git
    if not check_command_exists("git"):
        print_colored("\n[!] Git yuklu degil!", "red")
        input("\nDevam etmek icin ENTER'a basin...")
        return

    # Check if in git repo
    success, _, _ = run_command(["git", "rev-parse", "--git-dir"])
    if not success:
        print_colored("\n[!] Bu dizin bir git repository degil!", "red")
        input("\nDevam etmek icin ENTER'a basin...")
        return

    # Show current status
    print("\n[*] Mevcut degisiklikler:")
    print("-"*40)
    os_type, _ = get_os_info()
    if os_type == "windows":
        os.system("git status --short")
    else:
        subprocess.run(["git", "status", "--short"])
    print("-"*40)

    # Get commit message
    print("\n")
    commit_msg = input("Commit mesaji girin (bos birakirsaniz iptal): ").strip()

    if not commit_msg:
        print_colored("\n[i] Islem iptal edildi.", "yellow")
        input("\nDevam etmek icin ENTER'a basin...")
        return

    # Confirm
    print(f"\n[?] Commit mesaji: '{commit_msg}'")
    confirm = input("Devam etmek istiyor musunuz? (e/h): ").strip().lower()

    if confirm != 'e':
        print_colored("\n[i] Islem iptal edildi.", "yellow")
        input("\nDevam etmek icin ENTER'a basin...")
        return

    # Git operations
    print("\n[*] Degisiklikler ekleniyor...")
    success, _, err = run_command(["git", "add", "."])
    if not success:
        print_colored(f"[!] git add hatasi: {err}", "red")
        input("\nDevam etmek icin ENTER'a basin...")
        return

    print("[*] Commit olusturuluyor...")
    success, stdout, err = run_command(["git", "commit", "-m", commit_msg])
    if not success:
        if "nothing to commit" in err or "nothing to commit" in stdout or not (err or stdout):
            print_colored("[i] Commit edilecek yeni degisiklik yok (Sistem guncel).", "yellow")
        else:
            print_colored(f"[!] git commit hatasi: {err or stdout}", "red")
            input("\nDevam etmek icin ENTER'a basin...")
            return

    print("[*] GitHub'a gonderiliyor...")
    print_colored("[i] Terminalde GitHub sifrenizi veya Token'inizi girmeniz gerekebilir.", "yellow")
    
    # Use interactive=True for git push to allow password prompts
    success, _, _ = run_command(["git", "push"], interactive=True)
    if success:
        print_colored("\n[+] Tum degisiklikler GitHub'a basariyla gonderildi!", "green")
    else:
        print_colored("\n[!] git push hatasi. Lutfen terminaldeki mesaji kontrol edin.", "red")
        print("\n    Olasi cozumler:")
        print("    1. 'git pull' ile uzak degisiklikleri cekin.")
        print("    2. GitHub Personal Access Token (PAT) kullandiginizdan emin olun.")
        print("    3. Kimlik bilgilerinizi 'git config --global credential.helper store' ile kaydedin.")

    print("\n")
    input("Devam etmek icin ENTER'a basin...")

def main_menu():
    """Display main menu and handle selection."""
    while True:
        clear_screen()
        print_banner()

        print("  SECENEKLER:")
        print("  " + "-"*50)
        print()
        print("  1. Sistem Kontrolu ve Kurulum")
        print("     - Eksik dosyalari kontrol et")
        print("     - Bagimliliklari yukle")
        print("     - Gerekli araclari kontrol et")
        print()
        print("  2. GitHub'a Guncelleme Gonder")
        print("     - Degisiklikleri commit et")
        print("     - GitHub'a push et")
        print()
        print("  0. Cikis")
        print()
        print("  " + "-"*50)

        choice = input("\n  Seciminiz (0-2): ").strip()

        if choice == "1":
            option_check_and_setup()
        elif choice == "2":
            option_github_push()
        elif choice == "0":
            print("\n  Gorusmek uzere!")
            print()
            break
        else:
            print_colored("\n  [!] Gecersiz secim. Lutfen 0, 1 veya 2 girin.", "yellow")
            input("  Devam etmek icin ENTER'a basin...")

def main():
    """Main entry point."""
    # Ensure banner is shown immediately
    clear_screen()
    print_banner()

    # Setup virtual environment if not already in it
    if not is_in_venv():
        setup_venv()

    try:
        main_menu()
    except KeyboardInterrupt:
        print("\n\n  Cikis yapildi.")
        sys.exit(0)

if __name__ == "__main__":
    main()
