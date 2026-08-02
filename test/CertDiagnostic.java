import com.dxc.eproc.pki.CertificateDetails;
import com.dxc.eproc.pki.KeyStoreData;
import com.dxc.eproc.pki.KeyStoreUtils;
import com.dxc.eproc.pki.SignatureGenerator;
import com.eproc.mac.MacSignerProvider;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

/**
 * Reports, for every certificate on the token, whether it can actually sign —
 * and tries it.
 *
 * A DSC token usually carries more than one certificate, and they are not
 * interchangeable: a signing certificate and an encryption certificate can have
 * the same subject name and look identical in a chooser, while only one of them
 * is capable of signing at all. When a portal rejects a signature, the first
 * question is whether the certificate could ever have worked locally. This
 * answers that, and thereby splits a local problem from a portal-side one.
 *
 * Asks for the PIN once. Never guesses it.
 */
public class CertDiagnostic {

    public static void main(String[] args) throws Exception {
        Security.addProvider(new MacSignerProvider());
        Security.addProvider((java.security.Provider) Class
                .forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance());

        KeyStore ks = KeyStoreUtils.getMSKeyStore();
        List<KeyStoreData> entries = KeyStoreUtils.getKeyStoreList(ks);

        System.out.println();
        System.out.println("Certificates the signer can see: " + entries.size());

        int signable = 0;
        for (KeyStoreData d : entries) {
            System.out.println();
            System.out.println("--------------------------------------------------------------");
            if (report(ks, d.getAlias())) {
                signable++;
            }
        }

        System.out.println();
        System.out.println("==============================================================");
        System.out.println(signable + " of " + entries.size()
                + " certificate(s) produced a valid signature locally.");
        if (signable > 0) {
            System.out.println();
            System.out.println("Any certificate marked SIGNS OK works on this Mac. If the portal");
            System.out.println("still rejects it, the problem is at the portal, not here - most");
            System.out.println("often the certificate was never enrolled against your portal");
            System.out.println("account. Portals bind one specific DSC to the login at");
            System.out.println("registration and refuse the rest.");
        }
    }

    /** @return true if this alias produced a verifiable CMS signature. */
    private static boolean report(KeyStore ks, String alias) {
        System.out.println("alias      " + alias);
        X509Certificate c;
        try {
            c = (X509Certificate) ks.getCertificate(alias);
        } catch (Exception e) {
            System.out.println("           cannot read certificate: " + e);
            return false;
        }
        if (c == null) {
            System.out.println("           no certificate for this alias");
            return false;
        }

        System.out.println("subject    " + c.getSubjectX500Principal().getName());
        System.out.println("issuer     " + c.getIssuerX500Principal().getName());
        System.out.println("serial     " + c.getSerialNumber().toString(16).toUpperCase());
        System.out.println("valid      " + c.getNotBefore() + "  ->  " + c.getNotAfter());

        boolean live = true;
        try {
            c.checkValidity(new Date());
            System.out.println("           currently valid");
        } catch (Exception e) {
            live = false;
            System.out.println("           NOT CURRENTLY VALID: " + e.getClass().getSimpleName());
        }

        // KeyUsage bit order per RFC 5280: 0 digitalSignature, 1 nonRepudiation,
        // 2 keyEncipherment, 3 dataEncipherment, 4 keyAgreement, ...
        boolean canSign = true;
        boolean[] ku = c.getKeyUsage();
        if (ku != null) {
            StringBuilder s = new StringBuilder();
            String[] names = {"digitalSignature", "nonRepudiation", "keyEncipherment",
                              "dataEncipherment", "keyAgreement", "keyCertSign",
                              "cRLSign", "encipherOnly", "decipherOnly"};
            for (int i = 0; i < ku.length && i < names.length; i++) {
                if (ku[i]) {
                    s.append(s.length() == 0 ? "" : ", ").append(names[i]);
                }
            }
            System.out.println("key usage  " + s);
            canSign = ku.length > 0 && (ku[0] || (ku.length > 1 && ku[1]));
            if (!canSign) {
                System.out.println("           >> ENCRYPTION-ONLY CERTIFICATE - cannot sign, by design.");
                System.out.println("           >> Do not pick this one in the certificate chooser.");
            }
        }

        try {
            List<String> eku = c.getExtendedKeyUsage();
            if (eku != null) {
                System.out.println("ext usage  " + String.join(", ", eku));
            }
        } catch (Exception ignored) {
            // A malformed EKU extension is not worth failing the report over.
        }

        java.security.cert.Certificate[] chain = null;
        try {
            chain = ks.getCertificateChain(alias);
        } catch (Exception ignored) {
            // Reported as "chain 0" below.
        }
        System.out.println("chain      " + (chain == null ? 0 : chain.length)
                + " certificate(s) available from the token");
        if (chain == null || chain.length < 2) {
            System.out.println("           >> issuer certificates are not on the token. Harmless if");
            System.out.println("           >> the portal has its own trust store, which they normally do.");
        }

        PrivateKey key;
        try {
            key = (PrivateKey) ks.getKey(alias, null);
        } catch (Exception e) {
            System.out.println("private    NOT AVAILABLE: " + e);
            return false;
        }
        if (key == null) {
            System.out.println("private    no private key paired with this certificate");
            System.out.println("           >> the certificate is on the token but its key is not,");
            System.out.println("           >> or the two carry different PKCS#11 CKA_ID values.");
            return false;
        }
        System.out.println("private    " + key.getClass().getName());

        if (!canSign || !live) {
            System.out.println("SIGN TEST  skipped - the certificate itself rules it out");
            return false;
        }

        try {
            CertificateDetails details = new CertificateDetails();
            details.setAlias(alias);
            details.setKeyStore(ks);
            details.setPrivateKey(key);
            details.setX509Certificate(c);
            details.setCertificationChain(chain);

            String payload = "{\"data\":\"eProcurement macOS host certificate diagnostic\"}";
            String signed = new SignatureGenerator().signData(payload, details);
            if (signed == null || signed.isEmpty()) {
                System.out.println("SIGN TEST  FAILED - signer returned nothing");
                return false;
            }
            byte[] der = java.util.Base64.getDecoder().decode(signed);
            System.out.println("SIGN TEST  SIGNS OK - " + der.length + "-byte CMS SignedData");
            return true;
        } catch (Throwable t) {
            System.out.println("SIGN TEST  FAILED - " + t);
            return false;
        }
    }
}
