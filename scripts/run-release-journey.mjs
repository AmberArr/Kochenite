import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const serial = process.argv[2];
const [upgradeFlag, upgradeApk, sdk] = process.argv.slice(3);
const app = 'moe.frost.kochenite';
const result = { ok: false, stage: 'arguments' };
function command(binary, args, timeout = 20_000) {
  const process = spawnSync(binary, args, { encoding: 'utf8', timeout, maxBuffer: 2 * 1024 * 1024 });
  if (process.error || process.status !== 0) throw new Error(`${binary} failed or timed out`);
  return process.stdout.trim();
}
function adb(...args) { return command('adb', ['-s', serial, ...args]); }
function layout() {
  const activity = adb('shell', 'dumpsys', 'activity', 'activities');
  const resumed = activity.split('\n').filter(line => /(?:topResumedActivity|ResumedActivity):?=?.*ActivityRecord/.test(line));
  if (!resumed.some(line => line.includes(`${app}/`))) throw new Error('Release app is not foreground');
  const output = command('android', ['--no-metrics', 'layout', '--device', serial, '--full'], 30_000);
  // The layout server can return an empty response while reconnecting after instrumentation.
  if (!output) return [];
  const roots = JSON.parse(output);
  const nodes = [];
  function visit(node) { nodes.push(node); (node.children || []).forEach(visit); }
  roots.forEach(visit);
  return nodes;
}
function count(nodes) {
  const node = nodes.find(node => /^Records: \d+$/.test(node.text || ''));
  return node ? Number(node.text.slice(9)) : null;
}
function waitForCount(expected) {
  for (let attempt = 0; attempt < 8; attempt++) {
    const nodes = layout();
    const actual = count(nodes);
    if (actual !== null && (expected === undefined || actual === expected)) return { nodes, actual };
    if (nodes.some(node => /Storage (temporarily unavailable|needs recovery)/.test(node.text || ''))) {
      throw new Error('Release storage failed');
    }
  }
  throw new Error('Release UI did not reach the expected record count');
}
try {
  if (!serial || serial.startsWith('-') || /\s/.test(serial) ||
      !(process.argv.length === 3 || (process.argv.length === 6 && upgradeFlag === '--upgrade'))) {
    throw new Error('Usage: node scripts/run-release-journey.mjs SERIAL [--upgrade APK SDK_DIRECTORY]');
  }
  result.stage = 'device';
  if (adb('get-state') !== 'device') throw new Error('Device is not online');
  const user = adb('shell', 'am', 'get-current-user');
  if (!/^\d+$/.test(user)) throw new Error('Invalid Android user');
  if (!adb('shell', 'pm', 'list', 'packages', '--user', user, app).split('\n').includes(`package:${app}`)) {
    throw new Error('Release app is not installed');
  }
  result.stage = 'launch';
  const launch = () => adb('shell', 'am', 'start', '--user', user, '-W', '-n', `${app}/moe.frost.kochenite.MainActivity`);
  launch();
  const { nodes, actual: before } = waitForCount();
  const button = nodes.find(node => node.text === 'Add sample');
  const coordinates = button?.center?.match(/^\[(\d+),(\d+)\]$/);
  if (!coordinates) throw new Error('Add sample control not found');
  result.stage = 'insert';
  adb('shell', 'input', 'tap', coordinates[1], coordinates[2]);
  waitForCount(before + 1);
  result.stage = 'restart';
  adb('shell', 'am', 'force-stop', '--user', user, app);
  launch();
  const after = waitForCount(before + 1);
  if (!after.nodes.some(node => (node.text || '').includes('12,345'))) throw new Error('Expected synthetic amount missing');
  if (upgradeApk) {
    result.stage = 'upgrade-validation';
    command('node', ['scripts/check-apk.mjs', upgradeApk, sdk, 'release'], 60_000);
    const installedVersion = Number(adb('shell', 'dumpsys', 'package', app).match(/versionCode=(\d+)/)?.[1]);
    const badging = command(join(resolve(sdk), 'build-tools/37.0.0/aapt2'), ['dump', 'badging', resolve(upgradeApk)]);
    const nextVersion = Number(badging.match(/versionCode='(\d+)'/)?.[1]);
    if (!(nextVersion > installedVersion)) throw new Error('Upgrade must increase version code');
    const visibleRecords = nodes => nodes.map(node => node.text || '').filter(Boolean);
    const snapshot = JSON.stringify(visibleRecords(after.nodes));
    result.stage = 'upgrade';
    command('adb', ['-s', serial, 'install', '--user', user, '-r', resolve(upgradeApk)], 60_000);
    launch();
    const upgraded = waitForCount(before + 1);
    if (JSON.stringify(visibleRecords(upgraded.nodes)) !== snapshot) throw new Error('Visible release records changed after upgrade');
    adb('shell', 'am', 'force-stop', '--user', user, app);
    launch();
    waitForCount(before + 1);
    result.upgrade = { from: installedVersion, to: nextVersion, retainedCount: true, retainedVisibleRecords: true };
  }
  result.stage = 'capture';
  layout();
  const capture = spawnSync('adb', ['-s', serial, 'exec-out', 'screencap', '-p'],
    { timeout: 15_000, maxBuffer: 16 * 1024 * 1024 });
  if (capture.error || capture.status !== 0) throw new Error('Release screenshot failed');
  mkdirSync('.local/foundation-evidence', { recursive: true, mode: 0o700 });
  writeFileSync('.local/foundation-evidence/release.png', capture.stdout, { mode: 0o600 });
  result.ok = true;
  result.stage = 'complete';
} catch (error) {
  result.error = error.message;
}
mkdirSync('.local/foundation-evidence', { recursive: true });
writeFileSync(`.local/foundation-evidence/${upgradeApk ? 'release-upgrade' : 'release-journey'}.json`, `${JSON.stringify(result, null, 2)}\n`);
console.log(JSON.stringify(result));
process.exitCode = result.ok ? 0 : 1;
