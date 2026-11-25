import { randomBytes } from 'node:crypto';
import { chmodSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { dataDir } from '../paths.js';
import type { AppSnapshot } from '../../../shared/src/index.js';

export interface PersistedLAN {
  enabled: boolean;
  token: string;
}

export interface StoredState {
  snapshot: AppSnapshot;
  lan: PersistedLAN;
}

const privateMode = 0o600;
export const newLANToken = () => randomBytes(32).toString('base64url');

export function publicSnapshot(snapshot: AppSnapshot): AppSnapshot {
  return {
    ...snapshot,
    tailnets: snapshot.tailnets.map(({ id, name, controlURLString, deviceName, subnets, magicDNSSuffix, isEnabled, stableSlug }) =>
      ({ id, name, controlURLString, deviceName, subnets, magicDNSSuffix, isEnabled, stableSlug })),
  };
}

export class PersistenceStore {
  private readonly stateFile: string;
  private readonly keysFile: string;
  private readonly rulesFile: string;

  constructor(private readonly directory = dataDir) {
    this.stateFile = path.join(directory, 'state.json');
    this.keysFile = path.join(directory, 'tailnet-keys.json');
    this.rulesFile = path.join(directory, 'downloaded-rules.json');
  }

  readState(): StoredState | AppSnapshot | null {
    return this.read(this.stateFile);
  }

  writeState(value: StoredState) {
    this.write(this.stateFile, { snapshot: publicSnapshot(value.snapshot), lan: value.lan });
  }

  readKeys(): Record<string, string> {
    return this.read<Record<string, string>>(this.keysFile) ?? {};
  }

  writeKeys(value: Record<string, string>) {
    this.write(this.keysFile, value);
  }

  readRules(): Record<string, string> {
    return this.read<Record<string, string>>(this.rulesFile) ?? {};
  }

  writeRules(value: Record<string, string>) {
    this.write(this.rulesFile, value);
  }

  private read<T>(file: string): T | null {
    try {
      const data = readFileSync(file, 'utf8');
      chmodSync(file, privateMode);
      return JSON.parse(data) as T;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return null;
      throw error;
    }
  }

  private write(file: string, value: unknown) {
    mkdirSync(this.directory, { recursive: true, mode: 0o700 });
    const temporary = path.join(this.directory, `.${path.basename(file)}.${randomBytes(12).toString('hex')}.tmp`);
    try {
      writeFileSync(temporary, JSON.stringify(value, null, 2) + '\n', { mode: privateMode, flag: 'wx' });
      renameSync(temporary, file);
    } finally {
      try { unlinkSync(temporary); }
      catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
    }
  }
}
