"""Native GTK UI, updated on connection transitions rather than sensor frames."""

import io
from pathlib import Path

import gi
import qrcode

gi.require_version("Gtk", "3.0")
from gi.repository import Gdk, GdkPixbuf, GLib, Gtk

from .config import save_settings
from .input import shortcut_keys
from .pairing import network_addresses

ASSETS = Path(__file__).resolve().parent.parent / "assets"


def label(text, style=None):
    widget = Gtk.Label(label=text, xalign=0)
    widget.set_line_wrap(True)
    if style:
        widget.get_style_context().add_class(style)
    return widget


def button(text, callback, style=None):
    widget = Gtk.Button(label=text)
    widget.connect("clicked", callback)
    if style:
        widget.get_style_context().add_class(style)
    return widget


def vertical(spacing=12):
    return Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=spacing)


class CompanionWindow(Gtk.ApplicationWindow):
    def __init__(self, application):
        super().__init__(application=application, title="Levitate")
        self.app = application
        self.set_default_size(560, 740)
        self.set_icon_from_file(str(ASSETS / "levitate.svg"))
        self.connect("delete-event", self._hide_to_tray)
        header = Gtk.HeaderBar(title="Levitate", subtitle="Air mouse for Linux", show_close_button=True)
        self.set_titlebar(header)
        header.pack_end(button("About", self._show_about))
        content = vertical(20)
        content.set_border_width(24)
        self.add(content)
        self.stack = Gtk.Stack(transition_type=Gtk.StackTransitionType.NONE)
        switcher = Gtk.StackSwitcher(stack=self.stack, halign=Gtk.Align.CENTER)
        content.pack_start(switcher, False, False, 0)
        content.pack_start(self.stack, True, True, 0)
        self.stack.add_titled(self._pairing_page(), "pair", "Connection")
        self.stack.add_titled(self._preferences_page(), "settings", "Preferences")
        self.stack.add_titled(self._guide_page(), "guide", "How to use")
        self.refresh_addresses()

    def _pairing_page(self):
        page = vertical(12)
        page.pack_start(label("Pair your phone", "title"), False, False, 0)
        page.pack_start(label("Join the same Wi-Fi or your phone’s hotspot, then scan this code in Levitate.", "secondary"), False, False, 0)
        self.qr_image = Gtk.Image()
        qr_frame = Gtk.EventBox()
        qr_frame.get_style_context().add_class("qr")
        qr_frame.add(self.qr_image)
        page.pack_start(qr_frame, False, False, 4)
        self.addresses = Gtk.ComboBoxText()
        self.addresses.connect("changed", lambda _: self.refresh_qr())
        self.addresses.set_tooltip_text("Choose the network shared with your phone")
        page.pack_start(self.addresses, False, False, 0)
        actions = Gtk.Box(spacing=8)
        actions.pack_start(button("Copy pairing link", self._copy_pairing), True, True, 0)
        actions.pack_start(button("New QR", self._new_pairing), False, False, 0)
        actions.pack_start(button("Refresh networks", lambda _: self.refresh_addresses()), False, False, 0)
        page.pack_start(actions, False, False, 0)
        page.pack_start(Gtk.Separator(), False, False, 8)
        self.connection_label = label("Waiting for your phone", "status")
        self.input_label = label("Virtual input ready · Wayland and X11", "secondary")
        page.pack_start(self.connection_label, False, False, 0)
        page.pack_start(self.input_label, False, False, 0)
        self.pause_button = button("Pause input", self._toggle_pause)
        page.pack_start(self.pause_button, False, False, 0)
        page.pack_start(label("Closing this window keeps Levitate in the tray. Quit from the tray to stop the connection.", "secondary"), False, False, 4)
        return page

    def _preferences_page(self):
        page = vertical(16)
        page.pack_start(label("Make it feel right", "title"), False, False, 0)
        page.pack_start(label("Workspace shortcuts", "status"), False, False, 8)
        page.pack_start(label("Levitate sends keyboard shortcuts equivalent to changing workspaces. Match these to your desktop’s bindings.", "secondary"), False, False, 0)
        self.shortcut_entries = {}
        for key, title in (("workspace_left", "Previous workspace"), ("workspace_right", "Next workspace")):
            page.pack_start(label(title), False, False, 0)
            entry = Gtk.Entry(text=self.app.settings[key])
            entry.set_tooltip_text("META, CTRL, ALT, SHIFT + LEFT, RIGHT, PAGEUP, PAGEDOWN, F1…F10")
            self.shortcut_entries[key] = entry
            page.pack_start(entry, False, False, 0)
        self.settings_message = label("GNOME default: META+PAGEUP / META+PAGEDOWN", "secondary")
        page.pack_start(button("Save shortcuts", self._save_shortcuts, "suggested-action"), False, False, 0)
        page.pack_start(self.settings_message, False, False, 0)
        theme = Gtk.CheckButton(label="Use dark theme")
        theme.set_active(Gtk.Settings.get_default().get_property("gtk-application-prefer-dark-theme"))
        theme.connect("toggled", self._change_theme)
        page.pack_start(theme, False, False, 8)
        page.pack_start(label("Input device access", "status"), False, False, 8)
        page.pack_start(label("If input is unavailable, run the included setup-input.sh once, then sign out and back in. Levitate itself runs as your normal user.", "secondary"), False, False, 0)
        page.pack_start(button("Disconnect phone", lambda _: self.app.server.disconnect()), False, False, 8)
        return page

    def _guide_page(self):
        page = vertical(16)
        page.pack_start(label("A little movement goes a long way", "title"), False, False, 0)
        instructions = (
            ("Point", "Hold the screen facing up with the phone’s top toward your laptop. Tap Recenter, then aim left, right, up or down."),
            ("Switch workspaces", "Roll the left edge upward. Sweep the phone left or right; each deliberate sweep changes one workspace."),
            ("Scroll", "Roll the right edge upward. Tilt the phone’s top up or down to scroll. Return screen-up to move the pointer."),
            ("Click and drag", "Tap Left click or Right click. Hold Left click while moving to drag. Use the touchpad if you want a steadier pointer."),
            ("Pause and recenter", "Pause before putting the phone down. Recenter whenever your comfortable pointing direction changes."),
        )
        for title, description in instructions:
            page.pack_start(label(title, "status"), False, False, 4)
            page.pack_start(label(description, "secondary"), False, False, 0)
        return page

    def refresh_addresses(self):
        self.addresses.remove_all()
        addresses = network_addresses()
        addresses.sort(key=lambda entry: (entry[0].startswith(("vir", "docker", "br-")), entry[0]))
        for interface, address in addresses:
            self.addresses.append(address, f"{address} · {interface}")
        if addresses:
            self.addresses.set_active(0)
        else:
            self.connection_label.set_text("Connect your laptop to Wi-Fi, then refresh networks.")
            self.qr_image.clear()

    def refresh_qr(self):
        address = self.addresses.get_active_id()
        if not address:
            return
        # Preserve the QR quiet zone; never theme or recolor its modules.
        code = qrcode.QRCode(box_size=4, border=4, error_correction=qrcode.constants.ERROR_CORRECT_M)
        code.add_data(self.app.pairing.uri(address))
        code.make(fit=True)
        stream = io.BytesIO()
        code.make_image(fill_color="black", back_color="white").save(stream, format="PNG")
        loader = GdkPixbuf.PixbufLoader.new_with_type("png")
        loader.write(stream.getvalue())
        loader.close()
        self.qr_image.set_from_pixbuf(loader.get_pixbuf())

    def _copy_pairing(self, _):
        address = self.addresses.get_active_id()
        if address:
            Gtk.Clipboard.get(Gdk.SELECTION_CLIPBOARD).set_text(self.app.pairing.uri(address), -1)
            self.connection_label.set_text("Pairing link copied · paste it in the phone app")

    def _new_pairing(self, _):
        self.app.server.disconnect()
        self.app.pairing.rotate()
        self.refresh_qr()
        self.connection_label.set_text("New pairing code ready · previous links revoked")

    def update_status(self, name):
        self.connection_label.set_text(f"Connected to {name}" if name else "Waiting for your phone")
        ready = self.app.target.ready
        self.input_label.set_text("Virtual input ready · Wayland and X11" if ready else "Input unavailable · see Preferences for setup")
        self.pause_button.set_sensitive(ready)

    def _toggle_pause(self, _):
        self.app.toggle_pause()

    def update_pause(self):
        self.pause_button.set_label("Resume input" if self.app.target.paused else "Pause input")

    def _save_shortcuts(self, _):
        try:
            values = {key: entry.get_text().upper() for key, entry in self.shortcut_entries.items()}
            for value in values.values():
                shortcut_keys(value)
            self.app.settings.update(values)
            save_settings(self.app.settings)
            self.settings_message.set_text("Workspace shortcuts saved")
        except (ValueError, OSError) as error:
            self.settings_message.set_text(str(error))

    def _change_theme(self, widget):
        self.app.settings["dark_theme"] = widget.get_active()
        apply_styles(self.app.settings)
        save_settings(self.app.settings)

    def _hide_to_tray(self, *_):
        if self.app.indicator:
            self.hide()
            return True
        self.app.quit()
        return False

    def _show_about(self, _):
        dialog = Gtk.AboutDialog(transient_for=self, modal=True, program_name="Levitate",
                                 version="1.0.0", comments="Your phone. A little more freedom.",
                                 authors=["Uzair Mughal"], website="https://uzair.is-a.dev",
                                 website_label="uzair.is-a.dev", license_type=Gtk.License.MIT_X11)
        dialog.set_logo(GdkPixbuf.Pixbuf.new_from_file_at_scale(str(ASSETS / "levitate.svg"), 80, 80, True))
        dialog.run()
        dialog.destroy()


_STYLE_PROVIDER = None


def apply_styles(settings):
    global _STYLE_PROVIDER
    gtk_settings = Gtk.Settings.get_default()
    dark = settings.get("dark_theme")
    if dark is None:
        dark = (gtk_settings.get_property("gtk-application-prefer-dark-theme")
                or "dark" in gtk_settings.get_property("gtk-theme-name").lower())
    gtk_settings.set_property("gtk-application-prefer-dark-theme", dark)
    surface, container, foreground, muted, outline = (
        ("#11141b", "#1e2430", "#e1e5f0", "#c3cad8", "#8d96a7") if dark else
        ("#f8f9ff", "#ebeff8", "#191c24", "#444b58", "#747c8b"))
    provider = Gtk.CssProvider()
    palette = f"""
        * {{ font-family: 'Noto Sans', 'DejaVu Sans', sans-serif; }}
        window {{ background-color: {surface}; color: {foreground}; }}
        headerbar {{ background-image: none; background-color: {container}; color: {foreground}; }}
        label {{ color: {foreground}; }}
        .secondary {{ color: {muted}; }}
        button, entry, combobox button {{ background-image: none; background-color: {container}; color: {foreground}; border: 1px solid {outline}; }}
        button:hover {{ background-color: {surface}; }}
        button:checked {{ background-color: #315da8; color: #ffffff; }}
        button:disabled {{ opacity: 0.5; }}
    """
    provider.load_from_data((palette + """
        headerbar { min-height: 42px; }
        .title { font-size: 25px; font-weight: 700; }
        .status { font-size: 16px; font-weight: 600; }
        .secondary { font-size: 14px; }
        button { min-height: 32px; padding: 6px 12px; border-radius: 8px; }
        entry, combobox { min-height: 36px; }
        .suggested-action { background-image: none; background-color: #315da8; color: #ffffff; }
        .qr { background-color: #ffffff; border-radius: 16px; padding: 12px; }
    """).encode())
    if _STYLE_PROVIDER:
        Gtk.StyleContext.remove_provider_for_screen(Gdk.Screen.get_default(), _STYLE_PROVIDER)
    _STYLE_PROVIDER = provider
    Gtk.StyleContext.add_provider_for_screen(Gdk.Screen.get_default(), provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
