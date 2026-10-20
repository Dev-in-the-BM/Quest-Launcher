## Quest Launcher UI context

- Android/Kotlin application under `app/src/main/java/quest/devinthebm/launcher/`.
- Home rail and Yapchik integration are in `MainActivity.kt`; rail layout is `app/src/main/res/layout/activity_main.xml`.
- GitHub release `v1.0.0-alpha.4` is the latest tagged release. Its `updateWheelCurve()` uses a fixed `-9dp` closed offset; the earlier pronounced crescent formula is in parent of `a782350` (pre-carousel), `-9 + 18 * (1 - (distance / 2)^2)` closed and `16 * (1 - (distance / 2)^2)` open.
- Yapchik 1.0.2 `SoftkeyBar` is a vertical `LinearLayout`: divider at child 0, horizontal label row at child 1, weighted LEFT and RIGHT labels in that row. It exposes no center-slot API; add the app-grid view at index 1 in the row when the bar is present.
- Do not overwrite existing app drawer changes (`AppDrawerActivity.kt`, `activity_app_drawer.xml`); they predate this task.
- Before claiming on-device verification is unavailable, run `adb devices -l`: a phone may be attached over wireless ADB (currently a TT290 / DOOV). When a device is online, install the debug APK and verify UI/build changes there; use `adb -s <serial>` if multiple devices are listed. Check the foreground activity and wakefulness first, avoid disrupting unrelated user activity, and restore the previous foreground/sleep state after testing. Do not assume that a previous empty device list still applies.
