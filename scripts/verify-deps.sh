#!/bin/bash
# Checks the provenance of the two vendor binaries in lib/.
#
# The PKCS#11 module is native code that runs inside the JVM and, by design,
# sees your token PIN. Its Apple Developer ID signature is the one durable
# check that it is the vendor's build and not something substituted for it.
#
#   make verify-deps
#
# Exit code is non-zero if anything failed to verify. A SHA-256 mismatch alone
# is a warning, not a failure: vendors ship new builds. A signature mismatch is
# a failure.
set -uo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"

# The Apple Developer Team ID that signs the HYP2003 macOS driver.
# HYP2003 is a rebadged Feitian ePass2003, so FEITIAN — not Hypersecu — is the
# expected signer. See SECURITY.md section 4.
EXPECT_TEAM="S47T4UESP3"

# Exact builds this repo was tested against. Informational.
SHA_DYLIB="3e7b9e91a861fccbafa9e992daa4c3c4746d9a20eb99e2f43ac860037159cf9c"
SHA_JAR="819d940493ea3f550a8d93f5b17b768bb8b41895a9e649e2c2fb3d982baeaccd"

rc=0
ok()   { printf '  \033[32mok\033[0m    %s\n' "$*"; }
warn() { printf '  \033[33mwarn\033[0m  %s\n' "$*"; }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$*"; rc=1; }
kv()   { printf '  %-9s %s\n' "$1" "$2"; }

echo "Vendor binary verification"
echo

# ---------------------------------------------------------- PKCS#11 module ---
echo "PKCS#11 module"
LIB=$(ls "${HERE}"/lib/libcastle*.dylib 2>/dev/null | head -1)
if [ -z "$LIB" ]; then
    LIB=$(ls /usr/local/lib/libcastle*.dylib 2>/dev/null | head -1)
fi

if [ -z "$LIB" ]; then
    bad "not found - run 'make deps' first"
else
    kv "file" "$LIB"
    kv "arch" "$(lipo -archs "$LIB" 2>/dev/null)"

    if ! codesign --verify --strict "$LIB" 2>/dev/null; then
        bad "code signature does not verify - the file is damaged or modified"
    else
        info=$(codesign -dvvv "$LIB" 2>&1)
        auth=$(printf '%s' "$info" | awk -F= '/^Authority=/{print $2; exit}')
        team=$(printf '%s' "$info" | awk -F= '/^TeamIdentifier=/{print $2}')
        root=$(printf '%s' "$info" | awk -F= '/^Authority=/{a=$2} END{print a}')

        kv "authority" "$auth"
        kv "chains to" "$root"

        if [ "$team" = "$EXPECT_TEAM" ]; then
            ok "team id $team (expected $EXPECT_TEAM)"
        else
            bad "team id $team - expected $EXPECT_TEAM. Do NOT use this module."
            echo "        See SECURITY.md section 4 before going further."
        fi

        [ "$root" = "Apple Root CA" ] \
            && ok "signature chains to Apple Root CA" \
            || bad "does not chain to Apple Root CA"
    fi

    got=$(shasum -a 256 "$LIB" | cut -d' ' -f1)
    if [ "$got" = "$SHA_DYLIB" ]; then
        ok "sha256 matches the tested build"
    else
        warn "sha256 $got"
        echo "        differs from the tested build (${SHA_DYLIB:0:16}...)."
        echo "        Fine if the vendor has shipped a new version - the signature above is"
        echo "        the check that matters. It just means this exact build is untested here."
    fi
fi

# ------------------------------------------------------------- eSigner.jar ---
echo
echo "eSigner.jar"
JAR="${HERE}/lib/eSigner.jar"
if [ ! -r "$JAR" ]; then
    bad "not found - run 'make deps' first"
else
    kv "file" "$JAR"
    got=$(shasum -a 256 "$JAR" | cut -d' ' -f1)
    if [ "$got" = "$SHA_JAR" ]; then
        ok "sha256 matches the tested build (eSigner 1.9)"
    else
        warn "sha256 $got"
        echo "        differs from the tested build (${SHA_JAR:0:16}...)."
    fi
    echo "        Not code-signed - Java jars from this kit never are. Its provenance"
    echo "        rests on having downloaded it over HTTPS from the portal you log in to."
fi

echo
if [ $rc -eq 0 ]; then
    echo "Provenance checks passed."
else
    echo "Provenance checks FAILED. Read SECURITY.md section 4 before continuing."
fi
exit $rc
