# Verification and hardware

## Initial hardware

- Phone: OnePlus 8T, model KB2007, Android API 30, 1080 × 2400 physical display.
- Motion hardware inspected through ADB: Bosch bmi26x gyroscope and accelerometer, Qualcomm and AOSP game rotation vector, gravity and rotation vector sensors.
- Laptop: Ubuntu 24.04, GNOME, Wayland. `/dev/uinput` access was already available to the desktop user.
- Network: phone and laptop on the same private Wi-Fi subnet. No WAN service is involved.

## Confirmed first-device result

The Android app was installed on the connected OnePlus through ADB and paired by QR over Wi-Fi to the GTK companion. The user physically tested it and reported: “it is very smooth, all gestures work.” The running Android interface showed approximately 5 ms round-trip latency at the moment captured; this is a single local-network observation, not a general benchmark.

## Automated checks

Linux tests exercise bounded/coalesced framing, finite/ranged coordinate validation, shortcut allowlisting, actual TLS handshakes, QR certificate fingerprint consistency, revoked token rejection, mouse commands, and release on disconnect. They use a recording input sink rather than changing the tester's desktop.

Android motion tests exercise face-up coordinate signs, stationary drift suppression, angle wrap, sensor discontinuities, left/right roll selection with dwell, workspace debouncing, recenter, and manual modes. Lint checks Android resources, permissions and API use.

## Manual checks to repeat on another desktop

- Launch, select the shared network, scan QR, and verify connection state on both devices.
- Recenter face-up. Check horizontal/vertical pointer direction and stationary stability.
- Hold Left click and move, then release. Repeat disconnect while held and verify release.
- Roll left and sweep both ways; check the configured workspace bindings.
- Roll right and check vertical/horizontal scroll in a long document.
- Check manual modes, pause/resume, touchpad tap and two-finger scrolling.
- Background the Android app and verify sensor subscriptions stop.
- Try a hotspot, an unavailable companion, a revoked QR, and a blocked port.
- Inspect Android light/dark themes and larger font scale. Restore the device's original system settings afterward.
- Test tray close/reopen/quit, and a session without a tray host.

Compatibility is designed around Linux kernel uinput; actual hardware verification on this machine is GNOME Wayland. Other compositor and distribution combinations need independent testing. Workspace shortcuts and physical feel vary with desktop configuration, sensor quality, hand posture and Wi-Fi latency.

## v1.0.0 handoff

Hands-on testing of the final v1.0.0 binaries is delegated to the user at their request. Further automated verification uses recording input sinks and local builds, without controlling their phone or laptop. The phone's temporary review settings were restored to night mode `yes` and font scale `0.8`. Earlier files named as light-theme captures are not valid evidence of light-theme verification.

Ten Linux tests additionally verify rejection of boolean protocol versions and oversized integers, survival of deeply nested JSON input, a total authentication deadline even when a partial hello is trickled continuously, and revocation of commands already buffered before a disconnect. The signed Android release's certificate and zip alignment are verified by SDK tools; release assets carry SHA-256 checksums.
