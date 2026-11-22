# Handoff: instance-identity-and-certs

**Phase:** 04_analyze **PASS** (2026-08-26) → next `05_implement` (`dev`), start at T-01.
**Branch:** `refactor/bce-subpackages-and-boundary-security`.

## What exists
- `01-prd.md` — REQ-801…828, the honest LAN-name analysis, the prerequisites we operate.
- `02-design.md` — `de.gxp.instance` (BCE), the signed-request envelope `GXP-SIGNED-REQUEST/1`, the enrollment-service contract (to be written as `api/enrollment-service.yaml` in T-08), `backend/acme/` host layout (`lego` from apt, exec hook), exchange files, the SNI dual-certificate keystore with no-restart reload, D1–D20.
- `03-todo.md` — T-01…T-21 in dependency order.
- `04-analyze.md` — PASS.
- `docs/system-context.md` — "decided, not built" block added for this feature; `docs/features/index.md` row added.

## Facts verified at planning (2026-08-26, dev box = Debian 12 arm64)
- `~/.m2` Quarkus 3.33.3.1: `quarkus.tls.<bucket>.reload-period` and `quarkus.tls.key-store.sni` exist (`quarkus-tls-registry` config javadoc: with a P12 and SNI, "selects one alias based on the SNI hostname … do not set the `alias` property"); `io/quarkus/vertx/http/runtime/options/TlsCertificateReloader` is in `quarkus-vertx-http`.
- App image `ubi9/openjdk-21-runtime:1.24`: **no `openssl` binary** (`openssl-libs` only), `curl-minimal` — one reason the ACME client is host-side.
- `apt-cache policy lego` → candidate 4.9.1-1 (not installed yet — T-01 installs it).
- `pom.xml` names `api/openapi.yaml` explicitly as `inputSpec` (twice) → a sibling `api/enrollment-service.yaml` is not picked up.
- `AuditCoverageTest` pins 28 today; `ScheduledJobInventoryTest` pins 5 jobs; `ContractSecuredOperationsDeniedTest` 54.

## Things the implementer must not do
- Do not edit `docs/features/update-channel/**` or `de.gxp.update.*` (under review). Copy `ManifestClient`'s shape and `ExchangeFiles`' style into `de.gxp.instance.control`; the follow-up "promote both to `de.gxp.infrastructure`" is tracked in `index.md`.
- No `pom.xml` change (D18). If something seems to require one, stop and report — that is the one reserved owner gate.
- Never put the enrollment token in a field, a DTO kept beyond the request, a log, the DB or a file.

## Open follow-ups (not blocking)
- LAN-name record for manager devices (Design → "Deferred").
- Promote `EnrollmentClient`/exchange writer to shared infrastructure once the update-channel review closes.
- §8 row B "pairing trust-on-first-use window" stays open (D13).
