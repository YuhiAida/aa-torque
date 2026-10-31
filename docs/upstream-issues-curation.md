# Upstream issue curation — agronick/aa-torque

Snapshot: 2026-10-31. Upstream is dormant: last commit `2025-03-04`, last
release `2.0.30`, 44 open issues (46 items incl. 2 PRs), 557 stars, 56 forks.

This file records the open upstream issues that are worth a look, and the ones
that are already handled in this fork. None of the ideas below are urgent.

## Good ideas (candidates, not scheduled)

| # | Idea | Effort | Notes |
|---|---|---|---|
| [#67](https://github.com/agronick/aa-torque/issues/67) | Per-gauge "show in chart" toggle | S | Chart plots every gauge today; `Display` needs a `bool showInChart` + a settings checkbox + a filter in `TorqueChart.setupItems`. |
| [#203](https://github.com/agronick/aa-torque/issues/203) | Unit conversion (ft-lb ↔ Nm) | S | Add a unit mapping in `TorqueData.convertValue`. |
| [#206](https://github.com/agronick/aa-torque/issues/206) | 2-decimal precision for lambda/AFR | S | `twoPlaces` formatter already allows 2 decimals; wire the value display to it, or add a precision setting. |
| [#68](https://github.com/agronick/aa-torque/issues/68) | User-selectable needle/ray colour | M | Needle colour is theme-bound (`themedNeedleColor`); reuse the existing `ColorPreference`. |
| [#124](https://github.com/agronick/aa-torque/issues/124) | Blinkable alarm | S-M | Alarm already animates via `OpenCloseAnimator`; add a blink mode + frequency. |
| [#159](https://github.com/agronick/aa-torque/issues/159) | Vertical (portrait) backgrounds | S-M | Backgrounds are landscape drawables; add portrait variants and pick by orientation. |
| [#72](https://github.com/agronick/aa-torque/issues/72) | Decimal min/max | M | `minValue`/`maxValue` are `int32` in the proto; needs a float/fixed-point migration + UI. |
| [#129](https://github.com/agronick/aa-torque/issues/129) | Profiles for multiple cars | L | Named group of dashboards, selectable on the head unit; proto + UI. |
| [#135](https://github.com/agronick/aa-torque/issues/135) | Dial rotation (45°) | M | Needs a custom view transform over SpeedView. |

## Bugs worth investigating (need evidence/log)

- [#214](https://github.com/agronick/aa-torque/issues/214) — duplicate GM transmission-temp PIDs; dedupe in the PID list.
- [#207](https://github.com/agronick/aa-torque/issues/207) — knock-retard PID filtered out by `listECUSupportedPIDs`.
- [#137](https://github.com/agronick/aa-torque/issues/137) — custom Ford PID not updating.
- [#136](https://github.com/agronick/aa-torque/issues/136) / [#73](https://github.com/agronick/aa-torque/issues/73) — GPS value zero until Torque is opened (already in `docs/troubleshooting.md`).
- [#123](https://github.com/agronick/aa-torque/issues/123) — alarm colour with unit conversion.
- [#175](https://github.com/agronick/aa-torque/issues/175) — centre value clamped to gauge min.
- [#128](https://github.com/agronick/aa-torque/issues/128) — element overlap on the head unit.
- [#183](https://github.com/agronick/aa-torque/issues/183) — crash in AA on Samsung S10 (log attached upstream).

## Not code / already handled

**Install-record / support.** Commented on upstream (fixed in the fork): `#87`, `#130`, `#189`, `#195`, `#205`, `#150`, `#220`. Out of scope, not commented: `#171`, `#223`, `#138`.
The "not visible in AA" symptom is an install-record problem, not app code.
The fork documents the fix (KingInstaller + Shizuku) in `docs/aa-install-gate.md`.

**Already done in this fork.**
- [#143](https://github.com/agronick/aa-torque/issues/143) — swipe between dashboards (`DashboardFragment` GestureDetector + `setScreen`).
- [#202](https://github.com/agronick/aa-torque/issues/202) — CI builds/signs release (minified) APKs.
- [#200](https://github.com/agronick/aa-torque/issues/200) — pinned `SpeedView` submodule builds with the current AGP.

**Out of scope for this app.**
- [#223](https://github.com/agronick/aa-torque/issues/223) — Play Console testers; we ship via GitHub Releases.
- [#220](https://github.com/agronick/aa-torque/issues/220) — Android 8; `minSdk 28` (Android 9).
- [#138](https://github.com/agronick/aa-torque/issues/138) — car audio without Android Auto.
- [#191](https://github.com/agronick/aa-torque/issues/191) — adapter connect/disconnect; Torque owns the adapter.
- [#122](https://github.com/agronick/aa-torque/issues/122) — auto-start; AA controls the foreground app.
- [#85](https://github.com/agronick/aa-torque/issues/85) / [#146](https://github.com/agronick/aa-torque/issues/146) — split screen; AA controls it.
