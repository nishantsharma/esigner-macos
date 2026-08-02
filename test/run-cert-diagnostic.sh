#!/bin/bash
# Reports every certificate on the token and tries to sign with each one.
#
# Use this when the portal rejects a signature: it tells you whether the
# certificate could sign at all locally, which separates a local problem from a
# portal-side one.
#
# Asks for the PIN once. Never guesses it.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
CONF="${HOME}/Library/Application Support/eProcSigner/esigner.properties"

MODULE=$(sed -n 's/^[[:space:]]*pkcs11.library[[:space:]]*=[[:space:]]*//p' "$CONF" 2>/dev/null | head -1)
if [ -z "${MODULE:-}" ] || [ ! -r "$MODULE" ]; then
    echo "No pkcs11.library in $CONF - run 'make install' first" >&2
    exit 1
fi
echo "module: $MODULE"

JAVA_BIN=$(sed -n 's/^[[:space:]]*java.bin[[:space:]]*=[[:space:]]*//p' "$CONF" | head -1)
[ -x "$JAVA_BIN" ] || JAVA_BIN=java

"$(dirname "$JAVA_BIN")/javac" -nowarn \
    -cp "${ROOT}/build:${ROOT}/lib/eSigner.jar" -d "${HERE}" "${HERE}/CertDiagnostic.java"

exec "$JAVA_BIN" -Djava.awt.headless=false \
    -Designer.pkcs11.library="$MODULE" \
    -cp "${HERE}:${ROOT}/build:${ROOT}/lib/eSigner.jar" \
    CertDiagnostic
