# Releases

Everything is CI. Nobody builds a release by hand, and nobody publishes one from a laptop.

## What triggers a release

`.github/workflows/ci.yml` runs on pushes to `master` and on every pull request:

| Job | Runs on | Purpose |
|---|---|---|
| `changes` | every run | Diffs the change and sets `code`, `themes` and `docs`, so each gate reports on every pull request (a path-filtered workflow that never runs cannot be a required check). |
| `lint` | code changes | `ktlintCheck` and `lintRelease`, then regenerates `docs/generated/inventory.md` and commits it when it changed and the event is a **push to `master`**. |
| `render` | code changes | `verifyRoborazziDebug`: the JVM tests and the theme goldens. |
| `themes` | theme changes | `tools/check_themes.py`, the theme contract. |
| `docs` | markdown changes | `tools/check_docs.py`, links and paths. |
| `build_deploy` | `master` push with code changes | Reads the version bumped by the release PR, builds and publishes the APK, and refuses to publish a version that is already tagged. |

A pull request runs the four gates and `pr_artifact`; the release job is skipped. The release
path cannot be exercised from a branch without relaxing that condition; see "Changing the
workflow" below.

The workflow has a `concurrency` group per ref with `cancel-in-progress: true`. Merging several
pull requests in a row triggers one push per merge, so without this a release job starts per
merge. They no longer collide on `version.properties` — CI never writes it — but they would
publish in parallel and race for the same tag. With the group in place only the newest run
survives a burst, so a burst yields one version.

## The `build_deploy` steps

1. Restore the debug keystore from the `DEBUG_KEYSTORE` secret (base64).
2. Read the version from `version.properties` and check whether the tag `<version>` already
   exists. The version is bumped by the release PR (see "Version numbers"), so a merge that
   did not bump it finds its own tag and publishes nothing: the run prints an "already
   released" notice instead of failing.
3. Move the tag `latest` to the current commit (plain `git tag -f latest` + `git push -f`).
4. `./gradlew assembleRelease`. Forked repositories build unsigned by the runner and fall
   back to the `signingConfig signingConfigs.debug` in `app/build.gradle`, so the artifact is
   debug-signed and installs over earlier builds.
5. Rename the artifact to `aa-torque.apk`.
6. Create a GitHub release with `softprops/action-gh-release@v3.0.3`: APK plus
   `app/build/outputs/mapping/release/mapping.txt`, `generate_release_notes: true`,
   **`draft: false`** — published immediately. The release tag is read from
   `version.properties`, which the release PR already bumped.

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

### Bumping in this fork

The version is bumped **only by the release PR** (branch `release/*`), which the
`release-pr` workflow opens. CI does not write to `master`: the repository ruleset
rejects direct pushes, so a bump commit pushed by a workflow would fail the run.

No other pull request may touch `version.properties`; CI fails such a change. Before
merging a change that alters the APK, do not edit `version.properties` by hand —
wait for the release PR.

Before merging a change that alters the APK, the release PR computes the bump from
the conventional commits since the last tag:

| Commit | Bump |
|---|---|
| `fix:`, `docs:`, `chore:`, anything unrecognised | patch |
| `feat:` | minor |
| `feat!:`, `refactor!:`, or a `BREAKING CHANGE` body | major |

The tag is the plain version, `<major>.<minor>.<patch>` — no `v` prefix. `build_deploy` refuses
to publish a version whose tag already exists, so a merge that forgot the bump publishes nothing
and prints a notice instead of overwriting a release.

Consequence: two open pull requests that both bump `version.properties` conflict in that file.
Rebase the newer one and bump again.

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
