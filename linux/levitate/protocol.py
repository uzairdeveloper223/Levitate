"""Only a bounded set of pointer, button, workspace and media commands."""

import json
import math

from .input import BUTTONS, MEDIA

MAX_FRAME_BYTES = 2048


def read_message(stream):
    frame = stream.readline(MAX_FRAME_BYTES + 1)
    if not frame:
        raise EOFError
    if len(frame) > MAX_FRAME_BYTES or not frame.endswith(b"\n"):
        raise ValueError("Invalid frame length")
    try:
        message = json.loads(frame)
    except RecursionError as error:
        raise ValueError("JSON nesting exceeded") from error
    if not isinstance(message, dict):
        raise ValueError("Expected an object")
    return message


def coordinate(message, name, limit):
    value = message.get(name)
    if type(value) not in (int, float) or abs(value) > limit or not math.isfinite(value):
        raise ValueError("Invalid coordinate")
    return value


def dispatch(message, target):
    command = message.get("type")
    if command == "move":
        target.move(coordinate(message, "x", 500), coordinate(message, "y", 500))
    elif command == "scroll":
        target.scroll(coordinate(message, "x", 20), coordinate(message, "y", 20))
    elif command == "button":
        if message.get("button") not in BUTTONS or type(message.get("down")) is not bool:
            raise ValueError("Invalid button")
        target.button(message["button"], message["down"])
    elif command == "workspace":
        if type(message.get("direction")) is not int or message["direction"] not in (-1, 1):
            raise ValueError("Invalid direction")
        target.workspace(message["direction"])
    elif command == "media":
        if message.get("action") not in MEDIA:
            raise ValueError("Invalid media action")
        target.media(message["action"])
    elif command == "release":
        target.release()
    elif command != "ping":
        raise ValueError("Unknown command")
