import { randomBytes } from 'node:crypto';
import { chmodSync, existsSync, mkdirSync, renameSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

const directory = fileURLToPath(new URL('../.local/proof-signing/', import.meta.url));
const keystore = join(directory, 'temporary-proof.p12');
const properties = join(directory, 'keystore.properties');
if (existsSync(directory)) {
  throw new Error('Proof signing directory already exists; refusing to replace signing material.');
}
mkdirSync(directory, { recursive: true, mode: 0o700 });
chmodSync(directory, 0o700);
const password = randomBytes(32).toString('hex');
const result = spawnSync('keytool', [
  '-genkeypair', '-keystore', keystore, '-storetype', 'PKCS12',
  '-storepass:env', 'KOCHENITE_PROOF_PASSWORD',
  '-keypass:env', 'KOCHENITE_PROOF_PASSWORD',
  '-alias', 'toolchain-proof', '-keyalg', 'RSA', '-keysize', '3072',
  '-validity', '30', '-dname', 'CN=Kochenite Temporary Toolchain Proof', '-noprompt',
], {
  env: { ...process.env, KOCHENITE_PROOF_PASSWORD: password },
  stdio: ['ignore', 'pipe', 'pipe'], timeout: 60_000,
});
if (result.error || result.status !== 0) {
  throw new Error('Temporary proof key generation failed; inspect the local directory before retrying.');
}
chmodSync(keystore, 0o600);
writeFileSync(`${properties}.tmp`, [
  `storeFile=${keystore}`,
  `storePassword=${password}`,
  'keyAlias=toolchain-proof',
  `keyPassword=${password}`,
  '',
].join('\n'), { mode: 0o600, flag: 'wx' });
renameSync(`${properties}.tmp`, properties);
console.log('Temporary proof signing material created in .local/proof-signing (not a release identity).');
