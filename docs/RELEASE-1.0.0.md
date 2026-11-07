# Levitate 1.0.0

Turn an Android phone into an air mouse for a Linux desktop, with local encrypted QR pairing.

## Downloads

- **Android:** `Levitate-1.0.0-android.apk` — signed, optimized release APK; Android 8.0 or newer. Allow installation from your chosen file manager or browser. If you installed the development APK, uninstall that debug build before installing this release because the signing certificates differ. Future release APKs will use the same dedicated release key.
- **Ubuntu/Debian:** `levitate_1.0.0_all.deb` — install using `sudo apt install ./levitate_1.0.0_all.deb`. The Python source package is architecture-independent and installs required distro dependencies.
- **Linux AppImage:** `Levitate-1.0.0-x86_64.AppImage` — make executable and run. This lightweight package requires host Python/GTK, PyGObject, cryptography, qrcode and Pillow; it does not bundle those runtimes. Use `--appimage-extract-and-run` if FUSE mounting is unavailable.
- **Other Linux distributions:** `levitate-linux-1.0.0.tar.gz` — unpack, install the distribution dependencies listed in README, and run `sh linux/install.sh`.
- **Verification:** `SHA256SUMS` and `ANDROID-SIGNING-CERTIFICATE.txt` — run `sha256sum -c SHA256SUMS` with the downloaded assets together. The certificate file records the public Android release-signing fingerprint.

## Controls

Screen facing up with the phone's top toward the laptop moves the pointer. Left edge up enables deliberate left/right workspace sweeps; right edge up enables scrolling. Recenter, pause, manual modes, click/drag, a touchpad, middle click, media buttons, sensitivity, haptics and a battery-saver mode are included.

Linux kernel uinput supports pointer input on Wayland and X11. Workspace bindings are configurable. Configure device permissions once using the included `setup-input.sh`, then run Levitate as your normal desktop user. The companion offers a tray icon, network selector, QR pairing, preferences and instructions.

## Security and verification

TLS 1.2+, exact QR certificate pinning, random 256-bit pairing tokens, bounded/rate-limited messages, strict input validation, and input release on pause/disconnect. The v1.0.0 review includes protections against deeply nested JSON and execution of buffered commands after session revocation.

The release was built and signed locally without hosted GitHub builds. Linux protocol/TLS security tests, Android motion unit tests, release compilation and Android lint are automated checks. The initial development build was physically tested on a OnePlus 8T and Ubuntu GNOME Wayland; the user reported smooth movement and working gestures. Additional hands-on v1.0.0 testing is being performed by the user. See [TESTING.md](TESTING.md) for the scope of existing evidence.

IPv4 on shared Wi-Fi or a phone hotspot is supported. Other desktop configurations require their own gesture/binding checks. Keep the listener on a trusted local network. See [SECURITY.md](../SECURITY.md).

MIT licensed. By [Uzair Mughal](https://uzair.is-a.dev), [@uzairdeveloper223](https://github.com/uzairdeveloper223).
