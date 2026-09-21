import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';

const [apkArg, sdkArg, mode] = process.argv.slice(2);
if (!apkArg || !sdkArg || !['debug', 'release'].includes(mode) || process.argv.length !== 5) {
  throw new Error('Usage: node scripts/check-apk.mjs APK SDK_DIRECTORY debug|release');
}
const apk = resolve(apkArg);
const buildTools = join(resolve(sdkArg), 'build-tools', '37.0.0');
function run(command, args, binary = false) {
  const result = spawnSync(command, args, {
    encoding: binary ? undefined : 'utf8', timeout: 60_000, maxBuffer: 64 * 1024 * 1024,
  });
  if (result.error || result.status !== 0) throw new Error(`${command} failed`);
  return result.stdout;
}
const expected = mode === 'debug' ? 'moe.frost.kochenite.debug' : 'moe.frost.kochenite';
const badging = run(join(buildTools, 'aapt2'), ['dump', 'badging', apk]);
if (!badging.includes(`package: name='${expected}'`)) throw new Error('Unexpected application ID');
if (badging.includes('application-debuggable') !== (mode === 'debug')) throw new Error('Incorrect debuggable state');
if (!badging.includes("minSdkVersion:'26'") || !badging.includes("targetSdkVersion:'36'")) {
  throw new Error('Incorrect SDK configuration');
}
if (/uses-permission: name='android\.permission\./.test(badging)) throw new Error('Unexpected platform permission');
const signature = run(join(buildTools, 'apksigner'), ['verify', '--verbose', '--print-certs', apk]);
if (mode === 'release' && !signature.includes(`certificate SHA-256 digest: ${readFileSync('docs/release-certificate.sha256', 'utf8').trim()}`)) {
  throw new Error('Release signing identity does not match recorded certificate');
}
run(join(buildTools, 'zipalign'), ['-c', '-P', '16', '4', apk]);
const entries = run('unzip', ['-Z1', apk]).trim().split('\n');
const native = entries.filter(entry => /^lib\/[^/]+\/[^/]+\.so$/.test(entry));
if (native.length !== 4 || native.some(entry => !entry.endsWith('/libandroidx.graphics.path.so'))) {
  throw new Error('Unexpected native library set; review before changing the expectation');
}
mkdirSync('.local/foundation-evidence', { recursive: true });
const extracted = mkdtempSync('.local/foundation-evidence/apk-native-');
try {
  for (const entry of native) {
    const file = join(extracted, entry.replaceAll('/', '_'));
    writeFileSync(file, run('unzip', ['-p', apk, entry], true));
    const loads = run('readelf', ['-lW', file]).split('\n').filter(line => /^\s*LOAD\s/.test(line));
    if (loads.length === 0) throw new Error('No ELF load segments');
    for (const line of loads) {
      const fields = line.trim().split(/\s+/);
      const offset = BigInt(fields[1]);
      const address = BigInt(fields[2]);
      const alignment = BigInt(fields.at(-1));
      if (alignment < 16384n || offset % 16384n !== address % 16384n) {
        throw new Error(`16 KB ELF alignment failed: ${entry}`);
      }
    }
  }
} finally {
  rmSync(extracted, { recursive: true, force: true });
}
const dexFiles = entries.filter(entry => /^classes\d*\.dex$/.test(entry));
const marker = Buffer.from('kochenite-debug-only-marker');
const hasDebugCode = dexFiles.some(entry => run('unzip', ['-p', apk, entry], true).includes(marker));
const resources = run(join(buildTools, 'aapt2'), ['dump', 'resources', apk]);
const hasDebugResource = resources.includes('debug_only_marker');
if (hasDebugCode !== (mode === 'debug') || hasDebugResource !== (mode === 'debug')) {
  throw new Error('Debug source/resource isolation failed');
}
const summary = { ok: true, package: expected, debuggable: mode === 'debug', nativeLibraries: native.length,
  zipAlignment16Kb: true, elfAlignment16Kb: true, signature: true, debugIsolation: true,
  runtime16Kb: 'unverified' };
writeFileSync(`.local/foundation-evidence/${mode}-apk.json`, `${JSON.stringify(summary, null, 2)}\n`);
console.log(JSON.stringify(summary));
