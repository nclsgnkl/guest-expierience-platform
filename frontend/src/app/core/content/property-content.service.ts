import { Injectable } from '@angular/core';

import { EXAMPLE_PROPERTY } from './example-property.data';
import type { PropertyConfig } from './property.model';

/**
 * Supplies per-property/guest content via Angular DI (constitution
 * principle 2). Components inject this service and never import
 * `example-property.data.ts` directly — this is what keeps a future
 * Quarkus-backed implementation a one-file change.
 */
@Injectable({ providedIn: 'root' })
export class PropertyContentService {
  /**
   * Synchronous today (static data). The signature is intentionally the
   * shape an async version (HTTP -> future backend) can grow into without
   * changing any component's call site beyond how it reads the value.
   */
  getProperty(): PropertyConfig {
    return EXAMPLE_PROPERTY;
  }

  /**
   * Resolves a `targetSectionId` (e.g. an `ActionCard`'s) to the route the
   * matching `menuItems`/`settings` entry carries — the single place
   * REQ-019's "must resolve to a real id" invariant is enforced. Throws on
   * an unknown id: a dangling reference is a content-authoring bug that
   * should fail loudly in tests, not silently do nothing for a guest on a
   * touch kiosk.
   */
  getSectionRoute(targetSectionId: string): string {
    const property = this.getProperty();
    const match = [...property.menuItems, property.settings].find(
      (item) => item.id === targetSectionId,
    );

    if (!match) {
      throw new Error(`Unknown targetSectionId: "${targetSectionId}"`);
    }

    return match.route;
  }
}
