/**
 * Development environment (guest-frontend-integration REQ-503/505, D2/D3
 * approved 2026-08-20). The token literal below is **public, dev-only**
 * knowledge: it authenticates only against a `%dev` backend whose seeder
 * (`de.gxp.devseed.DevDataSeeder`) inserted its hash — that hash never exists
 * in a production database, which is the entire security argument (REQ-504).
 *
 * `DeviceTokenStore` consults this value only when `localStorage` holds no
 * token, so a dev box that actually paired keeps its real token.
 */
export const environment: { devDeviceToken: string | null; staleEscalationMs: number } = {
  devDeviceToken: 'gxp-dev-device-token',

  /**
   * **One minute, not the production hour** (owner, 2026-08-28). The escalated
   * staleness banner is otherwise unreachable by hand: seeing it would mean
   * waiting an hour or editing a running system's clock or storage, so in
   * practice it would be checked by nobody. Under this value the same path —
   * unplug the backend, wait — reaches the banner while you are still looking
   * at the screen.
   *
   * `environment.ts` holds the real threshold and is the source of truth;
   * this is a testability affordance and ships in no guest build. The banner's
   * own copy still says "Seit über einer Stunde" here, which is inaccurate
   * under a one-minute threshold and is **accepted**: the string is correct in
   * production, no guest ever sees a dev build, and a threshold-dependent
   * string would be code written for a state that does not ship.
   */
  staleEscalationMs: 60 * 1000,
};
