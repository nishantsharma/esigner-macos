#!/bin/bash
# Same smoke test as run-smoke-test.sh, but against the REAL DSC token using
# whatever PKCS#11 module install.sh recorded. A Swing dialog asks for the PIN.
#
# The PIN is never stored and never guessed: a wrong entry counts against the
# token's retry limit, so this prompts you and prompts you only once.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
CONF="${HOME}/Library/Application Support/eProcSigner/esigner.properties"

MODULE=$(sed -n 's/^[[:space:]]*pkcs11.library[[:space:]]*=[[:space:]]*//p' "$CONF" | head -1)
if [ -z "$MODULE" ] || [ ! -r "$MODULE" ]; then
    echo "No pkcs11.library in $CONF - run ./install.sh first" >&2
    exit 1
fi
echo "module: $MODULE"

JAVA_BIN=$(sed -n 's/^[[:space:]]*java.bin[[:space:]]*=[[:space:]]*//p' "$CONF" | head -1)
[ -x "$JAVA_BIN" ] || JAVA_BIN=java

"$(dirname "$JAVA_BIN")/javac" -nowarn \
    -cp "${ROOT}/build:${ROOT}/lib/eSigner.jar" -d "${HERE}" "${HERE}/HostSmokeTest.java"

exec "$JAVA_BIN" -Djava.awt.headless=false \
    -Designer.pkcs11.library="$MODULE" \
    -cp "${HERE}:${ROOT}/build:${ROOT}/lib/eSigner.jar" \
    HostSmokeTest
