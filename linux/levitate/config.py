"""Small, private configuration files; no credentials in the repository."""

import json
import os
from pathlib import Path

DEFAULTS = {
    "workspace_left": "META+PAGEUP",
    "workspace_right": "META+PAGEDOWN",
    "dark_theme": None,
}


def config_directory():
    directory = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "levitate"
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    return directory


def load_settings():
    settings = DEFAULTS.copy()
    try:
        stored = json.loads((config_directory() / "settings.json").read_text())
        settings.update({key: stored[key] for key in DEFAULTS if key in stored})
    except (OSError, ValueError, TypeError):
        pass
    return settings


def save_settings(settings):
    path = config_directory() / "settings.json"
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(settings, indent=2) + "\n")
    temporary.chmod(0o600)
    temporary.replace(path)
