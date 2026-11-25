import { randomUUID } from 'node:crypto';
import { isIP } from 'node:net';
import { load, JSON_SCHEMA } from 'js-yaml';
import { ALL_PROXY_KINDS, PROXY_KIND_LABELS } from '../../../shared/src/index.js';
import type { ProxyKind, ProxyNode, SourceDetectionResult, SubscriptionUsage } from '../../../shared/src/index.js';

export type Fields = Record<string, unknown>;
export type SkipReason = { reason: string; count: number };
export interface ParsedSubscription {
  nodes: ProxyNode[];
  skippedCount: number;
  skippedReasons: SkipReason[];
  notices: string[];
  status: SubscriptionUsage | null;
}

export const record = (value: unknown): Fields =>
  value && typeof value === 'object' && !Array.isArray(value) ? value as Fields : {};
export const str = (value: unknown): string | undefined =>
  typeof value === 'string' || typeof value === 'number' ? String(value) : undefined;
export const list = (value: unknown): string[] =>
  (Array.isArray(value) ? value : String(value ?? '').split(',')).map(item => String(item).trim()).filter(Boolean);
const bool = (value: unknown) => /^(true|1|yes)$/i.test(String(value));
const number = (value: unknown) => /^\d+(?:\.\d+)?(?:\s*mbps)?$/i.test(String(value)) ? parseFloat(String(value)) : undefined;
export const cleanLine = (value: string) => value.replace(/[\r\n\u0085\u2028\u2029]+/g, ' ').trim();

export function base64Decode(value: string): string | null {
  const input = value.replace(/\s/g, '');
  if (!/^[A-Za-z0-9+/_-]+={0,2}$/.test(input) || input.replace(/=+$/, '').length % 4 === 1) return null;
  try { return new TextDecoder('utf-8', { fatal: true }).decode(Buffer.from(input, 'base64')); }
  catch { return null; }
}

export function splitFields(value: string, separator = ','): string[] {
  const fields: string[] = [];
  let quoted = '';
  let start = 0;
  let depth = 0;
  for (let i = 0; i < value.length; i++) {
    const character = value[i];
    if (quoted) {
      if (character === '\\') i++;
      else if (character === quoted) quoted = '';
    } else if (character === '"' || character === "'") quoted = character;
    else if (character === '(') depth++;
    else if (character === ')') depth--;
    else if (character === separator && depth === 0) {
      fields.push(value.slice(start, i).trim());
      start = i + 1;
    }
  }
  fields.push(value.slice(start).trim());
  return fields;
}

export function iniSections(content: string): Map<string, string[]> {
  const sections = new Map<string, string[]>();
  let section = '';
  for (const raw of content.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || /^(?:#|;|\/\/)/.test(line)) continue;
    const header = /^\[([^\]]+)\]$/.exec(line);
    if (header) section = header[1].toLowerCase();
    else {
      const lines = sections.get(section) ?? [];
      lines.push(line);
      sections.set(section, lines);
    }
  }
  return sections;
}

function normalizedKind(value: string): ProxyKind {
  const kind = ({ 'shadowsocks': 'ss', 'shadowsocksr': 'ssr', 'hy': 'hysteria', 'hy2': 'hysteria2',
    'wg': 'wireguard', 'socks': 'socks5', 'socks5-tls': 'socks5', 'https': 'http', 'tuic-v5': 'tuic',
    'trust-tunnel': 'trusttunnel' } as Record<string, string>)[value] ?? value;
  return ALL_PROXY_KINDS.includes(kind as ProxyKind) ? kind as ProxyKind : 'unknown';
}

function normalizedTransport(value?: string): string | undefined {
  if (!value || /^(none|tcp|raw)$/i.test(value)) return undefined;
  return ({ websocket: 'ws', http2: 'h2', 'http-upgrade': 'httpupgrade', splithttp: 'xhttp' } as Record<string, string>)[value.toLowerCase()] ?? value.toLowerCase();
}

const noticeKeywords = /剩余流量|已用流量|总流量|流量重置|距离下次重置|重置剩余|套餐到期|到期时间|过期时间|有效期至|账户余额|官网|续费|客服|邮箱|订阅地址|机场地址|\b(?:expires?|traffic|remaining|reset)\b/i;
const placeholderHosts = new Set(['8.8.8.8', '8.8.4.4', '1.1.1.1', '1.0.0.1', '223.5.5.5', '127.0.0.1', '0.0.0.0', 'localhost', 'example.com']);
export function isSubscriptionMetadata(node: ProxyNode): boolean {
  return Boolean(node.isSubscriptionMetadata || noticeKeywords.test(node.name) || (node.sourceID && placeholderHosts.has(node.server.toLowerCase())));
}

const ignoredIdentity = new Set(['id', 'sourceID', 'rawURI', 'countryOverride', 'isSubscriptionMetadata']);
function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableValue);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).filter(([, v]) => v != null).sort(([a], [b]) => a.localeCompare(b))
      .map(([key, v]) => [key, stableValue(v)]));
  }
  return value;
}
export function canonicalKey(node: ProxyNode): string {
  return JSON.stringify(stableValue(Object.fromEntries(Object.entries(node)
    .filter(([key, value]) => !ignoredIdentity.has(key) && value !== '')
    .map(([key, value]) => [key, key === 'server' ? node.server.toLowerCase() : value]))));
}

class InvalidNode extends Error {}
function fail(message: string): never { throw new InvalidNode(message); }

export function validateNode(node: ProxyNode): ProxyNode {
  if (node.kind === 'unknown') fail('Unsupported proxy protocol');
  if (!node.server || (!isIP(node.server) && !/^[\p{L}\p{N}_.-]+$/u.test(node.server)) || node.server.startsWith('-') ||
      !Number.isInteger(node.port) || node.port < 1 || node.port > 65535) fail('Invalid proxy host or port');
  if (['ss', 'ssr'].includes(node.kind) && (!node.cipher || !node.password)) fail('Missing cipher or password');
  if (node.kind === 'ssr' && (!node.protocolName || !node.obfs)) fail('Missing ShadowsocksR protocol or obfuscation');
  if (['vmess', 'vless', 'tuic'].includes(node.kind) && (!node.uuid || (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(node.uuid) && Buffer.byteLength(node.uuid) >= 32))) {
    fail('Missing or invalid proxy ID');
  }
  if (['trojan', 'hysteria2', 'tuic', 'anytls', 'snell', 'trusttunnel'].includes(node.kind) && !node.password) fail('Missing proxy password');
  if (node.kind === 'snell' && node.version && ![1, 2, 3, 4, 5, 6].includes(node.version)) fail('Invalid Snell version');
  if (node.kind === 'ssh' && (!node.username || (!node.password && !node.ssh?.privateKey))) fail('Missing SSH credentials');
  if (node.kind === 'trusttunnel' && !node.username) fail('Missing TrustTunnel username');
  if (node.kind === 'wireguard' && (!node.wireGuardPrivateKey || !node.wireGuardPublicKey ||
      !(node.wireGuardIPv4 || node.wireGuardIPv6))) fail('Missing WireGuard credentials or address');
  if (node.kind === 'masque' && (node.masque?.mode === 'keyConnectIP' ?
      !node.masque.privateKey || !node.masque.publicKey || !(node.masque.ipv4 || node.masque.ipv6) :
      !node.username || !node.password)) fail('Missing MASQUE credentials or address');
  if (node.transport && !['ws', 'http', 'h2', 'grpc', 'httpupgrade', 'xhttp', 'quic', 'kcp'].includes(node.transport)) {
    fail('Unsupported proxy transport');
  }
  if (node.kind === 'ss' && node.plugin && !/^(?:obfs(?:-local)?|simple-obfs|v2ray-plugin|shadow-tls)(?:;|$)/i.test(node.plugin)) fail('Unsupported Shadowsocks SIP003 plugin');
  if (node.realityPublicKey && !node.tls) fail('REALITY requires TLS');
  if (node.shadowTLS && (!node.shadowTLS.password || !node.shadowTLS.host || ![1, 2, 3].includes(node.shadowTLS.version))) fail('Invalid ShadowTLS credentials');
  const integers = [node.alterID, node.idleSessionCheckInterval, node.idleSessionTimeout, node.minIdleSession, node.version,
    node.wireGuardMTU, node.wireGuardPersistentKeepalive, node.masque?.mtu, node.masque?.httpVersion];
  if (integers.some(value => value != null && (!Number.isSafeInteger(value) || value < 0))) fail('Invalid numeric proxy settings');
  return node;
}

function makeNode(kind: ProxyKind, server: unknown, port: unknown, name: unknown, rawURI: string, sourceID?: string): ProxyNode {
  const host = str(server)?.replace(/^\[|\]$/g, '').trim() ?? '';
  return {
    id: randomUUID(), kind, sourceID, server: host, port: Number(port),
    name: cleanLine(str(name) ?? '') || `${PROXY_KIND_LABELS[kind]} · ${host}`,
    tls: false, skipCertificateVerification: false, rawURI,
  };
}

const decode = (value: string) => decodeURIComponent(value);
function decodePlugin(raw: string, node: ProxyNode) {
  const [plugin, ...options] = raw.split(/(?<!\\);/);
  const fields = Object.fromEntries(options.map(item => {
    const equals = item.indexOf('=');
    return [equals < 0 ? item : item.slice(0, equals), equals < 0 ? 'true' : item.slice(equals + 1).replace(/\\([;=\\])/g, '$1')];
  }));
  if (/^(?:obfs(?:-local)?|simple-obfs)$/i.test(plugin) && ['http', 'tls'].includes(fields.obfs)) {
    if (Object.keys(fields).some(key => !['obfs', 'obfs-host'].includes(key))) fail('Unsupported simple-obfs options');
    Object.assign(node, { plugin: raw, obfs: fields.obfs, hostHeader: fields['obfs-host'] });
  } else if (plugin === 'v2ray-plugin' && (!fields.mode || fields.mode === 'websocket')) {
    if (Object.keys(fields).some(key => !['mode', 'host', 'path', 'tls', 'mux', 'skip-cert-verify'].includes(key))) fail('Unsupported v2ray-plugin options');
    Object.assign(node, { plugin: raw, transport: 'ws', tls: bool(fields.tls), hostHeader: fields.host, path: fields.path,
      pluginMux: fields.mux == null ? undefined : bool(fields.mux), skipCertificateVerification: bool(fields['skip-cert-verify']) });
  } else if (plugin === 'shadow-tls' && fields.host && fields.password && [1, 2, 3].includes(Number(fields.version))) {
    node.shadowTLS = { version: Number(fields.version), host: fields.host, password: fields.password,
      skipCertificateVerification: bool(fields['skip-cert-verify']) };
  } else fail('Unsupported Shadowsocks SIP003 plugin');
}

function parseSS(raw: string, sourceID?: string): ProxyNode {
  const [, body] = /^ss:\/\/(.*)$/i.exec(raw) ?? [];
  const [link, fragment] = body.split('#', 2);
  const [authority, query] = link.split('?', 2);
  const decoded = authority.includes('@') ? authority : base64Decode(authority);
  const match = decoded && /^(.*)@([^@]+?)\/?$/.exec(decoded);
  if (!match) return fail('Malformed Shadowsocks link');
  const credential = match[1].includes(':') ? decode(match[1]) : base64Decode(decode(match[1]));
  const endpoint = /^(.+):(\d+)$/.exec(match[2]);
  if (!credential || !endpoint || !credential.includes(':')) return fail('Malformed Shadowsocks credentials');
  const colon = credential.indexOf(':');
  const node = makeNode('ss', endpoint[1], endpoint[2], fragment && decode(fragment), raw, sourceID);
  node.cipher = credential.slice(0, colon).toLowerCase();
  node.password = credential.slice(colon + 1);
  const plugin = new URLSearchParams(query).get('plugin');
  if (plugin) decodePlugin(plugin, node);
  return node;
}

function parseSSR(raw: string, sourceID?: string): ProxyNode {
  const decoded = base64Decode(raw.slice(6));
  const match = decoded && /^(.*):(\d+):([^:]+):([^:]+):([^:]+):([^/?]+)(?:\/\?(.*))?$/.exec(decoded);
  if (!match) return fail('Malformed ShadowsocksR link');
  const query = new URLSearchParams(match[7]);
  const field = (name: string) => base64Decode(query.get(name) ?? '') ?? undefined;
  return {
    ...makeNode('ssr', match[1], match[2], field('remarks'), raw, sourceID),
    protocolName: match[3], cipher: match[4], obfs: match[5], password: base64Decode(match[6]),
    protocolParam: field('protoparam'), obfsParam: field('obfsparam'),
  };
}

function parseVMess(raw: string, sourceID?: string): ProxyNode | null {
  const decoded = base64Decode(raw.slice(8).split(/[?#]/)[0]);
  if (!decoded?.startsWith('{')) return null;
  const p = record(JSON.parse(decoded));
  return {
    ...makeNode('vmess', p.add, p.port, p.ps, raw, sourceID), uuid: str(p.id), cipher: str(p.scy),
    alterID: number(p.aid), transport: normalizedTransport(p.type === 'http' && p.net === 'tcp' ? 'http' : str(p.net)),
    tls: p.tls === 'tls' || bool(p.tls), sni: str(p.sni), hostHeader: str(p.host), path: str(p.path),
    alpn: list(p.alpn).join(',') || undefined, fingerprint: str(p.fp), skipCertificateVerification: bool(p.allowInsecure),
  };
}

function legacyV2Link(raw: string, kind: 'vmess' | 'vless', sourceID?: string): ProxyNode | null {
  const scheme = raw.indexOf('://');
  const match = /^([^?#]+)([^#]*)(#.*)?$/.exec(raw.slice(scheme + 3));
  if (!match || match[1].includes('@')) return null;
  const decoded = base64Decode(match[1]);
  const endpoint = decoded && /^(.*)@(\[[\da-f:.]+\]|[^:]+):(\d+)$/i.exec(decoded);
  if (!endpoint) return null;
  const [cipher, uuid] = endpoint[1].includes(':') ? endpoint[1].split(':', 2) : ['', endpoint[1]];
  if (kind === 'vless' && cipher && !['auto', 'none'].includes(cipher.toLowerCase())) fail('Unsupported legacy VLESS encryption');
  const fields = new URLSearchParams(match[2]);
  const obfs = fields.get('obfs')?.toLowerCase();
  if (obfs && !fields.has('type')) fields.set('type', obfs === 'wss' ? 'ws' : obfs);
  if (obfs === 'wss' && !fields.has('tls') && !fields.has('security')) fields.set('tls', '1');
  if (!fields.has('host') && fields.has('obfsParam')) {
    const rawHost = fields.get('obfsParam')!;
    let value = rawHost;
    if (rawHost.startsWith('{')) {
      const data = record(JSON.parse(rawHost));
      value = str(data.Host ?? data.host ?? record(data.headers).Host ?? record(data.headers).host) ?? '';
    }
    if (value) fields.set('host', value);
  }
  const uri = `${kind}://${encodeURIComponent(uuid)}@${endpoint[2]}:${endpoint[3]}?${fields}${match[3] ?? ''}`;
  const node = parseStandardURI(uri, kind, sourceID);
  if (kind === 'vmess' && cipher) node.cipher = cipher;
  return { ...node, rawURI: raw };
}

const amnezia = /^(?:jc|jmin|jmax|s[1-4]|h[1-4]|i[1-5]|amnezia(?:-wg)?|awg)$/i;
function parseStandardURI(raw: string, kind: ProxyKind, sourceID?: string): ProxyNode {
  let input = raw;
  let hopping: string | undefined;
  if (kind === 'hysteria2') {
    input = raw.replace(/(:)(\d+(?:[-,]\d+)+)(?=[/?#]|$)/, (_, colon: string, ports: string) => {
      hopping = ports;
      return colon + ports.split(/[-,]/)[0];
    });
  }
  const url = new URL(input);
  const fields = new Map([...url.searchParams].map(([key, value]) => [key.toLowerCase(), value]));
  const q = (...names: string[]) => names.map(name => fields.get(name)).find(value => value !== undefined);
  const user = decode(url.username);
  const password = decode(url.password);
  const port = url.port || ({ http: url.protocol === 'https:' ? 443 : 80, wireguard: 51820, ssh: 22, socks5: 1080 } as Partial<Record<ProxyKind, number>>)[kind];
  const node = makeNode(kind, decode(url.hostname), port, decode(url.hash.slice(1)) || q('remarks', 'remark', 'name', 'tag'), raw, sourceID);
  const security = q('security', 'tls') ?? '';
  const reality = security === 'reality' || Boolean(q('pbk'));
  if (reality && !q('pbk')) fail('Missing REALITY public key');
  const type = q('type', 'network');
  const transport = normalizedTransport(type === 'tcp' && q('headertype') === 'http' ? 'http' : type);
  if (transport === 'xhttp' && !['', '{}', 'null'].includes(q('extra')?.trim() ?? '')) fail('Unsupported XHTTP extra options');
  if (q('plugin')) fail('Unsupported URI plugin');
  Object.assign(node, {
    transport, transportMode: transport === 'xhttp' ? q('mode') : undefined,
    tls: reality || url.protocol === 'https:' || ['tls', '1', 'true', 'xtls'].includes(security) ||
      ['trojan', 'hysteria', 'hysteria2', 'tuic', 'masque', 'anytls', 'trusttunnel'].includes(kind),
    sni: q('sni', 'servername', 'peer'), hostHeader: q('host', 'authority'),
    path: transport === 'grpc' ? q('servicename', 'service_name', 'path') : q('path'),
    alpn: [...fields].filter(([key]) => /^alpn(?:\[\d*\])?$/.test(key)).flatMap(([, value]) => list(value)).join(',') || undefined,
    realityPublicKey: reality ? q('pbk') : undefined, realityShortID: reality ? q('sid') : undefined,
    realitySupportsX25519MLKEM768: q('support-x25519mlkem768') ? bool(q('support-x25519mlkem768')) : undefined,
    fingerprint: q('fp', 'client-fingerprint', 'client_fingerprint') ?? (reality ? q('fingerprint') : undefined),
    certificateFingerprint: q('pinsha256', 'pin-sha256', 'tls-fingerprint', 'pcs'),
    skipCertificateVerification: bool(q('allowinsecure', 'allow_insecure', 'insecure', 'skip-cert-verify')),
  });
  if (['vmess', 'vless', 'tuic'].includes(kind)) {
    node.uuid = user || q('uuid');
    node.flow = q('flow');
  }
  switch (kind) {
    case 'vmess':
      Object.assign(node, { cipher: q('cipher', 'scy'), alterID: number(q('alterid', 'aid')) });
      break;
    case 'vless': node.vlessEncryption = q('encryption') === 'none' ? undefined : q('encryption'); break;
    case 'trojan':
    case 'hysteria2':
    case 'anytls':
    case 'snell':
      node.password = user || q('password', 'psk');
      break;
    case 'hysteria': node.password = user || q('auth', 'auth_str', 'authstr'); break;
    case 'tuic': node.password = password || q('password'); break;
    case 'wireguard': {
      if ([...fields.keys()].some(key => amnezia.test(key))) fail('Unsupported AmneziaWG options');
      const addresses = list(q('address', 'addresses'));
      Object.assign(node, {
        wireGuardPrivateKey: user || q('private-key'), wireGuardPublicKey: q('publickey', 'public-key'),
        wireGuardPreSharedKey: q('presharedkey', 'pre-shared-key', 'preshared-key'),
        wireGuardIPv4: addresses.find(address => !address.includes(':')), wireGuardIPv6: addresses.find(address => address.includes(':')),
        wireGuardAllowedIPs: q('allowedips', 'allowed-ips'), wireGuardReserved: q('reserved'), wireGuardMTU: number(q('mtu')),
        wireGuardPersistentKeepalive: number(q('keepalive', 'persistent-keepalive')), wireGuardDNS: q('dns', 'dns-server'),
      });
      break;
    }
    default:
      node.username = user || q('username');
      node.password = password || q('password');
  }
  if (kind === 'hysteria' || kind === 'hysteria2') {
    Object.assign(node, { portHopping: hopping ?? q('mport', 'ports', 'server-ports', 'port-hopping'),
      obfs: kind === 'hysteria' ? q('obfsparam', 'obfs-password', 'obfs') : q('obfs'), obfsParam: q('obfs-password', 'obfspassword', 'obfs_password'),
      protocolName: q('protocol'), upMbps: number(q('upmbps', 'up')), downMbps: number(q('downmbps', 'down')) });
  }
  if (kind === 'tuic') Object.assign(node, { congestionControl: q('congestion_control', 'congestion-controller'), udpRelayMode: q('udp_relay_mode', 'udp-relay-mode') });
  if (kind === 'anytls') Object.assign(node, { idleSessionCheckInterval: number(q('idle-session-check-interval', 'idlesessioncheckinterval')),
    idleSessionTimeout: number(q('idle-session-timeout', 'idlesessiontimeout')), minIdleSession: number(q('min-idle-session', 'minidlesession')) });
  if (kind === 'snell') Object.assign(node, { version: number(q('version')) ?? 4, obfs: q('obfs'), obfsParam: q('obfs-host') });
  if (kind === 'ssh') node.ssh = { privateKey: q('private-key'), privateKeyPassphrase: q('private-key-passphrase'), hostKeys: list(q('host-key')), hostKeyAlgorithms: list(q('host-key-algorithms')) };
  if (kind === 'trusttunnel') node.trustTunnel = { quic: bool(q('quic', 'h3')), clientRandomAuthKey: q('client-random-auth-key') };
  if (kind === 'masque') {
    node.masque = { mode: q('private-key') ? 'keyConnectIP' : 'basicConnectIP',
      privateKey: q('private-key'), publicKey: q('public-key'), ipv4: q('ip', 'ipv4'), ipv6: q('ipv6'),
      network: q('network'), mtu: number(q('mtu')), dns: list(q('dns')) };
    if (transport && kind === 'masque') node.transport = undefined;
  }
  return node;
}

function uriNode(raw: string, sourceID?: string): ProxyNode {
  const scheme = /^([\w+-]+):\/\//i.exec(raw)?.[1].toLowerCase();
  if (!scheme) return fail('Unrecognized node link');
  const kind = normalizedKind(scheme);
  switch (kind) {
    case 'ss': return parseSS(raw, sourceID);
    case 'ssr': return parseSSR(raw, sourceID);
    case 'vmess': return parseVMess(raw, sourceID) ?? legacyV2Link(raw, kind, sourceID) ?? parseStandardURI(raw, kind, sourceID);
    case 'vless': return legacyV2Link(raw, kind, sourceID) ?? parseStandardURI(raw, kind, sourceID);
    case 'unknown': return fail('Unsupported proxy protocol');
    default: return parseStandardURI(raw, kind, sourceID);
  }
}

export function parseProxyURI(raw: string, sourceID?: string): ProxyNode | null {
  try { return validateNode(uriNode(raw.trim(), sourceID)); }
  catch { return null; }
}

function clashNode(value: unknown, raw: string, sourceID?: string): ProxyNode {
  const p = record(value);
  const kind = normalizedKind(String(p.type ?? '').toLowerCase());
  const reality = record(p['reality-opts']);
  const ws = record(p['ws-opts']);
  const grpc = record(p['grpc-opts']);
  const http = record(p['http-opts']);
  const h2 = record(p['h2-opts']);
  const xhttp = record(p['xhttp-opts']);
  const upgrade = record(p['http-upgrade-opts']);
  const network = normalizedTransport(str(p.network));
  if (p['dialer-proxy'] || record(p.smux).enabled === true || record(p['ech-opts']).enable === true || p['udp-over-tcp'] === true) {
    fail('Unsupported proxy chaining, multiplexing, or transport options');
  }
  const headers = { ...record(ws.headers), ...record(http.headers), ...record(upgrade.headers), ...record(xhttp.headers) };
  const hostHeader = Object.entries(headers).find(([key]) => key.toLowerCase() === 'host')?.[1];
  if (Object.keys(headers).some(key => key.toLowerCase() !== 'host')) fail('Unsupported transport headers');
  if (network === 'xhttp' && Object.keys(xhttp).some(key => !['path', 'host', 'headers', 'mode'].includes(key))) fail('Unsupported XHTTP extra options');
  const node: ProxyNode = {
    ...makeNode(kind, p.server, p.port, p.name, raw, sourceID), cipher: str(p.cipher ?? p.method),
    uuid: str(p.uuid), password: str(p.password ?? p.psk ?? p['auth-str'] ?? p.auth), username: str(p.username ?? p.user),
    transport: network, transportMode: str(xhttp.mode), tls: ['trojan', 'hysteria', 'hysteria2', 'tuic', 'anytls', 'masque', 'trusttunnel'].includes(kind) || bool(p.tls) || p.type === 'https' || p.type === 'socks5-tls' || !!reality['public-key'],
    sni: str(p.servername ?? p.sni), hostHeader: list(hostHeader ?? ws.host ?? h2.host ?? xhttp.host ?? grpc.authority)[0],
    path: list(grpc['grpc-service-name'] ?? ws.path ?? http.path ?? h2.path ?? xhttp.path ?? upgrade.path)[0],
    alpn: list(p.alpn).join(',') || undefined, skipCertificateVerification: bool(p['skip-cert-verify']),
    realityPublicKey: str(reality['public-key']), realityShortID: str(reality['short-id']),
    realitySupportsX25519MLKEM768: reality['support-x25519mlkem768'] == null ? undefined : bool(reality['support-x25519mlkem768']),
    fingerprint: str(p['client-fingerprint']), certificateFingerprint: str(p.fingerprint ?? p['tls-fingerprint']), flow: str(p.flow),
    vlessEncryption: p.encryption === 'none' ? undefined : str(p.encryption), alterID: number(p.alterId ?? p.alterID ?? p.alterid),
    protocolName: str(p.protocol), protocolParam: str(p['protocol-param']), obfs: str(p.obfs), obfsParam: str(p['obfs-param'] ?? p['obfs-password']),
    idleSessionCheckInterval: number(p['idle-session-check-interval']), idleSessionTimeout: number(p['idle-session-timeout']),
    minIdleSession: number(p['min-idle-session']), version: number(p.version), congestionControl: str(p['congestion-controller']),
    udpRelayMode: str(p['udp-relay-mode']), udpRelayEnabled: p.udp == null ? undefined : bool(p.udp),
    portHopping: list(p.ports ?? p['server-ports']).join(',') || undefined, upMbps: number(p.up ?? p['up-speed']), downMbps: number(p.down ?? p['down-speed']),
  };
  if (number(ws['max-early-data'])) {
    const header = str(ws['early-data-header-name']);
    if (header && header.toLowerCase() !== 'sec-websocket-protocol') fail('Unsupported WebSocket early-data header');
    node.path = (node.path || '/') + (node.path?.includes('?') ? '&ed=' : '?ed=') + number(ws['max-early-data']);
  }
  if (p.plugin != null) {
    if (!['ss', 'snell'].includes(kind) || (kind === 'snell' && p.plugin !== 'shadow-tls')) fail('Unsupported SIP003 plugin');
    const simple = /^(?:obfs(?:-local)?|simple-obfs)$/i.test(String(p.plugin));
    const aliases: Record<string, string> = simple ? { mode: 'obfs', host: 'obfs-host' } : {};
    const options = Object.entries(record(p['plugin-opts'])).map(([key, val]) => `${aliases[key] ?? key}=${String(val).replace(/[;\\]/g, '\\$&')}`);
    decodePlugin([String(p.plugin), ...options].join(';'), node);
  }
  if (kind === 'wireguard') {
    if (Object.keys(p).some(key => amnezia.test(key))) fail('Unsupported AmneziaWG options');
    const peers = Array.isArray(p.peers) ? p.peers : [];
    if (peers.length > 1) fail('Unsupported WireGuard configuration with multiple peers');
    const peer = record(peers[0]);
    Object.assign(node, {
      server: str(peer.server) ?? node.server, port: number(peer.port) ?? node.port,
      wireGuardPrivateKey: str(p['private-key']), wireGuardPublicKey: str(peer['public-key'] ?? p['public-key']),
      wireGuardPreSharedKey: str(peer['pre-shared-key'] ?? p['pre-shared-key']), wireGuardIPv4: str(p.ip), wireGuardIPv6: str(p.ipv6),
      wireGuardMTU: number(p.mtu), wireGuardDNS: list(p.dns).join(',') || undefined, wireGuardReserved: list(peer.reserved ?? p.reserved).join(',') || undefined,
      wireGuardAllowedIPs: list(peer['allowed-ips'] ?? p['allowed-ips']).join(',') || undefined,
      wireGuardPersistentKeepalive: number(peer['persistent-keepalive'] ?? p['persistent-keepalive']),
    });
  }
  if (kind === 'ssh') node.ssh = { privateKey: str(p['private-key']), privateKeyPassphrase: str(p['private-key-passphrase']),
    hostKeys: list(p['host-key']), hostKeyAlgorithms: list(p['host-key-algorithms']) };
  if (kind === 'trusttunnel') node.trustTunnel = { quic: bool(p.quic), clientRandomAuthKey: str(p['client-random-auth-key']) };
  if (kind === 'masque') {
    node.transport = undefined;
    node.masque = { mode: p['private-key'] ? 'keyConnectIP' : 'basicConnectIP', privateKey: str(p['private-key']),
      publicKey: str(p['public-key']), ipv4: str(p.ip), ipv6: str(p.ipv6), mtu: number(p.mtu), dns: list(p.dns),
      network: str(p.network), remoteDNSResolve: p['remote-dns-resolve'] == null ? undefined : bool(p['remote-dns-resolve']),
      httpVersion: number(p['http-version']), disableVersionFallback: p['disable-version-fallback'] == null ? undefined : bool(p['disable-version-fallback']),
      headers: Object.fromEntries(Object.entries(record(p.headers)).map(([key, item]) => [key, String(item)])), certificate: list(p.certificate) };
  }
  return node;
}

const unquote = (value: string) => /^"(?:[^"\\]|\\.)*"$/.test(value) ? JSON.parse(value) as string : value.replace(/^'|'$/g, '');
function surgeNode(line: string, sourceID?: string, sections?: Map<string, string[]>): ProxyNode | null {
  const match = /^([^=]+)=(.*)$/.exec(line);
  if (!match) return fail('Malformed Surge proxy line');
  const [type, server, port, ...fields] = splitFields(match[2]);
  if (['direct', 'reject', 'reject-tinygif', 'reject-drop', 'reject-no-drop'].includes(type.toLowerCase())) return null;
  const kind = normalizedKind(type.toLowerCase());
  const options = kind === 'wireguard' && server?.startsWith('section-name=') ? [server, ...(port ? [port] : []), ...fields] : fields;
  const opts = Object.fromEntries(options.filter(field => /^[\w-]+\s*=/.test(field)).map(field => {
    const i = field.indexOf('=');
    return [field.slice(0, i).trim().toLowerCase(), unquote(field.slice(i + 1).trim())];
  }));
  if (kind === 'wireguard' && opts['section-name']) {
    const settings = sections?.get('wireguard ' + opts['section-name'].toLowerCase()) ?? [];
    const config = Object.fromEntries(settings.flatMap(entry => {
      const pair = /^([^=]+)=(.*)$/.exec(entry);
      return pair ? [[pair[1].trim().toLowerCase(), pair[2].trim()]] : [];
    }));
    if (settings.filter(entry => /^peer\s*=/i.test(entry)).length > 1) fail('Unsupported WireGuard configuration with multiple peers');
    const peer = Object.fromEntries(splitFields((config.peer ?? '').replace(/^\(|\)$/g, '')).flatMap(entry => {
      const pair = /^([^=]+)=(.*)$/.exec(entry);
      return pair ? [[pair[1].trim().toLowerCase(), unquote(pair[2].trim())]] : [];
    }));
    const address = /^(.+):(\d+)$/.exec(peer.endpoint ?? '');
    if (!address) return fail('Missing WireGuard peer endpoint');
    return clashNode({ name: match[1], type, server: address[1], port: address[2],
      'private-key': config['private-key'], 'public-key': peer['public-key'], 'pre-shared-key': peer['preshared-key'],
      ip: config['self-ip'], ipv6: config['self-ip-v6'], 'allowed-ips': peer['allowed-ips'], dns: config['dns-server'],
      mtu: config.mtu, reserved: peer['client-id']?.replaceAll('/', ','), 'persistent-keepalive': peer.keepalive,
    }, line, sourceID);
  }
  const positional = fields.filter(field => !/^[\w-]+\s*=/.test(field)).map(unquote);
  const node = clashNode({
    ...opts, type, server, port, name: match[1].trim(), cipher: opts['encrypt-method'] ?? opts.method,
    tls: opts.tls, sni: opts.sni, uuid: opts.uuid ?? (['vmess', 'vless'].includes(kind) ? opts.username : undefined),
    username: opts.username ?? positional[0], password: opts.password ?? opts.psk ?? positional[1], network: bool(opts['ws-http-upgrade']) ? 'httpupgrade' :
      bool(opts.ws) ? 'ws' : bool(opts['grpc']) ? 'grpc' : bool(opts.xhttp) ? 'xhttp' : opts.transport,
    'ws-opts': { path: opts['ws-path'], headers: opts['ws-headers']?.startsWith('Host:') ? { Host: opts['ws-headers'].slice(5).trim() } : {} },
    'grpc-opts': { 'grpc-service-name': opts['grpc-service-name'], authority: opts['grpc-authority'] },
    'xhttp-opts': Object.fromEntries(['path', 'host', 'mode'].flatMap(key => opts['xhttp-' + key] == null ? [] : [[key, opts['xhttp-' + key]]])),
    'reality-opts': { 'public-key': opts['reality-public-key'], 'short-id': opts['reality-short-id'],
      'support-x25519mlkem768': opts['support-x25519mlkem768'] },
    'skip-cert-verify': opts['skip-cert-verify'], 'server-ports': opts['port-hopping'], 'udp': opts['udp-relay'],
    'client-fingerprint': opts['tls-fingerprint'], 'tls-fingerprint': opts['server-cert-fingerprint-sha256'],
    flow: opts['vless-flow'], 'obfs-password': opts['salamander-password'], obfs: opts['salamander-password'] ? 'salamander' : opts.obfs,
    quic: opts.h3 ?? opts.quic,
    ip: opts.ip, ipv6: opts.ipv6,
  }, line, sourceID);
  if (kind === 'vmess' || kind === 'vless') node.username = undefined;
  if (opts['shadow-tls-password'] || opts['shadow-tls-sni']) {
    const host = opts['shadow-tls-sni'];
    const password = opts['shadow-tls-password'];
    const version = Number(opts['shadow-tls-version'] ?? 2);
    if (!host || !password || ![1, 2, 3].includes(version)) fail('Invalid ShadowTLS options');
    node.shadowTLS = { version, host, password };
  }
  if (kind === 'ss' && opts.obfs) {
    if (!['http', 'tls', 'ws', 'wss'].includes(opts.obfs)) fail('Unsupported Shadowsocks obfuscation');
    node.hostHeader = opts['obfs-host'];
    if (opts.obfs.startsWith('ws')) {
      node.transport = 'ws';
      node.path = opts['obfs-uri'];
      node.tls = opts.obfs === 'wss';
    }
  }
  if (opts['underlying-proxy'] || opts['proxy-chain']) fail('Unsupported chained proxy');
  return node;
}

function bytes(text: string): number | undefined {
  const match = /(\d+(?:\.\d+)?)\s*([KMGTPE]i?B|B)\b/i.exec(text);
  return match ? Math.min(Number.MAX_SAFE_INTEGER, Math.round(Number(match[1]) * 1024 ** 'BKMGTPE'.indexOf(match[2][0].toUpperCase()))) : undefined;
}

function expiry(text: string): string | undefined {
  const date = /\b(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})(?:[ T](\d\d:\d\d(?::\d\d)?))?/.exec(text);
  if (!date) return undefined;
  const time = Date.parse(`${date[1]}-${date[2].padStart(2, '0')}-${date[3].padStart(2, '0')}T${date[4] ?? '00:00:00'}Z`);
  return Number.isFinite(time) ? new Date(time).toISOString() : undefined;
}

function usageFields(uploadBytes?: number, downloadBytes?: number, totalBytes?: number, expiresAt?: string): SubscriptionUsage | null {
  if (totalBytes === 1 && !expiresAt) return null;
  return uploadBytes != null || downloadBytes != null || totalBytes != null || expiresAt ? {
    ...(uploadBytes != null ? { uploadBytes } : {}), ...(downloadBytes != null ? { downloadBytes } : {}),
    ...(totalBytes != null ? { totalBytes } : {}), ...(expiresAt ? { expiresAt } : {}), notices: [],
  } : null;
}

export function quotaFromText(text: string): SubscriptionUsage | null {
  const values = Object.fromEntries([...text.matchAll(/(?:^|[\s,;&])([a-z_]+)\s*=\s*(\d+(?:\.\d+)?(?:e[+-]?\d+)?)(?=$|[\s,;&])/gi)].map(match => [match[1].toLowerCase(), Number(match[2])]));
  const valid = (key: string) => Number.isFinite(values[key]) ? Math.min(Math.trunc(values[key]), Number.MAX_SAFE_INTEGER) : undefined;
  const expire = valid('expire');
  const epoch = expire && expire < 8640000000000 ? new Date(expire * 1000).toISOString() : undefined;
  return usageFields(valid('upload'), valid('download'), valid('total'), epoch);
}

function quotaFromStatus(text: string): SubscriptionUsage | null {
  const usage = quotaFromText(text);
  if (usage) return usage;
  const value = (key: string) => bytes(new RegExp(key + '\\s*[:：]\\s*([\\d.]+\\s*[A-Za-z]+)', 'i').exec(text)?.[1] ?? '');
  return usageFields(value('↑'), value('↓'), value('TOT'), /EXPIRES\s*[:：]/i.test(text) ? expiry(text) : undefined);
}

export function quotaFromNotices(notices: string[]): SubscriptionUsage | null {
  if (!notices.length) return null;
  const usage: SubscriptionUsage = { notices };
  let remaining: number | undefined;
  for (const text of notices) {
    if (/总流量|total/i.test(text)) usage.totalBytes ??= bytes(text);
    if (/已用流量|used/i.test(text)) usage.downloadBytes ??= bytes(text);
    if (/剩余流量|remaining/i.test(text)) remaining ??= bytes(text);
    if (/到期|过期|有效期至|expir/i.test(text)) usage.expiresAt ??= expiry(text);
  }
  if (remaining != null && usage.totalBytes != null) usage.downloadBytes ??= Math.max(0, usage.totalBytes - remaining);
  return usage;
}

function bodyText(input: string): string {
  const text = input.replace(/^\uFEFF/, '').trim();
  const decoded = base64Decode(text);
  return decoded && /:\/\/|^\s*(?:proxies\s*:|\[Proxy\]|STATUS=)/im.test(decoded) ? decoded.trim() : text;
}

export function parseSubscription(input: string, sourceID?: string): ParsedSubscription {
  const text = bodyText(input);
  const seen = new Set<string>();
  const notices = new Set<string>();
  const nodes: ProxyNode[] = [];
  const skipped = new Map<string, number>();
  let status: SubscriptionUsage | null = null;
  const skip = (reason: string) => skipped.set(reason, (skipped.get(reason) ?? 0) + 1);
  const add = (parse: () => ProxyNode | null) => {
    try {
      const node = parse();
      if (!node) return;
      node.isSubscriptionMetadata = isSubscriptionMetadata(node);
      if (node.isSubscriptionMetadata) notices.add(node.name);
      validateNode(node);
      const key = canonicalKey(node);
      if (!seen.has(key)) {
        seen.add(key);
        nodes.push(node);
      }
    } catch (error) { skip(error instanceof InvalidNode ? error.message : 'Malformed proxy record'); }
  };
  if (/^\s*proxies\s*:/m.test(text) || /^\{\s*["']proxies["']/.test(text)) {
    try {
      const proxies = record(load(text, { schema: JSON_SCHEMA })).proxies;
      if (Array.isArray(proxies)) proxies.forEach(proxy => add(() => clashNode(proxy, '', sourceID)));
      else skip('Invalid Clash proxies list');
    } catch { skip('Invalid Clash YAML'); }
  } else if (/^\s*\[proxy\]\s*$/im.test(text)) {
    const sections = iniSections(text);
    for (const line of sections.get('proxy') ?? []) add(() => surgeNode(line, sourceID, sections));
  } else {
    for (const line of text.split(/\r?\n/).map(line => line.trim()).filter(Boolean)) {
      if (/^STATUS=/i.test(line)) { status ??= quotaFromStatus(line.slice(7)); continue; }
      if (/^[a-z][\w+-]*:\/\//i.test(line)) add(() => uriNode(line, sourceID));
      else if (/^\s*[^=]+=\s*snell\s*,/i.test(line)) add(() => surgeNode(line, sourceID));
      else if (noticeKeywords.test(line)) notices.add(cleanLine(line.replace(/^#\s*/, '')));
      else if (!/^(?:#|;|\/\/)/.test(line)) skip('Unrecognized node line');
    }
  }
  const skippedReasons = [...skipped].map(([reason, count]) => ({ reason, count }));
  return { nodes, skippedCount: skippedReasons.reduce((sum, item) => sum + item.count, 0), skippedReasons, notices: [...notices], status };
}

export function detectSourceInput(input: string): SourceDetectionResult {
  const text = bodyText(input);
  const parsed = parseSubscription(text);
  const count = parsed.nodes.length;
  if (/^\s*proxies\s*:/m.test(text)) return { kind: 'clashYAML', summary: 'Clash YAML', detectedNodeCount: count };
  if (/^\s*\[proxy\]\s*$/im.test(text)) return { kind: 'surgeConfig', summary: 'Surge 配置', detectedNodeCount: count };
  if (/^\s*(?:ruleset\s*=|custom_proxy_group\s*=|proxy-groups\s*:|\[rule\])/im.test(text)) return { kind: 'ruleScheme', summary: '规则方案' };
  if (/^https?:\/\/\S+$/i.test(text)) {
    try {
      const url = new URL(text);
      const explicitPort = /^https?:\/\/(?:\[[^\]]+\]|[^/:?#]+):\d+(?:[?#]|$)/i.test(text);
      const proxy = url.username || url.password || url.hash || (explicitPort && (url.protocol === 'http:' || !url.search));
      if (!proxy) return { kind: 'subscriptionURL', summary: url.hostname, normalizedURL: url.href };
    } catch { /* The node parser still reports malformed entries. */ }
  }
  if (count || /^[a-z][\w+-]*:\/\//im.test(text)) return { kind: 'proxyNodes', summary: '节点链接', detectedNodeCount: count };
  return { kind: 'unknown', summary: '无法识别来源' };
}
