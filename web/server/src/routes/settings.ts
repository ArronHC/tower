import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import type { AppStateService } from '../store/appStateService.js';
import { publicSnapshot } from '../store/persistenceStore.js';
import { exportPreferencesSchema, settingsSchema, tailnetInputSchema, unique } from './schemas.js';
import { required } from './errors.js';
import { DEFAULT_CLIENT_ORDER } from '../../../shared/src/index.js';

export function settingsRoutes(state: AppStateService): Router {
  const routes = Router();
  routes.patch('/settings', (req, res) => {
    const patch = settingsSchema.partial().parse(req.body);
    const { settings } = state.update(draft => { Object.assign(draft.settings, patch); });
    res.json({ settings, state: state.stateResponse() });
  });
  routes.put('/export/preferences', (req, res) => {
    const patch = exportPreferencesSchema.partial().parse(req.body);
    state.update(draft => {
      if (patch.clientOrder) patch.clientOrder = unique([...patch.clientOrder, ...DEFAULT_CLIENT_ORDER]);
      if (patch.visibleClientTargets) patch.visibleClientTargets = unique(patch.visibleClientTargets);
      const { excludedKinds, exportContentModes, ...preferences } = patch;
      Object.assign(draft, preferences);
      if (excludedKinds) Object.assign(draft.excludedKinds, excludedKinds);
      if (exportContentModes) Object.assign(draft.exportContentModes, exportContentModes);
    });
    res.json({ state: state.stateResponse() });
  });
  routes.get('/tailnets', (_req, res) => { res.json(state.getTailnets()); });
  routes.post('/tailnets', (req, res) => {
    const { authKey, ...input } = tailnetInputSchema.parse(req.body);
    const tailnet = state.updateTailnet({ ...input, id: randomUUID() }, authKey);
    res.status(201).json({ tailnet, state: state.stateResponse() });
  });
  routes.patch('/tailnets/:id', (req, res) => {
    const current = required(state.snapshot.tailnets.find(item => item.id === req.params.id), 'Tailnet not found');
    const { authKey, ...patch } = tailnetInputSchema.partial().parse(req.body);
    const tailnet = state.updateTailnet({ ...current, ...patch }, authKey);
    res.json({ tailnet, state: state.stateResponse() });
  });
  routes.delete('/tailnets/:id', (req, res) => {
    const current = required(state.snapshot.tailnets.find(item => item.id === req.params.id), 'Tailnet not found');
    state.updateTailnet(current, null, true);
    res.json({ ok: true, state: state.stateResponse() });
  });
  routes.get('/snapshot/export', (_req, res) => { res.json(publicSnapshot(state.snapshot)); });
  routes.post('/snapshot/import', (req, res) => {
    state.replace(req.body);
    res.json({ ok: true, state: state.stateResponse() });
  });
  routes.post('/snapshot/reset', (_req, res) => {
    state.replace(undefined, true);
    res.json({ ok: true, state: state.stateResponse() });
  });
  return routes;
}
