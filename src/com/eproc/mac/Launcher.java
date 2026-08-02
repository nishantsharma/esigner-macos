package com.eproc.mac;

import java.security.Security;

/**
 * Entry point for the macOS native messaging host.
 *
 * Installs the SunMSCAPI stand-in, then hands control to the stock
 * eSigner.jar main class. eSigner.jar itself is never modified.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) throws Exception {
        if (Security.getProvider("SunMSCAPI") == null) {
            // Appended, not inserted: this provider should only be consulted
            // for Windows-MY and for lookups that name it explicitly.
            Security.addProvider(new MacSignerProvider());
        }
        com.dxc.eproc.pki.EprocSigner.main(args);
    }
}
