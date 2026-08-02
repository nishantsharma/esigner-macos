package com.dxc.eproc.pki;

/** Shadows the stock Debug class for the same reason as {@link Logger}. */
public class Debug {

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
        MacLog.write("signer-debug.log", message, _debug);
    }
}
