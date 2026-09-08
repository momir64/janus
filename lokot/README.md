# Lokot

Lokot is a small vault for a project's secrets that opens with a passkey. Instead of a `.env`
that anything on the machine can read, the secrets live encrypted in `.env.lokot`, a file that
is safe to commit, and the key that opens it comes out of a FIDO2 authenticator every time it
is needed. A security key, Windows Hello or a passkey in a browser can all open it, and without
one of the enrolled keys present the file is just bytes.

It was written for [kredenac](../kredenac), which used to have a `.env`, two certificate scripts
and a handful of "remember to set this" notes. Now it has a `lokot.toml` that says what the
vault holds and which container gets what, and the backend opens the vault itself from a page
in the browser. Kotlin/Native for the command line on Windows and Linux, plus a JVM target for the
library half.

## From the passkey to the secrets

A FIDO2 authenticator can keep a secret HMAC key per credential and evaluate it over a salt you
hand it, without that key ever leaving the device. CTAP calls the extension `hmac-secret` and
WebAuthn exposes it to browsers as `prf`. Lokot rests entirely on it.

```
passkey --hmac-secret over the vault's salt--> 32 bytes --HKDF--> wrapping key
                                                                      |
header:  cred.N.kek = AES-256-GCM(wrapping key, KEK)  <---------------+

body:    AES-256-GCM(KEK, schema + secrets), with the header as associated data
```

Every enrolled key wraps the same KEK, so adding a key adds four lines to the header, and
rekeying replaces the KEK and the salt. The header is plaintext, so `git diff` shows which keys
are enrolled and under which relying party, but it is also the associated data of the body:
change a byte of it and the body refuses to open. Every write draws a fresh nonce, so two
commits of the same secrets do not look alike.

The salt is hashed the way browsers hash PRF inputs, `SHA-256("WebAuthn PRF" || 0x00 || salt)`,
before it reaches the authenticator. The same credential therefore gives the same 32 bytes
whether the CLI asks over CTAP or a page asks through `navigator.credentials.get`, which is
what lets one vault open from a terminal and from a browser with the same key. Enrolment asks
for `hmac-secret-mc`, the CTAP 2.2 extension that returns the output already at `makeCredential`,
so a new key is touched once. Older keys are asked a second time.

## The schema

`lokot.toml` beside the vault says what it holds. This is kredenac's, shortened:

```toml
project = "kredenac"

[secrets]
POSTGRES_DB = { type = "prompt", hint = "database name" }
POSTGRES_PASSWORD = { type = "random", chars = 32 }
MASTER_KEY_BASE64 = { type = "random", bytes = 32 }
POSTGRES_PORT = { type = "port" }

[certs.redis]
cn = "localhost"
alt = ["DNS:localhost", "DNS:redis", "IP:127.0.0.1"]

[deliver.postgres]
POSTGRES_DB = "db"
POSTGRES_PASSWORD = "password"

[deliver.redis]
REDIS_PASSWORD = { file = "redis.conf", prefix = "requirepass " }
REDIS_CRT = "redis.crt"
CA_CRT = "ca.crt"

[compose]
env = ["POSTGRES_PORT", "RP_ID"]
```

- `[secrets]` declares every value. A `prompt` is typed in at `init`, a `random` one is
  generated as `bytes` (base64, or `encoding = "hex"`) or as `chars` from a URL-safe alphabet,
  and a `port` is drawn from the dynamic range so it collides with nothing registered.
- `[certs.<name>]` makes lokot a tiny certificate authority. `init` generates a CA, signs one
  RSA 2048 leaf per table with the names given, and stores `<NAME>_CRT`, `<NAME>_KEY` and
  `CA_CRT` as ordinary secrets. The CA key signs and is then dropped, never written anywhere,
  which is fine for certificates whose only job is to let containers on one compose network
  verify each other. `days` under `[certs]` sets the lifetime, ten years by default.
- `[deliver.<service>]` says which secret becomes which file in that service's directory, with
  an optional `prefix` so a value can land inside a config line. `init = true` marks a group
  that only a first run needs.
- `[compose] env` lists the few values docker compose has to interpolate before anything runs,
  ports mostly. They go into a generated `.env` beside the compose file, so real secrets do not
  belong there.

The schema travels inside the encrypted body, so `unlock` needs no `lokot.toml` at all, and
every value is checked against it: a port is a number, 32 bytes decode to 32 bytes, a
certificate starts with `-----BEGIN`, nothing is missing and nothing is undeclared. `edit`
checks before it writes, and `unlock` refuses a vault that does not match its own schema, so a
bad edit cannot leave a half-configured deployment. When `lokot.toml` beside the vault has
changed, `edit` takes the new one, generates whatever it newly declares, and shows the result
for you to save.

## Commands

```
lokot init       create .env.lokot from lokot.toml, enrolling a passkey
lokot unlock     open .env.lokot with an enrolled passkey and write the service files
lokot lock       delete the files 'unlock' wrote
lokot edit       change the values in .env.lokot, in a full-screen editor
lokot add-key    enrol another passkey, so losing one does not lose the vault
lokot rekey      re-key the vault, keeping only the passkeys you present
lokot selftest   check the crypto and file format against known answers
```

Every command takes a target: nothing for the current directory, a directory, or
`user@host:/path` for one on another machine. For a remote target lokot runs
`ssh -s host sftp` and speaks SFTP to it over the pipe, so the vault is read from and the files
are written to that machine while the passkey stays plugged into yours. Whatever ssh would ask
you normally, it still asks.

`init` prompts for the `prompt` values without echoing them, generates the rest, issues the
certificates, then enrols the key you have plugged in. On Windows the key is whatever Windows
Hello offers, which can be a security key or the device itself. `--rp <id>` enrols the
credential under that relying party id instead of lokot's own `lokot.localhost`. A browser at
`https://<id>/` can then use it, and so can the CLI, since it enrolled the key directly.
`add-key` takes the same flag, and when keys of more than one family are enrolled, `unlock`
asks which to use.

`unlock` writes the files and the `.env`, then reminds you to `lock` once the containers are
up. `-i` also writes the groups marked `init = true`. `lock` removes the directory and the
`.env`, but only an `.env` that starts with `# generated by lokot`, since one you wrote yourself
is not lokot's to delete.

`edit` opens the whole document in a small editor: `^S` writes, `^X` quits, arrows and page
keys move. A value that spans lines is written as a `"""` block, which is how the PEM
certificates appear. Quitting with unsaved changes offers to save them, and if the schema
rejects the buffer it offers only to return or to discard.

`rekey` draws a new KEK and a new salt, asks you to present each key you want to keep, and
drops the rest. The dropped keys lose access permanently, which is the point.

`selftest` runs about 180 checks against RFC vectors and known answers: the crypto, the file
format, the document format, the TOML reader, the schema rules and the editor. Run it on a new
machine before trusting a build there. Debug builds also take `--trace`, which logs every CTAP
exchange including key material, so only ever use it on a throwaway vault.

## Where the files go

`unlock` writes one directory per service under a root only your user can enter:

|         | root                              | inside                        |
|---------|-----------------------------------|-------------------------------|
| Linux   | `/dev/shm/lokot-<uid>`, mode 0700 | `<service>/` 0755, files 0644 |
| Windows | `.lokot-secrets` beside the vault | same names, no modes          |

`/dev/shm` is the tmpfs Linux already has, so the secrets never touch a disk and a reboot
clears them regardless. The root is closed to everyone else, while the files inside are world
readable on purpose: a container reads them as its own uid, 999 for postgres, 65532 for
cloudflared, not as you, and the root directory is what keeps other users out. Compose mounts
only `${LOKOT_DIR}/<service>` into each container, so a container's mount namespace holds its
own secrets and nothing else.

`unlock` clears the whole root before writing, so what is there afterwards is exactly what the
current schema says. A remote target gets the same `/dev/shm` path with the uid ssh reports, or
`.lokot-secrets` in the directory if it cannot tell. The `.env` gets a header saying it was
generated, the `LOKOT_DIR` line compose needs for the mounts, and the `[compose]` values.

## The library

The same Gradle project has a `jvm` target, `lokot-<version>.jar`, so a Kotlin server can open
the vault without the CLI. It holds the file format, the crypto over JCA and an unlock page,
and nothing else: no libfido2, no editor. kredenac's backend uses it like this:

```kotlin
val vault = Lokot(Path(".env.lokot"))

// served while locked, on the host the passkeys are enrolled for
get { call.respondText(Lokot.page(), ContentType.Text.Html) }
get("challenge") { call.respondText(vault.challenge(rpId, home).json, ContentType.Application.Json) }
post("unlock") {
    if (vault.unlock(call.receiveText())) call.respond(HttpStatusCode.NoContent)
    else call.respond(HttpStatusCode.Unauthorized)
}

vault.awaitUnlock()                                    // blocks until a passkey opened it
val key: ByteArray = vault.getBytes("MASTER_KEY_BASE64")
val chars: CharArray = vault.get("POSTGRES_PASSWORD")  // your copy, wipe it when done
vault.lock()                                           // wipes everything it held
```

`challenge` lists the credentials enrolled for that relying party id, the salt, and where to go
afterwards. The page asks the browser for an assertion with `prf.eval.first` set to the salt,
posts the credential id and the PRF output back, and lokot unwraps the KEK with them. There is
no separate authentication step: a wrong key simply fails to unwrap. Values come out as
`CharArray` rather than `String` so the caller can overwrite them once a driver has taken its
copy. `Lokot.page(base)` rewrites the page's `<base href>` when it is served from somewhere
other than the root of its host.

## The file

```
"LOKOT\0"  u8 version  u32be headerLen  "\n"  header  "\n"  nonce(12)  ciphertext  tag(16)
```

The header is a `name = value` document:

```
project      = kredenac
salt         = <32 bytes, hex>
cred.0.id    = <credential id, hex>
cred.0.rp    = lokot-kredenac.moma.rs
cred.0.nonce = <12 bytes, hex>
cred.0.kek   = <the wrapped KEK, hex>
```

Once opened, the body is `u32be schemaLen`, the schema text, and the secrets in the same
`name = value` format, with `"""` blocks for values that span lines. The format version stays at
1 until the first release, so a vault from before then may need re-creating.

## Building

libfido2 1.17.0 is vendored under `vendor/libfido2`, built from source with one patch.
Upstream refuses `android-key` attestation statements, and that is what every Android
credential provider returns since Android made hardware attestation the default, so without
the patch a phone could not be enrolled. On Windows there is no way around it, because the
Windows Hello backend always asks for attestation. 1.17.0 is also the first release with
`hmac-secret-mc`, which is why the distro packages (1.14 on Ubuntu 24.04) are not used. The
patch is in `vendor/patches`, and lokot never verifies attestation anyway, it only keeps the
credential id.

**Windows.** `./gradlew build` links against `vendor/libfido2/win64` and copies `fido2.dll`,
`crypto-56.dll` (LibreSSL), `cbor.dll` and `zlib1.dll` beside the executable, which needs them
at runtime. The result is `build/mingw/bin/host/releaseExecutable/lokot.exe` with the DLLs
beside it. The static libraries Yubico ships are MSVC link-time-codegen objects that the
Kotlin/Native linker cannot consume, so the DLLs stay.

**Linux.** Install the headers once, let Gradle fetch the Kotlin/Native toolchain, build
libfido2 with that toolchain, then build lokot:

```bash
sudo apt install build-essential cmake pkg-config libcbor-dev libssl-dev zlib1g-dev libudev-dev
./gradlew build            # fails at the link the first time, but fetches the toolchain
./vendor/build-libfido2.sh
./gradlew build
```

The script explains why it compiles against Kotlin/Native's own glibc 2.19 sysroot rather than
with the system compiler: a libfido2 built against a modern glibc drags in symbols that sysroot
does not have, and the link fails on them. libfido2 is linked statically, while libcbor,
libcrypto, libudev and libz stay system libraries. The binary is
`build/linux-x86_64/bin/host/releaseExecutable/lokot.kexe`. The static archives for both Linux
architectures are committed, so a clone needs the script only to rebuild them, but it still
needs the `-dev` packages to link.

**Raspberry Pi.** The Kotlin/Native compiler runs on x86_64 Linux, Windows and macOS only, so an
aarch64 binary is cross-built with `-Plokot.arch=aarch64` from an x86_64 Linux machine, with the
Pi's headers and shared libraries copied into `vendor/downloads/sysroot-aarch64`. The top of
`build-libfido2.sh` has the exact commands.

**The jar.** `./gradlew jvmJar` writes `build/<host>/libs/lokot-jvm-0.0.1.jar`. kredenac keeps a
copy under `backend/libs`, and its build warns when that copy is older than lokot's sources.

**Tests.** `./gradlew hostTest jvmTest` runs every `selftest` check under Gradle, plus the
library's own tests: a vault round trip, the posted body, the challenge, `awaitUnlock` and the
page's base. The AES-GCM known answer is the same on both sides, so the native and the JVM
crypto are pinned to each other.

## Layout

```
src/commonMain/      the file format, the document format and the crypto interface, shared by both targets
src/hostMain/        the CLI: commands, editor, libfido2 and libcrypto bindings, schema, TOML, SFTP
src/posixMain/       terminal, files and processes on Linux
src/windowsMain/     the same on Windows
src/jvmMain/         the Lokot class, JCA crypto, the unlock page
src/hostTest/        runs every selftest check under Gradle
src/jvmTest/         the library's tests
src/nativeInterop/   cinterop definitions for fido.h and libcrypto
vendor/              libfido2 1.17.0 (headers, static libraries, Windows DLLs), LibreSSL headers, the patch, the build script
```

## Limits worth knowing

- The TOML reader covers what the schema needs: strings, integers, booleans, lists, inline
  tables and dotted headers. Floats, dates, multi-line strings and arrays of tables are refused
  with a message rather than misread.
- A remote write goes straight over SFTP without a rename, so a connection lost halfway can
  leave a truncated vault on the other side. Local writes go through a temporary file. Keep the
  vault in git, which is what it is for.
- On Windows the secrets directory is an ordinary folder beside the vault, not tmpfs, and no
  ACL is set on it. It exists for running the containers from a Windows machine while
  developing, and `lock` removes it.
- Losing every enrolled key loses the vault. There is no recovery, which is why `init` nags
  about `add-key`.

## Further reading

- [CTAP 2.2](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html):
  the [hmac-secret](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html#sctn-hmac-secret-extension)
  and [hmac-secret-mc](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html#sctn-hmac-secret-make-cred-extension)
  extensions
- [WebAuthn Level 3, the prf extension](https://www.w3.org/TR/webauthn-3/#prf-extension), which the unlock page uses and
  where the salt hashing comes from
- [RFC 5869](https://datatracker.ietf.org/doc/html/rfc5869) HKDF
  and [RFC 4231](https://datatracker.ietf.org/doc/html/rfc4231) HMAC-SHA-256, whose test vectors `selftest` checks
  against
- [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final), AES-GCM
- [libfido2](https://developers.yubico.com/libfido2/)
- [SFTP version 3](https://datatracker.ietf.org/doc/html/draft-ietf-secsh-filexfer-02), the subset the remote target
  speaks
- [TOML v1.0.0](https://toml.io/en/v1.0.0)
