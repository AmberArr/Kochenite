import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { completedTests } from './instrumentation-result.mjs';

const serial = process.argv[2];
const ui = process.argv[3] === '--ui';
const testPackage = 'moe.frost.kochenite.debug.storage.test';
const runner = `${testPackage}/androidx.test.runner.AndroidJUnitRunner`;
const directory = fileURLToPath(new URL('../.local/financial-evidence/', import.meta.url));
const result = { ok: false, stage: 'arguments', completedTests: 0 };

function adb(args, timeout = 15_000) {
  const command = spawnSync('adb', ['-s', serial, ...args], {
    encoding: 'utf8', timeout, maxBuffer: 2 * 1024 * 1024,
  });
  if (command.error || command.status !== 0) throw new Error('ADB command failed or timed out');
  return command.stdout.replaceAll('\r\n', '\n').trim();
}

try {
  if (!serial || (process.argv.length !== 3 && !(process.argv.length === 4 && ui)) || serial.startsWith('-') || /\s/.test(serial)) {
    throw new Error('Usage: node scripts/run-financial-tests.mjs EXPLICIT_DEVICE_SERIAL [--ui]');
  }
  result.stage = 'device';
  if (adb(['get-state']) !== 'device') throw new Error('Device is not online');
  const user = adb(['shell', 'am', 'get-current-user']);
  if (!/^\d+$/.test(user)) throw new Error('Cannot identify current Android user');
  result.stage = 'package';
  if (!adb(['shell', 'pm', 'list', 'packages', '--user', user, testPackage])
    .split('\n').includes(`package:${testPackage}`)) throw new Error('Financial test package is not installed');
  if (!adb(['shell', 'pm', 'list', 'instrumentation']).split('\n')
    .includes(`instrumentation:${runner} (target=${testPackage})`)) throw new Error('Unexpected instrumentation target');
  result.stage = 'financial-storage';
  const output = adb(['shell', 'am', 'instrument', '--user', user, '-w', '-r', '-e', 'class',
    'moe.frost.kochenite.persistence.ledger.FinancialStorageDeviceTest', runner], 120_000);
  result.completedTests = completedTests(output, 21);
  result.stage = 'book-setup';
  const setupOutput = adb(['shell', 'am', 'instrument', '--user', user, '-w', '-r', '-e', 'class',
    'moe.frost.kochenite.persistence.ledger.BookSetupDeviceTest', runner], 120_000);
  result.completedTests += completedTests(setupOutput, 5);
  if (ui) {
    result.stage = 'book-ui';
    const app = 'moe.frost.kochenite.debug';
    const uiRunner = `${app}.test/androidx.test.runner.AndroidJUnitRunner`;
    if (!adb(['shell', 'pm', 'list', 'instrumentation']).split('\n')
      .includes(`instrumentation:${uiRunner} (target=${app})`)) throw new Error('Unexpected UI instrumentation target');
    adb(['shell', 'input', 'keyevent', 'KEYCODE_WAKEUP']);
    const uiOutput = adb(['shell', 'am', 'instrument', '--user', user, '-w', '-r', '-e', 'class',
      'moe.frost.kochenite.BookUiDeviceTest', uiRunner], 120_000);
    result.completedTests += completedTests(uiOutput, 8);
    result.stage = 'process-draft-create';
    const createOutput = adb(['shell', 'am', 'instrument', '--user', user, '-w', '-r', '-e', 'class',
      'moe.frost.kochenite.BookProcessDeviceTest#createAcknowledgedDraft', uiRunner], 120_000);
    result.completedTests += completedTests(createOutput, 1);
    result.stage = 'process-restart';
    adb(['shell', 'am', 'force-stop', app]);
    result.stage = 'process-draft-verify';
    const verifyOutput = adb(['shell', 'am', 'instrument', '--user', user, '-w', '-r', '-e', 'class',
      'moe.frost.kochenite.BookProcessDeviceTest#verifyAfterProcessRestart', uiRunner], 120_000);
    result.completedTests += completedTests(verifyOutput, 1);
  }
  result.stage = 'complete';
  result.ok = true;
} catch (error) {
  result.error = error.message;
}
mkdirSync(directory, { recursive: true, mode: 0o700 });
const filename = result.ok ? 'instrumentation.json' : 'instrumentation-failure.json';
writeFileSync(`${directory}${filename}`, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });
console.log(JSON.stringify(result));
process.exitCode = result.ok ? 0 : 1;
