import io
import json
import unittest

from levitate.input import shortcut_keys
from levitate.protocol import dispatch, read_message


class RecordingInput:
    ready = True
    paused = False

    def __init__(self):
        self.events = []

    def move(self, x, y):
        self.events.append(("move", x, y))

    def scroll(self, x, y):
        self.events.append(("scroll", x, y))

    def button(self, name, down):
        self.events.append(("button", name, down))

    def workspace(self, direction):
        self.events.append(("workspace", direction))

    def media(self, action):
        self.events.append(("media", action))

    def release(self):
        self.events.append(("release",))


class ProtocolTests(unittest.TestCase):
    def test_invalid_input_never_reaches_device(self):
        invalid = [
            {"type": "move", "x": float("nan"), "y": 0},
            {"type": "move", "x": True, "y": 0},
            {"type": "move", "x": 10 ** 1000, "y": 0},
            {"type": "scroll", "x": 0, "y": 21},
            {"type": "scroll", "x": 0, "y": float("inf")},
            {"type": "button", "button": "left", "down": "true"},
            {"type": "button", "button": "power", "down": True},
            {"type": "workspace", "direction": True},
            {"type": "media", "action": "shell"},
            {"type": "execute", "command": "whoami"},
        ]
        target = RecordingInput()
        for message in invalid:
            with self.subTest(message=message), self.assertRaises(ValueError):
                dispatch(message, target)
        self.assertEqual(target.events, [])

    def test_framing_rejects_oversized_partial_and_nonobject_messages(self):
        for frame in (b"x" * 2049 + b"\n", b'{"type":"release"}', b"[]\n"):
            with self.subTest(frame=frame[:30]), self.assertRaises(ValueError):
                read_message(io.BytesIO(frame))

    def test_coalesced_frames_remain_distinct(self):
        frames = [{"type": "button", "button": "left", "down": True},
                  {"type": "move", "x": 4.5, "y": -2},
                  {"type": "button", "button": "left", "down": False}]
        stream = io.BytesIO("".join(json.dumps(message) + "\n" for message in frames).encode())
        target = RecordingInput()
        for _ in frames:
            dispatch(read_message(stream), target)
        self.assertEqual(target.events, [("button", "left", True), ("move", 4.5, -2), ("button", "left", False)])

    def test_shortcuts_are_allowlisted(self):
        self.assertEqual(shortcut_keys("META+PAGEUP"), [125, 104])
        with self.assertRaises(ValueError):
            shortcut_keys("META+$(reboot)")


if __name__ == "__main__":
    unittest.main()
