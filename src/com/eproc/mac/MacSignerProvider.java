package com.eproc.mac;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.security.Provider;
import java.security.Security;

/**
 * Stands in for Windows' SunMSCAPI provider on macOS.
 *
 * eSigner.jar reaches its certificates in exactly two ways, both hardcoded:
 *   KeyStore.getInstance("Windows-MY")                  -- KeyStoreUtils
 *   KeyStore.getInstance("Windows-MY", "SunMSCAPI")     -- CertificatePanel
 *
 * Registering a provider under the name SunMSCAPI that offers a KeyStore
 * called Windows-MY makes both succeed with no change to the signed jar.
 * The keystore itself is a PKCS#11 token (see TokenKeyStore).
 *
 * Anything else asked of this provider by name -- notably the Signature and
 * MessageDigest instances BouncyCastle requests using
 * keyStore.getProvider().getName() -- is forwarded to the real PKCS#11
 * provider, and failing that to whichever installed provider can serve it.
 */
public final class MacSignerProvider extends Provider {

    private static final long serialVersionUID = 1L;

    private static final Object LOCK = new Object();
    private static volatile Provider token;
    private static volatile String tokenError;

    public MacSignerProvider() {
        super("SunMSCAPI", "1.0",
                "PKCS#11-backed stand-in for SunMSCAPI (macOS)");
        putService(new Service(this, "KeyStore", "Windows-MY",
                TokenKeyStore.class.getName(), null, null));
    }

    @Override
    public Service getService(String type, String algorithm) {
        Service own = super.getService(type, algorithm);
        if (own != null) {
            return own;
        }
        Provider t = token;
        if (t != null) {
            Service s = t.getService(type, algorithm);
            if (s != null) {
                return s;
            }
        }
        for (Provider p : Security.getProviders()) {
            if (p == this || p == t) {
                continue;
            }
            Service s = p.getService(type, algorithm);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /** The configured SunPKCS11 provider, or null if the token can't be reached. */
    static Provider token() {
        Provider t = token;
        if (t != null) {
            return t;
        }
        synchronized (LOCK) {
            if (token != null) {
                return token;
            }
            String library = Config.libraryPath();
            if (library == null) {
                tokenError = "No PKCS#11 library found. Install the HYP2003 macOS "
                        + "driver, or set pkcs11.library in "
                        + new File(Config.DIR, "esigner.properties").getPath();
                return null;
            }
            try {
                Provider base = Security.getProvider("SunPKCS11");
                if (base == null) {
                    tokenError = "This JRE has no SunPKCS11 provider.";
                    return null;
                }
                Provider configured = base.configure(writeConfig(library).getAbsolutePath());
                Security.addProvider(configured);
                token = configured;
                return configured;
            } catch (Exception e) {
                tokenError = "Could not open the PKCS#11 library " + library
                        + " -- " + e.getMessage();
                return null;
            }
        }
    }

    static String tokenError() {
        return tokenError == null ? "PKCS#11 token unavailable" : tokenError;
    }

    private static File writeConfig(String library) throws Exception {
        File dir = Config.DIR;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            dir = new File(System.getProperty("java.io.tmpdir"));
        }
        File cfg = new File(dir, "pkcs11.cfg");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(cfg))) {
            w.write("name = eProcToken\n");
            // Quote the path: SunPKCS11 chokes on unquoted paths containing spaces.
            w.write("library = \"" + library + "\"\n");
            String slot = Config.get("pkcs11.slotListIndex", null);
            if (slot != null) {
                w.write("slotListIndex = " + slot + "\n");
            }
            String extra = Config.get("pkcs11.extraConfig", null);
            if (extra != null) {
                w.write(extra.replace("\\n", "\n") + "\n");
            }
        }
        return cfg;
    }
}
