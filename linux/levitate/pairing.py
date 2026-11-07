"""QR payload and a locally generated, phone-pinned TLS certificate."""

import datetime
import hashlib
import os
import secrets
import socket
import ssl
from dataclasses import dataclass
from urllib.parse import urlencode

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID

from .config import config_directory


def network_addresses():
    # ioctl avoids DNS and internet access, and includes hotspot interfaces.
    import fcntl
    import struct
    addresses = []
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        for _, interface in socket.if_nameindex():
            if interface == "lo":
                continue
            try:
                result = fcntl.ioctl(probe, 0x8915, struct.pack("256s", interface.encode()[:15]))
                address = socket.inet_ntoa(result[20:24])
                addresses.append((interface, address))
            except OSError:
                continue
    return addresses


def _generate_certificate(certificate_path, key_path):
    private_key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Levitate")])
    now = datetime.datetime.now(datetime.timezone.utc)
    certificate = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                   .public_key(private_key.public_key()).serial_number(x509.random_serial_number())
                   .not_valid_before(now - datetime.timedelta(minutes=5))
                   .not_valid_after(now + datetime.timedelta(days=3650))
                   .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                   .sign(private_key, hashes.SHA256()))
    # Create with private permissions, rather than exposing the key until chmod.
    with os.fdopen(os.open(key_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "wb") as stream:
        os.fchmod(stream.fileno(), 0o600)
        stream.write(private_key.private_bytes(serialization.Encoding.PEM,
                     serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    certificate_path.write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
    certificate_path.chmod(0o600)


@dataclass
class Pairing:
    port: int
    token: str
    fingerprint: str
    context: ssl.SSLContext

    @classmethod
    def create(cls, port):
        directory = config_directory()
        certificate_path, key_path = directory / "certificate.pem", directory / "private-key.pem"
        if not certificate_path.exists() or not key_path.exists():
            _generate_certificate(certificate_path, key_path)
        key_path.chmod(0o600)
        certificate_path.chmod(0o600)
        certificate = x509.load_pem_x509_certificate(certificate_path.read_bytes())
        fingerprint = hashlib.sha256(certificate.public_bytes(serialization.Encoding.DER)).hexdigest()
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(certificate_path, key_path)
        return cls(port, secrets.token_hex(32), fingerprint, context)

    def uri(self, host):
        return "levitate://pair?" + urlencode({"v": 1, "host": host, "port": self.port,
                                             "token": self.token, "fp": self.fingerprint,
                                             "name": socket.gethostname()[:64]})

    def rotate(self):
        self.token = secrets.token_hex(32)
