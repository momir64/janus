<p align="center">
  <img src="https://github.com/user-attachments/assets/6120a935-4900-4a92-a627-482c4ed82455" alt="janus" width="480">
</p>

# Janus

Janus is not only the Roman god of doors and passages, it's also the name of my bachelor thesis about passkeys.
It's made out of three parts. Privezak is an Android passkey manager that keeps every key in the
phone's secure hardware and can prove it. Kredenac is a web app for notes and files with no
passwords, only passkeys, and it trusts the ones kept on Privezak a bit more than the rest.
Lokot is a command-line vault that keeps Kredenac's own secrets behind a passkey too. What started
as "replace the password field" became the most interesting thing I have worked on. FIDO2 turned 
out to be full of details that are easy to skip and rewarding to actually understand, from parsing 
an attestation certificate chain to deriving encryption keys from a passkey.

## The parts

### [Kredenac](kredenac), the cabinet

A web app for notes and files that you open with a passkey and nothing else. There are no
passwords, and your email only serves to send you a link for registering a passkey. Any
passkey gets you notes. Only a passkey that Privezak made, which the server can verify from
the attestation, also gets you files. Everything stored is encrypted under keys the database
never sees. A Ktor backend, a plain TypeScript frontend, Docker behind a Cloudflare tunnel,
and live at [kredenac.moma.rs](https://kredenac.moma.rs).

### [Privezak](privezak), the keychain

An Android app that stores passkeys and offers them to websites and apps through the
system's Credential Manager, like Google Password Manager does, except that nothing ever
leaves the phone. Every private key is created inside the phone's secure hardware, and every
registration comes with a certificate chain that proves it and names the app that created
the key. It also answers PRF requests and scans the QR codes that start a cross-device
sign-in.

### [Lokot](lokot), the padlock

A command-line vault for a project's secrets that a passkey opens instead of a master
password. It came out of kredenac needing a better answer than a `.env` file. The passkey's
hmac-secret output becomes the key that unwraps the vault, the secrets go to each container
as files that are removed again once the containers have read them, and the internal TLS
certificates are issued on the way. It is also a JVM library, which is how kredenac's backend
opens the same vault from a page in the browser. Kotlin/Native and libfido2, for Windows and
Linux.

## How they fit

```
Privezak (Android)                              lokot (CLI and JVM library)
  creates the passkey in hardware                 keeps kredenac's secrets in .env.lokot
  attaches an android-key attestation             opens it with a security key's hmac-secret
         |                                        or a browser passkey's PRF output
         |  WebAuthn registration and login                 |
         v                                                  |  one file per secret for each container,
Kredenac (web) <--------------------------------------------+  keys straight into the backend's memory
  verifies the chain up to Google's hardware root
  records the credential as Privezak-made
  issues sessions carrying a pzk claim
         |
         +--> notes: any passkey
         +--> files: pzk sessions only
```

Each project has its own README covering its design, layout, and how to build and run it.
The kredenac one also documents the whole deployed stack and how it is configured.

## What it demonstrates

Each of these is implemented in the repository rather than described, and the pointer says
where.

- **Passkeys as the only credential.** Kredenac has no password field anywhere. Sessions are
  issued only by a WebAuthn assertion (`routes/AuthRoutes.kt`), and the email magic link
  exists to register a passkey, never to sign in.
- **Username-less login.** Registration asks for `residentKey: "required"`, so the
  authenticator stores the credential, and login sends no `allowCredentials` at all: the
  authenticator offers the matching passkeys and the returned credential id identifies the
  account (`frontend/src/lib/webauthn/webauthn.ts`).
- **User verification, always.** Both sides insist on it. Kredenac rejects any assertion
  without the UV flag (`WebAuthnService.verifyUserVerified`), and Privezak never signs
  without a PIN or biometric unlock.
- **Attestation with a purpose.** Privezak returns an `android-key` attestation whose
  certificate chain reaches Google's hardware attestation root and whose key description
  names the app's package and signing certificate. Kredenac walks that chain, checks the
  challenge binding, and pins the release signing key (`AndroidKeyAttestation.kt`). The
  result is one boolean on the credential, surfaced as the `pzk` claim in the session token.
- **Authenticator-aware authorisation.** Routes registered with `privezakOnly = true` (all
  of `/files`) answer `403` to sessions without `pzk`. The frontend hides what the session
  cannot use.
- **Clone detection with the signature counter.** Privezak keeps a per-credential counter.
  Kredenac treats a counter that fails to increase as a cloned authenticator, deletes the
  credential and emails the owner (`AssertionUtil.kt`, `UserService.revokeCompromisedCredential`).
- **The PRF extension, from both sides.** Privezak evaluates `prf` and `prfAlreadyHashed`
  requests, including `evalByCredential`, with a per-credential HMAC key in the Keystore (`provider/Prf.kt`). Kredenac's
  backend is a relying party for the same extension: its
  unlock page asks a browser passkey for a PRF output and lokot turns that into the key that
  opens the vault (`lokot/src/jvmMain`, `plugins/RoutingUnlock.kt`).
- **`hmac-secret` as a key source.** The lokot CLI asks a security key for its `hmac-secret`
  over a vault-specific salt and derives the wrapping key from the answer with HKDF (`externals/Authenticator.kt`,
  `files/LokotFile.kt`). It hashes the salt the way browsers
  do for PRF, so a key enrolled from the terminal and one enrolled for the browser open the
  same file, and it enrols with CTAP 2.2's `hmac-secret-mc` so a new key is touched once.
- **Hybrid transport.** Privezak's scanner hands a `FIDO:/` QR code to the system, which
  completes a cross-device sign-in over the hybrid transport (`ui/screens/ScanScreen.kt`).
- **The Signal API.** After a rejected assertion, Kredenac's frontend tells the passkey
  manager the credential is gone, and the settings page reports the accepted list and user
  details after every change (`frontend/src/lib/webauthn/signal.ts`).
- **Hand-rolled wire formats.** Privezak and Kredenac encode and decode CBOR, COSE keys and
  authenticator data themselves, without a WebAuthn library, so every byte the thesis talks
  about is visible in the code. lokot goes the same way with its vault format, its TOML
  reader, its SFTP client and the certificates it issues through libcrypto.

## Standards and references

| Reference                                                                                                                                                                                           | Used for                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [A Tour of WebAuthn](https://www.imperialviolet.org/tourofwebauthn/tourofwebauthn.html) (Adam Langley)                                                                                              | The overall mental model, from U2F to passkeys, hybrid transport and the server side. Cited in Privezak's `Prf.kt` for how hybrid connections pre-hash PRF salts (`prfAlreadyHashed`).                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| [WebAuthn Level 3](https://www.w3.org/TR/webauthn-3/), [Level 2](https://www.w3.org/TR/webauthn-2/#sctn-alg-identifier)                                                                             | The API itself: ceremonies, `clientDataJSON`, authenticator data, attestation, extensions. The [`COSEAlgorithmIdentifier`](https://www.w3.org/TR/webauthn-3/#sctn-alg-identifier) section is why the frontend offers `-7`, `-257` and `-8` and why Kredenac's `VerifyUtil` also accepts the newer fully-specified identifiers such as `-9` and `-19`. The [android-key](https://www.w3.org/TR/webauthn-3/#sctn-android-key-attestation) format, the [prf](https://www.w3.org/TR/webauthn-3/#prf-extension) extension and the [signal methods](https://www.w3.org/TR/webauthn-3/#sctn-signal-methods) each have their own section. |
| [CTAP 2.2](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html)                                                                        | What lokot speaks to a security key through libfido2: the [`hmac-secret`](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html#sctn-hmac-secret-extension) extension it unlocks with, and [`hmac-secret-mc`](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html#sctn-hmac-secret-make-cred-extension), which lets enrolment return the output in the same touch.                                                                                                                                        |
| [RFC 8949](https://datatracker.ietf.org/doc/html/rfc8949#name-specification-of-the-cbor-e) CBOR                                                                                                     | The encoders and decoders: Privezak's `Cbor` object in `WebAuthn.kt`, Kredenac's `CborUtil.kt`, and the test-side `Cbor.kt`. Privezak emits shortest-form lengths as the deterministic encoding rules require.                                                                                                                                                                                                                                                                                                                                                                                                                    |
| [RFC 9052](https://datatracker.ietf.org/doc/html/rfc9052/#name-key-objects) COSE structures                                                                                                         | The COSE key map: `kty` at label `1`, `alg` at `3`, type-specific parameters at negative labels.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| [RFC 9053](https://datatracker.ietf.org/doc/html/rfc9053#x-table-ec-curves) COSE algorithms                                                                                                         | EC2 keys (`crv` `-1`, `x` `-2`, `y` `-3`), P-256 as curve `1`, ES256 as `-7`, EdDSA as `-8`. Privezak builds exactly this map in `coseKey()`, and Kredenac parses it in `parseAuthData`.                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| [RFC 8230](https://datatracker.ietf.org/doc/html/rfc8230/#page-6) RSA in COSE                                                                                                                       | RSA keys as `kty` `3` with `n` at `-1` and `e` at `-2`, which is what Kredenac reads when a security key registers with RS256.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| [IANA COSE registries](https://www.iana.org/assignments/cose)                                                                                                                                       | The authoritative numbers behind all of the above, including the deprecation of `-8` in favour of `-19`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| [Android key attestation](https://developer.android.com/privacy-and-security/security-key-attestation), [its schema](https://source.android.com/docs/security/features/keystore/attestation#schema) | The certificate chain Privezak returns and Kredenac verifies: the hardware root, the `KeyDescription` extension, and the `attestationApplicationId` that names the package and its signer.                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| [Credential Manager for providers](https://developer.android.com/identity/sign-in/credential-provider)                                                                                              | How Privezak plugs into Android: the service, the two-phase request model and the pending intents that open `CredentialActivity`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| [Passkey authenticator AAGUIDs](https://passkeydeveloper.github.io/passkey-authenticator-aaguids/explorer/?combined)                                                                                | Kredenac's `aaguid-names.json` is derived from this dataset and used for exactly what its maintainers intend: naming passkeys on the settings page, not making security decisions. Privezak's own AAGUID is not in the list, so it is added by hand.                                                                                                                                                                                                                                                                                                                                                                              |
| [SimpleWebAuthn: passkeys](https://simplewebauthn.dev/docs/advanced/passkeys/#authentication)                                                                                                       | The discoverable-credential flow Kredenac follows: `residentKey: "required"`, empty `allowCredentials`, and resolving the user from the returned credential. Kredenac is stricter on one point, requiring user verification rather than preferring it.                                                                                                                                                                                                                                                                                                                                                                            |
| [RFC 4231](https://datatracker.ietf.org/doc/html/rfc4231) HMAC test vectors, [RFC 5869](https://datatracker.ietf.org/doc/html/rfc5869) HKDF                                                         | lokot's own HKDF is built on HMAC because LibreSSL has no `EVP_KDF`. `lokot selftest` checks both against the RFC vectors on every platform it runs on (`checks/CryptoChecks.kt`).                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| [RFC 8018](https://datatracker.ietf.org/doc/html/rfc8018) PBKDF2, [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final) GCM                                                               | Privezak's PIN stretching, and the AES-GCM that every part of the project encrypts with.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| [RFC 9679](https://datatracker.ietf.org/doc/html/rfc9679#name-cose-key-thumbprint) COSE key thumbprint                                                                                              | Background reading on identifying keys by a hash of their COSE form. Not used in the code as of September 2026.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

## Repository layout

```
janus/
  privezak/        Android app (Gradle, Kotlin, Compose)
  kredenac/        web app: backend/ (Ktor), frontend/ (Vite), compose files, lokot.toml
  lokot/           secrets vault: CLI (Kotlin/Native) and JVM library
  docs/            GitHub Pages site for privezak.moma.rs
  LICENSE
```

## License

[TWAORPWB](LICENSE): thou wilt at own risk, praise with beer.
