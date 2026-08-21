/**
 * Production environment (guest-frontend-integration REQ-505, D3): there is
 * **no** dev device token in a production bundle — `grep -r
 * gxp-dev-device-token dist/` after `ng build` must find nothing. The dev
 * literal lives only in `environment.development.ts`, swapped in by the
 * `fileReplacements` of the `development` build configuration.
 */
export const environment: { devDeviceToken: string | null } = {
  devDeviceToken: null,
};
