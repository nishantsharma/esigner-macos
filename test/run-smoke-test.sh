#!/bin/bash
# Exercises the real eSigner classes against a throwaway software token
# (SoftHSM), so the macOS path can be validated without touching a DSC.
#
# Requires: brew install softhsm opensc
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
MODULE=/opt/homebrew/lib/softhsm/libsofthsm2.so
[ -r "$MODULE" ] || MODULE=/usr/local/lib/softhsm/libsofthsm2.so

if [ ! -d "${HERE}/tokens" ] || [ -z "$(ls -A "${HERE}/tokens" 2>/dev/null)" ]; then
    echo "No test token yet - creating one."
    "${HERE}/setup-softhsm.sh"
fi

export SOFTHSM2_CONF="${HERE}/softhsm2.conf"
export ESIGNER_PIN=12345678

[ -d "${ROOT}/build" ] || { echo "Run ./install.sh first - it compiles src/." >&2; exit 1; }

javac -nowarn -cp "${ROOT}/build:${ROOT}/lib/eSigner.jar" -d "${HERE}" \
    "${HERE}/HostSmokeTest.java"

exec java -Djava.awt.headless=true \
    -Designer.pkcs11.library="$MODULE" \
    -cp "${HERE}:${ROOT}/build:${ROOT}/lib/eSigner.jar" \
    HostSmokeTest
