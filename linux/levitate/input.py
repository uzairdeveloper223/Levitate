"""Linux uinput: the same input path on Wayland and X11, without a daemon."""

import fcntl
import math
import os
import struct
import threading

EV_SYN, EV_KEY, EV_REL = 0, 1, 2
REL_X, REL_Y, REL_HWHEEL, REL_WHEEL = 0, 1, 6, 8
UI_SET_EVBIT, UI_SET_KEYBIT, UI_SET_RELBIT = 0x40045564, 0x40045565, 0x40045566
UI_DEV_SETUP, UI_DEV_CREATE, UI_DEV_DESTROY = 0x405C5503, 0x5501, 0x5502
BUTTONS = {"left": 272, "right": 273, "middle": 274}
KEYS = {
    "CTRL": 29, "SHIFT": 42, "ALT": 56, "META": 125,
    "LEFT": 105, "RIGHT": 106, "UP": 103, "DOWN": 108,
    "PAGEUP": 104, "PAGEDOWN": 109, "TAB": 15, "ENTER": 28,
    "HOME": 102, "END": 107, "SPACE": 57,
    **{f"F{number}": 58 + number for number in range(1, 11)},
}
MEDIA = {"play": 164, "next": 163, "previous": 165, "volume_up": 115, "volume_down": 114}


def shortcut_keys(shortcut):
    parts = shortcut.upper().replace(" ", "").split("+")
    if not 1 <= len(parts) <= 4 or any(part not in KEYS for part in parts):
        raise ValueError("Use key names such as META+PAGEUP or CTRL+ALT+LEFT.")
    return [KEYS[part] for part in parts]


class VirtualInput:
    def __init__(self, settings):
        self.settings = settings
        self.lock = threading.RLock()
        self.pressed = set()
        self.remainders = [0.0] * 4
        self.paused = False
        self.fd = None
        self.error = None
        try:
            self._create_device()
        except OSError as error:
            self.error = str(error)
            if self.fd is not None:
                os.close(self.fd)
                self.fd = None

    def _create_device(self):
        self.fd = os.open("/dev/uinput", os.O_WRONLY | os.O_NONBLOCK)
        for event_type in (EV_KEY, EV_REL):
            fcntl.ioctl(self.fd, UI_SET_EVBIT, event_type)
        for code in set(BUTTONS.values()) | set(KEYS.values()) | set(MEDIA.values()):
            fcntl.ioctl(self.fd, UI_SET_KEYBIT, code)
        for axis in (REL_X, REL_Y, REL_WHEEL, REL_HWHEEL):
            fcntl.ioctl(self.fd, UI_SET_RELBIT, axis)
        setup = struct.pack("HHHH80sI", 3, 0x1209, 0x1E71, 1, b"Levitate Air Mouse", 0)
        fcntl.ioctl(self.fd, UI_DEV_SETUP, setup)
        fcntl.ioctl(self.fd, UI_DEV_CREATE)

    @property
    def ready(self):
        return self.fd is not None

    def _write(self, event_type, code, value):
        os.write(self.fd, struct.pack("llHHi", 0, 0, event_type, code, value))

    def _sync(self):
        self._write(EV_SYN, 0, 0)

    def _relative(self, values, axes, offset):
        with self.lock:
            if not self.ready or self.paused:
                return
            for index, (value, axis) in enumerate(zip(values, axes), offset):
                self.remainders[index] += value
                steps = math.trunc(self.remainders[index])
                self.remainders[index] -= steps
                if steps:
                    self._write(EV_REL, axis, steps)
            self._sync()

    def move(self, x, y):
        self._relative((x, y), (REL_X, REL_Y), 0)

    def scroll(self, x, y):
        self._relative((x, y), (REL_HWHEEL, REL_WHEEL), 2)

    def button(self, name, down):
        with self.lock:
            if not self.ready or (self.paused and down):
                return
            code = BUTTONS[name]
            if down == (code in self.pressed):
                return
            self._write(EV_KEY, code, int(down))
            self.pressed.add(code) if down else self.pressed.discard(code)
            self._sync()

    def _tap_keys(self, codes):
        with self.lock:
            if not self.ready or self.paused:
                return
            for code in codes:
                self._write(EV_KEY, code, 1)
            self._sync()
            for code in reversed(codes):
                self._write(EV_KEY, code, 0)
            self._sync()

    def workspace(self, direction):
        setting = "workspace_left" if direction < 0 else "workspace_right"
        self._tap_keys(shortcut_keys(self.settings[setting]))

    def media(self, action):
        self._tap_keys([MEDIA[action]])

    def release(self):
        with self.lock:
            if self.ready:
                for code in self.pressed:
                    self._write(EV_KEY, code, 0)
                self._sync()
            self.pressed.clear()
            self.remainders = [0.0] * 4

    def set_paused(self, paused):
        with self.lock:
            self.paused = paused
            self.release()

    def close(self):
        with self.lock:
            self.release()
            if self.ready:
                fcntl.ioctl(self.fd, UI_DEV_DESTROY)
                os.close(self.fd)
                self.fd = None
