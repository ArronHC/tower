import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppStateService } from '../store/appStateService.js';
import type { StateOptions } from '../store/appStateService.js';
import { PersistenceStore } from '../store/persistenceStore.js';
import { parseSubscription } from '../services/subscriptionParser.js';
import { ssURI } from './fixtures.js';

let directory: string;
beforeEach(() => { directory = mkdtempSync(path.join(tmpdir(), 'tower-state-')); });
afterEach(() => { rmSync(directory, { force: true, recursive: true }); });

function waitingFetcher() {
  const waiting = new Map<string, () => void>();
  let suspend = false;
  const fetcher: NonNullable<StateOptions['fetcher']> = async (url, _options, sourceID) => {
    if (suspend) await new Promise<void>(resolve => waiting.set(url, resolve));
    const parsed = parseSubscription(ssURI('192.0.2.50'), sourceID);
    return { ...parsed, usage: null, name: null };
  };
  return { fetcher, waiting, suspend: () => { suspend = true; } };
}

describe('concurrent subscription refresh', () => {
  it('runs different hostname queues concurrently and serializes the same hostname', async () => {
    const { fetcher, waiting, suspend } = waitingFetcher();
    const state = new AppStateService({ store: new PersistenceStore(directory), fetcher });
    const first = 'https://a.example/one';
    const same = 'https://a.example/two';
    const other = 'https://b.example/one';
    for (const urlString of [first, same, other]) await state.addSubscription({ urlString });
    suspend();
    const batch = state.refreshAll();
    await vi.waitFor(() => expect([...waiting.keys()]).toEqual([first, other]));
    waiting.get(first)!();
    await vi.waitFor(() => expect([...waiting.keys()]).toEqual([first, other, same]));
    waiting.get(other)!();
    waiting.get(same)!();
    expect((await batch).results).toHaveLength(3);
  });

  it('does not let a late response restore a removed source or its nodes', async () => {
    const { fetcher, waiting, suspend } = waitingFetcher();
    const state = new AppStateService({ store: new PersistenceStore(directory), fetcher });
    const urlString = 'https://a.example/source';
    const { subscription } = await state.addSubscription({ urlString });
    suspend();
    const task = state.refreshSubscription(subscription.id);
    const outcome = expect(task).rejects.toMatchObject({ status: 409 });
    await vi.waitFor(() => expect(waiting.has(urlString)).toBe(true));
    state.removeSubscription(subscription.id);
    waiting.get(urlString)!();
    await outcome;
    expect(state.snapshot.subscriptions).toEqual([]);
    expect(state.snapshot.nodes).toEqual([]);
  });
});
