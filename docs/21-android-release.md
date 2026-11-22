# HeaRt Android release and saved login

Version 1.1 (`versionCode=2`) uses the hosted HTTPS API at `https://heart-rate-monitor-q6mx.onrender.com`. Release builds disable debug-only preview screens and loopback HTTP configuration. The package remains `com.ambali.heart`.

## Saved session design

- On successful sign-in, persist only the API origin and session cookie. Never save the password.
- Encrypt the payload with AES-256-GCM. Android Keystore holds the non-exportable key; each write gets a random IV and an authentication tag.
- Store the encrypted file using `AtomicFile` in the app's `noBackupFilesDir`, outside Android backups. A damaged file or unavailable key produces no authenticated account.
- On process start, reject expired or origin-mismatched cookies, then request `/api/auth/me`. Restore the account and CSRF token only after the server validates the cookie.
- Startup validation allows up to 90 seconds for a free Render cold start. A network failure retains the encrypted cookie for a later launch; it does not extend the server's session lifetime or manufacture an authenticated identity.
- Sign-out clears the encrypted file, cookies and local account even when the network request fails. A 401 from authenticated requests also clears the saved session.

Backend account sessions still expire after 12 hours. Saving the cookie does not create an indefinite login or refresh-token system. Active recording upload tokens remain memory-only; force-stop is not an automatic recording-resumption mechanism.

## Signing and data-preserving upgrade

The first installed APK used this machine's Android debug certificate. A separate RSA release key was generated in ignored `.codex/release/heart-release.p12`; its password is in `.codex/release/keystore-password.txt`. The public signing-certificate lineage is `.codex/release/heart-signing-lineage.bin`.

APK Signature Scheme v3 key rotation links the original signing certificate to the release certificate. On the connected Android 15 phone, this permits an in-place upgrade with the same package/UID and retained private storage. The APK includes the original signer for older-platform signature compatibility and uses the rotated release signer on Android 9+. Do not replace or delete either key/lineage without a planned signing migration. Back them up privately before deleting the workspace; none belongs in Git.

This is a signed release for direct installation. Store publication and Play App Signing were not performed.

## Build and sign on this machine

Run builds sequentially to limit memory use:

```powershell
cd companion
.\gradlew.bat :app:testDebugUnitTest --offline --no-configuration-cache --max-workers=1
.\gradlew.bat :app:assembleRelease --offline --no-configuration-cache --max-workers=1
cd ..
```

The initial release needed one additional lint dependency; after it was downloaded, the release could build offline. Existing vendor SDK metadata warnings are retained in the build log; they were not hidden by disabling release lint.

From the repository root, sign with the already-created local key and lineage:

```powershell
$taskSigner = Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/36.1.0/apksigner.bat'
& $taskSigner sign `
  --ks "$env:USERPROFILE/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android `
  --next-signer --ks .codex/release/heart-release.p12 --ks-key-alias heart-release `
  --ks-pass file:.codex/release/keystore-password.txt `
  --lineage .codex/release/heart-signing-lineage.bin --rotation-min-sdk-version 28 `
  --out companion/app/build/outputs/apk/release/HeaRt-1.1-release.apk `
  companion/app/build/outputs/apk/release/app-release-unsigned.apk

& $taskSigner verify --verbose --print-certs --min-sdk-version 35 --max-sdk-version 35 `
  companion/app/build/outputs/apk/release/HeaRt-1.1-release.apk
```

The `android` password shown above is the standard password for the existing debug key, not the private release-key password. The release password is read from its ignored file.

Install as an update, without uninstalling or clearing storage:

```powershell
adb -s R94Y100CR2E install -r companion/app/build/outputs/apk/release/HeaRt-1.1-release.apk
```

## Verification

All 18 automated tests passed: five CSV/standard-heart-rate decoding tests, six monitor tests, four encryption tests and three cookie-restoration tests. They cover authenticated encryption, changed ciphertext/keys, truncated payloads, expiry, HTTPS and origin matching.

The optimized release build and release lint passed. APK Signature Scheme v3 verification on API 35 identified the new HeaRt RSA certificate. The APK installed over the existing package without uninstalling it; Android reported the release as non-debuggable. The saved account restored after the signing upgrade and after a separate force-stop/relaunch. The encrypted file was inspected before the upgrade and contained no plaintext credentials or cookie marker.

The subsequent History assertion expected an empty list but did not match the UI; it is recorded as inconclusive rather than a passed test or diagnosed application defect. The script stopped there, so the release sign-out/relaunch check was not executed. The student then requested that work stop and the changes be committed/pushed. No watch-acquisition or app-store acceptance result is claimed.
