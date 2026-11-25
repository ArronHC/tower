import { lookup } from 'node:dns/promises';
import type { NodeRuntimeInfo, ProxyNode } from '../../../shared/src/index.js';
import { countries, countryByCode, countryNames, curatedAliases, cities } from './countryTable.js';
import { ipBytes, ipDatabase } from './ipCountryDb.js';

const normalize = (value: string) => value.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase();
const words = (value: string) => value.match(/[\p{L}\p{N}]+/gu) ?? [];
const reserved = new Set('SS SSR WS TLS TCP UDP QUIC KCP GRPC IPLC IEPL BGP CDN NAT VPN API DNS MUX KB MB GB TB'.split(' '));
const codes = new Map<string, string>();
const aliases = new Map<string, Array<{ phrase: string; code: string; length: number }>>();
const unspaced = new Map<string, Array<{ phrase: string; code: string; length: number }>>();

function add(code: string, phrases: string[], curated = false) {
  for (const raw of phrases) {
    const name = normalize(raw);
    const split = words(name);
    if (!split.length) continue;
    const cjk = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u.test(name);
    const phrase = cjk ? name : split.join(' ');
    const key = cjk ? phrase[0] : split[0]!;
    const index = cjk ? unspaced : aliases;
    const matches = index.get(key) ?? [];
    const item = { phrase, code, length: phrase.length };
    if (curated) matches.unshift(item);
    else matches.push(item);
    index.set(key, matches);
  }
}

for (const entry of countryNames) {
  add(entry.code, entry.names);
  for (const code of [entry.code, ...entry.codes]) if (!reserved.has(code)) codes.set(code, entry.code);
}
for (const [name, code] of Object.entries(cities)) add(code, [name], true);
for (const [code, names, tokens] of curatedAliases) {
  add(code, names, true);
  for (const token of tokens) if (!reserved.has(token.toUpperCase())) codes.set(token.toUpperCase(), code);
}

export function countryCodeFromName(name: string): string | null {
  for (const flag of name.match(/[\u{1F1E6}-\u{1F1FF}]{2}/gu) ?? []) {
    const code = String.fromCharCode(...[...flag].map(c => c.codePointAt(0)! - 127397));
    if (countryByCode.has(code)) return code;
  }
  const normalized = normalize(name);
  const tokens = words(normalized);
  let code: string | null = null;
  let length = 0;
  for (const [i, token] of tokens.entries()) {
    const suffix = tokens.slice(i).join(' ') + ' ';
    for (const match of aliases.get(token) ?? []) {
      if (match.length <= length || !suffix.startsWith(match.phrase + ' ')) continue;
      [code, length] = [match.code, match.length];
    }
  }
  for (const [i, character] of normalized.split('').entries()) {
    for (const match of unspaced.get(character) ?? []) {
      if (match.length <= length || !normalized.startsWith(match.phrase, i)) continue;
      [code, length] = [match.code, match.length];
    }
  }
  if (code) return code;
  for (const token of name.match(/(?<![\p{L}\p{N}])[A-Z]{2,3}(?=[\d]+(?:\b|[^\p{L}\p{N}])|[^\p{L}\p{N}]|$)/gu) ?? []) {
    const match = codes.get(token);
    if (match) return match;
  }
  return null;
}

export function nodeRegion(node: ProxyNode, addressCountry?: string | null): NodeRuntimeInfo {
  const supplied = node.countryOverride?.trim().toUpperCase();
  const override = supplied === 'UK' ? 'GB' : supplied;
  const manual = override && countryByCode.has(override) ? override : null;
  const named = manual ? null : countryCodeFromName(node.name);
  const country = countryByCode.get(manual ?? named ?? addressCountry ?? '');
  return {
    nodeId: node.id, countryCode: country?.code ?? null, countryName: country?.chineseName ?? null,
    countryEnglishName: country?.englishName ?? null, flagEmoji: country?.flag ?? null,
    latitude: country?.latitude ?? null, longitude: country?.longitude ?? null,
    regionSource: manual ? 'override' : named ? 'name' : country ? 'ip' : 'unknown',
  };
}

export type HostInfo = { country: string | null; resolvedIP: string | null; asn: number | null; asnOrg: string | null };
export class NodeRegionResolver {
  private readonly cache = new Map<string, { expires: number; result: Promise<HostInfo> }>();
  constructor(private readonly resolveHost = lookup) {}

  async resolve(node: ProxyNode): Promise<NodeRuntimeInfo> {
    const { country, ...network } = await this.resolveNetwork(node.server);
    return { ...nodeRegion(node, country), ...network };
  }

  resolveNetwork(server: string): Promise<HostInfo> {
    const host = server.toLowerCase().replace(/^\[|\]$/g, '');
    let entry = this.cache.get(host);
    if (!entry || entry.expires < Date.now()) {
      if (this.cache.size >= 4096) this.cache.delete(this.cache.keys().next().value!);
      const created = { expires: Infinity, result: this.hostInfo(host) };
      this.cache.set(host, created);
      entry = created;
      void created.result.then(info => { created.expires = Date.now() + (info.country ? 3600000 : 30000); }, () => {
        if (this.cache.get(host) === created) this.cache.delete(host);
      });
    }
    return entry.result;
  }

  private async hostInfo(host: string): Promise<HostInfo> {
    const addresses = ipBytes(host) ? [host] : await this.resolveHost(host, { all: true })
      .then(records => records.map(record => record.address), () => []);
    const db = ipDatabase();
    const matches = new Set(addresses.map(ip => db.countryCode(ip)).filter(Boolean));
    const resolvedIP = [...addresses].sort((a, b) => Number(a.includes(':')) - Number(b.includes(':')))[0] ?? null;
    const org = resolvedIP ? db.organization(resolvedIP) : null;
    return {
      country: matches.size === 1 ? [...matches][0] : null,
      resolvedIP, asn: org?.asn ?? null, asnOrg: org?.name ?? null,
    };
  }
}

export { countries };
