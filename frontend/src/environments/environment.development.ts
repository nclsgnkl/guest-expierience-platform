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
export const environment: { devDeviceToken: string | null } = {
  devDeviceToken: 'gxp-dev-device-token',
};
