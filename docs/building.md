# Build from source

CI builds every APK you download from Releases. You only need a local build when you change
code and want to see it on a phone, or when you want lint feedback before you push.

## Requirements

- **JDK 19 or newer.** The build targets Java 19 (`build.gradle`, `sourceCompatibility`).
  JDK 21 works.
- **Android SDK** with platform 34 and the matching build-tools. `compileSdk 34` and
  `targetSdkVersion 34`.
- **Git**, with the `speedviewlib` submodule.

The wrapper pins Gradle 8.2, so you do not install Gradle yourself.

## Trap 1: the submodule is empty

`settings.gradle` includes `:speedviewlib` at `lib/speedviewlib/speedviewlib`. The directory
is empty in a fresh clone, so the build fails with a project-path error until you fetch it.

`.gitmodules` declares an SSH URL (`git@github.com:agronick/SpeedView.git`). Without a
GitHub SSH key the fetch fails. Use HTTPS for this submodule:

```
git config submodule.lib/speedviewlib.url https://github.com/agronick/SpeedView.git
git submodule update --init --recursive
```

Verify the directory is populated:

```
ls lib/speedviewlib/speedviewlib/build.gradle
```

## Trap 2: the SDK is not found

The build needs `sdk.dir` in `local.properties` or `ANDROID_HOME` in the environment.
`local.properties` is git-ignored, so create it per machine:

```
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

CI works around this by appending an empty line to `local.properties`, because the Gradle
Android plugin only needs the file to exist on a runner that has the SDK preinstalled.

Typical error when this is missing:

```
SDK location not found. Define a valid SDK location with an ANDROID_HOME
environment variable or by setting the sdk.dir path in your project's local
properties file.
```

## Build and check

```
./gradlew assembleDebug        # debug APK, SIMULATE_METRICS=true
./gradlew assembleRelease      # release APK, signed with the debug key
./gradlew lintRelease          # what CI runs
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

Release builds use `signingConfig signingConfigs.debug` (`app/build.gradle`), so a local
release APK installs over a CI build and over upstream without a signature mismatch.

## Install on a phone

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

This installs the app, but it will **not** appear in Android Auto: the install record is
wrong. Repair it as described in [aa-install-gate.md](aa-install-gate.md).

## Debug builds simulate data

`debug` and `release_debug` set `SIMULATE_METRICS=true` (`app/build.gradle`), so a debug
build shows plausible values without Torque or an adapter. Do not use a debug build to judge
whether the Torque binding works; use a release build and a real adapter.

## `dist/`

`dist/` is git-ignored. A downloaded CI artifact usually lands there. It is scratch space,
not a source of truth.

## Legacy bench files

`dhu.bat` and `small_head_unit.ini` at the repository root are an Android Auto
desktop-head-unit bench from earlier work (`adb forward tcp:5277` plus
`desktop-head-unit -c ./small_head_unit.ini`, 800x480). They are not part of the current
workflow and are scheduled for removal.

## Without a local SDK

If you cannot install an SDK, work through CI instead: push a branch, let the `lint` job run
`./gradlew lintRelease`, and let CI build the APK for the release. That is the supported path
for an agent on a machine without an Android SDK.
