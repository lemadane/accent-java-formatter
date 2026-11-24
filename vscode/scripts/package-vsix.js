const { readFileSync } = require('fs');
const { join } = require('path');
const { spawnSync } = require('child_process');

const pkgPath = join(
  __dirname,
  '..',
  'package.json');
const pkg = JSON.parse(
  readFileSync(
    pkgPath,
    'utf8'));
const outFile = `accent-java-formatter-${pkg.version}.vsix`;

const result = spawnSync(
  'npx',
  ['vsce', 'package', '--no-dependencies', '--out', outFile],
  {
      cwd: join(
        __dirname,
        '..'),
      stdio: 'inherit',
      shell: process.platform === 'win32'
  });

if (result.status !== 0) {
    process.exit(result.status || 1);
}
