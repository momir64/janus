# Kredenac backend

A Ktor server that does three things: runs WebAuthn ceremonies and turns them into
sessions, keeps every stored byte encrypted under keys the database never sees, and
decides, from the attestation a passkey was registered with, which sessions are allowed
to touch files. It serves the built frontend when `FRONTEND_DIST_PATH` is set, and before
any of that it opens its own secrets vault with a passkey.

Kotlin 2.4 on JVM 21, Ktor 3.5 with Netty, Exposed and HikariCP on Postgres, Lettuce on
Redis, the MinIO SDK for object storage, Koin for wiring, Resend for email and
[lokot](../../lokot) for the secrets. There is no WebAuthn library: attestation and assertion
parsing, CBOR, COSE keys and the Android key attestation chain are all in `crypto/webauthn`,
which keeps the trust decisions in one readable place.

## Starting up

`main.kt` starts two servers in turn on the same TLS port. The first is a gate. It serves the
unlock page and its two endpoints for the `LOKOT_RP_ID` host, answers `503` under `/api`, and
serves the frontend, so a visitor sees the ordinary "can't reach the server" state rather than
nothing. When a passkey enrolled for that host opens the vault in the browser, `awaitUnlock`
returns, the gate stops, and the real application starts in its place. From then on the
unlock host answers `404`.

The vault is `.env.lokot` beside the compose file, or one directory up when the server runs
from `backend/` in the IDE. Every secret is read exactly once, while Koin wires the services:
the keys come out as bytes, the credentials for Postgres, Redis, MinIO and Resend as text,
and the JWT and CSRF secrets are wiped as soon as their `SecretKeySpec` holds a copy. The last
line of `Application.module` locks the vault again, which wipes everything it still held.
lokot hands values out as `CharArray` so they can be overwritten, and the backend keeps them
that way except where a driver only takes a `String`, which HikariCP, the Postgres driver,
MinIO and Resend do.

What this buys is that no secret sits in an environment variable, in a file the process
reads, or in a string that outlives its use. A dump of the container's environment shows
hostnames and ports. What still comes from the environment (`common/Env.kt`, with a `.env`
fallback for the IDE):

| Variables                                                                                | What they are                                                         |
|------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| `POSTGRES_HOST`, `POSTGRES_PORT`, `REDIS_HOST`, `REDIS_PORT`, `MINIO_HOST`, `MINIO_PORT` | where the services are                                                |
| `KTOR_PORT`                                                                              | the port to listen on                                                 |
| `RP_ID`, `LOKOT_RP_ID`                                                                   | the users' relying party id, and the host that serves the unlock page |
| `BACKEND_TLS_CERT_PATH`, `BACKEND_TLS_KEY_PATH`, `REDIS_TLS_CA_PATH`                     | the certificate lokot issued, its key, and the CA to trust Redis by   |
| `FRONTEND_DIST_PATH`                                                                     | the built frontend, optional                                          |

The vault holds the rest: `MASTER_KEY_BASE64`, `PII_ENCRYPTION_KEY_BASE64`,
`TOKEN_ENCRYPTION_KEY_BASE64`, `DB_HMAC_SECRET`, `JWT_SECRET`, `CSRF_SECRET`, `RP_ORIGIN`, the
Postgres database, user and password, the Redis password, the MinIO root user, password and
bucket, and the Resend key and sender. The [stack README](../README.md#secrets-and-configuration)
says where each one is set.

## Authentication

**Registering** takes two steps that never reveal whether an email already has an account:

1. `POST /auth/register/verify` with an email. A random token goes into Redis for 15 minutes
   and a magic link is mailed: a sign-up link for a new address, an "add a passkey" link
   for an existing one. Either way the response is `204`.
2. The link opens the frontend, which calls `register/start` with the token to get a
   challenge, and `register/finish` with the attestation. The token is consumed only after
   the attestation verifies, so a failed ceremony doesn't burn it. This same path is the
   recovery flow: an account that lost its last passkey gets a new one by email.

Every challenge lives in Redis for 3 minutes and is paired with a `challenge_session` cookie,
an HMAC of the challenge, so only the browser that asked for a challenge can finish it.

**Signing in** is `login/start` then `login/finish` with an assertion. The credential is looked
up, the signature checked, and the signature counter compared: a counter that did not increase
means a cloned authenticator, and the credential is deleted on the spot with an email to the
owner (`passkey_cloned`). A counter that stays at zero is left alone, since the spec allows
authenticators that do not count. A successful login yields:

- `access_token`: an HS256 JWT cookie (`HttpOnly`, `Secure`, `SameSite=Strict`, path `/`)
  valid for 5 minutes, with claims `sub` (user), `sid` (session), `cid` (credential) and
  `pzk` (Privezak-attested).
- `refresh_token`: an opaque cookie scoped to `/api/auth`, valid 30 days, stored as an HMAC.
  Refreshing rotates it within a chain, and presenting a rotated token again after a 5-second
  grace period is treated as theft and revokes the chain.
- a CSRF token in the response body, `HMAC(sid)`, that must come back as `X-CSRF-Token` on
  every non-safe method. The cookies are `SameSite=Strict` already, so this is a second line
  rather than the only one.

Adding a passkey or deleting the account require a fresh assertion from an existing
passkey first (`reauth/*`, `credentials/add/verify`), which yields a 2-minute token bound
to the session.

**Privezak detection** happens once, at registration. If the attestation format is
`android-key`, the chain verifies up to Google's hardware attestation root, the leaf key is
the credential's key, the attestation challenge is this registration's client-data hash,
and the key description names package `rs.moma.janus.privezak` signed by a certificate in
`PRIVEZAK_SIGNERS`, the credential is stored with `privezak = true`. Routes registered with
`privezakOnly = true` (all of `/files`) refuse other sessions with `403`.

## Data at rest

The database and object store hold nothing readable:

| What                        | How                                                                                                                              |
|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Email                       | AES-GCM under `PII_ENCRYPTION_KEY`, plus an HMAC under `DB_HMAC_SECRET` for lookup                                               |
| Last-used IP and location   | AES-GCM under `PII_ENCRYPTION_KEY`                                                                                               |
| Per-user data key           | random 256-bit key, wrapped by `MASTER_KEY` (envelope encryption)                                                                |
| Notes                       | title and body AES-GCM under the user's key                                                                                      |
| Files                       | name and content AES-GCM under the user's key, with content bound to the file ID via AAD and streamed to MinIO without buffering |
| Credentials, refresh tokens | an HMAC over every column, checked on read                                                                                       |
| Redis entries               | keyed by HMAC of the token, with the value (bound email, user or challenge) AES-GCM under `TOKEN_ENCRYPTION_KEY`                 |

A failed GCM tag or integrity hash raises `CompromisedException`, which is logged in full
and surfaces to the client as an unremarkable `500`. Nothing enforces that a new column is
included in a row's integrity hash or that AAD matches on both sides. Those are the
invariants to keep in mind when touching the repositories.

## API

All routes live under `/api`. Unauthenticated ones are rate-limited per client IP (`CF-Connecting-IP` behind the
tunnel): 3/min for magic links, 15/min for ceremonies,
30/min for refresh.

| Method      | Path                                                | Notes                                                                             |
|-------------|-----------------------------------------------------|-----------------------------------------------------------------------------------|
| POST        | `/auth/register/verify`                             | `{email}`, answers 204 and sends a magic link                                     |
| POST        | `/auth/register/start`                              | `{token}`, answers challenge, rpId, email, userHandle, excludeCredentials         |
| POST        | `/auth/register/finish`                             | `{clientDataJSON, attestationObject}`, answers 201                                |
| POST        | `/auth/login/start`                                 | answers `{challenge, rpId}`                                                       |
| POST        | `/auth/login/finish`                                | `{credentialId, clientDataJSON, authenticatorData, signature}`, answers a session |
| POST        | `/auth/refresh`                                     | rotates the refresh cookie, answers a session                                     |
| POST        | `/auth/logout`                                      | revokes the refresh chain, clears cookies                                         |
| GET         | `/auth/credentials`                                 | passkeys with device name (from AAGUID), last use, `currentSession` (1)           |
| DELETE      | `/auth/credentials/{id}`                            | emails the owner (1)                                                              |
| POST        | `/auth/credentials/add/start`, `/verify`, `/finish` | assert with an existing passkey, then register a new one (1)                      |
| POST        | `/auth/reauth/start`, `/finish`                     | assert with an existing passkey, answers a `{token}` for destructive actions (1)  |
| DELETE      | `/auth/account`                                     | `{token}` from reauth. Removes everything and emails the owner (1)                |
| GET, POST   | `/notes`                                            | list, or create `{title, content}` (1)                                            |
| PUT, DELETE | `/notes/{id}`                                       | update `{title, content}`, or delete (1)                                          |
| GET, POST   | `/files`                                            | list, or multipart upload with `size` **before** `file` (1) (2)                   |
| GET, DELETE | `/files/{id}`                                       | download as attachment, or delete (1) (2)                                         |

(1) needs a session and, for anything but GET, the CSRF header. (2) needs the `pzk` claim, so only sessions opened with
a Privezak-attested passkey.

Errors are JSON `{message, code?}`. The codes the frontend acts on are `passkey_unknown`,
`passkey_cloned`, `file_limit` and `note_limit`.

Limits, in `common/Limits.kt`: 50 MB per file, 100 files and 1000 notes per account, 255
characters of filename, 200 of note title, 2000 of note body.

## Layout

```
src/main/kotlin/rs/moma/janus/kredenac/
  main.kt                  the gate, then the real server, both on the TLS connector
  Application.kt           plugin order, and the vault lock at the end
  plugins/                 unlock routes, auth provider and the authenticated* route builders,
                           rate limits, security headers, DB bootstrap, Koin module, hourly cleanup
  routes/                  auth, notes, files
  services/                UserService, NotesService, FilesService, EmailService (Resend)
  repositories/            Exposed tables as encrypted rows, Redis tokens, MinIO blobs
  crypto/webauthn/         ceremony verification, CBOR, Android key attestation
  crypto/authentication/   JWT, refresh tokens, CSRF, magic links
  crypto/algorithms/       AES-GCM, HMAC, ES256/RS256/EdDSA verification
  tables/                  schema, created and column-migrated on startup
  common/                  Env, the vault, TLS helpers, Owner context, exceptions, limits, client-info parsing
src/main/resources/
  emails/                  HTML templates, filled with escaped values
  aaguid-names.json        AAGUID to authenticator name for the settings page
libs/lokot.jar             lokot's JVM target, copied in from a lokot build
```

`Owner` is a context parameter: a route body runs inside `context(Owner(userId, privezak))`
and every repository query is scoped by it, so a handler cannot forget to filter by user.

## Running and testing

With the data services from compose and the vault unlocked on this machine:

```bash
docker compose -f ../docker-compose.yml -f ../docker-compose.dev.yml up -d postgres redis minio
./gradlew run          # or run MainKt from the IDE
./gradlew test
./gradlew buildFatJar  # what the Dockerfile does
```

Outside compose nothing sets the hosts or the certificate paths, so the run configuration has
to: `POSTGRES_HOST`, `REDIS_HOST` and `MINIO_HOST` are `localhost`, and the three TLS paths
point into `$LOKOT_DIR/backend`, where `lokot unlock` put them. The ports, `RP_ID` and
`LOKOT_RP_ID` come from the generated `../.env`, which the server and the Gradle `test` task
both read.

Tests run against the real Postgres and Redis, in a separate `kredenac_test` database and
Redis index 15, both cleared before each test. They take the database credentials, the Redis
password and the CA from the files under `$LOKOT_DIR`, so they run between an `unlock` and a
`lock`, and they want `POSTGRES_HOST` and `REDIS_HOST` in the environment. `utils/Authenticator.kt`
is a software authenticator that produces valid attestations and assertions, and
`utils/KeyAttestation.kt` builds Android-style attestation chains against a test root, so the
Privezak path is covered without a device.
