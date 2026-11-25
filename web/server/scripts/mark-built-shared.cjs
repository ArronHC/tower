const { writeFileSync } = require('node:fs');
const path = require('node:path');

// NodeNext emits the shared sources as CommonJS because their source package
// has no "type" setting. Keep the same boundary under the server's ESM dist.
writeFileSync(path.join(__dirname, '../dist/shared/package.json'), '{"type":"commonjs"}\n');
