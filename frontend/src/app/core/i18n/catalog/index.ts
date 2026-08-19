import type { LanguageCode, TranslationCatalog } from '../translation.model';
import { de } from './de';
import { en } from './en';

/**
 * The registry of shipped catalogues, in registration order. This is the one
 * place in `src/` that names a language code: everything else reaches a
 * language through property config or through this registry, which is what
 * makes "add a third language" a config + catalogue change with no component
 * change (REQ-109).
 */
export const TRANSLATION_CATALOGS: ReadonlyMap<LanguageCode, TranslationCatalog> = new Map<
  LanguageCode,
  TranslationCatalog
>([
  ['de', de],
  ['en', en],
]);

/**
 * The catalogue used when a key is missing from the active language, and the
 * language `FALLBACK_DEFAULTS` starts on before the property config is
 * applied (02-design.md §6.1). Derived from the first registration rather
 * than written as a literal, so no code names `'de'` as "the default".
 */
export const DEFAULT_CATALOG_LANGUAGE: LanguageCode = [...TRANSLATION_CATALOGS.keys()][0];
