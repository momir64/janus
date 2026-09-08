# Kredenac

[Kredenac](https://kredenac.moma.rs/) is an online cabinet for all your notes and files. You unlock it with a passkey,
and your email is how you get one: a magic link registers your first passkey, or a replacement if you lose it. There are
no passwords anywhere in it.

It is also the other half of [Privezak](../privezak). Any passkey can open the cabinet and keep notes, but the file
drawer only opens for a passkey that Privezak created, and the server can tell: the registration carries an
`android-key` attestation chain up to Google's hardware root, naming Privezak's package and release signing certificate.
Sessions that pass that check get a `pzk` claim in their token, and with it the file storage.

## Screenshot

![Login page](frontend/docs/login.png)

## How it fits together

```
browser --> HTTPS --> Cloudflare --> tunnel --> cloudflared (container)
                                                    |
                                                    |  HTTPS, origin verified against the local CA
                                                    v
                                              backend :8080
                     Ktor, serves the built frontend on kredenac.moma.rs and,
                     while its vault is locked, the unlock page on lokot-kredenac.moma.rs
                                                    |
                        +---------------------------+---------------------------+
                        |                           |                           |
                        v                           v                           v
                    postgres                   redis (TLS)                    minio
                users, credentials,          challenges, magic              encrypted
                refresh tokens,              links, reauth                 file blobs
                notes, file metadata         tokens (short TTLs)
```

Everything runs from one `docker-compose.yml`. The backend image builds the frontend with
Node and the server with Gradle in separate stages and ships a JRE with the fat jar and the
`dist` folder. `cloudflared` is a token-based connector: the ingress rules live in the Cloudflare
dashboard, both hostnames point at `https://backend:8080`, and the connector verifies that origin
against the local CA, which lokot delivers to it as `/run/secrets/ca.crt`.

- [`backend/`](backend): the Ktor server, with WebAuthn, sessions, encryption at rest and
  the API.
- [`frontend/`](frontend): a TypeScript and Vite single-page app, no framework.
- `Dockerfile`: the three-stage build described above.
- `docker-compose.yml`: the production stack. `docker-compose.dev.yml` publishes the data
  services on localhost so the backend can run from the IDE.
- `lokot.toml`: what the vault holds, which service gets which file, and the certificates
  lokot issues. See [lokot](../lokot).
- `.env.lokot`: the vault itself, encrypted, and committed on purpose. Its plaintext header
  only says which passkeys can open it.
- `backend/libs/lokot-0.0.1.jar`: lokot's JVM target. Refresh it with
  `cd ../lokot && ./gradlew jvmJar && cp build/*/libs/lokot-jvm-0.0.1.jar
  ../kredenac/backend/libs/lokot-0.0.1.jar`. The backend build warns when it has fallen behind.

## Encryption at rest

Nothing in Postgres or MinIO is readable on its own. Each user gets a random data key wrapped
by a master key the database never sees, and every note title, note body, filename and file
blob is encrypted under that user's key with AES-GCM. Emails, IP addresses and locations are
encrypted under a separate key, with an HMAC alongside the email so it can still be looked up.
Credential and refresh-token rows carry an integrity hash over all of their columns, and the
short-lived tokens in Redis are stored under HMAC keys with encrypted values.

The two layers buy different things. A copy of the database is useless without the master
key, which only ever exists in the vault and in the backend's memory. The integrity hashes mean
that even someone with write access to the database cannot flip the `privezak` bit on a
credential, rewind a signature counter, or move a passkey to another account without the row
refusing to load. The full table is in [`backend/README.md`](backend/README.md#data-at-rest).

## Secrets and configuration

A `.env` beside a compose file is readable by anything on the box and tends to end up in backups
and shell histories. Here the secrets live encrypted in `.env.lokot`, which opens only with a
passkey, and [lokot](../lokot) does the rest. `lokot unlock` writes each secret as a file into a
directory per service, on tmpfs where the platform has one. Compose mounts only that service's
directory, so a container's mount namespace holds its own secrets and nothing else. `lokot lock`
removes the files again once the containers have started and read them.

The trade-off is worth knowing: every container reads its files at start, so between a lock
and the next unlock nothing can restart. A crash or a reboot means running `lokot unlock`
again, and until then `docker compose` complains about the variables the missing `.env` used
to set.

The generated `.env` beside `docker-compose.yml` holds nothing sensitive: `LOKOT_DIR`, which is
where the directories are, and the handful of values compose has to interpolate before anything
is running.

| Group                                                                                                  | Where it comes from                                                                                       |
|--------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`                                                    | files, read by initdb on the first start and baked into the volume, so changing one later rotates nothing |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`                                                               | files, on every start, since MinIO keeps no root credentials of its own                                   |
| `REDIS_PASSWORD`                                                                                       | a generated `redis.conf`, since Redis has no `_FILE` convention                                           |
| `CLOUDFLARE_TUNNEL_TOKEN`                                                                              | a file, through `TUNNEL_TOKEN_FILE`                                                                       |
| TLS certificates and keys                                                                              | files, issued by lokot from an internal CA whose key it never writes down                                 |
| `POSTGRES_PORT`, `REDIS_PORT`, `MINIO_PORT`, `MINIO_CONSOLE_PORT`, `KTOR_PORT`, `RP_ID`, `LOKOT_RP_ID` | the generated `.env`: the last three are needed before the vault can be opened at all                     |
| `POSTGRES_HOST`, `REDIS_HOST`, `MINIO_HOST`, the TLS paths, `FRONTEND_DIST_PATH`                       | the compose file                                                                                          |
| The backend's own keys, `RP_ORIGIN`, `MINIO_BUCKET`, Resend                                            | the vault, read by the backend itself                                                                     |

The backend opens the vault itself, so it gets no files of its own beyond its certificate and
the CA. While it is locked it serves the unlock page on the `LOKOT_RP_ID` host, answers `503`
under `/api`, and serves the frontend, which shows its ordinary server-down state. A passkey
enrolled for that host opens it in the browser, and only then does the backend build its
database pool, its Redis client and everything else. The [backend README](backend/README.md#starting-up)
has the details.

`RP_ID` is the domain the users' passkeys are scoped to and `RP_ORIGIN` the exact origin the
browser will report, `kredenac.moma.rs` and `https://kredenac.moma.rs` here. `LOKOT_RP_ID` is
the host the unlock page lives on, `lokot-kredenac.moma.rs`, and the relying party id the
unlocking passkeys are enrolled for. It is a separate host so that the passkey that opens the
vault is never one of the passkeys that open a user account.

The keys are all random, 32 bytes each, and lokot generates them. Rotating `MASTER_KEY_BASE64`
or `PII_ENCRYPTION_KEY_BASE64` makes existing rows unreadable, and rotating `DB_HMAC_SECRET`
invalidates every email lookup and integrity hash, so treat them as permanent once there is
data.

## Running it

**First time**, on the machine the stack runs on, with `lokot.toml` in place:

```bash
lokot init --rp lokot-kredenac.moma.rs      # asks for what it cannot make up, generates the rest
lokot add-key --rp lokot-kredenac.moma.rs   # a second passkey, before you rely only on the first
```

`--rp` enrols the passkey under the unlock page's host, which is what a browser checks against
its origin. The CLI can still use it, since it enrolled the key directly. In the Cloudflare
dashboard, route both hostnames to `https://backend:8080` with the origin CA pool set to
`/run/secrets/ca.crt`.

**Whole stack, as deployed:**

```bash
lokot unlock
docker compose up -d --build
# open https://lokot-kredenac.moma.rs and touch the passkey
lokot lock
```

The browser step comes before `lokot lock` because the backend reads the Redis CA out of
`/run/secrets` only once the vault is open. Lock first and it comes up with no way to trust
Redis.

**From another machine**, with the passkey where you are sitting and the checkout on the server:

```bash
lokot unlock user@host:/path/to/kredenac
```

lokot speaks SFTP over `ssh`, reads the vault from there, writes the files and the `.env`
there, and `lokot lock user@host:/path/to/kredenac` cleans up the same way.

**Backend from the IDE**, with the data services in Docker:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis minio
```

then run `rs.moma.janus.kredenac.MainKt` from `backend/`. Outside compose nothing sets the
hosts or the certificate paths, so the run configuration has to: `POSTGRES_HOST`, `REDIS_HOST`
and `MINIO_HOST` are `localhost`, and `BACKEND_TLS_CERT_PATH`, `BACKEND_TLS_KEY_PATH` and
`REDIS_TLS_CA_PATH` point into `$LOKOT_DIR/backend`. The rest comes from the generated `.env`,
which the server also finds one directory up. Both hostnames are matched on the `Host` header
and the passkeys are scoped to them, so for anything involving a real passkey the practical
route is to point the tunnel at your machine, as the [frontend README](frontend/README.md#development)
describes. The backend serves the frontend only when `FRONTEND_DIST_PATH` points at a built
`dist`. For UI work run the Vite dev server instead, which proxies `/api` to the backend.

**Backend tests** need the same Postgres and Redis running, and the files `lokot unlock`
wrote, since they take the database credentials, the Redis password and the CA from there.
With `POSTGRES_HOST` and `REDIS_HOST` set to `localhost` in the environment:

```bash
cd backend && ./gradlew test
```

They create a separate `kredenac_test` database and use Redis index 15, wiping both before
each test.
