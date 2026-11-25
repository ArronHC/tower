import { load } from 'js-yaml';
import { describe, expect, it } from 'vitest';
import { CLIENT_TARGETS, DEFAULT_CLIENT_ORDER } from '../../../shared/src/index.js';
import type { AppSnapshot } from '../../../shared/src/index.js';
import { generateConfiguration } from '../services/configGenerator.js';
import { confName } from '../services/nodeExport.js';
import { parseRuleScheme } from '../services/ruleSchemeParser.js';
import { initialSnapshot } from '../store/appStateService.js';
import { node, testKey, uuid } from './fixtures.js';

function fixtureSnapshot(): AppSnapshot {
  const snapshot = initialSnapshot();
  const scheme = parseRuleScheme('[Proxy Group]\nProxy = select, Auto, DIRECT, include-all-proxies=true\nAuto = url-test, include-all-proxies=true\n[Rule]\nDOMAIN,example.com,Proxy\nFINAL,Proxy',
    { id: 'fixture' });
  snapshot.importedSchemes.push(scheme);
  snapshot.selectedPresetID = scheme.id;
  snapshot.nodes = [node()];
  return snapshot;
}

describe('configuration families', () => {
  it.each(DEFAULT_CLIENT_ORDER)('generates valid structured exports for %s and normalizes unavailable modes', target => {
    const snapshot = fixtureSnapshot();
    for (const requested of ['fullConfiguration', 'nodesOnly'] as const) {
      const config = generateConfiguration(snapshot, { target, mode: requested });
      expect(config.supportedCount).toBe(1);
      expect(config.skippedCount).toBe(0);
      expect(config.hasExportableProxies).toBe(true);
      expect(config.content.length).toBeGreaterThan(5);
      if (config.fileExtension === 'yaml') expect(load(config.content)).toHaveProperty('proxies');
      else if (config.fileExtension === 'json') expect(JSON.parse(config.content)).toHaveProperty('outbounds');
      else if (config.contentMode === 'fullConfiguration') {
        expect(config.content).toContain(target === 'quanx' ? '[server_local]' : '[Proxy]');
      } else if (target === 'v2box' || target === 'shadowrocket') {
        expect(Buffer.from(config.content, 'base64').toString()).toMatch(/^ss:\/\//);
      }
      expect(config.contentMode).toBe(requested === 'fullConfiguration' && !CLIENT_TARGETS[target].supportsFullConfiguration ? 'nodesOnly' :
        requested === 'nodesOnly' && !CLIENT_TARGETS[target].supportsNodesOnly ? 'fullConfiguration' : requested);
    }
  });
  it('quotes YAML names and substitutes every unsafe INI name delimiter', () => {
    const snapshot = fixtureSnapshot();
    snapshot.nodes[0].name = '[Injected]\r\n=Bad, # Foo; // \"Box\"';
    const escaped = confName(snapshot.nodes[0].name);
    expect(escaped).toBe('［Injected］ -Bad， ＃ Foo； ／／ "Box"');
    const ini = generateConfiguration(snapshot, { target: 'surge', mode: 'fullConfiguration' });
    expect(ini.content).toContain(escaped + ' = ss,');
    expect(ini.content).not.toContain('[Injected]');
    const yaml = generateConfiguration(snapshot, { target: 'clash-mi', mode: 'fullConfiguration' });
    expect(load(yaml.content)).toMatchObject({ proxies: [{ name: '[Injected] =Bad, # Foo; // "Box"' }] });
  });
  it('enumerates all-node QuanX benchmark tags and leaves full direct import unset', () => {
    const snapshot = fixtureSnapshot();
    snapshot.nodes.push(node({ id: 'two', name: 'Second Node' }));
    const result = generateConfiguration(snapshot, { target: 'quanx', mode: 'fullConfiguration', directImportURL: 'ignored' });
    const benchmark = result.content.split('\n').find(line => line.startsWith('url-latency-benchmark='));
    expect(benchmark).toContain('Tokyo Fixture, Second Node');
    expect(benchmark).not.toContain('server-tag-regex');
    expect(benchmark).not.toMatch(/, direct(?:,|$)/);
    expect(result.directImportURL).toBeNull();
    snapshot.nodes = [];
    expect(generateConfiguration(snapshot, { target: 'quanx', mode: 'fullConfiguration' }).content).not.toContain('url-latency-benchmark=');
  });
  it('skips incompatible protocols and prioritizes warnings for skipped blocking rules', () => {
    const snapshot = fixtureSnapshot();
    snapshot.nodes.push(node({ id: 'vless', kind: 'vless', uuid, transport: 'grpc', tls: true }),
      node({ id: 'mask', kind: 'masque', username: 'fixture', masque: { mode: 'basicConnectIP' } }));
    snapshot.importedSchemes[0].rulesets.unshift(
      { groupName: 'DIRECT', resource: { type: 'inline', rule: 'SUBNET,10.0.0.0/8' } },
      { groupName: 'REJECT', resource: { type: 'inline', rule: 'UID,1000' } },
    );
    const result = generateConfiguration(snapshot, { target: 'quanx', mode: 'fullConfiguration' });
    expect(result.supportedCount).toBe(1);
    expect(result.skippedCount).toBe(2);
    expect(result.warnings[0]).toContain('这些请求不会被拦截');
    const surfboard = generateConfiguration(snapshot, { target: 'surge', mode: 'fullConfiguration', surfboard: true });
    expect(surfboard.skippedReasons.some(item => item.reason.includes('Surfboard'))).toBe(true);
  });
  it('honors metadata filtering, node naming, exclusions, and embedded subscriptions', () => {
    const snapshot = fixtureSnapshot();
    snapshot.subscriptions = [{ id: 'sub', name: 'Example Source', urlString: 'https://sub.example/test', isEnabled: true, createdAt: '2026-11-25' }];
    snapshot.nodes = [node({ sourceID: 'sub', server: '192.0.2.1' }), node({ id: 'other', name: 'Fixture Two' }), node({ id: 'notice', sourceID: 'sub', name: '套餐到期' })];
    snapshot.settings.appendSubscriptionNameToNodes = true;
    snapshot.nodeExportNameFilter = { includeKeywords: ['Tokyo'], excludeKeywords: [] };
    let result = generateConfiguration(snapshot, { target: 'clash-mi', mode: 'nodesOnly' });
    expect(load(result.content)).toMatchObject({ proxies: [{ name: '🇯🇵 Tokyo Fixture · Example Source' }] });
    snapshot.excludedNodeIDs = [snapshot.nodes[0].id];
    result = generateConfiguration(snapshot, { target: 'clash-mi', mode: 'nodesOnly' });
    expect(result.supportedCount).toBe(0);
    snapshot.settings.embedRemoteSubscriptionLinks = true;
    result = generateConfiguration(snapshot, { target: 'clash-mi', mode: 'fullConfiguration' });
    expect(result.remoteSourceCount).toBe(1);
    expect(load(result.content)).toHaveProperty('proxy-providers');
    snapshot.excludedNodeIDs = [];
    snapshot.settings.embedRemoteSubscriptionLinks = false;
    snapshot.excludedKinds['clash-mi'] = ['ss'];
    result = generateConfiguration(snapshot, { target: 'clash-mi', mode: 'nodesOnly' });
    expect(result.skippedCount).toBe(1);
  });
  it('expands GEOIP into sing-box CIDRs and keeps MRS rules opted in', () => {
    const snapshot = initialSnapshot();
    snapshot.nodes = [node()];
    const json = JSON.parse(generateConfiguration(snapshot, { target: 'sing-box', mode: 'fullConfiguration' }).content);
    expect(json.route.rules.some((rule: { ip_cidr?: string[] }) => (rule.ip_cidr?.length ?? 0) > 100)).toBe(true);
    snapshot.settings.preferRuleSets = true;
    const clash = load(generateConfiguration(snapshot, { target: 'clash-mi', mode: 'fullConfiguration' }).content) as Record<string, unknown>;
    expect(Object.values(clash['rule-providers'] as Record<string, object>).some(value => 'format' in value && value.format === 'mrs')).toBe(true);
  });
  it('uses a proxy-only DNS group for terminal GEOIP and preserves explicit no-resolve', () => {
    const snapshot = fixtureSnapshot();
    const scheme = snapshot.importedSchemes[0];
    scheme.rulesets.splice(1, 0, { groupName: 'DIRECT', resource: { type: 'inline', rule: 'GEOIP,CN' } });
    snapshot.settings.dnsProtectionMode = 'strict';
    const mihomo = load(generateConfiguration(snapshot, { target: 'clash-mi', mode: 'fullConfiguration' }).content) as Record<string, unknown>;
    expect(mihomo).toMatchObject({ dns: { nameserver: ['https://8.8.8.8/dns-query#♻️ Auto&ecs=114.114.114.0/24&ecs-override=true'] }, tun: { 'strict-route': true } });
    expect(mihomo.rules).toContain('GEOIP,CN,DIRECT');
    const stash = load(generateConfiguration(snapshot, { target: 'clash', mode: 'fullConfiguration' }).content) as Record<string, unknown>;
    expect(stash.rules).toContain('GEOIP,CN,DIRECT,no-resolve');
    scheme.rulesets[1].options = ['no-resolve'];
    expect(generateConfiguration(snapshot, { target: 'clash-mi', mode: 'fullConfiguration' }).content).toContain('GEOIP,CN,DIRECT,no-resolve');
  });
  it('preserves insecure-capable Anywhere nodes in AJSON only', () => {
    const snapshot = fixtureSnapshot();
    snapshot.nodes.push(node({ kind: 'vless', id: 'insecure', name: 'TLS Fixture', uuid, tls: true, skipCertificateVerification: true }));
    const output = generateConfiguration(snapshot, { target: 'anywhere', mode: 'nodesOnly' });
    expect(output.supportedCount).toBe(2);
    expect(output.content).not.toContain('vless://');
    expect(JSON.parse(output.anywhereAJSON!)).toMatchObject({ anywhere: 1, proxies: ['ss://' + output.content.split('ss://')[1].trim(),
      { protocol: 'vless', security: { insecure: true } }] });
  });
});

describe('tailnet export', () => {
  it.each(['surge', 'clash', 'clash-mi', 'sing-box'] as const)('pins routes for %s independently of selector groups', target => {
    const snapshot = fixtureSnapshot();
    snapshot.tailnets = [{ id: uuid, name: 'Home Tailnet', isEnabled: true, subnets: ['192.168.8.0/24'], magicDNSSuffix: 'home.ts.net' }];
    const keys = { [uuid]: testKey };
    const masked = generateConfiguration(snapshot, { target, mode: 'fullConfiguration', keys });
    const exported = generateConfiguration(snapshot, { target, mode: 'fullConfiguration', keys, maskSecrets: false });
    expect(masked.content).not.toContain(testKey);
    expect(exported.content).toContain('home.ts.net');
    expect(exported.content).toContain('100.64.0.0/10');
    expect(exported.content).toContain('fd7a:115c:a1e0::/48');
    expect(exported.content.indexOf('192.168.8.0/24')).toBeLessThan(exported.content.indexOf('example.com'));
    if (target !== 'clash') expect(exported.content).toContain('tower-tailnet-' + uuid.slice(0, 8));
    if (target === 'surge') {
      expect(exported.content).toContain('interactive-login = true');
      expect(exported.content).not.toContain(testKey);
      expect(exported.content.split('[Proxy Group]')[1].split('[Rule]')[0]).not.toContain('Home Tailnet');
    } else {
      expect(masked.content).toContain('********');
      expect(exported.content).toContain(testKey);
      if (target === 'sing-box') {
        expect(JSON.parse(exported.content).outbounds.every((item: { outbounds?: string[] }) => !item.outbounds?.includes('Home Tailnet'))).toBe(true);
      } else expect(JSON.stringify((load(exported.content) as Record<string, unknown>)['proxy-groups'])).not.toContain('Home Tailnet');
    }
    if (CLIENT_TARGETS[target].supportsNodesOnly) {
      expect(generateConfiguration(snapshot, { target, mode: 'nodesOnly', keys }).content).not.toContain('Home Tailnet');
    }
  });
});
