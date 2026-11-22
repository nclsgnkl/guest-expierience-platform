import type { GuestInfoModule, GuestModule } from '@gxp/api-client';

import type {
  AuthoredInfoModule,
  AuthoredModule,
  Localized,
  ResolvedModule,
} from '../content/property.model';

/**
 * The module registry (module-content REQ-970/971/980, 02-design.md §4).
 *
 * One place names every module type this build supports, and the compiler
 * makes that list total in three directions:
 *
 *   1. {@link MODULE_REGISTRY}'s mapped type forces its key set to equal the
 *      domain union's discriminants — a kind added to `property.model.ts` with
 *      no entry here fails `ng build`;
 *   2. {@link resolveModule}'s switch is exhaustive over the same union;
 *   3. `_EveryModuleMemberIsRegistered` (below) fails the build if the
 *      **contract** gains a member nothing here maps.
 *
 * The fourth touchpoint — which component renders a kind — deliberately lives
 * in `features/section-host/`, not here: this file importing a feature
 * component would be the app's first `core → features` import (§4.4). That map
 * is `satisfies Record<ModuleKind, Type<unknown>>`, a compiler-checked
 * projection of the key set below.
 *
 * **Why `core/guest/` and not `core/modules/`** (§4.1): this file must import
 * the generated `GuestModule` union, and `frontend/CLAUDE.md` permits
 * `@gxp/api-client` imports in `core/guest/` and `core/api/` only.
 *
 * Nothing here logs, on any path — a dropped module could carry property
 * content (the WiFi row is the temptation `info.md` names), and no log may
 * (constitution principle 3). A console warning is not exempt.
 */

/** One line per module type: the wire member each domain kind maps from. */
interface ModuleWireMap {
  readonly info: GuestInfoModule;
}

/** The domain union's discriminants — the single source of the type set. */
export type ModuleKind = AuthoredModule['type'];

type AuthoredOf<K extends ModuleKind> = Extract<AuthoredModule, { type: K }>;

interface ModuleEntry<K extends ModuleKind> {
  /**
   * Wire → authored, or `null` when the module is structurally invalid
   * (REQ-972). Takes the whole union: narrowing is the entry's own one-line
   * discriminant check (D9's cast-free arm), which keeps the call site below
   * cast-free too.
   */
  readonly map: (wire: GuestModule, defaultLanguage: string) => AuthoredOf<K> | null;
}

export const MODULE_REGISTRY: { [K in ModuleKind]: ModuleEntry<K> } = {
  info: { map: mapInfoModule },
};

/**
 * REQ-980: the compile-time guard D9's Variant B costs. TypeScript checks a
 * switch over a properly discriminated union for exhaustiveness; with the wire
 * member's `type` a plain `string` it cannot, so a contract member nobody
 * registered would fall through to REQ-973's "unknown → not shown" path
 * silently, in production. This is the hand-written replacement, and it fails
 * the BUILD:
 *
 *   error TS2344: Type 'GuestWlanModule' does not satisfy the constraint 'never'.
 *
 * **Its one limitation, recorded rather than discovered later:** `Exclude` is
 * assignability-based, so a future member whose properties are a subset of a
 * registered member's would be excluded structurally and slip through. Every
 * real member adds its own fields (that is what A3/D14 means), so this is a
 * theoretical hole, not a live one — but it is why the guard is `Exclude` over
 * member *types* and not something stronger: under Variant B there is no
 * literal union to check against.
 */
type AssertNever<T extends never> = T;
type RegisteredWire = ModuleWireMap[ModuleKind];
type _EveryModuleMemberIsRegistered = AssertNever<Exclude<GuestModule, RegisteredWire>>;

/**
 * REQ-971: **the wire-validation set IS the registration map.** No second
 * list — the `registered-icons.ts` precedent, whose comment states the reason:
 * deriving the accepted values from the registrations is what keeps the two
 * from drifting.
 *
 * The registry is a **parameter, not an import**, so a fabricated module type
 * can exist purely inside a spec (D17/§12.1) without a test-only seam in
 * production code — the `menu-split.ts` precedent of exporting the pure rule.
 *
 * `wire == null` is deliberate, and it is a bug fix rather than a style choice
 * (review finding F-3): the generated model types `GuestMenuItem.module` as
 * optional, but the server does **not** omit the key — Jackson's default
 * inclusion puts `"module": null` on the wire, exactly as `GuestContent.home`
 * already does. A `=== undefined` guard would let `null` through and then read
 * `.type` off it, throwing out of a branch §3.2 forbids a `try`/`catch` around
 * — turning one dropped module into a blank screen and breaking REQ-972's
 * per-module failure isolation.
 */
export function mapWireModule(
  registry: { [K in ModuleKind]: ModuleEntry<K> },
  wire: GuestModule | null | undefined,
  defaultLanguage: string,
): AuthoredModule | null {
  if (wire == null || !Object.hasOwn(registry, wire.type)) {
    return null; // nothing authored (D15), or a type this build does not know (REQ-973)
  }
  return registry[wire.type as ModuleKind].map(wire, defaultLanguage);
}

/**
 * Language resolution, one arm per kind. Under `strict` a new kind turns this
 * into a compile error — §4.4's third compiler-enforced touchpoint.
 *
 * A switch rather than a `resolve` per registry entry, deliberately: indexing a
 * record of per-kind functions with a union key is the classic correlated-union
 * problem and needs an `as never` at the call site. A switch over a real
 * discriminated union is cast-free and the compiler counts the arms.
 */
export function resolveModule(
  module: AuthoredModule,
  text: (value: Localized<string>, field: string) => string,
): ResolvedModule {
  switch (module.type) {
    case 'info':
      return {
        type: 'info',
        rows: module.rows.map((row) => ({
          label: text(row.label, 'module.info.label'),
          value: text(row.value, 'module.info.value'),
        })),
      };
  }
}

/**
 * The contract's two module bounds, **enforced here because here is the only
 * place they are enforced at all.**
 *
 * `api/openapi.yaml` declares `GuestInfoModule.rows.maxItems: 10` and
 * `ModuleText.additionalProperties.maxLength: 240`. Measured on the generated
 * tree, neither binds a served payload:
 *
 *   - `maxItems` becomes `@Size(max=10)` on `GuestInfoModule.getRows()`, but
 *     `GuestApi.getGuestContent`'s **return carries no `@Valid`**, and Bean
 *     Validation does not walk an unannotated response — the annotation is
 *     inert on the path that matters;
 *   - `maxLength` under `additionalProperties` does not reach Java at all
 *     (`GuestInfoRow.label` is a bare `Map<String, String>`), and `V12`'s
 *     `label` / `value` columns are unbounded `text`.
 *
 * So these numbers are **the kiosk's own boundary validation of an untrusted
 * wire**, quoting the contract as their *source* and not as their enforcement.
 * That distinction is why `MAX_MENU_ITEMS` is 50 in `guest-content.mapper.ts`
 * and no longer the invented 5 (REQ-901): a bound is fine, a bound whose
 * message cites a rule nothing applies is a bug waiting to reject something
 * legitimate. If the contract's numbers move, these move with them.
 */
const MAX_INFO_ROWS = 10;
const MAX_MODULE_TEXT = 240;

/**
 * `info`'s hand-written validator. **Reject, never truncate** — the mapper's
 * existing posture: a 241-character value costs the section its content and
 * shows "coming soon"; it does not silently render a truncated password.
 *
 * Validation does not lean on the types (§3.2): under Variant B a
 * `wire is GuestInfoModule` predicate is an assertion TypeScript trusts, not a
 * proof, so every field is checked against the runtime value. Returns `null`,
 * never throws — REQ-972's per-module isolation depends on one malformed
 * module leaving the envelope and every other item intact.
 *
 * Deliberately absent: any placeholder-shaped check. A value containing `{…}`
 * is not a drop trigger (D18 rejected that explicitly — it false-positives on
 * legitimately braced authored text).
 */
function mapInfoModule(wire: GuestModule, defaultLanguage: string): AuthoredInfoModule | null {
  if (wire.type !== 'info') {
    return null;
  }
  const rows: unknown = wire.rows;
  if (!Array.isArray(rows) || rows.length > MAX_INFO_ROWS) {
    return null;
  }
  const mapped: { label: Localized<string>; value: Localized<string> }[] = [];
  for (const row of rows as readonly (Record<string, unknown> | null)[]) {
    const label = localizedOrNull(row?.['label'], defaultLanguage);
    const value = localizedOrNull(row?.['value'], defaultLanguage);
    if (label === null || value === null) {
      return null;
    }
    mapped.push({ label, value });
  }
  return { type: 'info', rows: mapped };
}

/**
 * The null-returning sibling of `guest-content.mapper.ts`'s throwing
 * `localized()`, and it applies the same rule: a localized map is valid when it
 * is a plain object carrying a **non-blank entry for the property's default
 * language**, plus the wire bound every entry must respect.
 *
 * It returns rather than throws on purpose — §3.2 forbids a `try`/`catch`
 * around the module branch, because that would also swallow the envelope
 * failures that must stay fatal.
 */
function localizedOrNull(value: unknown, defaultLanguage: string): Localized<string> | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return null;
  }
  const entries = Object.entries(value as Record<string, unknown>);
  for (const [, entry] of entries) {
    if (typeof entry !== 'string' || entry.length > MAX_MODULE_TEXT) {
      return null;
    }
  }
  const map = value as Record<string, string>;
  const fallback = map[defaultLanguage];
  if (fallback === undefined || fallback.trim() === '') {
    return null;
  }
  return { ...map } as Localized<string>;
}
