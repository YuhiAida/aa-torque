# Releases

Everything is CI. Nobody builds a release by hand, and nobody publishes one from a laptop.

## What triggers a release

`.github/workflows/ci.yml` runs `on: [push]` and holds two jobs:

| Job | Runs on | Purpose |
|---|---|---|
| `lint` | every branch push | `./gradlew lintRelease`, then regenerates `docs/generated/inventory.md` and commits it when it changed. This is the required gate for a change. |
| `build_deploy` | `master` only (`if: github.ref == 'refs/heads/master'`) | Runs after `lint` (`needs: lint`). Bumps the version, builds and publishes the APK. |

A draft pull request therefore gets lint only. The release path cannot be exercised from a
branch without relaxing that condition; see "Changing the workflow" below.

## The `build_deploy` steps

1. Restore the debug keystore from the `DEBUG_KEYSTORE` secret (base64).
2. Bump the version (`oflynned/android-version-bump@v2.0.0`). See "Version numbers" below.
3. Force-push the tag `latest` to the current commit (`rickstaa/action-create-tag`).
4. `./gradlew assembleRelease`. Forked repositories build unsigned by the runner and fall
   back to the `signingConfig signingConfigs.debug` in `app/build.gradle`, so the artifact is
   debug-signed and installs over earlier builds.
5. Rename the artifact to `aa-torque.apk`.
6. Create a GitHub release with `softprops/action-gh-release`: APK plus
   `app/build/outputs/mapping/release/mapping.txt`, `generate_release_notes: true`,
   **`draft: true`**. The release takes the tag the bump produced, and falls back to `latest`
   when no bump ran.

A push to `master` therefore produces a **draft** release named after the new version. It is
not visible to users until a maintainer publishes it. Publishing is a manual step on the
Releases page.

## Version numbers

`version.properties` holds `majorVersion`, `minorVersion`, `patchVersion` and an optional
`buildNumber`. `app/build.gradle` reads it and computes:

```
versionName = major.minor.patch
versionCode = major * 1000 + minor * 100 + patch * 10
```

Consequences: the patch component must stay below 100, or `versionCode` collides with the
next minor version. Bad numbers fail the release quietly in the Play-style ordering sense —
Android still installs, but "newer" is wrong.

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

## Changing the workflow

The release job only runs on `master`, so a change to it cannot be proven on a branch.

Safe way to test a workflow change:

1. Add `workflow_dispatch` to `on:`.
2. Temporarily relax the `if: github.ref == 'refs/heads/master'` condition on your scratch
   branch.
3. Run the workflow manually from that branch and read the log.
4. Revert the relaxation before you open the pull request.

Never leave the relaxation in a merged change: it lets any branch push create a release.

## Publishing

Publishing a draft release makes the APK downloadable. Check three things first:

1. The APK installs and starts.
2. The version shown in the app matches `version.properties`.
3. The release notes describe the actual changes, not just the commit list.
