import { createHash } from 'node:crypto';
import { isIP } from 'node:net';
import { CLIENT_TARGETS, PROXY_KIND_LABELS } from '../../../shared/src/index.js';
import type { ClientTarget, ExportContentMode, ProxyNode } from '../../../shared/src/index.js';
import { cleanLine, list, validateNode } from './subscriptionParser.js';
import type { Fields } from './subscriptionParser.js';

export const confName = (value: string) => cleanLine(value)
  .replaceAll('=', '-').replaceAll(',', '，').replaceAll('#', '＃').replaceAll(';', '；')
  .replaceAll('//', '／／').replaceAll('[', '［').replaceAll(']', '］');
const confValue = (value: string | number | boolean) => cleanLine(String(value)).replaceAll(',', '%2C');
const quote = (value: string) => JSON.stringify(value);
export const endpoint = (node: ProxyNode) => `${node.server.includes(':') ? '[' + node.server + ']' : node.server}:${node.port}`;
export const surgeTargets = new Set<ClientTarget>(['surge', 'surge-mac']);
export const mihomoTargets = new Set<ClientTarget>(['clash-apple', 'clash-verge', 'clashmac', 'flclash', 'mihomo-party', 'clash-mi']);
const v2Transport = new Set(['vmess', 'vless', 'trojan']);
const uuidPattern = /^[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}$/i;
const pluginType = (node: ProxyNode) => node.plugin?.split(';', 1)[0];
const simpleObfs = (node: ProxyNode) => ['http', 'tls'].includes(node.obfs ?? '') ? node.obfs : null;
const absolutePath = (value?: string | null) => value && (value.startsWith('/') ? value : '/' + value);
const wsPath = (value?: string | null) => absolutePath(value)?.replace(/([?&])ed=\d+(&|$)/, '$1').replace(/[?&]$/, '');
const earlyData = (value?: string | null) => Number(/[?&]ed=(\d+)(?:&|$)/.exec(value ?? '')?.[1]) || undefined;
const nonempty = <T>(items: T[]) => items.length ? items : undefined;
const sni = (node: ProxyNode) => node.sni || (node.tls && v2Transport.has(node.kind) && ['ws', 'http', 'h2', 'httpupgrade'].includes(node.transport ?? '') ? node.hostHeader : undefined);
const alwaysTLS = new Set(['trojan', 'hysteria', 'hysteria2', 'tuic', 'anytls', 'trusttunnel', 'masque']);
const cleanFields = (fields: Fields): Fields => Object.fromEntries(Object.entries(fields).filter(([, value]) => value != null && value !== ''));
const shadowTLS = (node: ProxyNode) => node.shadowTLS ?? undefined;
const wireguardCIDRs = (node: ProxyNode) => [node.wireGuardIPv4, node.wireGuardIPv6].filter((value): value is string => !!value)
  .map(address => address.includes('/') ? address : address + (address.includes(':') ? '/128' : '/32'));
const wireguardIPs = (node: ProxyNode) => list(node.wireGuardAllowedIPs ?? '0.0.0.0/0,::/0');
const reservedBytes = (node: ProxyNode) => nonempty(list(node.wireGuardReserved?.replace(/[\[\]]/g, '')).map(Number));
const kv = (fields: Fields, escape = confValue) => Object.entries(cleanFields(fields)).map(([key, value]) => `${key}=${escape(value as string)}`);

export function normalizedUUID(value: string): string {
  if (uuidPattern.test(value)) return value.toLowerCase();
  const bytes = createHash('sha1').update(Buffer.alloc(16)).update(value).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 0xf) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}

const ssLegacy = 'rc4 rc4-md5 aes-128-cfb aes-192-cfb aes-256-cfb aes-128-ctr aes-192-ctr aes-256-ctr salsa20 chacha20 chacha20-ietf';
const ssExtraLegacy = 'bf-cfb camellia-128-cfb camellia-192-cfb camellia-256-cfb cast5-cfb des-cfb idea-cfb rc2-cfb seed-cfb';
const ssAEAD = 'aes-128-gcm aes-192-gcm aes-256-gcm chacha20-ietf-poly1305 xchacha20-ietf-poly1305';
const ss2022 = '2022-blake3-aes-128-gcm 2022-blake3-aes-256-gcm';
const ssCiphers: Partial<Record<ClientTarget, string>> = {
  surge: [ssLegacy, ssAEAD, ss2022, 'none'].join(' '), 'surge-mac': [ssLegacy, ssAEAD, ss2022, 'none'].join(' '),
  loon: [ssLegacy, ssExtraLegacy, ssAEAD, ss2022].join(' '), quanx: [ssLegacy, ssAEAD, ss2022].join(' '),
  clash: [ssLegacy, ssAEAD, ss2022, 'none'].join(' '),
  egern: [ssAEAD, ss2022, '2022-blake3-chacha20-poly1305 none'].join(' '),
  anywhere: 'aes-128-gcm aes-256-gcm chacha20-ietf-poly1305 chacha20-poly1305 ' + ss2022 + ' 2022-blake3-chacha20-poly1305 none',
};
const surfCiphers = (ssLegacy + ' ' + ssAEAD + ' ' + ss2022 + ' none bf-cfb camellia-128-cfb camellia-192-cfb camellia-256-cfb').split(' ');
const uriOutput = new Set<ClientTarget>(['v2box', 'anywhere']);

export function nodeExportReason(node: ProxyNode, target: ClientTarget, mode: ExportContentMode, surfboard = false): string | null {
  const { kind, transport, tls, obfs } = node;
  const transportName = transport && !['tcp', 'none', 'raw'].includes(transport) ? transport : undefined;
  const clash = CLIENT_TARGETS[target].usesClashFormat || target === 'shadowrocket';
  const sing = CLIENT_TARGETS[target].usesSingBoxFormat;
  const surge = surgeTargets.has(target);
  const reality = !!node.realityPublicKey;
  const plugin = pluginType(node);
  const shadow = shadowTLS(node);
  try { validateNode(node); } catch (error) { return (error as Error).message; }
  if (!CLIENT_TARGETS[target].supportedKinds.includes(kind)) return `${CLIENT_TARGETS[target].name} does not support ${PROXY_KIND_LABELS[kind]}`;
  if (mode === 'nodesOnly' && kind === 'wireguard' && (surge || target === 'shadowrocket')) return 'WireGuard requires a full profile';
  if (mode === 'nodesOnly' && kind === 'snell' && target === 'shadowrocket') return 'Snell requires a full profile';
  if (node.certificateFingerprint && (sing || uriOutput.has(target))) return 'Certificate pinning is unsupported';
  if (kind === 'tuic' && !uuidPattern.test(node.uuid ?? '')) return 'Invalid TUIC UUID';
  if (kind === 'ss') {
    const ciphers = ssCiphers[target];
    if (ciphers && !ciphers.split(' ').includes(node.cipher ?? '')) return 'Unsupported Shadowsocks cipher';
    if (tls && !plugin && !obfs && !['shadowrocket', 'quanx'].includes(target)) return 'Native Shadowsocks TLS is unsupported';
    if (plugin === 'v2ray-plugin' && !(clash || sing || target === 'quanx')) return 'WebSocket SIP003 plugin is unsupported';
    if (target === 'quanx' && plugin === 'v2ray-plugin' && node.pluginMux !== false) return 'Quantumult X requires WebSocket plugin multiplexing to be disabled';
    if (target === 'anywhere' && (plugin || shadow || tls || (obfs && obfs !== 'none'))) return 'Shadowsocks options are unsupported by Anywhere';
  }
  if (kind === 'snell') {
    const version = node.version ?? (target === 'clash' ? 1 : 4);
    if (target === 'clash' && version > 3) return 'This Stash dialect supports Snell versions 1–3';
    if (sing && (![4, 5].includes(version) || (obfs && !['http', 'none'].includes(obfs)))) return 'Unsupported sing-box Snell version or obfuscation';
    if (mihomoTargets.has(target) && version === 6 && (!['clash-apple', 'flclash', 'mihomo-party'].includes(target) ||
        (obfs && obfs !== 'none') || Buffer.byteLength(node.password ?? '') < 12 || Buffer.byteLength(node.password ?? '') > 255)) {
      return 'Unsupported Snell v6 options';
    }
  }
  if (shadow) {
    if (kind === 'snell' && !surge) return 'Snell with ShadowTLS is unsupported';
    if (!(surge || clash || sing || target === 'loon' || target === 'egern') || target === 'karing') return 'ShadowTLS is unsupported';
    if ((surge || target === 'loon' || target === 'clash') && shadow.version < 2) return 'Unsupported ShadowTLS version';
    if (['egern', 'shadowrocket'].includes(target) && shadow.version !== 3) return 'ShadowTLS v3 is required';
    if (shadow.skipCertificateVerification && !sing && target !== 'clash') return 'ShadowTLS with certificate verification disabled is unsupported';
    if (sing && node.udpRelayEnabled === true) return 'This ShadowTLS detour carries TCP only';
    if (!sing && node.fingerprint) return 'Custom ShadowTLS fingerprint is unsupported';
    if (target === 'loon' && /[,;#\r\n]/.test(shadow.password + shadow.host)) return 'ShadowTLS credentials cannot be represented by Loon';
  }
  if (kind === 'wireguard') {
    const reserved = reservedBytes(node);
    if (reserved && (reserved.length !== 3 || reserved.some(byte => !Number.isInteger(byte) || byte < 0 || byte > 255))) return 'Invalid WireGuard reserved bytes';
    if (wireguardCIDRs(node).some(address => !isIP(address.split('/')[0]))) return 'Invalid WireGuard interface address';
    if (target === 'hiddify' && (node.wireGuardPersistentKeepalive || node.wireGuardDNS)) return 'WireGuard keepalive or DNS is unsupported';
  }
  if (surfboard) {
    if (['ssh', 'trusttunnel', 'masque'].includes(kind) || shadow) return 'Surfboard does not support this proxy';
    if (kind === 'ss' && !surfCiphers.includes(node.cipher ?? '')) return 'Unsupported Surfboard Shadowsocks cipher';
    if (kind === 'snell' && (node.version ?? 1) > 5) return 'Unsupported Surfboard Snell version';
    if (['vmess', 'trojan'].includes(kind) && transport === 'httpupgrade') return 'Surfboard does not support HTTPUpgrade for this protocol';
    if (kind === 'wireguard' && (!node.wireGuardDNS || !node.wireGuardMTU || reservedBytes(node))) return 'Surfboard WireGuard requires DNS and MTU, without reserved bytes';
  }
  if (kind === 'ssh') {
    if ((surge || target === 'shadowrocket') && node.ssh?.privateKey) return 'SSH key authentication is unsupported by this client';
    if ((surge || target === 'egern') && node.ssh?.privateKeyPassphrase) return 'SSH key passphrases are unsupported';
    if (target === 'clash' && node.ssh?.hostKeys.length) return 'SSH host key pinning is unsupported';
  }
  if (kind === 'trusttunnel' && ((target === 'clash' && !node.trustTunnel?.quic) ||
      (node.trustTunnel?.clientRandomAuthKey && !surge))) return 'Unsupported TrustTunnel options';
  if (kind === 'masque' && !surge && node.masque?.mode !== 'keyConnectIP') return 'MASQUE Basic authentication is unsupported';
  if (kind === 'masque' && surge && node.masque?.mode === 'keyConnectIP') return 'MASQUE key authentication is unsupported';
  if (kind === 'masque' && surge && (node.masque?.headers && Object.keys(node.masque.headers).length || node.masque?.certificate?.length ||
      node.masque?.httpVersion || node.masque?.disableVersionFallback || node.masque?.mtu)) return 'Unsupported Surge MASQUE options';
  if (kind === 'socks5' && tls && (sing || target === 'karing' || target === 'anywhere')) return 'SOCKS5 TLS is unsupported';
  if (kind === 'hysteria' && sing && node.protocolName && node.protocolName !== 'udp') return 'Unsupported Hysteria transport';
  if (kind === 'hysteria2' && obfs && obfs !== 'none' && (!node.obfsParam ||
      (!['salamander'].includes(obfs) && (sing || surge || ['loon', 'shadowrocket', 'egern'].includes(target))))) return 'Unsupported Hysteria 2 obfuscation';
  if (reality && (!CLIENT_TARGETS[target].expressesReality && !(surge && kind === 'vless') ||
      (target === 'clash' && (kind !== 'vless' || transportName)) ||
      (mihomoTargets.has(target) && !v2Transport.has(kind)) ||
      (target === 'karing' && kind === 'trojan') ||
      (target === 'egern' && transportName) ||
      (target === 'anywhere' && kind !== 'vless'))) return 'This client cannot express REALITY for this node';
  if (node.realitySupportsX25519MLKEM768 && !(surge || mihomoTargets.has(target))) return 'Hybrid REALITY key exchange is unsupported';
  if (node.vlessEncryption && !(mihomoTargets.has(target) || target === 'anywhere' ||
    (surge && /^mlkem768x25519plus\.(?:native|xorpub|random)\.(?:1rtt|0rtt)(?:\.[\w=-]+)+$/i.test(node.vlessEncryption)))) return 'VLESS encryption is unsupported';
  if (target === 'anywhere' && (node.portHopping || node.udpRelayEnabled === false ||
      (kind === 'vless' && !uuidPattern.test(node.uuid ?? '')) ||
      (kind === 'hysteria2' && (node.alpn || (obfs && !['none', 'salamander', 'gecko'].includes(obfs)))) ||
      (node.fingerprint && !/^(chrome|firefox|safari|ios|edge|random|randomized|chrome_(120|133|106)|firefox_(120|148)|safari_26|edge_106|non_browser)$/i.test(node.fingerprint)) ||
      (node.skipCertificateVerification && (!['vless', 'trojan', 'anytls'].includes(kind) || reality || !tls ||
        (kind === 'vless' && !!transportName && transportName !== 'httpupgrade'))))) {
    return 'This node requires options that Anywhere cannot express';
  }
  if (transportName && !(kind === 'ss' && plugin === 'v2ray-plugin')) {
    if (!v2Transport.has(kind)) return 'Unsupported protocol transport';
    const allowed = surge ? kind === 'vless' ? ['ws', 'httpupgrade', 'grpc', 'xhttp'] : ['ws', 'httpupgrade'] :
      target === 'loon' ? kind === 'trojan' ? ['ws'] : ['ws', 'http', 'h2'] :
      target === 'quanx' ? ['ws', 'http'] :
      target === 'egern' ? kind === 'trojan' ? ['ws'] : ['ws', 'http', 'h2', 'grpc'] :
      target === 'clash' ? ['ws', 'http', 'h2', 'grpc'] :
      target === 'karing' ? ['ws', 'http', 'grpc', 'xhttp'] :
      sing ? ['ws', 'http', 'h2', 'grpc', 'httpupgrade', 'quic'] :
      target === 'anywhere' ? kind === 'vless' ? ['ws', 'grpc', 'httpupgrade', 'xhttp'] : [] :
      ['ws', 'http', 'h2', 'grpc', 'httpupgrade', 'xhttp'];
    if (!allowed.includes(transportName)) return `Unsupported transport: ${transportName}`;
    if (sing && transportName === 'grpc' && node.hostHeader) return 'Custom gRPC authority is unsupported';
    if (target === 'egern' && ((['h2', 'grpc'].includes(transportName) && !tls) || (transportName === 'http' && tls))) return 'Unsupported Egern transport and TLS combination';
  }
  if (['surge', 'surge-mac', 'loon', 'quanx'].includes(target) && Object.entries(node).some(([key, value]) =>
    key !== 'name' && key !== 'rawURI' && typeof value === 'string' && /[\r\n\u0085\u2028\u2029]/.test(value))) return 'Multiline credentials cannot be represented in INI';
  return null;
}

export function clashProxy(node: ProxyNode, target: ClientTarget): Fields {
  const p: Fields = { name: node.name, type: node.kind, server: node.server, port: node.port };
  const stash = target === 'clash';
  const network = node.transport;
  const hostname = sni(node);
  switch (node.kind) {
    case 'ss':
      Object.assign(p, { cipher: node.cipher, password: node.password, udp: node.udpRelayEnabled ?? true });
      if (node.shadowTLS) {
        p.plugin = 'shadow-tls';
        p['plugin-opts'] = cleanFields({ version: node.shadowTLS.version, host: node.shadowTLS.host, password: node.shadowTLS.password,
          'skip-cert-verify': node.shadowTLS.skipCertificateVerification });
      } else if (pluginType(node) === 'v2ray-plugin') {
        p.plugin = 'v2ray-plugin';
        p['plugin-opts'] = cleanFields({ mode: 'websocket', tls: node.tls, host: node.hostHeader, path: absolutePath(node.path), mux: node.pluginMux,
          'skip-cert-verify': node.skipCertificateVerification });
      } else if (simpleObfs(node)) {
        p.plugin = 'obfs';
        p['plugin-opts'] = cleanFields({ mode: simpleObfs(node), host: node.hostHeader ?? node.obfsParam });
      } else if (node.tls) Object.assign(p, { tls: true, sni: hostname, 'skip-cert-verify': node.skipCertificateVerification });
      break;
    case 'ssr':
      Object.assign(p, { cipher: node.cipher, password: node.password, protocol: node.protocolName,
        obfs: node.obfs, 'protocol-param': node.protocolParam, 'obfs-param': node.obfsParam, udp: node.udpRelayEnabled ?? true });
      break;
    case 'vmess':
    case 'vless':
    case 'trojan':
      if (node.uuid) p.uuid = normalizedUUID(node.uuid);
      if (node.kind === 'vmess') Object.assign(p, { alterId: node.alterID ?? 0, cipher: node.cipher || 'auto' });
      Object.assign(p, { password: node.password, flow: node.flow, encryption: node.vlessEncryption, udp: node.udpRelayEnabled ?? true,
        tls: node.tls || alwaysTLS.has(node.kind), [node.kind === 'trojan' ? 'sni' : 'servername']: hostname });
      if (network && network !== 'tcp') p.network = network;
      if (network === 'ws' || network === 'httpupgrade') {
        const ws: Fields = cleanFields({ path: stash ? wsPath(node.path) : absolutePath(node.path), headers: node.hostHeader ? { Host: node.hostHeader } : undefined });
        if (stash && earlyData(node.path)) Object.assign(ws, { 'max-early-data': earlyData(node.path), 'early-data-header-name': 'Sec-WebSocket-Protocol' });
        p[network === 'ws' ? 'ws-opts' : 'http-upgrade-opts'] = ws;
      }
      if (network === 'grpc') p['grpc-opts'] = cleanFields({ 'grpc-service-name': node.path?.replace(/^\//, ''), authority: node.hostHeader });
      if (network === 'h2' || network === 'http' || network === 'xhttp') p[network + '-opts'] = cleanFields({
        path: network === 'http' ? [absolutePath(node.path) ?? '/'] : absolutePath(node.path),
        host: network === 'h2' && node.hostHeader ? [node.hostHeader] : network === 'xhttp' ? node.hostHeader : undefined,
        headers: network === 'http' && node.hostHeader ? { Host: [node.hostHeader] } : undefined,
        mode: node.transportMode,
      });
      break;
    case 'hysteria':
      Object.assign(p, { 'auth-str': node.password ?? '', [stash ? 'up-speed' : 'up']: node.upMbps ?? 50,
        [stash ? 'down-speed' : 'down']: node.downMbps ?? 100, protocol: node.protocolName, obfs: node.obfs, sni: hostname });
      break;
    case 'hysteria2':
      Object.assign(p, { [stash ? 'auth' : 'password']: node.password, sni: hostname, ports: node.portHopping,
        obfs: node.obfs === 'none' ? undefined : node.obfs, 'obfs-password': node.obfsParam, udp: true, up: node.upMbps, down: node.downMbps });
      break;
    case 'tuic':
      Object.assign(p, { uuid: normalizedUUID(node.uuid!), password: node.password, sni: hostname, version: stash ? 5 : undefined,
        'congestion-controller': node.congestionControl, 'udp-relay-mode': node.udpRelayMode, ports: node.portHopping, udp: true });
      break;
    case 'wireguard':
      Object.assign(p, { 'private-key': node.wireGuardPrivateKey, 'public-key': node.wireGuardPublicKey,
        'pre-shared-key': node.wireGuardPreSharedKey, ip: node.wireGuardIPv4, ipv6: node.wireGuardIPv6,
        'allowed-ips': wireguardIPs(node), reserved: reservedBytes(node), mtu: node.wireGuardMTU,
        'persistent-keepalive': node.wireGuardPersistentKeepalive, dns: nonempty(list(node.wireGuardDNS)), udp: true });
      break;
    case 'anytls':
      Object.assign(p, { password: node.password, sni: hostname, udp: node.udpRelayEnabled ?? true, tls: target === 'shadowrocket' || target === 'karing' ? true : undefined,
        'idle-session-check-interval': node.idleSessionCheckInterval, 'idle-session-timeout': node.idleSessionTimeout, 'min-idle-session': node.minIdleSession });
      break;
    case 'snell':
      Object.assign(p, { psk: node.password, version: node.version ?? (stash ? 1 : 4), udp: (node.version ?? 4) >= 3 ? node.udpRelayEnabled ?? true : undefined });
      if (simpleObfs(node)) p['obfs-opts'] = cleanFields({ mode: node.obfs, host: node.obfsParam });
      break;
    case 'ssh':
      Object.assign(p, { [stash ? 'user' : 'username']: node.username, password: node.password, 'private-key': node.ssh?.privateKey,
        'private-key-passphrase': node.ssh?.privateKeyPassphrase, 'host-key': nonempty(node.ssh?.hostKeys ?? []), 'host-key-algorithms': nonempty(node.ssh?.hostKeyAlgorithms ?? []) });
      break;
    case 'masque':
      Object.assign(p, { 'private-key': node.masque?.privateKey, 'public-key': node.masque?.publicKey, ip: node.masque?.ipv4,
        ipv6: node.masque?.ipv6, mtu: node.masque?.mtu, network: node.masque?.network === 'h3' ? 'quic' : node.masque?.network ?? 'quic',
        'remote-dns-resolve': node.masque?.remoteDNSResolve ?? true, dns: nonempty(node.masque?.dns ?? []) ?? ['1.1.1.1', '8.8.8.8'],
        udp: node.udpRelayEnabled ?? false, sni: hostname });
      break;
    case 'trusttunnel':
      Object.assign(p, { username: node.username, password: node.password, sni: hostname, quic: node.trustTunnel?.quic,
        udp: node.udpRelayEnabled ?? undefined });
      break;
    case 'socks5':
    case 'http':
      Object.assign(p, { username: node.username, password: node.password, tls: node.tls,
        sni: node.tls ? hostname : undefined, udp: node.kind === 'socks5' ? node.udpRelayEnabled ?? true : undefined });
      break;
  }
  if (node.tls || alwaysTLS.has(node.kind)) {
    if (node.kind !== 'ss') {
      Object.assign(p, { alpn: nonempty(list(node.alpn)), fingerprint: node.certificateFingerprint,
        'client-fingerprint': node.fingerprint, 'skip-cert-verify': node.skipCertificateVerification });
    }
    if (node.realityPublicKey) p['reality-opts'] = cleanFields({ 'public-key': node.realityPublicKey,
      'short-id': node.realityShortID, 'support-x25519mlkem768': node.realitySupportsX25519MLKEM768 });
  }
  return cleanFields(p);
}

const wireguardSection = (node: ProxyNode) => 'tower-wg-' + createHash('sha256').update(node.id).digest('hex').slice(0, 12);

export function surgeWireguard(node: ProxyNode): string {
  const peer = kv({ 'public-key': node.wireGuardPublicKey, 'preshared-key': node.wireGuardPreSharedKey,
    endpoint: endpoint(node), 'keepalive': node.wireGuardPersistentKeepalive,
    'allowed-ips': JSON.stringify(wireguardIPs(node).join(',')), 'client-id': reservedBytes(node)?.join('/') }, value => String(value));
  const rows: [string, string | number | null | undefined][] = [
    ['private-key', node.wireGuardPrivateKey], ['self-ip', node.wireGuardIPv4], ['self-ip-v6', node.wireGuardIPv6], ['mtu', node.wireGuardMTU],
    ['dns-server', node.wireGuardDNS], ['peer', '(' + peer.join(', ') + ')'],
  ];
  return ['[WireGuard ' + wireguardSection(node) + ']', ...rows.flatMap(([key, value]) => value != null && value !== '' ? [`${key} = ${value}`] : [])].join('\n');
}

function tlsINI(node: ProxyNode): Fields {
  return { sni: sni(node), 'skip-cert-verify': node.skipCertificateVerification || undefined, alpn: node.alpn,
    'server-cert-fingerprint-sha256': node.certificateFingerprint };
}

export function surgeNode(node: ProxyNode, surfboard = false): string {
  let kind: string = node.kind;
  const fields: Fields = {};
  let parts: string[] = [node.server, String(node.port)];
  switch (node.kind) {
    case 'ss':
      Object.assign(fields, { 'encrypt-method': node.cipher, password: node.password, obfs: simpleObfs(node),
        'obfs-host': node.hostHeader ?? node.obfsParam, 'udp-relay': node.udpRelayEnabled ?? true });
      break;
    case 'vmess':
    case 'vless':
    case 'trojan':
      Object.assign(fields, { username: node.uuid ? normalizedUUID(node.uuid) : undefined, password: node.password,
        'vmess-aead': node.kind === 'vmess' ? !node.alterID : undefined, tls: node.kind !== 'trojan' ? node.tls : undefined,
        'udp-relay': node.udpRelayEnabled ?? true });
      if (node.kind === 'vless') Object.assign(fields, { 'vless-flow': node.flow, encryption: node.vlessEncryption });
      if (node.transport === 'ws' || node.transport === 'httpupgrade') {
        Object.assign(fields, { ws: true, 'ws-path': wsPath(node.path), 'ws-headers': node.hostHeader ? 'Host: ' + node.hostHeader : undefined,
          'ws-http-upgrade': node.transport === 'httpupgrade' ? true : undefined });
      }
      if (node.transport === 'grpc') Object.assign(fields, { grpc: true, 'grpc-service-name': node.path?.replace(/^\//, ''), 'grpc-authority': node.hostHeader });
      if (node.transport === 'xhttp') Object.assign(fields, { xhttp: true, 'xhttp-path': absolutePath(node.path), 'xhttp-host': node.hostHeader, 'xhttp-mode': node.transportMode });
      if (node.realityPublicKey) Object.assign(fields, { 'reality-public-key': node.realityPublicKey, 'reality-short-id': node.realityShortID,
        'support-x25519mlkem768': node.realitySupportsX25519MLKEM768, 'tls-fingerprint': node.fingerprint });
      break;
    case 'hysteria2':
      Object.assign(fields, { password: node.password, 'salamander-password': node.obfsParam, 'port-hopping': node.portHopping, 'udp-relay': true,
        'download-bandwidth': node.downMbps });
      break;
    case 'tuic':
      kind = 'tuic-v5';
      Object.assign(fields, { uuid: normalizedUUID(node.uuid!), password: node.password, 'udp-relay': true,
        'congestion-controller': node.congestionControl, 'udp-relay-mode': node.udpRelayMode });
      break;
    case 'wireguard':
      parts = [];
      fields['section-name'] = wireguardSection(node);
      break;
    case 'anytls':
      Object.assign(fields, { password: node.password, 'udp-relay': node.udpRelayEnabled ?? true });
      break;
    case 'snell':
      Object.assign(fields, { psk: node.password, version: surfboard && node.version === 5 ? 4 : node.version,
        obfs: simpleObfs(node), 'obfs-host': node.obfsParam, 'udp-relay': (node.version ?? 4) >= 3 ? node.udpRelayEnabled ?? !node.shadowTLS : undefined });
      break;
    case 'socks5':
    case 'http':
      if (node.tls) kind = node.kind === 'http' ? 'https' : 'socks5-tls';
      if (node.username || node.password) parts.push(confValue(node.username ?? ''), confValue(node.password ?? ''));
      if (node.kind === 'socks5') fields['udp-relay'] = node.udpRelayEnabled ?? true;
      break;
    case 'ssh':
      Object.assign(fields, { username: node.username, password: node.password });
      if (node.ssh?.hostKeys.length) parts.push('server-fingerprint=' + quote(node.ssh.hostKeys.join(', ')));
      break;
    case 'trusttunnel':
      kind = 'trust-tunnel';
      Object.assign(fields, { username: node.username, password: node.password, h3: node.trustTunnel?.quic || undefined,
        'client-random-auth-key': node.trustTunnel?.clientRandomAuthKey, 'udp-relay': node.udpRelayEnabled });
      break;
    case 'masque':
      Object.assign(fields, { username: node.username, password: node.password });
      break;
  }
  if (node.tls || alwaysTLS.has(node.kind)) Object.assign(fields, tlsINI(node));
  if (node.shadowTLS) Object.assign(fields, { 'shadow-tls-password': node.shadowTLS.password.replaceAll('%', '%25'),
    'shadow-tls-sni': node.shadowTLS.host, 'shadow-tls-version': node.shadowTLS.version });
  return `${confName(node.name)} = ${[kind, ...parts, ...kv(fields)].join(', ')}`;
}

export function loonNode(node: ProxyNode): string {
  let type: string = ({ ss: 'Shadowsocks', ssr: 'ShadowsocksR', vless: 'VLESS', hysteria2: 'Hysteria2', socks5: 'Socks5' } as Record<string, string>)[node.kind] ?? node.kind;
  const fields: Fields = {};
  let parts = [node.server, String(node.port)];
  switch (node.kind) {
    case 'ss':
    case 'ssr':
      parts.push(node.cipher!, quote(node.password!));
      if (node.kind === 'ssr') Object.assign(fields, { protocol: node.protocolName, obfs: node.obfs,
        'protocol-param': node.protocolParam, 'obfs-param': node.obfsParam });
      else Object.assign(fields, { 'obfs-name': simpleObfs(node), 'obfs-host': node.obfsParam ?? node.hostHeader,
        'shadow-tls-password': node.shadowTLS?.password, 'shadow-tls-sni': node.shadowTLS?.host, 'shadow-tls-version': node.shadowTLS?.version });
      break;
    case 'vmess':
    case 'vless':
      if (node.kind === 'vmess') { parts.push(node.cipher ?? 'auto'); fields.alterId = node.alterID ?? 0; }
      parts.push(quote(normalizedUUID(node.uuid!)));
      Object.assign(fields, { transport: node.transport ?? 'tcp', overTLS: node.tls, flow: node.flow,
        path: wsPath(node.path), host: node.hostHeader });
      break;
    case 'trojan':
      parts.push(quote(node.password!));
      Object.assign(fields, { transport: node.transport, path: wsPath(node.path), host: node.hostHeader });
      break;
    case 'hysteria2':
      parts.push(quote(node.password!));
      Object.assign(fields, { 'salamander-password': node.obfsParam, 'fast-open': true });
      if (node.portHopping) parts.push('server-ports=' + quote(node.portHopping.replaceAll('-', ':')));
      break;
    case 'anytls': parts.push(quote(node.password!)); break;
    case 'wireguard': {
      const peer = [
        'public-key=' + quote(node.wireGuardPublicKey!), 'allowed-ips=' + quote(wireguardIPs(node).join(',')), 'endpoint=' + endpoint(node),
        ...(node.wireGuardPreSharedKey ? ['preshared-key=' + quote(node.wireGuardPreSharedKey)] : []),
        ...(reservedBytes(node) ? ['reserved=[' + reservedBytes(node)!.join(',') + ']'] : []),
      ];
      parts = [...kv({ 'interface-ip': node.wireGuardIPv4, 'interface-ipv6': node.wireGuardIPv6,
        mtu: node.wireGuardMTU, keepalive: node.wireGuardPersistentKeepalive }),
        'private-key=' + quote(node.wireGuardPrivateKey!), `peers=[{${peer.join(',')}}]`];
      if (node.wireGuardDNS) parts.push('dns=' + node.wireGuardDNS);
      break;
    }
    case 'http':
      if (node.tls) type = 'https';
      if (node.username || node.password) parts.push(quote(node.username ?? ''), quote(node.password ?? ''));
      break;
    case 'socks5':
      if (node.username || node.password) parts.push(quote(node.username ?? ''), quote(node.password ?? ''));
      if (node.tls) fields['over-tls'] = true;
      break;
  }
  if (node.kind !== 'http') fields.udp = node.udpRelayEnabled ?? true;
  if (node.tls || alwaysTLS.has(node.kind)) Object.assign(fields, {
    [node.realityPublicKey || ['socks5', 'http', 'vmess', 'vless'].includes(node.kind) ? 'sni' : 'tls-name']: sni(node),
    'skip-cert-verify': node.skipCertificateVerification || undefined, alpn: node.alpn, 'tls-cert-sha256': node.certificateFingerprint,
    'public-key': node.realityPublicKey, 'short-id': node.realityShortID, 'tls-profile': node.fingerprint,
  });
  return `${confName(node.name)} = ${[type, ...parts, ...kv(fields)].join(',')}`;
}

export function quanXNode(node: ProxyNode): string {
  const fields: Fields = {};
  const kind = node.kind === 'ss' || node.kind === 'ssr' ? 'shadowsocks' : node.kind;
  if (node.kind === 'ss' || node.kind === 'ssr') {
    Object.assign(fields, { method: node.cipher, password: node.password, 'ssr-protocol': node.protocolName, 'ssr-protocol-param': node.protocolParam });
    if (pluginType(node) === 'v2ray-plugin') Object.assign(fields, { obfs: node.tls ? 'wss' : 'ws', 'obfs-host': node.hostHeader, 'obfs-uri': wsPath(node.path) });
    else if (node.tls) Object.assign(fields, { obfs: 'over-tls', 'obfs-host': sni(node) });
    else Object.assign(fields, { obfs: node.obfs, 'obfs-host': node.obfsParam ?? node.hostHeader });
  } else if (node.kind === 'vmess' || node.kind === 'vless') {
    Object.assign(fields, { method: node.kind === 'vless' ? 'none' : node.cipher === 'none' ? 'none' : 'aes-128-gcm',
      password: normalizedUUID(node.uuid!), 'aead': node.kind === 'vmess' ? !node.alterID : undefined,
      obfs: node.transport === 'ws' ? node.tls ? 'wss' : 'ws' : node.transport === 'http' ? 'http' : node.tls ? 'over-tls' : undefined,
      'obfs-host': node.hostHeader ?? sni(node), 'obfs-uri': wsPath(node.path), 'vless-flow': node.flow });
  } else {
    Object.assign(fields, { username: node.username, password: node.password });
    if (node.transport === 'ws') Object.assign(fields, { obfs: 'wss', 'obfs-host': node.hostHeader, 'obfs-uri': wsPath(node.path) });
    else if (node.tls || alwaysTLS.has(node.kind)) Object.assign(fields, { 'over-tls': true, 'tls-host': sni(node) });
  }
  if (node.tls || alwaysTLS.has(node.kind)) Object.assign(fields, { 'tls-verification': !node.skipCertificateVerification,
    'tls-cert-sha256': node.certificateFingerprint, 'tls-alpn': node.alpn, 'tls-pubkey': node.realityPublicKey,
    'tls-short-id': node.realityShortID, 'tls-fingerprint': node.fingerprint });
  if (node.kind !== 'http') fields['udp-relay'] = node.udpRelayEnabled ?? true;
  return `${kind}=${[endpoint(node), ...kv(fields), 'tag=' + confName(node.name)].join(', ')}`;
}

export function proxyURI(node: ProxyNode, asAnywhere = false): string | null {
  try { validateNode(node); } catch { return null; }
  const encode = encodeURIComponent;
  const remark = '#' + encode(node.name);
  const base64 = (value: string) => Buffer.from(value).toString('base64url');
  if (node.kind === 'ss') {
    if (node.shadowTLS || (node.tls && !node.plugin)) return null;
    const plugin = node.plugin || (simpleObfs(node) ? `obfs-local;obfs=${node.obfs}${node.hostHeader || node.obfsParam ? ';obfs-host=' + (node.hostHeader ?? node.obfsParam) : ''}` : null);
    return `ss://${base64(node.cipher + ':' + node.password)}@${endpoint(node)}${plugin ? '?plugin=' + encode(plugin) : ''}${remark}`;
  }
  if (node.kind === 'ssr') {
    const fields = new URLSearchParams({ remarks: base64(node.name) });
    if (node.obfsParam) fields.set('obfsparam', base64(node.obfsParam));
    if (node.protocolParam) fields.set('protoparam', base64(node.protocolParam));
    return 'ssr://' + base64(`${endpoint(node)}:${node.protocolName}:${node.cipher}:${node.obfs}:${base64(node.password!)}/?${fields}`);
  }
  if (node.kind === 'vmess') {
    if (node.realityPublicKey || node.certificateFingerprint || node.transport === 'xhttp') return null;
    const fields = cleanFields({ v: '2', ps: node.name, add: node.server, port: node.port, id: normalizedUUID(node.uuid!),
      aid: node.alterID ?? 0, scy: node.cipher ?? 'auto', net: node.transport === 'http' ? 'tcp' : node.transport ?? 'tcp',
      type: node.transport === 'http' ? 'http' : 'none', host: node.hostHeader, path: absolutePath(node.path), sni: sni(node),
      tls: node.tls ? 'tls' : '', alpn: node.alpn, fp: node.fingerprint, allowInsecure: node.skipCertificateVerification ? '1' : undefined });
    return 'vmess://' + Buffer.from(JSON.stringify(fields)).toString('base64');
  }
  const name = node.kind === 'http' && node.tls ? 'https' : node.kind;
  if (node.kind === 'snell') return surgeNode(node);
  const singleCredential = ['trojan', 'hysteria', 'hysteria2', 'wireguard', 'vless', 'anytls'].includes(node.kind);
  const user = node.kind === 'vless' || node.kind === 'tuic' ? normalizedUUID(node.uuid!) :
    node.kind === 'wireguard' ? node.wireGuardPrivateKey : singleCredential ? node.password : node.username;
  const includePassword = !singleCredential && !!node.password;
  const login = user || includePassword ? encode(user ?? '') + (includePassword ? ':' + encode(node.password!) : '') + '@' : '';
  const defaults: Fields = {
    type: node.transport, mode: node.transportMode, security: node.realityPublicKey ? 'reality' : node.tls ? 'tls' : undefined, sni: sni(node),
    [asAnywhere && node.transport === 'grpc' ? 'authority' : 'host']: node.hostHeader,
    [node.transport === 'grpc' ? 'serviceName' : 'path']: node.transport === 'grpc' ? node.path?.replace(/^\//, '') : absolutePath(node.path),
    alpn: node.alpn, pbk: node.realityPublicKey, sid: node.realityShortID, flow: node.flow, encryption: node.vlessEncryption,
    'support-x25519mlkem768': node.realitySupportsX25519MLKEM768 ? '1' : undefined,
    fp: asAnywhere ? anywhereFingerprint(node.fingerprint) : node.fingerprint,
    allowInsecure: !asAnywhere && node.skipCertificateVerification ? '1' : undefined, pinSHA256: node.certificateFingerprint,
  };
  if (node.kind === 'wireguard') Object.assign(defaults, { publickey: node.wireGuardPublicKey, presharedkey: node.wireGuardPreSharedKey,
    address: wireguardCIDRs(node).join(','), allowedips: wireguardIPs(node).join(','), mtu: node.wireGuardMTU,
    reserved: node.wireGuardReserved, keepalive: node.wireGuardPersistentKeepalive, dns: node.wireGuardDNS });
  if (['hysteria', 'hysteria2'].includes(node.kind)) Object.assign(defaults, { obfs: node.obfs, 'obfs-password': node.obfsParam,
    mport: node.portHopping, upmbps: node.upMbps, downmbps: node.downMbps, protocol: node.protocolName });
  if (node.kind === 'tuic') Object.assign(defaults, { congestion_control: node.congestionControl, udp_relay_mode: node.udpRelayMode });
  if (node.kind === 'anytls') Object.assign(defaults, { [asAnywhere ? 'ici' : 'idle-session-check-interval']: node.idleSessionCheckInterval,
    [asAnywhere ? 'it' : 'idle-session-timeout']: node.idleSessionTimeout, [asAnywhere ? 'mis' : 'min-idle-session']: node.minIdleSession });
  if (node.kind === 'ssh') Object.assign(defaults, { 'private-key': node.ssh?.privateKey, 'private-key-passphrase': node.ssh?.privateKeyPassphrase,
    'host-key': node.ssh?.hostKeys.join(','), 'host-key-algorithms': node.ssh?.hostKeyAlgorithms.join(',') });
  if (node.kind === 'trusttunnel') Object.assign(defaults, { quic: node.trustTunnel?.quic ? 'true' : undefined, 'client-random-auth-key': node.trustTunnel?.clientRandomAuthKey });
  if (node.kind === 'masque') Object.assign(defaults, { 'private-key': node.masque?.privateKey, 'public-key': node.masque?.publicKey,
    ip: node.masque?.ipv4, ipv6: node.masque?.ipv6, network: node.masque?.network, mtu: node.masque?.mtu, dns: node.masque?.dns?.join(',') });
  const query = asAnywhere && node.kind === 'socks5' ? '' : Object.entries(cleanFields(defaults)).map(([key, value]) => encode(key) + '=' + encode(String(value))).join('&');
  return `${name}://${login}${endpoint(node)}${query ? '?' + query : ''}${remark}`;
}

function anywhereFingerprint(value?: string | null): string | undefined {
  const aliases: Record<string, string> = { chrome: 'chrome_133', firefox: 'firefox_148', safari: 'safari_26', ios: 'chrome_120', edge: 'edge_106' };
  return value && !['random', 'randomized'].includes(value) ? aliases[value] ?? value : undefined;
}

export function anywhereProxy(node: ProxyNode): string | Fields | null {
  const uri = proxyURI(node, true);
  if (!uri || !node.skipCertificateVerification) return uri;
  if (!['vless', 'trojan', 'anytls'].includes(node.kind) || node.realityPublicKey) return null;
  const transport = node.transport && node.transport !== 'tcp' ? cleanFields({ type: node.transport,
    path: node.transport !== 'grpc' ? absolutePath(node.path) : undefined,
    host: node.hostHeader, serviceName: node.transport === 'grpc' ? node.path?.replace(/^\//, '') : undefined,
    mode: node.transportMode }) : undefined;
  return cleanFields({ name: node.name, protocol: node.kind, server: node.server, port: node.port,
    uuid: node.uuid, password: node.password, encryption: node.vlessEncryption, flow: node.flow, transport,
    security: cleanFields({ type: 'tls', insecure: true, sni: sni(node), fingerprint: anywhereFingerprint(node.fingerprint) ?? 'plain',
      alpn: nonempty(list(node.alpn)) }), idleCheckInterval: node.idleSessionCheckInterval,
    idleTimeout: node.idleSessionTimeout, minIdleSession: node.minIdleSession });
}

function singTLS(node: ProxyNode): Fields | undefined {
  if (!(node.tls || alwaysTLS.has(node.kind)) || ['ss', 'snell', 'wireguard', 'ssh'].includes(node.kind)) return undefined;
  return cleanFields({ enabled: true, server_name: sni(node) ?? node.server, insecure: node.skipCertificateVerification,
    alpn: nonempty(list(node.alpn)), utls: node.realityPublicKey || node.fingerprint ? { enabled: true, fingerprint: node.fingerprint ?? 'chrome' } : undefined,
    reality: node.realityPublicKey ? cleanFields({ enabled: true, public_key: node.realityPublicKey, short_id: node.realityShortID }) : undefined });
}

export function singProxy(node: ProxyNode, target: ClientTarget, helperTag = node.name + ' ShadowTLS'): { outbound?: Fields; endpoint?: Fields; helper?: Fields } {
  const base = { tag: node.name, server: node.server, server_port: node.port };
  const tls = singTLS(node);
  const result: Fields = { type: node.kind, ...base, tls };
  switch (node.kind) {
    case 'ss': {
      Object.assign(result, { type: 'shadowsocks', method: node.cipher, password: node.password, network: node.udpRelayEnabled === false ? 'tcp' : undefined });
      if (pluginType(node) === 'v2ray-plugin') {
        result.plugin = 'v2ray-plugin';
        result.plugin_opts = node.plugin?.includes(';') ? node.plugin.slice(node.plugin.indexOf(';') + 1) :
          ['mode=websocket', node.tls ? 'tls' : null, node.hostHeader ? 'host=' + node.hostHeader : null,
            node.path ? 'path=' + absolutePath(node.path) : null, node.pluginMux == null ? null : 'mux=' + Number(node.pluginMux)].filter(Boolean).join(';');
      } else if (simpleObfs(node)) {
        result.plugin = 'obfs-local';
        result.plugin_opts = 'obfs=' + node.obfs + (node.hostHeader || node.obfsParam ? ';obfs-host=' + (node.hostHeader ?? node.obfsParam) : '');
      }
      if (node.shadowTLS) {
        result.detour = helperTag;
        result.network = 'tcp';
        return { outbound: cleanFields(result), helper: { type: 'shadowtls', tag: helperTag, server: node.server, server_port: node.port,
          version: node.shadowTLS.version, password: node.shadowTLS.password, tls: cleanFields({ enabled: true,
            server_name: node.shadowTLS.host, insecure: node.shadowTLS.skipCertificateVerification ?? false,
            utls: node.fingerprint ? { enabled: true, fingerprint: node.fingerprint } : undefined }) } };
      }
      break;
    }
    case 'vmess':
      Object.assign(result, { uuid: normalizedUUID(node.uuid!), security: node.cipher && node.cipher !== 'auto' ? node.cipher : 'aes-128-gcm',
        alter_id: node.alterID ?? 0 });
      break;
    case 'vless': Object.assign(result, { uuid: normalizedUUID(node.uuid!), flow: node.flow }); break;
    case 'trojan': result.password = node.password; break;
    case 'hysteria':
      Object.assign(result, { auth_str: node.password, up_mbps: node.upMbps ?? 50, down_mbps: node.downMbps ?? 100, obfs: node.obfs });
      break;
    case 'hysteria2':
      Object.assign(result, { password: node.password, up_mbps: node.upMbps, down_mbps: node.downMbps,
        obfs: node.obfs && node.obfsParam ? { type: node.obfs, password: node.obfsParam } : undefined,
        server_ports: nonempty(list(node.portHopping).map(part => /[:-]/.test(part) ? part.replace('-', ':') : part + ':' + part)) });
      break;
    case 'tuic':
      Object.assign(result, { uuid: normalizedUUID(node.uuid!), password: node.password,
        congestion_control: node.congestionControl, udp_relay_mode: node.udpRelayMode });
      break;
    case 'wireguard':
      if (target === 'sing-box') {
        return { endpoint: cleanFields({ type: 'wireguard', tag: node.name, address: wireguardCIDRs(node),
          private_key: node.wireGuardPrivateKey, mtu: node.wireGuardMTU, peers: [cleanFields({ address: node.server, port: node.port,
            public_key: node.wireGuardPublicKey, pre_shared_key: node.wireGuardPreSharedKey, allowed_ips: wireguardIPs(node),
            reserved: reservedBytes(node), persistent_keepalive_interval: node.wireGuardPersistentKeepalive })] }) };
      }
      Object.assign(result, { local_address: wireguardCIDRs(node), private_key: node.wireGuardPrivateKey,
        peer_public_key: node.wireGuardPublicKey, pre_shared_key: node.wireGuardPreSharedKey, reserved: reservedBytes(node), mtu: node.wireGuardMTU });
      break;
    case 'anytls':
      Object.assign(result, { password: node.password, idle_session_check_interval: node.idleSessionCheckInterval == null ? undefined : node.idleSessionCheckInterval + 's',
        idle_session_timeout: node.idleSessionTimeout == null ? undefined : node.idleSessionTimeout + 's', min_idle_session: node.minIdleSession });
      break;
    case 'snell':
      Object.assign(result, { psk: node.password, version: 4, obfs_mode: simpleObfs(node), obfs_host: node.obfsParam });
      break;
    case 'socks5':
    case 'http':
      Object.assign(result, { type: node.kind === 'socks5' ? 'socks' : 'http', version: node.kind === 'socks5' ? '5' : undefined,
        username: node.username, password: node.password });
      break;
    case 'ssh':
      Object.assign(result, { user: node.username, password: node.password, private_key: node.ssh?.privateKey,
        private_key_passphrase: node.ssh?.privateKeyPassphrase, host_key: nonempty(node.ssh?.hostKeys ?? []),
        host_key_algorithms: nonempty(node.ssh?.hostKeyAlgorithms ?? []) });
      break;
  }
  const type = node.transport;
  if (v2Transport.has(node.kind) && type && type !== 'tcp') result.transport = cleanFields({
    type: type === 'h2' ? 'http' : type,
    service_name: type === 'grpc' ? node.path?.replace(/^\//, '') : undefined,
    path: ['grpc', 'quic'].includes(type) ? undefined : type === 'ws' ? wsPath(node.path) : absolutePath(node.path),
    host: type === 'httpupgrade' ? node.hostHeader : ['http', 'h2'].includes(type) && node.hostHeader ? [node.hostHeader] : undefined,
    headers: type === 'ws' && node.hostHeader ? { Host: node.hostHeader } : undefined,
    max_early_data: type === 'ws' ? earlyData(node.path) : undefined,
    early_data_header_name: type === 'ws' && earlyData(node.path) ? 'Sec-WebSocket-Protocol' : undefined,
  });
  return { outbound: cleanFields(result) };
}

export function egernProxy(node: ProxyNode): Fields {
  const hostname = sni(node);
  const fields: Fields = { name: node.name, server: node.server, port: node.port, password: node.password };
  let type: string = node.kind;
  switch (node.kind) {
    case 'ss':
    case 'ssr':
      type = node.kind === 'ss' ? 'shadowsocks' : 'shadowsocksr';
      fields.method = node.cipher === 'chacha20-ietf-poly1305' ? 'chacha20-poly1305' : node.cipher;
      if (node.kind === 'ssr') Object.assign(fields, { protocol: node.protocolName, obfs: node.obfs, protocol_param: node.protocolParam, obfs_param: node.obfsParam });
      else if (simpleObfs(node)) Object.assign(fields, { obfs: node.obfs, obfs_host: node.hostHeader ?? node.obfsParam });
      if (node.shadowTLS) fields.shadow_tls = { password: node.shadowTLS.password, sni: node.shadowTLS.host };
      break;
    case 'vmess':
    case 'vless': {
      fields.user_id = normalizedUUID(node.uuid!);
      if (node.kind === 'vmess') Object.assign(fields, { legacy: false, security: node.cipher && node.cipher !== 'auto' ? node.cipher : 'aes-128-gcm' });
      if (node.kind === 'vless') fields.flow = node.flow;
      const transport = node.transport ?? 'tcp';
      const tls = node.tls || !!node.realityPublicKey;
      const transportName = ({ ws: tls ? 'wss' : 'ws', http: 'http1', h2: 'http2', grpc: 'grpc', tcp: tls ? 'tls' : undefined } as Record<string, string | undefined>)[transport];
      if (transportName) fields.transport = { [transportName]: cleanFields({
        path: ['grpc', 'tcp'].includes(transport) ? undefined : wsPath(node.path) ?? '/',
        headers: !['grpc', 'tcp'].includes(transport) && node.hostHeader ? { Host: node.hostHeader } : undefined,
        service_name: transport === 'grpc' ? node.path?.replace(/^\//, '') : undefined,
        sni: tls ? hostname : undefined,
        reality: node.realityPublicKey ? cleanFields({ public_key: node.realityPublicKey, short_id: node.realityShortID }) : undefined,
        skip_tls_verify: tls ? node.skipCertificateVerification : undefined,
        fingerprint_sha256: tls ? node.certificateFingerprint : undefined,
      }) };
      break;
    }
    case 'trojan':
      if (node.transport === 'ws') fields.websocket = cleanFields({ path: wsPath(node.path) ?? '/', host: node.hostHeader });
      break;
    case 'hysteria2':
      delete fields.password;
      Object.assign(fields, { auth: node.password, obfs: node.obfsParam ? node.obfs : undefined,
        obfs_password: node.obfsParam, port_hopping: node.portHopping, bandwidth: node.upMbps });
      break;
    case 'tuic':
      Object.assign(fields, { uuid: normalizedUUID(node.uuid!), alpn: nonempty(list(node.alpn)) ?? ['h3'], udp_relay_mode: node.udpRelayMode });
      break;
    case 'wireguard':
      Object.assign(fields, { private_key: node.wireGuardPrivateKey, peer_public_key: node.wireGuardPublicKey,
        preshared_key: node.wireGuardPreSharedKey, local_ipv4: node.wireGuardIPv4, local_ipv6: node.wireGuardIPv6,
        reserved: reservedBytes(node), mtu: node.wireGuardMTU, dns_servers: nonempty(list(node.wireGuardDNS)), keepalive: node.wireGuardPersistentKeepalive });
      break;
    case 'snell':
      delete fields.password;
      Object.assign(fields, { psk: node.password, version: node.version ?? 4 });
      break;
    case 'ssh':
      Object.assign(fields, { username: node.username, private_key: node.ssh?.privateKey, host_keys: nonempty(node.ssh?.hostKeys ?? []) });
      break;
    case 'socks5':
    case 'http':
      if (node.tls) type = node.kind === 'socks5' ? 'socks5_tls' : 'https';
      fields.username = node.username;
      break;
  }
  if (node.kind !== 'ssh') fields.udp_relay = node.udpRelayEnabled ?? true;
  if ((node.tls || alwaysTLS.has(node.kind)) && !['ss', 'ssr', 'vmess', 'vless', 'ssh'].includes(node.kind)) {
    Object.assign(fields, { sni: hostname, skip_tls_verify: node.skipCertificateVerification, fingerprint_sha256: node.certificateFingerprint,
      reality: node.realityPublicKey ? cleanFields({ public_key: node.realityPublicKey, short_id: node.realityShortID }) : undefined });
  }
  return { [type]: cleanFields(fields) };
}
