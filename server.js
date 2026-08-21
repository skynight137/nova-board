
'use strict';

/**
 * Simple Node.js File Manager
 * Zero dependencies — serves a browsable "Index of" directory listing
 * with navigation and file download, similar to Apache's autoindex.
 *
 * Usage:
 *   node server.js [directory] [port]
 *   PORT=8080 ROOT_DIR=./shared node server.js
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(process.argv[2] || process.env.ROOT_DIR || '.');
const PORT = parseInt(process.argv[3] || process.env.PORT || '8080', 10);

// ---------- helpers ----------

function humanSize(bytes) {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const val = bytes / Math.pow(1024, i);
  return `${i === 0 ? val : val.toFixed(1)} ${units[i]}`;
}

function escapeHtml(str) {
  return str.replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

// Resolve a request path safely inside ROOT (blocks ../ traversal)
function safeResolve(reqPath) {
  const decoded = decodeURIComponent(reqPath);
  const resolved = path.normalize(path.join(ROOT, decoded));
  const rel = path.relative(ROOT, resolved);
  if (rel.startsWith('..') || path.isAbsolute(rel)) return null;
  return resolved;
}

const MIME = {
  '.html': 'text/html', '.htm': 'text/html', '.css': 'text/css',
  '.js': 'application/javascript', '.json': 'application/json',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.gif': 'image/gif', '.svg': 'image/svg+xml', '.webp': 'image/webp',
  '.pdf': 'application/pdf', '.txt': 'text/plain', '.md': 'text/plain',
  '.mp4': 'video/mp4', '.mp3': 'audio/mpeg', '.wav': 'audio/wav',
  '.zip': 'application/zip', '.csv': 'text/csv',
};
function mimeType(p) {
  return MIME[path.extname(p).toLowerCase()] || 'application/octet-stream';
}

function breadcrumb(reqPath) {
  const parts = reqPath.split('/').filter(Boolean);
  let acc = '';
  const links = [`<a href="/">home</a>`];
  for (const part of parts) {
    acc += '/' + part;
    links.push(`<a href="${acc}/">${escapeHtml(part)}</a>`);
  }
  return links.join(' / ');
}

function renderDirectory(dirPath, reqPath, res) {
  fs.readdir(dirPath, { withFileTypes: true }, (err, entries) => {
    if (err) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end('Error reading directory: ' + err.message);
      return;
    }

    entries.sort((a, b) => {
      if (a.isDirectory() !== b.isDirectory()) return a.isDirectory() ? -1 : 1;
      return a.name.localeCompare(b.name);
    });

    const rows = entries.map((entry) => {
      let stat;
      try {
        stat = fs.statSync(path.join(dirPath, entry.name));
      } catch {
        return '';
      }
      const isDir = entry.isDirectory();
      const displayName = entry.name + (isDir ? '/' : '');
      const href = encodeURIComponent(entry.name) + (isDir ? '/' : '');
      const size = isDir ? '&mdash;' : humanSize(stat.size);
      const mtime = stat.mtime.toISOString().slice(0, 19).replace('T', ' ');
      const icon = isDir ? '📁' : '📄';
      const download = isDir ? '' : `<a href="${href}?dl=1" class="dl" title="Download">⬇</a>`;
      return `<tr><td>${icon}</td><td><a href="${href}">${escapeHtml(displayName)}</a></td>` +
             `<td>${size}</td><td>${mtime}</td><td>${download}</td></tr>`;
    }).join('\n');

    const parentRow = reqPath !== '/'
      ? `<tr><td>📁</td><td><a href="../">../</a></td><td>&mdash;</td><td></td><td></td></tr>`
      : '';

    const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Index of ${escapeHtml(reqPath)}</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
         margin: 2rem; color: #222; background: #fafafa; }
  .breadcrumb { color: #888; margin-bottom: .5rem; font-size: .85rem; }
  h1 { font-size: 1.25rem; border-bottom: 1px solid #ddd; padding-bottom: .6rem; margin-top: 0; }
  table { border-collapse: collapse; width: 100%; margin-top: 1rem; background: #fff;
          box-shadow: 0 1px 3px rgba(0,0,0,.06); }
  th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eee; font-size: .9rem; }
  th { color: #777; font-weight: 600; font-size: .78rem; text-transform: uppercase; }
  td:first-child, th:first-child { width: 1.5rem; }
  a { color: #0366d6; text-decoration: none; }
  a:hover { text-decoration: underline; }
  tr:hover { background: #f6f8fa; }
  .dl { font-size: 1rem; }
  footer { margin-top: 1.5rem; color: #aaa; font-size: .75rem; }
</style>
</head>
<body>
<div class="breadcrumb">${breadcrumb(reqPath)}</div>
<h1>Index of ${escapeHtml(reqPath)}</h1>
<table>
<thead><tr><th></th><th>Name</th><th>Size</th><th>Modified</th><th></th></tr></thead>
<tbody>
${parentRow}
${rows}
</tbody>
</table>
<footer>simple-file-manager &middot; serving ${escapeHtml(ROOT)}</footer>
</body>
</html>`;

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
  });
}

// ---------- server ----------

const server = http.createServer((req, res) => {
  const reqUrl = new URL(req.url, `http://localhost:${PORT}`);
  const reqPath = decodeURIComponent(reqUrl.pathname);
  const forceDownload = reqUrl.searchParams.has('dl');

  const resolved = safeResolve(reqPath);
  if (!resolved) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('403 Forbidden');
    return;
  }

  fs.stat(resolved, (err, stat) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('404 Not Found');
      return;
    }

    if (stat.isDirectory()) {
      if (!reqPath.endsWith('/')) {
        res.writeHead(302, { Location: reqPath + '/' + reqUrl.search });
        res.end();
        return;
      }
      renderDirectory(resolved, reqPath, res);
      return;
    }

    const headers = { 'Content-Length': stat.size };
    if (forceDownload) {
      headers['Content-Disposition'] = `attachment; filename="${path.basename(resolved)}"`;
      headers['Content-Type'] = 'application/octet-stream';
    } else {
      headers['Content-Type'] = mimeType(resolved);
    }

    res.writeHead(200, headers);
    const stream = fs.createReadStream(resolved);
    stream.on('error', () => { res.end(); });
    stream.pipe(res);
  });
});

server.listen(PORT, () => {
  console.log(`Serving  ${ROOT}`);
  console.log(`URL      http://localhost:${PORT}`);
});