import { describe, expect, it } from 'vitest';
import { countryByCode, countries, flagEmoji } from '../services/countryTable.js';
import { ipBytes, ipDatabase } from '../services/ipCountryDb.js';
import { countryCodeFromName, nodeRegion, NodeRegionResolver } from '../services/nodeRegionResolver.js';
import { worldDotMap, parseWorldDotMap } from '../services/worldDotMapService.js';
import { node } from './fixtures.js';

describe('offline assets', () => {
  it('looks up IPv4, IPv6, and IPv4-mapped IPv6 in the packaged binary databases', () => {
    const database = ipDatabase();
    expect(database.version).toMatch(/^20\d\d-/);
    expect(database.countryCode('1.1.1.1')).toBe('AU');
    expect(database.organization('1.1.1.1')).toEqual({ asn: 13335, name: 'Cloudflare, Inc.' });
    expect(database.organization('2606:4700:4700::1111')?.asn).toBe(13335);
    expect(database.countryCode('2606:4700:4700::1111')).toMatch(/^[A-Z]{2}$/);
    expect(database.countryCode('::ffff:1.1.1.1')).toBe('AU');
    expect(database.organization('bad address')).toBeNull();
    expect(ipBytes('2001:db8::ffff:192.0.2.1')?.length).toBe(16);
  });
  it('expands indexed country and ASN ranges into IPv4 and IPv6 CIDRs', () => {
    const database = ipDatabase();
    const country = database.cidrsForCountry('au');
    const asn = database.cidrsForASN(13335);
    expect(country).toContain('1.1.1.0/24');
    expect(asn).toContain('1.1.1.0/24');
    expect(asn.some(cidr => cidr.startsWith('2606:4700:'))).toBe(true);
    expect(database.cidrsForCountry('invalid')).toEqual([]);
    expect(database.cidrsForASN(0)).toEqual([]);
  });
  it('loads all generated countries and translates flags', () => {
    expect(countries).toHaveLength(187);
    expect(flagEmoji('JP')).toBe('🇯🇵');
    expect(countryByCode.get('DE')?.englishName).toBe('Germany');
    expect(countryByCode.get('ZW')?.chineseName).toBe('津巴布韦');
  });
  it('loads the complete map and preserves land rows that begin with #', () => {
    const map = worldDotMap();
    expect(map).toMatchObject({ columns: 220, rows: 149, bounds: { minLat: -60, maxLat: 84, minLon: -180, maxLon: 180 } });
    expect(map.landRows).toHaveLength(149);
    expect(map.landRows.some(row => row.startsWith('#'))).toBe(true);
    expect(map.countryCells.JP.length).toBeGreaterThan(10);
    expect(Object.values(map.countryCells).flat().every(index => index >= 0 && index < 220 * 149)).toBe(true);
    expect(() => parseWorldDotMap('size 1 1', 'size 1 1')).toThrow();
  });
});

describe('region resolution', () => {
  it.each([
    ['🇯🇵 Singapore US', 'JP'], ['美国 HK', 'US'], ['HK 01', 'HK'], ['JP02', 'JP'],
    ['伦敦', 'GB'], ['Johannesburg | 01', 'ZA'], ['Bogotá', 'CO'], ['FRA Premium', 'DE'],
    ['My Node', null], ['Contact us', null], ['SS WS 90 GB', null],
  ])('resolves %s by precedence', (name, code) => {
    expect(countryCodeFromName(name)).toBe(code);
  });
  it('applies manual overrides and looks up ASN independently of the display name', async () => {
    const resolver = new NodeRegionResolver();
    const requested = node({ server: '1.1.1.1', countryOverride: 'CA' });
    expect(await resolver.resolve(requested)).toMatchObject({ countryCode: 'CA', regionSource: 'override', asn: 13335 });
    expect(await resolver.resolve({ ...requested, countryOverride: null })).toMatchObject({ countryCode: 'JP', regionSource: 'name', asn: 13335 });
    expect(await resolver.resolve({ ...requested, countryOverride: null, name: 'Fixture' })).toMatchObject({ countryCode: 'AU', regionSource: 'ip' });
    expect(nodeRegion(node({ server: 'something.example.jp', name: 'Fixture' })).countryCode).toBeNull();
  });
});
