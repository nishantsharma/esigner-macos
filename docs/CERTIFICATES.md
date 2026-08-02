# Which certificate on the token actually signs

A DSC token usually holds more than one certificate, and they are not
interchangeable. Some cannot sign at all. In a certificate chooser they can look
identical — same name, same organisation — so the wrong one is easy to pick, and
the resulting failure looks like "my DSC doesn't work with this portal".

This page is the anatomy of one real token, and what it says about diagnosing a
portal rejection. Run `make diagnose` to get the same breakdown for yours.

## The token in question

A Hypersecu HYP2003 carrying certificates from **two** Certifying Authorities.
Six objects in total — three end-entity certificates and three CA certificates.

| # | Issued by | Key usage | Signs? |
|---|---|---|---|
| 1 | Capricorn Sub CA for Individual DSC 2022 | `digitalSignature`, `nonRepudiation` | yes |
| 2 | Capricorn Sub CA for Individual DSC 2022 | `keyEncipherment` **only** | **no** |
| 3 | e-Mudhra Sub CA for Class 3 Individual 2022 | `digitalSignature`, `nonRepudiation` | yes |

Plus the Capricorn chain — Sub CA, `Capricorn CA 2022`, and the self-signed
`CCA India 2022` root. **No eMudhra CA certificates are on the token at all.**

### 1 & 2 — the Capricorn pair

Capricorn issued **two** certificates on the same day, which is the normal
"signing + encryption" pair:

```
#1  Key Usage (critical):  Digital Signature, Non Repudiation
    Extended Key Usage:    MS Smartcard Login, E-mail Protection,
                           TLS Web Client Auth, MS Document Signing,
                           Adobe Authentic Document (1.2.840.113583.1.1.5)
    Validity:              3 years
    PKCS#11 key:           generated on the token (CKA_LOCAL true)

#2  Key Usage (critical):  Key Encipherment
    Extended Key Usage:    Microsoft Encrypted File System   <- critical, and alone
    Validity:              3 years
    PKCS#11 key:           imported (CKA_LOCAL false)
```

**#2 cannot sign, by design.** Its key usage permits encryption only, and its
EKU is *critical* and names a single purpose that is not signing. That the key
was imported rather than generated on-chip is the giveaway: encryption keys are
escrowed by the CA so encrypted data stays recoverable, whereas a signing key is
generated on the token precisely so it can never be copied.

Both share the same subject CN. Nothing in a chooser distinguishes them.

### 3 — the eMudhra certificate

```
#3  Key Usage (critical):  Digital Signature, Non Repudiation
    Subject Alt Name:      email address present
    Validity:              1 year
    PKCS#11 key:           generated on the token
```

A single signing certificate, no encryption counterpart.

All three assert CCA India policy OID **2.16.356.100.2.3** — *Class 3*. Both CAs
are CCA-licensed. Both keys are RSA-2048, both certificates signed with SHA-256.

## So why did only the eMudhra certificate work on KPPP?

Working from what the token itself shows, several explanations can be
**eliminated**:

- **Not expiry.** The Capricorn certificates run to 2029; the eMudhra one to
  2027. If anything the Capricorn pair has the longer life.
- **Not certificate class.** All three carry the Class 3 policy OID.
- **Not key strength or algorithm.** RSA-2048 / SHA-256 across the board.
- **Not a missing chain.** The token carries the *complete* Capricorn chain up
  to the CCA India root and carries *nothing* for eMudhra — yet eMudhra is the
  one that works. The portal is clearly supplying its own trust anchors, so
  chain-on-token is not the variable.

That leaves two plausible causes, and they are diagnosable:

### The likely local cause: picking the encryption certificate

Two of the three certificates are Capricorn, and one of those two is
`keyEncipherment`-only. It appears in the chooser with the same name as the
signing certificate. Selecting it cannot produce a valid signature — the token
will either refuse the operation or return something the portal rejects.

"The Capricorn one doesn't work" and "one of the two Capricorn ones can never
work" are easy to confuse when both rows read *Nishant Sharma*.

### The likely portal-side cause: enrolment binding

Indian eProcurement portals bind **one specific DSC to your login** during
registration — they store the certificate's serial number and subject, and at
signing time they compare the signer certificate against the enrolled one. A
technically perfect signature from a certificate that was never enrolled is
still rejected, usually with a message about the DSC not matching or not being
registered.

If the eMudhra certificate is the enrolled one, every Capricorn certificate will
be refused no matter what. **Nothing local can change this** — you update the
DSC mapping in your portal profile, or you keep signing with the enrolled one.

### A third, weaker possibility

The Capricorn signing certificate's subject DN contains `pseudonym`
(OID 2.5.4.65) and `title`; the eMudhra one does not carry `pseudonym`. Portal
code that parses the DN with a limited attribute table can mishandle types it
doesn't recognise. This is speculation — unlike everything above it, nothing on
the token confirms or refutes it.

## Telling them apart

```bash
make diagnose
```

One PIN prompt, then for every certificate on the token: subject, issuer,
validity, key usage, extended key usage, whether a private key is paired with
it, and **an actual signature attempt** through the stock eSigner
`SignatureGenerator`.

```
alias      certificate.digital.1391070722
key usage  keyEncipherment
           >> ENCRYPTION-ONLY CERTIFICATE - cannot sign, by design.
           >> Do not pick this one in the certificate chooser.
SIGN TEST  skipped - the certificate itself rules it out

alias      e46a0a37-...
key usage  digitalSignature, nonRepudiation
SIGN TEST  SIGNS OK - 2488-byte CMS SignedData
```

That reading splits the problem cleanly:

- **`SIGNS OK` and the portal still refuses it** → the problem is at the portal.
  Enrolment binding is the first thing to check, then whether the portal accepts
  that CA at all. Neither is fixable from your Mac.
- **`SIGN TEST FAILED`** → the problem is local, and the output says what it is.

## A note on PKCS#11 object identity

Observed on this token, in case it saves someone an afternoon: the Capricorn
signing certificate and its key carry a `CKA_ID` that is the **ASCII text of the
object label** (52 bytes), while the other two use a conventional 20-byte binary
identifier. Java's `SunPKCS11` pairs a certificate with its key by comparing
`CKA_ID`, and since both objects carry the same odd value the pairing works
fine. Middleware that assumes a 20-byte ID might not be so relaxed.

If `make diagnose` ever reports *"the certificate is on the token but its key is
not, or the two carry different CKA_ID values"*, this is the attribute to go
looking at — with `pkcs11-tool --module <module> -O`, which needs no PIN.
