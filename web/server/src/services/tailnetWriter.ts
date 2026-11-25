import { isIP } from 'node:net';
import { createHash } from 'node:crypto';
import type { ClientTarget, TailnetConnection } from '../../../shared/src/index.js';
import { confName, mihomoTargets, surgeTargets } from './nodeExport.js';
import type { Fields } from './subscriptionParser.js';
import { cleanLine } from './subscriptionParser.js';
import { webURL } from './subscriptionFetcher.js';

const tailnetIPv4 = '100.64.0.0/10';
const tailnetIPv6 = 'fd7a:115c:a1e0::/48';
export const maskedAuthKey = '********';

export function tailnetStableSlug(id: string): string {
  const prefix = /^[0-9a-f]{8}-/i.test(id) ? id.slice(0, 8).toLowerCase() : createHash('sha256').update(id).digest('hex').slice(0, 8);
  return 'tower-tailnet-' + prefix;
}

export function normalizeTailnet(connection: TailnetConnection): TailnetConnection {
  const controlURLString = connection.controlURLString?.trim() ? webURL(connection.controlURLString, true).href : null;
  const subnets = [...new Set(connection.subnets.map(value => value.trim()).filter(Boolean))];
  for (const cidr of subnets) {
    const [address, prefix, extra] = cidr.split('/');
    const width = isIP(address) === 4 ? 32 : isIP(address) === 6 ? 128 : 0;
    if (!width || !/^\d+$/.test(prefix ?? '') || Number(prefix) > width || extra !== undefined) throw new Error('Invalid Tailnet subnet');
  }
  const magicDNSSuffix = connection.magicDNSSuffix?.trim().toLowerCase().replace(/^\.|\.$/g, '') || null;
  if (magicDNSSuffix && !/^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(magicDNSSuffix)) {
    throw new Error('Invalid MagicDNS suffix');
  }
  const deviceName = connection.deviceName?.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40).replace(/-$/, '') || null;
  return { id: connection.id, name: cleanLine(connection.name) || 'Tailscale', controlURLString, deviceName, subnets, magicDNSSuffix,
    isEnabled: connection.isEnabled, stableSlug: tailnetStableSlug(connection.id) };
}

export interface TailnetOutput {
  proxyLines: string[];
  sections: string[];
  rules: string[];
  proxies: Fields[];
  endpoints: Fields[];
  singRules: Fields[];
  dnsServers: Fields[];
  dnsRules: Fields[];
  warnings: string[];
}

export function writeTailnets(
  connections: TailnetConnection[], keys: Readonly<Record<string, string>>, target: ClientTarget,
  maskSecrets: boolean, existingNames: Iterable<string>, surfboard = false,
): TailnetOutput {
  const result: TailnetOutput = { proxyLines: [], sections: [], rules: [], proxies: [], endpoints: [], singRules: [], dnsServers: [], dnsRules: [], warnings: [] };
  const surge = surgeTargets.has(target) && !surfboard;
  const mihomo = mihomoTargets.has(target);
  const stash = target === 'clash';
  const sing = target === 'sing-box';
  const taken = new Set([...existingNames].map(name => name.toLowerCase()));
  taken.add('direct');
  taken.add('reject');
  const dnsTags = new Set(['local', 'remote', 'bootstrap']);
  const ruleClaims = new Set<string>();
  const enabled = connections.filter(connection => connection.isEnabled);
  if (!(surge || stash || mihomo || sing)) {
    if (enabled.length) result.warnings.push('此客户端不支持 Tailscale 内网，已跳过。');
    return result;
  }
  for (const connection of enabled) {
    const tailnet = normalizeTailnet(connection);
    const base = confName(tailnet.name).replaceAll('"', '＂') || 'Tailscale';
    let name = base;
    let index = 2;
    while (taken.has(name.toLowerCase())) name = `${base} ${index++}`;
    taken.add(name.toLowerCase());
    const slug = tailnet.stableSlug!;
    const hostname = `${tailnet.deviceName || 'tower'}-${stash ? 'stash' : target}`;
    const authKey = Object.hasOwn(keys, tailnet.id) ? keys[tailnet.id] : undefined;
    const effectiveKey = authKey && maskSecrets ? maskedAuthKey : authKey;
    const cidrs = [...new Set([tailnetIPv4, tailnetIPv6, ...tailnet.subnets])];
    const domain = tailnet.magicDNSSuffix;
    const control = tailnet.controlURLString;
    if (surge) {
      result.proxyLines.push(`${name} = tailscale, section-name=${slug}`);
      result.sections.push([`[Tailscale ${slug}]`, 'interactive-login = true',
        ...(control ? ['control-url = ' + control] : []), 'hostname = ' + hostname].join('\n'));
    } else if (sing) {
      result.endpoints.push({ type: 'tailscale', tag: name, state_directory: slug, hostname, accept_routes: true,
        ...(control ? { control_url: control } : {}), ...(effectiveKey ? { auth_key: effectiveKey } : {}) });
      if (domain) {
        let dnsTag = name + ' DNS';
        let suffix = 2;
        while (dnsTags.has(dnsTag)) dnsTag = name + ' DNS ' + suffix++;
        dnsTags.add(dnsTag);
        result.dnsServers.push({ type: 'tailscale', tag: dnsTag, endpoint: name });
        result.dnsRules.push({ action: 'route', domain_suffix: [domain], server: dnsTag });
        result.singRules.push({ action: 'route', domain_suffix: [domain], outbound: name });
      }
      result.singRules.push({ action: 'route', ip_cidr: cidrs, outbound: name });
    } else {
      result.proxies.push({ name, type: 'tailscale', hostname, ...(control ? { 'control-url': control } : {}),
        ...(effectiveKey ? { 'auth-key': effectiveKey } : {}), ...(mihomo ? { 'state-dir': slug, udp: true, 'accept-routes': true } : {}) });
    }
    if (!sing) {
      if (domain) result.rules.push(`DOMAIN-SUFFIX,${domain},${name}`);
      for (const cidr of cidrs) result.rules.push(`IP-CIDR${cidr.includes(':') ? '6' : ''},${cidr},${name},no-resolve`);
    }
    if (!authKey && !surge) result.warnings.push(`“${name}”没有 Auth Key。`);
    if (stash && !domain) result.warnings.push(`“${name}”未填写 MagicDNS 后缀，Stash 默认跳过 Tailscale 地址段。`);
    for (const route of [...cidrs, ...domain ? [domain] : []]) {
      if (ruleClaims.has(route) && ![tailnetIPv4, tailnetIPv6].includes(route)) result.warnings.push(`Tailscale 路由 ${route} 已由先声明的连接匹配。`);
      ruleClaims.add(route);
    }
  }
  return result;
}
