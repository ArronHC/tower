import { readFileSync } from 'node:fs';
import { isIP } from 'node:net';
import path from 'node:path';
import { resourcesDir } from '../paths.js';

export function ipBytes(value: string): Buffer | undefined {
  const address = value.replace(/^\[|\]$/g, '').split('%')[0];
  if (isIP(address) === 4) return Buffer.from(address.split('.').map(Number));
  if (isIP(address) !== 6) return undefined;
  const canonical = new URL(`http://[${address}]/`).hostname.slice(1, -1);
  const [left, right] = canonical.split('::');
  const groups = left ? left.split(':') : [];
  if (right !== undefined) {
    const suffix = right ? right.split(':') : [];
    groups.push(...Array<string>(8 - groups.length - suffix.length).fill('0'), ...suffix);
  }
  const bytes = Buffer.alloc(16);
  groups.forEach((word, i) => bytes.writeUInt16BE(parseInt(word, 16), i * 2));
  if (bytes.subarray(0, 12).equals(Buffer.from('00000000000000000000ffff', 'hex'))) return bytes.subarray(12);
  return bytes;
}

class RangeDatabase {
  constructor(private readonly v4: Buffer, private readonly v6: Buffer, private readonly extra: number) {
    if (v4.length % (8 + extra) || v6.length % (32 + extra)) throw new Error('Invalid IP database record size');
  }

  lookup(bytes: Buffer): [Buffer, number] | undefined {
    const width = bytes.length;
    const data = width === 4 ? this.v4 : this.v6;
    const stride = width * 2 + this.extra;
    let low = 0;
    let high = data.length / stride;
    while (low < high) {
      const middle = Math.floor((low + high) / 2);
      const offset = middle * stride;
      if (data.compare(bytes, 0, width, offset, offset + width) > 0) high = middle;
      else if (data.compare(bytes, 0, width, offset + width, offset + 2 * width) < 0) low = middle + 1;
      else return [data, offset + width * 2];
    }
    return undefined;
  }

  cidrs(matches: (data: Buffer, metadata: number) => boolean): string[] {
    const output: string[] = [];
    for (const [data, width] of [[this.v4, 4], [this.v6, 16]] as const) {
      const stride = width * 2 + this.extra;
      const word = (i: number) => width === 4 ? BigInt(data.readUInt32BE(i)) :
        (data.readBigUInt64BE(i) << 64n) | data.readBigUInt64BE(i + 8);
      let start = 0n;
      let end = -2n;
      for (let i = 0; i < data.length; i += stride) {
        if (!matches(data, i + width * 2)) continue;
        const next = word(i);
        if (next !== end + 1n) {
          appendBlocks(output, start, end, width);
          start = next;
        }
        end = word(i + width);
      }
      appendBlocks(output, start, end, width);
    }
    return output;
  }
}

function appendBlocks(output: string[], start: bigint, end: bigint, width: number) {
  const bits = width * 8;
  while (start <= end) {
    const remaining = end - start + 1n;
    const aligned = start === 0n ? bits : (start & -start).toString(2).length - 1;
    const hostBits = Math.min(aligned, remaining.toString(2).length - 1);
    let address: string;
    if (width === 4) address = [24n, 16n, 8n, 0n].map(shift => Number((start >> shift) & 255n)).join('.');
    else {
      const words = Array.from({ length: 8 }, (_, i) => ((start >> BigInt(112 - 16 * i)) & 65535n).toString(16));
      address = new URL(`http://[${words.join(':')}]/`).hostname.slice(1, -1);
    }
    output.push(`${address}/${bits - hostBits}`);
    start += 1n << BigInt(hostBits);
  }
}

export class IPDatabase {
  private readonly country: RangeDatabase;
  private readonly asn: RangeDatabase;
  private readonly names: Buffer;
  private readonly cidrCache = new Map<string, string[]>();
  readonly version: string;

  constructor(directory = path.join(resourcesDir, 'IPCountry')) {
    const load = (name: string) => readFileSync(path.join(directory, name));
    this.country = new RangeDatabase(load('IPCountryIPv4.bin'), load('IPCountryIPv6.bin'), 2);
    this.asn = new RangeDatabase(load('IPASNIPv4.bin'), load('IPASNIPv6.bin'), 10);
    this.names = load('IPASNNames.bin');
    this.version = load('IPCountryVersion.txt').toString().trim();
  }

  countryCode(address: string): string | null {
    const bytes = ipBytes(address);
    const result = bytes && this.country.lookup(bytes);
    return result ? result[0].toString('ascii', result[1], result[1] + 2) : null;
  }

  organization(address: string): { asn: number; name: string } | null {
    const bytes = ipBytes(address);
    const result = bytes && this.asn.lookup(bytes);
    if (!result) return null;
    const [data, offset] = result;
    const asn = data.readUInt32BE(offset);
    const start = data.readUInt32BE(offset + 4);
    const length = data.readUInt16BE(offset + 8);
    if (!asn || !length || start + length > this.names.length) return null;
    return { asn, name: this.names.toString('utf8', start, start + length) };
  }

  cidrsForCountry(code: string): string[] {
    const normalized = code.toUpperCase();
    if (!/^[A-Z]{2}$/.test(normalized)) return [];
    const match = Buffer.from(normalized).readUInt16BE(0);
    return this.cached(normalized, () => this.country.cidrs((data, i) => data.readUInt16BE(i) === match));
  }

  cidrsForASN(asn: number): string[] {
    if (!Number.isInteger(asn) || asn < 1 || asn > 0xffffffff) return [];
    return this.cached(String(asn), () => this.asn.cidrs((data, i) => data.readUInt32BE(i) === asn));
  }

  private cached(key: string, load: () => string[]): string[] {
    const previous = this.cidrCache.get(key);
    if (previous) return previous;
    const value = load();
    if (this.cidrCache.size >= 64) this.cidrCache.delete(this.cidrCache.keys().next().value!);
    this.cidrCache.set(key, value);
    return value;
  }
}

let database: IPDatabase | undefined;
export const ipDatabase = () => database ??= new IPDatabase();
export const cidrsForCountry = (code: string) => ipDatabase().cidrsForCountry(code);
