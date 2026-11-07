#!/bin/sh
# Run once with sudo; the application itself must run as the desktop user.
set -eu
if [ "$(id -u)" -ne 0 ]; then
    echo "Run: sudo sh linux/setup-input.sh"
    exit 1
fi
groupadd -f levitate
if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != root ]; then
    usermod -aG levitate "$SUDO_USER"
fi
install -d /etc/udev/rules.d /etc/modules-load.d
cat > /etc/udev/rules.d/70-levitate.rules <<'EOF'
KERNEL=="uinput", SUBSYSTEM=="misc", GROUP="levitate", MODE="0660", TAG+="uaccess", OPTIONS+="static_node=uinput"
EOF
echo uinput > /etc/modules-load.d/levitate.conf
modprobe uinput
udevadm control --reload-rules
udevadm trigger --action=change --subsystem-match=misc
echo "Input access configured. Sign out and back in, then start Levitate as your normal user."
