# SISE Tracker

Android app that makes the federal judiciary's public SISE portal ("Acuerdos
por expediente") easier to use: look up a case, save it, read its acuerdos and
get notified when new ones are published. It only uses the public portal.

Project rules, the portal protocol and the design live in
[`CLAUDE.md`](CLAUDE.md); the roadmap is in
[`docs/MILESTONES.md`](docs/MILESTONES.md).

## Building

Requirements: JDK 17 or newer and the Android SDK (`ANDROID_HOME`, or
`sdk.dir` in `local.properties`). The build targets Java 17.

```sh
./gradlew :sise-core:test                 # fast parser tests (plain JVM)
./gradlew testDebugUnitTest assembleDebug # what CI runs, plus the line above
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Modules

- `:sise-core`: Kotlin/JVM library with the models, URL builders and HTML
  parsers. No Android dependencies, so its tests run on the plain JVM against
  the saved portal responses in `sise-core/src/test/resources/fixtures/`.
- `:app`: the Android app (Jetpack Compose, Material 3, minSdk 26).

## CI

The GitHub App that works on this repo can't push workflow files, so the
proposed workflows live in [`docs/workflows/`](docs/workflows/). Copy them to
`.github/workflows/` to enable them.

## Releases

Distribution is private (see `CLAUDE.md`): a tag builds a signed APK and
attaches it to a GitHub Release of this private repository. Never publish it
anywhere else.

### One-time setup

1. Create the signing keystore and keep it, with its passwords, somewhere
   safe outside the repo. Losing it means installed copies can't be updated.

   ```sh
   keytool -genkeypair -v -keystore sise-release.jks -alias sise \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Add these repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `SISE_KEYSTORE_BASE64` | `base64 -w0 sise-release.jks` (on macOS: `base64 -i sise-release.jks`) |
   | `SISE_KEYSTORE_PASSWORD` | the keystore password |
   | `SISE_KEY_ALIAS` | `sise` (or the alias you chose) |
   | `SISE_KEY_PASSWORD` | the key password |

3. Copy `docs/workflows/release.yml` to `.github/workflows/`.

### Making a release

```sh
git tag v0.2.0
git push origin v0.2.0
```

The workflow runs the tests, builds `assembleRelease` with `versionName`
from the tag and `versionCode` from the run number (so each release installs
over the previous one), and attaches `sise-tracker-v0.2.0.apk` to the
release. Team members download it from the Releases page and allow installs
from that source when Android asks.

### Signing locally

The release build reads the same variables. Without them, `assembleRelease`
produces an unsigned APK.

```sh
SISE_KEYSTORE_PATH=/path/to/sise-release.jks SISE_KEYSTORE_PASSWORD=… \
SISE_KEY_ALIAS=sise SISE_KEY_PASSWORD=… ./gradlew assembleRelease
```
