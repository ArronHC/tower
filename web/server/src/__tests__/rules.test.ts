import { describe, expect, it } from 'vitest';
import type { RuleSchemeRuleset } from '../../../shared/src/index.js';
import { initialSnapshot } from '../store/appStateService.js';
import { parseRuleScheme } from '../services/ruleSchemeParser.js';
import {
  bundledSchemes, detectMatchingOrderConflicts, effectiveScheme, matchingGroupNames,
  matchingOrderAfterDragging, reordering, ruleCatalog, rulesetLines,
} from '../services/ruleSchemeService.js';
import { policyGroupEmoji, policyGroupIconURL } from '../services/policyGroupIcons.js';

const rule = (groupName: string, rule: string, origin?: RuleSchemeRuleset['origin']): RuleSchemeRuleset =>
  ({ groupName, origin, resource: { type: 'inline', rule } });

describe('rule scheme parsing', () => {
  it('loads all three bundled ACL4SSR schemes and every rule list offline', () => {
    expect(bundledSchemes.map(scheme => scheme.id)).toEqual(['acl4ssr-default', 'acl4ssr-full', 'acl4ssr-mini']);
    for (const scheme of bundledSchemes) {
      expect(scheme.groups[0].name).toContain('节点选择');
      expect(scheme.rulesets.every(rule => rule.resource.type !== 'remote' && rulesetLines(rule).length)).toBe(true);
    }
    expect(ruleCatalog.some(item => item.source === 'acl4ssr' && item.name === 'Apple')).toBe(true);
    expect(ruleCatalog.some(item => item.source === 'blackmatrix7' && item.name === 'Netflix')).toBe(true);
    expect(ruleCatalog.find(item => item.name === 'Netflix')?.emoji).toBe('🎞️');
  });
  it('imports subconverter node filters, Clash providers, and Surge rule conditions', () => {
    const sub = parseRuleScheme('[custom]\ncustom_proxy_group=Japan`url-test`(?i)japan`https://test.example/204`300,,50\nruleset=Japan,[]FINAL');
    expect(sub.groups[0]).toMatchObject({ kind: 'url-test', members: [{ type: 'nodePattern', pattern: '(?i)japan' }], interval: 300, tolerance: 50 });
    const clash = parseRuleScheme(`proxy-groups:
  - {name: Proxy, type: select, proxies: [DIRECT], include-all: true}
rule-providers:
  list: {type: http, url: 'https://rules.example/rules.yaml'}
rules:
  - 'RULE-SET,list,Proxy,no-resolve'
  - 'MATCH,Proxy'`);
    expect(clash.rulesets[0]).toMatchObject({ groupName: 'Proxy', resource: { type: 'remote', url: 'https://rules.example/rules.yaml' }, options: ['no-resolve'] });
    const surge = parseRuleScheme('[Proxy Group]\nMain = select, DIRECT, include-all-proxies=true\n[Rule]\nAND,((DOMAIN,a.example),(NOT,((DOMAIN,b.example)))),Main\nFINAL,Main');
    expect(surge.rulesets[0].resource).toEqual({ type: 'inline', rule: 'AND,((DOMAIN,a.example),(NOT,((DOMAIN,b.example))))' });
    expect(() => parseRuleScheme('[custom]\nruleset=Main,http://rules.example/a.list')).toThrow('HTTPS');
  });
  it('uses explicit, service, and region emoji in order and resolves FluentCircle images', () => {
    expect(policyGroupEmoji({ name: '🚀 Netflix HK', kind: 'select', members: [] })).toBe('🚀');
    expect(policyGroupEmoji({ name: 'Netflix HK', kind: 'select', members: [] })).toBe('🎥');
    expect(policyGroupEmoji({ name: '日本节点', kind: 'url-test', members: [] })).toBe('🇯🇵');
    expect(policyGroupIconURL('🚀', true)).toMatch(/FluentCircle\/1f680\.png$/);
    expect(policyGroupIconURL('🇺🇲', true)).toBe(policyGroupIconURL('🇺🇸', true));
  });
});

describe('rule priority', () => {
  it('moves one group without moving supplements or trailing fallbacks', () => {
    const input = [
      rule('Fix', 'DOMAIN,fix.example', 'supplement'), rule('A', 'DOMAIN,a.example'), rule('B', 'DOMAIN,b.example'),
      rule('A', 'DOMAIN,a2.example'), rule('C', 'DOMAIN,c.example'), rule('B', 'GEOIP,CN'), rule('A', 'FINAL'),
    ];
    expect(matchingGroupNames(input)).toEqual(['A', 'B', 'C']);
    const old = ['A', 'B', 'empty', 'C'];
    const order = matchingOrderAfterDragging('B', old, ['A', 'empty', 'C', 'B'], ['A', 'B', 'C']);
    expect(order).toEqual(['A', 'C', 'B']);
    expect(reordering(input, order, 'B')).toEqual([input[0], input[1], input[3], input[4], input[2], input[5], input[6]]);
    expect(matchingOrderAfterDragging('empty', old, ['empty', 'A', 'B', 'C'], ['A', 'B', 'C'])).toEqual(['A', 'B', 'C']);
    expect(matchingOrderAfterDragging('C', old, ['C', 'A', 'B', 'empty'], ['A', 'B', 'C'])).toEqual(['C', 'A', 'B']);
  });
  it('flags a domain that changes winner and ignores a domain still claimed by an earlier group', () => {
    const rules = [rule('A', 'DOMAIN-SUFFIX,example.com'), rule('B', 'DOMAIN,app.example.com'), rule('B', 'DOMAIN,stay.example.com'),
      rule('First', 'DOMAIN,stay.example.com')];
    expect(detectMatchingOrderConflicts(['First', 'A', 'B'], ['First', 'B', 'A'], rules, 'B')).toEqual([
      { movedGroup: 'B', otherGroup: 'A', sampleDomains: ['app.example.com'], direction: 'shadowedByMoved' },
    ]);
  });
  it('keeps group display and routing orders independent and inserts supplemental rules first', () => {
    const snapshot = initialSnapshot();
    const scheme = parseRuleScheme('[Proxy Group]\nA = select, DIRECT\nB = select, REJECT\n[Rule]\nDOMAIN-SUFFIX,example.com,A\nDOMAIN,b.example,B\nGEOIP,CN,A\nFINAL,A', { id: 'sample' });
    snapshot.ruleSchemeCustomizations.sample = { groupOrder: ['B', 'A'], renamedGroups: { B: 'Block' } };
    snapshot.customRuleFlows = [{ id: 'supplement', schemeID: scheme.id, name: 'Supplement', policyName: 'B',
      rulesText: 'DOMAIN-SUFFIX,more.example\nDOMAIN,one.more.example', isEnabled: true, isGroupSupplement: true }];
    const effective = effectiveScheme(snapshot, scheme);
    expect(effective.groups.map(group => group.name)).toEqual(['Block', 'A']);
    expect(effective.rulesets.map(rule => rule.resource.type === 'inline' && rule.resource.rule)).toEqual([
      'DOMAIN,one.more.example', 'DOMAIN-SUFFIX,more.example', 'DOMAIN-SUFFIX,example.com', 'DOMAIN,b.example', 'GEOIP,CN', 'FINAL',
    ]);
  });
});
