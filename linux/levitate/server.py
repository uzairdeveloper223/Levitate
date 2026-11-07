"""A single paired phone; TLS, bounded frames and automatic input release."""

import hmac
import json
import logging
import socket
import ssl
import threading
import time

from .protocol import dispatch, read_message

LOGGER = logging.getLogger(__name__)


class CompanionServer:
    def __init__(self, pairing, target, on_status):
        self.pairing = pairing
        self.target = target
        self.on_status = on_status
        self.listener = None
        self.client = None
        self.stopped = threading.Event()
        self.session_lock = threading.RLock()
        self.generation = 0
        self.packets = 0

    def start(self):
        self.listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.listener.bind(("0.0.0.0", self.pairing.port))
        self.listener.listen(4)
        self.listener.settimeout(1)
        threading.Thread(target=self._accept_connections, daemon=True, name="levitate-network").start()

    def _accept_connections(self):
        while not self.stopped.is_set():
            try:
                connection, address = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            # One blocking session avoids unbounded threads and gives exclusive control.
            try:
                self._serve_phone(connection, address)
            except (OSError, ssl.SSLError, EOFError, ValueError, TypeError) as error:
                LOGGER.info("Connection ended: %s", type(error).__name__)
            finally:
                connection.close()
                self.client = None
                self.target.release()
                self.on_status(None)

    def _serve_phone(self, connection, address):
        connection.settimeout(5)
        connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        with self.pairing.context.wrap_socket(connection, server_side=True,
                                             do_handshake_on_connect=False) as client:
            with self.session_lock:
                self.client = client
                generation = self.generation
            # A total deadline also stops a peer trickling a partial hello forever.
            deadline = threading.Timer(5, self._close_client, args=(client,))
            deadline.daemon = True
            deadline.start()
            try:
                client.do_handshake()
                with client.makefile("rb") as stream:
                    self._authenticate(client, stream, address, generation)
                    deadline.cancel()
                    self._receive_commands(client, stream, generation)
            finally:
                deadline.cancel()

    def _authenticate(self, client, stream, address, generation):
        hello = read_message(stream)
        token = hello.get("token", "")
        with self.session_lock:
            valid = (generation == self.generation and not self.stopped.is_set()
                     and hello.get("type") == "hello" and type(hello.get("v")) is int
                     and hello["v"] == 1 and isinstance(token, str) and token.isascii()
                     and hmac.compare_digest(token, self.pairing.token))
            if not valid:
                LOGGER.warning("Rejected pairing from %s", address[0])
                raise ValueError("Pairing rejected")
            name = hello.get("name", "Android phone")
            if not isinstance(name, str) or len(name) > 64 or any(ord(c) < 32 for c in name):
                raise ValueError("Invalid device name")
            LOGGER.info("Phone paired from %s", address[0])
            self.on_status(name)
            self._send(client, {"type": "welcome", "v": 1, "input_ready": self.target.ready})

    def _receive_commands(self, client, stream, generation):
        window_start, count = time.monotonic(), 0
        while not self.stopped.is_set():
            message = read_message(stream)
            now = time.monotonic()
            if now - window_start >= 1:
                window_start, count = now, 0
            count += 1
            if count > 240:
                raise ValueError("Input rate exceeded")
            with self.session_lock:
                if generation != self.generation or self.stopped.is_set():
                    raise EOFError
                dispatch(message, self.target)
                self.packets += 1
            if message.get("type") == "ping":
                self._send(client, {"type": "pong", "id": message.get("id"),
                                    "paused": self.target.paused, "input_ready": self.target.ready})

    @staticmethod
    def _send(client, message):
        client.sendall((json.dumps(message, separators=(",", ":")) + "\n").encode())

    @staticmethod
    def _close_client(client):
        try:
            client.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass

    def disconnect(self):
        with self.session_lock:
            self.generation += 1
            if self.client:
                self._close_client(self.client)
            self.target.release()

    def stop(self):
        self.stopped.set()
        self.disconnect()
        if self.listener:
            self.listener.close()
