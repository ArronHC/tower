import { execFile } from 'node:child_process';
import { Socket } from 'node:net';
import { performance } from 'node:perf_hooks';
import type { NodeLatencyMeasurement, NodeLatencyTestMode, ProxyNode } from '../../../shared/src/index.js';

const udpOnly = new Set(['wireguard', 'hysteria', 'hysteria2', 'tuic', 'masque']);

function tcp(server: string, port: number, timeout: number): Promise<number | null> {
  return new Promise(resolve => {
    const socket = new Socket();
    const start = performance.now();
    const finish = (value: number | null) => { socket.destroy(); resolve(value); };
    socket.setTimeout(timeout);
    socket.once('timeout', () => finish(null));
    socket.once('error', () => finish(null));
    socket.once('connect', () => finish(performance.now() - start));
    try { socket.connect(port, server); } catch { finish(null); }
  });
}

function icmp(server: string, timeout: number): Promise<number | null> {
  return new Promise(resolve => {
    if (!/^[\p{L}\p{N}_.:%-]+$/u.test(server) || server.startsWith('-')) return resolve(null);
    const args = process.platform === 'win32' ? ['-n', '1', '-w', String(timeout), server] :
      ['-n', '-c', '1', server];
    const executable = process.platform === 'darwin' && server.includes(':') ? 'ping6' : 'ping';
    execFile(executable, args, { timeout, maxBuffer: 16 * 1024, env: { ...process.env, LC_ALL: 'C' } }, (error, out) => {
      const time = /time[=<]\s*(\d+(?:\.\d+)?)\s*ms/i.exec(out);
      resolve(!error && time ? Number(time[1]) : null);
    });
  });
}

export async function probeNode(node: ProxyNode, mode: NodeLatencyTestMode, timeout = 2500): Promise<NodeLatencyMeasurement> {
  const testedAt = new Date().toISOString();
  const supportsTCP = !udpOnly.has(node.kind);
  if (mode === 'tcp' && !supportsTCP) return { testedAt, milliseconds: null, method: null, isApplicable: false };
  if (mode !== 'tcp') {
    const milliseconds = await icmp(node.server, timeout);
    if (milliseconds !== null || mode === 'icmp' || !supportsTCP) {
      return { testedAt, milliseconds, method: 'ICMP', isApplicable: true, errorMessage: milliseconds === null ? 'ICMP probe failed' : null };
    }
  }
  const milliseconds = await tcp(node.server, node.port, timeout);
  return { testedAt, milliseconds, method: 'TCP', isApplicable: true, errorMessage: milliseconds === null ? 'TCP connection failed' : null };
}

export async function mapConcurrent<T, R>(items: T[], concurrency: number, task: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = [];
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(items.length, concurrency) }, async () => {
    while (next < items.length) {
      const i = next++;
      results[i] = await task(items[i]);
    }
  }));
  return results;
}
