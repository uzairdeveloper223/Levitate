#!/bin/sh
# Dependency-aware AppImage: use distro Python/GTK rather than duplicating a runtime.
set -eu
umask 022
if ! command -v appimagetool >/dev/null 2>&1; then
    echo "Install appimagetool first: https://github.com/AppImage/appimagetool/releases"
    exit 1
fi
root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
install -d "$stage/levitate" "$stage/assets" "$stage/usr/share/metainfo" "$stage/usr/share/applications" "$root/dist"
cp "$root/linux/levitate/"*.py "$stage/levitate/"
cp "$root/linux/assets/"*.svg "$stage/assets/"
cp "$root/linux/assets/levitate.png" "$stage/dev.uzair.Levitate.png"
ln -s dev.uzair.Levitate.png "$stage/.DirIcon"
cp "$root/linux/assets/dev.uzair.Levitate.metainfo.xml" "$stage/usr/share/metainfo/dev.uzair.Levitate.appdata.xml"
chmod 755 "$stage"
cp "$root/LICENSE" "$stage/LICENSE"
cat > "$stage/AppRun" <<'EOF'
#!/bin/sh
set -eu
cd "$(dirname "$(readlink -f "$0")")"
exec /usr/bin/python3 -m levitate "$@"
EOF
chmod 755 "$stage/AppRun"
cat > "$stage/dev.uzair.Levitate.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=Levitate
Exec=AppRun
Icon=dev.uzair.Levitate
Categories=Utility;GTK;
Comment=Control your Linux desktop by moving your phone
Terminal=false
EOF
cp "$stage/dev.uzair.Levitate.desktop" "$stage/usr/share/applications/"
ARCH="$(uname -m)" appimagetool "$stage" "$root/dist/Levitate-1.0.0-$(uname -m).AppImage"
echo "Created a dependency-aware AppImage. Host Python/GTK and uinput setup are required."
