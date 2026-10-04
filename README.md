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
