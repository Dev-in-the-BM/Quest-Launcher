# Quest Launcher for keypad Phones

The launcher for your Android keypad phone.
So far a little UI polish added to the original KaiOS launcher it's based off of.
Hopefully more coming if I don't lose patience.

https://github.com/user-attachments/assets/2e2fe6a0-2790-4637-b332-ae51f3214e90


> [!NOTE]
> The rest of this README is outdated.

<details>
  <summary>Some relevant info here, some outdated pics and descriptions </summary>
A KaiOS-style Android launcher built for feature-phone-shaped devices (small,
low-resolution, D-pad/soft-key driven screens) rather than touch-first
flagships. Navigation leans on the physical soft keys and D-pad: number keys
1-9 double as shortcuts everywhere they make sense, and every screen mirrors
the three-soft-key layout (left / center / right) KaiOS users already know.

![Home Screen](docs/home.png)

## Features

### Home screen

- A vertical rail of up to 9 shortcut icons on the left
- A large clock and date on the right.
- Bottom soft keys: **Notices** (left), **All Apps** (center, tap; long-press
  for **Options**), and a configurable right key (Contacts by default).
- A notification summary (missed calls / messages / other) under the clock,
  each independently toggleable.
- Typing a digit anywhere on Home jumps straight into the dialer, prefilled.
- Long-pressing Back launches a configurable app (Claude, etc.).

![Home Screen](docs/home.png)

### App Drawer ("All Apps")

- Every installed, non-hidden app, in **grid** or **list** view (toggle in
  Options → App Drawer):
  - **Grid**: a 3x3 icon page at a time, tracked by a column of dots on the right.
    Number keys 1-9 launch the matching icon on the current page.
    Icon sizing and page boundaries remain stable while search or transient
    window changes alter the available height; D-pad navigation can focus
    off-screen cells without losing the selected app.
  - **List**: one continuous scroll, icon + label per row — no pages, no
    dots, just normal scrolling.
- Long-press (or the Options soft key) on any app to:
  - Add it to Home shortcuts
  - Hide it from the drawer
  - Change its icon (from an installed icon pack or the launcher's own
    bundled icon set)
  - Reset a per-app icon override
  - Uninstall it

![App Drawer Grid](docs/apps.png)
![App Drawer List](docs/list.png)

### Notices

A custom notification list standing in for the system shade, which isn't
designed for a screen this small. Shows icon, title, body text and a relative
timestamp ("2 minutes ago", "8:30 AM", …) per notification.

- **Dismiss** (left soft key) — dismiss the focused notice
- **Select** (center) — open the notice's action, then dismiss it
- **Dismiss All** (right) — clear every active notification

Requires Notification Access, granted from Options → Notifications.

![Notices](docs/notices.png)

### Options menu

Reached from Home's center soft key (long-press) or the App Drawer's Options
key:

- All Apps
- Home & Navigation
- App Drawer
- Appearance
- Notifications
- Customize Home Shortcuts
- Hide / Show Apps
- Set Wallpaper
- Set as Default Launcher

### Settings submenus

- **Home & Navigation** — customize the Home left and right keys and choose an
  app for Back-button long-press.
- **App Drawer** — choose icon size, Grid or List layout, and keypad behavior
  (Search, T9 App Jump, or Grid Position).
- **Appearance** — choose an installed icon pack, accent color (cyan, red,
  orange, yellow, green, blue, indigo, violet), and theme (KaiOS, Material 2,
  or Aeris).
  Configure icon shape (squircle, square, circle, rounded square, or none) and
  the background for plain icons here too. Material 2 follows the device's
  day/night mode.
- **Notifications** — grant/check Notification Access, toggle missed-call,
  message, and other-notification badges, and control notification dots on
  app icons.

![Settings](docs/iconpack.png)
![Icon Shape](docs/iconshape.png)
### Home Shortcuts

Customize the ordered list of up to 9 Home rail shortcuts. Pick a shortcut
row (or its number key) to reassign it, including pinning a specific
*activity* inside an app rather than just its main entry point. The trailing
"Add shortcut" row appends a new one; the Clear soft key removes the focused
shortcut.

Shortcut rows resolve their theme styling with Android's `TypedArray` lifecycle
API, avoiding a launch-time crash when favorites are bound on older Android
versions.

### Hide / Show Apps

Lists every installed app with a "Hidden" badge; Center/OK toggles whether
the focused app is hidden from Home and the App Drawer.

### Icon packs & built-in icons

- Apply any installed icon-pack app launcher-wide from Options → Appearance.
- Override a single app's icon independently of the active pack, choosing
  from that pack's full icon set or from the launcher's own bundled icon
  collection — so there's always something to pick from even with no icon
  pack installed.

## Requirements

- Android 5.0 (API 21) or newer
- Kotlin / AGP toolchain — see `build.gradle.kts`

## Building

```bash
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Installing

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then set Quest Launcher as your default Home app (Options → Set as Default
Launcher), and grant Notification Access from Options → Notifications if you
want the Notices screen and Home notification badges to work.

## Permissions

- `BIND_NOTIFICATION_LISTENER_SERVICE` (via Notification Access, granted by
  the user in system settings) — powers Notices and the Home badge summary.

No other runtime permissions are required. App enumeration uses a `<queries>`
declaration rather than `QUERY_ALL_PACKAGES`.
</details>
