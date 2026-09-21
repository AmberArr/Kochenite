import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { completedTests } from './instrumentation-result.mjs';

const serial = process.argv[2];
const app = 'moe.frost.kochenite.debug';
const tests = `${app}.test`;
const storageTests = `${app}.storage.test`;
const result = { ok: false, stage: 'arguments', completedTests: 0 };
const directory = fileURLToPath(new URL('../.local/foundation-evidence/', import.meta.url));
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
    throw new Error('Usage: node scripts/run-device-tests.mjs EXPLICIT_DEVICE_SERIAL');
  }
  result.stage = 'device';
  if (adb(['get-state']) !== 'device') throw new Error('Device is not online');
  const user = adb(['shell', 'am', 'get-current-user']);
  if (!/^\d+$/.test(user)) throw new Error('Cannot identify current Android user');
  result.stage = 'packages';
  for (const name of [app, tests, storageTests]) {
    const installed = adb(['shell', 'pm', 'list', 'packages', '--user', user, name]);
    if (!installed.split(/\r?\n/).includes(`package:${name}`)) {
      throw new Error('Expected foundation package is not installed for the current user');
    }
  }
  const registered = adb(['shell', 'pm', 'list', 'instrumentation']);
  for (const [testPackage, target] of [[tests, app], [storageTests, storageTests]]) {
    if (!registered.split('\n').includes(`instrumentation:${testPackage}/androidx.test.runner.AndroidJUnitRunner (target=${target})`)) {
      throw new Error('Unexpected instrumentation target');
    }
  }
  function instrument(testPackage, testClass, expected) {
    const output = adb([
      'shell', 'am', 'instrument', '--user', user, '-w', '-r',
      '-e', 'class', testClass,
      `${testPackage}/androidx.test.runner.AndroidJUnitRunner`,
    ], 120_000);
    result.completedTests += completedTests(output, expected);
  }
  result.stage = 'storage';
  instrument(storageTests, 'moe.frost.kochenite.persistence.StorageDeviceTest', 7);
  result.stage = 'add-recreate';
  instrument(tests, 'moe.frost.kochenite.FoundationDeviceTest#addRecreateAndCheckpoint', 1);
  result.stage = 'process-restart';
  adb(['shell', 'am', 'force-stop', '--user', user, app]);
  instrument(tests, 'moe.frost.kochenite.FoundationDeviceTest#verifyCheckpointAfterProcessRestart', 1);
  result.stage = 'presentation';
  instrument(tests, 'moe.frost.kochenite.FoundationPresentationTest', 2);
  for (const label of ['light', 'dark', 'large-text']) {
    const capture = spawnSync('adb', ['-s', serial, 'exec-out', 'run-as', app, 'cat', `cache/test-evidence/${label}.png`],
      { timeout: 15_000, maxBuffer: 16 * 1024 * 1024 });
    if (capture.error || capture.status !== 0) throw new Error('Cannot retrieve app-only screenshot');
    writeFileSync(`${directory}${label}.png`, capture.stdout, { mode: 0o600 });
  }
  result.ok = true;
  result.stage = 'complete';
} catch (error) {
  result.error = error.message;
}

// Only a sanitized summary is persisted; no device logs or assertion values.
writeFileSync(`${directory}instrumentation.json`, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });
console.log(JSON.stringify(result));
process.exitCode = result.ok ? 0 : 1;
