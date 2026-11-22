# TODO: Instance Identity & Certificates
**Feature:** [index.md](../index.md) | **PRD:** [01-prd.md](01-prd.md) | **Design:** [02-design.md](02-design.md)
**Status:** Not Started

> Each task traces to a REQ-ID and references the Design (D-numbers, §). Do
> not re-decide. **Environment:** branch
> `refactor/bce-subpackages-and-boundary-security`; the full build is
> `JAVA_HOME=/tmp/gxp-jdk/jdk-21.0.12+8 sg docker -c "cd <repo>/backend/app && ./mvnw -q verify"`
> (~25 min — run it in the background, and run single test classes with
> `-Dtest=…` while iterating); `docker` needs `sg docker -c "…"` on this
> box. **Concurrency:** `docs/features/update-channel/**` and the
> `de.gxp.update` package are under review by another agent — do not edit
> them; the two helpers this feature needs are **copied** into
> `de.gxp.instance.control` (Design §1, PRD risks), not shared.
> Dependency order top to bottom; T-01…T-06 are pure JVM and need no host
> tooling.

## Setup
- [ ] **T-01** Verify the platform facts the design leans on and record them in `handoff.md` — _REQ-817, REQ-819, REQ-813_
  - `TlsConfigurationRegistry`/`TlsConfiguration.reload()`/`CertificateUpdatedEvent` resolvable from `backend/app` without a `pom.xml` change (`./mvnw -q dependency:tree | grep tls-registry`); `quarkus.tls.key-store.sni` and `quarkus.tls.reload-period` in the 3.33.3.1 config model (already grepped in `~/.m2` at planning — re-confirm); `sudo apt-get install lego` on the dev box, then `lego --version`, `lego dnshelp --code exec` (confirm `EXEC_MODE=RAW` argument order `present <fqdn> <value>`), `lego renew --help` (`--days`, forced-run flag name).
  - Files: `docs/features/instance-identity-and-certs/handoff.md`
  - Validate: the four facts written down with the exact strings seen. If `EXEC_MODE=RAW` behaves differently, adjust `gxp-acme-hook.sh`'s argument parsing in T-13 — the contract with our service is unchanged.
- [ ] **T-02** Generate the committed test-only fixtures — _REQ-806, REQ-817, REQ-824_
  - `backend/acme/test/fixtures/envelope/`: a throwaway Ed25519 pair (`openssl genpkey -algorithm ed25519`), `instance-key.pub`, `body.json`, `canonical.txt` (the exact Design §3 string for `PUT /challenges/test.props.example.test` at a fixed timestamp/nonce), `signature.b64` produced by openssl, `headers.txt`. `backend/acme/test/fixtures/validation/{accept,reject}.txt` (one `field value` per line). `backend/app/src/test/resources/instance/`: `valid-chain.pem` + `valid-key.pem` (EC P-256 self-issued, SAN = `test.props.example.test`, 10 y), `expired-chain.pem` + `expired-key.pem`, `other-key.pem` (mismatch) — generated with `openssl req -x509` + `openssl pkey`, header comment "TEST MATERIAL — never deploy".
  - Validate: `openssl pkeyutl -verify -pubin -inkey instance-key.pub -rawin -in canonical.txt -sigfile <(base64 -d signature.b64)` → `Signature Verified Successfully`; `openssl x509 -in valid-chain.pem -noout -ext subjectAltName` shows the FQDN.

## Implementation — application
- [ ] **T-03** Contract: tag `manager-instance`, the four operations, `InstanceStatusDto`, `EnrollInstanceRequest`, error codes (Design → API contract); regenerate — _REQ-807, REQ-809, REQ-811, REQ-818_
  - Files: `api/openapi.yaml`, `packages/api-client/` (generated), `ContractSecuredOperationsDeniedTest` pin 54 → 58
  - Validate: `./mvnw -q generate-sources` compiles the interfaces; the guest/pairing sections of the contract are byte-identical (`git diff` shows only the new tag/paths/schemas).
- [ ] **T-04** `V10__instance_enrollment.sql` + `entity/InstanceEnrollment`, `InstanceEnrollmentMapper`, `control/InstanceEnrollmentRepository`, `InstanceStateStore` — _REQ-807, REQ-808, REQ-809_
  - Files: `backend/app/src/main/resources/db/migration/V10__instance_enrollment.sql`, `backend/app/src/main/java/de/gxp/instance/{entity,control}/…`
  - Validate: `-Dtest=InstanceStateStoreTest` (load/save/clear/outcomes; the CHECK constraints refuse a half row).
- [ ] **T-05** `InstanceIdentity`: generate/load/refuse, PEM writer, fingerprint, `sign()`; config `gxp.instance.tls-dir` (`/tls` in `%prod`, `target/instance-tls` in `%dev`/`%test`) — _REQ-801, REQ-802, REQ-803, REQ-804_
  - Files: `de/gxp/instance/control/InstanceIdentity.java`, `application.properties`
  - Validate: `-Dtest=InstanceIdentityTest` — fresh dir → files with modes 0600/0644, reload same key, garbage file → `IllegalStateException` naming the path and the recovery; `LogSweep` clean.
- [ ] **T-06** `SignedRequest` + `InstanceValidation` + `EnrollmentResponseParser` — _REQ-806, REQ-826_
  - Files: `de/gxp/instance/control/{SignedRequest,InstanceValidation,EnrollmentResponse,EnrollmentResponseParser}.java`
  - Validate: `-Dtest=SignedRequestTest` reproduces `fixtures/envelope/signature.b64` byte-for-byte from the fixture key; `-Dtest=InstanceValidationTest` walks `fixtures/validation/{accept,reject}.txt`; parser rejects `http://` URLs, a bad FQDN, a port 0.
- [ ] **T-07** `EnrollmentClient` (copy of the `ManifestClient` shape + POST/PUT/DELETE with body + envelope headers; `User-Agent: gxp-instance/1`; `gxp.instance.base-url`, `gxp.instance.allow-plain-http` `%test`-only) — _REQ-805, REQ-806, REQ-821, REQ-822, REQ-827_
  - Files: `de/gxp/instance/control/EnrollmentClient.java`, `application.properties`, `backend/compose.yaml` (`GXP_ENROLLMENT_BASE_URL`)
  - Validate: `-Dtest=EnrollmentClientTest` against a loopback JDK `HttpServer`: headers present and verifiable, 64 KiB cap, 15 s budget, non-2xx → outcome, no redirect followed, class-name-only failure.
- [ ] **T-08** `EnrollmentServiceDouble` (test scope) implementing `api/enrollment-service.yaml` §4: envelope verification (signature, ±300 s, nonce replay), tokens, key → name binding (idempotent), revoked names, challenge store, optional `challtestsrv` forwarding, per-operation counters, `main(--port --token --challtestsrv)` — _REQ-812, REQ-824_
  - Files: `backend/app/src/test/java/de/gxp/infrastructure/testsupport/EnrollmentServiceDouble.java`, `api/enrollment-service.yaml`
  - Validate: `-Dtest=EnrollmentServiceDoubleContractTest` — one case per documented response row of the YAML (the double is the executable spec).
- [ ] **T-09** `InstanceExchangeFiles` (`instance.json` writer/deleter, `acme-state.json` tolerant reader, Design §6 layouts) — _REQ-815_
  - Files: `de/gxp/instance/control/InstanceExchangeFiles.java`, `backend/acme/test/fixtures/exchange/{instance.json,acme-state.json,acme-state-unknown-keys.json}` (shared with `parse.sh` in T-14)
  - Validate: `-Dtest=InstanceExchangeFilesTest` — byte-identical output to the fixture, atomic replace, missing/unparseable state → empty + class-name log.
- [ ] **T-10** `CertificateReconciler`: read material, validate (chain, key match, `notAfter`, SAN), merge/remove alias `public` in `server.p12` (temp + atomic move), reload + event; `%prod` config `quarkus.tls.key-store.sni=true`, `quarkus.tls.reload-period=1h` — _REQ-817, REQ-819, REQ-821_
  - Files: `de/gxp/instance/control/CertificateReconciler.java`, `application.properties`
  - Validate: `-Dtest=CertificateReconcilerTest` on a keytool-made temp `server.p12` (test creates it with the JDK `KeyStore` API from `valid-*.pem`… — no keytool needed): valid → alias added, alias order `server` first; expired → removed; mismatched key → unchanged + `material-invalid`; reload hook counted once per change.
- [ ] **T-11** `InstanceControl` (status/enroll/unenroll/requestRenewal/reconcile per Design §1 sequences), `InstanceResource`, audit constants, `AuditCoverageTest` 28 → 31 (+ `EXPLICIT_ENTRIES` for `enroll`, `unenroll`), `/tls/instance-name` write/delete — _REQ-804, REQ-807, REQ-808, REQ-809, REQ-810, REQ-811, REQ-818, REQ-820, REQ-821, REQ-822_
  - Files: `de/gxp/instance/{boundary/InstanceResource,control/InstanceControl}.java`, `de/gxp/audit/control/AuditControl.java`, `AuditCoverageTest`
  - Validate: `-Dtest=InstanceResourceTest` with the double: every acceptance line of REQ-807/808/809/811/818/821 in the PRD; token literal grep-absent from `target/` logs and the DB; one request per operation (double counters); `BoundaryAnnotationAuditTest`, all five architecture guards green.
- [ ] **T-12** `InstanceScheduler#reconcileServedCertificate` (`0 5/15 * * * ?`, `SKIP`) + `ScheduledJobInventoryTest` pin (5 → 6) — _REQ-823_
  - Files: `de/gxp/infrastructure/scheduler/InstanceScheduler.java`, `de/gxp/architecture/ScheduledJobInventoryTest.java`
  - Validate: `-Dtest=ScheduledJobInventoryTest`; the job never fires in the suite (`halted`).

## Implementation — host (`backend/acme/`)
- [ ] **T-13** `gxp-acme-hook.sh` (envelope in bash, `present`/`cleanup`, own-FQDN guard, curl PUT/DELETE) + `test/hook-sign.sh` — _REQ-806, REQ-814, REQ-822, REQ-826_
  - Files: `backend/acme/gxp-acme-hook.sh`, `backend/acme/test/hook-sign.sh`
  - Validate: `bash backend/acme/test/hook-sign.sh` → signature byte-equal to the fixture; a foreign record name exits 1 before any request; a mock endpoint (the double from T-08 via `main`) receives a verifiable envelope.
- [ ] **T-14** `gxp-acme.sh` (Design §5 control flow, `snapshot_file`/`place_file` semantics copied from `gxp-updater.sh`, regex table, shadow + mirror state, `lego run|renew`, PKCS#8 conversion, SAN check) + `test/parse.sh` + `test/validation.sh` — _REQ-813, REQ-815, REQ-816, REQ-818, REQ-821, REQ-822, REQ-826_
  - Files: `backend/acme/gxp-acme.sh`, `backend/acme/test/{parse.sh,validation.sh}`
  - Validate: `bash backend/acme/test/parse.sh` (fixtures from T-09; symlink/oversize/malformed → `malformed`, no lego call — lego stubbed by `PATH`); `bash backend/acme/test/validation.sh`; `shellcheck` clean if installed.
- [ ] **T-15** Units, conf, `install.sh` (+ `--uninstall`), README stub — _REQ-813_
  - Files: `backend/acme/{gxp-acme.service,gxp-acme.timer,acme.conf.example,install.sh}`
  - Validate: `install.sh` without `lego` on `PATH` exits 2 naming `apt-get install lego`; `systemd-analyze verify backend/acme/gxp-acme.service`; a real install + uninstall on the dev box (throwaway conf), journal shows fixed strings only.
- [ ] **T-16** Entrypoint: SAN list from `GXP_TLS_HOST` + optional `/tls/instance-name` (regex-validated), `tls-sans` comparison triggers the re-issue under the unchanged CA — _REQ-820, REQ-819_
  - Files: `backend/app/src/main/docker/entrypoint-jvm.sh`
  - Validate: shell test `backend/app/src/main/docker/test-entrypoint.sh`-style run with a temp `GXP_TLS_DIR` and a stub `run-java.sh`: first start → host SAN; add `instance-name` → re-issue with two SANs and identical CA fingerprint; remove → host only; an invalid name file → refused, old material kept.
- [ ] **T-17** `test/rehearse-pebble.sh` — Pebble + challtestsrv compose project, the double's `main` with `--challtestsrv`, throwaway key/exchange/TLS dirs, `gxp-acme.sh` with the rehearsal overrides → `issued`, `not-due`, forced `renewed` — _REQ-816, REQ-824_
  - Files: `backend/acme/test/rehearse-pebble.sh`, `backend/acme/test/pebble/{compose.yaml,pebble-config.json}`
  - Validate: `sg docker -c "sudo -E backend/acme/test/rehearse-pebble.sh"` ends green; `public.crt` SAN = the test FQDN, uid 185, 0600; log in `handoff.md`.

## Tests (integration of the two halves)
- [ ] **T-18** Compose smoke on the dev box (manual, recorded): enrolled state via the double (`GXP_ENROLLMENT_BASE_URL` → the double over a loopback `https://`? no — `%prod` refuses plain HTTP: instead copy the rehearsal's `public.crt`/`public.key` into the real `gxp-tls` volume and set `instance-name` by hand), then `curl -k https://<ip>/api/system/health` → self-signed subject; `curl --cacert <pebble-root> --resolve <fqdn>:443:<ip> https://<fqdn>/api/system/health` → the Pebble chain; delete `public.*` → after ≤ 15 min (or a restart) the name falls back to the self-signed certificate — _REQ-817, REQ-819, REQ-821_
  - Validate: both `openssl s_client -servername` outputs pasted into `handoff.md`; alias order confirmed (or the two-bucket fallback triggered — Design §2).
- [ ] **T-19** `LogSweep` over enroll/unenroll/renew/reconcile paths; the full existing suite with `GXP_ENROLLMENT_BASE_URL` unset — _REQ-804, REQ-822_
  - Validate: background full `./mvnw -q verify` green; counts recorded.

## Cleanup
- [ ] **T-20** Docs: `backend/README.md` ("Instance identity and enrollment", "Public certificate (enrolled instances)", the honest LAN statement, the prerequisites-we-operate table, recovery: lost key, `key-mismatch`, `rollback` of an unenroll), `backend/compose.yaml` comments (four exchange files, new env var), `docs/system-context.md` (component rows, sixth job, the cross-cutting bullet) — _REQ-825, REQ-826, REQ-828_
  - Validate: read-through; every env var in compose appears in the README table.
- [ ] **T-21** Handoff + registry: `handoff.md` (facts from T-01, rehearsal logs, deviations), `docs/features/index.md` row → `06_review` — _REQ-828_
  - Validate: `04-analyze.md` coverage still holds against the implemented set (no new orphan tasks).
