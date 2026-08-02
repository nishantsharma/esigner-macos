#!/bin/bash
# Populates lib/ with the two binaries this repo deliberately does not ship:
#
#   lib/eSigner.jar                the signer itself, from the KPPP installation
#                                  kit (DXC / Government of Karnataka)
#   lib/libcastle_*.dylib          the PKCS#11 module, from Hypersecu's HYP2003
#                                  macOS driver package
#
# Both are third-party proprietary binaries. Supply your own copies — the ones
# you already downloaded from the portal and from hypersecu.com.
#
# Usage:
#   ./bootstrap.sh                          search ~/Downloads and this folder
#   ./bootstrap.sh <kit.jar> [<driver>]     point at them explicitly
#
# <driver> may be the .zip, the .dmg, or the .pkg. If you have already run the
# vendor installer, /usr/local/lib/libcastle*.dylib is picked up instead and no
# driver argument is needed.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${HERE}/.bootstrap-work"
SEARCH_DIRS=("$HERE" "${HOME}/Downloads" "${HOME}/Desktop")

say()  { printf '  %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

mkdir -p "${HERE}/lib"
trap 'rm -rf "$WORK"' EXIT
rm -rf "$WORK"; mkdir -p "$WORK"

echo "eSigner macOS host - dependency bootstrap"
echo

# ------------------------------------------------------------------ finder ---
# find_file <glob> [explicit-path]
find_file() {
    pattern="$1"; explicit="${2:-}"
    if [ -n "$explicit" ]; then
        [ -r "$explicit" ] || fail "not readable: $explicit"
        printf '%s' "$explicit"; return 0
    fi
    for d in "${SEARCH_DIRS[@]}"; do
        [ -d "$d" ] || continue
        hit=$(/usr/bin/find "$d" -maxdepth 2 -name "$pattern" -print 2>/dev/null | head -1)
        [ -n "$hit" ] && { printf '%s' "$hit"; return 0; }
    done
    return 1
}

# ------------------------------------------------------------- eSigner.jar ---
if [ -r "${HERE}/lib/eSigner.jar" ]; then
    say "eSigner.jar    already present"
else
    KIT=$(find_file 'eproc-native-installer.jar' "${1:-}") \
        || fail "eproc-native-installer.jar not found.
       Download the eSigner installation kit from the eProcurement portal
       (Downloads -> eSigner Installation Kit), unzip it, and either run this
       from that folder or pass the jar path as the first argument."
    say "kit:           $KIT"

    unzip -q -o "$KIT" 'eProcSigner.zip' -d "$WORK" \
        || fail "no eProcSigner.zip inside $KIT - is that really the kit jar?"
    unzip -q -o "${WORK}/eProcSigner.zip" 'eSigner.jar' -d "${HERE}/lib" \
        || fail "no eSigner.jar inside eProcSigner.zip"
    say "eSigner.jar    extracted -> lib/eSigner.jar"
fi

# ---------------------------------------------------------- PKCS#11 module ---
if ls "${HERE}"/lib/libcastle*.dylib >/dev/null 2>&1; then
    say "pkcs11 module  already present"
elif ls /usr/local/lib/libcastle*.dylib >/dev/null 2>&1; then
    say "pkcs11 module  vendor driver already installed system-wide; nothing to do"
else
    DRV=$(find_file 'HYP2003*' "${2:-}") \
        || DRV=$(find_file 'ePass2003*' "") \
        || fail "HYP2003 macOS driver not found.
       Download it from https://www.hypersecu.com/support/downloads (HYP2003,
       macOS) and either leave it in ~/Downloads or pass it as the second
       argument. A .zip, .dmg or .pkg all work."
    say "driver:        $DRV"

    case "$DRV" in
        *.zip)
            unzip -q -o "$DRV" -d "${WORK}/zip"
            DMG=$(/usr/bin/find "${WORK}/zip" -name '*.dmg' | head -1)
            PKG=$(/usr/bin/find "${WORK}/zip" -name '*.pkg' | head -1)
            ;;
        *.dmg) DMG="$DRV"; PKG="" ;;
        *.pkg) DMG="";     PKG="$DRV" ;;
        *)     fail "don't know what to do with $DRV" ;;
    esac

    MOUNT=""
    if [ -z "${PKG:-}" ] && [ -n "${DMG:-}" ]; then
        MOUNT="${WORK}/mnt"; mkdir -p "$MOUNT"
        hdiutil attach "$DMG" -nobrowse -readonly -mountpoint "$MOUNT" >/dev/null \
            || fail "could not mount $DMG"
        PKG=$(/usr/bin/find "$MOUNT" -maxdepth 2 -name '*.pkg' | head -1)
    fi
    [ -n "${PKG:-}" ] || fail "no .pkg found in the driver package"

    pkgutil --expand "$PKG" "${WORK}/expanded" \
        || fail "pkgutil could not expand $PKG"
    [ -n "$MOUNT" ] && hdiutil detach "$MOUNT" -quiet 2>/dev/null || true

    # The module lives in the pkcs11 sub-package. Search all of them so a
    # repackaged installer still works.
    ( cd "${WORK}/expanded" && for p in */Payload; do
        [ -r "$p" ] || continue
        mkdir -p "${WORK}/payload"
        gzip -dc < "$p" 2>/dev/null | ( cd "${WORK}/payload" && cpio -idm 2>/dev/null ) || true
      done )
    LIB=$(/usr/bin/find "${WORK}/payload" -name 'libcastle*.dylib' | head -1)
    [ -n "$LIB" ] || fail "no libcastle*.dylib inside the driver package"

    cp "$LIB" "${HERE}/lib/"
    xattr -c "${HERE}/lib/$(basename "$LIB")" 2>/dev/null || true
    say "pkcs11 module  extracted -> lib/$(basename "$LIB")"
    say "               arch: $(lipo -archs "${HERE}/lib/$(basename "$LIB")" 2>/dev/null)"
fi

echo
echo "Ready. Next:  make install"
