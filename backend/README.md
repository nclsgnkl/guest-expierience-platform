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

# 2. The deployment's `.env` — REQUIRED, next to compose.yaml (it is
#    .gitignored). GXP_DB_PASSWORD and GXP_TLS_HOST are read on every start;
#    the host-side updater reads the unit as root under systemd with NO
#    other environment, so a variable passed on the command line instead of
#    in `.env` makes every updater run fail (`install.sh` refuses such a
#    box). GXP_ADMIN_PASSWORD is needed for the FIRST boot only
#    (GXP_ADMIN_USERNAME is optional — default "admin").
cd .. && (umask 077 && cat > .env <<'EOF'
GXP_DB_PASSWORD=choose-a-db-password
GXP_TLS_HOST=192.168.1.10
GXP_ADMIN_PASSWORD=choose-the-initial-admin-password
EOF
)

# 3. FIRST start
docker compose up --build

# 4. After the first start: remove the GXP_ADMIN_PASSWORD line from .env.
#    Both services carry `restart: unless-stopped`, so after a reboot or a
#    power cut the unit comes back on its own; `docker compose up -d` is the
#    manual start.
sed -i '/^GXP_ADMIN_PASSWORD=/d' .env && docker compose up -d
```

**After the first start, remove `GXP_ADMIN_PASSWORD` from `.env`.** It is read
once and ignored forever afterwards, but as long as it stays in the
environment it is readable by anyone who can run `docker inspect` or read
`/proc/<pid>/environ` — which needs Docker access, not root. Leaving it out
removes that exposure without changing anything the running instance does; the
password lives on only as a PBKDF2 hash in PostgreSQL. Compose therefore
treats this variable (and `GXP_ADMIN_USERNAME`) as optional (`:-`), while the
other two keep their `:?` guard. A **fresh** instance started without it still
refuses to boot — `FirstUserSeeder` enforces that itself, and its message
names the variable and the ≥ 8 rule.

- `GXP_DB_PASSWORD` — PostgreSQL password (app and db service).
- `GXP_ADMIN_USERNAME` — the **username of the first manager account**,
  optional, default `admin`; `^[a-z0-9][a-z0-9._-]{0,63}$`. Read on first
  start only, together with the password below.
- `GXP_ADMIN_PASSWORD` — the first account's password, **read on first start
  only**, and the one variable compose does not require
  (constitution v1.5.x password lifecycle): when no manager account exists
  the app creates one from the two variables, hashing the password (JDK
  PBKDF2, 600 000 iterations) into PostgreSQL, and refuses to start if the
  password is unset, blank, or shorter than 8 characters (or the username
  invalid). Once any account exists both variables are **ignored forever** —
  rotate passwords and add accounts through the API (below), not through the
  environment. The values are never logged.
- `GXP_TRUSTED_PROXY_ENABLED` / `GXP_TRUSTED_PROXY` — **off by default; leave
  them off on a LAN-only box.** See "Trusted proxy" below.
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
   (unauthenticated, **not** rate-limited — owner decision 2026-08-24) → a
   short **display code** the kiosk shows, a **poll secret** it keeps (never
   displayed — the code alone can never yield a token, the screen stands in a
   guest-accessible room), and a 10-minute expiry. Live requests are bounded
   by that expiry and the opportunistic cleanup on each create, not by a
   budget; guessing a code is infeasible on entropy and TTL alone (32⁸ ≈
   1.1 × 10¹² combinations, valid 10 minutes).
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

The whole manager surface (`/api/manager/**`) is bearer-token secured. Since
`auth-hardening` there are **named manager accounts** (`manager_user`, every
one an admin), and the password of an account is sent to exactly one
endpoint:

```bash
# Log in — the ONLY operation that ever sees the password
curl -s -X POST https://<host>/api/manager/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"…"}'
# → {"token":"…","username":"admin","idleTimeoutSeconds":600,"expiresAt":"…"}

# Use the token (in-memory only on the client)
curl -s https://<host>/api/manager/property \
  -H 'Authorization: Bearer <token>'

# Log out — drops the presented token server-side immediately
curl -s -X POST https://<host>/api/manager/logout \
  -H 'Authorization: Bearer <token>'

# Change your OWN password (≥ 8 characters) — drops every token of YOUR
# account, including the caller's; log in again with the new password
curl -s -X PUT https://<host>/api/manager/password \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{"currentPassword":"…","newPassword":"…"}'
```

- **Sliding idle expiry:** tokens idle out after 10 minutes; every
  authenticated request slides the window, and every authenticated response
  carries the refreshed expiry in the **`GXP-Token-Expires`** header
  (ISO-8601 instant) so a client can warn before idle-out.
- **Per-address login throttle (auth-hardening, reopening the 2026-08-24
  decision):** login and the own-password change are throttled **per
  attributed client address, never globally and never per account**:
  5 failures within 15 minutes block that address for 60 s, doubling on
  every further exhaustion up to 900 s, answered `429 too-many-attempts` +
  `Retry-After` *before* any PBKDF2 work or database read; a success clears
  the address. Another address — and the manager's correct password from
  it — is never affected, which is exactly what the two withdrawn global
  mechanisms got wrong. Wrong-username, deactivated-account and wrong-
  password answers are one generic `401 invalid-credentials` after the same
  PBKDF2 work (no wording or timing oracle). On the LAN the attributed
  address is the socket peer; see "Trusted proxy" for the remote case.
- **Upgrading from an instance before V5** (single shared admin password):
  the Flyway migration `V5` carries the existing credential over as the
  active user **`admin`** with its hash unchanged — log in as `admin` with
  the same password; `GXP_ADMIN_USERNAME`/`GXP_ADMIN_PASSWORD` are ignored
  because an account exists.
- The token store is in-memory: **a restart logs everyone out** (deliberate).

### User management (admin, `/api/manager/users`)

```bash
curl -s https://<host>/api/manager/users -H 'Authorization: Bearer <token>'
curl -s -X POST https://<host>/api/manager/users -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"username":"reception","password":"…"}'   # 201
curl -s -X POST https://<host>/api/manager/users/<id>/deactivate -H 'Authorization: Bearer <token>'  # 204
curl -s -X POST https://<host>/api/manager/users/<id>/reactivate -H 'Authorization: Bearer <token>'  # 204
curl -s -X PUT  https://<host>/api/manager/users/<id>/password -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"newPassword":"…"}'                       # 204
curl -s -X DELETE https://<host>/api/manager/users/<id>/second-factor -H 'Authorization: Bearer <token>'  # 204
```

Deactivating an account, resetting its password or resetting its second
factor drops **that account's** tokens on the next request; you cannot
deactivate, password-reset or factor-reset **yourself** (`409`) — another
admin does it, so the last working account can never lock itself out. A
locked-out user recovers through another admin, never through the
environment variables.

### Second factor (TOTP + recovery codes) — required through the proxy only

Every account may enrol an RFC 6238 authenticator (SHA-1, 6 digits, 30 s —
what every authenticator app defaults to). The factor is **asked for only on
requests that arrive through the trusted proxy** (remote operations); on the
LAN the login is username + password and the code fields are ignored.

```bash
# 1. Enrol — returns the secret and an otpauth:// URI to scan; state "pending"
curl -s -X POST https://<host>/api/manager/users/me/second-factor \
  -H 'Authorization: Bearer <token>'
# 2. Confirm with the first code from the app — returns the TEN recovery codes,
#    shown exactly once; store them offline
curl -s -X POST https://<host>/api/manager/users/me/second-factor/confirm \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{"code":"123456"}'
# Status: none | pending | confirmed, and how many recovery codes remain
curl -s https://<host>/api/manager/users/me/second-factor -H 'Authorization: Bearer <token>'
# Regenerate recovery codes (invalidates all previous ones) / disable the factor —
# both confirm the password
curl -s -X POST https://<host>/api/manager/users/me/recovery-codes \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{"password":"…"}'
curl -s -X DELETE https://<host>/api/manager/users/me/second-factor \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{"password":"…"}'

# Remote login (via the proxy): password + totpCode, or password + recoveryCode
curl -s -X POST https://<host>/api/manager/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"…","totpCode":"123456"}'
curl -s -X POST https://<host>/api/manager/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"…","recoveryCode":"ab12cd34ef"}'
```

- A via-proxy login **without** a code is answered `401
  second-factor-required` before the password is even looked at and does not
  count against the throttle (the UI round-trip must not eat the budget).
  With a code, password and factor are verified together and every miss is
  the one generic `invalid-credentials`; an account **without a confirmed
  factor cannot log in remotely at all** (same generic answer).
- Each TOTP code is accepted **once** (±1 step tolerance, last accepted step
  stored per user); each recovery code is spent on use. Two simultaneous
  logins with the same code yield one session.
- **Lost authenticator:** use a recovery code, then regenerate/disable and
  re-enrol — or, with none left, have another admin reset your factor
  (`DELETE /api/manager/users/<id>/second-factor`) and enrol again on the
  LAN.
- **The box's clock must be right.** TOTP is a function of time, and a
  Raspberry Pi has **no battery-backed RTC**: remote login requires the box's
  clock to be **NTP-synchronised**. A clock that regressed after a reboot
  refuses every code (fail-closed; LAN logins are unaffected) until it
  catches up.

### Trusted proxy (`GXP_TRUSTED_PROXY_ENABLED`, `GXP_TRUSTED_PROXY`)

Both default to **off / loopback (`127.0.0.1/32`) — leave them there on a
LAN-only box.** With the switch off every `Forwarded` / `X-Forwarded-*` header
is ignored and the socket peer is the attributed address for the throttle and
the audit trail. The switch exists for the remote-operations relay
(`docs/backend/remote-operations.md`): set `GXP_TRUSTED_PROXY_ENABLED=true`
and `GXP_TRUSTED_PROXY` to the relay's **exact** address (`/32`) and the box
then (1) attributes a request to the `for=` address of the standard
`Forwarded` header **only** when it arrives from that peer, and (2) treats
such requests as remote — the second factor becomes mandatory for them.

- **Relay obligation:** the relay must **replace** the `Forwarded` header with
  a single `for=` element (never append — Quarkus honours the first element)
  and must not terminate TLS for the box (see the remote-operations spec).
- **Never put a LAN range in `GXP_TRUSTED_PROXY`.** Any peer in that range
  could then choose its own throttle bucket — and anyone else's — by sending
  a `Forwarded` header, turning the login throttle into a weapon on that
  range; and every such peer would be treated as remote.
- Only the standard `Forwarded` header is honoured (`X-Forwarded-For` and the
  PROXY protocol stay off); the details and the reason for each pin are in
  `application.properties`.

### Audit trail (admin, read-only)

Every manager mutation and every verified login outcome (success, failure)
leaves an append-only row — the `429` and the code-less via-proxy
`second-factor-required` answers are not recorded: when, actor
(null for a failed login), attributed address, via-proxy flag, action,
entity, entity id and field name — **never a value**, by schema. A failed
login records the account it matched (or nothing), never the submitted text.

```bash
# newest first; page ≥ 0, size 1..200, default 50
curl -s 'https://<host>/api/manager/audit?page=0&size=50' -H 'Authorization: Bearer <token>'
# → {"entries":[{"id":…,"occurredAt":"…","actor":"admin","address":"127.0.0.1","viaProxy":false,
#                "action":"update","entity":"property","entityId":"1","field":"name"}, …],
#    "page":0,"size":50,"total":…}
```

There is no update or delete endpoint. **Retention is 365 days**: the
scheduled job `AuditScheduler` (daily at 02:00, alongside the pairing-request
sweep and the token purge) deletes older rows. A `429` is never recorded —
the throttled path stays free of database work.

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

## Updates (`update-channel`)

One property, one box, no central patching — so the box updates **itself**,
outbound only, from a signed manifest. Two halves (constitution v1.8.0;
`docs/features/update-channel/`):

- **The app** (`de.gxp.update`) polls `GET <base-url>/manifest/<channel>.json`
  and `.json.sig` hourly (minute 17 + up to 10 min jitter), verifies the
  Ed25519 signature against the **trust set compiled into the image**
  (`src/main/resources/update/signing-public-keys.pem` — two keys, see
  "Keys"), records an available version, and — if automatic updates are on —
  writes a request file into the `gxp-updates` volume for the night's apply
  window. It never touches Docker.
- **The updater** (`backend/updater/`) is a root bash script on a systemd
  timer (every 5 min). It verifies the same signature **again** with OpenSSL
  (it is the trust root — the app's file is hostile input), dumps the
  database, pulls the image **by registry digest**, swaps it with exactly one
  restart, checks health, and rolls back on failure.

Nothing ever connects to the box; the request carries no identity
(`User-Agent: gxp-update/1`, nothing else), `https://` only, no new
dependency on either side (JDK `Signature`/`HttpClient`; host `docker`,
`openssl`, `systemd`).

### Environment (compose `.env`)

| Variable | Meaning | Default |
|---|---|---|
| `GXP_UPDATE_BASE_URL` | Manifest server, `https://…`; **unset = channel off** (status reports `not-configured`) | unset |
| `GXP_UPDATE_CHANNEL` | Manifest name `^[a-z0-9-]{1,32}$` | `stable` |
| `GXP_IMAGE` | The running app image. **Written by the updater** — do not hand-edit while the timer is enabled | `gxp-backend:1.0.0-SNAPSHOT` |

### Keys and signing a release (`tools/update/`)

Every box trusts a **set of two keys** (architect review A-1, 2026-08-26):
**key 1 (active)** signs releases; **key 2 (dormant)** exists only so that
losing or leaking key 1 is survivable without visiting every building. A
signature by *any* key of the set is accepted by both verifiers. The private
halves live on the release signer's machine, never in a repository (D17):

```bash
tools/update/gen-signing-key.sh          # → $HOME/.gxp/keys/signing-{private,public}-key.pem   (key 1)
tools/update/gen-signing-key.sh ~/.gxp/keys 2   # → …/signing-{private,public}-key-2.pem          (key 2)
cat ~/.gxp/keys/signing-public-key.pem ~/.gxp/keys/signing-public-key-2.pem \
    > backend/app/src/main/resources/update/signing-public-keys.pem   # the ONLY copy in the repo: public halves, key 1 first
```

Shipped set (SHA-256 of the DER public keys — `install.sh` prints them):

| | Fingerprint | Role |
|---|---|---|
| key 1 | `256caa773eb8ce34e86e8056b389c6e801ea250248da564a8a80abfdb27f31ff` | active — signs every release |
| key 2 | `09009e53be5fb4807c890e46c91d070b966dc57957a3372f9d78fa36bd79853b` | dormant — never used until a rotation |

The build refuses a resource with fewer than two distinct keys, or one that
is a test key (`SigningKeyResourceTest`); `install.sh` refuses a single-key
resource too.

**Rotation** happens through the channel itself: a release whose manifest
carries a `trustedKeys` line (`sign-manifest.sh --trusted-keys <pem>` —
the PEM of the *new* set, which must include the key signing that very
manifest) replaces the set on every box that verifies it. Both sides
persist the rotated set with the version that introduced it (app:
`update_channel.trusted_keys`; updater: the shadow state) and only ever
accept a set from a **newer** version, so a replayed older manifest cannot
re-admit a retired key. The persisted set beats the image's whenever its
version is newer than the running one — a rolled-back older image does not
re-trust a retired key — and yields to the image's set otherwise (a
hand-installed newer release carries its own).

**Who may sign a rotation (review 06 #3, owner decision 2026-08-26):** a
set that only **adds** keys may be signed by any key of the current set; a
set that **removes** a key must be signed by a **dormant** key of the
current set — key 2 (or any later key), never key 1, the active one (both
verifiers refuse it as `malformed`, nothing rotates, nothing is offered).
The reasoning: key 1 is the daily-use key and the one a thief gets; with
this rule a thief of key 1 can add keys but can never retire key 2, which
lives offline (D-2) precisely to survive that theft — so the "stolen" playbook
below always works. Order in the set is meaning: the first key is the active
one. Playbooks:

- **Key 1 lost:** sign the next release with key 2 (`sign-manifest.sh
  ~/.gxp/keys/signing-private-key-2.pem …`). Nothing on any box changes.
  Generate key 3 and ship `{key 2, key 3}` with `--trusted-keys` in that
  release's resource so a dormant key exists again.
- **Key 1 stolen:** generate key 3, build the release with
  `signing-public-keys.pem` = `{key 2, key 3}`, sign it with key 2 and
  `--trusted-keys backend/app/src/main/resources/update/signing-public-keys.pem`.
  Every box that sees that manifest refuses key 1 from then on. The thief
  cannot pre-empt this: a removal signed by key 1 is refused, so the worst
  a thief can do is publish a high version number that only *adds* keys —
  key 2's removal of key 1 still verifies on every box, provided its
  version is higher still (the version-monotonic set rule).
- **A box that must forget a rotation** (never routine): `install.sh
  --replace-key` reinstalls the resource's set and clears the shadow's
  rotated set; the app's persisted set is cleared with
  `UPDATE update_channel SET trusted_keys = NULL, trusted_keys_version = NULL`.

> **Open operator task — key custody (decision D-2, deferred by the owner
> on 2026-08-26 until the first customer).** Before the first production
> box leaves the door: (a) encrypt both private PEMs with a passphrase
> (`openssl pkey -in signing-private-key.pem -aes256 -out …` — `sign-manifest.sh`
> will prompt); (b) one **offline** backup of key 1 (encrypted USB or paper,
> not cloud); (c) key 2's private half on a **different** medium than the
> signing laptop, never used until a rotation; (d) both fingerprints above
> recorded where the operator keeps them. Until then both private keys are
> plaintext files on this one machine — a stolen disk is the fleet.

Per release — the `sha256` is the **registry manifest digest**, **not** the
local image ID. The one command to read it (index digest for a multi-arch
push, manifest digest for a single-arch one — always "what the tag resolves
to"; the fleet is arm64-only today, so push a **single-platform** image until
an amd64 box exists):

```bash
docker push ghcr.io/example/gxp-backend:1.0.1
docker buildx imagetools inspect ghcr.io/example/gxp-backend:1.0.1 --format '{{println .Manifest.Digest}}'
#   (the println is load-bearing: a bare '{{.Manifest.Digest}}' makes buildx 0.29 print its default table)
tools/update/sign-manifest.sh ~/.gxp/keys/signing-private-key.pem out/ \
    --channel stable --version 1.0.1 --artifact-ref ghcr.io/example/gxp-backend:1.0.1 \
    --sha256 <digest without sha256:> --published-at 2026-09-01T10:00:00Z \
    [--minimum-from-version 1.0.0] [--trusted-keys backend/app/src/main/resources/update/signing-public-keys.pem]
# upload out/stable.json and out/stable.json.sig to <base-url>/manifest/
```

The manifest is a fixed 8–10-line JSON layout (`docs/features/update-channel/02-design.md`
§2; optional lines `minimumFromVersion`, `trustedKeys`); the `.sig` is
base64 of the raw 64-byte signature over the exact file bytes. The tool
validates every field, signs, and verifies its own output before exiting 0.

**Release policy — TLS trust roots (review A-8).** The app trusts the image
JDK's CA bundle (UBI's, refreshed only by rebuilding) and the updater's
`docker pull` trusts the host's. So: (1) every release is rebuilt from the
**latest** `ubi9/openjdk-21-runtime` tag (the bundle refresh comes free);
(2) the update host and the registry must chain to **ISRG Root X1** (or
another root present in every deployed image and host) — changing the CA is
a *release* decision, never an ops one; (3) the host install requires
`unattended-upgrades` (security pocket) so the host bundle and Docker keep
moving. No custom truststore, no pinning.

### Installing the updater on the box

```bash
sudo backend/updater/install.sh --current-version 1.0.0   # script, units, conf (only if absent), the key SET; seeds the shadow; enables the timer
sudo nano /etc/gxp/updater.conf            # GXP_COMPOSE_DIR (default /opt/gxp/backend), backup dir, timeouts
systemctl list-timers gxp-updater.timer
journalctl -t gxp-updater                  # every run logs fixed strings + the outcome code, never file contents
sudo backend/updater/install.sh --uninstall   # timer, units, script, key set, conf, shadow removed; dumps kept
```

`--current-version` is the version running on the box at install time: it
seeds the updater's own state so the host-side **downgrade refusal is armed
from day one** (review A-9), not from the first applied update. The
deployment directory must **not** be under `/home` or `/root` — the unit
runs with `ProtectHome=yes` (`install.sh` refuses). **`GXP_COMPOSE_DIR/.env`
must hold every variable `compose.yaml` requires** (`GXP_DB_PASSWORD`,
`GXP_TLS_HOST`; the optional `GXP_UPDATE_*` too, if used): the updater reads
the unit through `docker compose config` as root under systemd with no other
environment, so a value that only ever lived on a shell's command line is
invisible to it. `install.sh` runs that same `compose config` with an empty
environment and refuses when it fails; should a box get there anyway (a
variable removed later), every run ends in a configuration error that the
manager sees as `updater.outcome = config-failed` with `message =
compose-config` (nothing is touched, the request stays pending, `journalctl
-t gxp-updater` has the line). The updater itself is
**not** updated through the channel: a new `gxp-updater.sh`, unit or key
set reaches a box by re-running `install.sh` on it (recorded as the next
feature — refreshing the updater from the verified image).

What a run does (`gxp-updater.sh`, all input regex-validated before it
reaches a command line): `flock` → snapshot `desired-update.json` (symlink,
non-regular or > 64 KiB → `malformed`) → replay guard (`requestedAt` must be
newer than the last handled request) → apply window (`[applyNotBefore,
+2 h)`) → OpenSSL verify → parse the **verified** manifest only → downgrade
check → state `in-progress` → `pg_dump --format=custom` into
`/var/lib/gxp/backups` (keep 5) → `docker pull <ref>@sha256:<digest>` (no
seam: the production branch is rehearsed by `test/rehearse-registry.sh`) →
`GXP_IMAGE=<ref>@sha256:<digest>` in `.env` → `compose up app` → health.
**Health** = `/api/system/ready` 200 **and** the newest
`flyway_schema_history` row `success` **and** the container not restarting,
within 180 s, then a 30 s hold and the same check again.

**Every step is bounded, and the unit's `TimeoutStartSec` sits above their
sum** (review 06 #1): `docker pull` by `GXP_PULL_TIMEOUT` (1800 s →
`pull-failed`, nothing swapped), `pg_dump` by `GXP_DUMP_TIMEOUT` (600 s →
`backup-failed`), `pg_restore` by `GXP_RESTORE_TIMEOUT` (600 s → the rung's
health decides), every `compose` command by 300 s and every in-container
probe by 30 s. The sum at the defaults is ≈ 118 min; the unit allows 3 h
(the arithmetic is in `gxp-updater.service`). That order matters: a run
killed by systemd mid-ladder would leave the box on the unhealthy image
with the request marked handled, so the unit's deadline must never be the
one that fires. Raising a `GXP_*_TIMEOUT` means re-checking that sum.

### What a rollback looks like

The ladder (D15): the new image fails health →
1. `.env` back to the previous image, `compose up app` (restart #2), health
   → **`rolled-back`** (the database was compatible; manager edits kept).
2. Still unhealthy (typically a failed migration the old image refuses too)
   → stop app, `pg_restore --list` the dump (unreadable → no wipe, plain
   `--clean` restore), `DROP SCHEMA public CASCADE; CREATE SCHEMA public`,
   `pg_restore` the pre-update dump, up (restart #3), health →
   **`rolled-back-with-restore`**. The wipe removes objects a *succeeded*
   migration created after the dump (they are not in it — review A-6), so a
   fixed release can be re-attempted. Manager edits made between the dump
   (seconds before the swap) and the restore are lost.
3. Still unhealthy → **`rollback-failed`**, the old image left as is.

Every outcome ends up in `GET /api/manager/updates` under `updater`
(`outcome`, `message` = `health-timeout` / `flyway-failed` /
`container-restarting` / `restore-failed` / `compose-config`, `attemptedVersion`,
`currentImage`, `previousImage`, `backupFile`). A refused migration usually
reads **`health-timeout`**: PostgreSQL rolls a failed versioned migration
back together with its own history row, so the app just never becomes
ready; `flyway-failed` is the explicit message for the rarer case where a
`success = f` row survives (a non-transactional migration or a manual
repair — rehearsal B plants one). A failed version is **not**
retried automatically; a manager's `POST /api/manager/updates/apply` (or the
next release) is the retry.

### Recovering from `rollback-failed`

The app is down or unhealthy on the old image; the database is whatever the
last step left. On the box, as root:

```bash
journalctl -t gxp-updater -n 50                       # which rung failed; `pg_restore exit=N` is logged
cat /var/lib/gxp-updater/state.json                   # backupFile, previousImage, attemptedVersion
cd /opt/gxp/backend && docker compose logs --tail 100 app db
docker compose stop app
docker compose exec -T db psql -U gxp -d gxp -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO gxp;'
docker compose exec -T db pg_restore -U gxp -d gxp < /var/lib/gxp/backups/<backupFile>   # what rung 2 does
grep GXP_IMAGE .env                                   # must name the previous image; fix by hand if not
docker compose up -d app && curl -k https://localhost/api/system/ready
```

The updater does nothing further for that request (hold rule); a manager's
apply-now or a new release starts the next attempt.

### Manual apply (without the timer)

```bash
# from the manager API: POST /api/manager/updates/apply → 202 and applyNotBefore = now
sudo systemctl start gxp-updater.service     # or wait ≤ 5 min for the timer
# without the app at all: write the same GXP_IMAGE=<ref>@sha256:<digest> into .env, `docker compose up -d app`,
# after a manual `pg_dump` — that is exactly what the script does, minus the health check and rollback
```

### The shadow state

The updater's memory is **its own** file `/var/lib/gxp-updater/state.json`
(root, 0600, `GXP_STATE_FILE`): the replay guard and the downgrade refusal
read only it. `state.json` in the `gxp-updates` volume is a **mirror** for
the app (14 lines, 0644); deleting or replacing the mirror changes nothing
the updater does. The shadow carries three extra lines: `currentImageId` —
the local image ID after the last applied update, recorded for diagnosis,
never compared (its meaning depends on the Docker image store) — and
`trustedKeys`/`trustedKeysVersion`, the rotated trust set (see "Keys").
Root **never opens a path inside the app-owned volume for writing**: the
mirror is built in the volume's root-owned parent directory (same
filesystem) and `rename(2)`d into place (review A-3); the only read there is
the snapshot of the request file (symlink, non-regular, > 64 KiB refused).

### Rehearsals (developer box, throwaway compose project)

```bash
bash backend/updater/test/parse.sh                 # readers + write_state + symlink/size guards, no Docker
sudo -E backend/updater/test/smoke-apply.sh        # happy path incl. replay guard and the deleted mirror
sudo -E backend/updater/test/rehearse-rollback.sh all   # A: image never ready; B: failed migration → schema wipe + restore
sudo -E backend/updater/test/rehearse-registry.sh       # C: the PRODUCTION pull path against a local registry:2, offline fallback
sudo -E backend/updater/test/rehearse-systemd.sh        # install.sh + the hardened unit under systemd (refuses if a real install exists), uninstall
```

They use project `gxp-rehearsal` in a throwaway directory (`/tmp`, or `/opt`
for the systemd one), never `gxp`, and clean up. Smoke
and rollback use local tags without a registry digest, so they run the
updater's **rehearsal-only** local-image path (`GXP_REHEARSAL_LOCAL_IMAGE=1`,
refused under systemd together with the other test seams); scenario C runs
the real `docker pull ref@sha256:…` branch with no seam (`registry:2` is a
dev-box test image, not a product dependency). The "exactly one restart"
counter is a live `docker events` stream captured for the whole rehearsal
(`--since/--until` replays a bounded ring the health probes overflow —
review 06 #5); each `start` is annotated at once with the container's
`RestartCount`, and only starts at count 0 are counted — a Docker
`restart: unless-stopped` policy restart (rehearsal B's crash loop) carries
≥ 1, every start through the start API (a fresh container, `compose up`
after `compose stop`) resets it to 0. Where a clean apply is claimed the
running container's `RestartCount` is asserted 0 too. Run them after every change
to `backend/updater/**`. `rehearse-systemd.sh` runs the real `install.sh`
and the unit with every hardening line under systemd (a throwaway project
under `/opt`, since `ProtectHome`/`PrivateTmp` hide `/home` and `/tmp`), then
`install.sh --uninstall`; it refuses to run on a box with a real install —
see the feature handoff for the journal.

**Clock:** the apply window and the replay guard compare instants — a box
without NTP whose clock is hours off applies at the wrong hour, never
unsafely (a request is handled at most once either way).

**Backup story:** `pre-update-<version>-<ts>.dump` files in
`/var/lib/gxp/backups` (0600, newest 5 kept) are `pg_dump --format=custom`
like the manual backup above — they restore the same way. The `gxp-updates`
volume holds no state worth backing up.
