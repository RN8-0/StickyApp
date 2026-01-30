#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
StickyApp Setup & Management Tool v3.0
Professional CLI tool for managing StickyApp project.
Cross-platform support: Windows, macOS, Linux
"""

import os
import sys
import subprocess
import shutil
import platform
import json
import time
import re

# --- CONFIGURATION ---
GITHUB_TOKEN = "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"
REPO_URL = f"https://{GITHUB_TOKEN}@github.com/arain-0/StickyApp.git"
APP_VERSION = "3.0"

REQUIRED_FILES = {
    "firebase-admin-sdk.json": {
        "description": "Firebase Admin SDK Key",
        "instructions": [
            "1. Firebase Console'u açın: https://console.firebase.google.com",
            "2. Projenizi seçin > Project Settings > Service Accounts",
            "3. 'Generate new private key' butonuna basın",
            "4. Dosyayı 'firebase-admin-sdk.json' adıyla proje dizinine koyun"
        ]
    },
    "app/google-services.json": {
        "description": "Android Firebase Config",
        "instructions": [
            "1. Firebase Console > Project Settings > General",
            "2. Android uygulamanızı seçin ve 'google-services.json' indirin",
            "3. Dosyayı 'app/' klasörüne koyun"
        ]
    }
}

# --- PLATFORM DETECTION ---

def get_os_info():
    system = platform.system().lower()
    if system == "darwin":
        return "mac", "macOS"
    if system == "windows":
        return "windows", "Windows"
    try:
        if hasattr(platform, 'freedesktop_os_release'):
            return "linux", f"Linux ({platform.freedesktop_os_release().get('NAME', 'Generic')})"
    except:
        pass
    return "linux", "Linux"

def get_gradle_cmd():
    """Get the correct gradle command for the current OS"""
    os_type, _ = get_os_info()
    if os_type == "windows":
        return "gradlew.bat"
    return "./gradlew"

def get_adb_path():
    """Find ADB executable"""
    # Check if adb is in PATH
    if shutil.which("adb"):
        return "adb"

    # Common ADB locations
    os_type, _ = get_os_info()
    possible_paths = []

    if os_type == "windows":
        local_app_data = os.environ.get("LOCALAPPDATA", "")
        possible_paths = [
            os.path.join(local_app_data, "Android", "Sdk", "platform-tools", "adb.exe"),
            os.path.join(os.environ.get("HOME", ""), "Android", "Sdk", "platform-tools", "adb.exe"),
        ]
    elif os_type == "mac":
        possible_paths = [
            os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
            "/usr/local/bin/adb",
        ]
    else:  # Linux
        possible_paths = [
            os.path.expanduser("~/Android/Sdk/platform-tools/adb"),
            "/usr/bin/adb",
            "/opt/android-sdk/platform-tools/adb",
        ]

    for path in possible_paths:
        if os.path.exists(path):
            return path

    return None

# --- STYLING & UI ---

class Colors:
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    MAGENTA = "\033[95m"
    CYAN = "\033[96m"
    WHITE = "\033[97m"
    GRAY = "\033[90m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    RESET = "\033[0m"

def clear_screen():
    os.system('cls' if platform.system().lower() == "windows" else 'clear')

def print_colored(text, color=Colors.WHITE, bold=False):
    prefix = Colors.BOLD if bold else ""
    print(f"{prefix}{color}{text}{Colors.RESET}")

def print_banner():
    logo = f"""
{Colors.GRAY}    .            .                   .            .            .{Colors.RESET}
{Colors.GRAY}            .            {Colors.YELLOW}★{Colors.GRAY}                   .            .    {Colors.RESET}
{Colors.WHITE}{Colors.BOLD}       __ _ _ __ __ _ _ _ __  {Colors.RESET} {Colors.GRAY}│{Colors.RESET} {Colors.GREEN}{Colors.BOLD} █▀ ▀█▀ █ █▀▀ █▄▀ █▄█{Colors.RESET}
{Colors.WHITE}{Colors.BOLD}      / _` | '__/ _` | | '_ \\ {Colors.RESET} {Colors.GRAY}│{Colors.RESET} {Colors.GREEN}{Colors.BOLD} ▄█  █  █ █▄▄ █ █  █{Colors.RESET}
{Colors.WHITE}{Colors.BOLD}     | (_| | | | (_| | | | | |{Colors.RESET} {Colors.GRAY}│{Colors.RESET}
{Colors.WHITE}{Colors.BOLD}      \\__,_|_|  \\__,_|_|_| |_|{Colors.RESET} {Colors.GRAY}│{Colors.RESET}
{Colors.GRAY}    .            .                   .            .            .{Colors.RESET}
    """
    print(logo)

    # Status bar
    os_type, os_name = get_os_info()
    print(f"    {Colors.GREEN}{'═' * 62}{Colors.RESET}")
    print(f"    {Colors.CYAN}◈ Platform: {os_name}  │  Python: {sys.version.split()[0]}  │  v{APP_VERSION}{Colors.RESET}")
    print(f"    {Colors.GREEN}{'═' * 62}{Colors.RESET}")
    print()

def print_menu_header(title, icon="◆"):
    print()
    print(f"    {Colors.GREEN}{icon}{Colors.RESET} {Colors.BOLD}{Colors.WHITE}{title}{Colors.RESET}")
    print(f"    {Colors.GRAY}{'─' * 50}{Colors.RESET}")
    print()

def print_menu_item(key, title, desc="", icon="›"):
    if desc:
        print(f"      {Colors.GREEN}[{key}]{Colors.RESET} {icon} {Colors.BOLD}{title.ljust(28)}{Colors.RESET} {Colors.GRAY}{desc}{Colors.RESET}")
    else:
        print(f"      {Colors.GREEN}[{key}]{Colors.RESET} {icon} {Colors.BOLD}{title}{Colors.RESET}")

def print_section(title):
    print(f"\n    {Colors.YELLOW}▸ {title}{Colors.RESET}")
    print(f"    {Colors.GRAY}{'─' * 40}{Colors.RESET}")

def print_success(msg):
    print(f"    {Colors.GREEN}✓{Colors.RESET} {msg}")

def print_error(msg):
    print(f"    {Colors.RED}✗{Colors.RESET} {msg}")

def print_warning(msg):
    print(f"    {Colors.YELLOW}!{Colors.RESET} {msg}")

def print_info(msg):
    print(f"    {Colors.BLUE}ℹ{Colors.RESET} {msg}")

def print_progress(msg):
    print(f"    {Colors.CYAN}⟳{Colors.RESET} {msg}")

def wait_for_enter(msg="Devam etmek için ENTER'a basın..."):
    input(f"\n    {Colors.GRAY}{msg}{Colors.RESET}")

# --- UTILITY ---

def run_command(cmd, cwd=None, shell=False, interactive=False, capture=True):
    try:
        if interactive:
            result = subprocess.run(cmd, cwd=cwd, shell=shell)
            return result.returncode == 0, "", ""

        if capture:
            result = subprocess.run(cmd, cwd=cwd, shell=shell, capture_output=True, text=True)
            return result.returncode == 0, result.stdout, result.stderr
        else:
            result = subprocess.run(cmd, cwd=cwd, shell=shell)
            return result.returncode == 0, "", ""
    except Exception as e:
        return False, "", str(e)

def check_command(cmd):
    return shutil.which(cmd) is not None

def get_current_version():
    """Read current version from build.gradle"""
    gradle_path = "app/build.gradle.kts"
    if not os.path.exists(gradle_path):
        gradle_path = "app/build.gradle"

    if not os.path.exists(gradle_path):
        return None, None

    with open(gradle_path, 'r') as f:
        content = f.read()

    version_code = re.search(r'versionCode\s*=?\s*(\d+)', content)
    version_name = re.search(r'versionName\s*=?\s*["\']([^"\']+)["\']', content)

    return (
        int(version_code.group(1)) if version_code else None,
        version_name.group(1) if version_name else None
    )

# --- WORKFLOWS ---

def check_dependencies():
    clear_screen()
    print_banner()
    print_menu_header("SİSTEM KONTROLÜ & KURULUM", "🔍")

    # System dependencies
    print_section("Sistem Gereksinimleri")

    deps = [
        {"name": "Git", "cmd": "git", "req": True},
        {"name": "Node.js", "cmd": "node", "req": True},
        {"name": "NPM", "cmd": "npm", "req": True},
        {"name": "Java (JDK)", "cmd": "java", "req": True},
        {"name": "ADB", "cmd": "adb", "req": False, "check_func": get_adb_path},
        {"name": "Firebase CLI", "cmd": "firebase", "req": False, "install": "npm install -g firebase-tools"},
    ]

    all_ok = True
    for dep in deps:
        found = False
        version = ""

        if dep.get('check_func'):
            found = dep['check_func']() is not None
        else:
            found = check_command(dep['cmd'])

        if found:
            if dep['cmd'] == 'firebase':
                _, out, _ = run_command([dep['cmd'], "-V"])
            elif dep['cmd'] != 'adb':
                _, out, _ = run_command([dep['cmd'], "--version"])
            else:
                _, out, _ = run_command(["adb", "version"])

            if out:
                version = out.strip().split('\n')[0].split()[-1] if out.strip() else ""
            print_success(f"{dep['name']}: {version}")
        else:
            if dep['req']:
                print_error(f"{dep['name']} - EKSİK (Zorunlu)")
                all_ok = False
            else:
                print_warning(f"{dep['name']} - Eksik (Opsiyonel)")
                if dep.get('install'):
                    print(f"         {Colors.GRAY}Yüklemek için: {dep['install']}{Colors.RESET}")

    # File check
    print_section("Proje Dosyaları")

    for filename, info in REQUIRED_FILES.items():
        if os.path.exists(filename):
            print_success(f"{info['description']}")
        else:
            print_error(f"{info['description']} - EKSİK")
            for step in info['instructions']:
                print(f"         {Colors.GRAY}{step}{Colors.RESET}")

    # Web dependencies
    if check_command("npm") and os.path.exists("sticker_admin_web"):
        print_section("Web Admin Panel")

        if not os.path.exists("sticker_admin_web/node_modules"):
            print_progress("Bağımlılıklar yükleniyor...")
            success, _, err = run_command("npm install", cwd="sticker_admin_web", shell=True)
            if success:
                print_success("Web bağımlılıkları yüklendi")
            else:
                print_error(f"Yükleme hatası: {err}")
        else:
            print_success("Web bağımlılıkları hazır")

    # OS specific instructions
    print_section("Platform Özel Talimatlar")
    os_type, _ = get_os_info()

    if os_type == "mac":
        print_info("brew install node git openjdk@17")
        print_info("npm install -g firebase-tools")
    elif os_type == "windows":
        print_info("Node.js: https://nodejs.org")
        print_info("JDK 17: https://adoptium.net")
        print_info("npm install -g firebase-tools")
    else:
        print_info("sudo apt install nodejs npm git openjdk-17-jdk")
        print_info("npm install -g firebase-tools")

    wait_for_enter()

def github_pull():
    clear_screen()
    print_banner()
    print_menu_header("GIT PULL - UZAK REPODAN ÇEK", "⬇")

    if not check_command("git"):
        print_error("Git bulunamadı!")
        wait_for_enter()
        return

    print_progress("Uzak repo kontrol ediliyor...")

    # Set remote URL with token
    run_command(["git", "remote", "set-url", "origin", REPO_URL])

    # Get current branch
    _, branch, _ = run_command(["git", "branch", "--show-current"])
    branch = branch.strip() or "main"

    print_info(f"Branch: {branch}")
    print()

    # Fetch first
    print_progress("Değişiklikler alınıyor...")
    success, out, err = run_command(["git", "pull", "origin", branch])

    if success:
        if "Already up to date" in out or "Already up to date" in (err or ""):
            print_success("Zaten güncel!")
        else:
            print_success("Değişiklikler başarıyla alındı!")
            print(f"\n{Colors.GRAY}{out}{Colors.RESET}")
    else:
        if "conflict" in err.lower():
            print_error("Merge conflict var! Manuel çözüm gerekli.")
        else:
            print_error(f"Hata: {err}")

    wait_for_enter()

def github_push():
    clear_screen()
    print_banner()
    print_menu_header("GIT PUSH - GITHUB'A GÖNDER", "⬆")

    if not check_command("git"):
        print_error("Git bulunamadı!")
        wait_for_enter()
        return

    # Check status
    print_progress("Değişiklikler kontrol ediliyor...")
    print()
    run_command("git status -s", shell=True, interactive=True)

    msg = input(f"\n    {Colors.CYAN}Commit mesajı (İptal için boş bırakın):{Colors.RESET} ").strip()
    if not msg:
        return

    print()
    print_progress("İşlem başlatılıyor...")

    # Set remote URL
    run_command(["git", "remote", "set-url", "origin", REPO_URL])

    # Get branch
    _, branch, _ = run_command(["git", "branch", "--show-current"])
    branch = branch.strip() or "main"

    # Add, Commit, Push
    steps = [
        (["git", "add", "."], "Dosyalar ekleniyor..."),
        (["git", "commit", "-m", msg], "Commit oluşturuluyor..."),
        (["git", "push", "origin", branch], f"GitHub'a gönderiliyor ({branch})...")
    ]

    for cmd, desc in steps:
        print_progress(desc)
        success, out, err = run_command(cmd)

        if not success:
            if "nothing to commit" in out or "nothing to commit" in err:
                print_warning("Değişiklik yok")
                break
            elif "rejected" in err:
                print_error("Uzak sunucuda değişiklikler var!")
                print_info("Önce 'Git Pull' yapın")
            else:
                print_error(f"Hata: {err or out}")
            wait_for_enter()
            return

    print_success("Başarıyla tamamlandı!")
    wait_for_enter()

def build_apk():
    clear_screen()
    print_banner()
    print_menu_header("ANDROID APK DERLEME", "📦")

    if not os.path.exists("app"):
        print_error("Android proje klasörü bulunamadı!")
        wait_for_enter()
        return

    gradle_cmd = get_gradle_cmd()

    # Check if gradlew exists
    if not os.path.exists(gradle_cmd.replace("./", "")):
        print_error("Gradle wrapper bulunamadı!")
        print_info("Android Studio'da projeyi açıp sync yapın")
        wait_for_enter()
        return

    print_menu_item("1", "Debug APK", "Test için (imzasız)")
    print_menu_item("2", "Release APK", "Yayın için (imzalı)")
    print_menu_item("0", "İptal", "")

    choice = input(f"\n    {Colors.CYAN}Seçiminiz:{Colors.RESET} ").strip()

    if choice == "0" or not choice:
        return

    build_type = "assembleDebug" if choice == "1" else "assembleRelease"
    apk_type = "Debug" if choice == "1" else "Release"

    print()
    print_progress(f"{apk_type} APK derleniyor... (Bu işlem uzun sürebilir)")
    print()

    # Run gradle build
    success, _, _ = run_command(f"{gradle_cmd} {build_type}", shell=True, interactive=True)

    if success:
        # Find APK path
        apk_dir = f"app/build/outputs/apk/{'debug' if choice == '1' else 'release'}"
        if os.path.exists(apk_dir):
            apks = [f for f in os.listdir(apk_dir) if f.endswith('.apk')]
            if apks:
                print()
                print_success(f"{apk_type} APK başarıyla oluşturuldu!")
                print_info(f"Konum: {apk_dir}/{apks[0]}")
    else:
        print_error("Derleme başarısız!")

    wait_for_enter()

def run_on_device():
    clear_screen()
    print_banner()
    print_menu_header("CİHAZDA DERLE & ÇALIŞTIR", "▶")

    if not os.path.exists("app"):
        print_error("Android proje klasörü bulunamadı!")
        wait_for_enter()
        return

    gradle_cmd = get_gradle_cmd()
    adb = get_adb_path()

    # Check gradle wrapper
    if not os.path.exists(gradle_cmd.replace("./", "")):
        print_error("Gradle wrapper bulunamadı!")
        print_info("Android Studio'da projeyi açıp sync yapın")
        wait_for_enter()
        return

    # Check ADB
    if not adb:
        print_error("ADB bulunamadı!")
        wait_for_enter()
        return

    # Check connected devices
    print_progress("Bağlı cihazlar kontrol ediliyor...")
    success, out, _ = run_command([adb, "devices"])
    devices = [line.split('\t')[0] for line in out.strip().split('\n')[1:] if '\tdevice' in line]

    if not devices:
        print_error("Bağlı cihaz bulunamadı!")
        print_info("USB debugging'i açın ve cihazı bağlayın")
        wait_for_enter()
        return

    print_success(f"{len(devices)} cihaz bulundu: {', '.join(devices)}")
    print()

    print_menu_item("1", "Debug", "Hızlı derleme (geliştirme için)")
    print_menu_item("2", "Release", "Optimize edilmiş (test için)")
    print_menu_item("0", "İptal", "")

    choice = input(f"\n    {Colors.CYAN}Seçiminiz [1]:{Colors.RESET} ").strip() or "1"

    if choice == "0":
        return

    build_type = "Debug" if choice == "1" else "Release"
    task = f"install{build_type}"

    print()
    print_progress(f"{build_type} APK derleniyor ve cihaza yükleniyor...")
    print_warning("Bu işlem uzun sürebilir, lütfen bekleyin...")
    print()

    # Run gradle installDebug/installRelease
    success, _, _ = run_command(f"{gradle_cmd} {task}", shell=True, interactive=True)

    if not success:
        print_error("Derleme/yükleme başarısız!")
        wait_for_enter()
        return

    # Launch the app
    print()
    print_progress("Uygulama başlatılıyor...")

    package_name = "com.sticly"
    launch_cmd = f"{adb} shell monkey -p {package_name} -c android.intent.category.LAUNCHER 1"
    success, _, err = run_command(launch_cmd, shell=True)

    if success:
        print_success("Uygulama cihazda başlatıldı!")
    else:
        print_warning("Uygulama yüklendi ancak otomatik başlatılamadı")
        print_info("Manuel olarak cihazdan açabilirsiniz")

    wait_for_enter()

def install_apk():
    clear_screen()
    print_banner()
    print_menu_header("APK YÜKLEME (ADB)", "📲")

    adb = get_adb_path()
    if not adb:
        print_error("ADB bulunamadı!")
        print_info("Android SDK platform-tools yükleyin")
        os_type, _ = get_os_info()
        if os_type == "linux":
            print_info("sudo apt install adb")
        wait_for_enter()
        return

    # Check connected devices
    print_progress("Bağlı cihazlar kontrol ediliyor...")
    success, out, _ = run_command([adb, "devices"])

    devices = [line.split('\t')[0] for line in out.strip().split('\n')[1:] if '\tdevice' in line]

    if not devices:
        print_error("Bağlı cihaz bulunamadı!")
        print_info("USB debugging'i açın ve cihazı bağlayın")
        wait_for_enter()
        return

    print_success(f"{len(devices)} cihaz bulundu")
    for i, device in enumerate(devices):
        print(f"         {Colors.GRAY}{i+1}. {device}{Colors.RESET}")

    # Find APKs
    print_section("Mevcut APK Dosyaları")

    apk_files = []
    for build_type in ['debug', 'release']:
        apk_dir = f"app/build/outputs/apk/{build_type}"
        if os.path.exists(apk_dir):
            for f in os.listdir(apk_dir):
                if f.endswith('.apk'):
                    apk_files.append((f"{apk_dir}/{f}", f, build_type.upper()))

    if not apk_files:
        print_error("APK bulunamadı! Önce APK derleyin.")
        wait_for_enter()
        return

    for i, (path, name, build_type) in enumerate(apk_files):
        print_menu_item(str(i+1), f"{name}", f"({build_type})")

    choice = input(f"\n    {Colors.CYAN}APK seçin:{Colors.RESET} ").strip()

    try:
        idx = int(choice) - 1
        if 0 <= idx < len(apk_files):
            apk_path = apk_files[idx][0]
            print()
            print_progress("APK yükleniyor...")

            success, out, err = run_command([adb, "install", "-r", apk_path])

            if success:
                print_success("APK başarıyla yüklendi!")
            else:
                print_error(f"Yükleme hatası: {err}")
    except (ValueError, IndexError):
        print_error("Geçersiz seçim!")

    wait_for_enter()

def update_version():
    clear_screen()
    print_banner()
    print_menu_header("VERSİYON GÜNCELLEME", "🔢")

    gradle_path = "app/build.gradle.kts"
    if not os.path.exists(gradle_path):
        gradle_path = "app/build.gradle"

    if not os.path.exists(gradle_path):
        print_error("build.gradle bulunamadı!")
        wait_for_enter()
        return

    current_code, current_name = get_current_version()

    print_info(f"Mevcut versionCode: {current_code}")
    print_info(f"Mevcut versionName: {current_name}")
    print()

    print_menu_item("1", "versionCode +1", f"→ {current_code + 1 if current_code else 'N/A'}")
    print_menu_item("2", "Manuel güncelle", "Değerleri kendin gir")
    print_menu_item("0", "İptal", "")

    choice = input(f"\n    {Colors.CYAN}Seçiminiz:{Colors.RESET} ").strip()

    if choice == "0" or not choice:
        return

    with open(gradle_path, 'r') as f:
        content = f.read()

    if choice == "1":
        new_code = current_code + 1 if current_code else 1
        content = re.sub(
            r'(versionCode\s*=?\s*)(\d+)',
            f'\\g<1>{new_code}',
            content
        )
        print_success(f"versionCode: {current_code} → {new_code}")

    elif choice == "2":
        new_code = input(f"    {Colors.CYAN}Yeni versionCode [{current_code}]:{Colors.RESET} ").strip()
        new_name = input(f"    {Colors.CYAN}Yeni versionName [{current_name}]:{Colors.RESET} ").strip()

        if new_code:
            content = re.sub(
                r'(versionCode\s*=?\s*)(\d+)',
                f'\\g<1>{new_code}',
                content
            )
        if new_name:
            content = re.sub(
                r'(versionName\s*=?\s*["\'])([^"\']+)(["\'])',
                f'\\g<1>{new_name}\\g<3>',
                content
            )

        print_success(f"Versiyon güncellendi!")

    with open(gradle_path, 'w') as f:
        f.write(content)

    # Show new version
    new_code, new_name = get_current_version()
    print()
    print_info(f"Yeni versionCode: {new_code}")
    print_info(f"Yeni versionName: {new_name}")

    wait_for_enter()

def start_dev_server():
    clear_screen()
    print_banner()
    print_menu_header("WEB ADMIN PANEL - DEV SERVER", "🌐")

    if not os.path.exists("sticker_admin_web"):
        print_error("'sticker_admin_web' klasörü bulunamadı!")
        wait_for_enter()
        return

    if not os.path.exists("sticker_admin_web/node_modules"):
        print_progress("Bağımlılıklar yükleniyor...")
        success, _, err = run_command("npm install", cwd="sticker_admin_web", shell=True)
        if not success:
            print_error(f"Yükleme hatası: {err}")
            wait_for_enter()
            return

    print_success("Sunucu başlatılıyor...")
    print_warning("Durdurmak için CTRL+C")
    print()

    try:
        subprocess.run("npm run dev -- --open", cwd="sticker_admin_web", shell=True)
    except KeyboardInterrupt:
        print("\n")
        print_info("Sunucu durduruldu")

    wait_for_enter()

def open_live_panel():
    import webbrowser
    url = "https://sticky-dcd20.web.app"
    print_info(f"Açılıyor: {url}")
    webbrowser.open(url)
    time.sleep(1)

def firebase_deploy():
    clear_screen()
    print_banner()
    print_menu_header("FIREBASE DEPLOY", "🚀")

    if not check_command("firebase"):
        print_error("Firebase CLI eksik!")
        print_info("Yüklemek için: npm install -g firebase-tools")
        wait_for_enter()
        return

    print_menu_item("1", "Admin Panel", "Web admin panelini yayınla")
    print_menu_item("2", "Privacy Sayfası", "Gizlilik politikası sayfası")
    print_menu_item("3", "Cloud Functions", "Backend fonksiyonları")
    print_menu_item("4", "Tümü", "Hosting + Functions (Önerilen)")
    print_menu_item("0", "İptal", "")

    choice = input(f"\n    {Colors.CYAN}Seçiminiz [4]:{Colors.RESET} ").strip() or "4"

    if choice == "0":
        return

    # Admin panel
    if choice in ["1", "4"]:
        print()
        print_progress("Admin panel derleniyor...")
        success, _, err = run_command("npm run build", cwd="sticker_admin_web", shell=True)
        if not success:
            print_error(f"Derleme hatası: {err}")
            wait_for_enter()
            return

        print_progress("Admin panel yayınlanıyor...")
        subprocess.run("firebase deploy --only hosting:admin", shell=True)

    # Privacy page
    if choice in ["2", "4"]:
        print()
        print_progress("Privacy sayfası yayınlanıyor...")
        subprocess.run("firebase deploy --only hosting:privacy", shell=True)

    # Functions
    if choice in ["3", "4"]:
        print()
        print_progress("Cloud Functions yayınlanıyor...")
        subprocess.run("firebase deploy --only functions", shell=True)

    print()
    print_success("Deploy tamamlandı!")
    wait_for_enter()

def clean_project():
    clear_screen()
    print_banner()
    print_menu_header("PROJE TEMİZLİĞİ", "🧹")

    targets = [
        ("sticker_admin_web/dist", "Web build dosyaları"),
        ("sticker_admin_web/node_modules", "Web bağımlılıkları"),
        ("app/build", "Android build dosyaları"),
        (".gradle", "Gradle cache"),
        ("__pycache__", "Python cache"),
    ]

    print_warning("Bu işlem seçilen klasörleri silecektir!")
    print()

    for path, desc in targets:
        exists = "✓" if os.path.exists(path) else "✗"
        color = Colors.GREEN if os.path.exists(path) else Colors.GRAY
        print(f"    {color}[{exists}]{Colors.RESET} {desc} ({path})")

    print()
    confirm = input(f"    {Colors.YELLOW}Temizlik yapılsın mı? (e/h):{Colors.RESET} ").lower()

    if confirm != 'e':
        return

    print()
    for path, desc in targets:
        if os.path.exists(path):
            print_progress(f"Siliniyor: {path}")
            try:
                if os.path.isfile(path):
                    os.remove(path)
                else:
                    shutil.rmtree(path)
                print_success(f"{desc} silindi")
            except Exception as e:
                print_error(f"Hata: {e}")

    print()
    print_success("Temizlik tamamlandı!")
    wait_for_enter()

def main_menu():
    while True:
        clear_screen()
        print_banner()

        # Development section
        print(f"    {Colors.MAGENTA}▎{Colors.RESET} {Colors.BOLD}GELİŞTİRME{Colors.RESET}")
        print()
        print_menu_item("1", "Sistem Kontrolü", "Gereksinimleri kontrol et")
        print_menu_item("2", "Web Admin Panel", "Lokal geliştirme sunucusu")
        print_menu_item("3", "Canlı Panel", "Tarayıcıda aç")

        print()
        print(f"    {Colors.BLUE}▎{Colors.RESET} {Colors.BOLD}ANDROID{Colors.RESET}")
        print()
        print_menu_item("4", "Cihazda Çalıştır", "Derle + Yükle + Başlat")
        print_menu_item("5", "APK Derle", "Debug/Release APK oluştur")
        print_menu_item("6", "APK Yükle", "Cihaza ADB ile yükle")
        print_menu_item("7", "Versiyon Güncelle", "versionCode/Name değiştir")

        print()
        print(f"    {Colors.CYAN}▎{Colors.RESET} {Colors.BOLD}GIT & DEPLOY{Colors.RESET}")
        print()
        print_menu_item("8", "Git Pull", "Uzak repodan çek")
        print_menu_item("9", "Git Push", "GitHub'a gönder")
        print_menu_item("10", "Firebase Deploy", "Hosting & Functions yayınla")

        print()
        print(f"    {Colors.RED}▎{Colors.RESET} {Colors.BOLD}DİĞER{Colors.RESET}")
        print()
        print_menu_item("0", "Proje Temizliği", "Cache ve build dosyalarını sil")
        print_menu_item("q", "Çıkış", "")

        print()
        choice = input(f"    {Colors.GREEN}›{Colors.RESET} Seçiminiz: ").strip().lower()

        if choice == "1": check_dependencies()
        elif choice == "2": start_dev_server()
        elif choice == "3": open_live_panel()
        elif choice == "4": run_on_device()
        elif choice == "5": build_apk()
        elif choice == "6": install_apk()
        elif choice == "7": update_version()
        elif choice == "8": github_pull()
        elif choice == "9": github_push()
        elif choice == "10": firebase_deploy()
        elif choice == "0": clean_project()
        elif choice == "q": break
        else:
            print_warning("Geçersiz seçim!")
            time.sleep(1)

if __name__ == "__main__":
    try:
        main_menu()
    except KeyboardInterrupt:
        print(f"\n\n    {Colors.CYAN}Güle güle!{Colors.RESET}\n")
        sys.exit(0)
