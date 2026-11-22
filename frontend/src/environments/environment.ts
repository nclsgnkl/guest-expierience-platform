/**
 * Production environment (guest-frontend-integration REQ-505, D3): there is
 * **no** dev device token in a production bundle — `grep -r
 * gxp-dev-device-token dist/` after `ng build` must find nothing. The dev
 * literal lives only in `environment.development.ts`, swapped in by the
 * `fileReplacements` of the `development` build configuration.
 */
export const environment: { devDeviceToken: string | null; staleEscalationMs: number } = {
  devDeviceToken: null,

  /**
   * How long `STALE` stays discreet before the pill escalates to the banner
   * (guest-payload-consolidation REQ-952, D13): **60 minutes without
   * successful backend contact**. This file is the single source of truth for
   * that number — the requirement, the design spec
   * (`docs/design/screens-inhouse/connection.md` -> Trigger) and the
   * user-visible copy ("Seit über einer Stunde…") all mean this value.
   * `environment.development.ts` shortens it, and nothing else may.
   */
  staleEscalationMs: 60 * 60 * 1000,
};
