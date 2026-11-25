import { Router } from 'express';
import QRCode from 'qrcode';
import { z } from 'zod';
import type { AppStateService } from '../store/appStateService.js';
import { detectSourceInput } from '../services/subscriptionParser.js';
import { proxyURI, clashProxy } from '../services/nodeExport.js';
import { yamlDocument } from '../services/configGenerator.js';
import { invalid, required } from './errors.js';
import { latencySchema, nameFilterSchema, nameList, nodePatchSchema, requestOptionsSchema, subscriptionPatchSchema, text, title, unique } from './schemas.js';

const sourceImport = z.object({ urlString: text, name: title.optional(), requestOptions: requestOptionsSchema.nullish() });
const selectedNodes = z.object({ nodeIds: nameList.optional() });
const probeSchema = selectedNodes.extend({ mode: latencySchema.optional() });

export function subscriptionRoutes(state: AppStateService): Router {
  const routes = Router();
  routes.post('/sources/detect', (req, res) => {
    res.json(detectSourceInput(z.object({ input: z.string() }).parse(req.body).input));
  });
  routes.post('/subscriptions', async (req, res) => {
    res.status(201).json(await state.addSubscription(sourceImport.parse(req.body)));
  });
  routes.post('/subscriptions/refresh-all', async (_req, res) => {
    res.json(await state.refreshAll());
  });
  routes.patch('/subscriptions/:id', async (req, res) => {
    res.json(await state.patchSubscription(req.params.id, subscriptionPatchSchema.parse(req.body)));
  });
  routes.delete('/subscriptions/:id', (req, res) => {
    state.removeSubscription(req.params.id);
    res.json({ ok: true, state: state.stateResponse() });
  });
  routes.post('/subscriptions/:id/refresh', async (req, res) => {
    res.json(await state.refreshSubscription(req.params.id));
  });
  routes.post('/nodes/import', (req, res) => {
    const { content } = z.object({ content: text }).parse(req.body);
    res.status(201).json(state.importNodes(content));
  });
  routes.patch('/nodes/:id', (req, res) => {
    const node = state.patchNode(req.params.id, nodePatchSchema.parse(req.body));
    res.json({ node, state: state.stateResponse() });
  });
  routes.delete('/nodes/:id', (req, res) => {
    state.removeNode(req.params.id);
    res.json({ ok: true, state: state.stateResponse() });
  });
  routes.post('/nodes/exclusions', (req, res) => {
    const input = z.union([z.object({ excludedNodeIDs: nameList }), z.object({ nodeIds: nameList, excluded: z.boolean() })]).parse(req.body);
    const live = new Set(state.snapshot.nodes.map(node => node.id));
    const excludedNodeIDs = 'excludedNodeIDs' in input ? unique(input.excludedNodeIDs).filter(id => live.has(id)) :
      input.excluded ? unique([...state.snapshot.excludedNodeIDs, ...input.nodeIds.filter(id => live.has(id))]) :
      state.snapshot.excludedNodeIDs.filter(id => !input.nodeIds.includes(id));
    state.update(draft => { draft.excludedNodeIDs = excludedNodeIDs; });
    res.json({ excludedNodeIDs, state: state.stateResponse() });
  });
  routes.post('/nodes/latency', async (req, res) => {
    const { nodeIds, mode } = probeSchema.parse(req.body ?? {});
    res.json({ results: await state.latency(nodeIds, mode ?? state.snapshot.settings.latencyTestMode) });
  });
  routes.post('/nodes/resolve-geo', async (req, res) => {
    const { nodeIds } = selectedNodes.parse(req.body ?? {});
    res.json({ runtime: await state.resolveGeo(nodeIds) });
  });
  routes.get('/nodes/:id/share', async (req, res) => {
    const node = required(state.snapshot.nodes.find(item => item.id === req.params.id), 'Node not found');
    const uri = proxyURI(node) ?? yamlDocument({ proxies: [clashProxy(node, 'clash-apple')] });
    if (!uri) invalid('This node has no share format');
    let qrDataUrl: string;
    try { qrDataUrl = await QRCode.toDataURL(uri, { width: 256, margin: 2 }); }
    catch { invalid('This node is too large for a QR code'); }
    res.json({ uri, qrDataUrl });
  });
  routes.put('/nodes/export-filter', (req, res) => {
    const filter = nameFilterSchema.nullable().parse(req.body);
    state.update(draft => { draft.nodeExportNameFilter = filter; });
    res.json({ nodeExportNameFilter: filter, state: state.stateResponse() });
  });
  return routes;
}
