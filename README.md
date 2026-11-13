# Janus

<img align="right" src="https://github.com/user-attachments/assets/3fa04a84-116c-4316-8181-be16d5207700" alt="janus" height="220px">

Janus is not only the Roman god of doors and passages, it's also the name of my bachelor thesis about 
passkeys. It's made out of three parts. Privezak is an Android passkey manager that keeps every key in 
the phone's secure hardware. Kredenac is a web app for notes and files with no passwords, only passkeys, 
and it trusts the ones kept on Privezak a bit more than the rest. Lokot is a command-line vault that keeps 
Kredenac's own secrets behind a passkey too. What started as "eliminating passwords" became the most interesting 
thing I've worked on. FIDO2 turned out to be full of details that are easy to overlook, but rewarding to actually 
understand, from certificate chain attestation to deriving encryption keys from a passkey.

## The parts

### [Kredenac](kredenac), the cabinet

A web app for notes and files that you open with a passkey and nothing else. There are no
passwords, and your email only serves to send you a link for registering a passkey. Any
passkey gets you notes. Only a passkey that Privezak made, which the server can verify from
the attestation, also gets you files. Notes and files are stored encrypted, under keys the database
never sees. A Ktor backend, a plain TypeScript frontend, Docker behind a Cloudflare tunnel,
and live at [kredenac.moma.rs](https://kredenac.moma.rs).

### [Privezak](privezak), the keychain

An Android app that stores passkeys and offers them to websites and apps through the
system's Credential Manager, like Google Password Manager does, except that your passkeys never
leave the phone. Every private key is created in the phone's secure hardware, and a
registration comes with a certificate chain that proves it and names the app that created
the key. It also answers PRF requests and scans the QR codes that start a cross-device
sign-in.

### [Lokot](lokot), the padlock

A command-line vault for a project's secrets that a passkey opens instead of a master
password. It came out of kredenac needing a better answer than a `.env` file. The passkey's
hmac-secret output becomes the key that unwraps the vault, the secrets go to each container
as files that one command removes again once the containers have read them, and the internal TLS
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

## Core features

The main things Janus does. All of it is in the code, so you can go and read how it works.

- Kredenac has no password field anywhere. You sign in by answering a passkey challenge, and the passkey itself tells the server who you are, so there is no username to type. The email link only exists to register a new passkey.
- Privezak can prove where its keys live. A registration comes with an Android key attestation, and Kredenac checks the whole certificate chain up to Google's hardware root. Sessions that pass get a `pzk` claim, and that is what unlocks the file storage (`AndroidKeyAttestation.kt`).
- Copied keys get caught. Passkeys keep a signature counter, and if Kredenac ever sees it fail to go up, it assumes the key was cloned, deletes the credential and emails the owner (`AssertionUtil.kt`).
- A passkey can also be an encryption key. Through the PRF extension (`hmac-secret` on a security key) it hands back a secret that only it can produce. Privezak answers those requests (`provider/Prf.kt`), and Lokot turns the result into the key that opens its vault, from a terminal with a security key or from a page in the browser.
- Signing in on another device works. Scan the QR code on a computer's login screen with Privezak's scanner and the system finishes the sign-in over the hybrid transport.
- There is no WebAuthn library in it. CBOR, COSE keys and authenticator data are encoded and decoded by hand in Privezak and Kredenac, which is a big part of why I understand them as well as I do now.

## Sources and standards

The specs, articles and documentation the project draws on, with a note on where each one shows up.

- [A Tour of WebAuthn](https://www.imperialviolet.org/tourofwebauthn/tourofwebauthn.html) by Adam Langley. The big picture, from U2F to passkeys and hybrid transport.
- [WebAuthn Level 3](https://www.w3.org/TR/webauthn-3/). The API itself: ceremonies, attestation formats, the PRF extension and the signal methods.
- [CTAP 2.2](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html). How Lokot talks to a security key through libfido2, and where `hmac-secret` comes from.
- [Android key attestation](https://developer.android.com/privacy-and-security/security-key-attestation). The certificate chain Privezak returns and Kredenac verifies.
- [Credential Manager for providers](https://developer.android.com/identity/sign-in/credential-provider). How an app like Privezak plugs into Android.
- [RFC 8949](https://datatracker.ietf.org/doc/html/rfc8949) (CBOR) and [RFC 9052](https://datatracker.ietf.org/doc/html/rfc9052) (COSE). The two encodings a passkey is built from.

## Repository layout

```
janus/
  privezak/        Android app (Gradle, Kotlin, Compose)
  kredenac/        web app: backend/ (Ktor), frontend/ (Vite), compose files, lokot.toml
  lokot/           secrets vault: CLI (Kotlin/Native) and JVM library
  docs/            the bachelor thesis (PDF and Word)
  LICENSE
```

## License

[TWAORPWB](LICENSE): thou wilt at own risk, praise with beer.
