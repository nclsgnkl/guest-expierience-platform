# PRD: Instance Identity & Certificates
**Feature:** [index.md](../index.md) | **Design:** [02-design.md](02-design.md) | **TODO:** [03-todo.md](03-todo.md)
**Status:** In Review

> Remote-operations **Part B** (`docs/backend/remote-operations.md` §5, §11,
> §12 items 6 and 7). Planned 2026-08-26 against constitution **v1.8.1**,
> after Parts A (`auth-hardening`) and C (`update-channel`) landed. Part D
> (the relay) is next and consumes what this feature produces: the instance
> key, the assigned name, the relay endpoint returned at enrollment, and a
> publicly trusted certificate for that name.

## Problem statement

Every instance serves HTTPS with a certificate signed by its own CA
(`backend/app/src/main/docker/entrypoint-jvm.sh`). That is right for kiosks
— the CA is installed once per device at pairing — and wrong for a manager
who arrives on an arbitrary laptop or phone, which is exactly the case
Part D (remote access through the relay) creates: the relay is a dumb pipe,
TLS terminates on the box, so the box must present a certificate the
manager's browser trusts natively. A browser warning there is both a bad
experience and a training in clicking warnings away.

Two things are missing: a **stable identity and name** for an instance
(§12 item 6 — a key pair generated on the box, a name under our domain
assigned when the box enrolls), and a **publicly trusted certificate** for
that name obtained over ACME DNS-01 without any inbound port (§5, §12 item 7
— the box asks a service of ours to publish its DNS challenge, authenticated
with its instance key; no DNS credential ever sits on a box).

**One claim of §5 is re-examined here and found not to hold** (see
"The LAN-name problem" below): a public certificate does *not* remove the
kiosk CA-install step. Kiosks reach the box by its LAN address; no public
CA issues certificates for private addresses, and every name-based route to
the LAN address depends on a resolver we do not control or on the uplink
being up — which §2 forbids for the guest screens. The kiosk path is
therefore **unchanged** by this feature, deliberately.

## Goals / Non-goals

- **Goals:**
  - An Ed25519 instance key pair generated on the box at first start, kept
    on the `gxp-tls` volume, never leaving the box (§12 item 6).
  - Manager-triggered, audited, reversible **enrollment** against our
    enrollment service (`/api/manager/instance`), assigning the opaque name
    `<id>.props.<our-domain>` bound to the key.
  - For an enrolled box: a publicly trusted certificate for that name via
    **ACME DNS-01**, renewed on a timer, served **alongside** the existing
    self-signed certificate (SNI), picked up by the JVM **without a
    restart**, falling back to the self-signed certificate when it expires.
  - **Both modes coexist**, and the offline/unenrolled mode stays the
    default and stays tested: today's CA + manual kiosk step, byte-for-byte.
  - The enrollment/challenge service's **contract** as an OpenAPI file and a
    **test double** in test scope, so the box side is fully testable
    in-repo.
  - No `pom.xml` change; no binary added to the app image.
- **Non-goals:**
  - The server side of the enrollment/challenge service (built elsewhere,
    against the contract this feature writes).
  - The relay itself (Part D). This feature only *stores* the relay endpoint
    the enrollment response carries.
  - A manager screen — backend API only, like `update-channel`.
  - Any change to `/api/guest/**`, `/api/pairing/**`, device tokens, the
    kiosk app, or the kiosk CA-install step.
  - Resolving the instance name to the LAN address (the LAN-name record) —
    designed as a follow-up, deferred; see "The LAN-name problem".
  - Client certificates, certificate pinning, CT-log monitoring.

## Assumptions

- Constitution v1.8.1 is in force: principle 4's survival invariant and
  "remote access is separately sold" bullets bind every requirement here.
- The host is Debian-family (Raspberry Pi OS / Debian 12+ / Ubuntu) with
  `docker`, `openssl`, `systemd` and `unattended-upgrades` — already
  required by the update-channel host install. `lego` is packaged in
  Debian main (4.9.1 in bookworm) and Ubuntu universe.
- Let's Encrypt (or any RFC 8555 CA) is the ACME CA; the enrollment
  response names the directory URL, so the choice is ours to change without
  a release. ACME account keys are EC P-256 (Let's Encrypt does not accept
  Ed25519 account keys — the instance key is *not* the ACME account key).
- The relay's public IP is stable and `*.props.<our-domain>` resolves to it
  (§5). The box never needs to resolve its own name.
- An enrollment **token** is issued by us per purchased capability and
  entered by the manager (API), never via environment (the
  `GXP_ADMIN_PASSWORD` lesson: an env value is readable via `docker
  inspect` for the life of the box).

## User stories

- As a **manager**, I want to enter the enrollment token I was given and
  see my instance's name, so that remote access (Part D) can be set up
  without a site visit by the vendor.
- As a **manager**, I want to see whether my instance serves a publicly
  trusted certificate, when it expires and whether the last renewal
  worked, so that I can call support before something breaks.
- As a **manager**, I want to unenroll (stop paying) and lose *only* remote
  access — kiosks, content, users and audit stay exactly as they are.
- As a **guest**, I never notice any of this: the kiosk keeps working with
  or without internet, enrolled or not.
- As the **operator (us)**, I want the DNS credential to live only in our
  service, a box to be able to influence only its own name, and the
  certificate never to be the thing that takes a property offline.
- As the **developer**, I want the whole box side testable on my machine
  against a test double and a local Pebble, without our production
  infrastructure existing yet.

## The LAN-name problem (re-examining §5's claimed benefit)

§5 claimed a beneficial side effect: with a public certificate "kiosks would
trust the certificate natively", removing the manual CA install. This was
checked against how a kiosk actually reaches the box and does **not** hold:

| Route for a kiosk on the LAN | Verdict |
|---|---|
| Connect by LAN IP (today) and present the public certificate | A public CA issues no certificate for an RFC 1918 address; the certificate for `<id>.props.<domain>` does not name the IP → **hard failure**, worse than today |
| The public name resolving to the LAN IP (a per-box `A` record with a private address, the Plex pattern) | Needs the uplink for every resolution; a kiosk with a cold resolver cache during an outage cannot reach the box → **breaks the §2 row "internet down → guest screens work"**. Also blocked by DNS-rebind protection in common routers (Fritz!Box, dnsmasq default). Rejected for kiosks |
| Split-horizon DNS on the property's router | A resolver we do not control and cannot configure from the box |
| mDNS (`.local`) | No public CA issues for `.local` |
| Hosts entries / Chrome `--host-resolver-rules` on each kiosk | A per-device provisioning step of the same weight as installing the CA — replaces the step, does not remove it, and is impossible on locked-down tablets |
| Browser connects by IP with the name in SNI/Host | Not a capability of any kiosk browser |

**Result, recorded as binding:** the kiosk path keeps the instance CA and the
manual install; `GET /api/pairing/ca` keeps its meaning in both modes; the
public certificate exists for the **name only** — which is what Part D
(the relay) needs, and nothing else in v1. A manager on the LAN keeps using
the LAN address with the instance CA (or the click-through) exactly as
today. The LAN-name record for the *manager's own devices when the uplink is
up* is a designed follow-up (Design → "Deferred: the LAN-name record"), not
part of this feature. §8's row for B ("the pairing trust-on-first-use
window") is therefore **not** resolved by this feature and is recorded as
still open.

## Requirements

| REQ-ID | Requirement | Priority |
|--------|-------------|----------|
| **Identity** | | |
| REQ-801 | On first start the application generates an **Ed25519 key pair** with JDK `KeyPairGenerator("Ed25519")` (no dependency) and stores it on the `gxp-tls` volume: the private key as an unencrypted PKCS#8 PEM (`instance-key.pem`, mode 0600, owned by the app user) and the public key as an SPKI PEM (`instance-key.pub`, 0644). Restarts reuse it. The private key never enters the database, a log, an API response, an exchange file or a request; only the public half ever leaves the volume | Must |
| REQ-802 | A **missing** key file means "generate" (first start, or a deliberate reset); a **present but unreadable/unparseable** key file refuses startup with a message naming the file and the recovery (delete it → new identity → re-enroll under a new name). The application never silently overwrites an existing key | Must |
| REQ-803 | The identity is exposed to the manager as the public key (base64 SPKI DER) and its **fingerprint** (lower-case hex SHA-256 over the SPKI DER — the update-channel key fingerprint convention). Nothing else about the key is exposed | Must |
| REQ-804 | **Survival invariant.** An instance that never enrolls — or whose enrollment base URL is unset — has a key and no name and is fully functional: no existing endpoint, job, startup step or kiosk behaviour depends on the identity, enrollment or the public certificate. `%dev`/`%test` keep the key under a `target/` directory (`gxp.instance.tls-dir`), never `/tls` | Must |
| **Enrollment** | | |
| REQ-805 | `GXP_ENROLLMENT_BASE_URL` (compose, optional) is the `https://` base of our enrollment service. Unset → enrollment state `not-configured`, **no request is ever made**. Plain `http://` is refused outside `%test` (the `gxp.update.allow-plain-http` precedent) | Must |
| REQ-806 | Every request from a box to the enrollment service is **authenticated by the instance key** with one **signed-request envelope** (Design §3): the request method, path, an RFC 3339 UTC timestamp, a random nonce and the SHA-256 of the body are signed with Ed25519; the public key, timestamp, nonce and signature travel as `GXP-Instance-*` headers. The envelope is defined once, implemented twice (JDK for the application, `openssl` for the host hook) and both implementations are pinned to the **same committed test vector** (Ed25519 is deterministic, so the expected signature is a fixed string) | Must |
| REQ-807 | `POST /api/manager/instance/enroll` `{token}` (admin) sends the signed `POST <base>/enroll` carrying the token; on success persists name, FQDN, ACME directory URL, challenge URL, relay endpoint and the key fingerprint (Flyway **V10**) and answers the status. The **token is never persisted and never logged**; it exists only for the duration of the request. Outcomes: `409 enrollment-not-configured`, `409 already-enrolled`, `400 enrollment-rejected` (the service refused the token or key), `503 enrollment-unavailable` (unreachable, timeout, non-2xx, over-size or unparseable response). Every value in the response is validated (Design §3: name/FQDN regex, `https://` URLs, relay host/port) before anything is stored; a failed validation is `503 enrollment-unavailable` and stores nothing. The HTTP call never runs inside a database transaction | Must |
| REQ-808 | Enrollment is **idempotent per key** on the service side (the contract says so): re-enrolling the same key returns the same name. On the box, a stored enrollment whose `key_fingerprint` differs from the key on the volume (a restored volume, a reset key) is state `key-mismatch`: no challenge file is written, the public certificate is not renewed, the status names the condition, and the manager resolves it by unenrolling and enrolling again | Must |
| REQ-809 | `POST /api/manager/instance/unenroll` (admin) clears the stored enrollment, removes the public certificate from the served keystore (the box serves the self-signed certificate only, without a restart), removes the host-facing instance file, and sends a **best-effort** signed `POST <base>/unenroll` (an unreachable service does not prevent unenrollment; the outcome is recorded). **Nothing else changes**: devices, users, content, audit, update settings and the instance key are untouched, and the box can enroll again later | Must |
| REQ-810 | Every mutation is **audited** through `AuditControl` (`instance-enroll`, `instance-unenroll`, `certificate-renew`; entity `instance`, id = the name or `null`), without the token, the key or any URL. `AuditCoverageTest`'s pinned set grows by exactly these operations | Must |
| REQ-811 | `GET /api/manager/instance` (admin) reports identity (REQ-803), enrollment (`state`: `not-configured` / `unenrolled` / `enrolled` / `key-mismatch`, name, FQDN, enrolled-at, last outcome + at), and certificate (`mode`: `self-signed` / `public`, the names the self-signed certificate carries, the public certificate's `notAfter` if present, and the host ACME component's last reported state: outcome, at, message code) | Must |
| REQ-812 | The enrollment/challenge service's contract is written as **`api/enrollment-service.yaml`** (OpenAPI 3.1, not consumed by the build) — `POST /enroll`, `POST /unenroll`, `PUT /challenges/{fqdn}`, `DELETE /challenges/{fqdn}`, the envelope as a security scheme — and a **test double** `EnrollmentServiceDouble` (test scope, JDK `HttpServer`) implements it: verifies the envelope (signature, timestamp window, nonce replay), issues names, binds keys, stores challenges, and can forward a challenge to a Pebble `challtestsrv` when told to. It is also runnable as a `main` for the host rehearsal | Must |
| **Certificates** | | |
| REQ-813 | The ACME client is **`lego`, installed from the host distribution's package** (`apt install lego`), run by a host-side timer beside the updater: `backend/acme/` holds `gxp-acme.sh`, the exec hook `gxp-acme-hook.sh`, `gxp-acme.service`/`.timer`, `acme.conf.example`, `install.sh` (refuses when `lego` is absent and prints the install line) and `test/`. `backend/CLAUDE.md`'s host-script rules apply verbatim (`set -euo pipefail`, `LC_ALL=C`, no `eval`, regex-validate every value before a command line). **Nothing enters `pom.xml`; nothing is added to the app image** | Must |
| REQ-814 | DNS-01 is solved through lego's `exec` provider: the hook publishes `_acme-challenge.<fqdn>` by `PUT <challengeUrl>/challenges/<fqdn>` with the envelope (REQ-806, signed with the instance key read from the `gxp-tls` volume) and removes it with `DELETE` on cleanup. The hook can only ever name the box's own FQDN — anything else is refused locally by regex before the request and by the service by key binding | Must |
| REQ-815 | App ↔ host exchange uses the existing `gxp-updates` volume with two new **fixed-layout** files: `instance.json` (app → host: `fqdn`, `acmeDirectoryUrl`, `challengeUrl`, `renewRequestedAt`) and `acme-state.json` (host → app: `outcome`, `lastAttemptAt`, `notAfter`, `message`). The host treats `instance.json` as **hostile input** (snapshot once; symlink/non-regular/> 64 KiB refused; every value regex-checked) and **never opens a path inside the app-owned directory for writing** (the update-channel A-3 rule: stage in the root-owned parent, `rename(2)` into place) | Must |
| REQ-816 | On success the host places the **full chain** and the private key (converted to PKCS#8 — `openssl pkey`) into the `gxp-tls` volume as `public.crt` / `public.key`, owned by the app user, 0600, by staged rename. The ACME **account** key stays on the host under `/var/lib/gxp-acme`; the hardware-replacement story is unchanged (restore the `gxp-tls` volume; a new host simply registers a new ACME account) | Must |
| REQ-817 | A scheduled job (`InstanceScheduler#reconcileServedCertificate`, every 15 min, `SKIP`) reconciles the served keystore: a `public.crt`/`public.key` pair that is valid (chain parses, key matches, `notAfter` in the future) and differs from the served one is merged into `server.p12` under alias `public` (temp file + atomic move, same passphrase); an **expired or absent** pair removes the alias. After every change the TLS configuration is **reloaded without a restart** (`TlsConfiguration.reload()` + `CertificateUpdatedEvent`, and `quarkus.tls.reload-period` as the safety net). The HTTPS listener is never stopped; the self-signed certificate is always the fallback | Must |
| REQ-818 | Renewal: the host timer runs hourly with jitter; `lego renew` renews at ≤ 30 days before expiry and is a local no-op otherwise. `POST /api/manager/instance/certificate/renew` (admin, `202`) sets `renewRequestedAt`, which the host honours once (replay guard) regardless of the threshold; `409 not-enrolled` otherwise | Should |
| REQ-819 | **Two modes coexist on one listener.** The self-signed server certificate (SAN = `GXP_TLS_HOST`) is always served — for a connection by IP, with no SNI, or with an SNI that matches no other alias. The public certificate is served only when the SNI equals the FQDN (`quarkus.tls.key-store.sni=true` — verified against 3.33.3.1). Kiosks paired before, during or after enrollment keep working; **`GET /api/pairing/ca` is unchanged in both modes** and stays the documented kiosk step | Must |
| REQ-820 | The self-signed server certificate gains the instance name as a **second SAN** on the next restart after enrollment (the entrypoint re-issues under the unchanged CA, the existing host-change path), and loses it on the next restart after unenrollment — so a device that trusts the instance CA also accepts the fallback under the name. Recorded as a restart-time effect, not immediate | Should |
| REQ-821 | **Availability.** An unreachable enrollment service, challenge endpoint, ACME CA or DNS costs exactly "no new enrollment / no new certificate": the outcome is recorded in state, the next tick is the retry, the served certificate stays as it is until it expires and then falls back to the self-signed one (REQ-817). No other function — guest API, LAN manager UI, update channel — ever waits on, retries for or degrades because of any of these. Every outbound call follows the `ManifestClient` convention (connect 5 s, request 10 s, 15 s budget, 64 KiB cap, no redirects, no retry, `https://` only) | Must |
| REQ-822 | **Logging.** Neither side ever logs the token, key material, an envelope header, a URL, a request/response body or an exception message — outcome codes and exception class names only, counts only on success (constitution principle 3 as `backend/CLAUDE.md` spells it for jobs and host scripts) | Must |
| REQ-823 | The new job is pinned in `ScheduledJobInventoryTest` (inventory 5 → 6); the job lives in `de.gxp.infrastructure.scheduler`, holds one delegating call, and never fires in the suite | Must |
| REQ-824 | **Testable in-repo.** JVM: the enrollment flow, envelope, validation, V10 persistence, keystore merge/fallback, exchange files and the API run against `EnrollmentServiceDouble` on loopback plain HTTP (`%test` only) and committed test-only certificate fixtures. Host: `backend/acme/test/hook-sign.sh` (the envelope against the shared vector), `parse.sh` (the exchange readers, no Docker), and `rehearse-pebble.sh` — a throwaway compose project with Pebble + `challtestsrv` (Docker images, not dependencies), the double forwarding challenges, and a real `lego` run through `gxp-acme.sh` producing `public.crt` for a test FQDN. The last one is a developer-box rehearsal, run after every change under `backend/acme/**` | Must |
| REQ-825 | Documentation: `backend/README.md` gains "Instance identity and enrollment" and "Public certificate (enrolled instances)" incl. the install steps, the **infrastructure we operate** (below), the honest LAN statement, and the recovery paths; `backend/compose.yaml` comments and `docs/system-context.md` are updated | Must |
| REQ-826 | **Config validation at every boundary.** Name `^[a-z0-9]{4,32}$`; FQDN `^[a-z0-9]{4,32}(\.[a-z0-9-]{1,63}){2,6}$` and starts with the name; URLs `^https://[a-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~/-]*)?$`; relay host a hostname, port 1..65535; token `^[A-Za-z0-9._-]{16,128}$`; the same regexes on the host side. A value failing validation is never stored, never written to a file and never reaches a command line | Must |
| REQ-827 | **Trust roots.** Outbound TLS from the app uses the image JDK's CA bundle and from the host `lego`/`curl` the host's bundle (the update-channel A-8 policy): the enrollment service and the ACME CA must chain to a root present in every deployed image and host (ISRG Root X1 is). No custom truststore in `%prod`; the test profile may trust the Pebble CA for the host rehearsal only | Must |
| REQ-828 | Infrastructure **we** must operate is recorded as prerequisites outside this repo (below), and the feature is complete without any of it existing — everything in it is exercised against the double and Pebble | Must |

## Acceptance criteria (testable)

- REQ-801: Given an empty `gxp.instance.tls-dir`, when the app starts, then `instance-key.pem` (0600) and `instance-key.pub` (0644) exist, parse as Ed25519, and a second start loads the same public key; `LogSweep` finds no key bytes in any log.
- REQ-802: Given `instance-key.pem` containing garbage, when the app starts, then startup fails with a message naming the file and the recovery; given the file absent, startup generates a new pair.
- REQ-803: `GET /api/manager/instance` → `identity.fingerprint` equals `sha256(SPKI DER)` computed in the test from the `.pub` file; `identity.publicKey` decodes to that SPKI.
- REQ-804: With `GXP_ENROLLMENT_BASE_URL` unset, the whole existing suite is green, `/api/manager/instance` answers `enrollment.state = not-configured`, and no outbound request is attempted (the double is not started; the job runs and does nothing).
- REQ-805: Base URL `http://…` in a non-test profile → state `not-configured`-equivalent refusal `insecure-base-url`, no request.
- REQ-806: The JVM signer over the committed vector produces the committed signature; `hook-sign.sh` over the same vector produces the same bytes; the double rejects a tampered body, a stale timestamp (> 300 s) and a replayed nonce.
- REQ-807: Enroll with the double's valid token → 200, V10 row with name/FQDN/URLs/relay/fingerprint, audit row `instance-enroll`; wrong token → 400 and no row; double down → 503 and no row; response with `http://` challenge URL → 503 and no row; a second enroll → 409; the token literal is absent from the database, logs and every file under `target/`.
- REQ-808: After enrolling, replacing the key files with another pair and restarting → state `key-mismatch`, no `instance.json`; unenroll + enroll → `enrolled`.
- REQ-809: Unenroll → row cleared, `instance.json` gone, keystore has no `public` alias, the double received the signed unenroll; with the double down → unenroll still succeeds locally with `lastOutcome = service-unreachable`; devices/users/audit counts unchanged.
- REQ-810: `AuditCoverageTest` pinned set includes `enroll`, `unenroll`, `requestRenewal`; audit rows carry no token/URL.
- REQ-811: Status shape asserted for all four states.
- REQ-812: `api/enrollment-service.yaml` parses; the double's behaviour matches its documented responses (a contract test per operation).
- REQ-813/814: `install.sh` on a host without `lego` exits 2 naming `apt-get install lego`; `hook-sign.sh` and `parse.sh` green; the hook refuses an FQDN that is not the file's own.
- REQ-815: A symlinked, oversized or malformed `instance.json` yields outcome `malformed` and no `lego` invocation; the hostile-input tests mirror the updater's.
- REQ-816/824: `rehearse-pebble.sh` ends with `public.crt` in the rehearsal TLS dir carrying the test FQDN, owned by uid 185, 0600, and `acme-state.json` outcome `issued`.
- REQ-817: JVM test with the committed fixtures: valid pair → `server.p12` gains alias `public`; expired pair → alias removed; mismatched key/cert → nothing changes, outcome `material-invalid`; the reload hook is invoked once per change. Compose smoke (manual, recorded in the handoff): `curl --resolve <fqdn>:443:<ip>` shows the public chain, `curl https://<ip>` shows the self-signed one.
- REQ-818: Renew → 202, `instance.json.renewRequestedAt` set; a second run of `gxp-acme.sh` with the same `renewRequestedAt` does not re-run lego (replay guard); not enrolled → 409.
- REQ-819: The existing TLS smoke by IP is unchanged; `getPairingCa` tests unchanged and green.
- REQ-820: Entrypoint unit check: with `/tls/instance-name` present the issued SAN list is `ip/dns:<host>, dns:<name>`; without it, the host only; a change re-issues under the same CA (CA fingerprint constant).
- REQ-821: With the double stopped, the reconcile job and a renew request record outcomes and every other test stays green; no retry loop (exactly one request per tick, asserted by the double's counter).
- REQ-822: `LogSweep` over the enroll/renew/reconcile paths finds no token, URL, key or message text.
- REQ-823: `ScheduledJobInventoryTest` pins `InstanceScheduler#reconcileServedCertificate cron="0 5/15 * * * ?"`.
- REQ-825–828: reviewed by reading; REQ-826 has a regex table test (accepted and rejected samples for every field, both sides against the same fixture file).

## Technical constraints

- Constitution v1.8.1: BCE sub-packages, one-line Boundaries, `@Transactional` on Controls, Controls speak generated DTOs, contract-first (`api/openapi.yaml`, tag `manager-instance` → `InstanceResource`), Flyway SQL only, closed dependency allow-list (**no `pom.xml` change** — verified feasible: JDK Ed25519, `KeyStore`, `CertificateFactory`, Jackson, `HttpClient`; `quarkus-tls-registry` is already on the classpath transitively), `quarkus-scheduler` core cron only, five architecture guards + `AuditCoverageTest` + `ContractSecuredOperationsDeniedTest` stay green.
- Principle 4: outbound only; nothing connects to the box; the survival invariant is REQ-804/821; the application never branches on where a request came from (this feature adds no request-origin logic at all).
- Principle 3 / `backend/CLAUDE.md` logging rules (REQ-822).
- Host scripts: `backend/CLAUDE.md` → "Non-Java pieces of the backend" (REQ-813).
- Kiosk/guest/pairing surfaces untouched (§1 out of scope) — checkable on the contract diff: only `manager-instance` operations are added.

## Infrastructure we must operate (prerequisites outside this repo)

| Prerequisite | Why | Note |
|---|---|---|
| A domain we control (`<our-domain>`) and the zone `props.<our-domain>` | Names and certificates | Opaque names because of CT logs (§5) |
| `*.props.<our-domain>  A  <relay-ip>` and `relay.<our-domain>  A  <relay-ip>` | §5 — one wildcard, forever; boxes dial `relay.…` (Part D) | No per-box DNS work at enrollment |
| A DNS provider API credential, held **only** by the enrollment/challenge service | Writes `_acme-challenge.<fqdn>` TXT records | Never on a box (§12 item 7) |
| The **enrollment/challenge service** implementing `api/enrollment-service.yaml`: token issuance/redemption, key → name binding, idempotent re-enroll, name revocation, challenge publish/cleanup restricted to the bound name, envelope verification with replay protection | REQ-807/812/814 | Built elsewhere; the double here is its executable spec |
| An ACME CA account policy (Let's Encrypt production for real boxes, staging for our own tests), a contact e-mail, and awareness of rate limits (50 certificates per registered domain per week — `props.<our-domain>` counts as one; a fleet above that needs a rate-limit adjustment request or a per-customer sub-zone) | REQ-813/818 | Recorded, not solved here |
| A per-box `GXP_ENROLLMENT_BASE_URL` (or a default baked into a later release) and the tokens handed to customers | REQ-805/807 | Until it exists, every box is `not-configured` and fully functional |

## Risks & open questions

- **DNS-01 propagation timing.** lego waits for the TXT record on the zone's authoritative servers before asking the CA to validate; a slow DNS provider means a slow (not failed) issuance. `EXEC_PROPAGATION_TIMEOUT` / `EXEC_POLLING_INTERVAL` are set in `acme.conf`.
- **Vert.x SNI default alias.** With SNI enabled and a P12, the certificate for a no-SNI/IP connection is the keystore's default entry. The design pins alias order (`server` first) and the compose smoke verifies it; if Vert.x picks otherwise, the fallback is two separate keystores under two TLS buckets — recorded in Design "Risks".
- **lego packaging drift.** Debian's `lego` is old (4.9); the `exec` provider's `RAW` mode and `renew --days` have been stable since 3.x. `install.sh` prints the installed version; the dev verifies the flags against `lego --help` at implementation.
- **Rate limits and name reuse.** A box that re-enrolls under a new name after a lost key leaves the old name revoked on our side; certificates for it expire naturally. Nothing on the box needs the old name.
- **Two copies of two small helpers** (`OutboundClient` and the fixed-layout exchange writer) exist after this feature because `update-channel`'s reviewer is working in that package at the time of planning. Tracked follow-up: promote both to `de.gxp.infrastructure` once that review closes.
- No `[NEEDS CLARIFICATION]` markers remain: every approval-gated choice was delegated by the owner and is recorded in Design → "Decisions taken under delegated authority". The single stop condition (a required `pom.xml` dependency) did not occur.
