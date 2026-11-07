# Third-party software

Levitate's code and original icons are MIT licensed. The libraries it uses have their own licenses:

- Kotlin, AndroidX, Material Components for Android: Apache-2.0.
- ZXing and ZXing Android Embedded / JourneyApps: Apache-2.0. Scanner consumer rules and license metadata remain in their upstream artifacts.
- Gradle and Gradle wrapper: Apache-2.0.
- Python: PSF license.
- GTK and PyGObject: LGPL (provided by the Linux distribution).
- libayatana-appindicator: LGPL (provided by the Linux distribution).
- cryptography: Apache-2.0 or BSD-3-Clause.
- python-qrcode: BSD-3-Clause.
- Pillow: HPND.

The app and tray icons are in `linux/assets/`. Android uses a matching vector drawable. `levitate.png` is a 256 × 256 copy of `levitate.svg`, converted with librsvg through ffmpeg because AppImage needs a PNG icon. It has the same MIT license as the SVG. There are no generated images or third-party icon packs in the downloads.

The AppStream metadata is CC0-1.0.
