/* Minimal ambient declarations for the two Node built-ins used by unit tests
   that assert something about a shipped *file* rather than about the DOM
   (see app/layout/sidebar/logo-asset.spec.ts). `@types/node` is not a
   dependency of this project and adding one would be a dependency-manifest
   change (docs/constitution.md), so only the members actually used are
   declared here.

   Scoped to the TEST compilation only: tsconfig.spec.json includes it via its
   d.ts include pattern, and tsconfig.app.json EXCLUDES it by name. Without
   that exclude the app compilation picked it up too -- its recursive src
   TypeScript include pattern matches d.ts files as well -- so application
   code could import node:fs, or call process.cwd(), and type-check clean
   while failing at bundle or runtime (round-3 review finding #5). */
declare module 'node:fs' {
  export function readFileSync(path: string, encoding: 'utf8'): string;
}

declare module 'node:path' {
  export function resolve(...segments: string[]): string;
}

declare const process: { cwd(): string };
