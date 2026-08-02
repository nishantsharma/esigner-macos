#!/bin/bash
# Removes the host and every browser manifest. Invoked by `make uninstall`.
set -uo pipefail

HOST_NAME="com.dxc.eproc.pki"
SUPPORT="${HOME}/Library/Application Support"

for dir in "Google/Chrome" "Google/Chrome Beta" "Google/Chrome Canary" \
           "Chromium" "Microsoft Edge" "BraveSoftware/Brave-Browser" "Mozilla"; do
    f="${SUPPORT}/${dir}/NativeMessagingHosts/${HOST_NAME}.json"
    [ -f "$f" ] && rm -f "$f" && printf '  removed  %s\n' "$f"
done

if [ -d "${SUPPORT}/eProcSigner" ]; then
    rm -rf "${SUPPORT}/eProcSigner"
    printf '  removed  %s\n' "${SUPPORT}/eProcSigner"
fi

echo "Uninstalled. Logs, if any, are left at ~/Library/Logs/eProcSigner"
