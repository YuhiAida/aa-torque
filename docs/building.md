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

Release builds use `signingConfig signingConfigs.debug` (`app/build.gradle`), so a local release
APK is signed with *your* `~/.android/debug.keystore`. That is not the key CI uses, so it will
not install over a release from Releases: expect `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and
uninstall first. CI signs with the key in the `DEBUG_KEYSTORE` secret, which is the only way two
builds can update each other.

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

## Code style

ktlint enforces style and CI blocks on it.

```
./gradlew ktlintCheck     # what CI runs
./gradlew ktlintFormat    # fix the mechanical violations
```

The rules come from `.editorconfig` at the repository root: `android_studio` code style, so
the Android Studio formatter and ktlint produce the same result. Compose functions are exempt
from the lowercase naming rule. `org/prowl/torque` (Torque's AIDL mirror) and the
`speedviewlib` submodule are excluded — both are third-party code.

The plugin is `org.jlleitschuh.gradle.ktlint` with the ktlint CLI pinned to `1.8.0` in
`app/build.gradle`. ktlint only fixes part of the rule set; the rest — naming, long lines,
file names — needs a manual change.

## Warnings you can ignore

Two warnings come from outside this repository and are expected:

- `The specified Android SDK Build Tools version (31.0.0) is ignored` — `buildToolsVersion` is
  not set anywhere here. It comes from the `lib/speedviewlib` submodule (`agronick/SpeedView`).
  Fixing it means patching the submodule or forking it.
- `SDK processing. This version only understands SDK XML versions up to 3` — a mismatch between
  runner tooling and the SDK, not repository configuration.

The `[dep-ann]` warnings from `org/prowl/torque/remote/ITorqueService.java` are suppressed in the
root `build.gradle`; that file mirrors Torque's AIDL interface and is not ours to annotate.

## `dist/`

`dist/` is git-ignored. A downloaded CI artifact usually lands there. It is scratch space,
not a source of truth.

## Without a local SDK

If you cannot install an SDK, work through CI instead: push a branch, let the `lint` job run
`./gradlew lintRelease`, and let CI build the APK for the release. That is the supported path
for an agent on a machine without an Android SDK.
