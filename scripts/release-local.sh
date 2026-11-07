#!/bin/sh
# Build, check, sign and package locally. Never upload signing material.
set -eu
umask 077
root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ]; then
    echo "Set ANDROID_HOME to your Android SDK directory." >&2
    exit 1
fi
tools="$sdk/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
for tool in zipalign apksigner; do
    if [ ! -x "$tools/$tool" ]; then
        echo "Missing $tools/$tool. Install Android build-tools 35.0.0." >&2
        exit 1
    fi
done
command -v appimagetool >/dev/null 2>&1 || {
    echo "Install appimagetool or add it to PATH." >&2
    exit 1
}
signing="${LEVITATE_SIGNING_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/levitate-signing}"
install -d -m 700 "$signing" "$root/dist"
key="$signing/release.p12"
password="$signing/release.password"
if [ ! -f "$key" ]; then
    if [ -e "$password" ]; then
        echo "Signing password exists without its key. Restore the key before continuing." >&2
        exit 1
    fi
    python3 -c 'import secrets; print(secrets.token_urlsafe(48))' > "$password"
    keytool -genkeypair -keystore "$key" -storetype PKCS12 -alias levitate \
        -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=Uzair Mughal, O=Levitate" \
        -storepass:file "$password" -keypass:file "$password"
    echo "Created release signing material in $signing. Back up this directory privately."
fi
chmod 600 "$key" "$password"
PYTHONPATH="$root/linux" /usr/bin/python3 -m unittest discover -s "$root/linux/tests" -v
(
    cd "$root/android"
    ./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --no-daemon
)
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
apk="$root/dist/Levitate-1.0.0-android.apk"
"$tools/zipalign" -P 16 -f 4 "$root/android/app/build/outputs/apk/release/app-release-unsigned.apk" "$stage/aligned.apk"
"$tools/apksigner" sign --ks "$key" --ks-key-alias levitate \
    --ks-pass "file:$password" --key-pass stdin \
    --v4-signing-enabled false --out "$apk" "$stage/aligned.apk" < "$password"
"$tools/apksigner" verify --verbose --print-certs "$apk" > "$root/dist/ANDROID-SIGNING-CERTIFICATE.txt"
"$tools/zipalign" -c -P 16 4 "$apk"
sh "$root/linux/package.sh"
sh "$root/linux/package-appimage.sh"
(
    cd "$root/dist"
    sha256sum Levitate-1.0.0-android.apk "Levitate-1.0.0-$(uname -m).AppImage" \
        levitate_1.0.0_all.deb levitate-linux-1.0.0.tar.gz ANDROID-SIGNING-CERTIFICATE.txt > SHA256SUMS
    sha256sum -c SHA256SUMS
)
chmod 644 "$apk" "$root/dist/ANDROID-SIGNING-CERTIFICATE.txt" "$root/dist/SHA256SUMS" \
    "$root/dist/levitate_1.0.0_all.deb" "$root/dist/levitate-linux-1.0.0.tar.gz"
chmod 755 "$root/dist/Levitate-1.0.0-$(uname -m).AppImage"
echo "Verified v1.0.0 release assets are ready in $root/dist."
