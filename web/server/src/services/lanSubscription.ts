import { timingSafeEqual } from 'node:crypto';
import { CLIENT_TARGETS, LAN_FORMAT_OPTIONS } from '../../../shared/src/index.js';
import type { AppSnapshot, ClientTarget, ExportContentMode, GeneratedConfiguration } from '../../../shared/src/index.js';
import { APIError, invalid } from '../routes/errors.js';
import { cleanLine } from './subscriptionParser.js';

const aliases: Record<string, ClientTarget> = {
  clash: 'clash-mi', clashmi: 'clash-mi', clashverge: 'clash-verge', mihomo: 'clash-mi', openclash: 'clash-mi', nikki: 'clash-mi',
  stash: 'clash', surfboard: 'surge', 'shadow-rocket': 'shadowrocket', quantumult: 'quanx', quantumultx: 'quanx',
  'quantumult-x': 'quanx', singbox: 'sing-box', v2ray: 'v2box', v2rayng: 'v2box', v2rayn: 'v2box',
};
const agents: Array<[RegExp, ClientTarget]> = [
  [/anywhere/i, 'anywhere'], [/shadowrocket/i, 'shadowrocket'], [/quantumult|quanx/i, 'quanx'],
  [/hiddify/i, 'hiddify'], [/sing-?box/i, 'sing-box'], [/stash/i, 'clash'], [/karing/i, 'karing'],
  [/surfboard/i, 'surge'], [/surge[ /_-]*mac/i, 'surge-mac'], [/surge/i, 'surge'],
  [/loon/i, 'loon'], [/egern/i, 'egern'], [/v2rayn|v2box/i, 'v2box'], [/clashmac/i, 'clashmac'],
  [/clash.?verge/i, 'clash-verge'], [/flclash/i, 'flclash'], [/clash.?party|mihomo.?party/i, 'mihomo-party'],
  [/clash|mihomo|nikki/i, 'clash-mi'],
];

export function lanTarget(explicit: string | undefined, agent = '', anywhereFormat = ''): { target: ClientTarget; surfboard: boolean } {
  const choice = explicit?.trim().toLowerCase();
  if (choice && choice !== 'auto') {
    const exact = Object.hasOwn(CLIENT_TARGETS, choice) ? choice as ClientTarget : undefined;
    const target = (Object.hasOwn(aliases, choice) ? aliases[choice] : undefined) ?? exact;
    if (!target) invalid('Unknown LAN subscription target');
    return { target, surfboard: choice === 'surfboard' };
  }
  const target = agents.find(([pattern]) => pattern.test(agent))?.[1] ?? (acceptsAJSON(anywhereFormat) ? 'anywhere' : null);
  if (!target) invalid('Unknown client. Specify a subscription target in the URL.');
  return { target, surfboard: target === 'surge' && /surfboard/i.test(agent) };
}

export const acceptsAJSON = (format?: string) => format?.split(',').some(value => value.trim().toLowerCase() === 'ajson/1') ?? false;

export function validateLANAccess(enabled: boolean, expected: string, provided: string) {
  const a = Buffer.from(expected);
  const b = Buffer.from(provided);
  if (a.length !== b.length || !timingSafeEqual(a, b)) throw new APIError(403, 'Invalid subscription token');
  if (!enabled) throw new APIError(503, 'LAN sharing is disabled');
}

export function lanURL(baseURL: string, token: string, format: string, mode: ExportContentMode): string {
  let url: URL;
  try { url = new URL(baseURL); } catch { return invalid('Invalid base URL'); }
  if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) invalid('Invalid base URL');
  if (!LAN_FORMAT_OPTIONS.some(option => option.id === format) && format !== 'anywhere') invalid('Unknown LAN subscription target');
  url.pathname = '/sub/' + token;
  url.hash = '';
  url.search = '';
  if (format !== 'auto') url.searchParams.set('target', format);
  if (mode !== 'fullConfiguration') url.searchParams.set('content', mode);
  return url.href;
}

export function subscriptionHeaders(snapshot: AppSnapshot): Record<string, string> {
  let upload = 0, download = 0, total = 0, expires = 0;
  for (const source of snapshot.subscriptions) {
    if (!source.isEnabled || !source.usage) continue;
    upload += source.usage.uploadBytes ?? 0;
    download += source.usage.downloadBytes ?? 0;
    total += source.usage.totalBytes ?? 0;
    const expiry = Math.floor(Date.parse(source.usage.expiresAt ?? '') / 1000);
    if (expiry > 0 && (!expires || expiry < expires)) expires = expiry;
  }
  return {
    'subscription-userinfo': `upload=${upload}; download=${download}; total=${total}; expire=${expires}`,
    'profile-title': 'base64:' + Buffer.from(cleanLine(snapshot.settings.configurationName) || 'Tower').toString('base64'),
    'profile-update-interval': '24',
  };
}

export function contentDisposition(fileName: string): string {
  const safe = fileName.replace(/[^\x20-\x7e]|["\\]/g, '_');
  const utf8 = encodeURIComponent(fileName).replace(/['()*]/g, char => '%' + char.charCodeAt(0).toString(16).toUpperCase());
  return `attachment; filename="${safe}"; filename*=UTF-8''${utf8}`;
}

export function subscriptionPayload(config: GeneratedConfiguration, ajson: boolean) {
  const anywhere = ajson && config.target === 'anywhere' && config.anywhereAJSON;
  return {
    content: anywhere || config.content, mimeType: anywhere ? 'application/json' : config.mimeType,
    filename: anywhere ? config.fileName.replace(/\.[^.]+$/, '.json') : config.fileName,
  };
}
