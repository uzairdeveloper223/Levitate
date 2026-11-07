# Levitate 1.0.0

This is the first stable release of Levitate. I use it to control my Linux laptop with my Android phone over Wi-Fi. Scan the laptop's QR code to pair, then point, scroll and click from the phone.

## Downloads

- **Android:** Install `Levitate-1.0.0-android.apk` on Android 8.0 or newer. It's the signed release build. If you installed the earlier debug APK, uninstall it first because its signing key is different. Future releases will use the same release key.
- **Ubuntu/Debian:** Install `levitate_1.0.0_all.deb` with `sudo apt install ./levitate_1.0.0_all.deb`. Apt installs the required dependencies too.
- **Linux AppImage:** Make `Levitate-1.0.0-x86_64.AppImage` executable and run it. You need Python/GTK, PyGObject, cryptography, qrcode and Pillow installed on your system. I kept those out of the AppImage to keep it small. If FUSE is unavailable, use `--appimage-extract-and-run`.
- **Other Linux distributions:** Unpack `levitate-linux-1.0.0.tar.gz`, install the dependencies from README, then run `sh linux/install.sh`.
- **Checksums:** Download `SHA256SUMS` alongside the files and run `sha256sum -c SHA256SUMS`. `ANDROID-SIGNING-CERTIFICATE.txt` contains the public APK signing fingerprint.

## Controls

Hold the screen facing up with the phone's top toward your laptop, tap Recenter, and aim to move the pointer. Roll the left edge up and sweep left or right to switch workspaces. Roll the right edge up to scroll.

There are buttons for click/drag, pause, manual modes, touchpad input, middle click and media controls. You can change sensitivity, vibration and battery-saver sampling in Settings.

The Linux companion uses uinput for Wayland and X11. Run the included `setup-input.sh` once if your account needs device access, then run the app as your normal user. Set your workspace shortcuts in Preferences. The window closes to the tray, where you can reopen it, pause input, disconnect or quit.

## Security and verification

Pairing uses TLS 1.2 or newer, the laptop certificate fingerprint from the QR, and a random 256-bit token. Messages have size and rate limits. Invalid input disconnects, and pause/disconnect releases held buttons. The release also handles deeply nested JSON, huge integers, slow partial pairing messages and commands left in a disconnected session's buffer.

I built and signed these files locally and uploaded them directly. All 10 Linux tests and 5 Android motion tests pass, along with lint and the Android release build.

I tested the first build on my OnePlus 8T and Ubuntu GNOME Wayland laptop. Movement felt very smooth and all the orientation gestures worked. I'm doing the remaining hands-on v1.0.0 checks myself. My setup and checklist are in [TESTING.md](TESTING.md).

Use the same Wi-Fi or a phone hotspot; v1.0.0 supports IPv4. Other desktops may need different workspace bindings. Keep the companion on a trusted local network. Pairing details are in [SECURITY.md](../SECURITY.md).

MIT licensed. By [Uzair Mughal](https://uzair.is-a.dev), [@uzairdeveloper223](https://github.com/uzairdeveloper223).
