<div align="center">

<img src="store_assets/Play Store/STICKY.png" alt="Sticly Banner" width="600"/>

# Sticly — WhatsApp Sticker App

**AI-Powered Sticker Creator & Manager for WhatsApp**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://play.google.com/store/apps/details?id=com.sticly)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![React](https://img.shields.io/badge/Admin_Panel-React-61DAFB?logo=react&logoColor=black)](https://sticky-dcd20.web.app)
[![Version](https://img.shields.io/badge/Version-4.0.0-blue)]()
[![License](https://img.shields.io/badge/License-Proprietary-red)]()

*Create, manage, and share stunning WhatsApp sticker packs with AI assistance, professional editing tools, and a cloud-powered admin panel.*

</div>

---

## 📸 Screenshots

<div align="center">
<table>
<tr>
<td><img src="store_assets/Play Store/Create Stickers in Seconds.png" width="160"/></td>
<td><img src="store_assets/Play Store/Create Stickers in Seconds (1).png" width="160"/></td>
<td><img src="store_assets/Play Store/Create Stickers in Seconds (2).png" width="160"/></td>
<td><img src="store_assets/Play Store/Create Stickers in Seconds (3).png" width="160"/></td>
</tr>
<tr>
<td><img src="store_assets/Play Store/Create Stickers in Seconds (4).png" width="160"/></td>
<td><img src="store_assets/Play Store/Create Stickers in Seconds (5).png" width="160"/></td>
<td><img src="store_assets/Play Store/Create Stickers in Seconds (6).png" width="160"/></td>
<td><img src="store_assets/Play Store/Create Stickers in Seconds (7).png" width="160"/></td>
</tr>
</table>
</div>

---

## ✨ Features

### 🎨 Sticker Creation & Editing
- **AI Background Removal** — ML Kit-powered subject segmentation removes backgrounds instantly
- **Professional Photo Editor** — Crop, rotate, draw, add text/emoji, apply filters with PhotoEditor SDK
- **Video/GIF to Sticker** — Convert any video or GIF to animated WebP stickers using FFmpeg
- **Lottie Animation Support** — Import and convert Telegram animated stickers (TGS/Lottie)
- **Custom Sticker Maker** — Full-featured editor for creating stickers from scratch

### 📦 Sticker Management
- **Cloud-Based Storage** — Firebase Firestore + Storage with real-time sync across devices
- **Category System** — 16 categories (humor, love, animals, anime, gaming, food, etc.)
- **Premium & Free Packs** — Dual-tier system with Google Play Billing integration
- **Favorites System** — One-tap favorites with smart tracking
- **Animated Stickers** — Full support for animated WebP stickers in WhatsApp

### 🤖 AI-Powered Features
- **DeepSeek AI Naming** — Automatic intelligent pack naming with theme emojis
- **AI Translation** — Auto-translate pack names to 17+ languages
- **Smart Category Detection** — AI analyzes sticker content and assigns the correct category
- **Wilson Score Ranking** — YouTube-inspired algorithm that balances popularity, freshness, and diversity

### 📊 Analytics & Ranking (v4.0 New!)
- **Wilson Score Interval Algorithm** — 95% confidence interval ranking like Reddit/YouTube
- **Time Decay System** — Fresh content gets boosted (4x for new, gradual decay over 30 days)
- **Diversity Bonus** — Animated (+8%), premium (+5%), both (+15%) get visibility boost
- **User Preference Intelligence** — Category engagement analysis with visual dashboards
- **Content Health Metrics** — Track active/passive ratio, premium distribution, popular packs

### 📱 Telegram Import
- **Bulk Import** — Import multiple Telegram sticker packs at once via bot token
- **Smart Splitting** — Auto-split large packs (100+ stickers) into WhatsApp-compatible packs (max 30)
- **Max Stickers Control** — Download only the first N stickers from large packs
- **AI Naming & Emoji** — Auto-generate themed names with emoji: `Duck 🦆`, `Duck 2 🦆`
- **Duplicate Detection** — Prevents importing the same pack twice
- **Animated Conversion** — TGS → WebP conversion with FFmpeg pipeline

### 🌐 Web Admin Panel
- **React + Vite Dashboard** — Modern, responsive admin panel for content management
- **Batch Operations** — Add, edit, delete, publish sticker packs in bulk
- **Statistics Dashboard** — Real-time analytics with charts, leaderboards, algorithm insights
- **Draft System** — Import to drafts first, review, then publish
- **Multi-language Support** — Manage translations for all pack names

### 💰 Monetization
- **Google AdMob** — Banner and interstitial ads with Unity Ads mediation
- **Premium Subscriptions** — Google Play Billing v7 integration
- **Ad-Free Premium** — Premium users enjoy an ad-free experience

---

## 🏗️ Architecture

```
StickyApp/
├── app/                        # 📱 Android app (Kotlin)
│   ├── src/main/java/com/sticly/
│   │   ├── MainActivity.kt         # Main screen, Wilson Score ranking
│   │   ├── PackAdapter.kt          # Sticker pack list adapter
│   │   ├── StickerMakerActivity.kt # Photo editor & sticker creator
│   │   ├── StickerProvider.kt      # WhatsApp content provider
│   │   ├── StickerRepository.kt    # Firebase data layer
│   │   ├── Models.kt               # Data models
│   │   └── ...
│   └── libs/
│       └── ffmpeg-kit-*.aar        # FFmpeg for video/animation conversion
│
├── sticker_admin_web/          # 🌐 React admin panel (Vite + TypeScript)
│   └── src/
│       ├── App.tsx                  # Main dashboard (~5500 lines)
│       └── utils/
│           ├── telegramImporter.ts  # Telegram bulk import engine
│           ├── deepseekService.ts   # AI naming, translation, categorization
│           └── ...
│
├── functions/                  # ☁️ Firebase Cloud Functions
│   └── index.js                    # Telegram proxy, server-side logic
│
├── sticker_maker/              # 🎨 Sticker maker module
├── sticky-privacy/             # 📄 Privacy policy page
├── store_assets/               # 🖼️ Play Store assets & screenshots
├── firebase.json               # Firebase hosting config
├── firestore.rules             # Firestore security rules
└── storage.rules               # Storage security rules
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Mobile** | Kotlin, Android SDK 35, Jetpack Libraries |
| **Backend** | Firebase (Firestore, Storage, Auth, Cloud Functions, Hosting) |
| **Admin Panel** | React 19, Vite 7, TypeScript 5.9, TailwindCSS |
| **AI** | DeepSeek API (naming, translation, categorization) |
| **ML** | Google ML Kit (subject segmentation / background removal) |
| **Media** | FFmpeg Kit, Lottie, Glide, ExoPlayer (Media3) |
| **Ads** | Google AdMob, Unity Ads SDK |
| **Billing** | Google Play Billing v7 |
| **Image Editing** | PhotoEditor SDK, uCrop |

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Description |
|-------------|-------------|
| Android Studio Hedgehog+ | For building the Android app |
| Node.js 18+ | For the admin panel |
| Firebase Account | Database, storage, and hosting |
| `google-services.json` | Place in `app/` folder |

### Quick Setup

```bash
# 1. Clone the repository
git clone https://github.com/arain-0/StickyApp.git
cd StickyApp

# 2. Run the setup script
python3 stickers_setup.py

# 3. Build the Android app
./gradlew assembleDebug

# 4. Start the admin panel
cd sticker_admin_web
npm install && npm run dev
```

### Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com) → Project Settings → **Service Accounts**
2. Click **"Generate New Private Key"**
3. Save as `firebase-admin-sdk.json` in the project root
4. Place `google-services.json` in the `app/` directory

### Deploy Admin Panel

```bash
cd sticker_admin_web
npm run build
npx firebase-tools deploy --only hosting
```

---

## 📈 Ranking Algorithm (v4.0)

Sticly uses a **Wilson Score Interval** algorithm inspired by YouTube and Reddit:

```
Score = (p̂ + z²/2n - z√(p̂(1-p̂)/n + z²/4n²)) / (1 + z²/n)
```

Where:
- **p̂** = positive ratio (favorites / views)
- **z** = 1.96 (95% confidence interval)
- **n** = total interactions (views)

Enhanced with:
- ⏰ **Time Decay** — 4x boost for ≤2 days, gradual decay to 0.85x for 30+ days
- 🎭 **Diversity Bonus** — Animated +8%, Premium +5%, Both +15%
- ⭐ **Popular Flag** — +30% boost for editor-picked packs
- 🎲 **Session Jitter** — ±5% deterministic variation for freshness

---

## 🌍 Supported Languages

The app and admin panel support **17 languages**: Turkish, English, German, French, Spanish, Portuguese, Italian, Russian, Arabic, Hindi, Japanese, Korean, Chinese, Thai, Vietnamese, Indonesian, Filipino.

---

## 📄 Privacy Policy

View our privacy policy at: [https://sticky-privacy-legal.web.app](https://sticky-privacy-legal.web.app)

---

## 📋 Version History

| Version | Highlights |
|---------|-----------|
| **4.0.0** | Wilson Score ranking, AI-powered stats, Telegram import overhaul, naming format fix |
| **3.3.1** | Performance improvements, animation fixes, popular packs redesign |
| **3.0.0** | Sticker Maker, background removal, video-to-sticker conversion |
| **2.0.0** | Premium system, Google Play Billing, Unity Ads integration |
| **1.0.0** | Initial release with basic sticker management |

---

<div align="center">

**Built with ❤️ by [arain-0](https://github.com/arain-0)**

*Sticly — Create Stickers in Seconds*

</div>
