"""Composition root and optional freedesktop StatusNotifier tray icon."""

import argparse
import logging
import signal

import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gio, GLib, Gtk

from .config import load_settings
from .input import VirtualInput
from .pairing import Pairing
from .server import CompanionServer
from .ui import ASSETS, CompanionWindow, apply_styles


class LevitateApplication(Gtk.Application):
    def __init__(self, options):
        super().__init__(application_id="dev.uzair.Levitate", flags=Gio.ApplicationFlags.FLAGS_NONE)
        self.options = options
        self.settings = load_settings()
        self.window = None
        self.indicator = None
        self.target = None
        self.server = None

    def do_startup(self):
        Gtk.Application.do_startup(self)
        apply_styles(self.settings)
        self.target = VirtualInput(self.settings)
        self.pairing = Pairing.create(self.options.port)
        self.server = CompanionServer(self.pairing, self.target, self._network_status)
        self.hold()
        signal.signal(signal.SIGTERM, lambda *_: GLib.idle_add(self.quit))
        signal.signal(signal.SIGINT, lambda *_: GLib.idle_add(self.quit))
        self._create_tray()
        try:
            self.server.start()
        except OSError as error:
            self.startup_error = str(error)
        else:
            self.startup_error = None

    def do_activate(self):
        if self.window is None:
            self.window = CompanionWindow(self)
            self.window.show_all()
            self.window.update_status(None)
            if self.startup_error:
                self.window.connection_label.set_text(f"Could not start listener: {self.startup_error}")
            if self.options.tray and self.indicator:
                self.window.hide()
        else:
            self.window.present()

    def _create_tray(self):
        try:
            gi.require_version("AyatanaAppIndicator3", "0.1")
            from gi.repository import AyatanaAppIndicator3 as Indicator
        except (ValueError, ImportError):
            try:
                gi.require_version("AppIndicator3", "0.1")
                from gi.repository import AppIndicator3 as Indicator
            except (ValueError, ImportError):
                logging.warning("Tray library unavailable; closing the window will quit")
                return
        self.indicator = Indicator.Indicator.new("levitate", str(ASSETS / "levitate-symbolic.svg"),
                                                 Indicator.IndicatorCategory.HARDWARE)
        self.indicator.set_status(Indicator.IndicatorStatus.ACTIVE)
        self.indicator.set_title("Levitate")
        menu = Gtk.Menu()
        for title, callback in (("Open Levitate", lambda _: self.activate()),
                                ("Pause / resume input", lambda _: self.toggle_pause()),
                                ("Disconnect phone", lambda _: self.server.disconnect()),
                                ("Quit Levitate", lambda _: self.quit())):
            item = Gtk.MenuItem(label=title)
            item.connect("activate", callback)
            menu.append(item)
        menu.show_all()
        self.indicator.set_menu(menu)

    def _network_status(self, name):
        GLib.idle_add(self._update_status, name)

    def _update_status(self, name):
        if self.window:
            self.window.update_status(name)
        if self.indicator:
            self.indicator.set_title(f"Levitate · {name}" if name else "Levitate · ready to pair")
        return False

    def toggle_pause(self):
        self.target.set_paused(not self.target.paused)
        if self.window:
            self.window.update_pause()

    def do_shutdown(self):
        if self.server:
            self.server.stop()
        if self.target:
            self.target.close()
        Gtk.Application.do_shutdown(self)


def main():
    parser = argparse.ArgumentParser(description="Levitate local air-mouse companion")
    parser.add_argument("--port", type=int, default=45879)
    parser.add_argument("--tray", action="store_true", help="Start in the system tray")
    options = parser.parse_args()
    if not 1024 <= options.port <= 65535:
        parser.error("port must be between 1024 and 65535")
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    return LevitateApplication(options).run([])
