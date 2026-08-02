# Contributing

## First, the honest part

**This is provided as-is, with no support from the author.**

I wrote it to sign my own tenders on my own Mac. It works there, completely and
repeatably. I am not running a help desk for it, and I can't debug your token,
your portal, your Java install or your Chrome profile — I don't have any of
them in front of me.

Concretely:

- **Issues may go unanswered.** No response time, no triage promise, and no
  commitment that a reported bug will ever be fixed.
- **I can't help with your DSC, your CA, or the portal.** Those belong to your
  Certifying Authority and to the portal operator. They have support desks; I
  am not one of them.
- **Nothing here is warranted.** See the [MIT licence](LICENSE) — it disclaims
  warranty and liability in the usual terms, and I mean them.
- **Test before you rely on it.** `make test-token` proves the whole signing
  path end to end. Run it before a deadline, not during one.

If that's not a footing you're comfortable on, keep the Windows machine. That's
a completely reasonable choice for something legally binding.

What I *will* do is merge good pull requests. That is the deal this repo
offers: the work is public so it doesn't have to be done twice.

## What's worth a PR

The design is deliberately narrow — one token, one portal, one Mac (see
[*Tested on*](README.md#tested-on)). Almost all of it is untested rather than
unsupported, which makes for good, cheap PRs:

**Another token.** Any PKCS#11 module should work — the shim only cares that
`SunPKCS11` can load it. If yours needed a change, say which token, which
module, and what you changed. If it worked with nothing but a `pkcs11.library`
edit, that's still worth a line in the README; "known to work" is information.

**Another portal.** Other states run DXC-built eProcurement portals with the
same eSigner. If yours works, add it. If it fails, the interesting detail is
*where* — certificate enumeration, signing, or the portal rejecting the client
outright over `osName`.

**Intel Macs.** The Rosetta path exists and has never run. If you have an Intel
Mac and can confirm it — or fix it — that closes a real gap.

**Another macOS version.** `make test-token` output plus your `sw_vers` is the
whole contribution. Add a row to *Tested on*.

**Another browser.** Manifests are written for Firefox, Edge, Brave, Chromium
and the Chrome channels because the format is identical. Only Chrome has been
exercised.

**Correctness or security.** Especially security — see [SECURITY.md](SECURITY.md)
for what is already known and deliberately documented.

## How

1. Fork, branch, and change what you need.
2. Run `make test` (software token — costs no PIN attempts). Run
   `make test-token` too if the change touches the signing path.
3. Say in the PR what you tested on: macOS version, hardware, JDK, token,
   portal. Untested guesses are fine if labelled as such — mislabelled ones are
   not, because someone will trust the label with a legally binding signature.
4. If it widens the tested surface, update *Tested on* in the README. That
   table is a claim about evidence, so only add a row for something you
   actually ran.

## Style

Match what's there. Shell is `bash` with `set -euo pipefail`; Java targets the
JDK's own PKCS#11 stack with no external dependencies; comments explain *why*,
since the *what* is usually plain enough.

Two hard rules:

- **No third-party binaries in the repo.** `bootstrap.sh` extracts them from
  the user's own copies, and it stays that way.
- **No key material or PINs in the repo, ever** — not in tests, not in
  fixtures, not in an example config. `test/setup-softhsm.sh` generates a
  throwaway key at runtime; extend that instead.
