# Releases

Everything is CI. Nobody builds a release by hand, and nobody publishes one from a laptop.

## What triggers a release

`.github/workflows/ci.yml` runs on pushes to `master` and on every pull request:

| Job | Runs on | Purpose |
|---|---|---|
| `changes` | every run | Diffs the change and sets `code`, `themes` and `docs`, so each gate reports on every pull request (a path-filtered workflow that never runs cannot be a required check). |
| `lint` | code changes | `ktlintCheck` and `lintRelease`, then regenerates `docs/generated/inventory.md` and commits it when it changed and the event is a push. |
| `render` | code changes | `verifyRoborazziDebug`: the JVM tests and the theme goldens. |
| `themes` | theme changes | `tools/check_themes.py`, the theme contract. |
| `docs` | markdown changes | `tools/check_docs.py`, links and paths. |
| `build_deploy` | `master` push with code changes | Bumps the version, builds and publishes the APK. |

A pull request runs the four gates and `pr_artifact`; the release job is skipped. The release
path cannot be exercised from a branch without relaxing that condition; see "Changing the
workflow" below.

The workflow has a `concurrency` group per ref with `cancel-in-progress: true`. Merging several
pull requests in a row triggers one push per merge, so without this a release job starts per
push and they all bump and push `version.properties` at the same time. On 2026-10-31 that
produced three failed runs — two `non-fast-forward`, one `tag '2.0.33' already exists` — and one
winner. With the group in place only the newest run survives a burst, so a burst yields one
version.

## The `build_deploy` steps

1. Restore the debug keystore from the `DEBUG_KEYSTORE` secret (base64).
2. Check whether HEAD is already a `release: v…` commit. If it is, skip the bump: a cancelled
   run may have pushed its bump commit before it died, and bumping again would skip versions.
3. Bump the version (`oflynned/android-version-bump@v2.0.0`). See "Version numbers" below.
4. Move the tag `latest` to the current commit (plain `git tag -f latest` + `git push -f`), so it
   points at the same commit as the version tag the bump produced.
5. `./gradlew assembleRelease`. Forked repositories build unsigned by the runner and fall
   back to the `signingConfig signingConfigs.debug` in `app/build.gradle`, so the artifact is
   debug-signed and installs over earlier builds.
6. Rename the artifact to `aa-torque.apk`.
7. Create a GitHub release with `softprops/action-gh-release@v3.0.3`: APK plus
   `app/build/outputs/mapping/release/mapping.txt`, `generate_release_notes: true`,
   **`draft: false`** — published immediately. The release tag is read from
   `version.properties`, so it is correct whether or not this run performed the bump.

A push to `master` therefore publishes a release named after the new version, with the APK
attached. Users can install it as soon as the run finishes, and the in-app update check sees
it because `GET /releases/latest` only returns published releases.

Because the `concurrency` group lets one run survive a burst, a burst of merges publishes one
release, not one per merge. Merges spaced more than a run apart (~4 minutes) each publish
their own.

## Version numbers

`version.properties` holds `majorVersion`, `minorVersion`, `patchVersion` and an optional
`buildNumber`. `app/build.gradle` reads it and computes:

```
versionName = major.minor.patch
versionCode = major * 10000 + minor * 100 + patch
```

Consequences: each component is packed into its own field, so patch and minor must each
stay below 100 and major must stay below 214748 (Android's `versionCode` is a signed
32-bit integer, so `major * 10000` must fit). In practice major is single-digit.

### The bump in this fork

The step runs when `github.repository == 'YuhiAida/aa-torque'`. It reads commit messages back
to the previous tag, so `build_deploy` checks out with `fetch-depth: 0`; without that history
the action cannot find the range.

The action writes `version.properties`, commits `release: v<version> [skip-ci]`, and pushes
the tag `<version>`. `[skip-ci]` matters in two ways: GitHub skips the workflow for that
push, and a push made with `GITHUB_TOKEN` does not start a workflow anyway.

Commit prefixes drive the size of the bump, because the action reads conventional commits:

| Commit | Bump |
|---|---|
| `fix:`, `docs:`, `chore:`, anything unrecognised | patch |
| `feat:` | minor |
| `feat!:`, `refactor!:`, or a `BREAKING CHANGE` body | major |

Proven by a dry run on 2026-10-31: a push produced `release: v2.0.32 [skip-ci]`, tag
`2.0.32`, and `version.properties` at `2.0.32`; the build step then assembled the release
in the same workspace, so the APK carries the bumped version.

## Docs-only pushes do not build

The `changes` job diffs the push or the pull request and sets `code=false` when every changed
file is prose: `LICENSE`, a `.md` file, or anything under `docs/`. `build_deploy` and
`pr_artifact` run only when `code=true`, so editing the README or a file under `docs/` leaves
the version alone. A mixed push (code plus docs) still releases.

Prose is not unchecked, though: the `docs` job runs `tools/check_docs.py` whenever markdown
changes, and the `themes` job runs `tools/check_themes.py` whenever theme resources change.
Both are ordinary jobs in `ci.yml`, so they appear as checks on every pull request; the step
that does the work is skipped when the category did not change.

`master` can then sit ahead of the newest release. That is intended: the release marks the last
change that altered the APK, which is exactly what the in-app update check should compare.

## Changing the workflow

The release job only runs on `master`, so a change to it cannot be proven on a branch.

Safe way to test a workflow change:

1. Add `workflow_dispatch` to `on:`.
2. Temporarily relax the `if: github.ref == 'refs/heads/master'` condition on your scratch
   branch.
3. Run the workflow manually from that branch and read the log.
4. Revert the relaxation before you open the pull request.

Never leave the relaxation in a merged change: it lets any branch push create a release.

## Checking a published release

A release goes out on its own, with no human step. If a bad one ships, check three things and
then fix forward:

1. The APK installs and starts.
2. The version shown in the app matches `version.properties`.
3. The release notes describe the actual changes, not just the commit list.

Fixing forward is normal: revert the change on `master` and the next run publishes the next
patch. Do not delete a published release and its tag — someone may already have installed it,
and reusing a version number breaks the version ordering on every phone that has it.

The only manual release action left is unpublishing a release that must not be visible
(Releases page → the release → edit → uncheck publish), which keeps the tag.
