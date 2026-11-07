# Levitate

<img src="linux/assets/levitate.svg" width="88" alt="Levitate icon">

An Android air mouse for Linux. Aim your phone to move the pointer, roll it to scroll or switch workspaces, and click with tactile on-screen buttons.

Native **Material 3 Android Views** and a small **Python/GTK companion**. Local Wi-Fi or a phone hotspot; encrypted QR pairing; no account or cloud.

**[Download v1.0.0](https://github.com/uzairdeveloper223/Levitate/releases/tag/v1.0.0)** · [Install and release notes](docs/RELEASE-1.0.0.md)

## Controls

| Hold / action | Result |
| --- | --- |
| Screen facing up, top toward the laptop | Air-mouse pointer |
| Left edge rolled upward, then sweep left/right | Previous / next workspace |
| Right edge rolled upward, then tilt the top up/down | Scroll |
| Left / right click buttons | Mouse buttons, with vibration |
| Hold Left click while moving | Drag |
| Recenter | Reset your comfortable orientation |
| Pause | Stop motion and release held buttons |
| Touchpad | Finger movement, tap to click, two-finger scrolling |

Automatic mode switching has a dwell time and hysteresis. Manual Point, Scroll and Spaces modes are also available. Settings include sensitivity, natural scrolling, haptics, screen-on behaviour, and a 30 Hz battery-saver mode. Middle click and media controls are included.

Sensors measure **rotation**, not the phone’s position relative to the laptop. Levitate uses the top-edge direction from the fused rotation matrix; it does not pretend to locate a physical screen or integrate acceleration into position. Recenter with the screen facing up.

## Linux

Python 3.10+, GTK 3, PyGObject, `cryptography`, and `qrcode` with Pillow are required. Ayatana AppIndicator provides the system tray. Run as your desktop user.

### Install dependencies

Ubuntu / Debian:

```sh
sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-ayatanaappindicator3-0.1 python3-cryptography python3-qrcode python3-pil
```

Fedora:

```sh
sudo dnf install python3-gobject gtk3 libayatana-appindicator-gtk3 python3-cryptography python3-qrcode python3-pillow
```

Arch Linux:

```sh
sudo pacman -S python-gobject gtk3 libayatana-appindicator python-cryptography python-qrcode python-pillow
```

### Configure input once

```sh
sudo sh linux/setup-input.sh
```

Sign out and back in. The setup grants the `levitate` group access to `/dev/uinput` and enables the kernel module. It does not grant access to physical input-event devices. On systems without logind, add your user to the `levitate` group manually if you ran the script without `sudo`.

### Run or install

```sh
./linux/levitate-run
# Optional user-local app-menu and icon installation:
sh linux/install.sh
# Optional tray-only startup:
~/.local/bin/levitate --tray
```

Closing the window keeps the companion in the tray; **Quit Levitate** stops it. If the tray library is missing, closing quits. GNOME needs an AppIndicator/StatusNotifier extension (Ubuntu includes one). KDE and most tray-enabled desktops support it directly. Minimal compositors need a StatusNotifier host such as a Waybar tray.

The virtual mouse and keyboard use Linux **uinput**, so the pointer, buttons and scrolling work across Wayland and X11 independently of compositor-specific remote-input protocols. Distributions must permit virtual input devices. Workspace changes use configurable shortcuts rather than synthetic touchpad gestures. GNOME defaults are `META+PAGEUP` and `META+PAGEDOWN`; set your compositor’s bindings in Preferences.

The listener uses TCP **45879** by default. Choose the shared Wi-Fi interface in the network selector; refresh after changing to a hotspot. If a firewall is enabled, allow this port from your local subnet. Guest-network client isolation can prevent pairing. IPv4 local networks are supported in this release.

## Android

Android 8.0+ (API 26). A game rotation vector or rotation vector sensor enables air-mouse control. Touchpad control remains available without a rotation sensor. The QR scanner operates on-device and requests camera permission only when opened.

Build with JDK 17 or 21 and Android SDK 36:

```sh
cd android
./gradlew :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Levitate on both devices, connect to the same Wi-Fi or hotspot, and scan the laptop QR. A pairing link can also be pasted manually. Click Recenter with the phone in the pointing position.

Sensor subscriptions run at 60 Hz (30 Hz in battery saver) only while the Control page is visible, connected and unpaused. Diagram updates are capped at 10 Hz. Backgrounding the app stops sensors and releases mouse buttons. The connection remains available until Android destroys the activity; the app does not run a foreground service or acquire a wake lock. Pairing credentials are kept in memory: scan again after restarting the Android app.

## Repository

```text
android/    Kotlin app, resources, Gradle wrapper and motion tests
linux/      Python companion, tray, input setup, installer and tests
docs/       Protocol, compatibility and testing notes
```

## Verification

```sh
cd linux
/usr/bin/python3 -m unittest discover -s tests -v
cd ../android
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon
```

See [testing notes](docs/TESTING.md) for hardware evidence, and [the protocol](docs/PROTOCOL.md) for local transport and pairing details. Linux uses distro packages instead of a bundled browser runtime. Build memory and runtime memory are distinct; Android Gradle compilation is limited to two workers and its single-use daemon exits afterward.

## Packages

`sh linux/package.sh` creates a source/runtime tarball and, when `dpkg-deb` is available, a Debian package in `dist/`. The tarball includes the user-local installer and input setup script for other distributions. `sh linux/package-appimage.sh` can wrap the companion in a dependency-aware AppImage using an installed `appimagetool`; it uses the host’s Python/GTK packages, which must be installed. It is not a self-contained Python/GTK runtime.

### Maintainer: local releases

With JDK, Android SDK 36/build-tools 35.0.0, Linux dependencies and `appimagetool` installed:

```sh
sh scripts/release-local.sh
```

This runs automated checks, builds the optimized Android release, signs it, creates Linux packages and writes `dist/SHA256SUMS`. The first run creates a dedicated signing key and random password in `~/.local/share/levitate-signing/` (or `LEVITATE_SIGNING_DIR`). **Back up that directory privately:** the same key is needed for future Android updates. It is never packaged or uploaded. Hosted GitHub checks are manual-only; source pushes and pull requests do not start paid build jobs. Release assets can be uploaded directly with `gh release create` from the maintainer's laptop.

See [security notes](SECURITY.md) for pairing and signing details. `PRODUCT.md` is local planning context and is excluded from Git.

## Contributing

Bug reports should include desktop/compositor, distribution, Android version, sensor name (from Settings), and which mode fails. Never attach a current QR code, pairing token or private key. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE) · Copyright 2026 [Uzair Mughal](https://uzair.is-a.dev) · [@uzairdeveloper223](https://github.com/uzairdeveloper223)

Third-party components retain their own licenses; see [THIRD_PARTY.md](THIRD_PARTY.md).
