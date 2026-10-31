# CI testing plan

Status as of v2.3.6 (2026-10-31): the render job landed (PR #57), the duplicate-run and
missing-required-checks issues are fixed (#47, #48).

## What the gate does today

| Job | Trigger | Checks |
|---|---|---|
| `ci.yml` → `changes` | every run | classifies `code`, `themes`, `docs` |
| `ci.yml` → `lint` | code changes | `ktlintCheck`, `lintRelease` |
| `ci.yml` → `render` | code changes | `verifyRoborazziDebug` (JVM tests + 26 theme goldens) |
| `ci.yml` → `themes` | theme changes | `tools/check_themes.py` (static theme contract) |
| `ci.yml` → `docs` | markdown changes | `tools/check_docs.py` (links and paths) |
| `ci.yml` → `pr_artifact` | pull requests with code | builds a debug APK and uploads it |
| `ci.yml` → `build_deploy` | `master` push with code | bumps, builds, verifies the signer, publishes |

The JVM tests render every theme and compare against committed goldens (PR #57). The app
still launches nothing, and the `master` ruleset requires `lint`, `render`, `themes` and
`docs` (issue #48).

## The gaps

1. **No visual test.** A theme or layout that compiles can still render wrong: a needle
   off its pivot, a clipped label, overlapping widgets, a wrong colour. The `themes` job only
   checks that resources exist and arrays line up.
2. **No runtime test.** Nothing starts an Activity or a Fragment. A crash on launch, a
   broken inflation, or a bad preference migration is found by users, not CI.
3. **No enforcement.** The `master protection` ruleset (26284427) only blocks deletion
   and force-push. A red PR can be merged, and a merge publishes immediately.

## Spike findings (2026-10-31, PR #56)

Robolectric native graphics **does** render SpeedView. Two themes (Porsche 718, Audi) rendered
with their dial faces, tick marks, redline and needle.

**Working stack:**
- Roborazzi **1.47.0** — the newest release compiled with Kotlin 1.9.22. 1.48+ is Kotlin 2.x
  and the project's Kotlin 1.9.20 compiler rejects its metadata.
- Robolectric **4.17** plus `org.robolectric:nativeruntime:4.17` — `@GraphicsMode(NATIVE)`
  needs the native runtime as a separate artifact.
- `android.jetifier.ignorelist=bcprov-jdk18on` in `gradle.properties`. Robolectric pulls
  BouncyCastle 1.85 (Java 25 bytecode) that AGP 8.2's Jetifier cannot transform. Excluding
  BouncyCastle breaks Robolectric at runtime (it needs `BouncyCastleProvider`), so the
  ignorelist is the fix: Jetifier skips it, the jar stays on the classpath.
- `testOptions.unitTests.includeAndroidResources = true`.

**Two traps in the test itself:**
- Inflate with a `ContextThemeWrapper`; `setTheme` on the application context does not reach
  the views' theme-attribute lookups.
- Call `binding.executePendingBindings()` before drawing, or the `@{}` attributes (the dial
  background) are not applied yet.

**Caveats for the real goldens:**
- The spike uses `minMax = 0..100` and `Typeface.DEFAULT`; use the display's real min/max and
  the theme font.
- The needle is the SpeedView default; the theme's image needle needs the
  `TorqueGauge.turnRaysEnabled` setup, or render the `TorqueGauge` fragment itself.

## Phase 1 — screenshot/golden tests (highest value)

Goal: render the gauge/dial per theme at a fixed size and compare against golden PNGs,
inside `testDebugUnitTest` (JVM, no emulator).

**Tool choice**

| Tool | How it renders | Fit for SpeedView |
|---|---|---|
| **Roborazzi** (Robolectric + native Skia) | Robolectric `GraphicsMode.NATIVE` | likely good — real Canvas |
| Paparazzi (LayoutLib) | Android Studio rendering | uncertain for custom Canvas views |

Recommendation: **spike Roborazzi** first. It is a JVM test, so it rides the existing
`testDebugUnitTest` gate and needs no emulator.

**Spike (one draft PR)**
1. Add `robolectric` + `roborazzi` as `testImplementation` (ask before adding deps — AGENTS.md).
2. One test inflates `TorqueSpeedometer` (or `TorqueGauge`) with `AppTheme.Porsche982`
   at a pinned size and density, and records a golden.
3. Run `testDebugUnitTest` in CI; compare.
4. Update path: `./gradlew recordRoborazziDebug`.

If the spike fails (SpeedView's custom attrs or Canvas do not render), fall back to Phase 3.

**After the spike**
- One golden per theme (26 today) or a representative subset (gauge, speedometer, chart).
- Keep it in `lint` or add a `render` job that runs in parallel.
- Pin density, font scale and size so goldens are stable across runners.

**Risks**
- Robolectric/AGP/JDK 19 version matrix; pin versions and verify.
- Goldens drift with font rendering; use a small tolerance or disable fonts in the test.
- A dependency addition needs approval.

## Phase 2 — enforce the gate (issue #48)

- Add a `required_status_checks` rule to the `master` ruleset: `lint`, `themes`, `docs`,
  and `render` once it exists.
- Optional: require one pull request review (you are the only maintainer; can be skipped).
- This is a repo-settings change (API or UI), not code.

## Phase 3 — emulator instrumentation (optional)

- `reactivecircus/android-emulator-runner` + `connectedDebugAndroidTest` on an AVD (API 34).
- Smoke test: launch `SettingsActivity`, apply each theme, assert no crash, capture a screenshot.
- Cost: 5–10 minutes, flaky; run it as a separate workflow, not on every push.
- Limit: the Android Auto `CarActivity` still needs a phone + DHU/head unit, so this only
  covers the phone UI.

## Phase 4 — CI hygiene (optional)

- Issue #47: same-repo PRs run both `push` and `pull_request`. Accept, or add a per-job
  `if` that skips the duplicate when a PR is open.
- Issue #46: re-encode the theme PNGs to WebP (perf, separate).

## Deliverables

1. This plan in `docs/ci-testing.md`.
2. A Roborazzi spike PR with one golden (Porsche 982).
3. A `render` job if the spike works, with the golden-update command in `docs/building.md`.
4. A ruleset update adding the required checks.

## Decisions needed

1. Approve the **Roborazzi** dependency for the spike? (Paparazzi as fallback.)
2. Golden scope: **all 26 themes**, or a representative set (one gauge + one speedometer)?
3. Emulator smoke test: yes or no?
4. Required checks: `lint` + `themes` + `docs` now, and `render` when it lands?
