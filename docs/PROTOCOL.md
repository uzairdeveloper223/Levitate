# Levitate local protocol v1

These are the messages between the Android app and Linux companion. If you're changing the networking code, keep both sides compatible with this format.

## Pairing

The QR encodes `levitate://pair?v=1&host=<IPv4>&port=<port>&token=<64 hex>&fp=<64 hex>&name=<hostname>`.

- `token`: 256 random bits, generated when the companion starts. New QR replaces it and disconnects the phone. Restarting also invalidates old links.
- `fp`: SHA-256 of the DER-encoded companion certificate. Android pins this certificate and checks its validity before sending the token.
- The P-256 private key and certificate live in `$XDG_CONFIG_HOME/levitate/`, normally `~/.config/levitate/`, with owner-only permissions. They stay on the laptop.
- Android accepts local IPv4 addresses only. The QR is a control credential: anyone with it and network access can control the laptop.

## Transport

I use TCP with `TCP_NODELAY` and TLS 1.2 or newer. Each message is a UTF-8 JSON object followed by a newline. Incoming messages can be at most 2048 bytes, including that newline. Pairing and input stay on the local network.

One phone controls the laptop at a time. TLS and the hello message must finish within five seconds. Reads time out after five seconds once paired, and input is capped at 240 messages per second. Android checks the welcome before enabling controls. If its outgoing queue fills, it disconnects so Linux can release the mouse buttons. Disconnect invalidates the old session before releasing input, including commands already buffered for that session.

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

Pong includes `id`, `paused`, and `input_ready`. Android sends one heartbeat per second. Closing the connection, sending bad data, a read timeout, backgrounding the app, pausing from the tray or disconnecting releases held buttons. While paused, the companion ignores pointer and workspace commands.

The command set is limited to the input actions above. It can't run a shell, type arbitrary text, transfer files or share the clipboard.
