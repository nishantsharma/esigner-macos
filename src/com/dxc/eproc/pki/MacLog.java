package com.dxc.eproc.pki;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Shared file-append helper for the Logger/Debug shadows. */
final class MacLog {

    private static final File DIR =
            new File(System.getProperty("user.home"), "Library/Logs/eProcSigner");

    private MacLog() {
    }

    static synchronized void write(String file, String message, boolean enabled) {
        if (!enabled) {
            return;
        }
        try {
            if (!DIR.isDirectory() && !DIR.mkdirs()) {
                return;
            }
            String stamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
            try (PrintWriter out = new PrintWriter(new FileWriter(new File(DIR, file), true))) {
                out.println(stamp + " : " + message);
            }
        } catch (Exception ignored) {
            // Logging must never break signing, and stdout belongs to the
            // native messaging protocol -- so swallow it.
        }
    }
}
