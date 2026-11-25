import type { ProxyNode } from '../../../shared/src/index.js';

export const id = '8c2ac9ca-99b7-4d10-90bc-c2e49cc39b0d';
export const uuid = '42d615fc-99b2-4540-970f-ba18461528e1';
export const testKey = 'tskey-auth-fixture-key';
export const ssCredentials = Buffer.from('aes-128-gcm:example-secret').toString('base64url');

export function ssURI(server = '127.0.0.1', name = '🇯🇵 Tokyo Fixture', port = 8388) {
  return `ss://${ssCredentials}@${server}:${port}#${encodeURIComponent(name)}`;
}

export const node = (overrides: Partial<ProxyNode> = {}): ProxyNode => ({
  id, kind: 'ss', name: '🇯🇵 Tokyo Fixture', server: '127.0.0.1', port: 8388, cipher: 'aes-128-gcm',
  password: 'example-secret', tls: false, skipCertificateVerification: false, rawURI: ssURI(), ...overrides,
});
