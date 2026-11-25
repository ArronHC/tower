import { readFileSync } from 'node:fs';
import path from 'node:path';
import type { WorldDotMapData } from '../../../shared/src/index.js';
import { resourcesDir } from '../paths.js';

export function parseWorldDotMap(land: string, ownership: string): WorldDotMapData {
  function grid(text: string) {
    const all = text.trim().split(/\r?\n/);
    const header = all.findIndex(line => line.startsWith('size '));
    const size = /^size (\d+) (\d+)$/.exec(all[header] ?? '');
    const lines = all.slice(header + 1);
    if (!size || lines.length !== Number(size[2])) throw new Error('Invalid world map grid');
    return { columns: Number(size[1]), rows: Number(size[2]), lines };
  }
  const { columns, rows, lines: landRows } = grid(land);
  const owners = grid(ownership);
  if (owners.columns !== columns || owners.rows !== rows || landRows.some(row => row.length !== columns || /[^.#]/.test(row))) {
    throw new Error('Mismatched world map grids');
  }
  const countryCells: Record<string, number[]> = {};
  owners.lines.forEach((line, row) => {
    const cells = line.trim().split(/\s+/);
    if (cells.length !== columns) throw new Error('Invalid world country grid row');
    cells.forEach((code, col) => {
      if (/^[A-Z]{2}$/.test(code)) (countryCells[code] ??= []).push(row * columns + col);
    });
  });
  const bounds = /bounds: lon ([-\d.]+)\.\.([-\d.]+), lat ([-\d.]+)\.\.([-\d.]+)/.exec(land);
  if (!bounds) throw new Error('Missing world map bounds');
  const [minLon, maxLon, minLat, maxLat] = bounds.slice(1).map(Number);
  return { columns, rows, landRows, countryCells, bounds: { minLat, maxLat, minLon, maxLon } };
}

let map: WorldDotMapData | undefined;
export function worldDotMap(): WorldDotMapData {
  const read = (file: string) => readFileSync(path.join(resourcesDir, 'WorldMap', file), 'utf8');
  return map ??= parseWorldDotMap(read('WorldDotMap.txt'), read('WorldDotCountries.txt'));
}
