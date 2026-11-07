# Testing

I'm testing Levitate on my own phone and laptop. This is what works on my setup so far, along with the checks I use before a release.

## My setup

- OnePlus 8T (KB2007), Android 11 / API 30, 1080 × 2400 display.
- Bosch bmi26x gyroscope and accelerometer. The phone also has game rotation vector, gravity and rotation vector sensors.
- Ubuntu 24.04 with GNOME on Wayland. My account already had access to `/dev/uinput`.
- Both devices connected to the same Wi-Fi network.

I paired the first build by scanning the laptop's QR code. The pointer felt very smooth and all the orientation gestures worked. The app showed about 5 ms round-trip latency during that session. That's one reading on my Wi-Fi, so I wouldn't use it as a promise for every network.

I'm doing the remaining hands-on testing of v1.0.0 myself. Other phones, desktops and hotspots still need checking.

## Local release checks

The v1.0.0 local checks pass: 10 Linux tests, 5 Android motion tests, Android lint and the release build. The APK's signature and zip alignment are also verified. The downloads include SHA-256 checksums.

The Linux tests cover TLS pairing, wrong and revoked tokens, frame limits, input validation, shortcut restrictions and release on disconnect. They also check that huge integers and deeply nested JSON don't stop the listener, that a partial hello can't keep authentication open forever, and that buffered commands can't run after a disconnect. These tests record input commands without moving the desktop pointer.

The Android tests cover pointer direction, stationary movement, angle wrap, sudden sensor changes, mode-switch timing, workspace debouncing, recenter and manual modes.

To run them locally:

```sh
PYTHONPATH=linux /usr/bin/python3 -m unittest discover -s linux/tests -v
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon
```

## Things to check on a device

- Pair over Wi-Fi, then repeat using a phone hotspot.
- Recenter with the screen facing up. Check both pointer axes and leave the phone still for a moment.
- Hold Left click while moving to drag. Disconnect while holding it and check that the button releases.
- Roll the left edge up and sweep both ways to change workspaces.
- Roll the right edge up and check vertical and horizontal scrolling.
- Try manual modes, pause/resume, touchpad taps and two-finger scrolling.
- Put the Android app in the background and check that its sensors stop.
- Try an old QR, a closed companion and a blocked network port.
- Check light and dark themes, larger text, and labels near the bottom of each page.
- Close and reopen the Linux window from the tray, then quit. Also try a desktop without a tray host.

So far, my hardware testing is on GNOME Wayland. Levitate uses uinput for Wayland and X11, but each desktop can have different workspace shortcuts. Include your phone, sensor, distribution and compositor when reporting a problem.
