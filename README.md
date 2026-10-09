<div align="center">

<img src="assets/logo.svg" alt="SnapOut Logo" width="108" height="108" />

# SnapOut

### *"Snap out of the scroll. Snap into your life."*

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Runtime](https://img.shields.io/badge/Runtime-Google_LiteRT--LM-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/edge/litert)
[![Network](https://img.shields.io/badge/Network-100%25_Offline-00C853?style=for-the-badge)](https://github.com)
[![Internet Permission](https://img.shields.io/badge/INTERNET_Permission-NONE_(Zero_Network)-critical?style=for-the-badge)](https://github.com)
[![Hackathon](https://img.shields.io/badge/Hackathon-AppBuilders_PH_2026-FF6F00?style=for-the-badge)](https://github.com)

**A privacy-first, offline Android app that detects mindless short-form video doomscrolling (TikTok, Reels, Shorts) from scrolling kinematics and posture, interrupting users with deterministic circuit-breaker nudges and personalized on-device LLM interventions.**

Built with ❤️ by **The Assemblers** for **AppBuilders PH Hackathon 2026**.

---

[🎯 The Problem](#-the-problem) • 
[💡 The Solution](#-the-proposed-solution-snapout) • 
[⚖️ Comparison](#️-traditional-blockers-vs-snapout) • 
[🔒 Why Local AI?](#-why-does-this-product-benefit-from-running-ai-locally) • 
[🚀 Key Features](#-key-features) • 
[🧠 Models](#-disclosed-models-used) • 
[🛠️ Tech Stack](#️-frameworks--tech-stack) • 
[🤖 AI Dev Tools](#-ai-developer-tools-disclosed) • 
[📦 Installation](#-build--installation)

</div>

---

## 🎯 The Problem

Short-form algorithmic feeds (TikTok, Instagram Reels, YouTube Shorts, X) are deliberately engineered to induce a **dissociative scrolling trance** (often described as "zombie thumb"):

- **Variable Reward Schedules & Rapid Dwell Times:** Users skip through dozens of videos per minute without retaining content, creating rapid dopamine spikes followed by cognitive depletion.
- **Physical & Temporal Disconnection:** Users scroll late at night in dark bedrooms while lying down flat, severely disrupting circadian rhythms, melatonin production, and sleep hygiene.
- **Flawed Traditional Solutions:** Existing app-blockers rely on blunt, rigid timers (e.g. *"lock Instagram after 30 minutes"*). Users instinctively override, snooze, or uninstall them because they lack context, empathy, and real-time behavioral awareness.
- **The Cloud Privacy Nightmare:** Uploading raw screen activity, ambient light levels, physical body posture, or personal habits to a remote cloud server to "analyze attention" creates unacceptable privacy and surveillance risks.

---

## 💡 The Proposed Solution: SnapOut

**SnapOut** is an intelligent on-device digital wellbeing companion that runs **100% locally with zero internet permissions** (`android.permission.INTERNET` is not even declared).

Instead of waiting for an arbitrary 30-minute timer to expire, SnapOut analyzes **how** you are interacting with your device in real time:

1. **Deterministic Sensory Trance Detection:** A continuous scoring engine computes a real-time **Focus / Trance Score (0–100)** based on scroll physics, video dwell times, tap-to-swipe ratios, ambient light, device gravity tilt (lying down in bed), and current time of day.
2. **Context-Aware AI Circuit Breakers:** When mindless trance is sustained (Score ≥ 80 for 30s), an on-device Large Language Model dynamically crafts a 1–2 sentence compassionate, non-judgmental somatic nudge notification tailored to the exact context (e.g., your actual scroll speed, physical meters scrolled, personal goal, and whether it is morning, afternoon, evening, or late night).
3. **Local Wellbeing Analyst & Conversational Coach:** An interactive dashboard featuring multi-app stacked activity charts, feed mileage metrics, an isolated AI telemetry analysis modal, and an on-device AI chat assistant that answers questions directly from SQLite history.

---

## ⚖️ Traditional Blockers vs. SnapOut

| Dimension | Traditional App Blockers | ⚡ SnapOut |
|---|---|---|
| **Trigger Mechanism** | Rigid wall-clock timers (e.g., "30 mins elapsed") | **Real-time behavioral trance scoring** (cadence, posture, dwell time) |
| **Intervention Style** | Aggressive, abrupt lockout screens (easy to override) | **Compassionate, context-aware somatic nudges** with 1-tap exit |
| **Privacy & Network** | Requires cloud sync, accounts, analytics telemetry | **Zero network permissions (`INTERNET` undeclared). 100% offline.** |
| **Intelligence** | Dumb rule countdowns | **On-device Generative SLMs** (Gemma 3 & Qwen via Google LiteRT) |
| **Telemetry Insights** | Static bar charts of minutes spent | **Interactive stacked bar timeline, feed mileage (meters scrolled), local AI chat** |

---

## 🔒 Why Does This Product Benefit From Running AI Locally?

Running AI entirely on-device is not merely an optimization—it is a foundational requirement for digital wellbeing:

> [!IMPORTANT]
> **Kernel-Level Network Isolation:**
> SnapOut completely omits the `android.permission.INTERNET` manifest permission. The Android OS kernel mathematically guarantees zero bytes can ever leave your phone. All SQLite records and prompt inferences stay in volatile device memory.

1. **Absolute Privacy:**
   - Physical posture (lying down flat in bed), ambient room lighting (dark bedroom), app usage patterns, and personal life goals are among the most sensitive personal data on a smartphone.
2. **Instant Real-Time Latency & Zero Cloud Costs:**
   - No cloud API round-trips, no recurring per-token server costs, and zero external service dependencies.
3. **Works Anywhere, 100% Offline:**
   - Functions seamlessly on airplane mode, subway commutes, or in remote areas with zero cell connectivity.
4. **User Trust & Psychological Safety:**
   - Users are far more receptive to self-reflection and candid about their habits when they have mathematical certainty that their scrolling behavior is not being monetized, fingerprinted, or analyzed by third-party servers.

---

## 🚀 Key Features

### 1. Real-Time Focus / Trance Meter & Physics Engine
- **Swipe Rate & Feed Mileage:** Measures scroll frequency and translates DPI-scaled finger drags into physical distance scrolled (e.g., *"You've scrolled 142 meters"*).
- **Video Dwell Time Analysis:** Detects rapid skimming (<6s per video) versus intentional viewing.
- **Passive Ratio:** Flags low-engagement scrolling (e.g., <5 taps/likes per 100 swipes).
- **Environmental Context:** Gathers ambient light sensor readings and accelerometer gravity vectors (detecting when you are scrolling lying flat in bed in total darkness).

### 2. Time-Aware Contextual Nudges
- Nudges and fallback heuristics are strictly grounded in your current time of day:
  - 🌅 **Morning (05:00–11:59):** Intent check for the day ahead, physical stretch reminders.
  - ☀️ **Afternoon (12:00–16:59):** Distraction check vs. intentional break.
  - 🌆 **Evening (17:00–21:59):** Offline evening winding down.
  - 🌙 **Late Night (22:00–04:59):** Sleep hygiene and bedtime preservation.
- Actionable notification buttons:
  - `[ Take me out ]`: Instantly exits the feed to the Android launcher.
  - `[ Snooze 15 min ]`: Temporarily silences nudges for deliberate browsing sessions.

### 3. Interactive Multi-App Stacked Bar Timeline (24-Hour Activity)
- **Multi-App Stacked Bars:** Hourly distribution of scrolling minutes across monitored apps (TikTok, Instagram, YouTube Shorts, X, Facebook, Reddit).
- **Interactive Highlighting:** Tap any hour column to inspect exact minutes per app for that hour.
- **App Isolation Chips:** Tap an app filter chip to highlight only that app's stacked segment across the entire 24-hour timeline.

### 4. Dedicated Activity & History Tab
- A dedicated 5th navigation tab separating **Recent Nudges** and **Feed Sessions** away from the dashboard for fast inspection and auditability.
- Filter by **All**, **Nudges**, or **Sessions** with timestamps, trigger scores, and AI response latencies.

### 5. AI Telemetry Analysis Modal Dialog
- In-depth behavioral synthesis moved into an on-demand modal (`AI Telemetry Analysis → [ View Analysis ↗ ]`), keeping the main dashboard clean and eliminating vertical scrolling fatigue.

### 6. Context-Aware Local AI Chatbot
- Interactive on-device attention coach with query history collapsing (`Show earlier messages / Collapse`).
- Inspects real-time SQLite telemetry data to answer questions like:
  - *"Which app do I scroll the most?"*
  - *"How far did I scroll today?"*
  - *"Am I meeting my goal?"*

### 7. Transparent Privacy Audit Tab
- Clearly displays **What Is Collected** (kinematics, engagement, package ID, posture, context) vs. **What Is NEVER Collected** (screen content, video, keystrokes, audio, GPS, identity).
- Backed by zero network permissions and local database purge controls.

### 8. "No Model (Rules)" Toggle
- Instantly switch to deterministic rule-based heuristics in Settings or Insights to compare rule heuristics against LLM generation without downloading a model.

---

## 🧠 Disclosed Models Used

SnapOut utilizes quantized on-device SLMs (Small Language Models) powered by Google's **LiteRT-LM** runtime with GPU acceleration (OpenCL/Vulkan) and automated CPU fallback:

| Model | Quantization | Size | Description & Usage |
|---|---|---|---|
| **Gemma 3 1B IT** (`gemma3-1b-it-int4.litertlm`) | int4 | ~584 MB | **Primary Model.** Google's state-of-the-art compact instruction-tuned model. Generates empathetic, somatic reframing nudges and comprehensive 6-line dashboard analytics. |
| **Qwen 2.5 / 3 0.6B** (`qwen3_0_6b_mixed_int4.litertlm`) | mixed int4 | ~390 MB | **Ultra-Lightweight Model.** High-speed alternative for lower-memory devices with custom ChatML template support. |

> [!NOTE]
> The app also bundles 14 deterministic rule-based fallback templates and the `RuleInsights` heuristic engine when running in "No Model" mode.

---

## 🛠️ Frameworks & Tech Stack

- **Platform:** Android OS (API 26+ up to Android 14/15, target SDK 34/36).
- **Language:** 100% Kotlin with Coroutines & StateFlow.
- **UI Toolkit:** Jetpack Compose (Material Design 3 dark aesthetic).
- **On-Device Inference:** Google LiteRT-LM runtime (`com.google.ai.edge.litert:litert-lm`).
- **Operating System Services:**
  - Android Accessibility Service (filtering purely event metrics: `TYPE_VIEW_SCROLLED`, `TYPE_VIEW_CLICKED`, with `canRetrieveWindowContent = false` for zero screen snooping).
  - Android SensorManager (`TYPE_LIGHT`, `TYPE_GRAVITY`).
  - Android NotificationManager with heads-up reminder actions.
- **Local Storage:** SQLite (`SnapOutDb.kt`) with local purge capabilities.

---

## 🤖 AI Developer Tools Disclosed

This hackathon project was designed, developed, and iterated using state-of-the-art AI-assisted engineering agents:
- **Google Antigravity:** Used for pair programming, reactive terminal debugging, Jetpack Compose UI architecture, on-device ADB device deployment, LiteRT prompt design, and iterative code refactoring.
- **Devin AI:** Leveraged during architectural ideation, background service design, and edge runtime testing workflows.

---

## 📦 Build & Installation

### Requirements
- JDK 17 (Adoptium / Temurin recommended)
- Android SDK 34+
- Android device connected via ADB

### Build APK
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Push Model File to Device
```bash
# Push Gemma 3 1B or Qwen to app storage
adb shell mkdir -p /sdcard/Android/data/com.assemblers.snapout/files
adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/com.assemblers.snapout/files/
```

### Verification & Testing
1. Enable Airplane Mode on the target device.
2. Open SnapOut → Enable Accessibility Service (*SnapOut scroll detection*).
3. Check the **Insights** tab: Verify the model is loaded or switch between models / "No Model (Rules Only)".
4. Scroll on TikTok, YouTube Shorts, or Instagram: Observe the real-time Focus/Trance meter and receive context-aware circuit-breaker nudges!


