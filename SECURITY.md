# Security

## Supported release

I maintain security fixes for the latest stable release, starting with 1.0.0. If you find a vulnerability, [report it privately on GitHub](https://github.com/uzairdeveloper223/Levitate/security/advisories/new) so I can look into it. Keep working pairing links, private keys and details that could affect other users out of public issues.

## What pairing allows

A paired phone can move your pointer, click, scroll, change workspaces and use media keys. Only pair devices you trust. Anyone with your current QR or copied pairing link can connect if they can reach the laptop. Select New QR or restart the companion to invalidate that link.

- The QR includes a random 256-bit token and the SHA-256 fingerprint of the laptop's TLS certificate. Android checks that exact certificate and its validity before sending the token. Both ends require TLS 1.2 or newer; there is no plaintext fallback.
- Pairing stays in Android memory. Linux creates a new token each time it starts, and New QR invalidates the old token and connection. Disconnect ends the current session and releases held buttons. You can use the same QR to reconnect until you replace it or restart the companion.
- Linux accepts one phone at a time. TLS and the first pairing message must finish within five seconds. After pairing, messages are limited to 240 per second and 2,048 bytes each. Bad data closes the connection. Disconnect also blocks commands that were already waiting in the old session's buffer.
- Coordinates must be finite and within the allowed range. Huge integers are rejected before conversion. Buttons, media keys and workspace shortcuts have a fixed set of allowed values. The protocol can't run shell commands, upload files or type arbitrary text.
- The desktop application runs as your normal user. The input setup script grants access to `/dev/uinput`, not to physical input-event devices. Access to uinput itself is privileged desktop control; grant it only to trusted local users.
- Only your Linux account can read the private key and configuration directory. Release downloads don't contain pairing tokens or private keys. Android backups and plaintext network traffic are disabled.

The companion listens on IPv4 TCP port 45879 on all interfaces, including public ones if your laptop has them. Use a trusted local network and restrict incoming connections with your firewall when needed. An unpaired device can't send input, but it can still interrupt pairing by tying up the single connection listener. I use Levitate on a local network and don't recommend exposing this port to the Internet.

## Releases

I build and sign the release APK locally with a dedicated release key. The APK is optimized and has debugging disabled. Downloads include SHA-256 checksums, and the public Android signing fingerprint is in `ANDROID-SIGNING-CERTIFICATE.txt`.

The signing key and password stay outside the repository. I need that same key for future Android updates. Linux packages use your distribution's Python, GTK and cryptography libraries, so keep those packages updated too.
