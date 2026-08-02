#!/bin/bash
# Creates a throwaway SoftHSM token with a self-signed key pair, so the whole
# signing path can be exercised without a real DSC (and without risking the
# retry counter on one).
#
# Requires: brew install softhsm openssl
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PIN=12345678
SO_PIN=1234567890
LABEL=eProcTest

command -v softhsm2-util >/dev/null || { echo "need: brew install softhsm" >&2; exit 1; }

rm -rf "${HERE}/tokens"; mkdir -p "${HERE}/tokens"
cat > "${HERE}/softhsm2.conf" <<CONF
directories.tokendir = ${HERE}/tokens
objectstore.backend = file
log.level = ERROR
CONF
export SOFTHSM2_CONF="${HERE}/softhsm2.conf"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -keyout "${TMP}/key.pem" -out "${TMP}/cert.pem" \
    -subj "/C=IN/O=Test/CN=eProc macOS host test" 2>/dev/null
openssl pkcs8 -topk8 -nocrypt -in "${TMP}/key.pem" -outform DER -out "${TMP}/key.der"
openssl x509 -in "${TMP}/cert.pem" -outform DER -out "${TMP}/cert.der"

softhsm2-util --init-token --free --label "$LABEL" --so-pin "$SO_PIN" --pin "$PIN" >/dev/null

MODULE=/opt/homebrew/lib/softhsm/libsofthsm2.so
[ -r "$MODULE" ] || MODULE=/usr/local/lib/softhsm/libsofthsm2.so

pkcs11-tool --module "$MODULE" --login --pin "$PIN" \
    --write-object "${TMP}/key.der"  --type privkey --id 01 --label testkey >/dev/null
pkcs11-tool --module "$MODULE" --login --pin "$PIN" \
    --write-object "${TMP}/cert.der" --type cert    --id 01 --label testkey >/dev/null

echo "Test token ready (label=${LABEL}, PIN=${PIN}). Now: ./test/run-smoke-test.sh"
