# Guest Experience Platform — Backend

One self-hosted Quarkus instance per building. Governed by
`docs/constitution.md` and `docs/backend/specification.md`; binding working
conventions in `backend/CLAUDE.md` (approved 2026-08-19).

## Pinned versions

| What | Pin |
|---|---|
| Java | 21 (LTS) |
| Quarkus platform | `io.quarkus.platform:quarkus-bom:3.33.3.1` (3.33 LTS) |
| `openapi-generator-maven-plugin` | 7.24.0 |
| PostgreSQL | `postgres:17.11` (compose **and** Dev Services — tests run against what ships) |

**OpenAPI version choice (T-03):** the contract is **OpenAPI 3.1**
(`api/openapi.yaml`). Generator 7.24.0 prints an "OpenAPI 3.1 support is
still in beta" warning but generates both the JAX-RS interfaces and the
TypeScript client correctly for this contract (verified: field-add and
operation-remove both propagate; output is byte-identical across runs). No
fallback to 3.0.x was needed.

Note: the constitution's allow-list names `quarkus-junit5`; Quarkus 3.31
renamed that artifact to `quarkus-junit` (the old id is only a relocation
pom). The pom uses the corrected name — same artifact, same intent.

## First run (dev/preview ONLY — no TLS yet)

> **Warning:** this compose unit serves **plain HTTP**. Until
> `device-pairing-and-guest-api` adds HTTPS with the instance CA, it is for
> development/preview only — never for a real building. The local network is
> not trusted (constitution principle 4).

Prerequisites: Docker (with the Compose plugin), Java 21 + the committed
Maven wrapper for building.

```bash
# 1. Build the application (also regenerates contract artifacts)
cd backend/app && ./mvnw verify

# 2. Start the instance — both variables are REQUIRED; compose refuses to
#    interpolate without them, so a missing admin password fails at once.
cd .. \
  && GXP_DB_PASSWORD='choose-a-db-password' \
     GXP_ADMIN_PASSWORD='choose-the-initial-admin-password' \
     docker compose up --build
```

- `GXP_DB_PASSWORD` — PostgreSQL password (app and db service).
- `GXP_ADMIN_PASSWORD` — the admin password, **read on first start only**
  (constitution v1.5.x password lifecycle): when no credential row exists the
  app hashes it (JDK PBKDF2, 600 000 iterations) into PostgreSQL and refuses
  to start if the variable is unset or blank. Once a hash exists the variable
  is **ignored forever** — rotate the password through the API (below), not
  through the environment. The value is never logged.
- Check the box: `curl http://localhost:8080/api/system/ready` → `{"status":"READY"}`;
  `/api/system/health` answers whenever the process serves HTTP;
  `/api/system/info` returns instance metadata read from the database.
- Data lives in the **one named volume** `gxp-pgdata`; `docker compose down`
  keeps it, `docker compose down -v` destroys it.

## Manager auth workflow

The whole manager surface (`/api/manager/**`) is bearer-token secured; the
shared admin password is sent to exactly one endpoint:

```bash
# Log in — the ONLY operation that ever sees the password
curl -s -X POST http://localhost:8080/api/manager/login \
  -H 'Content-Type: application/json' \
  -d '{"password":"…"}'
# → {"token":"…","idleTimeoutSeconds":600,"expiresAt":"…"}

# Use the token (in-memory only on the client)
curl -s http://localhost:8080/api/manager/property \
  -H 'Authorization: Bearer <token>'

# Log out — drops the presented token server-side immediately
curl -s -X POST http://localhost:8080/api/manager/logout \
  -H 'Authorization: Bearer <token>'

# Change the password (≥ 8 characters) — drops EVERY active token,
# including the caller's; log in again with the new password
curl -s -X PUT http://localhost:8080/api/manager/password \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"currentPassword":"…","newPassword":"…"}'
```

- **Sliding idle expiry:** tokens idle out after 10 minutes; every
  authenticated request slides the window, and every authenticated response
  carries the refreshed expiry in the **`GXP-Token-Expires`** header
  (ISO-8601 instant) so a client can warn before idle-out.
- **Lockout:** 5 consecutive failed logins lock the endpoint for 30 s
  (`429` + `Retry-After`). Wrong-password answers are deliberately generic.
- The token store is in-memory: **a restart logs everyone out** (deliberate).

## Assets

Uploads are raw binary bodies (`POST /api/manager/assets?kind=LOGO|ARTWORK`,
no multipart) and are strictly checked server-side:

- **Allow-list `image/png` + `image/jpeg` only**, verified by decoding the
  content — a mismatch between declared type and actual bytes is a `400`.
  SVG is rejected on purpose (stored-XSS vector when served); WebP has no
  JDK decoder and cannot be content-verified.
- **10 MiB cap** (app-enforced → `413`; the HTTP layer's limit is set to
  12M so the app's error answers, not the transport's).
- Anything over **2560 px** longest edge is downscaled server-side and
  re-encoded; the stored sha-256/size/dimensions describe the stored bytes.
- Delivery uses the sha-256 as a **strong `ETag`** (`304` on
  `If-None-Match`); metadata lists never read the payload column.
- Deleting an asset still referenced by the property or a room is a `409`
  naming the referrer.

No manual database setup, ever: Flyway migrations under
`app/src/main/resources/db/migration/` run at startup.

## Development

```bash
cd backend/app
./mvnw quarkus:dev   # Dev Services starts postgres:17.11 automatically (Docker required)
./mvnw verify        # build + tests (@QuarkusTest against Dev Services)
```

Dev/test deliberately configure **no datasource URL** — Quarkus Dev Services
provisions PostgreSQL. Production reads `GXP_DB_JDBC_URL` /
`GXP_DB_USERNAME` / `GXP_DB_PASSWORD` from the environment (supplied by
`compose.yaml`).

## Changing the API contract

`api/openapi.yaml` is the source of truth. The workflow (REQ-209):

```
edit api/openapi.yaml
        │
   ./mvnw verify  ──► generate-jaxrs ──► SystemApi.java changes ──► compile fails
        │                                                            until Resource/Control follow
        ├────────────► generate-ts-client ──► packages/api-client/ diff
        │
implement/adjust Resource + Control (+ Mapper, migration if needed)
        │
one commit: api/openapi.yaml + backend code + packages/api-client/
```

- Generated Java lives under `target/generated-sources/` and is **never
  committed** — the backend cannot compile against a stale interface, so
  contract drift is a compile failure.
- `packages/api-client/` is generated TypeScript, **committed and never
  hand-edited** — a diff there means the contract changed. Generation is
  deterministic: an unchanged contract produces no diff.

## Backup & restore

The entire instance state is the PostgreSQL database in the `gxp-pgdata`
volume — **backup is one `pg_dump`**:

```bash
# Backup
docker compose exec db pg_dump -U gxp -d gxp --format=custom > gxp-backup.dump

# Restore (into a fresh, running db service)
docker compose exec -T db pg_restore -U gxp -d gxp --clean --if-exists < gxp-backup.dump
```

Binary assets (logo, artwork — later features) live in the database as
`BYTEA` precisely so this one dump stays the whole backup.
