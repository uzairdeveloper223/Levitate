import hashlib
import json
import os
import socket
import ssl
import tempfile
import threading
import unittest
from unittest.mock import patch

from levitate.pairing import Pairing
from levitate.protocol import read_message
from levitate.server import CompanionServer
from test_protocol import RecordingInput


class ServerTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.environment = patch.dict(os.environ, {"XDG_CONFIG_HOME": self.directory.name})
        self.environment.start()
        self.pairing = Pairing.create(0)
        self.target = RecordingInput()
        self.disconnected = threading.Event()
        self.server = CompanionServer(self.pairing, self.target,
                                      lambda name: self.disconnected.set() if name is None else None)
        self.server.start()

    def tearDown(self):
        self.server.stop()
        self.environment.stop()
        self.directory.cleanup()

    def open_tls(self):
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        raw = socket.create_connection(("127.0.0.1", self.server.listener.getsockname()[1]), timeout=3)
        secure = context.wrap_socket(raw, server_hostname="Levitate")
        actual = hashlib.sha256(secure.getpeercert(binary_form=True)).hexdigest()
        self.assertEqual(actual, self.pairing.fingerprint)
        return secure

    def connect(self, token, version=1):
        secure = self.open_tls()
        secure.sendall((json.dumps({"type": "hello", "v": version, "token": token, "name": "Test phone"}) + "\n").encode())
        return secure

    def test_tls_pairing_input_and_disconnect_release(self):
        with self.connect(self.pairing.token) as client, client.makefile("rb") as stream:
            self.assertTrue(read_message(stream)["input_ready"])
            client.sendall(b'{"type":"button","button":"left","down":true}\n'
                           b'{"type":"move","x":12,"y":-6}\n'
                           b'{"type":"ping","id":17}\n')
            self.assertEqual(read_message(stream)["id"], 17)
            self.assertIn(("move", 12, -6), self.target.events)
        self.assertTrue(self.disconnected.wait(3))
        self.assertEqual(self.target.events[-1], ("release",))

    def test_wrong_and_revoked_tokens_cannot_control_input(self):
        old_token = self.pairing.token
        self.pairing.rotate()
        with self.connect(old_token) as client, client.makefile("rb") as stream:
            with self.assertRaises(EOFError):
                read_message(stream)
        self.assertTrue(self.disconnected.wait(3))
        self.assertEqual(self.target.events, [("release",)])

    def test_boolean_protocol_version_is_rejected(self):
        with self.connect(self.pairing.token, version=True) as client, client.makefile("rb") as stream:
            with self.assertRaises(EOFError):
                read_message(stream)
        self.assertTrue(self.disconnected.wait(3))
        self.assertEqual(self.target.events, [("release",)])

    def test_trickled_partial_hello_has_total_authentication_deadline(self):
        done = threading.Event()
        with self.open_tls() as client:
            client.sendall(b'{"type":"hello"')

            def trickle():
                while not done.wait(0.2):
                    try:
                        client.sendall(b' ')
                    except OSError:
                        return

            writer = threading.Thread(target=trickle, daemon=True)
            writer.start()
            try:
                self.assertTrue(self.disconnected.wait(6), "Partial authentication was not terminated")
            finally:
                done.set()
                writer.join(1)
        self.assertEqual(self.target.events, [("release",)])

    def test_nested_json_disconnects_peer_without_killing_listener(self):
        with self.connect(self.pairing.token) as client, client.makefile("rb") as stream:
            read_message(stream)
            client.sendall(b'[' * 1000 + b'0' + b']' * 1000 + b'\n')
            with self.assertRaises(EOFError):
                read_message(stream)
        self.assertTrue(self.disconnected.wait(3))
        with self.connect(self.pairing.token) as client, client.makefile("rb") as stream:
            self.assertEqual(read_message(stream)["type"], "welcome")

    def test_disconnect_revokes_commands_already_in_read_buffer(self):
        original = self.target.button

        def disconnect_on_button(name, down):
            original(name, down)
            self.server.disconnect()

        with patch.object(self.target, "button", side_effect=disconnect_on_button):
            with self.connect(self.pairing.token) as client, client.makefile("rb") as stream:
                read_message(stream)
                client.sendall(b'{"type":"button","button":"left","down":true}\n'
                               b'{"type":"move","x":99,"y":99}\n')
                with self.assertRaises(EOFError):
                    read_message(stream)
            self.assertTrue(self.disconnected.wait(3))
        self.assertIn(("button", "left", True), self.target.events)
        self.assertNotIn(("move", 99, 99), self.target.events)


if __name__ == "__main__":
    unittest.main()
