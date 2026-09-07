# Kredenac

[Kredenac](https://kredenac.moma.rs/) is an online cabinet for all your notes and files. You unlock it with a passkey, and your email is how you get one: a magic link registers your first passkey, or a replacement if you lose it. There are no passwords anywhere in it.

It is also the other half of [Privezak](../privezak). Any passkey can open the cabinet and keep notes, but the file drawer only opens for a passkey that Privezak created, and the server can tell: the registration carries an `android-key` attestation chain up to Google's hardware root, naming Privezak's package and release signing certificate. Sessions that pass that check gets a `pzk` claim in their token, gving them file storage access.

## Screenshot

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
dashboard, and the origin is `https://backend:8080` verified against the local CA mounted at
`/certs/ca.crt`.

- [`backend/`](backend): the Ktor server, with WebAuthn, sessions, encryption at rest and
  the API.
- [`frontend/`](frontend): a TypeScript + Vite single-page app, no framework.
- `Dockerfile`: the three-stage build described above.
- `docker-compose.yml`: the production stack. `docker-compose.dev.yml` publishes the data
  services on localhost so the backend can run from the IDE.
- `generate-certs.ps1` / `generate-certs.sh`: the local CA and the leaf certificates.

## Configuration

The stack is configured entirely through environment variables. Compose reads them from a
`.env` file next to `docker-compose.yml`, and the backend reads the same names from its
environment. These are the variables in use:

| Group | Variables |
| --- | --- |
| Server | `KTOR_PORT`, `RP_ID`, `RP_ORIGIN`, `BACKEND_TLS_KEYSTORE_PATH`, `BACKEND_TLS_KEYSTORE_PASSWORD` |
| Postgres | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_HOST`, `POSTGRES_PORT` |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_TLS_TRUSTSTORE_PATH`, `REDIS_TLS_TRUSTSTORE_PASSWORD` |
| MinIO | `MINIO_HOST`, `MINIO_PORT`, `MINIO_CONSOLE_PORT`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_BUCKET` |
| Keys | `MASTER_KEY_BASE64`, `PII_ENCRYPTION_KEY_BASE64`, `TOKEN_ENCRYPTION_KEY_BASE64`, `JWT_SECRET`, `CSRF_SECRET`, `DB_HMAC_SECRET` |
| Email | `RESEND_API_KEY`, `RESEND_FROM_EMAIL` |
| Tunnel | `CLOUDFLARE_TUNNEL_TOKEN` |

`RP_ID` is the domain passkeys are scoped to and `RP_ORIGIN` the exact origin the browser
will report, e.g. `kredenac.moma.rs` and `https://kredenac.moma.rs`. Compose overrides the
`*_HOST` values and the certificate paths for the containers, so `.env` can hold the
values for running the backend on the host.

The keys are all random: 32 bytes, base64-encoded for the `*_BASE64` ones. Rotating
`MASTER_KEY_BASE64` or `PII_ENCRYPTION_KEY_BASE64` makes existing rows unreadable, and
rotating `DB_HMAC_SECRET` invalidates every email lookup and integrity hash, so treat them as
permanent once there is data. The `.env` file is git-ignored. A proper secrets workflow is
being built as a separate tool ([`../lokot`](../lokot)) and will replace the file when it
lands.

## Running it

**Whole stack, as deployed:**

```bash
docker compose up -d --build
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
