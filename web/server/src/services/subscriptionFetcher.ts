import { lookup } from 'node:dns';
import type { LookupAddress, LookupOptions } from 'node:dns';
import { request as httpRequest } from 'node:http';
import type { IncomingMessage } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { isIP } from 'node:net';
import { createBrotliDecompress, createGunzip, createInflate } from 'node:zlib';
import type { SubscriptionRequestOptions, SubscriptionUsage } from '../../../shared/src/index.js';
import { base64Decode, cleanLine, parseSubscription, quotaFromNotices, quotaFromText } from './subscriptionParser.js';

const maxBody = 16 * 1024 * 1024;
type LookupCallback = (error: NodeJS.ErrnoException | null, address: string | LookupAddress[], family?: number) => void;

export function webURL(input: string, httpsOnly = false): URL {
  let url: URL;
  try { url = new URL(input.trim()); } catch { throw new Error('Invalid URL'); }
  if (!(url.protocol === 'https:' || (!httpsOnly && url.protocol === 'http:')) || url.username || url.password) {
    throw new Error(httpsOnly ? 'An HTTPS URL is required' : 'An HTTP or HTTPS URL is required');
  }
  return url;
}

function dnsQuery(host: string, type: number): Buffer {
  const header = Buffer.alloc(12);
  header.writeUInt16BE(0x0100, 2);
  header.writeUInt16BE(1, 4);
  const labels = host.replace(/\.$/, '').split('.').map(label => {
    if (!label.length || Buffer.byteLength(label) > 63) throw new Error('Invalid DNS name');
    return Buffer.concat([Buffer.from([Buffer.byteLength(label)]), Buffer.from(label)]);
  });
  return Buffer.concat([header, ...labels, Buffer.from([0, 0, type, 0, 1])]);
}

function dnsAddresses(bytes: Buffer, type: number): LookupAddress[] {
  if (bytes.length < 12 || (bytes.readUInt16BE(2) & 0x800f) !== 0x8000) return [];
  let offset = 12;
  function skipName() {
    while (offset < bytes.length) {
      const size = bytes[offset++];
      if ((size & 0xc0) === 0xc0) { offset++; return; }
      if (size === 0) return;
      offset += size;
    }
    throw new Error('Malformed DNS response');
  }
  for (let i = 0; i < bytes.readUInt16BE(4); i++) { skipName(); offset += 4; }
  const addresses: LookupAddress[] = [];
  for (let i = 0; i < bytes.readUInt16BE(6); i++) {
    skipName();
    if (offset + 10 > bytes.length) throw new Error('Malformed DNS answer');
    const kind = bytes.readUInt16BE(offset);
    const length = bytes.readUInt16BE(offset + 8);
    offset += 10;
    if (offset + length > bytes.length) throw new Error('Malformed DNS answer length');
    if (kind === type && kind === 1 && length === 4) {
      addresses.push({ address: [...bytes.subarray(offset, offset + length)].join('.'), family: 4 });
    }
    if (kind === type && kind === 28 && length === 16) {
      const address = Array.from({ length: 8 }, (_, i) => bytes.readUInt16BE(offset + i * 2).toString(16)).join(':');
      addresses.push({ address, family: 6 });
    }
    offset += length;
  }
  return addresses;
}

function dohLookup(endpoint: URL): typeof lookup {
  return ((host: string, options: LookupOptions, callback: LookupCallback) => {
    const family = options.family === 4 || options.family === 'IPv4' ? 4 : options.family === 6 || options.family === 'IPv6' ? 6 : 0;
    const types = family === 4 ? [1] : family === 6 ? [28] : [1, 28];
    void Promise.allSettled(types.map(async type => {
      const url = new URL(endpoint);
      url.searchParams.set('dns', dnsQuery(host, type).toString('base64url'));
      const response = await fetch(url, { headers: { accept: 'application/dns-message' }, redirect: 'error', signal: AbortSignal.timeout(7000) });
      if (!response.ok) throw new Error('DoH lookup failed');
      const bytes = await readLimitedWebBody(response, 65535);
      return dnsAddresses(bytes, type);
    })).then(results => {
      const addresses = results.flatMap(result => result.status === 'fulfilled' ? result.value : []);
      if (!addresses.length) {
        callback(Object.assign(new Error('DoH lookup found no address'), { code: 'ENOTFOUND' }), '', 0);
      } else if (options.all) callback(null, addresses);
      else callback(null, addresses[0].address, addresses[0].family);
    });
  }) as typeof lookup;
}

async function readLimitedWebBody(response: Response, maxBytes: number): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let size = 0;
  const reader = response.body?.getReader();
  if (!reader) return Buffer.alloc(0);
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > maxBytes) throw new Error('Response body is too large');
      chunks.push(Buffer.from(value));
    }
    return Buffer.concat(chunks, size);
  } finally { await reader.cancel().catch(() => undefined); }
}

async function readBody(response: IncomingMessage): Promise<string> {
  const decompress = response.headers['content-encoding'] === 'gzip' ? createGunzip() :
    response.headers['content-encoding'] === 'deflate' ? createInflate() :
    response.headers['content-encoding'] === 'br' ? createBrotliDecompress() : null;
  if (decompress) response.on('error', error => decompress.destroy(error));
  const stream = decompress ? response.pipe(decompress) : response;
  const chunks: Buffer[] = [];
  let size = 0;
  try {
    for await (const chunk of stream) {
      size += chunk.length;
      if (size > maxBody) throw new Error('Response body is too large');
      chunks.push(chunk);
    }
    return Buffer.concat(chunks, size).toString('utf8');
  } finally { if (!response.complete) response.destroy(); }
}

export async function fetchText(
  url: URL,
  options: SubscriptionRequestOptions = {},
  policy: { headersOnly?: boolean; httpsOnly?: boolean; redirects?: number; signal?: AbortSignal } = {},
): Promise<{ body: string; headers: IncomingMessage['headers'] }> {
  const endpoint = options.dnsOverHTTPSURL ? webURL(options.dnsOverHTTPSURL, true) : null;
  const requester = url.protocol === 'https:' ? httpsRequest : httpRequest;
  const signal = policy.signal ?? AbortSignal.timeout(policy.headersOnly ? 15000 : 30000);
  const response = await new Promise<IncomingMessage>((resolve, reject) => {
    const req = requester(url, {
      signal, agent: false,
      headers: { 'User-Agent': cleanLine(options.userAgent || 'Tower/1.0'), 'Accept-Encoding': 'gzip, deflate, br' },
      lookup: endpoint && !isIP(url.hostname) ? dohLookup(endpoint) : undefined,
    }, resolve);
    req.on('error', reject);
    req.end();
  });
  const status = response.statusCode ?? 0;
  if ([301, 302, 303, 307, 308].includes(status) && response.headers.location) {
    response.destroy();
    const remaining = policy.redirects ?? 5;
    if (!remaining) throw new Error('Too many redirects');
    return fetchText(webURL(new URL(response.headers.location, url).href, policy.httpsOnly), options, { ...policy, redirects: remaining - 1, signal });
  }
  if (status < 200 || status >= 300) {
    response.destroy();
    throw new Error(`Server returned HTTP ${status}`);
  }
  if (policy.headersOnly) {
    response.destroy();
    return { body: '', headers: response.headers };
  }
  return { body: await readBody(response), headers: response.headers };
}

const header = (value: string | string[] | undefined) => Array.isArray(value) ? value[0] : value;

function planDetail(usage: SubscriptionUsage | null): boolean {
  return Boolean(usage && (usage.totalBytes != null || usage.expiresAt));
}
function quotaProbe(url: URL): URL | null {
  const fields = new Map([...url.searchParams].map(([key, value]) => [key.toLowerCase(), value]));
  const wrapped = fields.has('target') ? fields.get('url') : null;
  let result = new URL(url);
  if (wrapped && !wrapped.includes('|')) {
    try { result = webURL(wrapped); } catch { /* Probe the original subscription URL. */ }
  }
  if ([...result.searchParams.keys()].some(key => key.toLowerCase() === 'flag')) return result.href !== url.href ? result : null;
  result.searchParams.append('flag', 'clash');
  return result;
}

export async function fetchSubscription(urlString: string, options: SubscriptionRequestOptions = {}, sourceID?: string) {
  const url = webURL(urlString);
  const original = await fetchText(url, options);
  const parsed = parseSubscription(original.body, sourceID);
  if (!parsed.nodes.length) throw Object.assign(new Error('No supported nodes found'), {
    skippedCount: parsed.skippedCount, skippedReasons: parsed.skippedReasons,
  });
  const originalHeader = quotaFromText(header(original.headers['subscription-userinfo']) ?? '');
  let structured = originalHeader || parsed.status ? { ...parsed.status, ...originalHeader, notices: [] } : null;
  const notices = quotaFromNotices(parsed.notices);
  const probe = !planDetail(structured) && !planDetail(notices) ? quotaProbe(url) : null;
  if (probe) {
    try {
      const extra = await fetchText(probe, options, { headersOnly: true });
      const extraHeader = quotaFromText(header(extra.headers['subscription-userinfo']) ?? '');
      if (extraHeader) structured = { ...extraHeader, ...structured, notices: [] };
    } catch { /* A compatibility probe cannot invalidate the original response. */ }
  }
  const usage = structured ? { ...notices, ...structured, notices: parsed.notices } : notices;
  const title = header(original.headers['profile-title']);
  const base64 = title && /^base64:/i.test(title) ? base64Decode(title.slice(7)) : null;
  let name = title && !/^base64:/i.test(title) ? title : base64;
  if (name) {
    try { name = decodeURIComponent(name); } catch { /* Keep the original title. */ }
  }
  return { ...parsed, usage, name: name ? cleanLine(name).slice(0, 256) || null : null };
}
