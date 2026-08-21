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

## First run (production-shaped: HTTPS only)

Since `device-pairing-and-guest-api` the compose unit is **production-
shaped**: `%prod` serves **HTTPS only** — no plain listener, no redirect
(a redirect would still invite the first password over HTTP; decided
2026-08-19). The instance generates its **own CA** and a CA-signed server
certificate on first start (constitution: HTTPS on the untrusted LAN with an
instance-generated CA distributed to kiosks at pairing).

Prerequisites: Docker (with the Compose plugin), Java 21 + the committed
Maven wrapper for building.

```bash
# 1. Build the application (also regenerates contract artifacts)
cd backend/app && ./mvnw verify

# 2. Start the instance — all three variables are REQUIRED; compose refuses
#    to interpolate without them, so a missing value fails at once.
cd .. \
  && GXP_DB_PASSWORD='choose-a-db-password' \
     GXP_ADMIN_PASSWORD='choose-the-initial-admin-password' \
     GXP_TLS_HOST='192.168.1.10' \
     docker compose up --build
```

- `GXP_DB_PASSWORD` — PostgreSQL password (app and db service).
- `GXP_ADMIN_PASSWORD` — the admin password, **read on first start only**
  (constitution v1.5.x password lifecycle): when no credential row exists the
  app hashes it (JDK PBKDF2, 600 000 iterations) into PostgreSQL and refuses
  to start if the variable is unset or blank. Once a hash exists the variable
  is **ignored forever** — rotate the password through the API (below), not
  through the environment. The value is never logged.
- `GXP_TLS_HOST` — the **one stable host name or IP the kiosks use** to reach
  this instance; the server certificate is issued for exactly it (SAN).
  Required in `%prod` — the boot refuses without it (same posture as the
  admin password).
- Check the box: `curl -k https://<GXP_TLS_HOST>/api/system/ready` →
  `{"status":"READY"}` (`-k` only until the CA is installed, see the pairing
  workflow); `/api/system/health` answers whenever the process serves HTTPS.
  `/api/system/info` requires an admin or device token (no anonymous version
  fingerprinting).
- Data lives in **two named volumes**: `gxp-pgdata` (database) and `gxp-tls`
  (TLS material); `docker compose down` keeps them, `down -v` destroys both.

### TLS lifecycle

On first start the container entrypoint generates, with `keytool` (already
in the JVM image — no extra dependency):

| What | Parameters |
|---|---|
| Instance CA | EC P-256, self-signed, `CA:true`, valid **20 years** |
| Server certificate | EC P-256, signed by the CA, SAN = `GXP_TLS_HOST`, valid **10 years** |

Both live on the `gxp-tls` volume (PKCS12 keystores + a generated
passphrase file, mode 0600); restarts reuse them. **Changing `GXP_TLS_HOST`**
re-issues the **server certificate only**, under the unchanged CA — paired
kiosks keep trusting without any re-install or re-pairing. Private keys
never leave the volume and never appear in a log. Rotation before expiry is
a documented manual path: remove the `gxp-tls` volume, restart (new CA),
re-install the CA on every kiosk at the next visit.

## Pairing a kiosk

Pairing is manager-attended and needs no typing on the kiosk:

1. **Install the instance CA on the kiosk (a human provisioning step).**
   Fetch it from `GET /api/pairing/ca` (PEM, public material, also over
   not-yet-trusted HTTPS — accept-once or out-of-band) and install it in the
   device's OS/browser trust store. A web page cannot do this; a person does
   it once per device.
2. **The kiosk requests a pairing code:** `POST /api/pairing/requests`
   (unauthenticated, rate-limited 5/min) → a short **display code** the kiosk
   shows, a **poll secret** it keeps (never displayed — the code alone can
   never yield a token, the screen stands in a guest-accessible room), and a
   10-minute expiry.
3. **The manager confirms the code to a room:**
   `POST /api/manager/pairings/{code}/confirm` with
   `{"roomId": …, "deviceName": "…"}` (name optional — defaults to
   `Kiosk <room code>`).
4. **The kiosk polls** `GET /api/pairing/requests/{code}` with the
   `GXP-Pairing-Secret` header: `202` while pending, then **`200` with the
   device token exactly once** (the request is atomically claimed; any later
   poll — like any unknown/expired/wrong-secret request — is the same `404`).

The device token is long-lived, stored **hashed** server-side, does not
idle out, and is the device's identity: the kiosk's room is derived from it,
never from any request parameter.

### Device administration & revocation

```bash
curl -s https://<host>/api/manager/devices -H 'Authorization: Bearer <token>'
curl -s -X PUT https://<host>/api/manager/devices/<id> \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"name":"Flur-Tablet"}'
curl -s -X POST https://<host>/api/manager/devices/<id>/revoke \
  -H 'Authorization: Bearer <token>'
```

Revocation is **immediate**: the token is dead on the device's very next
request (verification reads the row every time — no grace window, no cache).
Deleting a room with active devices is a `409` naming them; revoke or
re-pair first. No device operation ever returns token material.

## Manager auth workflow

The whole manager surface (`/api/manager/**`) is bearer-token secured; the
shared admin password is sent to exactly one endpoint:

```bash
# Log in — the ONLY operation that ever sees the password
curl -s -X POST https://<host>/api/manager/login \
  -H 'Content-Type: application/json' \
  -d '{"password":"…"}'
# → {"token":"…","idleTimeoutSeconds":600,"expiresAt":"…"}

# Use the token (in-memory only on the client)
curl -s https://<host>/api/manager/property \
  -H 'Authorization: Bearer <token>'

# Log out — drops the presented token server-side immediately
curl -s -X POST https://<host>/api/manager/logout \
  -H 'Authorization: Bearer <token>'

# Change the password (≥ 8 characters) — drops EVERY active token,
# including the caller's; log in again with the new password
curl -s -X PUT https://<host>/api/manager/password \
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

## Serving the guest app (production)

The Angular kiosk app ships **inside the backend jar** (guest-frontend-
integration D7, decided 2026-08-20): the `bundle-guest-app` Maven profile —
auto-activated by the existence of `frontend/dist/frontend/browser/index.html`
— copies the **prebuilt** production bundle into `META-INF/resources`, so
kiosks load the app at `/` on the same HTTPS origin as `/api`. The
shippable-artifact workflow is therefore:

```bash
cd frontend && npx ng build          # 1. Angular production build
cd ../backend/app && ./mvnw clean package   # 2. picks the bundle up automatically
cd .. && docker compose up --build   # 3. unchanged image/compose
```

- **`clean` matters** when toggling the bundle: `process-resources` copies
  into `target/classes`, and a previously copied app survives an unclean
  rebuild.
- Without a frontend build the profile stays inactive: backend-only
  `./mvnw test`/`package` remain Node-free and green and simply serve no app.
- A guest deep link (e.g. a reload on `/einstellungen`) is served
  `index.html` by `de.gxp.web.SpaRoutingHandler` — which **never** rewrites
  `/api/**`, `/q/**` or the reserved future manager path `/manage/**`, and is
  inert when no bundle is packaged.
- Static-resource cache headers are disabled
  (`quarkus.http.static-resources.caching-enabled=false`): the default
  `immutable, max-age=86400` would pin a stale `index.html` for a day after
  an update; on a LAN appliance re-downloading ~100 KB on a (rare) kiosk
  reload is the better trade.

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

### The dev seed (`%dev` only)

A fresh `quarkus:dev` database is seeded by `de.gxp.devseed.DevDataSeeder`
(guest-frontend-integration REQ-503) with the example-property content, an
active stay on room `1` and one paired device whose bearer token is the
**public, dev-only** literal:

```
gxp-dev-device-token
```

Its safety derives from its **hash never existing in a production database**,
never from secrecy (REQ-504): the seeder is `@IfBuildProfile("dev")` — the
**bean** is removed from the container at build time, so no seeding code can
run in `%prod` or `%test` (verified at review: a prod build generates no
`DevDataSeeder_Bean`/observer class, while every other bean has one). The
compiled class itself — and with it this literal and the seed SQL — still
ships inside the jar and is simply never instantiated; a grep over the
image's `META-INF/resources` therefore proves nothing about it, which is why
the runtime assertions below are the actual proof. The compose smoke asserts
the inverse:
a fresh `%prod` instance has zero devices and answers this token with `401`.
The Angular dev server picks the same literal up from
`frontend/src/environments/environment.development.ts` — `ng serve` +
`quarkus:dev` render a paired, occupied kiosk with zero manual pairing.
Re-seeding is idempotent (keyed on the dev device row), so nothing you edit
via the manager API is ever overwritten by a restart.

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

Instance state is the PostgreSQL database in the `gxp-pgdata` volume —
**backup is one `pg_dump`** — **plus the `gxp-tls` volume** (CA + server
key material):

```bash
# Backup
docker compose exec db pg_dump -U gxp -d gxp --format=custom > gxp-backup.dump
docker run --rm -v gxp_gxp-tls:/tls -v "$PWD":/backup alpine tar czf /backup/gxp-tls.tgz -C /tls .

# Restore (into a fresh, running db service)
docker compose exec -T db pg_restore -U gxp -d gxp --clean --if-exists < gxp-backup.dump
docker run --rm -v gxp_gxp-tls:/tls -v "$PWD":/backup alpine tar xzf /backup/gxp-tls.tgz -C /tls
```

Binary assets (logo, artwork) live in the database as `BYTEA` precisely so
the dump stays the data backup. Losing the TLS volume is survivable but
expensive, stated honestly: the next start generates a **new CA** and a
person must **re-install it in every kiosk's trust store** (device tokens
live in PostgreSQL, so no re-pairing is needed).
