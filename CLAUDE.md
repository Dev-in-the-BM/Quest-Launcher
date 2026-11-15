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

- `app/src/main/java/quest/devinthebm/launcher/` — activities live in the
  root package; `data/` for prefs/repositories, `ui/` for adapters/custom
  views, `service/` for the notification listener, `util/` for small
  extensions, and `theme/` for the theme catalog/lifecycle.
- `BaseListActivity` is shared scaffolding (title bar + RecyclerView +
  SoftKeyBar) for every vertical list screen (Options, Settings Categories,
  Hide Apps, Shortcuts, App/Activity Picker, Notices). It also owns the
  accent-color theme-overlay-on-`onCreate` + recreate-on-resume-if-changed
  pattern — new list screens should extend it rather than reinventing this.
- `ListRowAdapter` + `Row` is the generic one-line-or-icon-row adapter reused
  across those list screens; `AppGridAdapter` is the App Drawer's icon grid;
  `NoticeRowAdapter` is the richer 3-line notice row.
- `LauncherPrefs` is the single SharedPreferences wrapper — all settings
  (icon size/shape/pack, accent color, drawer view mode, badges, shortcuts,
  hidden apps, per-app icon overrides, and the selected launcher theme) live
  there. Theme selection is stored under `launcher_theme_id`.
- `LauncherThemeCatalog` defines the stable `kaios` and `material2` IDs;
  KaiOS is the fallback for missing or unknown IDs. `ThemedActivity` applies
  the selected style before view inflation and refreshes stale activities when
  the selection changes. Palette and focus resources have `values/` and
  `values-night/` variants; keep day/night style definitions shared so night
  resources don't replace the complete theme.
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

## Current UI task history and status (Nov 10, 2026)

This repository is being worked on in the Linux workspace
`/home/naftali/Tinkering/Projects/Quest Launcher`; the old macOS SDK/JDK notes
above describe a different environment and are not valid build instructions
for this session. The current host's Gradle wrapper can build with its installed
Java runtime using `./gradlew assembleDebug`.

### Scope requested

The user requested a follow-up to the Home/Yapchik and app-drawer work:

- Options on the left softkey, Search on the right; popup aligned from the left
  Yapchik label and styled to the active KaiOS or Material 2 theme.
- Remove Open, Change Icon, and Reset Icon from the focused-app popup; include
  App Info.
- Show the letter selected by T9 initial-jump navigation as a temporary overlay.
- Keep Yapchik's Home bar transparent without compromising the grid spotlight.
- Also correct reports that the favorites wheel intrudes into the bar, the grid
  icon duplicates across theme changes, Material focus is too large, and
  opening search pushes the grid down.

### Implementation details

- `AppDrawerActivity.kt` swaps softkey actions and anchors the focused-app
  popup to Yapchik's left label (falling back to the grid if no bar is attached).
  The popup list contains Add to Home, Hide App, App Info, icon-wrap toggle and
  Uninstall; Open, Change Icon and Reset Icon are absent.
- The popup now uses `androidx.appcompat.widget.PopupMenu` rather than the
  framework widget, so AppCompat theme attributes are honored. The KaiOS theme
  selects `Widget.QuestLauncher.KaiOS.PopupMenu` and a dropdown list selector;
  its resources are `kai_popup_background.xml` (dark surface with accent
  outline) and `kai_popup_focus.xml` (accent keyboard/press state). Material 2
  keeps its existing `Widget.QuestLauncher.Material2.PopupMenu` and elevated
  surface background. The KaiOS popup context is explicitly wrapped with
  `Theme.QuestLauncher`.
- Material 2's moving grid focus highlight uses
  `m2_grid_focus_inset.xml`; its measured position and size are inset by 4dp.
  KaiOS retains its current spotlight/focus drawing.
- `showT9Letter()` adds a centered themed letter overlay over `drawer_content`;
  it fades in, remains for 2.2 seconds, then fades out over 220ms. It is invoked
  for the actual T9 initial-jump letter whether or not a matching app exists.
- `activity_app_drawer.xml` places the search `EditText` as a top overlay inside
  the grid `FrameLayout`, leaving the weighted grid region's dimensions stable.
- `MainActivity.kt` keeps the Yapchik background transparent, tags and removes
  duplicate app-grid controls when reconciling softkey rows, and reserves a
  bottom margin equal to the measured softkey bar height on the favorites rail.
  The `SpotlightDrawable` assignment remains unchanged.
- `GOALS.md`, `PLAN.md`, and `PROGRESS.md` carry the concise requested scope,
  approach and current state. `PROGRESS.md` is authoritative for remaining
  verification: the latest build passed and is installed on TT290. The phone's
  foreground app and awake state were checked before and after install and left
  unchanged. A visual screenshot pass for both themes has not yet been done.

### Verification / device handling

- `./gradlew assembleDebug` passed after the UI changes.
- Latest check: TT290 serial `MTK0002608041913160` and Gflip serial `7d50442` are
  online. TT290 was awake with `dev.alenajam.opendialer/.ui.MainActivity`
  foreground. Installed the latest APK and confirmed the same foreground app
  and awake state afterward; no restoration was needed. Do not use synthetic
  taps/keys without explicit permission.
  For visual verification, capture after transitions settle and restore the
  prior activity/sleep state.
- Next remaining task: verify the actual rendered popup, focus inset, T9
  overlay, wheel/bar boundary, and search overlay on TT290 under both KaiOS and
  Material 2 preferences. The latest APK is installed. Record concrete results
  in `PROGRESS.md` and add the full story here in this file.

## User-reported visual corrections (Nov 10, 2026)

The user rejected the current T9 and popup rendering and explicitly stated the
Material 2 popup focus was not inset. Treat these as open work even though the
earlier task summary described the popup as themed and the app-grid focus as
inset. In particular, the prior `m2_grid_focus_inset.xml` change affected the
app icon highlight only; it did **not** satisfy inset popup-row focus.

### Evidence / design constraints

- `docs/theming.md` says Material 2 focus is a neutral-grey fill behind the
  selected control/app icon, a stronger shade for pressed state, no focus frame,
  and no recoloring app-owned icons.
- `app/src/main/res/drawable/m2_row_focus.xml` implements the settings/list
  focus states: `m2_settings_focus_fill` for focused, `m2_focus_fill` for
  pressed/selected, with 2dp corners.
- KaiOS settings selection in `bg_item_focus.xml` uses 18dp rounded corners.
  Existing KaiOS popup resources used only 4dp outer / 3dp inner radii, which
  explains the visual mismatch.
- The existing T9 feedback in `AppDrawerActivity.showT9Letter()` used a 72dp
  square centered in the frame; user wants it substantially larger, horizontally
  centered and vertically near the bottom (above Yapchik's bar).

### Correction pass implementation (build and install passed; visual verification pending)

- T9 overlay was initially 144dp square with 76sp letter, bottom/center-horizontal
  gravity, and bottom margin equal to actual overlap between drawer content and
  Yapchik's measured bar plus 18dp. This handles both auto-inset and overlay
  layouts without lifting the letter unnecessarily.
  It uses selected state so the theme-specific focus drawable paints; M2 text is
  on-surface and KaiOS text uses on-accent. Repeated T9 keys remove the prior
  overlay before adding the next, avoiding stacked badges. Fade/hold remains
  roughly 2.4 seconds total.
- KaiOS popup surface radius increased from 4dp to 18dp, matching the settings
  selection shape in `bg_item_focus.xml`; focused/pressed popup rows use 14dp
  corners to sit comfortably within that rounded surface.
- Material 2 now has `Widget.QuestLauncher.Material2.PopupList`, registered via
  the theme's AppCompat `dropDownListViewStyle`. Its `android:listSelector` is
  `m2_popup_focus_inset.xml`: 8dp horizontal and 3dp vertical inset around each
  row; neutral `m2_settings_focus_fill` when focused/activated/selected, stronger
  neutral `m2_focus_fill` when pressed, 2dp corners, and no outline. This is the
  popup-row selector, separate from the grid highlight selector.
- `./gradlew assembleDebug` and `git diff --check` passed after the correction pass.
  On the next fresh device check, TT290 serial `MTK0002608041913160` was online.
  It was awake with `dev.alenajam.opendialer/.ui.MainActivity` foreground. Installed
  `app/build/outputs/apk/debug/app-debug.apk` successfully using `adb install -r`;
  `pm path quest.devinthebm.launcher` confirmed the app path. The foreground app
  remained unchanged after install, so no restore action was needed. Visual
  inspection of the latest T9/popup corrections is still outstanding; do not
  claim it without captures.
- Latest user refinement: reduce the badge modestly to 128dp square with 68sp
  text, preserving its bottom-center placement and timing. `assembleDebug` and
  `git diff --check` pass; this revised APK is installed on TT290, with the
  previous foreground app and awake state preserved.
- Latest user correction: the 128dp badge still appeared twice the desired size,
  so halve it to 64dp with 34sp text while preserving position/timing. Removed
  the KaiOS popup background's 1dp accent stroke; retained its 18dp corners and
  existing focus behavior. `assembleDebug` and `git diff --check` passed, then
  the APK installed successfully on TT290 serial `MTK0002608041913160`.
  `pm path` confirms installation. The phone was awake with
  `dev.alenajam.opendialer/.ui.DialerActivity` foreground before and after, so
  no restore was needed. Popup/T9 visual verification remains pending.

### Required implementation and remaining checks

- Increase the T9 badge substantially (large legible letter and larger surface),
  anchor `Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL`, reserve the measured
  softkey-bar height plus spacing, and remove any prior transient badge before
  adding a new one so rapid T9 multi-taps never stack overlays. Retain the
  transient ~2.4s display unless user changes that duration.
- Increase KaiOS popup surface corner radius to match the 18dp settings visual
  language and round its focused item correspondingly; retain the KaiOS dark
  surface/accent color behavior.
- Add a Material 2 AppCompat dropdown-list style and use an `InsetDrawable`
  selector on popup rows (not the grid-focus overlay). Inset selected/focused
  background about 8dp left/right and 3dp top/bottom; focused/selected fill is
  `m2_settings_focus_fill`, pressed fill is `m2_focus_fill`, both 2dp corners,
  no outline/frame. Confirm selector is actually wired into AppCompat's
  `dropDownListViewStyle` used by `PopupMenu`.
- Update `GOALS.md`, `PLAN.md`, `PROGRESS.md` while work proceeds; `PROGRESS.md`
  must distinguish implementation/build/install/visual verification. Rebuild,
  check `git diff --check`, and inspect TT290 connection + foreground/wake state
  before installation. Don't claim visual verification without captures.
