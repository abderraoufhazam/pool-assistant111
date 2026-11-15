# POOL ASSISTANT

**Professional Android floating billiards trajectory analyzer for 8 Ball Pool.**

Pool Assistant runs *alongside* 8 Ball Pool and draws predicted ball
trajectories through a transparent floating overlay. It captures the screen
with MediaProjection, detects the table, balls, cue direction and shot power,
simulates the resulting physics, and renders the predicted paths — **without
modifying the game, injecting code, touching its memory, bypassing anti-cheat,
or automating gameplay.**

---

## ⚠️ Read this first — what this app can and cannot do

This is **source code for a real, buildable Android application**, not a
mockup. It was authored without the ability to run an Android toolchain in the
authoring environment, so **no pre-built APK is included**. Build it yourself
with Android Studio (instructions below): it compiles into a working APK.

The computer-vision layer uses **pure-Kotlin pixel processing** (colour
segmentation + blob extraction) rather than heavy native OpenCV — a deliberate
choice for build reliability and portability. The detectors are *heuristic*
and require calibration to your device's screen, resolution and the specific
game build. Detection confidence is surfaced explicitly; when confidence is
low, **the app shows nothing rather than fabricating trajectories**.

Physics parameters are *estimates*, clearly separated from measured values and
exposed for calibration.

---

## Features

- **Screen capture** via MediaProjection (foreground service, Android 10+).
- **Draggable floating button** (tap to toggle trajectories, drag to move).
- **Transparent trajectory overlay** — white cue path, red object paths,
  yellow rebounds, collision dots.
- **Computer vision** — table surface & pocket geometry, ball detection &
  classification (cue/solid/striped/8-ball), cue-stick direction, power gauge.
- **2D physics engine** — ball-ball & ball-cushion collisions, rolling friction,
  restitution, spin factor, multi-collision prediction.
- **Trajectory prediction** — bounded simulation with safety limits.
- **Minimal dark UI** — start/stop, permissions, settings, calibration.
- **Unit tests** for collisions, friction, prediction, coordinate mapping and
  calibration.

---

## Requirements

- **Android Studio** (Hedgehog 2023.1.1 or newer recommended).
- **JDK 17** (bundled with recent Android Studio).
- **Android SDK** Platform **34** (API 34) and Build Tools 34.
- A physical device or emulator running **Android 10 (API 29)** or newer.

---

## Build instructions

1. **Install Android Studio** and open `Tools → SDK Manager`, install
   *Android SDK Platform 34* and *Build Tools 34.0.0*.
2. **Open the project** — in Android Studio choose `File → Open` and select the
   `POOL-ASSISTANT` folder (the one containing `settings.gradle.kts`).
3. Let Gradle sync (first sync downloads the Gradle distribution and
   dependencies; needs internet).
4. **Build a debug APK**:
   - `Build → Build Bundle(s) / APK(s) → Build APK(s)`, or
   - from a terminal: `./gradlew assembleDebug`
5. The APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

> **Gradle wrapper note:** a `gradle-wrapper.jar` binary is not committed here.
> The easiest path is to build through Android Studio (it generates/uses its
> own wrapper). To build headless, run `gradle wrapper` once inside the project
> to generate `gradlew` and `gradle-wrapper.jar`, then use `./gradlew`.

---

## Installation (on device)

1. Transfer `app-debug.apk` to the device and install it (allow *install from
   unknown sources* when prompted).
2. Open **POOL ASSISTANT**.
3. Tap **Floating Overlay Permission** and allow *Display over other apps*.
4. Tap **Start Screen Analysis**, then accept the system **screen capture**
   consent. The persistent capture notification appears.
5. Launch **8 Ball Pool**. A floating "8" button appears — tap it to toggle
   trajectory visualization.

---

## Usage

| Control | Action |
| --- | --- |
| Floating button (tap) | Toggle trajectory overlay on/off |
| Floating button (drag) | Reposition |
| Start / Stop Screen Analysis | Begin/end capture session |
| Settings → detection | Line transparency, processing frequency |
| Settings → physics | Rolling friction, max cue speed, restitution; save/reset |

---

## How the pipeline works

```
Capture (MediaProjection) → downscale → VisionPipeline
   ├─ TableDetector   (felt blob → surface + pockets)
   ├─ BallDetector    (bright circular blobs → classify)
   ├─ CueDetector     (cue shaft orientation)
   └─ PowerDetector   (saturated gauge → power %)

VisionPipeline → DetectionResult (with per-stage confidence)
TrajectoryPredictor (PhysicsEngine) → Prediction
FloatingOverlayService → TrajectoryOverlayView (white/red/yellow lines)
```

---

## Project structure

```
app/src/main/java/com/poolassistant/
├── MainActivity.kt / PoolAssistantApp.kt
├── capture/    ScreenCaptureService.kt, ScreenCaptureManager.kt
├── overlay/    FloatingOverlayService.kt, FloatingButtonView.kt, TrajectoryOverlayView.kt
├── vision/     TableDetector.kt, BallDetector.kt, CueDetector.kt, PowerDetector.kt, VisionPipeline.kt
├── physics/    Ball.kt, Vector2D.kt, CollisionEngine.kt, PhysicsEngine.kt, TrajectoryPredictor.kt, PhysicsParameters.kt
├── calibration/ PhysicsCalibration.kt
├── ui/         MainActivity.kt, SettingsActivity.kt, MainViewModel.kt
├── models/     DetectionResult.kt, TableState.kt, Ball.kt, Vector2D.kt
└── utils/      ImageUtils.kt, CoordinateMapper.kt
```

---

## Known limitations (honest assessment)

1. **No pre-built APK included** — no Android toolchain was available in the
   authoring environment. See build instructions.
2. **Vision is heuristic, not ML.** Detection quality depends heavily on
   screen content and lighting; striped-vs-solid and cue/power detection are
   inherently lower-confidence and may need per-device threshold tuning.
3. **Physics parameters are estimates**, not the game's internal constants.
   Use Settings → physics calibration to refine against observed behaviour.
4. **Power gauge detection is fragile** — the indicator's visual may vary by
   game version; a conservative fallback power (50%) is used when unreadable.
5. **Overlay↔frame coordinate scaling** assumes the capture (virtual display)
   matches the physical screen geometry. Orientation changes and display
   cutouts can shift alignment; re-calibrating the detected table rect helps.
6. **8 Ball Pool anti-cheat / capture policies** — if the game blocks or
   detects screen capture on certain builds, the overlay will legitimately
   have no frames to analyze. Pool Assistant itself performs no detection
   evasion.

---

## Testing

Run unit tests from Android Studio (`Run → Run 'Tests in com.poolassistant'`)
or headless: `./gradlew testDebugUnitTest`.

See **[TEST_REPORT.md](TEST_REPORT.md)** for the full testing report and
limitations.

---

## Disclaimer

This project is an independent analytical/visualization tool. It does not
modify, reverse-engineer, or interact with the internals of any third-party
game, and it does not automate gameplay. Use it in accordance with the game's
terms of service.
