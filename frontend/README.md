# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.1.4.

Working conventions live in `CLAUDE.md` (binding); design specs under
`docs/design/`.

## Development server (with the real backend)

Since `guest-frontend-integration` (2026-08-20) the app renders **entirely
from the guest API** — there is no static content fallback. The dev loop is
two processes:

```bash
# 1. the backend — Dev Services provisions PostgreSQL (Docker required)
cd backend/app && ./mvnw quarkus:dev

# 2. the frontend — proxies /api to localhost:8080 (proxy.conf.json)
cd frontend && ng serve
```

`http://localhost:4200/` then shows a **paired, occupied kiosk with the
example content, no manual pairing**: the `%dev` backend seeds a device for
the public dev-only token `gxp-dev-device-token`
(`backend/README.md` → "The dev seed"), and the development build's
`environment.development.ts` supplies the same literal as a fallback whenever
`localStorage` holds no token. A production build contains no token
(`fileReplacements`; verifiable via `grep -r gxp-dev-device-token dist/`).

Useful dev states:
- **Pairing screen:** revoke the seeded device via the manager API (or set an
  invalid `gxp.device-token` in localStorage) — the next poll's 401 wipes the
  kiosk to `/pairing`; confirm the shown code via
  `POST /api/manager/pairings/{code}/confirm`.
- **Standby:** delete the active stay via `DELETE /api/manager/stays/{id}`.
- **Staleness pill / connection screen:** stop the backend; with a cache the
  kiosk renders stale, without one it shows the connection screen — both heal
  unaided when the backend returns.

**CORS is never enabled on the backend** — same-origin in production (the
app ships inside the backend jar, served at `/`; see `backend/README.md` →
"Serving the guest app"), the proxy in development.

## Measurement instruments (review-time)

```bash
node tools/measure.mjs http://localhost:4200          # the standard route grid
GXP_SMOKE_ADMIN_PW=… node tools/measure-states.mjs    # pairing/standby/connection + pill
```

Prefix the grid with `GXP_MEASURE_DEVICE_TOKEN=gxp-dev-device-token` to make its
preference-write guard live. Without the variable no **device token** is written
to `localStorage`, so that guard short-circuits and confirms nothing — the run
says so once, as an `INSTRUMENT` finding. The app itself renders identically
either way; what differs is the *measurements*, and only when a slow preference
write races the reload: measured on this box with a 2 s delay on
`PUT /api/guest/preferences`, an unseeded run mislabelled an entire axis
(14 × `PREFERENCE NOT APPLIED`, plus 8 contrast findings inherited from it) where
the seeded run reported none. That race is exactly what the guard exists to
catch, so run the grid seeded whenever its numbers are going to be quoted.

Both report and assert nothing (`docs/constitution.md` v1.3.0: playwright is
verification tooling only). `measure-states.mjs` drives backend state via the
manager API (dev instance only) and restores it.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
