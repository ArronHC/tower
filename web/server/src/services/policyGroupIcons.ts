import { readFileSync } from 'node:fs';
import path from 'node:path';
import type { ClientTarget, RuleSchemeGroup, RuleSchemeRuleset } from '../../../shared/src/index.js';
import { flagEmoji } from './countryTable.js';
import { countryCodeFromName } from './nodeRegionResolver.js';
import { emojiTable, roles, ruleLists, symbols, table } from './policyGroupEmojiData.js';
import type { EmojiEntry } from './policyGroupEmojiData.js';

interface Manifest {
  circles: { base: string; files: Record<string, string>; artifactCommit?: string };
  flags: { base: string; files: Record<string, string>; artifactCommit?: string };
  fluent: { base: string; emoji: Record<string, string> };
}
import { resourcesDir } from '../paths.js';
const manifest: Manifest = JSON.parse(readFileSync(path.join(resourcesDir, 'PolicyGroupIcons/PolicyGroupIcons_manifest.json'), 'utf8'));
const segments = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
const trimSelectors = (value: string) => value.replace(/[\ufe0e\ufe0f]/g, '');
const tokens = (value: string) => value.replace(/([A-Z])([A-Z][a-z])/g, '$1 $2').replace(/([a-z])([A-Z])/g, '$1 $2')
  .replace(/([A-Za-z])(\d)/g, '$1 $2').toLowerCase().match(/[a-z0-9]+/g) ?? [];
export const leadingEmoji = (name: string): string | null => {
  const first = segments.segment(name.trim())[Symbol.iterator]().next().value?.segment;
  return first && /[^\x00-\x7f]/.test(first) && /\p{Emoji}/u.test(first) ? first : null;
};
export const withoutLeadingEmoji = (name: string) => name.trim().slice(leadingEmoji(name)?.length ?? 0).trim();

function match(name: string, entries: EmojiEntry[]): string | null {
  const lowered = name.toLowerCase();
  const words = tokens(name);
  const variants = new Set(words);
  for (let i = 0; i < words.length; i++) {
    for (const width of [2, 3]) if (i + width <= words.length) variants.add(words.slice(i, i + width).join(''));
  }
  return entries.find(([, keywords, phrases, exact]) =>
    phrases.some(phrase => lowered.includes(phrase)) ||
    (exact?.includes(words.join(' ')) && !/[^\x00-\x7f]\p{L}/u.test(lowered)) ||
    keywords.some(word => variants.has(word.replace(/ /g, '')) ||
      (' ' + words.join(' ') + ' ').includes(' ' + word + ' ')))?.[0] ?? null;
}

function recognized(name: string, regions = true): string | null {
  const code = regions && (/加坡/.test(name) ? 'SG' : /欧洲|欧盟|\b(?:Europe|EU)\b/i.test(name) ? 'EU' : countryCodeFromName(name));
  return match(name, table) ?? (code ? flagEmoji(code) : null) ?? match(name, roles);
}

export const ruleListEmoji = (name: string) => match(name, ruleLists) ?? recognized(name, false);
export const catalogEmoji = (name: string, fallback: string) => leadingEmoji(name) ?? match(name, emojiTable) ?? fallback;
export const isFlag = (value: string): boolean => /^[\u{1f1e6}-\u{1f1ff}]{2}$/u.test(trimSelectors(value));

function rulesEmoji(group: RuleSchemeGroup, rules: RuleSchemeRuleset[]): string | null {
  const scores = new Map<string, number>();
  for (const { groupName, resource } of rules) {
    if (groupName !== group.name) continue;
    const emoji = resource.type === 'inline' ? (/^(?:FINAL|MATCH)$/i.test(resource.rule) ? '🐟' : /^GEOIP,CN$/i.test(resource.rule) ? '🎯' : null) :
      ruleListEmoji(path.basename(resource.type === 'bundled' ? resource.file : new URL(resource.url).pathname).replace(/\.list$/i, ''));
    if (emoji) scores.set(emoji, (scores.get(emoji) ?? 0) + 1);
  }
  if (scores.size > 1 && [...scores.keys()].every(value => ['🎥', '📹', '🧸', '🎬', '📺', '🍿', '🎵'].includes(value))) return '🎬';
  return [...scores].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
}

export function policyGroupEmoji(group: RuleSchemeGroup, rules: RuleSchemeRuleset[] = [], fallback = true): string | null {
  const references = group.members.flatMap(member => member.type === 'reference' ? [member.name.toUpperCase()] : []);
  const patterns = group.members.flatMap(member => member.type === 'nodePattern' ? [countryCodeFromName(member.pattern)] : []);
  let memberEmoji: string | null = null;
  if (!patterns.length && references.length) {
    if (references.every(name => name === 'DIRECT')) memberEmoji = '🎯';
    if (references.every(name => name.startsWith('REJECT'))) memberEmoji = '🛑';
  } else if (!references.length && patterns[0] && patterns.every(code => code === patterns[0])) memberEmoji = flagEmoji(patterns[0]);
  return leadingEmoji(group.name) ?? group.iconEmoji ?? recognized(group.name) ?? rulesEmoji(group, rules) ?? memberEmoji ??
    (fallback ? ({ 'url-test': '⚡️', fallback: '🔁', 'load-balance': '⚖️', select: '🌐' } as Record<string, string>)[group.kind] ?? null : null);
}

export function policyGroupIconURL(emoji: string | null, circle = false): string | null {
  if (!emoji) return null;
  const key = trimSelectors(emoji === '🇺🇲' ? '🇺🇸' : emoji);
  if (circle) {
    if (manifest.circles.artifactCommit && Object.hasOwn(manifest.circles.files, key)) {
      return manifest.circles.base + [...key].map(char => char.codePointAt(0)!.toString(16)).join('-') + '.png';
    }
    return symbols[key] ? symbols[key] + '.system' : null;
  }
  if (isFlag(key)) {
    const code = [...key].map(char => String.fromCharCode(char.codePointAt(0)! - 127397)).join('');
    return manifest.flags.artifactCommit && Object.hasOwn(manifest.flags.files, code) ? manifest.flags.base + code.toLowerCase() + '.png' : null;
  }
  const location = manifest.fluent.emoji[key];
  return typeof location === 'string' ? manifest.fluent.base + location.split('/').map(encodeURIComponent).join('/') : null;
}

export const drawsIcons = new Set<ClientTarget>(['surge', 'surge-mac', 'clash', 'clash-apple', 'clash-verge', 'quanx', 'loon', 'egern']);
export const writesIcons = new Set<ClientTarget>([...drawsIcons, 'clashmac', 'flclash', 'mihomo-party', 'clash-mi']);
