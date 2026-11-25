import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import express from 'express';
import type { ErrorRequestHandler, RequestHandler } from 'express';
import { ZodError } from 'zod';
import { clientDistDir, clientIconsDir, serverDir, towerIconPath } from './paths.js';
import { AppStateService } from './store/appStateService.js';
import type { StateOptions } from './store/appStateService.js';
import { ipDatabase } from './services/ipCountryDb.js';
import { countries } from './services/nodeRegionResolver.js';
import { worldDotMap } from './services/worldDotMapService.js';
import { DirectImportService } from './services/directImportService.js';
import { APIError } from './routes/errors.js';
import { exportRoutes, isLoopback, lanRoutes } from './routes/export.js';
import { ruleRoutes } from './routes/rules.js';
import { settingsRoutes } from './routes/settings.js';
import { subscriptionRoutes } from './routes/subscriptions.js';

const { version } = JSON.parse(readFileSync(path.join(serverDir, 'package.json'), 'utf8')) as { version: string };

interface AppOptions extends StateOptions {
  state?: AppStateService;
  allowRemoteAPI?: boolean;
  directImports?: DirectImportService;
  staticDir?: string;
}

function localAPI(allowRemote: boolean): RequestHandler {
  return (req, res, next) => {
    res.set('Cache-Control', 'no-store');
    if (!allowRemote && (!isLoopback(req.socket.remoteAddress) || !isLoopback(req.hostname))) {
      next(new APIError(403, 'The administration API requires a loopback connection', 'LOCAL_API_ONLY'));
      return;
    }
    const origin = req.get('origin');
    if (origin) {
      try {
        const from = new URL(origin);
        const host = req.get('host')?.toLowerCase();
        if (!['http:', 'https:'].includes(from.protocol) || from.host.toLowerCase() !== host) throw new Error('Cross-origin request');
      } catch {
        next(new APIError(403, 'Cross-origin API requests are not allowed', 'CROSS_ORIGIN'));
        return;
      }
    }
    if (req.get('sec-fetch-site') === 'cross-site') {
      next(new APIError(403, 'Cross-site API requests are not allowed', 'CROSS_ORIGIN'));
      return;
    }
    next();
  };
}

export function createApp(options: AppOptions = {}) {
  const app = express();
  const state = options.state ?? new AppStateService(options);
  const imports = options.directImports ?? new DirectImportService();
  const staticDir = options.staticDir ?? clientDistDir;
  app.locals.state = state;
  app.locals.directImports = imports;
  app.disable('x-powered-by');
  app.use((_req, res, next) => {
    res.set({ 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
    next();
  });

  app.use('/api', localAPI(options.allowRemoteAPI ?? false));
  app.use('/api', express.json({ limit: '24mb' }));
  app.get('/api/health', (_req, res) => {
    try {
      const ipDbVersion = ipDatabase().version;
      worldDotMap();
      res.json({ ok: true, version, resourcesReady: true, ipDbVersion });
    } catch { res.status(503).json({ ok: false, version, resourcesReady: false, ipDbVersion: null }); }
  });
  app.get('/api/state', (_req, res) => { res.json(state.stateResponse()); });
  app.get('/api/world-map', (_req, res) => { res.json(worldDotMap()); });
  app.get('/api/countries', (_req, res) => { res.json(countries); });
  app.use('/api', subscriptionRoutes(state));
  app.use('/api/rules', ruleRoutes(state));
  app.use('/api', settingsRoutes(state));
  app.use('/api', exportRoutes(state, imports));
  app.use('/api', (_req, _res, next) => { next(new APIError(404, 'API route not found', 'NOT_FOUND')); });
  app.use('/sub', (_req, res, next) => { res.set('Cache-Control', 'no-store'); next(); }, lanRoutes(state));
  app.use('/sub', (req, res, next) => {
    if (!['GET', 'HEAD'].includes(req.method)) res.set('Allow', 'GET, HEAD');
    next(new APIError(['GET', 'HEAD'].includes(req.method) ? 404 : 405, 'Subscription route unavailable'));
  });

  app.get('/assets/clients/:filename', (req, res, next) => {
    if (!/^[a-z0-9_-]+\.png$/i.test(req.params.filename)) return next(new APIError(404, 'Asset not found'));
    res.sendFile(req.params.filename, { root: clientIconsDir, maxAge: '1d' }, error => { if (error) next(error); });
  });
  app.get('/assets/tower-icon.png', (_req, res, next) => {
    res.sendFile(path.basename(towerIconPath), { root: path.dirname(towerIconPath), maxAge: '1d' }, error => { if (error) next(error); });
  });
  if (existsSync(path.join(staticDir, 'index.html'))) {
    app.use(express.static(staticDir, { index: false }));
    app.get('/{*path}', (req, res, next) => {
      if (path.extname(req.path) || req.path.startsWith('/assets/')) return next();
      res.sendFile('index.html', { root: staticDir }, error => { if (error) next(error); });
    });
  }
  app.use((_req, _res, next) => { next(new APIError(404, 'Route not found', 'NOT_FOUND')); });

  const handleError: ErrorRequestHandler = (error, _req, res, next) => {
    if (res.headersSent) { next(error); return; }
    if (error instanceof APIError) {
      res.status(error.status).json({ error: error.message, code: error.code, details: error.details });
    } else if (error instanceof ZodError) {
      res.status(400).json({ error: 'Invalid request', code: 'INVALID_INPUT', details: error.issues.map(({ path, code }) => ({ path, code })) });
    } else if (error?.status === 400 || error?.status === 404 || error?.status === 413) {
      const status = error.status as number;
      res.status(status).json({ error: status === 404 ? 'Not found' : status === 413 ? 'Request is too large' : 'Invalid request body' });
    } else {
      res.status(500).json({ error: 'Local server error', code: 'INTERNAL_ERROR' });
    }
  };
  app.use(handleError);
  return app;
}
