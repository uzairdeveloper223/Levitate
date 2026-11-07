# Contributing to Levitate

Small fixes are easier for me to review. Keep motion processing in the Android app and send input commands to Linux. I want Levitate to stay lightweight and work locally without an account or cloud service.

Before opening a pull request, run the Linux tests, Android motion tests and Android lint. The commands are in [TESTING.md](docs/TESTING.md). For UI changes, include screenshots from the Android app or GTK window in both themes. Keep the native Kotlin/GTK setup and avoid extra background work when the app is idle.

If a gesture breaks, tell me which phone, Android version and sensor you're using, plus your Linux distribution, compositor and whether you're on Wi-Fi or a hotspot. Explain what you did and what happened. Remove pairing tokens, active QR codes and private keys before sharing logs or screenshots.

Open an issue first if you want to change the pairing protocol or input permissions, so we can work through compatibility before writing the change.

By contributing, you agree your contribution is licensed under the project's MIT license.
