# Levitate local protocol v1

## Pairing

The QR encodes `levitate://pair?v=1&host=<IPv4>&port=<port>&token=<64 hex>&fp=<64 hex>&name=<hostname>`.

- `token`: 256 random bits, generated for each companion process. New QR rotates it and disconnects the active phone. Restarting invalidates old links.
- `fp`: SHA-256 of the DER-encoded companion certificate. Android pins this certificate and checks its validity before sending the token.
- The P-256 private key and certificate are stored under `$XDG_CONFIG_HOME/levitate/` (normally `~/.config/levitate/`) with private file permissions. Credentials are never part of the repository.
- Android accepts local IPv4 addresses only. The QR is a control credential: anyone with it and network access can control the laptop.

## Transport

TCP with `TCP_NODELAY`, wrapped in TLS 1.2 or newer. UTF-8 JSON objects separated by newlines. Maximum incoming frame is 2048 bytes, including newline. No compression, account, discovery broadcast, Internet service or telemetry.

One active phone has exclusive control. The server handles one connection at a time, with a five-second total TLS-and-hello authentication deadline, a five-second read timeout after authentication, and a bounded listen backlog. Input is capped at 240 messages per second. Android validates the welcome before enabling controls. Its outgoing queue is bounded; overload disconnects instead of losing button-release messages. Disconnect revokes the session before releasing input, so commands buffered by the old session cannot execute afterward.

Client hello:

```json
{"type":"hello","v":1,"token":"<QR token>","name":"<device model>"}
```

Server response:

```json
{"type":"welcome","v":1,"input_ready":true}
```

## Commands

| Type | Fields | Meaning |
| --- | --- | --- |
| `move` | `x`, `y`: finite numbers, ±500 maximum | Relative pointer units; fractional movement accumulated on Linux |
| `scroll` | `x`, `y`: finite numbers, ±20 maximum | Fractional wheel steps, accumulated before injection; positive Y scrolls upward |
| `button` | `button`: `left`, `right`, `middle`; `down`: boolean | Button press/release |
| `workspace` | `direction`: integer `-1` or `1` | Configured previous/next workspace shortcut |
| `media` | `action`: `play`, `previous`, `next`, `volume_up`, `volume_down` | Allowlisted media-key tap |
| `release` | — | Release held mouse buttons and fractional state |
| `ping` | `id`: monotonic millisecond timestamp | Heartbeat and measured round-trip latency |

Pong includes `id`, `paused`, and `input_ready`. Android sends one heartbeat per second. Socket EOF, malformed frames, idle timeout, app backgrounding, tray pause, and explicit disconnect release held buttons. Pointer and workspace commands are ignored while the companion is paused. There is no remote shell, arbitrary key injection, file transfer, clipboard sharing, or executable command field.
