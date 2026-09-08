# Kredenac

[Kredenac](https://kredenac.moma.rs/) is an online cabinet for all your notes and files. You unlock it with a passkey, and your email is how you get one: a magic link registers your first passkey, or a replacement if you lose it. There are no passwords anywhere in it.

It is also the other half of [Privezak](../privezak). Any passkey can open the cabinet and keep notes, but the file drawer only opens for a passkey that Privezak created, and the server can tell: the registration carries an `android-key` attestation chain up to Google's hardware root, naming Privezak's package and release signing certificate. Sessions that pass that check gets a `pzk` claim in their token, gving them file storage access.

## Frontend screenshot

![Home page](frontend/docs/login.png)

## How it fits together

```
browser--> HTTPS--> Cloudflare--> tunnel--> cloudflared (container)
                                                |
                                                |  HTTPS, origin verified against the local CA
                                                v
                                            backend :8080
                              Ktor, also serves the built frontend
                                                |
                        +-----------------------+-----------------------+
                        |                       |                       |
                        v                       v                       v
                    postgres               redis (TLS)                minio
                users, credentials,      challenges, magic           encrypted
                refresh tokens,          links, reauth              file blobs
                notes, file metadata     tokens (short TTLs)
```

Everything runs from one `docker-compose.yml`. The backend image builds the frontend with
Node and the server with Gradle in separate stages and ships a JRE with the fat jar and the
`dist` folder. `cloudflared` is a token-based connector: ingress rules live in the Cloudflare
dashboard, and the origin is `https://backend:8080` verified against the local CA, which lokot
delivers to the connector as `/run/secrets/ca.crt`.

- [`backend/`](backend): the Ktor server, with WebAuthn, sessions, encryption at rest and
  the API.
- [`frontend/`](frontend): a TypeScript + Vite single-page app, no framework.
- `Dockerfile`: the three-stage build described above.
- `docker-compose.yml`: the production stack. `docker-compose.dev.yml` publishes the data
  services on localhost so the backend can run from the IDE.
- `lokot.toml`: what the vault holds, which service gets which file, and the certificates
  lokot issues. See [lokot](../lokot).
- `backend/libs/lokot-0.0.1.jar`: lokot's JVM target. Refresh it with
  `cd ../lokot && ./gradlew jvmJar && cp build/*/libs/lokot-jvm-0.0.1.jar
  ../kredenac/backend/libs/lokot-0.0.1.jar`; the build warns when it has fallen behind.

## Encryption at rest

Nothing in Postgres or MinIO is readable on its own. Each user gets a random data key wrapped 
by a master key the database never sees, and every note title, note body, filename and file 
blob is encrypted under that user's key with AES-GCM. Emails, IP addresses and locations are 
encrypted under a separate key, with an HMAC alongside the email so it can still be looked up. 
Credential and refresh-token rows carry an integrity hash over all of their columns, and the 
short-lived tokens in Redis are stored under HMAC keys with encrypted values. The full table 
is in [`backend/README.md`](backend/README.md#data-at-rest).

## Configuration

Secrets do not live in a file that anything reads by accident. [lokot](../lokot) keeps them in an
encrypted vault that opens with a passkey, and `lokot unlock` writes each one as a file into a
directory per service, on tmpfs where the platform has one. Compose mounts only that service's
directory, so a container's mount namespace holds its own secrets and nothing else. `lokot lock`
removes them again once the containers have started and read them.

The generated `.env` beside `docker-compose.yml` therefore holds nothing sensitive: `LOKOT_DIR`,
which is where the directories are, and the four container ports compose has to interpolate before
anything is running.

| Group | Where it comes from |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | files, read once by initdb and then baked into the volume, so changing one rotates nothing |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | files, on every start, since MinIO keeps no root credentials of its own |
| `REDIS_PASSWORD` | a generated `redis.conf`, since Redis has no `_FILE` convention |
| `CLOUDFLARE_TUNNEL_TOKEN` | a file, through `TUNNEL_TOKEN_FILE` |
| TLS certificates and keys | files, issued by lokot from an internal CA whose key it never writes down |
| `POSTGRES_PORT`, `REDIS_PORT`, `MINIO_PORT`, `MINIO_CONSOLE_PORT`, `KTOR_PORT`, `RP_ID` | the generated `.env`: the last two are needed before the vault can be opened at all |
| `POSTGRES_HOST`, `REDIS_HOST`, `MINIO_HOST`, `FRONTEND_DIST_PATH` | the compose file |
| The backend's own keys, `RP_ORIGIN`, `MINIO_BUCKET`, Resend | the vault, read by the backend itself |

The backend opens the vault itself. While it is locked, it serves the unlock page at `/lokot`,
answers `503` for the API and serves the frontend, which shows its ordinary server-down state. A
passkey enrolled for this origin opens it in the browser, and only then does the backend build its
database pool, its Redis client and everything else.

`RP_ID` is the domain passkeys are scoped to and `RP_ORIGIN` the exact origin the browser will
report, e.g. `kredenac.moma.rs` and `https://kredenac.moma.rs`.

The keys are all random: 32 bytes, base64-encoded for the `*_BASE64` ones, and lokot generates them.
Rotating `MASTER_KEY_BASE64` or `PII_ENCRYPTION_KEY_BASE64` makes existing rows unreadable, and
rotating `DB_HMAC_SECRET` invalidates every email lookup and integrity hash, so treat them as
permanent once there is data.

## Running it

**Whole stack, as deployed:**

```bash
lokot unlock
docker compose up -d --build
lokot lock
```

**From another machine**, with the vault and the passkey on the machine you are sitting at:

```bash
lokot unlock user@host:/path/to/kredenac
```

**Backend from the IDE**, with the data services in Docker:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis minio
```

then run `rs.moma.janus.kredenac.MainKt` from `backend/`. The backend serves the frontend
only when `FRONTEND_DIST_PATH` points at a built `dist`. For UI work run the Vite dev server
instead, which proxies `/api` to the backend, as described in
[`frontend/README.md`](frontend/README.md).

**Backend tests** need the same Postgres and Redis running. They create a separate
`kredenac_test` database and use Redis index 15, wiping both before each test:

```bash
cd backend && ./gradlew test
```
