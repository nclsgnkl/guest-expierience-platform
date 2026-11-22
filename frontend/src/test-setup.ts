/**
 * Restores `localStorage`/`sessionStorage` under the Vitest jsdom environment
 * on Node ≥ 26.
 *
 * Not a project misconfiguration and not a test convenience — a runtime
 * regression that has to be worked around here:
 *
 *   - `@angular/build:unit-test` already defaults to the jsdom environment
 *     when no `browsers` are configured, and jsdom is installed. That part is
 *     correct and stays untouched.
 *   - Vitest copies the jsdom window's properties onto `globalThis`, but skips
 *     any name that `globalThis` already carries (`if (k in global)`), unless
 *     the name is on its own curated list. `Storage` is on that list;
 *     `localStorage` is not.
 *   - Node 26 ships an experimental Web Storage implementation, so
 *     `globalThis.localStorage` now *exists* — as a getter that returns
 *     `undefined` unless the process was started with `--localstorage-file`.
 *
 * The two together mean jsdom's storage never lands and every
 * `localStorage.clear()` in a `beforeEach` throws, which is why the suite
 * failed only after the Node upgrade and not while the frontend was written.
 *
 * `--localstorage-file` is deliberately NOT the fix: it is one persistent file
 * shared by every Vitest worker, so state bleeds between spec files and
 * between runs — the opposite of what these specs need.
 *
 * Each worker therefore gets its own in-memory `Storage`, matching the Web
 * Storage API the specs use (`getItem`, `setItem`, `removeItem`, `clear`,
 * `length`, `key`, and index access). Installed only when the real one is
 * missing, so a future Node/Vitest that fixes this upstream silently wins.
 */
class MemoryStorage implements Storage {
  private entries = new Map<string, string>();

  get length(): number {
    return this.entries.size;
  }

  key(index: number): string | null {
    return [...this.entries.keys()][index] ?? null;
  }

  getItem(key: string): string | null {
    return this.entries.get(String(key)) ?? null;
  }

  setItem(key: string, value: string): void {
    this.entries.set(String(key), String(value));
  }

  removeItem(key: string): void {
    this.entries.delete(String(key));
  }

  clear(): void {
    this.entries.clear();
  }
}

function install(name: 'localStorage' | 'sessionStorage'): void {
  let present = false;
  try {
    present = globalThis[name] != null;
  } catch {
    present = false; // some environments throw on access rather than return undefined
  }
  if (present) {
    return;
  }
  Object.defineProperty(globalThis, name, {
    value: new MemoryStorage(),
    configurable: true,
    writable: true,
  });
}

install('localStorage');
install('sessionStorage');
