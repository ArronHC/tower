import type { AddressInfo } from 'node:net';
import { createApp } from './app.js';
import type { AppStateService } from './store/appStateService.js';
import type { DirectImportService } from './services/directImportService.js';

const host = process.env.HOST || '0.0.0.0';
const port = Number(process.env.PORT || 38420);
if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid PORT');
const app = createApp({ host, port, allowRemoteAPI: process.env.TOWER_ALLOW_REMOTE_API === '1' });
const state = app.locals.state as AppStateService;
const directImports = app.locals.directImports as DirectImportService;
const server = app.listen(port, host, () => {
  const actual = server.address() as AddressInfo;
  state.setListenPort(actual.port);
  console.log(`Tower Web listening on port ${actual.port}`);
});
function stop() {
  directImports.close();
  server.close();
}
process.once('SIGINT', stop);
process.once('SIGTERM', stop);
