import { isIP } from 'node:net';
import { dump, JSON_SCHEMA } from 'js-yaml';
import { CLIENT_TARGETS } from '../../../shared/src/index.js';
import type { AppSnapshot, ClientTarget, ExportContentMode, GeneratedConfiguration, NodeRuntimeInfo, ProxyNode, RuleScheme, RuleSchemeGroup, SubscriptionSource } from '../../../shared/src/index.js';
import { flagEmoji } from './countryTable.js';
import { ipDatabase } from './ipCountryDb.js';
import { nodeRegion } from './nodeRegionResolver.js';
import { anywhereProxy, clashProxy, confName, egernProxy, loonNode, mihomoTargets, nodeExportReason, proxyURI, quanXNode, singProxy, surgeNode, surgeTargets, surgeWireguard } from './nodeExport.js';
import { drawsIcons, isFlag, leadingEmoji, policyGroupEmoji, policyGroupIconURL, withoutLeadingEmoji, writesIcons } from './policyGroupIcons.js';
import { isBuiltinPolicy } from './ruleSchemeParser.js';
import { aclManifest, bundledSchemes, effectiveScheme, findScheme, nodeMatchesPattern, rulesetLines } from './ruleSchemeService.js';
import { builtinPolicy, exportRouting, isBlock } from './routingExport.js';
import { cleanLine, isSubscriptionMetadata } from './subscriptionParser.js';
import type { Fields } from './subscriptionParser.js';
import { writeTailnets } from './tailnetWriter.js';

const testURL = 'https://www.gstatic.com/generate_204';
const clientSubnet = '114.114.114.0/24';
const localEncryptedDNS = ['https://223.5.5.5/dns-query', 'https://doh.pub/dns-query'];
const asciiRegex = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const exactPattern = (names: string[]) => names.length ? `^(?:${names.map(asciiRegex).join('|')})$` : '(?!)';
const unique = <T>(items: T[]) => [...new Set(items)];
export function yamlDocument(value: unknown): string {
  return dump(value, { schema: JSON_SCHEMA, forceQuotes: true, quotingType: '"', noRefs: true, lineWidth: -1 });
}

function nextName(base: string, taken: Set<string>): string {
  let value = base, index = 2;
  while (taken.has(value.toLowerCase())) value = `${base} (${index++})`;
  taken.add(value.toLowerCase());
  return value;
}

export function activeNodes(snapshot: AppSnapshot): ProxyNode[] {
  const sourceIDs = new Set(snapshot.subscriptions.filter(source => source.isEnabled).map(source => source.id));
  const exclusions = new Set(snapshot.excludedNodeIDs);
  return snapshot.nodes.filter(node => (!node.sourceID || sourceIDs.has(node.sourceID)) && !exclusions.has(node.id));
}

function nameMatches(snapshot: AppSnapshot, name: string): boolean {
  const filter = snapshot.nodeExportNameFilter;
  if (!filter) return true;
  const check = (term: string) => filter.useRegex ? nodeMatchesPattern(name, term) : name.toLowerCase().includes(term.toLowerCase());
  const includes = filter.includeKeywords.filter(term => term.trim());
  const excludes = filter.excludeKeywords.filter(term => term.trim());
  return (!includes.length || includes.some(check)) && !excludes.some(check);
}

interface GenerateOptions {
  target: ClientTarget;
  mode: ExportContentMode;
  maskSecrets?: boolean;
  surfboard?: boolean;
  runtime?: Readonly<Record<string, NodeRuntimeInfo>>;
  keys?: Readonly<Record<string, string>>;
  downloads?: Readonly<Record<string, string>>;
  directImportURL?: string | null;
}

interface PlannedGroup extends RuleSchemeGroup {
  sourceName: string;
  choices: string[];
  remoteIDs: string[];
  remoteNames: string[];
  acceptsAllRemote: boolean;
}

interface RemoteSource { id: string; source: SubscriptionSource }
const regionOrder = ['HK', 'JP', 'SG', 'TW', 'KR', 'US', 'CA', 'GB', 'DE', 'FR', 'NL', 'AU'];

function namedGroups(
  scheme: RuleScheme, target: ClientTarget, snapshot: AppSnapshot, taken: Set<string>, sanitize: (name: string) => string, surfboard: boolean,
) {
  const displayEmoji = snapshot.ruleGroupEmojisEnabled[scheme.id] !== false;
  const autoFill = snapshot.ruleGroupEmojiAutofill[scheme.id] ?? !scheme.isBundled;
  const mapping = new Map<string, string>();
  const groups: PlannedGroup[] = [];
  for (const group of scheme.groups) {
    if (isBuiltinPolicy(group.name)) continue;
    const recognized = policyGroupEmoji(group, scheme.rulesets);
    const leading = leadingEmoji(group.name);
    const region = recognized != null && isFlag(recognized);
    const supportsIcons = !surfboard && writesIcons.has(target) && !['ssid', 'relay', 'unsupported'].includes(group.kind);
    const iconURL = displayEmoji && supportsIcons && !(region && surgeTargets.has(target)) ? policyGroupIconURL(recognized, target === 'quanx') : null;
    let base = group.name;
    if (!displayEmoji || (iconURL && drawsIcons.has(target) && !region)) base = withoutLeadingEmoji(base);
    else if (!leading && displayEmoji && recognized && (region || autoFill)) base = recognized + ' ' + base;
    const name = nextName(sanitize(base) || 'Policy', taken);
    mapping.set(group.name, name);
    groups.push({ ...group, sourceName: group.name, name, iconURL, choices: [], remoteIDs: [], remoteNames: [], acceptsAllRemote: false });
  }
  return { groups, mapping };
}

function groupPlan(
  scheme: RuleScheme, target: ClientTarget, snapshot: AppSnapshot, nodes: ProxyNode[], initial: ProxyNode[],
  remotes: RemoteSource[], runtime: Readonly<Record<string, NodeRuntimeInfo>>, taken: Set<string>,
  sanitize: (name: string) => string, surfboard: boolean,
) {
  const direct = builtinPolicy('DIRECT', target)!;
  const { groups, mapping } = namedGroups(scheme, target, snapshot, taken, sanitize, surfboard);
  const proxyMapping = new Map<string, ProxyNode>();
  initial.forEach((node, i) => { if (!proxyMapping.has(node.name)) proxyMapping.set(node.name, nodes[i]); });
  const remoteMap = new Map(remotes.map(({ source, id }) => [source.id, id]));
  const groupMap = new Map(groups.map(group => [group.name, group]));
  const addNode = (group: PlannedGroup, node: ProxyNode) => {
    const remote = node.sourceID && remoteMap.get(node.sourceID);
    if (remote) { group.remoteIDs.push(remote); group.remoteNames.push(node.name); }
    else group.choices.push(node.name);
  };
  for (const group of groups) {
    for (const item of group.members) {
      if (item.type === 'reference') {
        const ref = isBuiltinPolicy(item.name) ? builtinPolicy(item.name, target) : mapping.get(item.name);
        if (ref && ref !== group.name) group.choices.push(ref);
        else if (!ref) {
          const node = proxyMapping.get(item.name);
          if (node) addNode(group, node);
        }
      } else {
        if (item.pattern === '.*') {
          group.remoteIDs.push(...remotes.map(({ id }) => id));
          group.acceptsAllRemote = true;
        }
        // Renaming nodes for export must not change imported regex matches.
        initial.forEach((node, index) => {
          if (nodeMatchesPattern(node.name, item.pattern)) addNode(group, nodes[index]);
        });
      }
    }
    group.choices = unique(group.choices);
    group.remoteIDs = unique(group.remoteIDs);
    group.remoteNames = unique(group.remoteNames);
  }

  if (!groups.length) {
    const name = nextName('Proxy', taken);
    const fallback: PlannedGroup = { name, sourceName: name, kind: 'select', members: [], choices: [], remoteNames: [], remoteIDs: remotes.map(({ id }) => id), acceptsAllRemote: true };
    for (const node of nodes) addNode(fallback, node);
    fallback.remoteIDs = unique(fallback.remoteIDs);
    groups.push(fallback);
  }
  const primary = groups[0];

  // The bundled ACL4SSR Full preset declares its own regexp-based groups.
  // Standalone presets without such groups can use the same region resolver as the UI.
  if (!scheme.isBundled && scheme.id === 'self-configuration') {
    const buckets = new Map<string, ProxyNode[]>();
    for (const node of nodes) {
      const code = nodeRegion(node).countryCode ?? runtime[node.id]?.countryCode ?? ipDatabase().countryCode(node.server);
      if (code) buckets.set(code, [...buckets.get(code) ?? [], node]);
    }
    const ranked = [...buckets].sort(([a], [b]) => (regionOrder.indexOf(a) + 1 || 99) - (regionOrder.indexOf(b) + 1 || 99));
    for (const [code, matches] of ranked) {
      const flag = flagEmoji(code);
      const name = nextName(sanitize(`${flag} ${code}`), taken);
      const auto = nextName(sanitize(`${flag} ${code} Auto`), taken);
      const iconURL = !surgeTargets.has(target) && writesIcons.has(target) ? policyGroupIconURL(flag, target === 'quanx') : null;
      const region: PlannedGroup = { name, sourceName: name, kind: 'select', members: [], choices: [auto], remoteIDs: [], remoteNames: [], acceptsAllRemote: false, iconURL };
      const test: PlannedGroup = { name: auto, sourceName: auto, kind: 'url-test', members: [], choices: [], remoteIDs: [], remoteNames: [], acceptsAllRemote: false, iconURL };
      for (const node of matches) { addNode(region, node); addNode(test, node); }
      groups.push(region, test);
      primary.choices.push(name);
    }
  }

  const visited = new Set<string>();
  const path = new Set<string>();
  function removeCycles(group: PlannedGroup) {
    if (visited.has(group.name)) return;
    path.add(group.name);
    group.choices = group.choices.filter((name) => {
      if (path.has(name)) return false;
      const child = groupMap.get(name);
      if (child) removeCycles(child);
      return true;
    });
    path.delete(group.name);
    visited.add(group.name);
  }
  for (const group of groups) removeCycles(group);
  for (const group of groups) if (!group.choices.length && !group.remoteIDs.length) group.choices = [direct];
  const blocking = new Set(groups.filter(group => isBlock(group.choices[0] ?? '')).map(group => group.sourceName));
  return { groups, mapping, primary: primary.name, blocking };
}

function remoteProvider({ id, source }: RemoteSource) {
  return { type: 'http', url: source.urlString, path: `./providers/${id}.yaml`, interval: 86400,
    ...(source.requestOptions?.userAgent ? { header: { 'User-Agent': [source.requestOptions.userAgent] } } : {}) };
}

function networkSettings(snapshot: AppSnapshot, scheme: RuleScheme) {
  const follow = snapshot.settings.dnsProtectionMode === 'followScheme';
  const strict = snapshot.settings.dnsProtectionMode === 'strict';
  const network = scheme.networkSettings;
  const direct = follow && network?.directDNSServers?.length ? network.directDNSServers :
    follow && network?.dnsServers?.length ? network.dnsServers : ['223.5.5.5', '119.29.29.29'];
  const remote = follow && network?.fallbackDNSServers?.length ? network.fallbackDNSServers : ['https://8.8.8.8/dns-query', 'https://1.1.1.1/dns-query'];
  return { strict, follow, direct, remote, network };
}

function clashProfile(
  nodes: Fields[], groups: PlannedGroup[], tailnet: ReturnType<typeof writeTailnets>, rules: ReturnType<typeof exportRouting>,
  remotes: RemoteSource[], dns: ReturnType<typeof networkSettings>, dnsGroup: string | undefined, target: ClientTarget,
) {
  const policies = groups.map(group => {
    const kind = ['select', 'url-test', 'fallback', 'load-balance', 'relay'].includes(group.kind) ? group.kind : group.kind === 'smart' && target !== 'clash' && target !== 'karing' ? 'smart' : 'select';
    return {
      name: group.name, type: kind,
      ...(group.choices.length ? { proxies: group.choices } : {}),
      ...(group.remoteIDs.length ? { use: group.remoteIDs,
        ...!group.acceptsAllRemote ? { filter: exactPattern(group.remoteNames) } : {} } : {}),
      ...(['url-test', 'fallback', 'load-balance'].includes(kind) ? { url: group.testURL || testURL, interval: group.interval ?? 300 } : {}),
      ...(kind === 'url-test' ? { tolerance: group.tolerance ?? 50 } : {}),
      ...(group.iconURL ? { icon: group.iconURL } : {}),
    };
  });
  const resolver = dnsGroup ? [dns.remote[0] + '#' + dnsGroup + '&ecs=' + clientSubnet + '&ecs-override=true'] :
    dns.follow ? dns.direct : dns.strict ? dns.remote : localEncryptedDNS;
  const useTUN = dns.strict && mihomoTargets.has(target);
  return {
    mode: 'rule', 'mixed-port': 7890, 'allow-lan': false, 'log-level': 'info', ipv6: dns.network?.ipv6 ?? true,
    dns: { enable: true, ipv6: dns.network?.ipv6 ?? true, ...(dns.follow ? {} : { 'enhanced-mode': 'fake-ip',
      'fake-ip-filter': ['*.lan', '+.local', '+.msftconnecttest.com', '+.msftncsi.com'] }), nameserver: resolver,
      'default-nameserver': dns.direct.filter(value => isIP(value)).length ? dns.direct.filter(value => isIP(value)) : ['223.5.5.5'],
      ...(dns.follow && dns.network?.fallbackDNSServers?.length ? { fallback: dns.remote } : {}),
      ...(!dns.follow ? { 'proxy-server-nameserver': localEncryptedDNS } : {}),
      ...(dnsGroup && !dns.strict ? { 'direct-nameserver': localEncryptedDNS } : {}),
      ...(dns.follow && dns.network?.directDNSServers?.length ? { 'direct-nameserver': dns.network.directDNSServers } : {}) },
    ...(useTUN || dns.network?.tunExcludedRoutes?.length ? { tun: {
      ...(useTUN ? { enable: true, stack: 'mixed', 'auto-route': true, 'auto-detect-interface': true,
        'dns-hijack': ['any:53', 'tcp://any:53'], 'strict-route': true } : {}),
      ...(dns.network?.tunExcludedRoutes?.length ? { 'route-exclude-address': dns.network.tunExcludedRoutes } : {}),
    } } : {}),
    proxies: [...tailnet.proxies, ...nodes],
    'proxy-groups': policies,
    ...(remotes.length ? { 'proxy-providers': Object.fromEntries(remotes.map(item => [item.id, remoteProvider(item)])) } : {}),
    ...(Object.keys(rules.providers).length ? { 'rule-providers': rules.providers } : {}),
    rules: [...tailnet.rules, ...rules.lines, 'MATCH,' + rules.final],
  };
}

function singDNSServer(value: string, tag: string, detour?: string): Fields {
  if (isIP(value)) return { type: 'udp', tag, server: value, ...(detour ? { detour } : {}) };
  try {
    const url = new URL(value);
    const type = ({ 'https:': 'https', 'tls:': 'tls', 'udp:': 'udp', 'tcp:': 'tcp', 'quic:': 'quic', 'h3:': 'h3' } as Record<string, string>)[url.protocol];
    if (type) return { type, tag, server: url.hostname.replace(/^\[|\]$/g, ''),
      ...(!isIP(url.hostname.replace(/^\[|\]$/g, '')) ? { domain_resolver: 'bootstrap' } : {}),
      ...(url.port ? { server_port: Number(url.port) } : {}), ...((type === 'https' || type === 'h3') && url.pathname !== '/' ? { path: url.pathname } : {}),
      ...(detour ? { detour } : {}) };
  } catch { /* Use the local resolver for legacy "system" DNS settings. */ }
  return { type: 'local', tag };
}

function singProfile(
  nodeObjects: ReturnType<typeof singProxy>[], groups: PlannedGroup[], tailnet: ReturnType<typeof writeTailnets>,
  rules: ReturnType<typeof exportRouting>, dns: ReturnType<typeof networkSettings>, primary: string, dnsGroup: string | undefined, target: ClientTarget,
) {
  const end = nodeObjects.flatMap(item => item.endpoint ? [item.endpoint] : []);
  const nodes = nodeObjects.flatMap(item => [item.helper, item.outbound].filter(Boolean) as Fields[]);
  const selector = (group: PlannedGroup) => {
    const test = group.kind === 'url-test';
    const choices = group.choices.filter(choice => choice !== 'reject' || !test);
    const usable = choices.length ? choices : ['direct'];
    return { type: test ? 'urltest' : 'selector', tag: group.name, outbounds: unique(usable),
      ...(test ? { url: group.testURL || testURL, interval: (group.interval ?? 300) + 's', tolerance: group.tolerance ?? 50 } : {}) };
  };
  const rejectsReferenced = groups.some(group => group.choices.includes('reject'));
  const useRemote = !dns.follow && !!dnsGroup;
  const outbounds = [{ type: 'direct', tag: 'direct' }, ...rejectsReferenced ? [{ type: 'block', tag: 'reject' }] : [],
    ...groups.map(selector), ...nodes];
  const server = target === 'hiddify' && !useRemote ? 'local' : useRemote ? 'remote' : 'local';
  const destination = rules.final === 'reject' ? primary : rules.final;
  const routing = [{ action: 'sniff' }, { type: 'logical', mode: 'or', rules: [{ protocol: 'dns' }, { port: 53 }], action: 'hijack-dns' }, ...tailnet.singRules,
    ...rules.objects, ...(rules.final === 'reject' ? [{ action: 'reject' }] : [])];
  return {
    log: { level: 'warn' },
    dns: { servers: [singDNSServer(dns.direct[0], 'local'), { type: 'local', tag: 'bootstrap' },
      ...useRemote || dns.strict ? [singDNSServer(dns.remote[0], 'remote', dnsGroup)] : [], ...tailnet.dnsServers],
      rules: [...tailnet.dnsRules], final: dns.strict && !useRemote ? 'remote' : server, strategy: dns.network?.ipv6 === false ? 'ipv4_only' : 'prefer_ipv4' },
    inbounds: [{ type: 'tun', tag: 'tun-in', address: ['172.19.0.1/30', ...dns.network?.ipv6 === false ? [] : ['fdfe:dcba:9876::1/126']],
      auto_route: true, strict_route: dns.strict, stack: 'mixed', ...(dns.network?.tunExcludedRoutes?.length ? { route_exclude_address: dns.network.tunExcludedRoutes } : {}) }],
    outbounds, ...(end.length || tailnet.endpoints.length ? { endpoints: [...end, ...tailnet.endpoints] } : {}),
    route: { auto_detect_interface: true, default_domain_resolver: 'local', final: destination,
      rules: routing, ...(rules.ruleSets.length ? { rule_set: rules.ruleSets } : {}),
      ...(target === 'sing-box' && rules.ruleSets.length ? { default_http_client: 'tower-rule-set' } : {}) },
    ...(target === 'sing-box' && rules.ruleSets.length ? { http_clients: [{ tag: 'tower-rule-set', engine: 'go', domain_resolver: 'local',
      ...(dnsGroup ? { detour: dnsGroup } : {}) }] } : {}),
  };
}

function renderINI(
  target: ClientTarget, proxyLines: string[], groups: PlannedGroup[], tailnet: ReturnType<typeof writeTailnets>,
  rules: ReturnType<typeof exportRouting>, remotes: RemoteSource[], dns: ReturnType<typeof networkSettings>, title: string,
  inlineProxyNames: Set<string>,
) {
  const refer = new Map(remotes.map(item => [item.id, item.source]));
  const loonFilters = new Map<string, string>();
  const genericGroup = (group: PlannedGroup) => {
    const accepted = target === 'loon' ? ['select', 'url-test', 'fallback', 'load-balance'] : ['select', 'url-test', 'fallback', 'load-balance', 'smart'];
    return accepted.includes(group.kind) ? group.kind : 'select';
  };
  const emitURL = (url: string) => url.replaceAll(',', '%2C');
  const regular = groups.map(group => {
    const kind = genericGroup(group);
    const sources = group.remoteIDs.flatMap(id => refer.get(id)?.urlString ?? []).map(emitURL);
    const icon = group.iconURL ? [`${target === 'loon' ? 'img-url' : 'icon-url'}=${group.iconURL}`] : [];
    const filter = group.acceptsAllRemote ? [] : [`policy-regex-filter=${JSON.stringify(exactPattern(group.remoteNames))}`];
    if (target === 'loon' && sources.length && !group.acceptsAllRemote) {
      loonFilters.set(group.name, 'Tower Filter ' + (loonFilters.size + 1));
    }
    const imports = !sources.length ? [] : target === 'loon' ? loonFilters.has(group.name) ? [loonFilters.get(group.name)!] : group.remoteIDs :
      ['policy-path=' + JSON.stringify(sources.join(',')), 'update-interval=86400', ...filter];
    return `${group.name} = ${[kind, ...group.choices, ...imports,
      ...kind !== 'select' ? ['url=' + safeTestURL(group.testURL), 'interval=' + (group.interval ?? 300)] : [],
      ...kind === 'url-test' ? ['tolerance=' + (group.tolerance ?? 50)] : [], ...icon].join(', ')}`;
  });
  const quanx = groups.map(group => {
    let kind = ({ 'url-test': 'url-latency-benchmark', fallback: 'available', 'load-balance': 'round-robin' } as Record<string, string>)[group.kind] ?? 'static';
    const params = group.remoteIDs.length && group.remoteNames.length ?
      ['server-tag-regex=' + exactPattern(group.remoteNames)] : [];
    // The all-node benchmark must enumerate proxy tags. Only genuinely
    // filtered remote groups need a regex, and it is anchored to their names.
    const fullBenchmark = kind === 'url-latency-benchmark' && group.acceptsAllRemote;
    const choices = fullBenchmark ? unique([...group.choices.filter(name => inlineProxyNames.has(name)), ...group.remoteNames]) : group.choices.slice();
    if (fullBenchmark && !choices.length) kind = 'static';
    if (!choices.length && !params.length) choices.push('direct');
    return `${kind}=${[group.name, ...choices, ...fullBenchmark ? [] : params,
      ...kind === 'url-latency-benchmark' ? ['check-interval=' + (group.interval ?? 300), 'tolerance=' + (group.tolerance ?? 50)] : [],
      ...group.iconURL ? ['img-url=' + group.iconURL] : []].join(', ')}`;
  });
  const section = (name: string, values: string[]) => `[${name}]\n${values.join('\n')}`;
  const safeDNS = (items: string[]) => items.map(cleanLine).filter(value => !/[,;#\[\]]/.test(value));
  if (target === 'quanx') return [
    '# ' + title,
    section('general', ['server_check_url=' + testURL]),
    section('dns', [...dns.strict ? ['no-system'] : [], ...safeDNS(dns.strict ? dns.remote : dns.direct).map(server => /^https:/.test(server) ? 'doh-server=' + server : 'server=' + server)]),
    section('policy', quanx),
    section('server_remote', remotes.map(({ source }) => `${emitURL(source.urlString)}, tag=${confName(source.name)}, update-interval=86400, enabled=true`)),
    section('filter_remote', [...rules.remoteLines, ...rules.remoteLines.length ? [
      `${aclManifest.quanxCatchAll.url}, tag=Tower Final, force-policy=${rules.final}, enabled=true`,
    ] : []]),
    section('rewrite_remote', []), section('server_local', proxyLines),
    section('filter_local', [...rules.lines, ...(!dns.follow && !rules.remoteLines.length ? ['HOST-KEYWORD,.,' + rules.final] : []), 'FINAL,' + rules.final]),
    ...['rewrite_local', 'task_local', 'http_backend', 'mitm'].map(name => section(name, [])),
  ].join('\n\n') + '\n';
  return [
    '# ' + title,
    section('General', ['loglevel = notify', 'ipv6 = ' + (dns.network?.ipv6 ?? true),
      ...(dns.strict && target !== 'loon' ? ['dns-server = 127.0.0.1'] : ['dns-server = ' + safeDNS(dns.direct).join(', ')]),
      ...dns.strict || dns.follow && dns.network?.fallbackDNSServers?.length ? ['encrypted-dns-server = ' + safeDNS(dns.remote).join(', ')] : [],
      ...dns.network?.skipProxyHosts?.length ? ['skip-proxy = ' + safeDNS(dns.network.skipProxyHosts).join(', ')] : [],
      ...dns.network?.tunExcludedRoutes?.length ? ['tun-excluded-routes = ' + safeDNS(dns.network.tunExcludedRoutes).join(', ')] : []]),
    section('Proxy', [...tailnet.proxyLines, ...proxyLines]), section('Proxy Group', regular),
    ...target === 'loon' && remotes.length ? [section('Remote Proxy', remotes.map(({ id, source }) => `${id} = ${emitURL(source.urlString)}`))] : [],
    ...target === 'loon' && loonFilters.size ? [section('Remote Filter', groups.flatMap(group => {
      const filter = loonFilters.get(group.name);
      return filter ? [filter + ' = NameRegex,' + group.remoteIDs.join(',') + ',FilterKey = ' + JSON.stringify(exactPattern(group.remoteNames))] : [];
    }))] : [],
    section('Rule', [...tailnet.rules, ...rules.lines, 'FINAL,' + rules.final]),
    ...target === 'loon' && rules.remoteLines.length ? [section('Remote Rule', rules.remoteLines)] : [],
    ...tailnet.sections,
  ].join('\n\n') + '\n';
}

const lineList = (lines: string[]) => lines.length ? lines.join('\n') + '\n' : '';
function safeTestURL(value?: string | null): string {
  if (value) {
    try {
      const url = new URL(value);
      if (['https:', 'http:'].includes(url.protocol) && !url.username && !url.password && !url.hash && !/[,;]/.test(url.href)) return url.href;
    } catch { /* Use the standard connectivity endpoint. */ }
  }
  return testURL;
}
function fileBase(name: string): string {
  return cleanLine(name).replace(/[\\/<>:"|?*\u0000-\u001f]/g, '-').replace(/^[. ]+|[. ]+$/g, '').slice(0, 100) || 'Tower';
}

export function generateConfiguration(snapshot: AppSnapshot, options: GenerateOptions): GeneratedConfiguration {
  const { target, maskSecrets = true, surfboard = false, runtime = {}, keys = {}, downloads = {} } = options;
  const meta = CLIENT_TARGETS[target];
  const mode = !meta.supportsFullConfiguration ? 'nodesOnly' : !meta.supportsNodesOnly ? 'fullConfiguration' : options.mode;
  const full = mode === 'fullConfiguration';
  const ini = surgeTargets.has(target) || target === 'loon' || target === 'quanx';
  const clash = meta.usesClashFormat || (target === 'shadowrocket' && full);
  const sanitize = (name: string) => ini ? confName(name) : cleanLine(name);
  const sanitizeGroup = (name: string) => clash ? sanitize(name).replaceAll(',', '，') : sanitize(name);
  const title = sanitize(snapshot.settings.configurationName || 'Tower') || 'Tower';
  const sourceMap = new Map(snapshot.subscriptions.map(source => [source.id, source]));
  const excluded = new Set(snapshot.excludedKinds[target] ?? []);
  const candidates = activeNodes(snapshot).filter(node => !(snapshot.settings.filterSubscriptionInfoNodes && isSubscriptionMetadata(node)) &&
    nameMatches(snapshot, node.name));
  const selectedScheme = findScheme(snapshot) ?? bundledSchemes[0];
  const scheme = effectiveScheme(snapshot, selectedScheme, downloads);
  const taken = new Set(['direct', 'reject', 'reject-drop', 'reject-img', 'pass', 'compatible']);
  const skips = new Map<string, number>();
  const mark = (reason: string) => skips.set(reason, (skips.get(reason) ?? 0) + 1);
  const supported: ProxyNode[] = [];
  const originals: ProxyNode[] = [];
  const remoteAllowed = full && snapshot.settings.embedRemoteSubscriptionLinks && meta.supportsEmbeddedRemoteSubscriptions && !surfboard;
  const remotes: RemoteSource[] = remoteAllowed ? snapshot.subscriptions.filter(source => source.isEnabled).flatMap((source, index) => {
    try {
      const url = new URL(source.urlString);
      if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || source.requestOptions?.dnsOverHTTPSURL) return [];
      if (source.requestOptions?.userAgent && !meta.usesClashFormat) return [];
      return [{ source, id: 'tower-source-' + (index + 1) }];
    } catch { return []; }
  }) : [];
  const remoteIDs = new Set(remotes.map(({ source }) => source.id));
  for (const original of candidates) {
    const sourceName = original.sourceID && sourceMap.get(original.sourceID)?.name;
    const base = original.name + (snapshot.settings.appendSubscriptionNameToNodes && sourceName ? ' · ' + sourceName : '');
    const remoteNode = original.sourceID && remoteIDs.has(original.sourceID);
    const node = { ...original, name: remoteNode ? sanitize(original.name) : nextName(sanitize(base) || 'Proxy', taken) };
    const reason = excluded.has(node.kind) ? '该协议已从此客户端导出中排除' : nodeExportReason(node, target, mode, surfboard);
    if (reason) { if (!remoteNode) mark(reason); continue; }
    if ((target === 'anywhere' && !anywhereProxy(node)) || (target === 'v2box' && !proxyURI(node))) { mark('节点链接不能完整表示此代理'); continue; }
    originals.push(original);
    supported.push(node);
  }
  const inlineNodes = supported.filter(node => !node.sourceID || !remoteIDs.has(node.sourceID));
  const warnings: string[] = [];
  let content = '';
  let anywhereAJSON: string | null = null;
  let fileExtension = full ? (clash ? 'yaml' : meta.fileExtension) : meta.usesClashFormat ? 'yaml' : 'txt';
  if (!full) {
    if (meta.usesClashFormat) content = yamlDocument({ proxies: inlineNodes.map(node => clashProxy(node, target)) });
    else if (target === 'anywhere') {
      const proxies = supported.flatMap(node => { const item = anywhereProxy(node); return item ? [item] : []; });
      content = lineList(proxies.filter((proxy): proxy is string => typeof proxy === 'string'));
      if (proxies.some(proxy => typeof proxy !== 'string')) {
        anywhereAJSON = JSON.stringify({ anywhere: 1, name: title, proxies }) + '\n';
        warnings.push('跳过证书校验的节点仅在 Anywhere AJSON 导入中可用。');
      }
    } else if (target === 'v2box' || target === 'shadowrocket') {
      const structured = target === 'shadowrocket' && supported.some(node => !proxyURI(node) || ['ssh', 'trusttunnel', 'masque'].includes(node.kind));
      if (structured) {
        fileExtension = 'yaml';
        content = yamlDocument({ proxies: supported.map(node => clashProxy(node, target)) });
      } else content = Buffer.from(supported.flatMap(node => proxyURI(node) ?? []).join('\n')).toString('base64');
    } else if (target === 'hiddify') {
      const structured = supported.some(node => node.shadowTLS || !proxyURI(node) || node.kind === 'ssh');
      if (structured) {
        fileExtension = 'json';
        content = JSON.stringify({ outbounds: supported.flatMap(node => {
          const { helper, outbound } = singProxy(node, target, nextName(node.name + ' §hide§ ShadowTLS', taken));
          return [helper, outbound].filter(Boolean);
        }) }, null, 2) + '\n';
      } else content = lineList(supported.flatMap(node => proxyURI(node) ?? []));
    } else content = lineList(supported.map(node => target === 'loon' ? loonNode(node) : target === 'quanx' ? quanXNode(node) : surgeNode(node, surfboard)));
  } else {
    const { groups, mapping, primary, blocking } = groupPlan(scheme, target, snapshot, supported, originals, remotes, runtime, taken, sanitizeGroup, surfboard);
    const dns = networkSettings(snapshot, scheme);
    let dnsGroup: string | undefined;
    if (!dns.follow && (mihomoTargets.has(target) || meta.usesSingBoxFormat) && (inlineNodes.length || remotes.length)) {
      const nodeNames = new Set(inlineNodes.map(node => node.name));
      dnsGroup = groups.find(group => group.kind === 'url-test' && group.choices.length && group.choices.every(name => nodeNames.has(name)) && !/[#&]/.test(group.name))?.name;
      if (!dnsGroup) {
        dnsGroup = nextName('DNS 自动选择', taken);
        groups.push({ name: dnsGroup, sourceName: dnsGroup, kind: 'url-test', members: [], choices: [...nodeNames],
          remoteIDs: remotes.map(item => item.id), remoteNames: [], acceptsAllRemote: true });
      }
    }
    const policies = new Map(mapping);
    originals.forEach((node, index) => { if (!policies.has(node.name)) policies.set(node.name, supported[index].name); });
    const rules = exportRouting({ target: target === 'shadowrocket' ? 'shadowrocket' : target,
      rules: scheme.rulesets, groupNames: policies, primary, blockingGroups: blocking,
      settings: snapshot.settings, downloads, protectedDNS: dns.follow || !!dnsGroup, surfboard });
    const tailnet = writeTailnets(snapshot.tailnets, keys, target, maskSecrets, taken, surfboard);
    warnings.push(...rules.blockedWarnings, ...rules.warnings, ...tailnet.warnings);
    if (remotes.length) warnings.push('远程代理集合由客户端自行下载，塔台的节点筛选和改名只应用于本地写入的节点。');
    for (const group of groups.filter(group => group.kind === 'unsupported' || group.kind === 'ssid' || group.kind === 'relay' && !clash)) {
      warnings.push(`策略组“${group.name}”的类型无法完整表示，已保留可选成员。`);
    }
    if (clash) content = yamlDocument(clashProfile(inlineNodes.map(node => clashProxy(node, target)), groups, tailnet, rules, remotes, dns, dnsGroup, target));
    else if (meta.usesSingBoxFormat) {
      const nodes = inlineNodes.map(node => singProxy(node, target, node.shadowTLS ? nextName(node.name + ' ShadowTLS', taken) : undefined));
      content = JSON.stringify(singProfile(nodes, groups, tailnet, rules, dns, primary, dnsGroup, target), null, 2) + '\n';
    } else if (target === 'egern') {
      const remoteURLs = new Map(remotes.map(({ source, id }) => [id, source.urlString]));
      content = yamlDocument({
        proxies: inlineNodes.map(egernProxy),
        policy_groups: groups.map(group => {
          const kind = ({ 'url-test': 'auto_test', 'load-balance': 'load_balance', fallback: 'fallback', smart: 'smart' } as Record<string, string>)[group.kind] ?? 'select';
          return { [kind]: { name: group.name, ...(group.choices.length ? { policies: group.choices } : {}),
            ...group.remoteIDs.length ? { urls: group.remoteIDs.map(id => remoteURLs.get(id)!), update_interval: 86400,
              ...!group.acceptsAllRemote ? { filter: exactPattern(group.remoteNames) } : {} } : {},
            ...kind === 'auto_test' || kind === 'fallback' ? { interval: group.interval ?? 300, latency_test_url: safeTestURL(group.testURL) } : {},
            ...kind === 'auto_test' ? { tolerance: group.tolerance ?? 50 } : {},
            ...kind === 'load_balance' ? { algorithm: 'hash' } : {},
            ...group.iconURL ? { icon: group.iconURL } : {} } };
        }),
        rules: [...rules.objects, { default: { policy: rules.final } }],
      });
    } else {
      const lines = inlineNodes.map(node => target === 'loon' ? loonNode(node) : target === 'quanx' ? quanXNode(node) : surgeNode(node, surfboard));
      if (surgeTargets.has(target)) tailnet.sections.unshift(...inlineNodes.filter(node => node.kind === 'wireguard').map(surgeWireguard));
      content = renderINI(target, lines, groups, tailnet, rules, remotes, dns, title, new Set(inlineNodes.map(node => node.name)));
    }
    if (!inlineNodes.length && !remotes.length && !tailnet.proxyLines.length && !tailnet.proxies.length && !tailnet.endpoints.length && scheme.rulesets.every(rule => !rulesetLines(rule, downloads).length)) content = '';
  }
  const skippedReasons = [...skips].map(([reason, count]) => ({ reason, count }));
  const supportedCount = inlineNodes.length;
  return {
    target, contentMode: mode, fileName: `${fileBase(title)}-${target}.${fileExtension}`, fileExtension,
    mimeType: fileExtension === 'json' ? 'application/json' : fileExtension === 'yaml' ? 'application/yaml' : 'text/plain',
    content, supportedCount, skippedCount: skippedReasons.reduce((sum, item) => sum + item.count, 0), skippedReasons, warnings,
    remoteSourceCount: remotes.length, hasExportableProxies: Boolean(supportedCount || remotes.length), anywhereAJSON,
    directImportURL: full && target === 'quanx' ? null : options.directImportURL ?? null, generatedAt: new Date().toISOString(),
  };
}
