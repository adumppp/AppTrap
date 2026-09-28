<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="AppTrap Logo" width="120" height="120" style="border-radius: 20px;">
</p>

<h1 align="center">AppTrap — Android Privacy Analyzer</h1>

<p align="center">
  <strong>A rule-based + ML-powered Android application that detects privacy-overreaching apps on your device</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Oreo)-blue" alt="Min SDK">
  <img src="https://img.shields.io/badge/Target%20SDK-36-blue" alt="Target SDK">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Dataset-2%2C308%20Apps-orange" alt="Dataset">
  <img src="https://img.shields.io/badge/License-Academic%20(FYP)-lightgrey" alt="License">
</p>

---

## 📖 About

**AppTrap** is an Android privacy analysis tool developed as a **Final Year Project (FYP)** for the Diploma in Cybersecurity program. It scans all installed applications on a user's device and evaluates whether each app's requested permissions are consistent with its claimed purpose — or if they overreach into suspicious territory.

Unlike simple permission counters, AppTrap uses an **8-stage analysis pipeline** combining a curated dataset of 2,308 apps, online research lookups (Google Play Store & Exodus Privacy), k-Nearest Neighbors machine learning, and cross-permission anomaly detection rules to deliver accurate, context-aware risk verdicts.

### 🎯 Problem Statement

Most Android users are unaware of how many sensitive permissions their installed apps request — and whether those permissions are actually justified. A flashlight app requesting SMS, contacts, and location access is a red flag, but a messaging app requesting the same permissions is perfectly normal. AppTrap bridges this gap by analyzing permissions **in context**.

---

## ✨ Features

### Core Analysis
- **8-Stage TrustEngine Pipeline** — Dataset lookup → Research Engine → Package-name heuristic → K-NN ML → Manifest analysis → Permission differential → Anomaly rules → Final verdict
- **19 Semantic Capabilities** — SMS, call log, contacts, location, overlay, boot, camera, microphone, storage, notifications, biometric, network, bluetooth, NFC, install unknown apps, phone state, accessibility, usage stats, DRM
- **4-Tier Risk Classification** — CONSISTENT (safe), OVERREACH (mild), DECEPTIVE (concerning), SPYWARE-LIKE (critical)
- **Cross-Permission Anomaly Detection** — 6 behavioral rules that catch suspicious permission combos (e.g., overlay + boot + network = stalkerware pattern)

### Intelligence Sources
- **Curated Dataset** — 2,308 verified app profiles across 16 categories with expected permission baselines
- **ResearchEngine** — Live lookups via Google Play Store (batchexecute API) and Exodus Privacy database
- **SimpleML (k-NN)** — Jaccard similarity classifier (k=5) for unknown apps not in the dataset or online sources
- **OEM Bypass** — Recognizes manufacturer platform apps (Xiaomi, Samsung, Huawei, OPPO, Vivo, etc.) to avoid false positives

### User Experience
- **Material 3 Dark Theme** — Modern, eye-friendly design with dynamic color coding per risk level
- **Animated Scan Dialog** — Breathing shield, gradient progress bar, live counters, elapsed timer with adaptive pacing
- **Detail View** — Per-app breakdown with score ring, risk badge, suspicious signals, normal permissions, and action buttons
- **Safe Apps View** — Dedicated screen showing all apps that passed every check (severity = 0)
- **Onboarding** — First-run explainer in plain language for non-technical users
- **Bilingual Support** — English and Bahasa Malaysia (EN/BM toggle)

### Data & Feedback
- **CSV Export** — 13-field export with full analysis data for every scanned app
- **Feedback System** — In-app email feedback with automatic scan summary attachment
- **TrustLog** — Complete engine decision trace for every app (debug/academic evidence)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    TrustEngine.kt                        │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │ STAGE 1  │  │   STAGE 2    │  │     STAGE 3        │ │
│  │ Dataset  │→ │ ResearchEngine│→ │ K-NN (SimpleML.kt) │ │
│  │ Lookup   │  │ (Exodus/Play)│  │ Jaccard, k=5       │ │
│  └──────────┘  └──────────────┘  └────────────────────┘ │
│       ↓ (first match wins)                               │
│  ┌──────────────────────────────────────────────────┐   │
│  │ STAGE 4: Manifest → 19 observed capabilities     │   │
│  │ STAGE 5: Expected vs Observed differential       │   │
│  │ STAGE 6: Confidence adjustment + severity caps   │   │
│  │ STAGE 7: Cross-permission anomaly rules (6)      │   │
│  │ STAGE 8: Final verdict → TrustVerdict            │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────┐  ┌──────────────────────────┐
│   MainActivity.kt   │  │    DetailActivity.kt     │
│ • Scan all apps     │→ │ • Per-app risk breakdown │
│ • Top 10 risky list │  │ • Score ring + signals   │
│ • Distribution bar  │  │ • Remove / Keep actions  │
│ • CSV export        │  └──────────────────────────┘
│ • Safe apps view    │
└─────────────────────┘
```

---

## 📂 Project Structure

```
app/src/main/
├── assets/
│   └── dataset_final.csv              # 2,308 app profiles (16 categories)
├── java/com/example/androidprivacyanalyzer/
│   ├── TrustEngine.kt                 # 8-stage analysis pipeline (856 lines)
│   ├── ResearchEngine.kt              # Exodus + Play Store + heuristic lookups
│   ├── SimpleML.kt                    # k-NN classifier (Jaccard similarity)
│   ├── AppDataset.kt                  # CSV dataset loader & profile lookup
│   ├── TrustVerdict.kt                # Verdict data class (Serializable)
│   ├── TrustLog.kt                    # Engine debug logging system
│   ├── MainActivity.kt                # Home screen, scan orchestration, CSV export
│   ├── DetailActivity.kt              # Per-app risk detail view
│   ├── ConsistentAppsActivity.kt      # Safe apps list
│   ├── FeedbackActivity.kt            # User feedback via email
│   ├── OnboardingActivity.kt          # First-run explainer
│   └── ui/
│       ├── ScanDialog.kt              # Animated scanning dialog
│       ├── RiskAdapter.kt             # RecyclerView adapter for risky apps
│       ├── ConsistentAdapter.kt       # RecyclerView adapter for safe apps
│       ├── RiskLevel.kt               # Risk level colors, labels, drawables
│       ├── SignalViews.kt             # Signal card rendering
│       ├── PermissionPlainLanguage.kt  # Human-friendly permission descriptions
│       ├── LanguageToggle.kt          # EN/BM language switcher
│       ├── DeleteGuideDialog.kt       # Step-by-step app removal guide
│       └── UiAnimations.kt           # Breathe, pulse, morph, count animations
└── res/
    ├── layout/                        # 12 XML layouts
    ├── anim/                          # 9 animation resources
    ├── drawable/                      # Icons, badges, backgrounds, spinner
    ├── values/                        # Colors, strings, styles, themes
    └── values-ms/                     # Bahasa Malaysia translations
```

---

## 📊 Dataset Categories (16)

| Category | Examples |
|----------|----------|
| Communications / Messaging | WhatsApp, Telegram, Signal |
| Web Browser | Chrome, Firefox, Brave |
| Social Media | Instagram, TikTok, Facebook |
| Email Client | Gmail, Outlook |
| Cloud Storage | Google Drive, Dropbox |
| Maps / Navigation | Google Maps, Waze |
| Video Streaming | YouTube, Netflix |
| Music Streaming | Spotify, Apple Music |
| Productivity | Microsoft Office, Notion |
| Security / Antivirus | Kaspersky, Bitdefender |
| Finance / Banking | Banking apps, e-wallets |
| Games | PUBG, Mobile Legends, Roblox |
| Media / Photo Editing | Snapseed, Adobe Lightroom |
| System Tools | Launchers, file managers |
| Smart Home / IoT | Google Home, Tuya Smart |
| News / RSS | Google News, Flipboard |

---

## 🚀 Installation

### Prerequisites

- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 11** or higher
- **Android SDK** with API Level 26+ (Android 8.0 Oreo)
- A physical Android device is recommended for accurate scanning (emulator has limited installed apps)

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/adumppp/AppTrap.git
   cd AppTrap
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select **File → Open** and navigate to the cloned `AppTrap` folder
   - Wait for Gradle sync to complete (this may take a few minutes on first run)

3. **Build the project**
   - Click **Build → Make Project** or press `Ctrl+F9`
   - Ensure there are no build errors

4. **Run on a device**
   - Connect your Android device via USB (enable **USB Debugging** in Developer Options)
   - Or create an AVD (Android Virtual Device) from **Tools → Device Manager**
   - Click **Run → Run 'app'** or press `Shift+F10`
   - Select your device and wait for the app to install

### Direct APK Install

If you have a pre-built APK:

1. Transfer the `apptrap.apk` to your Android device
2. Open the APK file on your device
3. Allow installation from unknown sources if prompted
4. Launch **AppTrap** from your app drawer

---

## 📱 Usage

1. **First Launch** — The onboarding screen explains what AppTrap does in plain language. Choose your language (EN/BM) and tap **Start**.

2. **Scan** — Tap the **Scan My Apps** button. AppTrap will analyze all non-system apps on your device. The animated dialog shows real-time progress.

3. **Review Results** — After scanning:
   - The **top 10 riskiest apps** are displayed with color-coded risk badges
   - A **distribution bar** shows the proportion of each risk level
   - Tap any app to see the full **detail breakdown** (score ring, suspicious signals, normal permissions)

4. **Take Action** — In the detail view:
   - Tap **Remove** for a step-by-step guide to uninstall/disable the app
   - Tap **Keep** if you trust the app

5. **Safe Apps** — Tap **Safe Apps** to see all apps that passed every check (severity = 0)

6. **Export** — Tap the export button to save a full CSV report of all scan results

7. **Feedback** — Submit feedback directly via email through the in-app feedback system

---

## 🔬 How the Analysis Works

### Risk Scoring

Each app receives a **severity score (0–100)** based on:

| Factor | Impact |
|--------|--------|
| Unexpected capabilities for the app's role | Weighted penalty per capability (e.g., SMS = +8, camera = +4) |
| Unknown app penalty | +1 if app has unexpected weighted capabilities and isn't in the dataset |
| Anomaly rule triggers | +4 to +7 per rule (stalkerware pattern, spyware pattern, accessibility abuse, etc.) |
| Confidence-based caps | Limits max severity for apps with uncertain baselines |

### Risk Labels

| Label | Severity | Meaning |
|-------|----------|---------|
| 🟢 **CONSISTENT** | 0 | Permissions match expected behavior perfectly |
| 🟡 **OVERREACH** | 1–10 | Slightly more permissions than expected |
| 🟠 **DECEPTIVE** | 11–25 | Significant permission overreach beyond stated purpose |
| 🔴 **SPYWARE-LIKE** | 26–100 | Severe overreach or dangerous permission combinations |

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI Framework | Android Views + Material Design 3 |
| ML Algorithm | k-Nearest Neighbors (Jaccard similarity, k=5) |
| Online Research | Exodus Privacy API, Google Play Store (batchexecute) |
| Async | Kotlin Coroutines + ExecutorService |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 16 (API 36) |
| Build System | Gradle (Kotlin DSL) with Version Catalog |

---

## 👥 Team

| Name | Role |
|------|------|
| **Adam** | Software logic, TrustEngine pipeline, ML integration, ResearchEngine |
| **Izz** | UI/UX design, Material3 layouts, animations, dark theme |
| **Ikhwan** | Dataset construction, validation, evaluation methodology |

**Supervisor:** Mr. Yasir

---

## 📄 Academic Context

This project was developed as part of the **Final Year Project (FYP)** for the Diploma program at the polytechnic level, spanning 19 weeks from proposal to final defense.

### Key Contributions
- A curated dataset of **2,308 Android app permission profiles** across 16 categories
- An **8-stage hybrid analysis pipeline** combining rule-based detection with k-NN machine learning
- **Real-time online research** via Exodus Privacy and Google Play Store APIs
- **Cross-permission anomaly rules** for detecting stalkerware, spyware, and permission abuse patterns
- A **user-friendly mobile interface** designed for non-technical users

---

## 📜 License

This project was developed for academic purposes as a Final Year Project. All rights reserved by the development team.

---

<p align="center">
  <strong>AppTrap</strong> — Because your privacy shouldn't be an afterthought. 🛡️
</p>
