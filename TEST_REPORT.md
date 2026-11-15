# TEST REPORT — Pool Assistant

Project: Pool Assistant (com.poolassistant)
Date: _authored offline; tests are to be executed in the build environment._
Scope: unit tests for physics, prediction, coordinate mapping and calibration.

---

## 1. Test environment

| Item | Value |
| --- | --- |
| Language | Kotlin 1.9.24 |
| Unit test framework | JUnit 4.13.2 |
| Coroutine test lib | kotlinx-coroutines-test 1.8.1 |
| Test runner | Gradle `testDebugUnitTest` |
| Instrumentation | androidx.test (Espresso) — optional, device only |

> The authoring environment had **no JDK, Gradle, or Android SDK**, so the
> tests below were authored and statically reviewed but **not executed here**.
> Execute them in Android Studio / Gradle; the assertions are deterministic and
> designed to pass on a correct build.

---

## 2. Test cases

### Vector2DTest
| # | Case | Expected |
| --- | --- | --- |
| 1 | addition/subtraction | exact component results |
| 2 | scalar * and / | exact |
| 3 | length | 3-4-5 → 5.0 |
| 4 | normalized | unit length |
| 5 | dot product | (1,2)·(3,4)=11 |
| 6 | reflect about normal | x flips, y preserved |
| 7 | rotate 90° | (1,0)→(0,1) |

### CollisionEngineTest
| # | Case | Expected |
| --- | --- | --- |
| 1 | head-on equal-mass elastic collision | cue ≈stops, target ≈5 m/s |
| 2 | overlap detection | finds 1 colliding pair |
| 3 | non-overlap | finds 0 pairs |
| 4 | cushion reflection (restitution 0.8) | vx: −3 → +2.4; normal (1,0) |
| 5 | pocket capture | ball inside pocket radius → potted |

### PhysicsEngineTest
| # | Case | Expected |
| --- | --- | --- |
| 1 | friction deceleration (1 m/s²) | 2 m/s → ~1.5 m/s in 0.5 s |
| 2 | stop below threshold | ball freezes, v=0 |
| 3 | cushion rebound event | emits CushionRebound |
| 4 | time advance | t += dt exactly |

### TrajectoryPredictorTest
| # | Case | Expected |
| --- | --- | --- |
| 1 | power→speed linear | 0→0, 50→3, 100→6 (max 6) |
| 2 | cue path + finals | >10 points; final positions present |
| 3 | zero direction | empty path, confidence 0 |
| 4 | impact produces collision point + object path | collision recorded, target path exists |

### CoordinateMapperTest
| # | Case | Expected |
| --- | --- | --- |
| 1 | screen↔world round-trip | identity within 1e-6 |
| 2 | center mapping | (500,250)→(1.0,0.5) |
| 3 | scaleX/scaleY | 2/1000 and 1/1000 |

### PhysicsCalibrationTest
| # | Case | Expected |
| --- | --- | --- |
| 1 | save→load round-trip | values preserved |
| 2 | defaults when empty | matches PhysicsParameters defaults |
| 3 | reset | restores defaults |

---

## 3. Test modes

- **Simulation mode (physics):** the predictor is fully deterministic and can
  be driven by any manually-entered ball positions (pure world-space vectors),
  enabling offline trajectory testing without a device.
- **Multiple-collision scenario:** covered by `cue hits target` (head-on) plus
  cushion-rebound event test; the engine supports iterative multi-contact
  relaxation over up to 3 iterations.

---

## 4. Known limitations & risks

1. **Vision detectors are not covered by automated unit tests** here because
   they require device bitmaps and, ideally, a reference frame set. They are
   exercised manually / via instrumentation on-device.
2. **MediaProjection / overlay services** cannot be unit-tested without an
   Android runtime; validate via manual device testing.
3. **Detection confidence thresholds** (`MIN_ACCEPTABLE_CONFIDENCE = 0.55`,
   `MIN_BALL_CONFIDENCE = 0.6`) are initial values; tune after on-device
   observation.
4. **Floating-overlay ↔ capture coordinate alignment** depends on display
   metrics matching; validate orientation and cutout behaviour on real
   devices.

---

## 5. Result summary (once executed)

| Suite | Cases | Result |
| --- | --- | --- |
| Vector2DTest | 7 | Pending |
| CollisionEngineTest | 5 | Pending |
| PhysicsEngineTest | 4 | Pending |
| TrajectoryPredictorTest | 4 | Pending |
| CoordinateMapperTest | 3 | Pending |
| PhysicsCalibrationTest | 3 | Pending |

Run `./gradlew testDebugUnitTest` and fill in results.
