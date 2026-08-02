#!/bin/sh
# eProcurement eSigner - macOS native messaging host.
#
# Chrome execs this with a near-empty environment, so nothing here may rely on
# a login shell's PATH. install.sh bakes the absolute java path into
# esigner.properties; that value wins, with a best-effort search as fallback.

HOME_DIR="${HOME}/Library/Application Support/eProcSigner"
CONF="${HOME_DIR}/esigner.properties"

read_conf() {
    [ -r "$CONF" ] || return 1
    val=$(sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$CONF" | head -1)
    [ -n "$val" ] && printf '%s' "$val"
}

JAVA_BIN="$ESIGNER_JAVA"
[ -n "$JAVA_BIN" ] || JAVA_BIN=$(read_conf 'java.bin')
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    for candidate in \
        /opt/homebrew/opt/openjdk/bin/java \
        /usr/local/opt/openjdk/bin/java \
        /usr/bin/java \
        "$(command -v java 2>/dev/null)"
    do
        [ -n "$candidate" ] && [ -x "$candidate" ] && JAVA_BIN="$candidate" && break
    done
fi

if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    # Nothing to talk to Chrome with; report on stderr and fail.
    echo "eSigner: no Java runtime found. Set java.bin in $CONF" >&2
    exit 1
fi

# The vendor PKCS#11 library may be x86_64-only. If install.sh detected that,
# it recorded arch=x86_64 and we relaunch the JVM under Rosetta.
ARCH_PREFIX=""
if [ "$(read_conf 'arch')" = "x86_64" ] && [ "$(uname -m)" = "arm64" ]; then
    ARCH_PREFIX="arch -x86_64"
fi

# headless=false: the certificate chooser and PIN prompt are Swing dialogs.
exec $ARCH_PREFIX "$JAVA_BIN" \
    -Djava.awt.headless=false \
    -Dapple.awt.UIElement=false \
    -cp "${HOME_DIR}/esigner-mac.jar:${HOME_DIR}/eSigner.jar" \
    com.eproc.mac.Launcher "$@"
