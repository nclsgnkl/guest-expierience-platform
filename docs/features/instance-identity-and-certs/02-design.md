# Design: Instance Identity & Certificates
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review

> HOW for `01-prd.md` (REQ-801…828). Remote-operations Part B. Reuses, and
> deliberately does not modify, the shapes `update-channel` established:
> the outbound HTTP convention (`ManifestClient` javadoc, D16), the
> fixed-layout exchange files in the `gxp-updates` volume, the host-side
> `systemd` timer + hardened unit + `install.sh` layout, the hostile-input
> posture, the class-name-only logging. Facts verified against the local
> `~/.m2` (Quarkus 3.33.3.1) and the local image/host on 2026-08-26:
> `quarkus.tls.<bucket>.reload-period` and `quarkus.tls.key-store.sni`
> exist (`quarkus-tls-registry` config model); `TlsCertificateReloader`
> lives in `quarkus-vertx-http`; the app image
> (`ubi9/openjdk-21-runtime:1.24`) ships **no `openssl` CLI** (only
> `openssl-libs`) and only `curl-minimal`; `lego` 4.9.1 is an apt candidate
> on Debian 12 (the dev box, a Raspberry Pi).

## Architecture decision

```
                 ┌──────────────── box ────────────────────────────────────────────┐
                 │  app container (uid 185)                host (root, systemd)    │
                 │  de.gxp.instance                        backend/acme/           │
                 │   InstanceIdentity  ── /tls/instance-key.pem ──▶ gxp-acme-hook.sh (signs with openssl)
                 │   InstanceControl   ── /updates/instance.json ─▶ gxp-acme.sh ──▶ lego (apt) ──▶ ACME CA
                 │   CertificateReconciler ◀─ /tls/public.crt,.key ◀── staged rename      │       │
   manager ──▶   │   InstanceResource  ◀─ /updates/acme-state.json ◀─┘                     │       │
   /api/manager/ │        │ signed POST /enroll, /unenroll                                  │       │
   instance      │        ▼                                     PUT/DELETE /challenges/{fqdn} (signed)
                 └────────┼──────────────────────────────────────────────┼──────────────────┼──────┘
                          ▼                                              ▼                  ▼
                   enrollment service (ours, out of repo; api/enrollment-service.yaml)     DNS-01 lookup
                          │ DNS API credential (only here)                                  by the CA
                          ▼
                   _acme-challenge.<fqdn> TXT
```

### 1. `de.gxp.instance` — the application side (REQ-801…811, 817, 821–823, 826)

| Sub-package | Type | Role |
|---|---|---|
| `boundary` | `InstanceResource` (implements generated `ManagerInstanceApi`, class-level `@RolesAllowed("admin")`) | four one-line delegations: status, enroll, unenroll, requestRenewal |
| `control` | **`InstanceControl`** (public) | the only type other code references: the four operations, the job entry point `reconcileServedCertificate()`, outcome mapping, audit calls |
| `control` | `InstanceIdentity` (package-private, `@ApplicationScoped`, `@Observes StartupEvent`) | REQ-801/802: `gxp.instance.tls-dir` → `instance-key.pem`/`.pub`. Missing → `KeyPairGenerator.getInstance("Ed25519")`, PKCS#8 via `key.getEncoded()` (PEM armour written by hand, 64-col base64), created with `Files.newOutputStream` on a temp name + `PosixFilePermissions` `rw-------` + `ATOMIC_MOVE`; `.pub` = X.509 SPKI PEM 0644. Present → `KeyFactory("Ed25519")` + `PKCS8EncodedKeySpec`; any failure → `IllegalStateException("instance key unreadable: <path> — delete it to generate a new identity (the box must then re-enroll under a new name)")`. Exposes `publicKeySpkiDer()`, `fingerprint()`, `sign(byte[])` |
| `control` | `SignedRequest` (package-private, static) | REQ-806: builds the canonical string and the four headers (§3); Ed25519 via `Signature.getInstance("Ed25519")`; nonce = 16 `SecureRandom` bytes hex; timestamp `Instant.now().truncatedTo(SECONDS)` |
| `control` | `EnrollmentClient` (package-private) | the outbound convention copied from `ManifestClient` (D16 shape: JDK `HttpClient` built once, 5 s connect, 10 s request, 15 s budget, 64 KiB cap on declared and streamed body, no redirects, no proxy, no retry, `User-Agent: gxp-instance/1`, `Accept`/`Content-Type: application/json`) plus **`POST`/`PUT`/`DELETE` with a body and the signed headers**. Imports `InstanceIdentity` only for `sign` — the one sanctioned identity carrier, unlike `ManifestClient` |
| `control` | `EnrollmentResponse` (package-private record) + `EnrollmentResponseParser` | Jackson (`FAIL_ON_UNKNOWN_PROPERTIES = false` here — the service may add fields; unknown fields are ignored, known ones validated by the REQ-826 regexes) |
| `control` | `InstanceValidation` (package-private, static) | the REQ-826 regex table — the single Java home of the patterns; the same literals live in `gxp-acme.sh` and are pinned by the shared fixture `backend/acme/test/fixtures/validation/{accept,reject}.txt` read by both test sides |
| `control` | `InstanceExchangeFiles` (package-private) | the only code touching `gxp.instance.exchange-dir` (= `/updates`): `writeInstance(InstanceFile)` / `deleteInstance()` (temp + `ATOMIC_MOVE`), `readAcmeState()` (tolerant, capped 64 KiB, missing/unparseable → `Optional.empty()` + class-name log). Same fixed-layout writer/reader style as `de.gxp.update.control.ExchangeFiles`; a copy, not a shared type (see PRD risks) |
| `control` | `CertificateReconciler` (package-private) | REQ-817: reads `/tls/public.crt` (PEM chain, `CertificateFactory.getInstance("X.509").generateCertificates`) and `/tls/public.key` (PKCS#8, EC or RSA — `KeyFactory` by the PEM's algorithm OID, JDK only); checks leaf `notAfter > now + 5 min`, leaf public key equals the private key's public half, leaf SAN contains the enrolled FQDN; loads `server.p12` with `gxp.tls.keystore-path`/`-password` (the entrypoint's `GXP_TLS_KEYSTORE_*`), compares alias `public`'s leaf serial + `notAfter` with the file's; on difference `setKeyEntry("public", key, pass, chain)`, on expired/absent/invalid `deleteEntry("public")`; stores to `server.p12.tmp` + `ATOMIC_MOVE`; then `TlsConfigurationRegistry.getDefault().reload()` and fires `CertificateUpdatedEvent` (both from `io.quarkus.tls`, already on the classpath through `quarkus-vertx-http`). Returns an outcome code; never throws |
| `control` | `InstanceStateStore` (package-private, `@ApplicationScoped`) | the **transactional** collaborator (`load()`, `saveEnrollment(...)`, `clearEnrollment(outcome)`, `recordOutcome(...)`, `recordRenewRequest(at)`), the D9 precedent — HTTP never inside a transaction |
| `control` | `InstanceEnrollmentRepository` (package-private, Panache) | the single row |
| `entity` | `InstanceEnrollment` (`@Entity`, table `instance_enrollment`) + `InstanceEnrollmentMapper` (→ `InstanceStatusDto` pieces) | public fields only where the Control/store touch them |
| `de.gxp.infrastructure.scheduler` | `InstanceScheduler` (package-private) | one `@Scheduled` method, one delegating call, class-name-only catch (REQ-823) |

Control dependencies: `InstanceControl → AuditControl` (three request-path
methods). Nothing else references `InstanceControl` except `InstanceResource`
and `InstanceScheduler`. `DeviceControl` (`getPairingCa`) is untouched.

**Transactions and audit** (the `UpdateControl` table, same rules):

| `InstanceControl` method | Path | `@Transactional`? | Audit |
|---|---|---|---|
| `status()` | `GET` | no | no |
| `enroll(EnrollRequest)` | `POST /enroll` | **no** (HTTP inside) — validates state in the store, calls the client, then `store.saveEnrollment` commits, then `record(ACTION_INSTANCE_ENROLL, ENTITY_INSTANCE, name, null)` in its own tx | yes — `EXPLICIT_ENTRIES` |
| `unenroll()` | `POST /unenroll` | **no** (best-effort HTTP) — `store.clearEnrollment` first (local truth), reconciler drops the alias, file deleted, then the signed unenroll, then `record(ACTION_INSTANCE_UNENROLL, ENTITY_INSTANCE, oldName, null)` | yes — `EXPLICIT_ENTRIES` |
| `requestRenewal()` | `POST /certificate/renew` | **yes** | `record(ACTION_CERTIFICATE_RENEW, ENTITY_INSTANCE, name, null)` |
| `reconcileServedCertificate()` | scheduler | no | never |

`AuditCoverageTest`: `requestRenewal` is found by the `@Transactional public`
scan; `enroll`/`unenroll` join `EXPLICIT_ENTRIES`
(`de/gxp/instance/control/InstanceControl.java` → `List.of("enroll", "unenroll")`). Pinned count **28 → 31**.
Constants added to `AuditControl`: `ACTION_INSTANCE_ENROLL = "instance-enroll"`,
`ACTION_INSTANCE_UNENROLL = "instance-unenroll"`,
`ACTION_CERTIFICATE_RENEW = "certificate-renew"`, `ENTITY_INSTANCE = "instance"`.

**`enroll()` sequence:**

```
enroll(token):
  cfg.baseUrl unset                                  → 409 enrollment-not-configured
  baseUrl not https:// and !allow-plain-http         → 409 enrollment-not-configured  (code insecure-base-url in status)
  row.name != null                                   → 409 already-enrolled
  token !~ TOKEN_RE                                  → 400 enrollment-rejected (no request)
  r = client.post(base + "/enroll", {"token": token}, signed)
      UNREACHABLE | TOO_LARGE | 5xx                  → 503 enrollment-unavailable,   store.recordOutcome(service-unreachable)
      401 | 403 | 404 | 409                          → 400 enrollment-rejected,      store.recordOutcome(rejected)
      other non-2xx                                  → 503 enrollment-unavailable,   store.recordOutcome(service-error)
  resp = parser.parse(r.body)  parse/validation fail → 503 enrollment-unavailable,   store.recordOutcome(malformed-response)
  store.saveEnrollment(resp, identity.fingerprint(), now)       (one tx)
  files.writeInstance(fqdn, acmeDirectoryUrl, challengeUrl, renewRequestedAt = null)
  audit.record(instance-enroll)                                 (own tx)
  return status()
```
The token variable is a method-local `String` handed to the client and
never assigned to a field, DTO or log; the `EnrollRequest` DTO is not
retained. (A `char[]`-wipe is not attempted — the JDK `HttpClient` copies
the body anyway; the guarantee is *no persistence, no log*, REQ-807.)

**`reconcileServedCertificate()` sequence** (every 15 min, and called at the
end of `unenroll()` so the alias drops immediately):

```
row = store.load()
enrolled = row.name != null && row.keyFingerprint == identity.fingerprint()
if row.name != null && !enrolled     → store.recordOutcome(key-mismatch); files.deleteInstance(); (alias handling below still runs)
material = reconciler.readPublicMaterial()      // absent | invalid(code) | valid(chain, key)
served   = reconciler.servedPublicEntry()       // absent | (serial, notAfter)
if !enrolled || material.absent || material.invalid || material.expired:
     if served.present → reconciler.removePublicAlias(); reload; log "public certificate removed: <code>"
else if served.absent || served.serial != material.serial:
     reconciler.installPublicAlias(material); reload; log "public certificate installed" (count 1)
state = files.readAcmeState()  → kept in memory for status() (not persisted; the file is the source)
```

### 2. TLS: one listener, two certificates (REQ-817, 819, 820)

- `server.p12` (the entrypoint's keystore) holds alias `server` (self-signed,
  first) and, when installed, alias `public`. `%prod` config gains
  `quarkus.tls.key-store.sni=true` and `quarkus.tls.reload-period=1h`
  (`alias` stays unset, one passphrase for both entries — the documented
  precondition of SNI with a P12). The HTTP server selects `public` for
  SNI = FQDN and the default entry otherwise. **Default-entry order is
  verified by the compose smoke** (`curl https://<ip>` → self-signed;
  `curl --resolve <fqdn>:443:<ip> https://<fqdn>` → public chain); if
  Vert.x's default turned out not to be the first alias, the fallback design
  is two buckets (`quarkus.tls.public.*`) on the same server — recorded, not
  built.
- Reload without restart: the reconciler calls `reload()` on the default
  `TlsConfiguration` and fires `CertificateUpdatedEvent`; the vertx-http
  `TlsCertificateReloader` swaps the server's key manager. `reload-period=1h`
  is the belt for anything else that rewrites the file (e.g. the entrypoint
  at a restart — moot, but cheap). The listener never closes.
- **Fallback** (REQ-817/821): an expired public certificate → alias removed →
  a client connecting by the name receives the self-signed certificate. With
  REQ-820 that certificate names the FQDN too, so a device trusting the
  instance CA gets a clean connection; any other device gets an untrusted-CA
  error instead of an expired-certificate error — both are "click-through",
  neither is "nothing served".
- REQ-820 (entrypoint): `InstanceControl` writes `/tls/instance-name`
  (the FQDN, 0644) on enrollment and deletes it on unenrollment; the
  entrypoint's re-issue condition becomes "`tls-host` **or** the SAN list
  recorded in `/tls/tls-sans` differs from what `GXP_TLS_HOST` + the optional
  `instance-name` file give"; SAN = `ip:<host>|dns:<host>[,dns:<fqdn>]`,
  regex-validated (`^[a-z0-9.-]{1,253}$`) before it reaches `keytool`. CA
  unchanged, so kiosks keep trusting.

### 3. The signed-request envelope (REQ-806) — `GXP-SIGNED-REQUEST/1`

Headers on every request from a box to the service:

| Header | Value |
|---|---|
| `GXP-Instance-Key` | base64 (standard, no wrap) of the SPKI DER of the Ed25519 public key |
| `GXP-Instance-Timestamp` | RFC 3339 UTC, seconds, `Z` (`2026-08-26T10:15:00Z`) |
| `GXP-Instance-Nonce` | 32 lower-case hex chars (16 random bytes) |
| `GXP-Instance-Signature` | base64 of the 64-byte Ed25519 signature over the canonical string |

Canonical string (exact bytes, `\n` = 0x0A, no trailing newline):

```
GXP-SIGNED-REQUEST/1\n<METHOD>\n<PATH>\n<TIMESTAMP>\n<NONCE>\n<lower-hex sha256(body)>
```
`METHOD` upper-case; `PATH` = the request-target path without scheme/host
and without a query (the contract defines no queries); an empty body hashes
as the empty string. The service accepts a timestamp within ±300 s and a
nonce not seen for that key within 600 s. Two implementations: `SignedRequest`
(JDK) and `gxp-acme-hook.sh` (`printf '%s' "$CANON" | openssl pkeyutl -sign
-rawin -inkey instance-key.pem | base64 -w0`; key header via `openssl pkey
-pubout -outform DER | base64 -w0`; body hash `sha256sum`). Shared vector:
`backend/acme/test/fixtures/envelope/{instance-key.pem,instance-key.pub,canonical.txt,body.json,signature.b64,headers.txt}` —
a **test-only** key generated once and committed; both `SignedRequestTest`
and `hook-sign.sh` must reproduce `signature.b64` byte-for-byte.

### 4. The enrollment/challenge service contract (REQ-812) — `api/enrollment-service.yaml`

OpenAPI 3.1, **not** referenced by any generator (`pom.xml` names
`api/openapi.yaml` explicitly). Security scheme `instanceSignature` (type
`apiKey`-style documentation of §3). Base path is `GXP_ENROLLMENT_BASE_URL`
for `/enroll`, `/unenroll`; the challenge operations live under the
`challengeUrl` returned at enrollment (normally the same base — kept
separate so we can move it without a release).

| Operation | Request | Responses |
|---|---|---|
| `POST /enroll` | envelope + `{"token": "<TOKEN_RE>"}` | `200 {"name","fqdn","acmeDirectoryUrl","challengeUrl","relay":{"host","port"}}` (idempotent per key: a bound key gets its existing name and the token is not consumed); `401` bad envelope; `403 {"code":"token-rejected"\|"token-used"}`; `409 {"code":"name-revoked"}` (this key's name was revoked by us — the box must reset its key) |
| `POST /unenroll` | envelope, empty body | `204`; `401`; `404` not bound |
| `PUT /challenges/{fqdn}` | envelope + `{"recordName": "_acme-challenge.<fqdn>", "txt": "<[A-Za-z0-9_-]{1,255}>"}` | `204` published (the service waits for its provider to accept, not for propagation); `401`; `403 {"code":"not-your-name"}`; `404` not bound; `429` |
| `DELETE /challenges/{fqdn}` | envelope, empty body | `204`; `401`; `403`; `404` |

Rules the file states for the server: bind on first `/enroll` with a valid
unused token (token consumed), same key → same name forever, a revoked name
never re-issued, the challenge operations only for the bound name, replay
protection per §3, no request body logged, and the DNS credential never
leaves the service. `EnrollmentServiceDouble` (test scope,
`de.gxp.infrastructure.testsupport`) implements every row: constructor
takes the valid tokens and an optional `challtestsrvUrl` (forwarding
`{"host": recordName, "value": txt}` to `POST /set-txt` / `/clear-txt`),
counts requests per operation (the REQ-821 "one request per tick"
assertion), and has a `main(--port --token --challtestsrv)` for
`rehearse-pebble.sh`.

### 5. `backend/acme/` — the host side (REQ-813…816, 818, 822, 826)

**Choice: `lego` from the distribution package, host-side, `exec` DNS
provider.** Reasoning and rejected alternatives in D2.

| File | Role |
|---|---|
| `gxp-acme.sh` | the timer script (below); root; `flock`; reads `instance.json` from the exchange volume (hostile: `snapshot_file` semantics copied from the updater), regex-validates every value, runs `lego` with `--path /var/lib/gxp-acme --server <acmeDirectoryUrl> --email $GXP_ACME_EMAIL --accept-tos --dns exec --domains <fqdn> --key-type ec256 run` (first) or `renew --days 30` (later; `--days 90`-equivalent forced run when `renewRequestedAt` is newer than the shadow's `lastRenewRequestedAt`), `EXEC_PATH=/usr/local/lib/gxp/gxp-acme-hook.sh`, `EXEC_MODE=RAW`, `EXEC_PROPAGATION_TIMEOUT`/`EXEC_POLLING_INTERVAL` from conf; on success converts the key (`openssl pkey -in <fqdn>.key -out …` → PKCS#8), and places `public.crt` (lego's `.crt` = full chain) and `public.key` into the `gxp-tls` mountpoint via `place_file` (staged in the root-owned parent, `chown 185:root`, `chmod 0600`, `mv -fT`); writes `acme-state.json` (mirror) and its own shadow `/var/lib/gxp-acme/state.json` (root 0600: `lastRenewRequestedAt`, `lastAttemptAt`, `outcome`, `notAfter`). Outcomes: `none`, `not-enrolled`, `malformed`, `issued`, `renewed`, `not-due`, `challenge-failed`, `acme-failed`, `deploy-failed`. Exits 0 after every outcome; non-zero only for configuration errors before touching anything |
| `gxp-acme-hook.sh` | lego's exec hook. `present <recordName> <txt>` / `cleanup <recordName> <txt>` (RAW mode). Reads `GXP_CHALLENGE_URL`, `GXP_FQDN` and `GXP_INSTANCE_KEY` from the environment `gxp-acme.sh` exports (never argv for the key path — the path is not secret, but consistency); refuses a `recordName` ≠ `_acme-challenge.<GXP_FQDN>.` (with or without trailing dot); builds the envelope (§3) and `curl -sS --fail-with-body --max-time 15 -X PUT|DELETE` with the four headers; non-2xx → exit 1 (lego reports `challenge-failed`). Logs fixed strings + HTTP status only |
| `gxp-acme.service` | `Type=oneshot`, `ExecStart=/usr/local/lib/gxp/gxp-acme.sh`, `EnvironmentFile=/etc/gxp/acme.conf`, the updater's hardening block verbatim (`ProtectSystem=full`, `ProtectHome`, `PrivateTmp`, `PrivateDevices`, `ProtectKernelTunables/Modules`, `ProtectControlGroups`, `NoNewPrivileges`, `RestrictSUIDSGID`, `LockPersonality`, `RestrictRealtime`, `SystemCallArchitectures=native`), `ReadWritePaths=/var/lib/gxp-acme /var/lib/docker/volumes`, `TimeoutStartSec=15min` |
| `gxp-acme.timer` | `OnCalendar=hourly`, `Persistent=true`, `RandomizedDelaySec=1800` |
| `acme.conf.example` | `GXP_COMPOSE_PROJECT=gxp`, `GXP_ACME_EMAIL=` (required — ours, the ACME contact), `GXP_ACME_DIR=/var/lib/gxp-acme`, `GXP_STATE_FILE=/var/lib/gxp-acme/state.json`, `GXP_APP_UID=185`, `EXEC_PROPAGATION_TIMEOUT=300`, `EXEC_POLLING_INTERVAL=10`; rehearsal-only overrides (`GXP_EXCHANGE_DIR`, `GXP_TLS_DIR`, `LEGO_CA_CERTIFICATES`, `GXP_ACME_SERVER_OVERRIDE`) refused under systemd like the updater's seams |
| `install.sh` | `command -v lego` or exit 2 printing `apt-get install lego`; prints `lego --version`; copies script + hook (0755) to `/usr/local/lib/gxp/`, units, `acme.conf` (only if absent, then prints "set GXP_ACME_EMAIL"), `mkdir -p /var/lib/gxp-acme` 0700; `daemon-reload`, `enable --now gxp-acme.timer`; `--uninstall` |
| `test/parse.sh` | the readers/validators + `place_file` guards, no Docker |
| `test/hook-sign.sh` | the envelope against `fixtures/envelope/` (byte-equal signature) |
| `test/validation.sh` | the REQ-826 regexes against `fixtures/validation/` |
| `test/rehearse-pebble.sh` | throwaway compose project `gxp-rehearsal` with `ghcr.io/letsencrypt/pebble` (+ `pebble-challtestsrv`, `-dnsserver` pointed at it), the double's `main` with `--challtestsrv`, a temp exchange dir + TLS dir + throwaway instance key, `gxp-acme.sh` with the rehearsal overrides and `LEGO_CA_CERTIFICATES=<pebble minica>`; asserts `public.crt` (SAN = the test FQDN, uid 185, 0600) and `acme-state.json` outcome `issued`; then a second run → `not-due`; then `renewRequestedAt` bumped → `renewed`. Cleans up |

`gxp-acme.sh` control flow:

```
 0  flock -n /run/lock/gxp-acme.lock
 1  EXCHANGE = docker volume inspect <project>_gxp-updates; TLS = … <project>_gxp-tls (both mountpoints, root-owned parents)
    no instance.json → outcome none (state only if it changed) → exit 0
 2  snapshot instance.json (symlink / non-regular / > 64 KiB → malformed); parse fixed layout; every value regex-checked
    (fqdn, https URLs, RFC 3339 or empty renewRequestedAt) → malformed
 3  instance-key.pem readable in TLS, .pub parses (openssl pkey -pubin -noout) → else deploy-failed(code key-unreadable)
 4  due? = no certificate for fqdn in $GXP_ACME_DIR | notAfter − now < 30 d | renewRequestedAt > shadow.lastRenewRequestedAt
    not due → outcome not-due → exit 0
 5  shadow: lastAttemptAt = now, lastRenewRequestedAt = renewRequestedAt (carried forward on every outcome)
 6  lego … run | renew  (env: EXEC_PATH, EXEC_MODE=RAW, GXP_CHALLENGE_URL, GXP_FQDN, GXP_INSTANCE_KEY=<TLS>/instance-key.pem)
    hook non-zero → challenge-failed ; lego non-zero → acme-failed
 7  openssl pkey → PKCS#8; openssl x509 -noout -enddate → notAfter; SAN contains fqdn (else deploy-failed)
 8  place_file TLS public.key 0600 ; place_file TLS public.crt 0600 (both uid 185)      → deploy-failed on error
 9  outcome issued | renewed, notAfter → shadow, then mirror acme-state.json (0644) via place_file in EXCHANGE
```

### 6. Exchange files (REQ-815) — fixed layout, `gxp-updates` volume

`instance.json` (app → host, 0644, temp + atomic move, exactly these lines):
```
{
  "schemaVersion": 1,
  "fqdn": "k3f9.props.example.com",
  "acmeDirectoryUrl": "https://acme-v02.api.letsencrypt.org/directory",
  "challengeUrl": "https://enroll.example.com/v1",
  "renewRequestedAt": "2026-08-26T10:15:00Z"      ← or ""
}
```
`acme-state.json` (host → app, 0644, `place_file`):
```
{
  "schemaVersion": 1,
  "outcome": "issued",
  "lastAttemptAt": "2026-08-26T10:20:11Z",
  "notAfter": "2026-11-24T10:19:00Z",             ← or ""
  "message": ""                                    ← a fixed code, never free text
}
```
Compose comments change "two files" → "four files"; the volume still holds
no secrets and stays out of the backup.

### 7. Files on the `gxp-tls` volume (REQ-801, 816, 820)

| File | Owner/mode | Writer | Purpose |
|---|---|---|---|
| `ca.p12`, `ca.pem`, `server.p12`, `keystore-pass`, `tls-host` | existing | entrypoint / app (`server.p12` now also the reconciler) | unchanged |
| `tls-sans` | 185, 0600 | entrypoint | the SAN list last issued (REQ-820 re-issue trigger; replaces the `tls-host` comparison — `tls-host` stays for compatibility) |
| `instance-key.pem` | 185, 0600 | app (once) | the identity's private half; read by root's hook |
| `instance-key.pub` | 185, 0644 | app (once) | SPKI PEM |
| `instance-name` | 185, 0644 | app | the FQDN while enrolled (entrypoint SAN input) |
| `public.crt`, `public.key` | 185, 0600 | host (`place_file`) | the ACME material; merged into `server.p12` by the app |

Backup story unchanged: `pg_dump` + the `gxp-tls` volume — which now
carries the identity, so a restored volume restores the name binding.

## Data model

`V10__instance_enrollment.sql`:
```sql
CREATE TABLE instance_enrollment (
    id                     smallint    PRIMARY KEY CHECK (id = 1),
    name                   text,
    fqdn                   text,
    key_fingerprint        text,
    acme_directory_url     text,
    challenge_url          text,
    relay_host             text,
    relay_port             integer     CHECK (relay_port IS NULL OR relay_port BETWEEN 1 AND 65535),
    enrolled_at            timestamptz,
    last_outcome           text        NOT NULL DEFAULT 'never',
    last_outcome_at        timestamptz,
    renew_requested_at     timestamptz,
    updated_at             timestamptz NOT NULL,
    CHECK ((name IS NULL) = (fqdn IS NULL) AND (name IS NULL) = (key_fingerprint IS NULL)
           AND (name IS NULL) = (enrolled_at IS NULL))
);
INSERT INTO instance_enrollment (id, updated_at) VALUES (1, now());
```
No token column, no key column, by schema. `relay_*` is stored for Part D
and read by nothing in this feature except the status DTO.

## API contract (`api/openapi.yaml`, new tag `manager-instance` → `InstanceResource`)

All `bearerAuth`, `admin`; `ContractSecuredOperationsDeniedTest` grows 54 → 58.

| Operation | Method/path | Request | Responses |
|---|---|---|---|
| `getInstanceStatus` | `GET /api/manager/instance` | — | `200 InstanceStatusDto` |
| `enrollInstance` | `POST /api/manager/instance/enroll` | `EnrollInstanceRequest {token: string, ^[A-Za-z0-9._-]{16,128}$}` | `200 InstanceStatusDto`; `400 enrollment-rejected`; `409 enrollment-not-configured` / `already-enrolled`; `503 enrollment-unavailable` (all `ApiError`) |
| `unenrollInstance` | `POST /api/manager/instance/unenroll` | — | `200 InstanceStatusDto`; `409 not-enrolled` |
| `requestCertificateRenewal` | `POST /api/manager/instance/certificate/renew` | — | `202 InstanceStatusDto`; `409 not-enrolled` |

`InstanceStatusDto`:
```
identity:    { fingerprint: string, publicKey: string (base64 SPKI DER) }
enrollment:  { state: not-configured|unenrolled|enrolled|key-mismatch, name?, fqdn?, enrolledAt?,
               relayHost?, relayPort?, lastOutcome: string, lastOutcomeAt? }
certificate: { mode: self-signed|public, selfSignedNames: string[], publicNotAfter?,
               acme?: { outcome: string, lastAttemptAt?, notAfter?, message?: string }, renewRequestedAt? }
```
`lastOutcome`/`outcome`/`message` are free strings (forward-tolerant, the
D21 precedent). `selfSignedNames` is read from `server.p12`'s `server` entry
SAN at request time. Guest/pairing paths: no change (checkable on the diff).

## Sequence — enrollment and first certificate

```
manager → POST /api/manager/instance/enroll {token}
app     → POST <base>/enroll  (envelope)              service: token ok, key unbound → name k3f9, bound
app     ← 200 {name, fqdn, acmeDirectoryUrl, challengeUrl, relay}
app     : V10 row, /tls/instance-name, /updates/instance.json, audit instance-enroll
manager ← 200 status (enrolled, certificate.mode = self-signed)
…≤ 1 h (timer)…
host    : gxp-acme.sh reads instance.json → lego run
lego    → hook present _acme-challenge.k3f9.props.example.com. <txt>
hook    → PUT <challengeUrl>/challenges/k3f9.props.example.com (envelope)   service → DNS API → TXT
lego    : waits for the TXT on the authoritative NS, asks the CA to validate, finalises, downloads chain
lego    → hook cleanup → DELETE …/challenges/…
host    : PKCS#8, place_file public.key/public.crt into gxp-tls, acme-state.json = issued
…≤ 15 min (job)…
app     : reconciler installs alias public into server.p12, reload() + CertificateUpdatedEvent
browser (via relay, Part D) → SNI k3f9.props.example.com → public chain; kiosk → 192.168.1.10 → self-signed
```

## Risks & trade-offs

- **Vert.x default alias under SNI** — see §2; verified by the compose
  smoke, with a two-bucket fallback design named.
- **Root reads the app's identity key.** The hook signs with
  `instance-key.pem` read from the volume. Root can read everything on the
  box anyway; what matters is that the key is never *copied* — the hook
  passes a path, never the bytes, and the systemd unit's `ReadWritePaths`
  is limited to the two directories it needs.
- **`lego` version drift** — Debian's 4.9 vs upstream; the flags used are
  old and stable; `install.sh` prints the version; the dev checks `lego
  dnshelp --code exec` and `lego renew --help` at implementation and pins
  what was seen in the handoff.
- **Two helper copies** (`EnrollmentClient`, `InstanceExchangeFiles`) —
  accepted for the concurrency reason in the PRD; follow-up tracked in
  `index.md`.
- **ACME rate limits** are ours to manage (PRD prerequisites); a failed
  issuance is a recorded outcome and the next hour's retry — a fleet
  hammering a rate limit is bounded by the hourly timer and the `not-due`
  short-circuit (lego is only invoked when due).
- **A restored `gxp-tls` volume with an older key** than the DB's binding
  → `key-mismatch`, a visible state with a documented fix, never a silent
  wrong certificate.
- **CT logs** publish `<id>.props.<domain>` — opaque by design (§5); the
  status API shows the name to the manager only.

## Deferred: the LAN-name record (designed, not built)

`<id>.lan.props.<our-domain>  A  <LAN IP>` as a second SAN in the same order,
maintained by the service from a LAN IP the box reports at enrollment and
on change, would give the **manager's own devices** a warning-free LAN URL
while the uplink is up. Not built because: (1) it does nothing for kiosks
(PRD → "The LAN-name problem"), (2) DNS-rebind protection in common routers
blocks private answers and needs a per-router exception, (3) it reports a
customer-network fact to us that nothing else needs, (4) the owner's
standing preference for the minimal option first. Revisit with the first
customer who asks for a warning-free LAN manager URL.

## Open decisions

None. No `[NEEDS CLARIFICATION]` markers.

## Decisions taken under delegated authority (owner via lead, 2026-08-26)

| # | Decision | Chosen | Rejected alternative |
|---|---|---|---|
| D1 | Where the identity is generated | in the app at startup (`InstanceIdentity`, JDK `KeyPairGenerator("Ed25519")`), PKCS#8/SPKI PEM on the `gxp-tls` volume | in the entrypoint with `keytool` (cannot export an unencrypted private key PEM); in the database (the key must never enter the DB, §12 item 6) |
| D2 | **ACME client** | **`lego` from the distribution package, host-side beside the updater**, `exec` DNS provider (RAW mode) calling our hook; renewed by a `systemd` timer | **acme.sh** — ~8 000 lines of shell with `eval`, self-updating from GitHub unless disabled, not OS-packaged: everything `backend/CLAUDE.md` forbids in our own scripts. **certbot** — OS-packaged too (`--manual --manual-auth-hook` would do the same job), but a Python dependency tree an order of magnitude larger; recorded as the fallback if a target distribution has no `lego` package. **In the app image** — the image has no `openssl` CLI and no package for lego; adding either means a binary fetched at build time, +30 MB, patched only by our release instead of by `unattended-upgrades` (which the host install already requires, A-8). **A minimal RFC 8555 client in JDK `HttpClient`** — JWS, a hand-built PKCS#10 CSR with a SAN extension (no public JDK API), account keys of a second type (Let's Encrypt refuses Ed25519 accounts), nonce/retry/order polling: ~600 lines of security-sensitive code we own for a protocol a packaged tool already speaks — and §9 said "shell out to an existing small client" |
| D3 | Who holds the challenge credential | nobody on the box: the hook proves the instance key to our service (§12 item 7 ratified) | a DNS token on the box (zone-wide); a wildcard certificate distributed to boxes |
| D4 | Envelope | `GXP-SIGNED-REQUEST/1`: four headers, canonical string, raw Ed25519, deterministic shared vector | JWS/JOSE (a base64url/JSON canonicaliser in bash); HTTP Message Signatures RFC 9421 (more surface than needed); mTLS with the instance key (Ed25519 client certs need a CA and a certificate builder we do not have) |
| D5 | Token entry | manager API only (`POST …/enroll {token}`), never persisted | environment variable (readable for the life of the box — the `GXP_ADMIN_PASSWORD` lesson); a file on the volume |
| D6 | Enrollment idempotency | service-side: same key → same name, token not consumed on a re-enroll | box-side memory only (a lost DB row could not recover the name) |
| D7 | Key ↔ DB mismatch | explicit `key-mismatch` state, no certificate work, manual unenroll + enroll | silently re-enroll (needs a token anyway); overwrite the key (destroys an identity) |
| D8 | Where the ACME account key lives | on the host (`/var/lib/gxp-acme`, lego's own store) | on the volume (would put a second private key into the backup for no benefit — a new host registers a new account in seconds) |
| D9 | Who builds the keystore | the app (`KeyStore` API; it owns the passphrase); the host delivers PEM only | the host running `openssl pkcs12 -export` (root would read the app's passphrase) |
| D10 | Reload | programmatic `reload()` + `CertificateUpdatedEvent` after each change, `reload-period=1h` as the belt | restart in the update window (a restart is a kiosk staleness episode for a routine renewal); reload-period only (a manual renew would take up to a period to show) |
| D11 | Two modes on one listener | one P12, aliases `server` (default) + `public`, `quarkus.tls.key-store.sni=true` | switch entirely to the public certificate (breaks every kiosk connecting by IP); a second port (kiosks and Part D both need 443) |
| D12 | Expired public certificate | alias removed → self-signed fallback; REQ-820 adds the FQDN to the self-signed SAN at the next restart | keep serving the expired certificate (a hard browser error in every case); stop the listener (never) |
| D13 | LAN name resolution | **not solved for kiosks — no solution exists that survives an uplink outage**; kiosk path unchanged; LAN-name record for manager devices deferred | split-horizon DNS (not ours), private-IP public record (uplink-dependent, rebind-blocked), hosts entries / `--host-resolver-rules` (a per-device step of the same weight), mDNS (no public CA) |
| D14 | App ↔ host transport | the existing `gxp-updates` exchange volume, two more fixed-layout files, hostile-input + `place_file` rules reused | a new volume (one more thing to mount and document); the app calling a host socket (inbound) |
| D15 | Scheduled cadence | reconcile every 15 min at minute 5 (`0 5/15 * * * ?`, offset from the update reconcile), host timer hourly + 30 min jitter, renewal at ≤ 30 days | a file watcher (the JDK `WatchService` on an overlay/volume mount is unreliable); daily only (a manual renew would wait a day) |
| D16 | Contract file location | `api/enrollment-service.yaml` (contracts live in `api/`; the generator names its input explicitly) | `docs/backend/` (prose, not a contract) |
| D17 | Test double | one Java class in test scope with a `main` reused by the host rehearsal | a second double in bash/python for the host (two specs to keep equal) |
| D18 | Dependencies | **none** — JDK crypto/keystore/HTTP/Jackson; host `lego` (apt), `openssl`, `curl`, `docker`, `systemd`; Pebble + `challtestsrv` are dev-box rehearsal images only | acme4j (§9 heavy alternative); any HTTP/JOSE library; Testcontainers |
| D19 | Renew-now | included as Should (REQ-818): a `renewRequestedAt` line + a replay guard, `202` | left out (no remote lever for support without a site visit) |
| D20 | Audit names | `instance-enroll` / `instance-unenroll` / `certificate-renew`, entity `instance` | a single `instance` action family |

## Decisions confirmed by the user
| Decision | Chosen | Date |
|----------|--------|------|
| Instance identity, name assignment, hardware replacement | §12 item 6 (Ed25519 on the box, name from our enrollment service, restore the volume) | 2026-08-25 |
| DNS credential placement | §12 item 7 (challenge-publishing service authenticated by the instance key) | 2026-08-25 |
| Constitution v1.8.0/v1.8.1 | survival invariant, remote access separately sold | 2026-08-25/26 |
| Everything else in this feature | delegated to the planner (table above) | 2026-08-26 |
