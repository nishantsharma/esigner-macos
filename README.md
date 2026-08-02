# eSigner for macOS

Sign eProcurement tenders on a Mac with your DSC token.

The Karnataka eProcurement portal (and other DXC-built state portals) requires a
browser signing helper called **eSigner**, shipped only as a Windows kit. It
installs via `.bat` files and reaches your certificate through the Windows
certificate store, so there is no way to run it on macOS as delivered — the
usual advice is to keep a Windows machine or a VM around.

This is a native macOS host for it. The signer's own code is **not modified**;
what changes is where it finds your certificate — the DSC token directly, over
PKCS#11, instead of the Windows certificate store.

> **As-is, no author support.** This was built for one person's token, one Mac
> and one portal, then written up so nobody has to redo it. It is offered with
> no warranty and no support — issues may go unanswered, and I can't debug your
> token, CA or portal. Run `make test-token` and satisfy yourself it works
> *before* a deadline depends on it. Details in
> [CONTRIBUTING.md](CONTRIBUTING.md); good PRs are very welcome.

It handles a legally binding signing key, so read
**[SECURITY.md](SECURITY.md)** — particularly [§4, on obtaining the vendor
binaries safely](SECURITY.md#4-getting-the-vendor-binaries-safely). Downloading
the wrong "driver" is the one mistake here with real consequences.

---

## Requirements

- macOS 12 or later, Intel or Apple Silicon — see [Tested on](#tested-on)
- Xcode Command Line Tools, for `make` — `xcode-select --install`
- A JDK — `brew install openjdk`
- Your DSC token, and its PIN
- Two vendor files you already have or can download:
  - `eproc-native-installer.jar` — from the portal's *eSigner Installation
    Kit*, downloaded while logged in to the portal itself
  - the **HYP2003 macOS driver** — `.zip`, `.dmg` or `.pkg`, whichever you get

Those two are third-party proprietary binaries, so this repo does not ship
them. `make deps` extracts what it needs from your own copies.

> ⚠️ **Where you get the driver matters more than anything else here.** It is
> native code that runs inside the signing process and sees your PIN. Do not
> search for it and click the first result — driver-download sites are a
> standard malware route. Trace a link from your Certifying Authority or type
> the vendor's domain by hand, then verify the Apple Developer ID signature.
> `make verify-deps` does the checking;
> [SECURITY.md §4](SECURITY.md#4-getting-the-vendor-binaries-safely) explains
> the chain, including why the signature legitimately reads *FEITIAN* rather
> than *Hypersecu*.

## Install

```bash
git clone https://github.com/<you>/esigner-macos.git
cd esigner-macos
make install
```

That fetches the vendor binaries, compiles, verifies the PKCS#11 module's code
signature (and refuses to install if it doesn't check out), deploys to
`~/Library/Application Support/eProcSigner/`, and registers with every browser
it finds. Then **quit Chrome completely** (⌘Q — closing the window is not
enough) and reopen it. Install the eSigner extension from the portal if you
haven't already.

If your vendor downloads aren't in `~/Downloads` or `~/Desktop`, point at them
once before installing:

```bash
./bootstrap.sh ~/path/eproc-native-installer.jar ~/path/HYP2003-MAC-iOS.zip
```

Run `make` on its own for the full target list.

| Target | |
|---|---|
| `make install` | build and register |
| `make status` | check an existing installation |
| `make verify-deps` | check the vendor binaries' signature and hashes |
| `make test` | signing test on a software token — no DSC, no PIN |
| `make test-token` | signing test on the real DSC — prompts for your PIN |
| `make diagnose` | list every certificate on the token and try signing with each |
| `make reinstall` | clean slate |
| `make uninstall` | remove everything |

**No `sudo` is required and nothing is installed outside your home directory.**
The vendor's PKCS#11 module links only against Apple's own frameworks, so it is
used directly from `~/Library/Application Support/eProcSigner/` — no kernel
extension, no launch daemon, no system-wide package. If you'd rather run the
vendor installer, that works too and takes precedence.

## Use

There is nothing to launch. Chrome starts the host itself when the portal asks
for a signature. Plug in the token, go to the portal, and sign as you would on
Windows. You'll get:

1. a **certificate chooser** listing the DSCs on your token, then
2. a **PIN prompt**, once per host launch.

> The PIN dialog belongs to a background process, so it can open *behind*
> Chrome. If a signature seems to hang, look in the Dock for a Java icon.

### Does it survive a reboot? Yes — and nothing autostarts

A native messaging host is not a daemon. Chrome `exec`s it when the portal asks
for a signature, talks to it over a pipe, and it exits with the tab. It *cannot*
be started at login: the protocol is that stdin/stdout pair, so a process
launched by `launchd` would have nothing to talk to.

What persists is a JSON manifest that Chrome reads at every startup. `make
install` writes it once. So there is no launch agent, no login item, and nothing
running in the background between signatures — and after a reboot it simply
works. `make status` confirms the files are still in place.

## Verify it works

```bash
make status        # installation intact? token visible? host answering?
make test          # full signing path on a throwaway software token
make test-token    # full signing path on your real DSC
```

`make test` and `make test-token` drive the stock `KeyStoreUtils` and
`SignatureGenerator` classes out of `eSigner.jar` and verify the resulting CMS
signature:

```
PASS  KeyStoreUtils.getMSKeyStore() returns a keystore
PASS    provider is named SunMSCAPI as the app expects
PASS  CertificatePanel-style lookup resolves
PASS  KeyStoreUtils.getKeyStoreList() finds key entries
PASS  private key handle obtained from token
      key class=sun.security.pkcs11.P11Key$P11RSAPrivateKeyInternal
PASS  SignatureGenerator.signData() produced output
PASS    output is a well-formed CMS SignedData

ALL CHECKS PASSED
```

The key class is the important line: `P11Key…Internal` means the private key is
a handle to the chip, not a copy in memory.

`make test` needs `brew install softhsm opensc` and generates its own throwaway
key. Prefer it during development — it spends no PIN attempts.

## Tested on

Everything below was verified end to end: certificates enumerated, PIN
prompted, CMS signature produced and verified against its signer certificate.

| | |
|---|---|
| macOS | 26.5.1 (build 25F80) |
| Hardware | Apple Silicon (arm64) |
| Java | OpenJDK 26.0.1 (Homebrew) |
| Browser | Google Chrome 150.0.7871.187 |
| Token | Hypersecu HYP2003, module `libcastle_v2.1.0.0.dylib` (universal) |
| Certificates | Class 3 individual DSCs from Capricorn and eMudhra — see [docs/CERTIFICATES.md](docs/CERTIFICATES.md) |
| Portal | Karnataka eProcurement, eSigner 1.9 |
| Make | GNU Make 3.81 (Apple, Xcode CLT) |

**Untested, and honestly so:**

- **Other macOS versions.** Nothing here uses a recent API — PC/SC and
  `SunPKCS11` are both long-stable — so macOS 12+ should be fine, but only
  26.5.1 has actually been run. `make install` warns on a mismatch and carries
  on; `make test` is the real answer.
- **Intel Macs.** The code path exists (`make install` sets `arch=x86_64` and
  `run-host.sh` relaunches under Rosetta if the module is Intel-only), but no
  Intel Mac has run it. The current module is universal, so this should not
  trigger at all.
- **Browsers other than Chrome.** Manifests are written for Chrome Beta/Canary,
  Chromium, Edge, Brave and Firefox because the format is identical, but only
  Chrome has been exercised.
- **Other tokens and other portals.** Any PKCS#11 token on any DXC portal
  running eSigner 1.9 should work, but that is reasoning, not evidence. Reports
  welcome.

## Troubleshooting

Start with `make status` — it checks the installed files, the module and its
architecture, each browser manifest, whether the token is visible, and whether
the host answers the protocol.

**Logs:**

```
~/Library/Logs/eProcSigner/signer.log
~/Library/Logs/eProcSigner/signer-debug.log
```

**Nothing happens when the portal tries to sign.** Chrome reads native-messaging
manifests only at startup — quit it fully with ⌘Q and reopen.

**Check the host by hand.** Native messaging frames each message with a 4-byte
little-endian length, so `\x1a` is the 26-byte request below:

```bash
printf '\x1a\x00\x00\x00{"appletMode":"CHECK_API"}' \
  | ~/Library/Application\ Support/eProcSigner/run-host.sh | xxd
```

You should see `{"hasNative":true,"appVersion":"1.9"}`.

**The portal rejects the signature, or you have several certificates and don't
know which to pick.** A DSC token often holds more than one — typically a
signing certificate *and* an encryption certificate that cannot sign at all,
both showing the same name in the chooser. Run:

```bash
make diagnose
```

One PIN prompt, then every certificate on the token with its key usage and an
actual signature attempt. `SIGNS OK` means the certificate works on this Mac —
so if the portal still refuses it, the problem is at the portal (usually the
DSC enrolled against your account is a different one). Worked example, including
why two certificates from the same CA behave differently, in
[docs/CERTIFICATES.md](docs/CERTIFICATES.md).

**"No certificates found."** Check the token is visible at all:

```bash
pkcs11-tool --module ~/Library/Application\ Support/eProcSigner/libcastle_v2.1.0.0.dylib -L
```

If that lists no slots, it's a token or driver problem, not this host.

**A different token.** Any PKCS#11 module works — set `pkcs11.library` in
`~/Library/Application Support/eProcSigner/esigner.properties`. Note that
**OpenSC will not drive an ePass2003/HYP2003**: it reaches the card, but the
token is initialised in Feitian's EnterSafe minidriver layout rather than
PKCS#15, so there is no `3f00/5015` directory for OpenSC's PKCS#15 layer to
bind to. Use the vendor module. See [docs/DESIGN.md](docs/DESIGN.md).

**The portal itself rejects you.** The host reports `osName`/`osArch` to the
portal. If the server-side refuses non-Windows clients, nothing local can fix
that — it has to go to portal support.

## Security

Three things move when you sign, and only one of them leaves your Mac:

![Where the PIN, the private key and the signature go](docs/secret-flow.svg)

The short version: the private key is generated on the token and cannot be
exported, so signing happens on the chip and Java only ever holds a handle. The
PIN lives in a `char[]`, goes to the token, and is zeroed — never disked, never
logged, never sent to the browser. Only the allow-listed extension can open the
pipe to the host. Nothing runs as root and nothing runs in the background.

Two things you should know rather than discover: **one PIN entry unlocks the
token for the life of the host process**, and **the vendor's signer uploads over
TLS with certificate and hostname verification disabled**. Both are inherited
from the Windows kit, not introduced here; the second is why you should sign
from a network you trust.

**[SECURITY.md](SECURITY.md)** has all of it in full — the threat model, the
`javap` output behind that TLS claim, how to obtain the vendor binaries without
being phished, and the commands to verify every assertion above yourself.

## How it works

Briefly: a JCE provider *named* `SunMSCAPI` offering a keystore *named*
`Windows-MY`, backed by PKCS#11 — so the signer's two hardcoded Windows lookups
resolve unchanged, against your token. Full write-up, including why the shim
forwards `Signature` and `MessageDigest` lookups, in
**[docs/DESIGN.md](docs/DESIGN.md)**.

## Scope

Built for one person's HYP2003 token on one portal, then written up properly.
Everything outside that one configuration is untested — see *Tested on* above,
which is a claim about evidence rather than about what ought to work.

**As-is, and unsupported.** No warranty, no support, no promise that an issue
gets a reply. Verify it works for you with `make test-token` before you depend
on it. Pull requests widening the tested surface are genuinely welcome —
[CONTRIBUTING.md](CONTRIBUTING.md).

Not affiliated with, endorsed by, or supported by DXC Technology, the
Government of Karnataka, or Hypersecu. Nothing here is decompiled, patched or
redistributed: `eSigner.jar` is used exactly as shipped, and both vendor
binaries are fetched from your own copies at build time.

## Licence

MIT — see [LICENSE](LICENSE). Applies to the code in this repository only. The
binaries `make deps` fetches remain under their vendors' own licences.
