/* Tiny static + /api proxy server for measuring the PRODUCTION bundle
 * (throwaway; the dev loop uses `ng serve`). No dependencies. */
import { createServer, request as httpRequest } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const ROOT = new URL('../dist/frontend/browser', import.meta.url).pathname;
const PORT = Number(process.argv[2] ?? 4300);
const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.woff2': 'font/woff2',
  '.png': 'image/png', '.txt': 'text/plain',
};

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname.startsWith('/api/')) {
    const proxied = httpRequest(
      { host: 'localhost', port: 8080, path: req.url, method: req.method, headers: req.headers },
      (upstream) => {
        res.writeHead(upstream.statusCode, upstream.headers);
        upstream.pipe(res);
      },
    );
    proxied.on('error', () => { res.writeHead(502); res.end(); });
    req.pipe(proxied);
    return;
  }
  let path = normalize(url.pathname).replace(/^\/+/, '');
  if (path === '' || !path.includes('.')) path = 'index.html'; // SPA fallback
  try {
    const body = await readFile(join(ROOT, path));
    res.writeHead(200, { 'content-type': MIME[extname(path)] ?? 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end();
  }
}).listen(PORT, () => console.log(`static+proxy on :${PORT}`));
