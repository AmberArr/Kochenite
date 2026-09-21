# Release Signing and Recovery

Release application ID: `moe.frost.kochenite`. Debug uses
`moe.frost.kochenite.debug` and the standard development certificate.
The permanent RSA-3072/PKCS12 identity was created for Phase 1, with a 10,000-day
certificate. Its public SHA-256 fingerprint is in [release-certificate.sha256](release-certificate.sha256).

Private material is ignored under `.local/signing`: `kochenite-release.p12` and
`keystore.properties`. Directory permissions are 700; files are 600. The random
keystore password is stored only in that local properties file. Do not print it,
pass it as a shell argument, commit it or include it in build logs. The key's
password does not protect its sibling plaintext credentials from host access;
encrypt the entire backup bundle with a separate passphrase.

`node scripts/create-release-key.mjs` is for initial creation only. It refuses if
either local signing material or the recorded public fingerprint exists. A fresh
checkout must restore the original identity. Release builds fail if signing is
missing; never delete the fingerprint to generate a replacement.

## Encrypted Backup

Backup status: **not completed off-machine**. The user must choose the destination
and keep the backup passphrase independently, before real bookkeeping use.
This does not block synthetic foundation testing.

1. On a trusted machine with GnuPG installed, create an encrypted bundle using
   an interactive passphrase prompt. Include both key and credentials. Choose a
   new backup path to avoid overwriting a previous backup:

   ```sh
   umask 077
   tar -C .local -cf - signing | gpg --symmetric --cipher-algo AES256 --output .local/kochenite-signing-backup.tar.gpg
   gpg --decrypt .local/kochenite-signing-backup.tar.gpg | tar -tf -
   ```

2. Transfer only the encrypted bundle to the user-selected off-machine location.
   Keep its independent passphrase in a password manager or another secure place.
3. Restore into a private temporary directory on a trusted machine, verify the
   public fingerprint, and remove the temporary plaintext copy after testing.
   Record backup date, destination description and successful recovery check in
   a private inventory, not in repository logs. Record only completion status
   in the current phase worklog and update the plan's remaining prerequisite.

## Recovery

1. Do not create a new key. Retrieve and decrypt the backup into a private
   temporary directory. Inspect that it contains only the expected signing files.
2. Restore `.local/signing`, retaining the PKCS12 bytes and passwords. Adjust only
   `storeFile` in the local properties file for the new machine's absolute path.
   Restore directory mode 700 and file modes 600.
3. Run `keytool -list -v -keystore .local/signing/kochenite-release.p12 -alias kochenite-release`
   using its interactive password prompt; compare its SHA-256 fingerprint with
   the checked-in public value (ignoring colon separators and case).
4. Build the release with a higher `-PreleaseVersionCode`, run
   `scripts/check-apk.mjs`, and use the upgrade journey documented in
   [gradle-build.md](gradle-build.md). Never uninstall or clear app data to bypass
   a signature mismatch. Restore the matching original key instead.

The original temporary Toolchain proof certificate is not this identity and
cannot sign updates to the real release. Losing the permanent identity prevents
normal in-place upgrades; its backup is independent of future database backups.
