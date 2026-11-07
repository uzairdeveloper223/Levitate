# Security

## Supported release

Security fixes target the latest stable release, starting with 1.0.0. Report a vulnerability privately through [GitHub's security reporting page](https://github.com/uzairdeveloper223/Levitate/security/advisories/new). Do not put working pairing credentials, private keys, or exploit instructions affecting other users in a public issue.

## Local trust model

Levitate grants the paired phone control of the desktop pointer and a bounded set of mouse, workspace and media actions. Pair only with your own phone and laptop. Anyone who can read a current QR or copied pairing link can authenticate to that companion until you select **New QR** or restart it.

- The QR includes a random 256-bit token and the SHA-256 fingerprint of the laptop's TLS certificate. Android checks that exact certificate and its validity before sending the token. Both ends require TLS 1.2 or newer; there is no plaintext fallback.
- Pairing credentials live in Android memory. Linux generates a new token each launch, and **New QR** revokes the previous token and session. Disconnect revokes the current session and releases held input; the QR remains available for deliberate reconnection.
- Linux accepts one phone session at a time. Authentication has a five-second total deadline. Authenticated traffic is limited to 240 messages per second and 2,048 bytes per frame. Malformed data closes the session. Commands buffered before a disconnect cannot regain control afterward.
- Coordinates must be finite and within bounds; arbitrarily large JSON integers are rejected before numeric conversion. Buttons, media actions and workspace shortcut keys are allowlisted. The protocol cannot execute shell commands, upload files or enter arbitrary keyboard text.
- The desktop application runs as your normal user. The input setup script grants access to `/dev/uinput`, not to physical input-event devices. Access to uinput itself is privileged desktop control; grant it only to trusted local users.
- Private certificate keys and configuration directories are restricted to the owning Linux user. No pairing tokens or private keys are included in release files. Android backups and plaintext network traffic are disabled.

The listener binds IPv4 TCP port 45879 on all interfaces, including any public interface. Keep it on a trusted LAN and restrict inbound connections with your firewall when needed. TLS and tokens prevent unauthenticated control, but a hostile network peer can still disrupt availability of a single-session listener. This is a local remote control, not an Internet-facing service.

## Releases

The Android release APK is optimized, non-debuggable, and signed with a dedicated release key. Release assets include SHA-256 checksums. The signing key and password remain outside this repository and must be backed up by the maintainer to sign future updates. Linux packages use distribution-managed Python and GTK dependencies, including cryptography updates.
