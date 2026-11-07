#!/bin/sh
# User-local install, no privileged application runtime.
set -eu
source_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
data_home="${XDG_DATA_HOME:-$HOME/.local/share}"
destination="$data_home/levitate"
install -d "$destination/levitate" "$destination/assets" "$HOME/.local/bin" "$data_home/applications" "$data_home/icons/hicolor/scalable/apps" "$data_home/icons/hicolor/symbolic/apps" "$data_home/metainfo"
cp "$source_dir"/levitate/*.py "$destination/levitate/"
cp "$source_dir"/assets/*.svg "$destination/assets/"
install -m 755 "$source_dir/levitate-run" "$destination/levitate-run"
ln -sfn "$destination/levitate-run" "$HOME/.local/bin/levitate"
cp "$source_dir/assets/levitate.svg" "$data_home/icons/hicolor/scalable/apps/dev.uzair.Levitate.svg"
cp "$source_dir/assets/levitate-symbolic.svg" "$data_home/icons/hicolor/symbolic/apps/dev.uzair.Levitate-symbolic.svg"
cp "$source_dir/assets/dev.uzair.Levitate.metainfo.xml" "$data_home/metainfo/"
cat > "$data_home/applications/dev.uzair.Levitate.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Levitate
Comment=Control your Linux desktop by moving your phone
Exec="$destination/levitate-run"
Icon=dev.uzair.Levitate
Terminal=false
Categories=Utility;GTK;
StartupWMClass=Levitate
EOF
if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$data_home/applications"
fi
echo "Installed Levitate. Launch it from your app menu or ~/.local/bin/levitate."
