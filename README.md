# Sticly - WhatsApp Sticker App

A professional Android application for creating and managing WhatsApp sticker packs with cloud-based storage and an admin panel.

---

## ✨ Features

- **Cloud-Based Storage:** Firebase-powered sticker management with real-time sync
- **Premium System:** Free and premium sticker pack support with in-app purchases
- **Custom Sticker Creator:** Create your own stickers from photos with background removal
- **Favorites System:** Track and prioritize favorite sticker packs
- **Category Management:** Organize stickers by categories
- **Animated Stickers:** Full support for animated WebP stickers
- **WhatsApp Integration:** Direct sticker pack addition to WhatsApp

---

## 🛠️ Setup

To check dependencies and configure the project, use the setup script:

```bash
python3 stickers_setup.py
```

**Firebase Admin SDK Setup:**
1. Go to [Firebase Console](https://console.firebase.google.com) > Project Settings > **Service Accounts**
2. Click **"Generate New Private Key"**
3. Save the file as `firebase-admin-sdk.json` in the project root

---

## 📱 Requirements

| Requirement | Description |
|-------------|-------------|
| Android Studio | For building and compiling the APK |
| Firebase Account | Database and file storage |
| google-services.json | Must be placed in the `app/` folder |

---

## 📂 Project Structure

```
StickyApp/
├── app/                      # Android app source code (Kotlin)
├── sticker_admin_web/        # React-based admin panel
├── sticky-privacy/           # Privacy policy page
├── stickers_setup.py         # Setup and automation script
└── README.md                 # This file
```

---

## 🚀 Deployment

Deploy to Firebase Hosting using the setup script:
```bash
python3 stickers_setup.py
# Select option 3 to deploy both admin panel and privacy policy
```

---

## 📄 Privacy Policy

View our privacy policy at: [Privacy Policy](https://sticky-privacy-legal.web.app)

---

## 📄 License

This project is developed to be compatible with WhatsApp Sticker APIs.

**Version:** 1.0.3 (January 2026)
