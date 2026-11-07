# Android controller and Linux companion

Mode: Operate. Material 3 on Android, GTK on Linux.

## Interface notes

I want the phone to show what mode it's in and keep the mouse buttons easy to reach. Show pairing actions when disconnected and focus on the controls after pairing.

Use system fonts, blue primary controls and blue-gray surfaces. Green shows a connected phone; red shows errors. Both light and dark themes follow the system unless changed in Settings. Use native Material buttons, sliders and switches on Android, and ordinary GTK controls on Linux.

The phone's Control page has connection status, a phone diagram, mode selection, recenter/pause and large mouse buttons. Settings and instructions have their own pages. The diagram follows the phone's roll, and mode text changes after a stable tilt.

The Linux window has the QR code and network selector, connection/input status, preferences and instructions. Keep the QR black on white with its quiet zone. Closing the window leaves the app in the tray when a tray host is available.

For UI changes, check real Android and GTK screens, both themes and larger text. My hardware checks and remaining test list are in `docs/TESTING.md`. Icon sources and licenses are in `THIRD_PARTY.md`.
