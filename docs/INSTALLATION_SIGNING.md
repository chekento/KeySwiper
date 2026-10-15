# KeySwiper: installation, APK signing and safe updates

## Root cause

Older GitHub Releases (including alpha25/build25/run68) uploaded `app-debug.apk` under the name `KeySwiper-latest.apk`. Each temporary GitHub Actions runner generates its own Android debug signing key. **Android cannot upgrade the same application ID when the signing certificate differs.** An otherwise successful build does not establish upgrade compatibility.

The release workflow now only publishes the release APK if four protected GitHub repository secrets exist. There is no fallback to a debug-signed published APK. Signing identity is pinned to the public SHA-256 fingerprint in `docs/RELEASE_SIGNER_SHA256.txt`.

### Four GitHub Actions repository secrets (never commit any private key or password)

In the KeySwiper repository, open **Settings → Secrets and variables → Actions → New repository secret**. Create each value (from the owner's separately protected signing bundle):

| Secret | Value |
| --- | --- |
| `KEYSWIPER_RELEASE_KEYSTORE_B64` | Base64 of the private PKCS12/JKS keystore (a single line) |
| `KEYSWIPER_RELEASE_STORE_PASSWORD` | Keystore password |
| `KEYSWIPER_RELEASE_KEY_ALIAS` | `keyswiper-release` |
| `KEYSWIPER_RELEASE_KEY_PASSWORD` | Private key password |

The certificate's *public* SHA-256 identity must equal `18a953f051a20e2324fced2fe30fce100ab9ffe540fff09e3a297993e82f66bb`. Keep the JKS and passwords securely backed up **outside the public repository**. The keystore must never be added to a commit, artifact, release, screenshot, GitHub issue or chat.

Then open **Actions → Android CI → Run workflow → main**. Only after the release-signature check succeeds will a production APK be published. The workflow checks the pinned certificate on every release.

### Already installed older alpha/debug APKs

Updating a debug-signed older APK with the new private release signature will **not** work, even after fixing CI. Do **not** clear app data or uninstall until you have copied any important saved snippets, pins, user dictionaries or configuration out to a safe place. The encrypted clipboard store uses Android Keystore and no-backup private storage; uninstall can irreversibly remove it.

A safe, data-preserving installation test is available as an additional Actions artifact named `KeySwiper-INSTALL-CHECK-<run number>`. It installs the **separate** package `cloud.kosch.keyswiper.installcheck` beside the existing application, so the existing app can remain installed. It does **not** migrate the previous app's private data. This temporary testing build is independently debug-signed and is NOT meant as a stable update channel. To try it, download the CI artifact ZIP, extract the APK, install it, and select KeySwiper (Installationstest) in Android's keyboard settings.

When ready to move from an old debug signature to the permanently signed production package, archive important data first, uninstall only the older production-package version, and install the new signed release. The old application's encrypted data cannot be restored by simply reinstalling.

### Error messages

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or "App nicht installiert": often mismatched certificates for the already installed app.
- `INSTALL_FAILED_VERSION_DOWNGRADE`: a version code older than the installed one.
- `INSTALL_FAILED_INSUFFICIENT_STORAGE`: low free device space; leave room for APK extraction and app data.
- `INSTALL_FAILED_NO_MATCHING_ABIS`: missing native library for the device architecture.
- "Installation aus unbekannten Quellen gesperrt": check Android's per-source **Install unknown apps** setting. On Samsung Galaxy devices, **Auto Blocker** can also block sideloaded APKs. Only install trusted files and re-enable protection after a temporary exception.

The APK compatibility diagnostic in CI verifies that a parallel package can install beside the original test IME. This tests installation on an emulator, not every Samsung firmware or on-device speech model.
