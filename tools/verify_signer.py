#!/usr/bin/env python3
"""Verify that a release APK is signed with this project's key.

Android refuses to install an update whose signing certificate differs from the
installed one. Two consecutive releases signed with different keys therefore force
an uninstall, which wipes the user's dashboards and settings. This script makes
that failure visible in CI instead of on a user's phone.

Usage:
    python3 tools/verify_signer.py app/build/outputs/apk/release/aa-torque.apk

Exits 0 when the signer certificate matches EXPECTED_SHA256, 1 otherwise.
"""

import hashlib
import pathlib
import struct
import sys

# SHA-256 of the signer certificate DER for this project's key. The key lives in the
# DEBUG_KEYSTORE secret and is restored to ~/.android/debug.keystore by CI. Rotating the
# key changes this constant and breaks in-place updates once, so do it deliberately.
EXPECTED_SHA256 = "d74e9800418c65d749bea8151dd6c07e318997b21fb81bae09d4aa71e938a27e"

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0


def signer_certificates(apk: pathlib.Path) -> dict[str, bytes]:
    """Return {scheme: certificate DER} for every signing scheme block in the APK."""
    data = apk.read_bytes()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise SystemExit(f"{apk}: not a zip file")
    central_dir_offset = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[central_dir_offset - 16:central_dir_offset] != APK_SIG_BLOCK_MAGIC:
        raise SystemExit(f"{apk}: no APK Signing Block (unsigned, or v1 only)")

    block_size = struct.unpack_from("<Q", data, central_dir_offset - 24)[0]
    # Layout: [block size][pairs][block size][magic]. The pairs start after the first size field.
    position = central_dir_offset - block_size
    end = central_dir_offset - 24

    found = {}
    while position < end:
        pair_length = struct.unpack_from("<Q", data, position)[0]
        pair_id = struct.unpack_from("<I", data, position + 8)[0]
        value = data[position + 12: position + 8 + pair_length]
        if pair_id in (V2_BLOCK_ID, V3_BLOCK_ID):
            scheme = "v2" if pair_id == V2_BLOCK_ID else "v3"
            try:
                found[scheme] = first_certificate(value)
            except struct.error:
                pass
        position += 8 + pair_length
    return found


def first_certificate(value: bytes) -> bytes:
    """Walk signer -> signed data -> certificates and return the first certificate DER."""
    signer_length = struct.unpack_from("<I", value, 4)[0]
    signer = value[8:8 + signer_length]
    signed_data_length = struct.unpack_from("<I", signer, 0)[0]
    signed_data = signer[4:4 + signed_data_length]
    # signed data: digests, then certificates, then attributes
    offset = 4 + struct.unpack_from("<I", signed_data, 0)[0]
    certificate_length = struct.unpack_from("<I", signed_data, offset)[0]
    return signed_data[offset + 4: offset + 4 + certificate_length]


def main() -> int:
    if len(sys.argv) != 2:
        print((__doc__ or "").strip())
        return 2
    apk = pathlib.Path(sys.argv[1])
    if not apk.is_file():
        print(f"missing APK: {apk}")
        return 2

    certificates = signer_certificates(apk)
    if not certificates:
        print(f"{apk}: no v2/v3 signer certificate found")
        return 1

    ok = True
    for scheme, certificate in sorted(certificates.items()):
        digest = hashlib.sha256(certificate).hexdigest()
        matches = digest == EXPECTED_SHA256
        ok = ok and matches
        print(f"{apk.name}: {scheme} signer sha256 {digest} "
              f"({'matches' if matches else 'MISMATCH'})")

    if not ok:
        print(f"expected {EXPECTED_SHA256}")
        print("This APK cannot update an earlier release in place. Check that the "
              "DEBUG_KEYSTORE secret holds this project's keystore.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
