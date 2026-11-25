import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

function findServerDir(): string {
  let current = path.dirname(fileURLToPath(import.meta.url));
  while (current !== path.dirname(current)) {
    if (existsSync(path.join(current, 'tsconfig.json')) &&
        existsSync(path.join(current, '../../Tower/Resources'))) return current;
    current = path.dirname(current);
  }
  throw new Error('Cannot locate the Tower repository');
}

export const serverDir = findServerDir();
export const repoRoot = path.resolve(serverDir, '../..');
export const resourcesDir = path.join(repoRoot, 'Tower/Resources');
export const clientIconsDir = path.join(repoRoot, 'docs/images/clients');
export const towerIconPath = path.join(repoRoot, 'docs/images/icon.png');
export const clientDistDir = path.join(serverDir, '../client/dist');
export const dataDir = path.resolve(process.env.TOWER_DATA_DIR || path.join(serverDir, 'data'));
