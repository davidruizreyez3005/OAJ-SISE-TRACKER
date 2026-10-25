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

Distribution is private (see `CLAUDE.md`): the release build is signed with
the team's key and attached to a GitHub Release of this private repository.
Never publish it anywhere else.

- **Release build** (`assembleRelease`): the team's app, `mx.sisetracker`,
  shrunk with R8 (about 2.5 MB). Its versionCode is the number of commits
  (`git rev-list --count HEAD`), so each release installs over the previous
  one and keeps the saved cases.
- **Debug build** (`assembleDebug`, also CI's artifact): a separate app,
  "SISE Tracker (prueba)" (`mx.sisetracker.debug`), with its own data. It's
  signed with the key of whatever machine built it, so it never replaces the
  real app.

### One-time setup

1. The signing keystore is `sise-release.jks`, alias `sise`, certificate
   SHA-256 `91:61:1C:4C:51:D1:43:83:E9:3D:62:B7:95:CD:96:F9:69:DC:72:8C:06:37:47:23:B0:0B:84:A0:45:35:4B:FE`.
   Keep it and its password somewhere safe outside the repo (a password
   manager). Losing it means installed copies can't be updated; anyone who
   has it can sign updates. Check an APK with
   `apksigner verify --print-certs sise-tracker-v0.2.0.apk`.
2. Add these repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `SISE_KEYSTORE_BASE64` | `base64 -w0 sise-release.jks` (on macOS: `base64 -i sise-release.jks`) |
   | `SISE_KEYSTORE_PASSWORD` | the keystore password |
   | `SISE_KEY_ALIAS` | `sise` |
   | `SISE_KEY_PASSWORD` | the key password (the same as the keystore's) |

3. Copy `docs/workflows/ci.yml` and `docs/workflows/release.yml` to
   `.github/workflows/`. On GitHub: Add file → Create new file, name it
   `.github/workflows/release.yml`, paste the contents, commit; the same for
   `ci.yml`.

### Making a release

From the browser: Actions → Release → Run workflow, choose the branch, type
the version (`0.3.0`). The workflow runs the tests, builds the signed APK,
creates the tag `v0.3.0` and a release with `sise-tracker-v0.3.0.apk`. Team
members download it from the Releases page and allow installs from that
source when Android asks. From a computer, pushing a tag does the same:

```sh
git tag v0.3.0
git push origin v0.3.0
```

Don't rewrite the history of the branch you release from: the versionCode
would go down, and Android refuses to install an older version over a newer
one.

### Moving from the first debug builds

Copies installed before 0.2.0 were debug builds signed with a cloud build
machine's key (SHA-256 `84:D2:6E:86:…:4E:9F`). 0.2.0 was signed with APK
Signature Scheme v3 key rotation from that key to the team key
(`apksigner sign --lineage`), so on Android 9 and newer it installs over them,
keeps every saved case, and from then on the copy only accepts the team key.
Later releases need nothing special. Android 8 can't rotate keys: 0.2.0
still installs over the old copy, but the next release won't, so there first
export the list (Ajustes → Mis expedientes → Exportar la lista), uninstall,
install the new release and import it (Ajustes → Importar una lista).

### Signing locally

The release build reads the same variables. Without them, `assembleRelease`
produces an unsigned APK.

```sh
SISE_KEYSTORE_PATH=/path/to/sise-release.jks SISE_KEYSTORE_PASSWORD=… \
SISE_KEY_ALIAS=sise SISE_KEY_PASSWORD=… ./gradlew assembleRelease
```
