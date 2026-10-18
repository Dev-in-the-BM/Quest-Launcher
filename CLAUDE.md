# Quest Launcher (KaiOS-style Android launcher)

`quest.devinthebm.launcher` — minSdk 21, compileSdk/targetSdk 34, Kotlin, no Compose.

## Environment

- Android SDK root: `/Users/jc.sanchez/Library/Android/sdk`
- ADB: `/Users/jc.sanchez/Library/Android/sdk/platform-tools/adb`
- **JDK gotcha**: only JDK 25 is on PATH via Homebrew, but this Gradle/Kotlin
  version can't parse its 3-part version string (`25.0.3`) — even `./gradlew help`
  fails with `IllegalArgumentException: 25.0.3`. JDK 17 is installed at
  `/opt/homebrew/opt/openjdk@17`. Always prefix Gradle invocations:
  ```
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug -q
  ```
- Build output: `app/build/outputs/apk/debug/app-debug.apk`.

## Devices

- An emulator AVD named `Pixel_9_Pro_XL` exists. Boot it with:
  ```
  emulator -avd Pixel_9_Pro_XL -no-snapshot -no-boot-anim &
  ```
  then poll `adb -s emulator-5554 shell getprop sys.boot_completed` until `1`.
- A real physical device is often connected (serial varies, e.g.
  `H6HUGQSG45AUJBT4`) and may be **actively in use by the user** — don't drive
  it with synthetic `input keyevent`/`input tap` unless the user explicitly
  asks to test on their phone, since it can collide with their real input and
  navigate them away from whatever they were doing. Prefer the emulator for
  any exploratory or repro-driven UI testing.
- On the real device, a "Button Mapper" app may remap/intercept hardware key
  events, making synthetic `adb shell input keyevent` behave inconsistently
  (events silently dropped, double-firing, etc). If repro behavior looks
  contradictory or flaky on the real device, that's the likely cause — switch
  to the emulator for clean signal.
- `uiautomator dump` can capture a transitional frame for fast-changing focus
  state (stale or non-monotonic results). Prefer `exec-out screencap -p` with
  ~0.5-0.8s settle time after the triggering input when verifying focus/UI
  state, or cross-check with `dumpsys window | grep mCurrentFocus` for which
  Activity is foregrounded.

## Project structure notes

- `app/src/main/java/com/flipos/launcher/` — all activities live flat in the
  root package; `data/` for prefs/repositories, `ui/` for adapters/custom
  views, `service/` for the notification listener, `util/` for small
  extensions.
- `BaseListActivity` is shared scaffolding (title bar + RecyclerView +
  SoftKeyBar) for every vertical list screen (Options, Launcher Settings,
  Hide Apps, Shortcuts, App/Activity Picker, Notices). It also owns the
  accent-color theme-overlay-on-`onCreate` + recreate-on-resume-if-changed
  pattern — new list screens should extend it rather than reinventing this.
- `ListRowAdapter` + `Row` is the generic one-line-or-icon-row adapter reused
  across those list screens; `AppGridAdapter` is the App Drawer's icon grid;
  `NoticeRowAdapter` is the richer 3-line notice row.
- `LauncherPrefs` is the single SharedPreferences wrapper — all settings
  (icon size/shape/pack, accent color, drawer view mode, badges, shortcuts,
  hidden apps, per-app icon overrides) live there.
- `IconShapeRenderer` masks app icons into the user's chosen shape (adaptive
  icons composite their own fg/bg layers then get clipped; legacy icons get
  an optional synthesized tinted background disc).
- Bundled "built-in" icon PNGs the user drops in the repo-root `icons/`
  folder are **not** auto-discovered — copy them into
  `app/src/main/res/drawable-nodpi/` and add the filename (no extension) to
  `BuiltInIcons.NAMES` in `data/BuiltInIcons.kt`.
- `landing/` at the repo root is a separate, unrelated landing-page project
  (currently just an empty `index.html`) — not part of the Android app.
- No README screenshots exist yet; `README.md` has placeholder callouts
  (📸) marking where they should go.