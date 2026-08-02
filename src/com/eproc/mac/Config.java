package com.eproc.mac;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Locates the PKCS#11 library that fronts the DSC token.
 *
 * Resolution order: -Designer.pkcs11.library, $ESIGNER_PKCS11_LIBRARY,
 * the pkcs11.library key in esigner.properties, then a scan of the places
 * the usual macOS middleware installs itself.
 */
public final class Config {

    public static final File DIR =
            new File(System.getProperty("user.home"), "Library/Application Support/eProcSigner");

    private static final String[] CANDIDATES = {
        // Hypersecu / Feitian ePass2003 ("EnterSafe") macOS middleware
        "/usr/local/lib/libcastle_v2.1.0.0.dylib",
        "/usr/local/lib/libcastle.dylib",
        "/Library/Frameworks/eps2003csp11.framework/Versions/A/eps2003csp11",
        "/usr/local/lib/libeps2003csp11.dylib",
        // WatchData / other common Indian DSC middleware
        "/usr/local/lib/libwdpkcs.dylib",
        "/Library/Frameworks/WDPKCS.framework/Versions/A/WDPKCS",
        // OpenSC, last resort
        "/Library/OpenSC/lib/opensc-pkcs11.so",
        "/opt/homebrew/lib/opensc-pkcs11.so",
        "/usr/local/lib/opensc-pkcs11.so",
    };

    private static Properties props;

    private Config() {
    }

    private static synchronized Properties props() {
        if (props == null) {
            props = new Properties();
            File f = new File(DIR, "esigner.properties");
            if (f.canRead()) {
                try (InputStream in = new FileInputStream(f)) {
                    props.load(in);
                } catch (Exception ignored) {
                    // fall through to auto-detection
                }
            }
        }
        return props;
    }

    public static String get(String key, String fallback) {
        String v = System.getProperty("esigner." + key);
        if (v == null || v.isEmpty()) {
            v = props().getProperty(key);
        }
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    /** Absolute path of the PKCS#11 library, or null if none can be found. */
    public static String libraryPath() {
        String explicit = System.getProperty("esigner.pkcs11.library");
        if (explicit == null || explicit.isEmpty()) {
            explicit = System.getenv("ESIGNER_PKCS11_LIBRARY");
        }
        if (explicit == null || explicit.isEmpty()) {
            explicit = props().getProperty("pkcs11.library");
        }
        if (explicit != null && !explicit.isEmpty()) {
            return new File(explicit).canRead() ? explicit : null;
        }
        // A module installed beside us by install.sh, when no vendor package
        // was ever run.
        File[] local = DIR.listFiles();
        if (local != null) {
            for (File f : local) {
                if (f.getName().startsWith("libcastle") && f.getName().endsWith(".dylib")) {
                    return f.getAbsolutePath();
                }
            }
        }
        for (String c : CANDIDATES) {
            if (new File(c).canRead()) {
                return c;
            }
        }
        // Glob the versioned EnterSafe library, whose version is baked into the name.
        File usrLocalLib = new File("/usr/local/lib");
        File[] kids = usrLocalLib.listFiles();
        if (kids != null) {
            List<String> hits = new ArrayList<>();
            for (File k : kids) {
                String n = k.getName();
                if (n.startsWith("libcastle") && n.endsWith(".dylib")) {
                    hits.add(k.getAbsolutePath());
                }
            }
            if (!hits.isEmpty()) {
                hits.sort(null);
                return hits.get(hits.size() - 1);
            }
        }
        return null;
    }
}
