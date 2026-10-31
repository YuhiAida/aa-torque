# AGENTS.md

Instructions for coding agents that work in this repository. Read this file first.

Human-facing notes are in [CONTRIBUTING.md](CONTRIBUTING.md). Reference material is in
[`docs/`](docs/).

## What this project is

AA Torque is an Android app. It shows OBD-II data from **Torque Pro** (`org.prowl.torque`)
as gauges on an Android Auto head unit. The app binds to Torque's AIDL service, reads PID
values, and draws them through the Android Auto projection SDK.

This repository is a fork of `agronick/aa-torque`. Upstream is unmaintained, so we change
the app freely.

- Kotlin and Java sources, about 8k lines, all in `app/`.
- `minSdkVersion 28`, `targetSdkVersion 34`, `compileSdk 34`.
- Gradle wrapper 8.2, Android Gradle Plugin 8.2.2, Kotlin 1.9.20, Java 19 target.
- Application id `com.aatorque.stats`. It replaces upstream in place. Both cannot install
  side by side.

## Repository map

| Path | Purpose |
|---|---|
| `app/` | The only app module. All app code lives here. |
| `app/src/main/java/com/aatorque/stats/` | Android Auto entry, Torque binding, refresh loop, gauges. |
| `app/src/main/java/com/aatorque/prefs/` | Settings screens and the DataStore/Protobuf preference store. |
| `app/src/main/java/com/aatorque/ui/theme/`, `utils/` | Compose theme and small helpers. |
| `app/src/main/java/org/prowl/torque/remote/` | Mirror of Torque's AIDL interface. Treat it as third-party code. |
| `app/src/main/aidl/` | The AIDL definition the mirror implements. |
| `app/src/main/proto/user_prefs.proto` | Preference schema, generated into `com.aatorque.datastore`. |
| `app/src/main/res/` | Resources, including 16 translation folders. |
| `lib/aauto.aar` | Prebuilt Android Auto SDK. Ask before you change or replace it. |
| `lib/speedviewlib/` | Git submodule (`agronick/SpeedView`). It is **empty** until `git submodule update --init`. |
| `version.properties` | Version source. CI writes it on `master`. Do not edit it by hand. |
| `docs/` | Reference documentation. `docs/generated/` is machine-written. |
| `dist/` | Git-ignored scratch directory where a downloaded CI artifact lands. |

The source inventory is [docs/generated/inventory.md](docs/generated/inventory.md). CI
generates that file. Do not edit it.

## How to verify a change

CI is the required gate. It starts on every branch push.

1. Work on a branch and push it.
2. Read the run: `gh run list -R YuhiAida/aa-torque -L 5`
3. The `lint` job runs three checks and all must pass:
   - `./gradlew ktlintCheck` — style, from `.editorconfig`
   - `./gradlew testDebugUnitTest` — the JVM unit tests in `app/src/test`
   - `./gradlew lintRelease` — Android lint, and it compiles the release variant
4. Do not merge. Open a **draft** pull request and stop. Yuhi merges.

A local build is optional. This machine has no Android SDK installed. The setup steps are
in [docs/building.md](docs/building.md). Do not install an SDK, NDK or emulator image on
Yuhi's machine without asking first.

There are no instrumentation tests. The JVM tests in `app/src/test` cover pure logic only, so
nothing exercises Android, the Torque binding, or the gauges. Do not claim a runtime behaviour
is verified — state what CI checked and what it did not.

## Branch and release rules

- Never push to `master`. Use a branch and a draft pull request.
- A push to `master` starts a release. CI bumps `version.properties`, force-pushes the tag
  `latest`, and **publishes** a GitHub release that holds the APK. Publishing is automatic.
  Never delete a release or a version tag, and never reuse a version number — fix forward.
- Never edit `version.properties` by hand.

## Track what you find

Every anomaly, warning or improvement you notice becomes a GitHub issue in this repository.
Do not leave it in a chat message or a pull request comment.

- State the evidence: run id, `file:line`, command output.
- List the options and give a recommendation.
- Reference the issue from the pull request that fixes it.
- Small enough to fix in the same change? Fix it and say so in the commit message; file the
  issue only for what is left.

## Code rules

- Make the smallest diff that solves the problem. Do not reformat unrelated code.
  A formatting-only change belongs in its own pull request.
- Run `./gradlew ktlintCheck` before you push. `./gradlew ktlintFormat` fixes what is
  mechanical. The style comes from `.editorconfig` (`android_studio`), so the IDE and ktlint
  agree. `org/prowl/torque` and the `speedviewlib` submodule are excluded on purpose.
- Ask before you add a dependency.
- Do not add network, analytics or telemetry code. `ch.acra` (crash mail) is the only
  component that sends data off the phone.
- Match the style of the surrounding file.
- When your change moves a data path, adds a module or renames a subsystem, update
  [docs/architecture.md](docs/architecture.md) in the same pull request.

## Two behaviours you must not "fix"

Both look like bugs. Both are correct. Details are in `docs/`.

- **Which PIDs are offered.** User-facing lists come from `listECUSupportedPIDs()` plus
  Torque's synthetic `ff*` ids. They never come from `listAllPIDs()` alone. See
  [docs/pid-filtering.md](docs/pid-filtering.md).
- **The app is missing from Android Auto.** This is an install-record problem, not app
  code. See [docs/aa-install-gate.md](docs/aa-install-gate.md). Do not look for it in
  `CarService.java`.

## Where to write documentation

| Question | File |
|---|---|
| How do I build, install and run it? | `docs/building.md` |
| How does data flow through the app? | `docs/architecture.md` |
| Why does a PID show zero? | `docs/pid-filtering.md` |
| Why is the app not in Android Auto? | `docs/aa-install-gate.md` |
| How does a release happen? | `docs/releasing.md` |
| Something else is broken | `docs/troubleshooting.md` |
| Themes and graphics | `CONTRIBUTING.md` |
