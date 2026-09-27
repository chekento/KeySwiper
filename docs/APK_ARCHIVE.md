# APK archive policy

KeySwiper keeps every successful `main`-branch APK build addressable.

## Identity

A build is identified by:

- semantic `versionName`
- Android `versionCode`
- GitHub Actions run number
- Git commit SHA

This means two builds produced from the same app version still remain separately downloadable.

## Storage

Each successful build is stored twice:

1. as a normal GitHub Actions artifact for convenient CI inspection;
2. as a GitHub Release asset for long-term download.

The corresponding page in `docs/versions/` contains the permanent release link, SHA-256, commit metadata and the version's changelog.

## Version discipline

Feature or behavior changes should increment `versionCode` and normally advance `versionName`. Rebuilds that do not change the app version remain distinguishable by their CI run number.
