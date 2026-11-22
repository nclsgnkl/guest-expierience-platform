import { describe, expect, it } from 'vitest';

import type { GuestModule } from '@gxp/api-client';

import { MODULE_REGISTRY, mapWireModule } from './module-registry';

/**
 * The registry's dispatch, driven with **a module type that exists only in
 * this file** (module-content REQ-976, D17): no contract member, no seed row,
 * no component, no entry in the real `MODULE_REGISTRY`. That is what §4.2's
 * registry-as-a-parameter is for — a second type can be exercised without a
 * test-only seam in production code, and without designing a second screen.
 *
 * **What this spec deliberately does NOT prove: that the registry's literals
 * match the contract's.** Its discriminant is defined by this file, so there is
 * no contract for it to disagree with. That axis is
 * `de.gxp.guest.GuestModuleContractTest`'s, on the Java side, and 02-design.md
 * §8 explains why it cannot live here.
 */

/** Invented here and nowhere else — grep `zeitkapsel` to prove it. */
const FABRICATED_KIND = 'zeitkapsel';

interface FabricatedModule {
  readonly type: 'zeitkapsel';
  readonly caption: string;
}

function mapFabricated(wire: GuestModule): FabricatedModule | null {
  const caption = (wire as unknown as { caption?: unknown }).caption;
  return typeof caption === 'string' ? { type: 'zeitkapsel', caption } : null;
}

/**
 * The real `info` entry beside the fabricated one. Structural assignability
 * lets this pass as the registry parameter: `info` is present and correctly
 * typed, and the extra key is what the test is about.
 */
const REGISTRY_WITH_BOTH = {
  ...MODULE_REGISTRY,
  [FABRICATED_KIND]: { map: mapFabricated },
};

function infoWire(rows: { label: string; value: string }[]): GuestModule {
  return {
    type: 'info',
    rows: rows.map((row) => ({ label: { de: row.label }, value: { de: row.value } })),
  } as unknown as GuestModule;
}

describe('mapWireModule', () => {
  it('dispatches a fabricated type to its own entry — `info` does not swallow it', () => {
    const wire = { type: FABRICATED_KIND, caption: 'Kapsel' } as unknown as GuestModule;

    const mapped = mapWireModule(REGISTRY_WITH_BOTH, wire, 'de');

    expect(mapped).toEqual({ type: 'zeitkapsel', caption: 'Kapsel' });
  });

  it('still maps `info` through the `info` entry with both registered', () => {
    const wire = infoWire([{ label: 'Check-out', value: 'Bis 11:00 Uhr' }]);

    const mapped = mapWireModule(REGISTRY_WITH_BOTH, wire, 'de');

    expect(mapped).toEqual({
      type: 'info',
      rows: [{ label: { de: 'Check-out' }, value: { de: 'Bis 11:00 Uhr' } }],
    });
  });

  it('returns null for a discriminant in neither entry (REQ-973)', () => {
    const wire = { type: 'wlan', ssid: 'Haus' } as unknown as GuestModule;

    expect(mapWireModule(REGISTRY_WITH_BOTH, wire, 'de')).toBeNull();
  });

  it('returns null for a structurally invalid known type (REQ-972)', () => {
    const wire = { type: 'info', rows: 'not-an-array' } as unknown as GuestModule;

    expect(mapWireModule(REGISTRY_WITH_BOTH, wire, 'de')).toBeNull();
  });

  /**
   * Review finding F-3, pinned so it cannot come back. The generated model
   * types `GuestMenuItem.module` as optional, but the server does not omit the
   * key — Jackson's default inclusion puts `"module": null` on the wire. A
   * `=== undefined` guard would read `.type` off `null` and throw, out of a
   * branch 02-design.md §3.2 forbids a `try`/`catch` around.
   */
  it('returns null for both null and undefined — the wire really sends null (F-3)', () => {
    expect(mapWireModule(REGISTRY_WITH_BOTH, null, 'de')).toBeNull();
    expect(mapWireModule(REGISTRY_WITH_BOTH, undefined, 'de')).toBeNull();
  });
});
