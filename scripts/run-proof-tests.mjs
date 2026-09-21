import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const serial = process.argv[2];
const app = 'moe.frost.kochenite.debug';
const tests = `${app}.test`;
const result = { ok: false, stage: 'arguments', completedTests: 0 };
const directory = fileURLToPath(new URL('../.local/proof-evidence/', import.meta.url));
mkdirSync(directory, { recursive: true, mode: 0o700 });

function adb(args, timeout = 15_000) {
  const command = spawnSync('adb', ['-s', serial, ...args], {
    encoding: 'utf8', timeout, maxBuffer: 2 * 1024 * 1024,
  });
  if (command.error || command.status !== 0) throw new Error('ADB command failed or timed out');
  return command.stdout.replaceAll('\r\n', '\n').trim();
}

try {
  if (!serial || process.argv.length !== 3 || serial.startsWith('-') || /\s/.test(serial)) {
    throw new Error('Usage: node scripts/run-proof-tests.mjs EXPLICIT_DEVICE_SERIAL');
  }
  result.stage = 'device';
  if (adb(['get-state']) !== 'device') throw new Error('Device is not online');
  const user = adb(['shell', 'am', 'get-current-user']);
  if (!/^\d+$/.test(user)) throw new Error('Cannot identify current Android user');
  result.stage = 'packages';
  for (const name of [app, tests]) {
    const installed = adb(['shell', 'pm', 'list', 'packages', '--user', user, name]);
    if (!installed.split(/\r?\n/).includes(`package:${name}`)) {
      throw new Error('Expected proof package is not installed for the current user');
    }
  }
  result.stage = 'instrumentation';
  const output = adb([
    'shell', 'am', 'instrument', '--user', user, '-w', '-r',
    '-e', 'class', 'moe.frost.kochenite.proof.test.ComposeDeviceTest,moe.frost.kochenite.proof.test.EncryptedStorageDeviceTest',
    `${tests}/androidx.test.runner.AndroidJUnitRunner`,
  ], 120_000);
  const statuses = [...output.matchAll(/^INSTRUMENTATION_STATUS_CODE: (-?\d+)$/gm)]
    .map(match => Number(match[1]));
  result.completedTests = statuses.filter(code => code === 0).length;
  const reported = output.match(/OK \((\d+) tests?\)/);
  result.ok = /^INSTRUMENTATION_CODE: -1$/m.test(output)
    && reported !== null
    && Number(reported[1]) === result.completedTests
    && result.completedTests === 2
    && statuses.every(code => code === 0 || code === 1);
  if (!result.ok) throw new Error('Instrumentation did not complete all tests successfully');
  result.stage = 'complete';
} catch (error) {
  result.error = error.message;
}

// Only a sanitized summary is persisted; no device logs or assertion values.
writeFileSync(`${directory}instrumentation.json`, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });
console.log(JSON.stringify(result));
process.exitCode = result.ok ? 0 : 1;
