import { createServer } from 'node:http';
import type { AddressInfo } from 'node:net';
import { describe, expect, it, vi } from 'vitest';
import { detectSourceInput, parseProxyURI, parseSubscription } from '../services/subscriptionParser.js';
import { fetchSubscription } from '../services/subscriptionFetcher.js';
import { probeNode } from '../services/nodeLatencyService.js';
import { carriedOverExclusions } from '../store/appStateService.js';
import { node, ssURI, uuid } from './fixtures.js';

describe('subscription parsing', () => {
  it.each([
    [ssURI(), 'ss'],
    ['ssr://' + Buffer.from('127.0.0.1:8388:origin:aes-256-cfb:plain:' + Buffer.from('password').toString('base64url')).toString('base64url'), 'ssr'],
    ['vmess://' + Buffer.from(JSON.stringify({ add: '127.0.0.1', port: 443, id: uuid, ps: 'Fixture', net: 'ws', path: '/ws' })).toString('base64'), 'vmess'],
    [`vless://${uuid}@127.0.0.1:443?type=grpc&security=reality&pbk=test&sid=00&serviceName=ping`, 'vless'],
    ['trojan://pass@127.0.0.1:443?sni=test.example', 'trojan'],
    ['hysteria://127.0.0.1:443?auth=pass&upmbps=50', 'hysteria'],
    ['hy2://pass@127.0.0.1:443,5000-6000?obfs=salamander&obfs-password=token', 'hysteria2'],
    [`tuic://${uuid}:pass@127.0.0.1:443`, 'tuic'],
    ['wg://private@127.0.0.1:51820?publickey=public&address=10.0.0.2%2F32', 'wireguard'],
    ['anytls://pass@127.0.0.1:443', 'anytls'],
    ['snell://pass@127.0.0.1:443?version=4', 'snell'],
    ['socks://user:pass@127.0.0.1:1080#Fixture', 'socks5'],
    ['https://user:pass@127.0.0.1:443#Fixture', 'http'],
    ['ssh://user:pass@127.0.0.1:22', 'ssh'],
    ['trusttunnel://user:pass@127.0.0.1:443?quic=1', 'trusttunnel'],
    ['masque://user:pass@127.0.0.1:443?network=h3', 'masque'],
  ])('decodes %s', (uri, kind) => {
    const parsed = parseProxyURI(uri)!;
    expect(parsed.kind).toBe(kind);
    expect(parsed.port).toBeGreaterThan(0);
  });
  it('retains literal percent-escaped credentials after one URL decode', () => {
    expect(parseProxyURI('trojan://literal%2541%25@127.0.0.1:443')?.password).toBe('literal%41%');
  });
  it('detects content type, imports Base64, and reports rejection reasons', () => {
    const bad = ssURI().split('#')[0] + '?plugin=unsupported#Fixture';
    const text = Buffer.from([ssURI(), bad, 'vless://@127.0.0.1:0', ssURI()].join('\n')).toString('base64');
    const parsed = parseSubscription(text);
    expect(parsed.nodes).toHaveLength(1);
    expect(parsed.skippedCount).toBe(2);
    expect(parsed.skippedReasons.map(item => item.reason)).toEqual(['Unsupported Shadowsocks SIP003 plugin', 'Invalid proxy host or port']);
    expect(detectSourceInput(text)).toMatchObject({ kind: 'proxyNodes', detectedNodeCount: 1 });
    expect(detectSourceInput('https://sub.example.test/path')).toMatchObject({ kind: 'subscriptionURL' });
    expect(detectSourceInput('https://proxy.example.test:443')).toMatchObject({ kind: 'proxyNodes' });
    expect(detectSourceInput('[custom]\nruleset=Foo,[]FINAL').kind).toBe('ruleScheme');
    expect(detectSourceInput('No links').kind).toBe('unknown');
  });
  it('parses YAML transport settings and Surge INI ShadowTLS', () => {
    const yaml = `proxies:\n  - { name: Japan, type: vless, server: 127.0.0.1, port: 443, uuid: '${uuid}', network: ws, tls: true, ws-opts: {path: /x, headers: {Host: cdn.example}} }\n`;
    expect(detectSourceInput(yaml).kind).toBe('clashYAML');
    expect(parseSubscription(yaml).nodes[0]).toMatchObject({ transport: 'ws', tls: true, path: '/x', hostHeader: 'cdn.example' });
    const ini = '[Proxy]\nNode = snell, 127.0.0.1, 443, version=4, psk=base, shadow-tls-password=outer, shadow-tls-sni=host.example, shadow-tls-version=3\n';
    expect(detectSourceInput(ini).kind).toBe('surgeConfig');
    expect(parseSubscription(ini).nodes[0]).toMatchObject({ kind: 'snell', password: 'base', shadowTLS: { password: 'outer', version: 3 } });
    const plugin = 'proxies:\n  - {name: Plugin, type: ss, server: 192.0.2.1, port: 443, cipher: aes-128-gcm, password: fixture, plugin: obfs, plugin-opts: {mode: tls, host: cdn.example}}';
    expect(parseSubscription(plugin).nodes[0]).toMatchObject({ kind: 'ss', obfs: 'tls', hostHeader: 'cdn.example' });
  });
  it('marks metadata nodes and retains STATUS and notice information', () => {
    const data = parseSubscription('STATUS=upload=1; download=2; total=3\n' +
      ssURI('127.0.0.1', '剩余流量：5 GB') + '\n' + ssURI('1.1.1.1', 'Subscription mobile app'), 'provider');
    expect(data.status).toMatchObject({ uploadBytes: 1, downloadBytes: 2, totalBytes: 3 });
    expect(data.nodes.every(node => node.isSubscriptionMetadata)).toBe(true);
    expect(data.notices).toEqual(['剩余流量：5 GB', 'Subscription mobile app']);
    expect(parseSubscription('STATUS=🚀↑:2GB,↓:3GB,TOT:10GB💡EXPIRES:2026-12-09\n' + ssURI()).status)
      .toMatchObject({ uploadBytes: 2 * 1024 ** 3, downloadBytes: 3 * 1024 ** 3, totalBytes: 10 * 1024 ** 3, expiresAt: '2026-12-09T00:00:00.000Z' });
  });
  it('preserves legacy VMess and VLESS authorities and separate Surge WireGuard fields', () => {
    const vmess = Buffer.from('aes-128-gcm:' + uuid + '@192.0.2.2:443').toString('base64url');
    const vless = Buffer.from('none:' + uuid + '@192.0.2.2:443').toString('base64url');
    expect(parseProxyURI('vmess://' + vmess + '?obfs=wss&obfsParam=cdn.example')).toMatchObject({ uuid, tls: true, transport: 'ws', hostHeader: 'cdn.example' });
    expect(parseProxyURI('vless://' + vless + '?security=tls')).toMatchObject({ uuid, tls: true });
    const wireguard = '[Proxy]\nFixture = wireguard, section-name=peer\n[WireGuard peer]\nprivate-key = private\nself-ip = 10.0.0.2\npeer = (public-key=public, allowed-ips="0.0.0.0/0,::/0", endpoint=[2001:db8::1]:51820, client-id=4/5/6)';
    expect(parseSubscription(wireguard).nodes[0]).toMatchObject({ kind: 'wireguard', server: '2001:db8::1', port: 51820,
      wireGuardReserved: '4,5,6', wireGuardAllowedIPs: '0.0.0.0/0,::/0', wireGuardIPv4: '10.0.0.2' });
  });
});

describe('fetch behavior', () => {
  it('reads the original body and makes at most one headers-only compatibility probe', async () => {
    const calls: string[] = [];
    const server = createServer((req, res) => {
      calls.push(req.url!);
      expect(req.headers['user-agent']).toBe('Fixture Agent');
      if (req.url!.includes('flag=clash')) {
        res.setHeader('subscription-userinfo', 'upload=2; download=3; total=8');
        res.end(ssURI('127.0.0.1', 'Wrong converted body'));
      } else if (req.url!.startsWith('/status')) {
        res.end('STATUS=upload=1; download=2; total=5\n' + 'anytls://secret@127.0.0.1:443#original');
      } else if (req.url!.startsWith('/header')) {
        res.setHeader('subscription-userinfo', 'upload=4; download=8; total=16');
        res.end('STATUS=upload=1; download=2; total=5\n' + ssURI());
      } else {
        res.setHeader('profile-title', 'base64:' + Buffer.from('套餐一').toString('base64'));
        res.end('anytls://secret@127.0.0.1:443#original');
      }
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
    try {
      const options = { userAgent: 'Fixture Agent' };
      const result = await fetchSubscription(base + '/probe', options);
      expect(result.name).toBe('套餐一');
      expect(result.nodes.map(node => node.kind)).toEqual(['anytls']);
      expect(result.usage).toMatchObject({ totalBytes: 8 });
      expect((await fetchSubscription(base + '/status', options)).usage?.totalBytes).toBe(5);
      expect((await fetchSubscription(base + '/header', options)).usage?.totalBytes).toBe(16);
      expect(calls).toEqual(['/probe', '/probe?flag=clash', '/status', '/header']);
    } finally { await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())); }
  });
  it('marks UDP-only TCP probes as inapplicable', async () => {
    expect(await probeNode(node({ kind: 'masque' }), 'tcp')).toMatchObject({ method: null, milliseconds: null, isApplicable: false });
  });
  it('resolves a subscription hostname through the configured DNS-over-HTTPS endpoint', async () => {
    const server = createServer((req, res) => {
      expect(req.headers.host).toMatch(/^fixture\.invalid:/);
      res.setHeader('subscription-userinfo', 'total=100');
      res.end(ssURI());
    });
    const queries: number[] = [];
    vi.stubGlobal('fetch', async (input: URL) => {
      expect(input.origin).toBe('https://doh.example');
      const question = Buffer.from(input.searchParams.get('dns')!, 'base64url');
      const type = question.readUInt16BE(question.length - 4);
      queries.push(type);
      question.writeUInt16BE(0x8180, 2);
      question.writeUInt16BE(type === 1 ? 1 : 0, 6);
      const answer = type === 1 ? Buffer.concat([question, Buffer.from([0xc0, 0x0c, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 127, 0, 0, 1])]) : question;
      return new Response(new Uint8Array(answer));
    });
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    try {
      const url = `http://fixture.invalid:${(server.address() as AddressInfo).port}/subscription`;
      expect(await fetchSubscription(url, { dnsOverHTTPSURL: 'https://doh.example/dns-query' })).toMatchObject({ usage: { totalBytes: 100 } });
      expect(queries).toContain(1);
    } finally {
      vi.unstubAllGlobals();
      await new Promise<void>(resolve => server.close(() => resolve()));
    }
  });
  it('measures a local TCP handshake', async () => {
    const server = createServer();
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    try {
      const result = await probeNode(node({ port: (server.address() as AddressInfo).port }), 'tcp');
      expect(result).toMatchObject({ method: 'TCP', isApplicable: true });
      expect(result.milliseconds).toBeGreaterThan(0);
    } finally { await new Promise<void>(resolve => server.close(() => resolve())); }
  });
});

describe('refresh inheritance', () => {
  it('gives exact identity priority and carries changed remarks only when unique', () => {
    const first = node({ id: 'first', name: 'A', countryOverride: 'GB' });
    const second = node({ id: 'second', name: 'Other region', sni: 'secondary.example' });
    const after = carriedOverExclusions([first, second], ['first'], [
      { ...first, id: 'new-a', rawURI: 'changed metadata' }, { ...second, id: 'new-b', name: 'New remark' },
    ]);
    expect(after.excludedNodeIDs).toEqual(['new-a']);
    expect(after.nodes[0].countryOverride).toBe('GB');
    const renamed = carriedOverExclusions([first], ['first'], [node({ id: 'new', name: 'Renamed' })]);
    expect(renamed.excludedNodeIDs).toEqual(['new']);
    expect(renamed.nodes[0].countryOverride).toBe('GB');
  });
  it('does not transfer exclusions or overrides through ambiguous loose identities', () => {
    const first = node({ id: 'first', name: 'A', countryOverride: 'JP' });
    const second = node({ id: 'second', name: 'B' });
    const newNodes = [node({ id: 'new', name: 'Renamed' })];
    expect(carriedOverExclusions([first, second], ['first'], newNodes)).toEqual({ nodes: newNodes, excludedNodeIDs: [] });
    expect(carriedOverExclusions([first], ['first'], [second, ...newNodes]).excludedNodeIDs).toEqual([]);
  });
});
