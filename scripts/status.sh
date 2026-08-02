#!/bin/bash
# Reports whether an installation is intact and able to sign.
#
# There is no daemon and nothing autostarts: Chrome execs the host on demand and
# it exits with the tab. What has to survive a reboot is the set of files this
# checks for. Invoked by `make status`.
set -uo pipefail

DEST="${HOME}/Library/Application Support/eProcSigner"
SUPPORT="${HOME}/Library/Application Support"
HOST_NAME="com.dxc.eproc.pki"
TESTED_MACOS=26
problems=0

ok()   { printf '  \033[32mok\033[0m    %s\n' "$*"; }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$*"; problems=$((problems + 1)); }
warn() { printf '  \033[33mwarn\033[0m  %s\n' "$*"; }
head_() { printf '\n\033[1m%s\033[0m\n' "$*"; }

head_ "System"
MACOS=$(sw_vers -productVersion)
if [ "${MACOS%%.*}" = "$TESTED_MACOS" ]; then
    ok "macOS $MACOS ($(uname -m))"
else
    warn "macOS $MACOS ($(uname -m)) - verified on ${TESTED_MACOS}.x, this is untested"
fi

head_ "Installed files"
for f in run-host.sh esigner-mac.jar eSigner.jar esigner.properties; do
    if [ -r "${DEST}/${f}" ]; then ok "$f"; else bad "$f missing - run 'make install'"; fi
done

JAVA_BIN=$(sed -n 's/^[[:space:]]*java.bin[[:space:]]*=[[:space:]]*//p' "${DEST}/esigner.properties" 2>/dev/null | head -1)
if [ -x "${JAVA_BIN:-}" ]; then
    ok "java  $JAVA_BIN ($("$JAVA_BIN" -version 2>&1 | head -1))"
else
    bad "java.bin in esigner.properties is not executable: ${JAVA_BIN:-unset}"
fi

head_ "PKCS#11 module"
MODULE=$(sed -n 's/^[[:space:]]*pkcs11.library[[:space:]]*=[[:space:]]*//p' "${DEST}/esigner.properties" 2>/dev/null | head -1)
if [ -r "${MODULE:-}" ]; then
    ok "$MODULE"
    ARCHS=$(lipo -archs "$MODULE" 2>/dev/null)
    case "$ARCHS" in
        *$(uname -m)*) ok "arch  $ARCHS" ;;
        *) warn "arch  $ARCHS - does not include $(uname -m); host runs under Rosetta" ;;
    esac
else
    bad "no readable pkcs11.library - run 'make deps && make install'"
fi

head_ "Browser registration"
found=0
for dir in "Google/Chrome" "Google/Chrome Beta" "Google/Chrome Canary" \
           "Chromium" "Microsoft Edge" "BraveSoftware/Brave-Browser" "Mozilla"; do
    f="${SUPPORT}/${dir}/NativeMessagingHosts/${HOST_NAME}.json"
    [ -f "$f" ] || continue
    found=$((found + 1))
    target=$(sed -n 's/.*"path"[[:space:]]*:[[:space:]]*"\(.*\)".*/\1/p' "$f")
    if [ -x "$target" ]; then ok "$dir"; else bad "$dir manifest points at a missing host: $target"; fi
done
[ "$found" -eq 0 ] && bad "no browser manifests - run 'make install'"
echo "        (read at browser startup; quit with Cmd-Q and reopen after installing)"

head_ "Token"
READER=$(system_profiler SPSmartCardsDataType 2>/dev/null | sed -n 's/^ *#01: \(.*\) (ATR.*/\1/p' | head -1)
if [ -n "$READER" ]; then
    ok "reader  $READER"
    if [ -r "${MODULE:-}" ] && command -v pkcs11-tool >/dev/null 2>&1; then
        # Slot and certificate listing are public objects: no PIN, no risk to
        # the retry counter.
        LABEL=$(pkcs11-tool --module "$MODULE" -L 2>/dev/null | sed -n 's/^ *token label *: *//p' | head -1)
        if [ -n "$LABEL" ]; then
            ok "token   $LABEL"
            pkcs11-tool --module "$MODULE" -O --type cert 2>/dev/null \
                | sed -n 's/^ *subject: *DN: *//p' | sed 's/^/        cert  /'
        else
            bad "module loaded but reported no token"
        fi
    fi
else
    warn "no smart card reader detected - is the token plugged in?"
fi

head_ "Protocol"
MSG='{"appletMode":"CHECK_API"}'
if [ -x "${DEST}/run-host.sh" ]; then
    LEN=$(printf '\\x%02x\\x00\\x00\\x00' "${#MSG}")
    REPLY=$( { printf "$LEN%s" "$MSG" | "${DEST}/run-host.sh" 2>/dev/null; } \
             | LC_ALL=C tr -cd '[:print:]' )
    case "$REPLY" in
        *hasNative*true*) ok "handshake  ${REPLY#*\{}" ;;
        "")               bad "host produced no reply - see ~/Library/Logs/eProcSigner/" ;;
        *)                bad "unexpected reply: $REPLY" ;;
    esac
else
    bad "run-host.sh not installed"
fi

echo
if [ "$problems" -eq 0 ]; then
    printf '\033[32mReady.\033[0m Plug in the token and sign from the portal.\n'
else
    printf '\033[31m%d problem(s).\033[0m See above.\n' "$problems"
    exit 1
fi
