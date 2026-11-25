import { isIP } from 'node:net';
import type { AppSettings, ClientTarget, RuleSchemeRuleset } from '../../../shared/src/index.js';
import { CLIENT_TARGETS } from '../../../shared/src/index.js';
import { ipDatabase } from './ipCountryDb.js';
import { mihomoTargets, surgeTargets } from './nodeExport.js';
import { isBuiltinPolicy, splitRule } from './ruleSchemeParser.js';
import { aclManifest, rulesetLines } from './ruleSchemeService.js';
import { splitFields } from './subscriptionParser.js';
import type { Fields } from './subscriptionParser.js';

const set = (value: string) => new Set(value.split(' '));
const common = 'DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD IP-CIDR IP-CIDR6 IP-ASN GEOIP PROCESS-NAME SRC-PORT IN-PORT';
const supported: Record<string, Set<string>> = {
  mihomo: set(common + ' DOMAIN-REGEX GEOSITE IP-SUFFIX SRC-GEOIP SRC-IP-ASN SRC-IP-CIDR SRC-IP-SUFFIX DST-PORT NETWORK IN-TYPE IN-USER IN-NAME REMATCH-NAME PROCESS-PATH PROCESS-PATH-WILDCARD PROCESS-PATH-REGEX PROCESS-NAME-WILDCARD PROCESS-NAME-REGEX DSCP'),
  surge: set(common + ' RULE-SET DOMAIN-SET DEST-PORT SRC-IP PROTOCOL USER-AGENT URL-REGEX SUBNET DEVICE-NAME MAC-ADDRESS HOSTNAME-TYPE CELLULAR-RADIO CELLULAR-CARRIER'),
  clash: set('DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD DOMAIN-REGEX GEOSITE IP-CIDR IP-CIDR6 IP-ASN GEOIP DST-PORT NETWORK PROTOCOL PROCESS-NAME PROCESS-PATH USER-AGENT URL-REGEX SRC-IP'),
  shadowrocket: set('DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD USER-AGENT URL-REGEX IP-CIDR IP-CIDR6 IP-ASN GEOIP DST-PORT PROTOCOL'),
  loon: set('DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD IP-CIDR IP-CIDR6 GEOIP IP-ASN URL-REGEX USER-AGENT SRC-PORT DEST-PORT PROTOCOL'),
  quanx: set('DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD IP-CIDR IP-CIDR6 GEOIP USER-AGENT'),
};
const ignorable = set('extended-matching pre-matching notification-text notification-interval always-capture');
const ipRule = set('IP-CIDR IP-CIDR6 IP-ASN GEOIP');
const egernFields: Record<string, string> = {
  DOMAIN: 'domain', 'DOMAIN-SUFFIX': 'domain_suffix', 'DOMAIN-KEYWORD': 'domain_keyword', 'DOMAIN-WILDCARD': 'domain_wildcard',
  'DOMAIN-REGEX': 'domain_regex', 'IP-CIDR': 'ip_cidr', 'IP-CIDR6': 'ip_cidr', GEOIP: 'geoip', 'IP-ASN': 'ip_asn',
  'PROCESS-NAME': 'process_name', 'PROCESS-PATH': 'process_path', 'SRC-IP': 'src_ip_cidr', 'SRC-IP-CIDR': 'src_ip_cidr',
  'USER-AGENT': 'user_agent', 'URL-REGEX': 'url_regex', 'DEST-PORT': 'dest_port', 'DST-PORT': 'dest_port', 'SRC-PORT': 'src_port',
};
export const isBlock = (policy: string) => /^reject(?:-|$)/i.test(policy);
const isFinal = (rule: string) => /^(?:FINAL|MATCH)$/i.test(rule.trim());
const domainType = set('DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD DOMAIN-REGEX');
const regexEscape = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function familyOf(target: ClientTarget) {
  if (surgeTargets.has(target)) return 'surge';
  if (CLIENT_TARGETS[target].usesSingBoxFormat) return 'sing';
  if (mihomoTargets.has(target) || target === 'karing') return 'mihomo';
  return target;
}

export function builtinPolicy(name: string, target: ClientTarget): string | null {
  const policy = name.toUpperCase();
  const family = familyOf(target);
  if (policy === 'DIRECT' || policy === 'REJECT') return ['sing', 'egern', 'quanx'].includes(family) ? policy.toLowerCase() : policy;
  if (policy === 'REJECT-DROP') {
    if (['mihomo', 'clash', 'surge', 'loon', 'shadowrocket'].includes(family)) return policy;
    return builtinPolicy('REJECT', target);
  }
  if (policy === 'REJECT-TINYGIF' || policy === 'REJECT-NO-DROP') {
    if (['surge', 'shadowrocket'].includes(family)) return policy;
    if (family === 'loon' && policy === 'REJECT-TINYGIF') return 'REJECT-IMG';
    return builtinPolicy('REJECT', target);
  }
  if (policy === 'REJECT-IMG') return family === 'loon' ? policy : null;
  if (['PASS', 'PASS-RULE', 'COMPATIBLE'].includes(policy)) return family === 'mihomo' ? policy : null;
  if (['CELLULAR', 'CELLULAR-ONLY', 'HYBRID', 'NO-HYBRID'].includes(policy)) return target === 'surge' ? policy : null;
  return null;
}

type Condition = { type: string; value: string; options: string[]; children?: Condition[] };
function parseCondition(line: string, depth = 0): Condition | null {
  if (depth > 10) return null;
  const parsed = splitRule(line);
  if (!parsed) return null;
  if (!['AND', 'OR', 'NOT'].includes(parsed.type)) return parsed;
  if (!parsed.value.startsWith('(') || !parsed.value.endsWith(')')) return null;
  const children = splitFields(parsed.value.slice(1, -1)).map(value => {
    if (!value.startsWith('(') || !value.endsWith(')')) return null;
    return parseCondition(value.slice(1, -1), depth + 1);
  });
  if (children.some(child => !child) || (parsed.type === 'NOT' ? children.length !== 1 : children.length < 2)) return null;
  return { ...parsed, value: '', children: children as Condition[] };
}

function optionsFor(family: string, options: string[]) {
  const allowed = family === 'surge' ? set('no-resolve dns-failed extended-matching pre-matching notification-text notification-interval always-capture update-interval') :
    family === 'clash' ? set('no-resolve no-track') : family === 'mihomo' ? set('no-resolve src') :
    family === 'shadowrocket' ? set('no-resolve extended-matching pre-matching') :
    family === 'sing' || family === 'egern' ? set('no-resolve src') : set('no-resolve');
  const normalized = [...new Set(options)].filter(option => allowed.has(option.split('=')[0].toLowerCase()) ||
    !ignorable.has(option.split('=')[0].toLowerCase()));
  return normalized.every(option => allowed.has(option.split('=')[0].toLowerCase()) && !/[\r\n\u0085\u2028\u2029#;]/.test(option)) ? normalized : null;
}

function toCIDR(value: string): string | null {
  const [address, prefix, extra] = value.split('/');
  const version = isIP(address);
  const width = version === 4 ? 32 : version === 6 ? 128 : 0;
  if (!width || extra !== undefined) return null;
  if (prefix === undefined) return address + '/' + width;
  return /^\d+$/.test(prefix) && Number(prefix) <= width ? value : null;
}

function ports(value: string): string[] | null {
  const results = value.split('/').map(part => {
    const match = /^(>=|<=|>|<)?(\d+)(?:-(\d+))?$/.exec(part);
    if (!match) return null;
    const [, op, first, last] = match;
    const low = op?.startsWith('<') ? 0 : Number(first) + Number(op === '>');
    const high = op?.startsWith('>') ? 65535 : Number(last ?? first) - Number(op === '<');
    return low <= high && high <= 65535 ? low === high ? String(low) : `${low}-${high}` : null;
  });
  return results.every(item => item !== null) ? results as string[] : null;
}

function convert(condition: Condition, family: string, nested = false): Condition | null {
  const options = optionsFor(family, condition.options);
  if (!options) return null;
  let { type, value } = condition;
  if (condition.children) {
    if (family === 'quanx') return null;
    const children = condition.children.map(child => convert(child, family, true));
    return children.every(child => child !== null) ? { ...condition, options, children: children as Condition[] } : null;
  }
  type = ({ HOST: 'DOMAIN', 'HOST-SUFFIX': 'DOMAIN-SUFFIX', 'HOST-KEYWORD': 'DOMAIN-KEYWORD', 'IP6-CIDR': 'IP-CIDR6' } as Record<string, string>)[type] ?? type;
  const original = value;
  if (value.startsWith('"') && value.endsWith('"')) value = value.slice(1, -1);
  if (/[\r\n\u0085\u2028\u2029]/.test(value)) return null;
  const ini = ['surge', 'shadowrocket', 'loon', 'quanx'].includes(family);
  if (ini && (/[#;]/.test(value) || /\s\/\//.test(value)) && !['URL-REGEX', 'USER-AGENT'].includes(type)) return null;
  if (value.includes(',') && (family !== 'surge' || original === value)) return null;
  if (type === 'IP-CIDR' || type === 'IP-CIDR6') {
    if (!value.includes('/') || !toCIDR(value)) return null;
    type = value.includes(':') ? 'IP-CIDR6' : 'IP-CIDR';
  }
  if (options.includes('src') && !['IP-CIDR', 'IP-CIDR6'].includes(type)) return null;
  if (['DEST-PORT', 'DST-PORT'].includes(type)) type = ['loon', 'surge'].includes(family) ? 'DEST-PORT' : 'DST-PORT';
  if (['DEST-PORT', 'DST-PORT', 'SRC-PORT', 'IN-PORT'].includes(type)) {
    const valid = ports(value);
    if (!valid) return null;
    if (valid.length > 1 && ['surge', 'loon', 'shadowrocket'].includes(family)) {
      return { type: 'OR', value: '', options, children: valid.map(port => ({ type, value: port, options: [] })) };
    }
    value = valid.join('/');
  }
  if (type === 'PROTOCOL' || type === 'NETWORK') {
    const protocols = family === 'loon' ? ['HTTP', 'HTTPS', 'TCP', 'QUIC', 'STUN', 'UDP'] : ['TCP', 'UDP'];
    if ((family === 'shadowrocket' && !nested) || (!protocols.includes(value.toUpperCase()) && family !== 'surge')) return null;
    type = ['mihomo', 'sing'].includes(family) ? 'NETWORK' : 'PROTOCOL';
    value = value.toUpperCase();
  }
  if (type === 'SRC-IP' || type === 'SRC-IP-CIDR') {
    const cidr = toCIDR(value);
    if (!cidr) return null;
    type = ['surge', 'clash'].includes(family) ? 'SRC-IP' : 'SRC-IP-CIDR';
    value = cidr;
  }
  if (family === 'surge' && options.includes('src')) return null;
  if (type === 'DOMAIN-WILDCARD' && family === 'loon') {
    if (/[\[\]]/.test(value)) return null;
    if (!/[*?]/.test(value)) type = 'DOMAIN';
    else if (value.startsWith('*.') && !/[*?]/.test(value.slice(2))) {
      const apex = value.slice(2);
      return { type: 'AND', value: '', options, children: [
        { type: 'DOMAIN-SUFFIX', value: apex, options: [] },
        { type: 'NOT', value: '', options: [], children: [{ type: 'DOMAIN', value: apex, options: [] }] },
      ] };
    }
  }
  if (family === 'mihomo' && type === 'PROCESS-NAME') {
    if (value.startsWith('/')) {
      type = /[*?]|\/$/.test(value) ? 'PROCESS-PATH-WILDCARD' : 'PROCESS-PATH';
      if (value.endsWith('/')) value += '*';
    } else if (/[*?]/.test(value)) type = 'PROCESS-NAME-WILDCARD';
  }
  if (['RULE-SET', 'DOMAIN-SET'].includes(type) && !(family === 'surge' &&
      (['SYSTEM', 'LAN'].includes(value) && type === 'RULE-SET' || /^https:\/\//.test(value)))) return null;
  if (family !== 'sing' && family !== 'egern' && !supported[family]?.has(type)) return null;
  return { type, value: original.startsWith('"') && family === 'surge' ? original : value, options };
}

function csvCondition(condition: Condition, nested = false): string {
  const value = condition.children ? '(' + condition.children.map(child => '(' + csvCondition(child, true) + ')').join(',') + ')' : condition.value;
  return [condition.type, value, ...(nested ? condition.options : [])].join(',');
}

function singCondition(condition: Condition): Fields | null {
  const { type, value } = condition;
  if (condition.children) {
    const children = condition.children.map(singCondition);
    return children.every(child => child !== null) ? { type: 'logical', mode: type === 'OR' ? 'or' : 'and', rules: children, ...(type === 'NOT' ? { invert: true } : {}) } : null;
  }
  const fields: Record<string, string> = {
    DOMAIN: 'domain', 'DOMAIN-SUFFIX': 'domain_suffix', 'DOMAIN-KEYWORD': 'domain_keyword', 'DOMAIN-REGEX': 'domain_regex',
    'IP-CIDR': 'ip_cidr', 'IP-CIDR6': 'ip_cidr', 'SRC-IP-CIDR': 'source_ip_cidr', 'PROCESS-NAME': 'process_name',
    'PROCESS-PATH': 'process_path', 'PROCESS-PATH-REGEX': 'process_path_regex', NETWORK: 'network',
  };
  if (type === 'DOMAIN-WILDCARD') return { domain_regex: ['^' + value.split(/([*?])/).map(char => char === '*' ? '.*' : char === '?' ? '.' : regexEscape(char)).join('') + '$'] };
  if (type === 'GEOIP' || type === 'IP-ASN') {
    if (type === 'GEOIP' && /^(?:LAN|PRIVATE)$/i.test(value)) return { ip_is_private: true };
    const cidrs = type === 'GEOIP' ? ipDatabase().cidrsForCountry(value) : ipDatabase().cidrsForASN(Number(value.replace(/^AS/i, '')));
    return cidrs.length ? { ip_cidr: cidrs } : null;
  }
  if (type === 'SRC-PORT' || type === 'DST-PORT') {
    const fields = type === 'SRC-PORT' ? ['source_port', 'source_port_range'] : ['port', 'port_range'];
    const ranges = value.split('/');
    const exact = ranges.filter(range => !range.includes('-')).map(Number);
    const filtered = ranges.filter(range => range.includes('-')).map(range => range.replace('-', ':'));
    if (exact.length && filtered.length) return { type: 'logical', mode: 'or', rules: [{ [fields[0]]: exact }, { [fields[1]]: filtered }] };
    return exact.length ? { [fields[0]]: exact } : { [fields[1]]: filtered };
  }
  const key = fields[type];
  if (!key) return null;
  return { [condition.options.includes('src') && key === 'ip_cidr' ? 'source_ip_cidr' : key]: [type === 'NETWORK' ? value.toLowerCase() : value] };
}

function egernCondition(condition: Condition): Fields | null {
  const { type, value } = condition;
  if (condition.children) {
    const children = condition.children.map(egernCondition);
    return children.every(child => child !== null) ? { [type.toLowerCase()]: { match: type === 'NOT' ? children[0] : children } } : null;
  }
  const key = condition.options.includes('src') && ['IP-CIDR', 'IP-CIDR6'].includes(type) ? 'src_ip_cidr' : egernFields[type];
  if (!key) return null;
  return { [key]: { match: value, ...(ipRule.has(type) ? { no_resolve: true } : {}) } };
}

interface RulesOutput {
  lines: string[];
  remoteLines: string[];
  objects: Fields[];
  providers: Record<string, Fields>;
  ruleSets: Fields[];
  blockedWarnings: string[];
  warnings: string[];
  final: string;
}
interface RulesOptions {
  target: ClientTarget;
  rules: RuleSchemeRuleset[];
  groupNames: ReadonlyMap<string, string>;
  primary: string;
  blockingGroups: Set<string>;
  settings: AppSettings;
  downloads: Readonly<Record<string, string>>;
  protectedDNS: boolean;
  surfboard?: boolean;
}

export function exportRouting(options: RulesOptions): RulesOutput {
  const { target, rules, groupNames, primary, blockingGroups, settings, downloads } = options;
  const family = familyOf(target);
  const remoteAfterLocal = ['loon', 'quanx'].includes(family);
  const output: RulesOutput = { lines: [], remoteLines: [], objects: [], providers: {}, ruleSets: [], blockedWarnings: [], warnings: [], final: primary };
  const skips = new Map<string, { count: number; block: boolean }>();
  const lookupPolicy = (name: string) => isBuiltinPolicy(name) ? builtinPolicy(name, target) : groupNames.get(name);
  const mark = (reason: string, policy: string, count = 1) => {
    const block = isBlock(policy) || blockingGroups.has(policy);
    const key = `${block}:${reason}`;
    const prior = skips.get(key);
    skips.set(key, { count: (prior?.count || 0) + count, block });
  };
  const representable = (line: string, added: string[]) => {
    const parsed = parseCondition(line);
    return parsed && convert({ ...parsed, options: [...parsed.options, ...added] }, family);
  };
  const lastOrdinary = rules.findLastIndex(rule => rule.resource.type !== 'inline' || !isFinal(rule.resource.rule));
  const hasLocalAfter = (index: number) => rules.slice(index + 1).some(rule => {
    if (rule.resource.type === 'remote' || rule.resource.type === 'inline' && isFinal(rule.resource.rule)) return false;
    if (!lookupPolicy(rule.groupName)) return false;
    if (rule.resource.type === 'inline' && settings.preferRuleSets && aclManifest.inlineRules[rule.resource.rule]?.[family]) return false;
    if (rule.resource.type === 'bundled' && settings.preferRuleSets) {
      const metadata = aclManifest.rulesets[rule.resource.file];
      const artifact = family === 'loon' ? metadata?.loon : metadata?.quanx;
      if (artifact && artifact.residualRuleCount === 0) return false;
    }
    return rulesetLines(rule, downloads).some(line => representable(line, rule.options ?? []));
  });
  function remote(url: string, policy: string, name: string, format?: string, behavior = 'classical', trailingOptions: string[] = []) {
    if (!/^https:\/\//i.test(url) || /[\r\n\u0085\u2028\u2029]/.test(url)) return false;
    if (options.surfboard && format) return false;
    if (family === 'surge' && !format && !url.includes(',')) {
      const valid = optionsFor(family, trailingOptions);
      if (!valid) return false;
      output.lines.push(['RULE-SET', url, policy, ...valid].join(','));
    } else if (['mihomo', 'clash'].includes(family) && format !== 'srs') {
      const valid = optionsFor(family, trailingOptions);
      if (!valid) return false;
      output.providers[name] = { type: 'http', behavior, format: format ?? (/\.ya?ml(?:\?|$)/i.test(url) ? 'yaml' : 'text'),
        url, path: `./ruleset/${name}.${format ?? 'txt'}`, interval: 86400 };
      output.lines.push(['RULE-SET', name, policy, ...valid].join(','));
    } else if (family === 'sing' && format === 'srs' && trailingOptions.every(option => option === 'no-resolve')) {
      output.ruleSets.push({ type: 'remote', tag: name, format: 'binary', url,
        ...(target === 'hiddify' && primary !== 'direct' ? { download_detour: primary } : {}) });
      output.objects.push({ action: isBlock(policy) ? 'reject' : 'route', rule_set: [name], ...(isBlock(policy) ? {} : { outbound: policy }) });
    } else if (family === 'egern' && format === 'egern' && !trailingOptions.length) {
      output.objects.push({ rule_set: { match: url, policy, update_interval: 86400 } });
    } else if (remoteAfterLocal && !format && !trailingOptions.length && !url.includes(',')) {
      output.remoteLines.push(family === 'quanx' ? `${url}, tag=${name}, force-policy=${policy}, update-interval=86400, enabled=true` : `${url},policy=${policy},tag=${name},enabled=true`);
    } else return false;
    return true;
  }

  for (const [index, rule] of rules.entries()) {
    const policy = lookupPolicy(rule.groupName);
    if (!policy) { mark('目标策略不可用', rule.groupName); continue; }
    const { resource } = rule;
    const lines = rulesetLines(rule, downloads);
    const ruleName = 'tower-' + (index + 1);
    if (resource.type === 'inline' && isFinal(resource.rule)) {
      output.final = policy;
      continue;
    }
    const canRemote = !remoteAfterLocal || !hasLocalAfter(index);
    if (resource.type === 'remote') {
      const format = /\.mrs(?:\?|$)/i.test(resource.url) ? 'mrs' : /\.srs(?:\?|$)/i.test(resource.url) ? 'srs' : undefined;
      if ((!lines.length || settings.preferRuleSets) && canRemote &&
          remote(resource.url, policy, ruleName, format, format === 'mrs' ? (/ipcidr/i.test(resource.url) ? 'ipcidr' : 'domain') : 'classical', rule.options)) continue;
      if (!lines.length) { mark(canRemote ? '远程规则集无法完整表达' : '远程规则集会改变规则匹配顺序', rule.groupName); continue; }
    }
    if (settings.preferRuleSets && canRemote && resource.type === 'bundled') {
      const config = aclManifest.rulesets[resource.file];
      if (family === 'surge' && config && remote(config.source, policy, ruleName, undefined, 'classical', rule.options)) continue;
      const artifact = family === 'loon' ? config?.loon : family === 'quanx' ? config?.quanx : family === 'egern' ? config?.egern : undefined;
      if (artifact?.residualRuleCount === 0 && remote(artifact.url, policy, ruleName, family === 'egern' ? 'egern' : undefined, 'classical', rule.options)) continue;
      if (config && ['mihomo', 'clash'].includes(family) && config.mrs && config.mrsResidualRuleCount === 0 && !rule.options?.length) {
        const artifacts = Object.entries(config.mrs);
        const kinds = lines.map(line => splitRule(line)?.type ?? '');
        // Splitting a mixed list is safe only when its conditions are disjoint
        // or share the same action. Every artifact here has the same policy.
        if (artifacts.length && kinds.every(type => domainType.has(type) || ipRule.has(type))) {
          for (const [behavior, value] of artifacts) remote(value.url, policy, ruleName + '-' + behavior, 'mrs', behavior, value.noResolve ? ['no-resolve'] : []);
          continue;
        }
      }
      if (family === 'sing' && config?.srs?.residualRuleCount === 0 && remote(config.srs.url, policy, ruleName, 'srs', 'classical', rule.options)) continue;
    }
    if (resource.type === 'inline' && settings.preferRuleSets && canRemote) {
      const published = aclManifest.inlineRules[resource.rule]?.[family];
      if (published && remote(published.url, policy, ruleName)) continue;
    }
    for (const line of lines) {
      const condition = representable(line, rule.options ?? []);
      if (!condition) { mark(`不支持 ${splitRule(line)?.type ?? '此规则'}`, rule.groupName); continue; }
      if (family === 'sing') {
        const fields = singCondition(condition);
        if (!fields) { mark(`不支持 ${condition.type}`, rule.groupName); continue; }
        output.objects.push({ ...fields, action: isBlock(policy) ? 'reject' : 'route', ...(isBlock(policy) ? {} : { outbound: policy }) });
      } else if (family === 'egern') {
        const fields = egernCondition(condition);
        if (!fields) { mark(`不支持 ${condition.type}`, rule.groupName); continue; }
        const key = Object.keys(fields)[0];
        output.objects.push({ [key]: { ...fields[key] as Fields, policy } });
      } else {
        if (family === 'quanx') condition.type = condition.type.replace(/^DOMAIN/, 'HOST').replace(/^IP-CIDR6$/, 'IP6-CIDR');
        const omitNoResolve = mihomoTargets.has(target) && condition.type === 'GEOIP' && index === lastOrdinary && options.protectedDNS;
        const autoNoResolve = ipRule.has(condition.type) || condition.type === 'IP6-CIDR';
        const opts = [...condition.options];
        if (!omitNoResolve && autoNoResolve && !opts.includes('src') && !opts.includes('no-resolve') && family !== 'quanx') opts.push('no-resolve');
        if (family === 'quanx') {
          const unsupported = opts.filter(option => option !== 'no-resolve');
          if (unsupported.length) { mark(`不支持 ${condition.type} 规则选项`, rule.groupName); continue; }
        }
        output.lines.push([csvCondition(condition), policy, ...family === 'quanx' ? [] : opts].join(','));
      }
    }
  }
  for (const [key, { count, block }] of skips) {
    const message = `已跳过 ${count} 条规则（${key.slice(key.indexOf(':') + 1)}）。`;
    if (block) output.blockedWarnings.push(`${message} 这些请求不会被拦截。`);
    else output.warnings.push(message);
  }
  return output;
}
