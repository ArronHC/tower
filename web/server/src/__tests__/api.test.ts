import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import type { Server, RequestListener } from 'node:http';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../app.js';
import { AppStateService } from '../store/appStateService.js';
import { PersistenceStore } from '../store/persistenceStore.js';
import { ssURI, testKey, uuid } from './fixtures.js';

let directory: string;
let store: PersistenceStore;
let state: AppStateService;
let app: ReturnType<typeof createApp>;
let base: string;
let servers: Server[];
let providerName: string;

async function listen(handler: RequestListener): Promise<string> {
  const server = createServer(handler);
  servers.push(server);
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

beforeEach(async () => {
  servers = [];
  directory = mkdtempSync(path.join(tmpdir(), 'tower-web-'));
  store = new PersistenceStore(directory);
  state = new AppStateService({ store, host: '127.0.0.1' });
  app = createApp({ state, staticDir: directory });
  base = await listen(app);
  state.setListenPort(Number(new URL(base).port));
  providerName = 'Japan Node';
});

afterEach(async () => {
  app.locals.directImports.close();
  await Promise.all(servers.map(server => new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()))));
  rmSync(directory, { recursive: true, force: true });
});

function request(route: string, method = 'GET', input?: unknown, headers?: Record<string, string>): Promise<Response> {
  return fetch(base + route, {
    method, body: input === undefined ? undefined : JSON.stringify(input),
    headers: input === undefined ? headers : { 'Content-Type': 'application/json', ...headers },
  });
}

async function json(route: string, method = 'GET', input?: unknown, status = 200) {
  const response = await request(route, method, input);
  const body = JSON.parse(await response.text());
  expect(response.status, JSON.stringify(body)).toBe(status);
  return body;
}

describe('Express API', () => {
  it('serves health, state, map data, country choices, and the packaged images', async () => {
    expect(await json('/api/health')).toMatchObject({ ok: true, version: '1.0.34', resourcesReady: true });
    expect(await json('/api/state')).toMatchObject({ snapshot: { selectedPresetID: 'acl4ssr-default' }, stats: { totalNodeCount: 0 } });
    expect(await json('/api/world-map')).toMatchObject({ columns: 220, rows: 149 });
    expect((await json('/api/countries')).some((item: { code: string }) => item.code === 'JP')).toBe(true);
    for (const route of ['/assets/clients/stash.png', '/assets/tower-icon.png']) {
      const response = await request(route);
      expect(response.status).toBe(200);
      expect(response.headers.get('content-type')).toBe('image/png');
      await response.arrayBuffer();
    }
    expect((await request('/assets/clients/invalid.txt')).status).toBe(404);
    expect((await request('/api/state', 'GET', undefined, { Origin: 'http://another.example' })).status).toBe(403);
    expect((await request('/api/unknown')).status).toBe(404);
    expect(await json('/api/nodes/import', 'POST', {}, 400)).toMatchObject({ code: 'INVALID_INPUT' });
    writeFileSync(path.join(directory, 'index.html'), '<main>Tower SPA fixture</main>');
    const spa = await listen(createApp({ state, staticDir: directory }));
    expect(await (await fetch(spa + '/subscriptions')).text()).toBe('<main>Tower SPA fixture</main>');
    expect((await fetch(spa + '/api/missing')).status).toBe(404);
  });

  it('creates, edits, probes, shares, filters, and removes local nodes', async () => {
    const uri = ssURI('1.1.1.1');
    expect(await json('/api/sources/detect', 'POST', { input: uri })).toMatchObject({ kind: 'proxyNodes', detectedNodeCount: 1 });
    const imported = await json('/api/nodes/import', 'POST', { content: uri + '\nss://broken' }, 201);
    expect(imported.skippedCount).toBe(1);
    const id = imported.addedNodes[0].id;
    expect(await json('/api/nodes/' + id, 'PATCH', { countryOverride: 'CA', name: 'Renamed', excluded: true }))
      .toMatchObject({ node: { name: 'Renamed' }, state: { snapshot: { excludedNodeIDs: [id] }, runtime: { [id]: { countryCode: 'CA' } } } });
    expect(await json('/api/nodes/resolve-geo', 'POST', { nodeIds: [id] })).toMatchObject({ runtime: { [id]: { countryCode: 'CA', asn: 13335 } } });
    await json('/api/nodes/exclusions', 'POST', { nodeIds: [id], excluded: false });
    expect(await json('/api/nodes/exclusions', 'POST', { excludedNodeIDs: [id, 'missing'] })).toMatchObject({ excludedNodeIDs: [id] });
    const share = await json('/api/nodes/' + id + '/share');
    expect(share.uri).toMatch(/#Renamed$/);
    expect(share.qrDataUrl).toMatch(/^data:image\/png;base64,/);
    expect(await json('/api/nodes/export-filter', 'PUT', { includeKeywords: ['Fixture'], excludeKeywords: [] })).toMatchObject({ nodeExportNameFilter: { includeKeywords: ['Fixture'] } });
    const udp = await json('/api/nodes/import', 'POST', { content: 'hy2://password@192.0.2.1:443#UDP' }, 201);
    const udpID = udp.addedNodes[0].id;
    expect(await json('/api/nodes/latency', 'POST', { mode: 'tcp', nodeIds: [udpID] })).toMatchObject({ results: { [udpID]: { isApplicable: false } } });
    expect(await json('/api/nodes/' + id, 'DELETE')).toMatchObject({ ok: true, state: { snapshot: { excludedNodeIDs: [] } } });
  });

  it('refreshes subscriptions with carried exclusions and supports batch refresh and deletion', async () => {
    const provider = await listen((req, res) => {
      res.setHeader('subscription-userinfo', 'upload=1; download=2; total=100');
      res.end(ssURI('192.0.2.1', providerName + ' ' + req.url));
    });
    const result = await json('/api/subscriptions', 'POST', { urlString: provider + '/first' }, 201);
    const id = result.subscription.id;
    const nodeID = result.state.snapshot.nodes[0].id;
    expect(result.addedNodeCount).toBe(1);
    await json('/api/nodes/' + nodeID, 'PATCH', { countryOverride: 'GB', excluded: true });
    providerName = 'Changed Remark';
    const refreshed = await json('/api/subscriptions/' + id + '/refresh', 'POST');
    const nextNode = refreshed.state.snapshot.nodes[0];
    expect(nextNode.id).not.toBe(nodeID);
    expect(nextNode).toMatchObject({ countryOverride: 'GB', name: 'Changed Remark /first' });
    expect(refreshed.state.snapshot.excludedNodeIDs).toEqual([nextNode.id]);
    await json('/api/subscriptions/' + id, 'PATCH', { name: 'Edited', isEnabled: false });
    expect((await json('/api/subscriptions/refresh-all', 'POST')).results).toEqual([]);
    await json('/api/subscriptions/' + id, 'PATCH', { isEnabled: true, urlString: provider + '/second' });
    expect((await json('/api/subscriptions/refresh-all', 'POST')).results[0]).toMatchObject({ id, name: 'Edited', addedNodeCount: 1 });
    expect((await request('/api/subscriptions', 'POST', { urlString: provider + '/second' })).status).toBe(409);
    expect((await request('/api/nodes/' + nextNode.id, 'DELETE')).status).toBe(404);
    const response = await json('/api/subscriptions/' + id, 'DELETE');
    expect(response.state.snapshot.nodes).toEqual([]);
    expect(response.state.snapshot.excludedNodeIDs).toEqual([]);
  });

  it('validates group drags, returns conflicts, and restores order through customization', async () => {
    expect((await json('/api/rules/schemes')).bundledSchemes).toHaveLength(3);
    expect((await json('/api/rules/catalog?q=netflix&source=acl4ssr'))[0]?.name).toBe('Netflix');
    const imported = await json('/api/rules/schemes/import', 'POST', {
      name: 'Fixture',
      content: '[Proxy Group]\nA = select, DIRECT\nB = select, REJECT\n[Rule]\nDOMAIN-SUFFIX,example.com,A\nDOMAIN,app.example.com,B\nFINAL,A',
    }, 201);
    const id = imported.scheme.id;
    const prefix = '/api/rules/schemes/' + id;
    const drag = await json(prefix + '/reorder-groups', 'POST', { fromIndex: 1, toIndex: 0, orderedGroupNames: ['B', 'A'] });
    expect(drag.previousCustomization).toBeNull();
    expect(drag.customization).toMatchObject({ matchingOrder: ['B', 'A'], groupOrder: ['B', 'A'] });
    expect(drag.conflicts[0]).toMatchObject({ movedGroup: 'B', otherGroup: 'A', sampleDomains: ['app.example.com'] });
    await json(prefix + '/customization', 'PUT', { customization: {}, emojiEnabled: false });
    const result = await json(prefix + '/groups', 'POST', { name: 'Japan', kind: 'url-test', members: [{ type: 'nodePattern', pattern: 'JP' }] }, 201);
    expect(result.state.activeSchemeGroups.map((group: { sourceName: string }) => group.sourceName)).toEqual(['A', 'B', 'Japan']);
    const edited = await json(prefix + '/groups/Japan', 'PATCH', { newName: 'Tokyo', supplementRules: ['DOMAIN,park.example'] });
    expect(edited.state.activeSchemeGroups.at(-1)).toMatchObject({ displayName: 'Tokyo', supplementRules: ['DOMAIN,park.example'] });
    await json(prefix + '/enabled-groups', 'PUT', { enabledGroupNames: ['A', 'B'] });
    expect((await json('/api/rules/schemes')).activeSchemeGroups.at(-1).isEnabled).toBe(false);
    await json(prefix + '/groups/Japan', 'DELETE');
    expect((await request(prefix + '/groups/A', 'DELETE')).status).toBe(400);
    expect((await request(prefix + '/refresh', 'POST')).status).toBe(400);
    await json('/api/rules/selected', 'PUT', { schemeId: 'acl4ssr-mini' });
    await json(prefix, 'DELETE');
    expect((await request('/api/rules/schemes/acl4ssr-default', 'DELETE')).status).toBe(400);
  });

  it('manages local rule sets and flows with exact DTOs', async () => {
    const item = await json('/api/rules/local-sets', 'POST', { name: 'Local', rulesText: 'DOMAIN,local.example' }, 201);
    const id = item.localRuleSet.id;
    expect((await json('/api/rules/local-sets'))[0].id).toBe(id);
    await json('/api/rules/local-sets/' + id, 'PATCH', { rulesText: 'DOMAIN,updated.example' });
    const created = await json('/api/rules/flows', 'POST', {
      schemeID: 'acl4ssr-default', name: 'My Rules', policyName: 'DIRECT', localRuleSetID: id,
    }, 201);
    const flowId = created.flow.id;
    const config = await json('/api/export/generate', 'POST', { target: 'surge', mode: 'fullConfiguration' });
    expect(config.content).toContain('DOMAIN,updated.example,DIRECT');
    await json('/api/rules/flows/' + flowId, 'PATCH', { isEnabled: false });
    const deleted = await json('/api/rules/local-sets/' + id, 'DELETE');
    expect(deleted.state.snapshot.customRuleFlows[0]).toMatchObject({ localRuleSetID: null, rulesText: 'DOMAIN,updated.example' });
    await json('/api/rules/flows/' + flowId, 'DELETE');
  });

  it('keeps keys separate through restart, preview, export, backup and reset', async () => {
    const created = await json('/api/tailnets', 'POST', { name: 'Home', authKey: testKey, magicDNSSuffix: 'home.ts.net' }, 201);
    const id = created.tailnet.id;
    expect(JSON.stringify(created)).not.toContain(testKey);
    const stored = readFileSync(path.join(directory, 'state.json'), 'utf8');
    expect(stored).not.toContain(testKey);
    expect(readFileSync(path.join(directory, 'tailnet-keys.json'), 'utf8')).toContain(testKey);
    for (const file of ['state.json', 'tailnet-keys.json']) expect(statSync(path.join(directory, file)).mode & 0o777).toBe(0o600);
    const restored = new AppStateService({ store });
    expect(restored.getTailnets()[0]).toMatchObject({ id, hasAuthKey: true });
    expect((await json('/api/tailnets'))[0]).toMatchObject({ id, hasAuthKey: true });
    expect(await json('/api/tailnets/' + id, 'PATCH', { name: 'Home Edited' })).toMatchObject({ tailnet: { hasAuthKey: true } });
    await json('/api/export/preferences', 'PUT', { selectedTarget: 'clash', visibleClientTargets: ['clash', 'surge'], clientOrder: ['clash'] });
    await json('/api/settings', 'PATCH', { configurationName: '塔台 Fixture' });
    const preview = await json('/api/export/generate', 'POST', { target: 'clash', mode: 'fullConfiguration' });
    expect(preview.content).toContain('********');
    expect(preview.content).not.toContain(testKey);
    const direct = new URL(preview.directImportURL).searchParams.get('url')!;
    expect(direct).toMatch(/^http:\/\/127\.0\.0\.1:\d+\/import\//);
    expect(await (await fetch(direct)).text()).toContain(testKey);
    const download = await request('/api/export/download?target=clash&mode=fullConfiguration');
    expect(download.headers.get('content-disposition')).toMatch(/^attachment/);
    expect(await download.text()).toContain(testKey);
    const saved = await json('/api/snapshot/export');
    expect(saved.tailnets[0]).not.toHaveProperty('hasAuthKey');
    expect(JSON.stringify(saved)).not.toContain(testKey);
    saved.tailnets[0].authKey = 'tskey-imported-fixture';
    await json('/api/snapshot/import', 'POST', saved);
    expect(readFileSync(path.join(directory, 'state.json'), 'utf8')).not.toContain('tskey-imported-fixture');
    expect((await fetch(direct)).status).toBe(404);
    await json('/api/tailnets/' + id, 'PATCH', { authKey: null });
    expect((await json('/api/tailnets'))[0].hasAuthKey).toBe(false);
    await json('/api/tailnets/' + id, 'DELETE');
    await json('/api/tailnets', 'POST', { name: 'Reset Fixture', authKey: testKey }, 201);
    await json('/api/snapshot/reset', 'POST');
    expect(await json('/api/tailnets')).toEqual([]);
    expect(JSON.parse(readFileSync(path.join(directory, 'tailnet-keys.json'), 'utf8'))).toEqual({});
  });

  it('routes LAN shares by target and User-Agent, responds with AJSON, and rotates tokens', async () => {
    const disabled = await json('/api/lan/status');
    expect((await request('/sub/' + disabled.token + '?target=clash')).status).toBe(503);
    await json('/api/nodes/import', 'POST', { content: ssURI() + '\n' +
      `vless://${uuid}@192.0.2.1:443?security=tls&allowInsecure=1#TLS\nssh://fixture:pass@192.0.2.1:22#SSH` }, 201);
    await json('/api/tailnets', 'POST', { name: 'Home', authKey: testKey }, 201);
    const enabled = await json('/api/lan/toggle', 'POST', { enabled: true });
    const url = '/sub/' + enabled.token;
    expect((await request('/sub/bad?target=clash')).status).toBe(403);
    let response = await request(url, 'GET', undefined, { 'User-Agent': 'v2rayNG/2.3.15' });
    expect(response.headers.get('x-tower-target')).toBe('v2box');
    expect(response.headers.get('profile-title')).toMatch(/^base64:/);
    expect(response.headers.get('subscription-userinfo')).toContain('total=');
    expect(Buffer.from(await response.text(), 'base64').toString()).toContain('ss://');
    response = await request(url, 'GET', undefined, { 'User-Agent': 'Surfboard/2.36.0' });
    const surfboard = await response.text();
    expect(surfboard).not.toContain('ssh,');
    expect(surfboard).not.toContain('tailscale,');
    response = await request(url, 'GET', undefined, { 'User-Agent': 'Anywhere', 'X-Anywhere-Format': 'ajson/1' });
    expect(response.headers.get('content-type')).toContain('application/json');
    expect(await response.json()).toMatchObject({ anywhere: 1, proxies: [expect.any(String), { protocol: 'vless' }] });
    response = await request(url + '?target=stash');
    expect(await response.text()).toContain(testKey);
    const qr = await json('/api/lan/qr?format=surge&content=nodesOnly');
    expect(qr.url).toContain('?target=surge&content=nodesOnly');
    expect(qr.qrDataUrl).toMatch(/^data:image\/png;base64,/);
    const rotated = await json('/api/lan/rotate-token', 'POST');
    expect(rotated.token).not.toBe(enabled.token);
    expect((await request(url + '?target=clash')).status).toBe(403);
    expect(new AppStateService({ store }).lanStatus.token).toBe(rotated.token);
  });
});
