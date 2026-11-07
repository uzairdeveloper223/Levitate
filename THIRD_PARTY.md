# Third-party software

Levitate's original code and vector artwork are MIT licensed. Dependencies retain their licenses.

- Kotlin, AndroidX, Material Components for Android: Apache-2.0.
- ZXing and ZXing Android Embedded / JourneyApps: Apache-2.0. Scanner consumer rules and license metadata remain in their upstream artifacts.
- Gradle and Gradle wrapper: Apache-2.0.
- Python: PSF license.
- GTK and PyGObject: LGPL (provided by the Linux distribution).
- libayatana-appindicator: LGPL (provided by the Linux distribution).
- cryptography: Apache-2.0 or BSD-3-Clause.
- python-qrcode: BSD-3-Clause.
- Pillow: HPND.

No generated images or third-party icon artwork are bundled. Application and tray icons are original vectors in `linux/assets/`; Android carries the corresponding vector drawable. `linux/assets/levitate.png` is a 256 × 256 rasterization of the original `levitate.svg` for AppImage icon compatibility, rendered with librsvg via ffmpeg, and carries the same MIT license. The AppStream metadata is CC0-1.0.
