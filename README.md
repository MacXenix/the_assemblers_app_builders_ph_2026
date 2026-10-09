# SnapOut

> **A deterministic sensor rule catches when you're stuck in a dopamine loop; a 100% offline, on-device LLM talks you out of it personally and compassionately.**

Built by **The Assemblers** for **AppBuilders PH Hackathon 2026**.

---

## 🎯 The Problem

Short-form algorithmic feeds (TikTok, Instagram Reels, YouTube Shorts, X) are engineered specifically to induce a **dissociative scrolling trance** (often dubbed "zombie thumb"):
- **Variable Reward Schedules & Rapid Dwell Times:** Users skip through dozens of videos per minute without retaining content or feeling genuine enjoyment.
- **Physical & Temporal Disconnection:** Users scroll late at night in dark rooms while lying down, disrupting circadian rhythms and sleep hygiene.
- **Flawed Existing Solutions:** Traditional app-blockers rely on rigid, blunt timers (e.g. "shut down Instagram after 30 minutes"). Users instinctively override, snooze, or uninstall them because they lack context, empathy, and real-time awareness. Sending raw screen activity, ambient light, posture, or personal habits to a cloud server to "analyze attention" creates massive privacy and surveillance violations.

---

## 💡 The Proposed Solution: SnapOut

**SnapOut** is an on-device digital wellbeing companion that operates **100% locally with zero internet access**. 

Instead of waiting for an arbitrary 30-minute timer to expire, SnapOut analyzes **how** you are interacting with your device in real-time:
1. **Deterministic Sensory Trance Detection:** A continuous scoring engine computes a real-time **Focus / Trance Score (0–100)** based on scroll physics, video dwell time, tap-to-swipe ratio, ambient light, device gravity tilt (lying down), and local time.
2. **Contextual AI Circuit Breakers:** When mindless trance is sustained (Score ≥ 80 for 30s), an on-device Large Language Model dynamically drafts a 1–2 sentence compassionate, non-judgmental somatic nudge notification tailored to the exact context (e.g., your actual scroll speed, meters scrolled, personal goal, and time of day).
3. **Local Wellbeing Analyst & Conversational Coach:** An interactive dashboard with telemetry visualizations (feed mileage, dwell times, 24-hour activity timeline) paired with an on-device AI coach that answers questions and synthesizes behavioral insights directly from SQLite history.

---

## 🚀 Key Features

### 1. Real-Time Focus / Trance Meter & Physics Engine
- **Swipe Rate & Feed Mileage:** Measures scroll frequency and translates DPI-scaled finger drag into physical meters scrolled (e.g., *"You've scrolled 142 meters"*).
- **Video Dwell Time Analysis:** Detects rapid skimming (<6s per video) vs. intentional viewing.
- **Passive Ratio:** Flags low-engagement scrolling (e.g., <5 taps/likes per 100 swipes).
- **Environmental Context:** Gathers ambient light sensor readings and accelerometer gravity vector (detecting when you are scrolling lying flat in bed in total darkness).

### 2. Time-Aware Contextual Nudges
- Nudges are strictly grounded in your current time of day:
  - **Morning (05:00–11:59):** Intent check for the day ahead, physical stretch reminders.
  - **Afternoon (12:00–16:59):** Distraction check vs. intentional break.
  - **Evening (17:00–21:59):** Offline evening winding down.
  - **Late Night (22:00–04:59):** Sleep hygiene and bedtime preservation.
- Interactive notification actions:
  - `[ Take me out ]`: Instantly exits the feed to the Android launcher.
  - `[ Snooze 15 min ]`: Temporarily silences nudges for deliberate sessions.

### 3. Local AI Dashboard & 24-Hour Activity Timeline
- **Per-App Telemetry Breakdown:** Individualized dashboards for TikTok, Instagram, YouTube Shorts, X, Facebook, and Reddit displaying sessions, minutes, meters scrolled, seconds/video, bedtime %, and peak usage hours.
- **24-Hour Timeline Bar Chart:** Color-coded visualization mapping scrolling minutes across morning, afternoon, evening, and late-night hours.
- **Collapsible AI Chatbot:** Interactive on-device attention coach with query history collapsing (`Show earlier messages / Collapse`) that inspects telemetry data to answer questions like *"Which app do I scroll the most?"* or *"How far did I scroll?"*.
- **"No Model (Rules)" Toggle:** Instantly disable the neural network to test and compare deterministic rule heuristics against LLM generation.

---

## 🔒 Why Does This Product Benefit From Running AI Locally?

Running AI entirely on-device is not just a technical feature—it is a foundational requirement for digital wellbeing:

1. **Absolute Privacy with Zero Network Permission (`android.permission.INTERNET` is NOT declared):**
   - Screen monitoring data, physical posture (lying down), ambient room light (dark bedroom), app usage patterns, and personal life goals are among the most sensitive personal data on a phone.
   - SnapOut physically cannot transmit telemetry to external servers. All SQLite records and prompt inferences stay in volatile device memory.
2. **Instant Latency & Zero Cost:**
   - No cloud API round-trips, no per-token API costs, and zero external service dependencies.
3. **Works Anywhere, Completely Offline:**
   - Functions flawlessly on airplane mode, during subway commutes, or in remote areas with zero cell signal.
4. **User Trust & Non-Judgmental Psychological Safety:**
   - Users are far more honest with their wellbeing goals and candid questions when they have mathematical certainty that their scrolling habits are not being monetized, fingerprinted, or analyzed by third-party servers.

---

## 🧠 Disclosed Models Used

SnapOut utilizes quantized on-device SLMs (Small Language Models) powered by Google's **LiteRT-LM** (formerly TensorFlow Lite / MediaPipe GenAI) runtime with GPU acceleration (OpenCL/Vulkan) and automated CPU fallback:

| Model | Quantization | Size | Description & Usage |
|---|---|---|---|
| **Gemma 3 1B IT** (`gemma3-1b-it-int4.litertlm`) | int4 | ~584 MB | **Primary Model.** Google's state-of-the-art compact instruction-tuned model. Generates empathetic, somatic reframing nudges and comprehensive 6-line dashboard analytics. |
| **Qwen 2.5 / 3 0.6B** (`qwen3_0_6b_mixed_int4.litertlm`) | mixed int4 | ~390 MB | **Ultra-Lightweight Model.** High-speed alternative for lower-memory devices with custom ChatML template support. |

*(The app also bundles 14 deterministic rule-based fallback templates and the `RuleInsights` heuristic engine when running in "No Model" mode).*

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
