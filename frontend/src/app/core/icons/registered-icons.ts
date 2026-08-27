import {
  phosphorArrowRight,
  phosphorCar,
  phosphorCaretDown,
  phosphorCheck,
  phosphorCircle,
  phosphorClock,
  phosphorDotsThree,
  phosphorFileText,
  phosphorFirstAid,
  phosphorForkKnife,
  phosphorGearSix,
  phosphorGlobe,
  phosphorHouse,
  phosphorInfo,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorMoon,
  phosphorPersonSimpleHike,
  phosphorRecycle,
  phosphorShoppingBag,
  phosphorSun,
  phosphorWifiHigh,
  phosphorWifiSlash,
} from '@ng-icons/phosphor-icons/regular';
// The heading sparkle is the one glyph rendered in the `fill` weight: the
// reference render shows it solid and gradient-filled, where every other glyph
// is `regular` (docs/design/screens/home.md -> Heading block).
import { phosphorSparkleFill } from '@ng-icons/phosphor-icons/fill';

/**
 * Every Phosphor glyph the bundle ships (offline principle: nothing is ever
 * fetched). One registry, two consumers:
 *
 *   - `app.config.ts` hands it to `provideIcons()` so `NgIcon` can resolve
 *     each name;
 *   - `guest-content.mapper.ts` validates icon names ARRIVING FROM THE WIRE
 *     against exactly this set (REQ-510 boundary validation) — an unknown
 *     name renders `CONTENT_FALLBACK_ICON` instead of a blank glyph or a
 *     crash (02-design.md §6).
 *
 * Deriving the validation set from the registration map is what keeps the two
 * from drifting: registering a glyph makes it valid wire content in the same
 * line.
 *
 * `phosphorCircleHalf` — the natural icon for the `system` appearance — stays
 * deliberately unregistered: decision #4 (2026-08-18) keeps `system` out of
 * the shipped option list. A property that ever configures `system` adds it
 * here.
 *
 * Additions for guest-frontend-integration:
 *   - `phosphorWifiSlash` — the connection screen's glyph
 *     (docs/design/screens-inhouse/connection.md).
 *   - `phosphorCircle` — the neutral fallback for unknown wire icon names.
 *
 * Additions for sidebar-catalogue-overflow (REQ-904):
 *   - the `default_icon` of every row `V10__catalogue_expansion.sql` adds —
 *     `phosphorWifiHigh`, `phosphorClock`, `phosphorRecycle`,
 *     `phosphorFirstAid`, `phosphorCar`, `phosphorForkKnife`,
 *     `phosphorPersonSimpleHike` (`phosphorFileText` was already here).
 *     Registering them is what makes those names valid WIRE content: an
 *     unregistered name renders `CONTENT_FALLBACK_ICON` rather than failing,
 *     so a popover full of identical neutral glyphs would look like a design
 *     problem instead of a missing registration. Those rows are disposable
 *     fixtures — removing them is that migration reversed plus these seven
 *     lines, and nothing else (REQ-903).
 *   - `phosphorDotsThree` — the sidebar's overflow trigger, app chrome rather
 *     than wire content (02-design.md §7.1).
 *
 * `phosphorWifiSlash` is deliberately NOT reused for the WLAN fixture: it is
 * the connection screen's offline glyph, and a Wi-Fi page marked with a
 * struck-out Wi-Fi symbol reads as "no connection".
 */
export const REGISTERED_ICONS = {
  phosphorArrowRight,
  phosphorCar,
  phosphorCaretDown,
  phosphorCheck,
  phosphorCircle,
  phosphorClock,
  phosphorDotsThree,
  phosphorFileText,
  phosphorFirstAid,
  phosphorForkKnife,
  phosphorGearSix,
  phosphorGlobe,
  phosphorHouse,
  phosphorInfo,
  phosphorKey,
  phosphorLightbulbFilament,
  phosphorMapPin,
  phosphorMoon,
  phosphorPersonSimpleHike,
  phosphorRecycle,
  phosphorShoppingBag,
  phosphorSparkleFill,
  phosphorSun,
  phosphorWifiHigh,
  phosphorWifiSlash,
};

/** What an unknown wire icon name renders as — neutral, never a crash. */
export const CONTENT_FALLBACK_ICON = 'phosphorCircle';

export function isRegisteredIcon(name: string): boolean {
  return Object.prototype.hasOwnProperty.call(REGISTERED_ICONS, name);
}
