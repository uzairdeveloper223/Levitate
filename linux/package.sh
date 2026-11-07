#!/bin/sh
set -eu
umask 022
root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
install -d "$root/dist"
tar --exclude='__pycache__' --exclude='*.pyc' -czf "$root/dist/levitate-linux-1.0.0.tar.gz" -C "$root" \
    linux/levitate linux/assets linux/levitate-run linux/install.sh linux/setup-input.sh LICENSE README.md THIRD_PARTY.md SECURITY.md
if ! command -v dpkg-deb >/dev/null 2>&1; then
    echo "Created Linux tarball in dist/. dpkg-deb unavailable; Debian packaging skipped."
    exit 0
fi
stage="$(mktemp -d)"
chmod 755 "$stage"
trap 'rm -rf "$stage"' EXIT
install -d "$stage/DEBIAN" "$stage/usr/share/levitate/levitate" "$stage/usr/share/levitate/assets" \
    "$stage/usr/bin" "$stage/usr/share/applications" "$stage/usr/share/icons/hicolor/scalable/apps" \
    "$stage/usr/share/doc/levitate" "$stage/usr/share/metainfo" "$stage/usr/share/icons/hicolor/symbolic/apps"
cp "$root/linux/levitate/"*.py "$stage/usr/share/levitate/levitate/"
cp "$root/linux/assets/"*.svg "$stage/usr/share/levitate/assets/"
install -m 755 "$root/linux/levitate-run" "$stage/usr/share/levitate/levitate-run"
ln -s /usr/share/levitate/levitate-run "$stage/usr/bin/levitate"
cp "$root/linux/assets/levitate.svg" "$stage/usr/share/icons/hicolor/scalable/apps/dev.uzair.Levitate.svg"
cp "$root/linux/assets/levitate-symbolic.svg" "$stage/usr/share/icons/hicolor/symbolic/apps/dev.uzair.Levitate-symbolic.svg"
cp "$root/linux/assets/dev.uzair.Levitate.metainfo.xml" "$stage/usr/share/metainfo/"
cp "$root/LICENSE" "$stage/usr/share/doc/levitate/copyright"
cp "$root/linux/setup-input.sh" "$stage/usr/share/doc/levitate/setup-input.sh"
cat > "$stage/DEBIAN/control" <<'EOF'
Package: levitate
Version: 1.0.0
Section: utils
Priority: optional
Architecture: all
Maintainer: Uzair Mughal <uzairdeveloper223@users.noreply.github.com>
Depends: python3 (>= 3.10), python3-gi, gir1.2-gtk-3.0, python3-cryptography, python3-qrcode, python3-pil
Recommends: gir1.2-ayatanaappindicator3-0.1
Homepage: https://uzair.is-a.dev
Description: Sensor-based Android air mouse for Linux
 Native GTK companion with encrypted local QR pairing and Wayland-compatible input.
EOF
cat > "$stage/usr/share/applications/dev.uzair.Levitate.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=Levitate
Comment=Control your Linux desktop by moving your phone
Exec=levitate
Icon=dev.uzair.Levitate
Terminal=false
Categories=Utility;GTK;
EOF
dpkg-deb --build --root-owner-group "$stage" "$root/dist/levitate_1.0.0_all.deb"
echo "Packages created in dist/. Configure uinput access separately with setup-input.sh."
