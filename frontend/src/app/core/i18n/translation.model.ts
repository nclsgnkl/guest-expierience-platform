import type { de } from './catalog/de';

/**
 * i18n contract (02-design.md §7.1). Hand-rolled and signal-based, no
 * dependency (decision #1, 2026-08-18).
 */

/** BCP-47 primary subtag, e.g. `de`. Also what `<html lang>` carries. */
export type LanguageCode = string;

/**
 * Every key the app chrome may ask for, derived from the German catalogue.
 * This is what makes catalogue completeness a compile-time property
 * (REQ-111): adding a key to `de.ts` immediately fails every other
 * catalogue until it is translated.
 */
export type TranslationKey = keyof typeof de;

/** A complete catalogue for one language. */
export type TranslationCatalog = Record<TranslationKey, string>;

/** Values interpolated into `{placeholder}`s. */
export type TranslationParams = Record<string, string | number>;
