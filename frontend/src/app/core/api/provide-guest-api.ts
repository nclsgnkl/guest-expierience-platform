import { provideHttpClient } from '@angular/common/http';
import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { Configuration } from '@gxp/api-client';

import { DeviceTokenStore } from './device-token.store';

/**
 * Wires the generated client (guest-frontend-integration REQ-501):
 * `provideHttpClient()` plus one `Configuration` whose `bearerAuth`
 * credential resolves the stored device token **at call time** — the
 * generated services call `addCredentialToHeaders('bearerAuth', …)` on every
 * request, so a token set by the pairing claim (or wiped by a 401) is
 * effective on the very next call with no re-wiring (verify-at-implementation
 * (b): confirmed by `provide-guest-api.spec.ts`).
 *
 * `basePath: ''` = same-origin: `/api/**` reaches the serving Quarkus
 * instance in production (D7) and the `ng serve` proxy in development
 * (REQ-502) — CORS never exists on the backend.
 *
 * A factory provider instead of the generated `provideApi()` helper, because
 * the credential function needs the DI-managed `DeviceTokenStore` and
 * `provideApi()` only accepts a ready-made value. Semantics are identical.
 */
export function provideGuestApi(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideHttpClient(),
    {
      provide: Configuration,
      deps: [DeviceTokenStore],
      useFactory: (tokenStore: DeviceTokenStore) =>
        new Configuration({
          basePath: '',
          credentials: { bearerAuth: () => tokenStore.token() ?? undefined },
        }),
    },
  ]);
}
