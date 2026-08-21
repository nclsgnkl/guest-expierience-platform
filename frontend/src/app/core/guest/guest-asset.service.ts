import { DestroyRef, Injectable, inject, signal, type Signal } from '@angular/core';
import { GuestService } from '@gxp/api-client';

/**
 * Authorized asset delivery (guest-frontend-integration REQ-513): every
 * image is fetched through `GuestService.getGuestAsset` — carrying the
 * device bearer — and handed to templates as an **object URL signal**. A bare
 * `<img src="/api/…">` can not authenticate and must never exist.
 *
 * Lifecycle rules (02-design.md §2/§12):
 *
 *   - **Per-id memo.** Asset content per id is immutable by contract (the id
 *     changes when the content does), so one fetch per id per app lifetime is
 *     correct — the ETag/Cache-Control story matters only across reloads.
 *   - **Revoke-on-replace + revoke-on-destroy.** Object URLs pin blobs in
 *     memory; on a 24/7 kiosk a leak is slow but certain. Replacing an id's
 *     URL revokes the old one, and `DestroyRef` teardown revokes everything.
 *   - **404 → `null`**, and the component renders its existing fallback
 *     (placeholder logo / no-artwork) — never a broken image.
 *
 * Verify-at-implementation (a) — blob fetch vs. the browser HTTP cache —
 * is recorded in the feature handoff: within a session the memo makes
 * revalidation moot; across reloads the browser cache serves the authorized
 * GET per its immutable Cache-Control, with `HttpClient` uninvolved.
 */
@Injectable({ providedIn: 'root' })
export class GuestAssetService {
  private readonly api = inject(GuestService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly urls = new Map<number, ReturnType<typeof signal<string | null>>>();

  constructor() {
    this.destroyRef.onDestroy(() => {
      for (const url of this.urls.values()) {
        revoke(url());
      }
      this.urls.clear();
    });
  }

  /**
   * The object URL for an asset id — `null` until loaded (and forever, when
   * the asset 404s). The first call per id triggers the fetch.
   */
  url(assetId: number): Signal<string | null> {
    let holder = this.urls.get(assetId);
    if (!holder) {
      holder = signal<string | null>(null);
      this.urls.set(assetId, holder);
      this.fetch(assetId, holder);
    }
    return holder.asReadonly();
  }

  private fetch(assetId: number, holder: ReturnType<typeof signal<string | null>>): void {
    this.api.getGuestAsset(assetId).subscribe({
      next: (blob) => {
        revoke(holder()); // replace = revoke first (defensive; normally null)
        holder.set(URL.createObjectURL(blob));
      },
      error: () => holder.set(null), // 404/network → fallback rendering
    });
  }
}

function revoke(url: string | null): void {
  if (url !== null) {
    URL.revokeObjectURL(url);
  }
}
