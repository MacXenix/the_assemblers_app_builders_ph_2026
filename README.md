# SnapOut

**A rule notices you're stuck; on-device Gemma talks you out of it, personally, privately, offline.**

SnapOut is an Android app that detects mindless short-form-video scrolling (TikTok, Reels, Shorts…) from *how* you scroll, and interrupts at that moment with a four-stage circuit breaker. The message is written by Gemma running locally via LiteRT-LM. The app has **no INTERNET permission**.

> **New here? Start with [SETUP.md](SETUP.md)**: step-by-step install, copying the model, Accessibility, test checklist, what the score means, switching models, troubleshooting.

## How it works

| Layer | What it does | Code |
|---|---|---|
| Accessibility service | Receives only `TYPE_VIEW_SCROLLED`, `TYPE_VIEW_CLICKED`, `TYPE_WINDOW_STATE_CHANGED` (package name, timestamp, scroll delta). `canRetrieveWindowContent=false` — it cannot read the screen. | `service/SnapOutAccessibilityService.kt` |
| Trance score (deterministic) | 0–100 from swipe rate, dwell time, session length, tap ratio, darkness × late night, lying-down posture. ≥80 for 30 s → intervene. **The AI never decides when to intervene.** | `core/TranceScorer.kt` |
| Sensors | Ambient light + gravity, only while a feed is open | `service/SensorMonitor.kt` |
| Nudge | Heads-up notification (no screen block): stats title + a message the local model writes from the current parameters (swipe rate, seconds per video, taps, session length, time, posture, goal). Buttons: **Take me out** (goes Home) / **Snooze 15 min**. | `service/NudgeNotifier.kt` |
| Insights | 7-day analytics from the local DB (minutes/day, per app, seconds per video, taps per 100 swipes, late-night %, lying-down %, peak hour, nudge outcomes). **Analyze my scrolling** has the local model turn them into 3 insights + 3 tips; rule-based if no model. | `core/UsageSummary.kt`, `ui/InsightsScreen.kt` |
| Local AI | Gemma 3 1B (int4) on LiteRT-LM, GPU with CPU fallback. Warmed up when you start Drifting; built-in messages if the model is missing. | `ai/LlmManager.kt`, `ai/PromptBuilder.kt` |
| Storage | SQLite on device: feed sessions + interventions. Purge from Privacy tab. | `data/SnapOutDb.kt` |

## Build & install

Requirements: JDK 17, Android SDK 36.

```bash
./gradlew :app:assembleRelease          # signed with debug key for the hackathon
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Install the model (once)

1. Download **Gemma 3 1B IT int4** in `.litertlm` format from the LiteRT Community on Hugging Face
   (`litert-community/Gemma3-1B-IT`, file `gemma3-1b-it-int4.litertlm`, 584 MB). Accept the Gemma license first.
   No-login alternative: `litert-community/Qwen3-0.6B`, file `qwen3_0_6b_mixed_int4.litertlm`. More options in [SETUP.md §9](SETUP.md#9-changing-the-model).
2. Push it to the app's files dir (any `*.litertlm` filename works):

```bash
adb shell mkdir -p /sdcard/Android/data/com.assemblers.snapout/files
adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/com.assemblers.snapout/files/
```

(`/data/local/tmp/llm/` is also scanned.) Open SnapOut → Home → **On-device AI** should show the model; tap **Load model now** to verify.

## Enable

Home → **Open Accessibility settings** → *SnapOut scroll detection* → On. (On Android 13+ sideloaded apps may need *App info → ⋮ → Allow restricted settings* first.)

## Demo script

1. Turn on **airplane mode**.
2. Home → enable **Demo mode** (low thresholds, 5 s trigger) and optionally **Pretend it's late & dark**.
3. Load the model, open TikTok/Shorts, swipe quickly for ~1 min → a nudge notification pops up by itself.
   Backup: **Send test nudge now** button.
4. The notification's subtext shows which model wrote it and how fast. Insights tab → **Analyze my scrolling** (use **Load sample week** on a fresh install) → **What the model sees** shows the exact numbers it got.
5. Privacy tab → “✓ No network permission” + purge. Proof from a laptop:
   `$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer manifest permissions app-release.apk`

The **Raw events** line on Home shows whether the feed app is actually sending scroll events — check this first on the demo phone.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```
