import { randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import type { Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { CLIENT_TARGETS } from '../../../shared/src/index.js';
import type { ClientTarget, ExportContentMode, GeneratedConfiguration } from '../../../shared/src/index.js';
import { acceptsAJSON, contentDisposition, subscriptionPayload } from './lanSubscription.js';

const lifetime = 180000;
const maxImports = 24;
const nodeScheme: Partial<Record<ClientTarget, string>> = {
  shadowrocket: 'sub://{base64}',
  loon: 'loon://import?nodelist={url}',
  quanx: 'quantumult-x:///add-resource?remote-resource={quanx}',
  anywhere: 'anywhere://add-subscription?url={url}',
  v2box: 'v2box://install-sub?url={url}',
  hiddify: 'hiddify://import/{url}',
};

interface ImportLink {
  expiresAt: number;
  load: () => GeneratedConfiguration | null;
}

export class DirectImportService {
  private readonly entries = new Map<string, ImportLink>();
  private server?: Server;
  private listener?: Promise<number>;
  private idleTimer?: ReturnType<typeof setTimeout>;

  async issue(target: ClientTarget, mode: ExportContentMode, load: ImportLink['load']): Promise<string | null> {
    const meta = CLIENT_TARGETS[target];
    const template = mode === 'nodesOnly' ? nodeScheme[target] : meta.supportsDirectConfigurationImport ? meta.urlSchemeHint : null;
    if (!template) return null;
    const port = await (this.listener ??= this.start());
    const token = randomBytes(24).toString('base64url');
    const now = Date.now();
    this.prune(now);
    if (this.entries.size >= maxImports) this.entries.delete(this.entries.keys().next().value!);
    this.entries.set(token, { expiresAt: now + lifetime, load });
    clearTimeout(this.idleTimer);
    this.idleTimer = setTimeout(() => { this.close(); }, lifetime);
    this.idleTimer.unref();
    const url = `http://127.0.0.1:${port}/import/${token}`;
    return template.replace('{url}', encodeURIComponent(url))
      .replace('{base64}', Buffer.from(url).toString('base64url'))
      .replace('{quanx}', encodeURIComponent(JSON.stringify({ server_remote: [url] })));
  }

  close() {
    clearTimeout(this.idleTimer);
    this.entries.clear();
    this.server?.close();
    this.server = undefined;
    this.listener = undefined;
  }

  private prune(now: number) {
    for (const [key, entry] of this.entries) if (entry.expiresAt <= now) this.entries.delete(key);
  }

  private start(): Promise<number> {
    return new Promise((resolve, reject) => {
      const server = createServer((req, res) => {
        res.setHeader('Cache-Control', 'no-store');
        const address = server.address() as AddressInfo;
        const token = /^\/import\/([\w-]+)(?:\?[^#]*)?$/.exec(req.url ?? '')?.[1];
        const entry = token ? this.entries.get(token) : undefined;
        if (!entry || entry.expiresAt <= Date.now() || req.headers.host !== `127.0.0.1:${address.port}`) {
          res.writeHead(404).end();
          return;
        }
        if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405, { Allow: 'GET, HEAD' }).end(); return; }
        try {
          const generated = entry.load();
          if (!generated) { res.writeHead(404).end(); return; }
          const header = req.headers['x-anywhere-format'];
          const { content, mimeType, filename } = subscriptionPayload(generated, acceptsAJSON(Array.isArray(header) ? header.join(',') : header));
          res.writeHead(200, { 'Content-Type': mimeType + '; charset=utf-8', 'Content-Length': Buffer.byteLength(content),
            'Content-Disposition': contentDisposition(filename) });
          res.end(req.method === 'HEAD' ? undefined : content);
        } catch { res.writeHead(500).end(); }
      });
      this.server = server;
      server.once('error', error => { this.close(); reject(error); });
      server.listen(0, '127.0.0.1', () => {
        server.unref();
        resolve((server.address() as AddressInfo).port);
      });
    });
  }
}
