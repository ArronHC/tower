import { Router } from 'express';
import type { Response } from 'express';
import QRCode from 'qrcode';
import { z } from 'zod';
import type { ClientTarget, ExportContentMode } from '../../../shared/src/index.js';
import { generateConfiguration } from '../services/configGenerator.js';
import type { DirectImportService } from '../services/directImportService.js';
import { acceptsAJSON, contentDisposition, lanTarget, lanURL, subscriptionHeaders, subscriptionPayload, validateLANAccess } from '../services/lanSubscription.js';
import type { AppStateService } from '../store/appStateService.js';
import { invalid } from './errors.js';
import { modeSchema, targetSchema } from './schemas.js';

const generateSchema = z.object({ target: targetSchema, mode: modeSchema, maskSecrets: z.boolean().default(true) });
const downloadSchema = z.object({ target: targetSchema, mode: modeSchema.default('fullConfiguration') });
const subscriptionQuery = z.object({ target: z.string().optional(), content: modeSchema.default('fullConfiguration') });
const qrSchema = z.object({ format: z.string().default('auto'), content: modeSchema.default('fullConfiguration'), baseUrl: z.string().optional() });
const localHosts = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);
export const isLoopback = (address?: string) => Boolean(address && (localHosts.has(address) || address.startsWith('::ffff:127.')));

export function configurationFor(state: AppStateService, target: ClientTarget, mode: ExportContentMode, maskSecrets: boolean, surfboard = false) {
  return generateConfiguration(state.snapshot, {
    target, mode, maskSecrets, surfboard, runtime: state.runtimeForExport(), keys: state.authKeys, downloads: state.downloads,
  });
}

function sendConfig(res: Response, content: string, mimeType: string, filename: string) {
  res.set({ 'Cache-Control': 'no-store', 'Content-Type': mimeType + '; charset=utf-8', 'Content-Disposition': contentDisposition(filename) });
  res.send(content);
}

export function exportRoutes(state: AppStateService, directImports: DirectImportService): Router {
  const routes = Router();
  routes.post('/export/generate', async (req, res) => {
    const { target, mode, maskSecrets } = generateSchema.parse(req.body);
    const preview = configurationFor(state, target, mode, maskSecrets);
    const generation = state.revision;
    if ((preview.hasExportableProxies || state.snapshot.tailnets.some(item => item.isEnabled)) && isLoopback(req.socket.remoteAddress) && isLoopback(req.hostname)) {
      const link = await directImports.issue(target, preview.contentMode, () => generation === state.revision ? configurationFor(state, target, mode, false) : null)
        .catch(() => null);
      preview.directImportURL = link;
    }
    res.json(preview);
  });
  routes.get('/export/download', (req, res) => {
    const { target, mode } = downloadSchema.parse(req.query);
    const config = configurationFor(state, target, mode, false);
    sendConfig(res, config.content, config.mimeType, config.fileName);
  });
  routes.get('/lan/status', (_req, res) => { res.json(state.lanStatus); });
  routes.post('/lan/toggle', (req, res) => {
    const { enabled } = z.object({ enabled: z.boolean() }).parse(req.body);
    res.json(state.setLAN(enabled));
  });
  routes.post('/lan/rotate-token', (_req, res) => { res.json(state.setLAN(state.lanStatus.enabled, true)); });
  routes.get('/lan/qr', async (req, res) => {
    const { format, content, baseUrl } = qrSchema.parse(req.query);
    const lan = state.lanStatus;
    const url = lanURL(baseUrl ?? lan.primaryURL, lan.token, format, content);
    if (url.length > 2048) invalid('This URL is too long for a QR code');
    const qrDataUrl = await QRCode.toDataURL(url, { width: 256, margin: 2 });
    res.json({ url, qrDataUrl });
  });
  return routes;
}

export function lanRoutes(state: AppStateService): Router {
  const routes = Router();
  routes.get('/:token', (req, res) => {
    const lan = state.lanStatus;
    validateLANAccess(lan.enabled, lan.token, req.params.token);
    const { target, content } = subscriptionQuery.parse(req.query);
    const header = req.get('X-Anywhere-Format');
    const flavor = lanTarget(target, req.get('User-Agent'), header);
    const config = configurationFor(state, flavor.target, content, false, flavor.surfboard);
    const payload = subscriptionPayload(config, acceptsAJSON(header));
    res.set({ ...subscriptionHeaders(state.snapshot), 'X-Tower-Target': flavor.surfboard ? 'surfboard' : flavor.target, Vary: 'User-Agent, X-Anywhere-Format' });
    sendConfig(res, payload.content, payload.mimeType, payload.filename);
  });
  return routes;
}
