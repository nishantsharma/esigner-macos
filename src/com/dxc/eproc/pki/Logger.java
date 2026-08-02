package com.dxc.eproc.pki;

/**
 * Replaces the stock Logger, which writes to
 * user.home + "\\AppData\\Local\\eProcSigner\\signerlog.txt" -- on macOS that
 * is one file with backslashes in its name, dumped in the home directory.
 *
 * Placed ahead of eSigner.jar on the classpath so it shadows the original.
 * Same signatures, macOS-appropriate destination.
 */
public class Logger {

    private static boolean _debug = true;

    public static void setDebug(boolean debug) {
        _debug = debug;
    }

    public static void setEnabled(boolean enabled) {
        _debug = enabled;
    }

    public static boolean getEnabled() {
        return _debug;
    }

    public static void print(String message) {
        MacLog.write("signer.log", message, _debug);
    }
}
