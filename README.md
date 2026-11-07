# Levitate

<img src="linux/assets/levitate.svg" width="88" alt="Levitate icon">

I built Levitate to use my Android phone as an air mouse on Linux. Aim the phone to move the pointer, roll it to scroll or switch workspaces, and use the on-screen buttons to click and drag.

The Android app is Kotlin with Material 3. The Linux companion is Python and GTK. Connect both devices to the same Wi-Fi or your phone's hotspot, then scan the laptop's QR code. Pairing is encrypted and everything stays local. You don't need an account.

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

The app waits for a stable tilt before changing modes, so small movements don't keep switching them. You can also select Point, Scroll or Spaces manually. Settings include sensitivity, natural scrolling, vibration, keeping the screen on, and a 30 Hz battery-saver mode. There are middle-click and media buttons too.

Levitate uses the phone's rotation sensors to track where its top edge is pointing. Recenter sets your comfortable pointing direction. It can't measure the phone's position relative to the laptop, so recenter with the screen facing up whenever you change how you're holding it.

## Linux

You'll need Python 3.10+, GTK 3, PyGObject, `cryptography`, and `qrcode` with Pillow. Install Ayatana AppIndicator for the tray icon. Run the app as your normal desktop user.

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

Close the window to keep Levitate in the tray. Choose Quit Levitate from the tray menu to stop it. If the tray library isn't installed, closing the window quits the app.

GNOME needs an AppIndicator/StatusNotifier extension; Ubuntu includes one. KDE supports it directly. On a minimal compositor, use a tray host such as Waybar.

I use Linux uinput for the virtual mouse and keyboard. It works with Wayland and X11 once your account has permission to create the input device. Workspace switching sends the shortcuts you set in Preferences. The defaults are GNOME's `META+PAGEUP` and `META+PAGEDOWN`; change them to match your desktop.

The companion listens on TCP port 45879. Choose the network shared with your phone and refresh the list after switching to a hotspot. If your firewall blocks pairing, allow this port from your local subnet. Some guest Wi-Fi networks block connections between devices. This release supports IPv4.

## Android

The app needs Android 8.0 or newer (API 26). Air-mouse control uses a game rotation vector or rotation vector sensor. You can still use the touchpad if your phone doesn't have one. The QR scanner runs on the phone and asks for camera permission when you open it.

Build with JDK 17 or 21 and Android SDK 36:

```sh
cd android
./gradlew :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Levitate on both devices, connect to the same Wi-Fi or hotspot, and scan the laptop QR. A pairing link can also be pasted manually. Click Recenter with the phone in the pointing position.

The phone samples motion at 60 Hz, or 30 Hz in battery saver, while the Control page is open, connected and unpaused. The diagram updates at most 10 times a second. Leaving the app stops the sensors and releases held mouse buttons.

The connection can stay open while the activity exists. There isn't a background service or wake lock. Pairing stays in memory, so scan again after restarting the app.

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

My setup and test results are in [TESTING.md](docs/TESTING.md). The pairing messages are documented in [PROTOCOL.md](docs/PROTOCOL.md). The Linux app uses distro packages to keep the download small. Android builds use at most two Gradle workers, and the build daemon exits when they're done.

## Packages

Run `sh linux/package.sh` to create the Linux tarball and Debian package in `dist/`. Debian packaging needs `dpkg-deb`. The tarball includes the installer and input setup script for other distributions.

With `appimagetool` installed, run `sh linux/package-appimage.sh` for the AppImage. It uses your system's Python and GTK packages, so install those dependencies first.

### Building a release locally

With JDK, Android SDK 36/build-tools 35.0.0, Linux dependencies and `appimagetool` installed:

```sh
sh scripts/release-local.sh
```

I build releases on my laptop and upload the files directly to GitHub Releases. The script runs the local checks, builds and signs the Android APK, packages Linux, and writes `dist/SHA256SUMS`. GitHub build checks only run when started manually.

On its first run, the script creates a signing key and random password in `~/.local/share/levitate-signing/`, or the directory set by `LEVITATE_SIGNING_DIR`. Back up that directory privately. Future Android updates need the same key. The script keeps it out of the packages and uploads.

See [SECURITY.md](SECURITY.md) for pairing and signing details. I keep `PRODUCT.md` as local project notes, so it's in `.gitignore`.

## Contributing

If something breaks, open an issue and tell me your phone, Android version, sensor name from Settings, Linux distribution and compositor. Include the mode you're using and what went wrong. Keep active QR codes, pairing tokens and private keys out of screenshots and logs. More details are in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE) · Copyright 2026 [Uzair Mughal](https://uzair.is-a.dev) · [@uzairdeveloper223](https://github.com/uzairdeveloper223)

Third-party components retain their own licenses; see [THIRD_PARTY.md](THIRD_PARTY.md).
