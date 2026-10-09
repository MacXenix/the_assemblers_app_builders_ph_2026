# SnapOut — Setup & Testing Guide

This guide takes you from nothing to a working demo on an Android phone (Android 12 or newer). It's written for Windows; Mac/Linux commands are the same apart from the paths.

- [1. What you need](#1-what-you-need)
- [2. Get the app](#2-get-the-app)
- [3. Connect the phone to your PC (adb)](#3-connect-the-phone-to-your-pc-adb)
- [4. Download a model](#4-download-a-model)
- [5. Copy the model to the phone](#5-copy-the-model-to-the-phone)
- [6. Turn on scroll detection (Accessibility)](#6-turn-on-scroll-detection-accessibility)
- [7. Test checklist](#7-test-checklist)
- [8. Understanding the score](#8-understanding-the-score)
- [9. Changing the model](#9-changing-the-model)
- [10. Where the files are](#10-where-the-files-are)
- [11. Troubleshooting](#11-troubleshooting)
- [12. Other ways to use the local model](#12-other-ways-to-use-the-local-model)

---

## 1. What you need

| | Needed for |
|---|---|
| Android phone, Android 12+ (8 GB RAM recommended) | everything |
| USB data cable (not charge-only) | copying the model |
| Windows/Mac/Linux PC | copying the model, building from source |
| Hugging Face account (free) | Gemma models only; some models need no account |

The app works **without a model**. It then shows built-in messages (labelled "Built-in message" in the pop-up). The model only makes the messages personal.

## 2. Get the app

**Option A: download the APK.** Use the APK shared by the team, or build it (Option B). Copy `app-release.apk` to the phone and tap it in the Files app. If Android blocks it: **Settings → Allow from this source**, then **Install**. If Play Protect warns you, tap **Install anyway**; it warns because the app isn't from the Play Store.

**Option B: build from source** (needs JDK 17 and Android SDK 36, or just Android Studio):

```powershell
git clone https://github.com/MacXenix/the_assemblers_app_builders_ph_2026
cd the_assemblers_app_builders_ph_2026
.\gradlew.bat :app:assembleRelease
# APK: app\build\outputs\apk\release\app-release.apk
```

In Android Studio: **File → Open** the folder, plug in the phone, press **Run ▶**.

## 3. Connect the phone to your PC (adb)

`adb` is Google's command-line tool for talking to an Android phone over USB. You need it once, to copy the model.

1. **Get adb:** download *SDK Platform-Tools* from <https://developer.android.com/tools/releases/platform-tools> and unzip it. If you have Android Studio, it's already in `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
2. **Enable USB debugging on the phone:**
   - Settings → About phone → tap **Build number** 7 times. On Xiaomi it's *MIUI/HyperOS version*; on Samsung it's under *Software information*.
   - Settings → **Developer options** → **USB debugging** → On.
3. **Plug in the phone.** Unlock it and tap **Allow** on "Allow USB debugging?" (tick *Always allow*).
4. Open PowerShell **inside the platform-tools folder** (click the folder's address bar, type `powershell`, press Enter) and run:

```powershell
.\adb devices
```

You should see a line ending in `device`. If you see `unauthorized`, accept the prompt on the phone. If nothing appears, see [Troubleshooting](#11-troubleshooting).

**No cable? Use Wireless debugging (Android 11+, same Wi-Fi):**
1. Developer options → **Wireless debugging** → On → **Pair device with pairing code**.
2. Run `.\adb pair IP:PORT` with the IP and port from the pairing popup, then type the code.
3. Run `.\adb connect IP:PORT` with the IP and port from the main Wireless debugging screen (a different port).

## 4. Download a model

Any `.litertlm` file works. See [§9](#9-changing-the-model) for the full list. The default:

**Gemma 3 1B (recommended, 584 MB)**
1. Log in at <https://huggingface.co>.
2. Open <https://huggingface.co/litert-community/Gemma3-1B-IT> and accept the Gemma license. Access is granted immediately.
3. Download **`gemma3-1b-it-int4.litertlm`**: <https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm>
   Don't use the files with chip names (`sm8650`, `mt6991`, `Tensor_G5`…); those only work on specific phones.

**No account? Qwen3 0.6B (498 MB, no login):**
<https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm>

## 5. Copy the model to the phone

The model must go in SnapOut's own folder on the phone's internal storage:

```
Internal storage/Android/data/com.assemblers.snapout/files/
```

On Android, `/sdcard` means **internal storage**, so you don't need a memory card. Open SnapOut once first so Android creates the folder.

**With adb** (put the model in the platform-tools folder first):

```powershell
.\adb shell mkdir -p /sdcard/Android/data/com.assemblers.snapout/files
.\adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/com.assemblers.snapout/files/
.\adb shell ls -l /sdcard/Android/data/com.assemblers.snapout/files/
```

**Without adb (works on some phones):** plug in, switch USB mode to **File transfer**, then in File Explorer go to *This PC → (phone) → Internal storage → Android → data → com.assemblers.snapout → files* and drag the file in. If `Android/data` is empty or hidden, your phone blocks this; use adb.

File manager apps *on the phone* usually can't open `Android/data` on Android 11+. That's expected.

Then in SnapOut go to **Home → On-device AI → Rescan** (or reopen the app) and tap **Load model now**. You should see something like:
`gemma3-1b-it-int4.litertlm · GPU · loaded in 2400 ms`.

## 6. Turn on scroll detection (Accessibility)

SnapOut needs Accessibility permission. It's the only way Android lets an app notice scrolling inside TikTok/YouTube and draw on top of them. It is configured with `canRetrieveWindowContent="false"`, so it **cannot read what's on screen**.

1. SnapOut → Home → **Open Accessibility settings**.
2. **Installed apps** (or *Downloaded apps*) → **SnapOut scroll detection** → On → Allow.
3. If the switch is greyed out or says *Restricted setting* (Android 13+ sideloaded apps):
   Settings → Apps → SnapOut → **⋮** (top right) → **Allow restricted settings**, then repeat step 2.
4. Back in SnapOut, the Protection card should say **Service running**.

**Shortcut with adb** (skips all of the above, including restricted settings):

```powershell
.\adb shell settings put secure enabled_accessibility_services com.assemblers.snapout/com.assemblers.snapout.service.SnapOutAccessibilityService
.\adb shell settings put secure accessibility_enabled 1
```

This replaces any other accessibility services you had enabled.

## 7. Test checklist

| # | Do | Expect |
|---|---|---|
| 1 | Save a goal, e.g. *be asleep by midnight*. Later: **Edit goal** | Goal shown in the "Your goal" card |
| 2 | Home → On-device AI → **Load model now** | Model name · GPU/CPU · load time |
| 3 | Turn on **Demo mode**, tap **Trigger intervention now** | Pop-up: stats → message → 10 s breathing → buttons |
| 4 | Tap the grey AI panel in the pop-up | Exact context the model saw (time, app, minutes, goal, angle…) |
| 5 | Trigger 3 times | Different messages each time; tone gets firmer |
| 6 | **I'm done — take me out** | Goes to the home screen |
| 7 | Trigger again, hold **Hold 3.0s to keep scrolling** | Must hold the full time; letting go early resets it |
| 8 | Open TikTok/YouTube/Instagram, swipe every 2–3 s for ~1 min (Demo mode on) | Score on Home rises; pop-up appears by itself |
| 9 | History tab | Each pop-up: message, source (model or built-in), outcome |
| 10 | Airplane mode on, repeat 3 & 8 | Still works, because everything is on-device |
| 11 | Privacy tab | "✓ No network permission"; **Purge** empties History |

Without Demo mode the real thresholds apply: score ≥ 80 held for 30 s, then a 10-minute cooldown.

## 8. Understanding the score

The score (0–100) is a fixed formula, not AI. The AI only writes the message. On Home, tap any row under **Why this score** to see its explanation.

| Row | Points | 0 when… | Full when… |
|---|---|---|---|
| Swipe speed | 25 | ≤ 6 swipes/min (demo: 2) | ≥ 20 swipes/min (demo: 10) |
| Short views | 20 | ~20 s per video | ≤ 4 s per video |
| Session length | 20 | just opened | 20 min in one feed (demo: 1 min) |
| Few taps | 10 | you tap (like/comment/open) on ≥ 30% of swipes | ≤ 5% (passive watching) |
| Dark + late | 15 | bright room, daytime | light < 10 lux **and** 22:00–05:00 (one of the two = half) |
| Lying down | 10 | phone held upright | phone held flat/overhead, like in bed |

- **0–49 Focused · 50–79 Drifting** (the model starts loading in the background) **· 80+ Zombie-scrolling** (held for 30 s, the pop-up appears).
- **Demo mode** ignores Dark + late and Lying down, and scales the four behaviour rows up to 100, so it can trigger in a bright room during the day.
- **Pretend it's late & dark** forces Dark + late and Lying down to full (normal mode only).
- **Raw events** (bottom of Home) is for debugging: how many scroll events arrived in total and from feed apps, plus the last one (`dy` = scroll distance, `item` = which list position is on screen).

**Which apps count:** TikTok, Instagram, YouTube, X, Facebook, Reddit (apps only, not their websites in Chrome). The app list is in `app/src/main/java/com/assemblers/snapout/core/FeedApps.kt`.

## 9. Changing the model

1. Copy one or more `.litertlm` files into the model folder ([§5](#5-copy-the-model-to-the-phone)).
2. Home → On-device AI → **Rescan**. With more than one model present, a list appears; tap one to select it.
3. Tap **Load model now**.

If none is selected, SnapOut uses the smallest model in the folder.

| Model | File | Size | Login? | Notes |
|---|---|---|---|---|
| **Gemma 3 1B** (default) | [`gemma3-1b-it-int4.litertlm`](https://huggingface.co/litert-community/Gemma3-1B-IT) | 584 MB | Yes (accept license) | Best balance for 8 GB phones |
| Gemma 3 270M | [`gemma3-270m-it-q8.litertlm`](https://huggingface.co/litert-community/gemma-3-270m-it) | 304 MB | Yes | Fastest, noticeably weaker writing |
| Qwen3 0.6B | [`qwen3_0_6b_mixed_int4.litertlm`](https://huggingface.co/litert-community/Qwen3-0.6B) | 498 MB | No | Good no-login option; SnapOut strips its `<think>` text |
| Qwen2.5 1.5B | [`Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct) | 1.6 GB | No | Better writing, slower load |
| Gemma 4 E2B | [`gemma-4-E2B-it.litertlm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) | 2.6 GB | No | Best quality; slow to load, flagship phones only |

Use the generic file from each repo, not the ones named after a chip.

**Tested so far:** Qwen3 0.6B generates on-topic messages in SnapOut (Android emulator, CPU: ~25 s to first word there, so a real phone will be much faster). Gemma 3 1B is the intended default but has not been run in SnapOut yet, because its download needs a Hugging Face login. The other models use the same runtime (LiteRT-LM) but are untested. If the GPU fails while generating (e.g. no OpenCL, which is common on emulators), SnapOut switches to CPU automatically.

**To change the prompt or behaviour of the AI:** `app/src/main/java/com/assemblers/snapout/ai/PromptBuilder.kt` (system prompt, "angles" picked at random per message, output filter). The built-in messages used when no model is loaded are in `ai/FallbackTemplates.kt`.

## 10. Where the files are

| What | Location |
|---|---|
| Models | `/sdcard/Android/data/com.assemblers.snapout/files/` (also scanned: `/data/local/tmp/llm/`) |
| Database (sessions, interventions) | `/data/data/com.assemblers.snapout/databases/snapout.db` (private; use the History tab, or `adb shell run-as com.assemblers.snapout ls databases` on debug builds) |
| Settings | `/data/data/com.assemblers.snapout/shared_prefs/snapout.xml` (private) |

Useful adb commands:

```powershell
.\adb shell ls -l /sdcard/Android/data/com.assemblers.snapout/files/     # list models
.\adb shell rm /sdcard/Android/data/com.assemblers.snapout/files/NAME     # delete a model
.\adb logcat -s SnapOutLlm                                               # model loading/generation logs
.\adb shell pm clear com.assemblers.snapout                              # reset app (also deletes the model folder)
```

## 11. Troubleshooting

| Problem | Fix |
|---|---|
| `adb: no devices/emulators found` | Unlock the phone and accept the USB debugging prompt; set USB mode to *File transfer*; try another cable/port; `.\adb kill-server` then `.\adb devices`; on Windows install your brand's USB driver (Samsung USB Driver, or Google USB Driver: <https://developer.android.com/studio/run/win-usb>). Or use Wireless debugging (§3). |
| `adb` not recognized | Run it from the platform-tools folder as `.\adb` |
| "No model found" | File isn't in the right folder or doesn't end in `.litertlm`. Check with `adb shell ls` (§10), then tap **Rescan**. |
| Model "Failed" to load | Try a smaller model; close other apps; check `.\adb logcat -s SnapOutLlm`. |
| Accessibility switch greyed out | Allow restricted settings (§6), or use the adb shortcut. |
| Service turns itself off | Battery optimisation killed it. Settings → Apps → SnapOut → Battery → **Unrestricted**. |
| Pop-up never appears by itself | Check that Protection is on and **Service running**. Then scroll in the app and watch **Raw events** on Home: if *feed apps* stays 0, that app isn't sending scroll events on your phone. Note the app and phone model and report it. Use **Trigger intervention now** for the demo meanwhile. |
| YouTube score stays low | YouTube sends fewer scroll events than TikTok. Swipe Shorts every 2–3 s with Demo mode on. Check that the *feed apps* counter and the `item=` value in **last scroll** change as you swipe. |
| Messages look the same | Check the label in the pop-up's AI panel. "Built-in message" means the model isn't loaded (step 2 of §7). |

## 12. Other ways to use the local model

The same on-device model, with the same privacy guarantees, could also do the following. These are ideas, not implemented.

| Idea | What the model does | Effort |
|---|---|---|
| **"What were you looking for?" reply** | Overlay offers quick answers (Bored / Can't sleep / Avoiding something / typed reason); the model replies to *that* reason with one tiny next step | Small: one more prompt + buttons |
| **Morning recap card** | Summarises last night from the local DB: "You drifted ~10 min after opening TikTok in bed, 3 nights running" | Small: query DB → prompt |
| **Weekly pattern insights** | Finds your riskiest time/app/context and suggests one rule ("no TikTok after 23:00?") | Medium |
| **Goal coach at onboarding** | Turns a vague goal ("sleep better") into a concrete one ("phone down by 23:30") | Small |
| **Personal "why" memory** | Remembers the reasons you gave and reuses them later ("Last time you said you were avoiding the essay…") | Medium |
| **Voice check-in** | On-device speech → model → spoken reply during the breathing pause (Gemma 3n / Gemma 4 accept audio) | Medium–large |
| **Adaptive thresholds** | The model suggests (and the user approves) tuning of their own score thresholds from history. The trigger stays rule-based | Medium |
| **Journaling prompt after exit** | When you tap "take me out", offers one reflective question to answer in a private note | Small |

Keep the rule: **the score decides *when* to step in, and the model only decides *what to say***. That keeps the app predictable and within Google Play's Accessibility policy.
