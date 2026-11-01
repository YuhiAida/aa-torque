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
| `version.properties` | Version source. Only the release PR bumps it; do not edit it by hand. |
| `docs/` | Reference documentation. |
| `dist/` | Git-ignored scratch directory where a downloaded CI artifact lands. |

## How to verify a change

CI is the required gate. It runs on every pull request and on pushes to `master`.

1. Work on a branch, push it, and open a pull request. A branch push alone starts no checks.
2. Read the run: `gh run list -R YuhiAida/aa-torque -L 5`
3. Four checks must pass, each its own job:
   - `lint` — `./gradlew ktlintCheck` and `./gradlew lintRelease`
   - `render` — `./gradlew verifyRoborazziDebug`: the JVM tests and the theme goldens
   - `themes` — `tools/check_themes.py` when theme resources changed
   - `docs` — `tools/check_docs.py` when markdown changed
4. Do not merge. Open a **draft** pull request and stop. Yuhi merges.

A local build is optional. This machine has no Android SDK installed. The setup steps are
in [docs/building.md](docs/building.md). Do not install an SDK, NDK or emulator image on
Yuhi's machine without asking first.

There are no instrumentation tests. The JVM tests in `app/src/test` render every theme with
Robolectric and compare against the goldens in `app/src/test/screenshots`; they still do not
exercise the Torque binding or the Android Auto surface. Do not claim a runtime behaviour is
verified — state what CI checked and what it did not.

Record a theme golden with `./gradlew recordRoborazziDebug`, or the `record` workflow
(Actions → record → Run workflow) when there is no local SDK. Record, review the PNG, and
commit it with the theme change. Never hand-edit a golden.

Markdown is checked by `tools/check_docs.py`, which runs in the `docs` job whenever a `.md` file
or anything under `docs/` changes. It verifies that every relative link and every repository path
written in backticks resolves. Run it before pushing a doc change.

Themes are checked by `tools/check_themes.py`, which runs in the `themes` job whenever theme
resources change. It verifies that `Themes`, `ThemesThumbs` and `ThemeList` agree by index,
that every style named in `ThemeList` is defined and sets the required attributes, and that
every referenced drawable and colour exists. Run it before pushing a theme change.

A pull request also builds a debug APK (`pr_artifact`). Download it from the run and
install it if you need to look at the UI: CI compiles and checks, it does not render. A theme
that compiles can still be visually wrong, and a merge to `master` publishes immediately.

Pull requests run the same checks through the `pull_request` trigger, including PRs from
forks. Nothing is committed by CI any more, so a fork PR behaves exactly like a branch PR:
both are checked. Only the release PR bumps `version.properties`. The debug artifact still
runs.

## Branch and release rules

- Never push to `master`. A repository ruleset requires a pull request and blocks direct
  pushes — for humans and for CI, with no bypass.
- **Only the release PR bumps `version.properties`.** CI opens a `release/*` PR weekly or
  after five merged PRs; every other PR that changes the file fails its checks. CI never
  writes `master`: a commit pushed by a job is a direct push and the ruleset rejects it.
- A push to `master` starts a release. CI reads the version, force-pushes the tag `latest`,
  and **publishes** a GitHub release that holds the APK. Publishing is automatic. If the
  version was not bumped, the run reports "already released" and publishes nothing. Never
  delete a release or a version tag, and never reuse a version number — fix forward.
- A separate `dev` workflow publishes a rolling pre-release on every code merge to `master`:
  package `com.aatorque.stats.debug`, version `<last stable>-dev.<commits>` (the installed
  APK's versionName ends with `.debug`), signed with the shared debug key. It installs beside
  the stable app and is not the stable channel; the update check is disabled in that build.

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
