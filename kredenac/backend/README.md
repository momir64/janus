# Kredenac backend

A Ktor server that does three things: runs WebAuthn ceremonies and turns them into
sessions, keeps every stored byte encrypted under keys the database never sees, and
decides, from the attestation a passkey was registered with, which sessions are allowed
to touch files. It also serves the built frontend when `FRONTEND_DIST_PATH` is set.

Kotlin 2.4 on JVM 21, Ktor 3.5 with Netty, Exposed + HikariCP on Postgres, Lettuce on
Redis, the MinIO SDK for object storage, Koin for wiring and Resend for email. There is no
WebAuthn library: attestation and assertion parsing, CBOR, COSE keys and the Android key
attestation chain are all in `crypto/webauthn`, which keeps the trust decisions in one
readable place.

## Authentication

**Registering** takes two steps that never reveal whether an email already has an account:

1. `POST /auth/register/verify` with an email. A random token goes into Redis for 15 minutes
   and a magic link is mailed: a sign-up link for a new address, an "add a passkey" link
   for an existing one. Either way the response is `204`.
2. The link opens the frontend, which calls `register/start` with the token to get a
   challenge, and `register/finish` with the attestation. The token is consumed only after
   the attestation verifies, so a failed ceremony doesn't burn it. This same path is the
   recovery flow: an account that lost its last passkey gets a new one by email.

**Signing in** is `login/start` -> `login/finish` with an assertion. The credential is looked
up, the signature checked, and the signature counter compared: a counter that did not
increase means a cloned authenticator, and the credential is deleted on the spot with an
email to the owner (`passkey_cloned`). A successful login yields:

- `access_token`: an HS256 JWT cookie (`HttpOnly`, `Secure`, `SameSite=Strict`, path `/`)
  valid for 5 minutes, with claims `sub` (user), `sid` (session), `cid` (credential) and
  `pzk` (Privezak-attested).
- `refresh_token`: an opaque cookie scoped to `/api/auth`, valid 30 days, stored as an HMAC.
  Refreshing rotates it within a chain, and presenting a rotated token again after a 5-second
  grace period is treated as theft and revokes the chain.
- a CSRF token in the response body, `HMAC(sid)`, that must come back as `X-CSRF-Token` on
  every non-safe method.

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

| What | How |
| --- | --- |
| Email | AES-GCM under `PII_ENCRYPTION_KEY`, plus an HMAC under `DB_HMAC_SECRET` for lookup |
| Last-used IP and location | AES-GCM under `PII_ENCRYPTION_KEY` |
| Per-user data key | random 256-bit key, wrapped by `MASTER_KEY` (envelope encryption) |
| Notes | title and body AES-GCM under the user's key |
| Files | name and content AES-GCM under the user's key, with content bound to the file ID via AAD and streamed to MinIO without buffering |
| Credentials, refresh tokens | an HMAC over every column, checked on read |
| Redis entries | keyed by HMAC of the token, with the value (bound email, user or challenge) AES-GCM under `TOKEN_ENCRYPTION_KEY` |

A failed GCM tag or integrity hash raises `CompromisedException`, which is logged in full
and surfaces to the client as an unremarkable `500`. Nothing enforces that a new column is
included in a row's integrity hash or that AAD matches on both sides. Those are the
invariants to keep in mind when touching the repositories.

## API

All routes live under `/api`. Unauthenticated ones are rate-limited per client IP
(`CF-Connecting-IP` behind the tunnel): 3/min for magic links, 15/min for ceremonies,
30/min for refresh.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/auth/register/verify` | `{email}` -> 204, sends a magic link |
| POST | `/auth/register/start` | `{token}` -> challenge, rpId, email, userHandle, excludeCredentials |
| POST | `/auth/register/finish` | `{clientDataJSON, attestationObject}` -> 201 |
| POST | `/auth/login/start` | returns `{challenge, rpId}` |
| POST | `/auth/login/finish` | `{credentialId, clientDataJSON, authenticatorData, signature}` -> session |
| POST | `/auth/refresh` | rotates the refresh cookie -> session |
| POST | `/auth/logout` | revokes the refresh chain, clears cookies |
| GET | `/auth/credentials` | passkeys with device name (from AAGUID), last use, `currentSession` ¹ |
| DELETE | `/auth/credentials/{id}` | emails the owner ¹ |
| POST | `/auth/credentials/add/start` `/verify` `/finish` | assert with an existing passkey, then register a new one ¹ |
| POST | `/auth/reauth/start` `/finish` | assert with an existing passkey, returns a `{token}` for destructive actions ¹ |
| DELETE | `/auth/account` | `{token}` from reauth. Removes everything and emails the owner ¹ |
| GET/POST | `/notes` | list / create `{title, content}` ¹ |
| PUT/DELETE | `/notes/{id}` | update `{title, content}` / delete ¹ |
| GET/POST | `/files` | list / multipart upload with `size` **before** `file` ¹ ² |
| GET/DELETE | `/files/{id}` | download as attachment / delete ¹ ² |

¹ needs a session and, for non-GET, the CSRF header.  
² needs the `pzk` claim, so only sessions opened with a Privezak-attested passkey.

Errors are JSON
`{message, code?}`. The codes the frontend acts on are `passkey_unknown`, `passkey_cloned`
and `file_limit`.

Limits, in `common/Limits.kt`: 50 MB per file, 100 files and 1000 notes per account, 255
characters of filename, 200 of note title, 2000 of note body.

## Layout

```
src/main/kotlin/rs/moma/janus/kredenac/
├── main.kt                  Netty with the TLS connector
├── Application.kt           plugin order
├── plugins/                 auth provider + `authenticated*` route builders, rate limits,
│                            security headers, DB bootstrap, Koin module, hourly cleanup
├── routes/                  auth, notes, files
├── services/                UserService, NotesService, FilesService, EmailService (Resend)
├── repositories/            Exposed tables as encrypted rows, Redis tokens, MinIO blobs
├── crypto/webauthn/         ceremony verification, CBOR, Android key attestation
├── crypto/authentication/   JWT, refresh tokens, CSRF, magic links
├── crypto/algorithms/       AES-GCM, HMAC, ES256/RS256/EdDSA verification
├── tables/                  schema, created and column-migrated on startup
└── common/                  Env, Owner context, exceptions, limits, client-info parsing
src/main/resources/
├── emails/                  HTML templates, filled with escaped values
└── aaguid-names.json        AAGUID -> authenticator name for the settings page
```

`Owner` is a context parameter: a route body runs inside `context(Owner(userId, privezak))`
and every repository query is scoped by it, so a handler cannot forget to filter by user.

## Configuration

Everything comes from environment variables (`common/Env.kt`). The names are listed in the
[stack README](../README.md#configuration). For convenience, when a variable is not in the
environment the server currently also reads a `.env` file from its working directory, and
the Gradle `test` task loads `../.env` into the test JVM. Both are stopgaps for running from
the IDE, not a configuration mechanism to build on. Secrets handling is moving to a
dedicated tool, and the file-based path will go with it.

## Running and testing

```bash
docker compose -f ../docker-compose.yml -f ../docker-compose.dev.yml up -d postgres redis minio
./gradlew run          # or run MainKt from the IDE
./gradlew test
./gradlew buildFatJar  # what the Dockerfile does
```

Tests run against the real Postgres and Redis, in a separate `kredenac_test` database and
Redis index 15, both cleared before each test. `utils/Authenticator.kt` is a software
authenticator that produces valid attestations and assertions, and `utils/KeyAttestation.kt`
builds Android-style attestation chains against a test root, so the Privezak path is
covered without a device.
