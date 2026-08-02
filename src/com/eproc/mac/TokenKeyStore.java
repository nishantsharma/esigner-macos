package com.eproc.mac;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;

/**
 * A "Windows-MY" keystore whose entries actually live on a PKCS#11 token.
 *
 * The token is unlocked once per process: eSigner builds this keystore in two
 * different places, and the user should only be asked for their PIN once.
 */
public final class TokenKeyStore extends KeyStoreSpi {

    private static final Object LOCK = new Object();
    private static volatile KeyStore unlocked;

    private KeyStore ks;

    public TokenKeyStore() {
    }

    private KeyStore ks() {
        KeyStore k = ks;
        if (k == null) {
            throw new IllegalStateException("Keystore has not been loaded");
        }
        return k;
    }

    @Override
    public void engineLoad(InputStream stream, char[] password)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        if (password == null) {
            KeyStore cached = unlocked;
            if (cached != null) {
                ks = cached;
                return;
            }
        }
        synchronized (LOCK) {
            if (password == null && unlocked != null) {
                ks = unlocked;
                return;
            }
            Provider token = MacSignerProvider.token();
            if (token == null) {
                throw new IOException(MacSignerProvider.tokenError());
            }
            // SunPKCS11 loads the library happily with an empty slot, but then
            // registers no KeyStore service at all — which surfaces as a bare
            // "PKCS11 not found". Check here so the commonest failure says so,
            // and so we never ask for a PIN the user would type for nothing.
            if (token.getService("KeyStore", "PKCS11") == null) {
                throw new IOException("No DSC token detected. The PKCS#11 module "
                        + "loaded but reports no token in any slot — plug the token "
                        + "in and try again.");
            }
            char[] pin = password;
            if (pin == null) {
                pin = PinDialog.ask(tokenLabel(token));
                if (pin == null) {
                    throw new IOException("PIN entry was cancelled");
                }
            }
            try {
                KeyStore k = KeyStore.getInstance("PKCS11", token);
                k.load(null, pin);
                ks = k;
                unlocked = k;
            } catch (KeyStoreException e) {
                throw new IOException(e.getMessage(), e);
            } finally {
                java.util.Arrays.fill(pin, '\0');
            }
        }
    }

    private static String tokenLabel(Provider token) {
        String name = token.getName();
        int dash = name.indexOf('-');
        return dash >= 0 ? name.substring(dash + 1) : name;
    }

    @Override
    public Key engineGetKey(String alias, char[] password)
            throws NoSuchAlgorithmException, UnrecoverableKeyException {
        try {
            // The token is already logged in; PKCS#11 wants a null password here.
            return ks().getKey(alias, null);
        } catch (KeyStoreException e) {
            throw new UnrecoverableKeyException(e.getMessage());
        }
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        try {
            return ks().getCertificateChain(alias);
        } catch (KeyStoreException e) {
            return null;
        }
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        try {
            return ks().getCertificate(alias);
        } catch (KeyStoreException e) {
            return null;
        }
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        try {
            return ks().getCreationDate(alias);
        } catch (KeyStoreException e) {
            return null;
        }
    }

    @Override
    public Enumeration<String> engineAliases() {
        try {
            return ks().aliases();
        } catch (KeyStoreException e) {
            return Collections.emptyEnumeration();
        }
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        try {
            return ks().containsAlias(alias);
        } catch (KeyStoreException e) {
            return false;
        }
    }

    @Override
    public int engineSize() {
        try {
            return ks().size();
        } catch (KeyStoreException e) {
            return 0;
        }
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        try {
            return ks().isKeyEntry(alias);
        } catch (KeyStoreException e) {
            return false;
        }
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        try {
            return ks().isCertificateEntry(alias);
        } catch (KeyStoreException e) {
            return false;
        }
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        try {
            return ks().getCertificateAlias(cert);
        } catch (KeyStoreException e) {
            return null;
        }
    }

    // A signing token is read-only as far as this application is concerned.

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain)
            throws KeyStoreException {
        throw new KeyStoreException("Token keystore is read-only");
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain)
            throws KeyStoreException {
        throw new KeyStoreException("Token keystore is read-only");
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert)
            throws KeyStoreException {
        throw new KeyStoreException("Token keystore is read-only");
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        throw new KeyStoreException("Token keystore is read-only");
    }

    @Override
    public void engineStore(OutputStream stream, char[] password)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new IOException("Token keystore is read-only");
    }
}
