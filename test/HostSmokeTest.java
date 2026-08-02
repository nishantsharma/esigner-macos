import com.dxc.eproc.pki.CertificateDetails;
import com.dxc.eproc.pki.KeyStoreData;
import com.dxc.eproc.pki.KeyStoreUtils;
import com.dxc.eproc.pki.SignatureGenerator;
import com.eproc.mac.MacSignerProvider;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Drives the real, unmodified eSigner classes against a PKCS#11 token to prove
 * the SunMSCAPI stand-in carries the whole path: certificate enumeration
 * through to a BouncyCastle CMS signature produced by a key that never leaves
 * the token.
 */
public class HostSmokeTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Security.addProvider(new MacSignerProvider());
        // eSigner asks for provider "BC" by name; it ships BouncyCastle but
        // registers it in EprocSigner.main, which this harness bypasses.
        Security.addProvider((java.security.Provider) Class
                .forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance());

        // 1. The KeyStoreUtils call site: KeyStore.getInstance("Windows-MY")
        KeyStore viaUtils = KeyStoreUtils.getMSKeyStore();
        check("KeyStoreUtils.getMSKeyStore() returns a keystore", viaUtils != null);
        check("  provider is named SunMSCAPI as the app expects",
                "SunMSCAPI".equals(viaUtils.getProvider().getName()));

        // 2. The CertificatePanel call site: getInstance(alg, providerName)
        KeyStore viaPanel = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
        viaPanel.load(null, null);
        check("CertificatePanel-style lookup resolves", viaPanel.size() > 0);

        // 3. Certificate enumeration, using the app's own class
        List<KeyStoreData> certs = KeyStoreUtils.getKeyStoreList(viaUtils);
        check("KeyStoreUtils.getKeyStoreList() finds key entries", !certs.isEmpty());
        for (KeyStoreData d : certs) {
            System.out.println("      alias=" + d.getAlias() + "  subject=" + d.getSubject());
        }

        // 4. The actual signing path, through the app's SignatureGenerator
        KeyStoreData first = certs.get(0);
        String alias = first.getAlias();
        PrivateKey key = (PrivateKey) viaUtils.getKey(alias, null);
        check("private key handle obtained from token", key != null);
        System.out.println("      key class=" + (key == null ? "null" : key.getClass().getName()));

        X509Certificate cert = (X509Certificate) viaUtils.getCertificate(alias);
        CertificateDetails details = new CertificateDetails();
        details.setAlias(alias);
        details.setKeyStore(viaUtils);
        details.setPrivateKey(key);
        details.setX509Certificate(cert);
        details.setCertificationChain(viaUtils.getCertificateChain(alias));

        String payload = "{\"data\":\"eProcurement macOS host smoke test\"}";
        String signed = new SignatureGenerator().signData(payload, details);
        check("SignatureGenerator.signData() produced output",
                signed != null && !signed.isEmpty());

        if (signed != null && !signed.isEmpty()) {
            byte[] der = java.util.Base64.getDecoder().decode(signed);
            check("  output is a well-formed CMS SignedData", verifyCms(der, payload));
            System.out.println("      CMS length=" + der.length + " bytes");
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Verify with BouncyCastle straight out of eSigner.jar, via reflection. */
    private static boolean verifyCms(byte[] der, String expectedContent) {
        try {
            Class<?> sdClass = Class.forName("org.bouncycastle.cms.CMSSignedData");
            Object sd = sdClass.getConstructor(byte[].class).newInstance((Object) der);

            Object signedContent = sdClass.getMethod("getSignedContent").invoke(sd);
            byte[] content = (byte[]) signedContent.getClass()
                    .getMethod("getContent").invoke(signedContent);
            if (!expectedContent.equals(new String(content))) {
                System.out.println("      content mismatch: " + new String(content));
                return false;
            }

            Object certStore = sdClass.getMethod("getCertificatesAndCRLs",
                    String.class, String.class).invoke(sd, "Collection", "BC");
            Object signers = sdClass.getMethod("getSignerInfos").invoke(sd);
            java.util.Collection<?> all = (java.util.Collection<?>) signers.getClass()
                    .getMethod("getSigners").invoke(signers);

            for (Object signer : all) {
                Object sid = signer.getClass().getMethod("getSID").invoke(signer);
                java.util.Collection<?> matches = (java.util.Collection<?>) certStore.getClass()
                        .getMethod("getCertificates", java.security.cert.CertSelector.class)
                        .invoke(certStore, sid);
                if (matches.isEmpty()) {
                    System.out.println("      no matching certificate in CMS");
                    return false;
                }
                java.security.cert.X509Certificate c =
                        (java.security.cert.X509Certificate) matches.iterator().next();
                Boolean ok = (Boolean) signer.getClass()
                        .getMethod("verify", java.security.cert.X509Certificate.class, String.class)
                        .invoke(signer, c, "BC");
                if (!Boolean.TRUE.equals(ok)) {
                    return false;
                }
            }
            return !all.isEmpty();
        } catch (Throwable t) {
            System.out.println("      CMS verification error: " + t);
            return false;
        }
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) {
            failures++;
        }
    }
}
