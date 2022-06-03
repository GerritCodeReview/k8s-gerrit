# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from OpenSSL import crypto


class MockSSLKeyPair:
    def __init__(self, common_name, subject_alt_name):
        self.common_name = common_name
        self.subject_alt_name = subject_alt_name
        self.cert = None
        self.key = None

        self._create_keypair()

    def _create_keypair(self):
        self.key = crypto.PKey()
        self.key.generate_key(crypto.TYPE_RSA, 2048)

        self.cert = crypto.X509()
        self.cert.set_version(2)
        self.cert.get_subject().O = "Gerrit"
        self.cert.get_subject().CN = self.common_name
        san = f"DNS:{self.subject_alt_name}"
        self.cert.add_extensions(
            [crypto.X509Extension(b"subjectAltName", False, san.encode())]
        )
        self.cert.gmtime_adj_notBefore(0)
        self.cert.gmtime_adj_notAfter(10 * 365 * 24 * 60 * 60)
        self.cert.set_issuer(self.cert.get_subject())
        self.cert.set_pubkey(self.key)
        self.cert.sign(self.key, "sha256")

    def get_key(self):
        return crypto.dump_privatekey(crypto.FILETYPE_PEM, self.key)

    def get_cert(self):
        return crypto.dump_certificate(crypto.FILETYPE_PEM, self.cert)
