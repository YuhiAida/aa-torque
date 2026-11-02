# Android Auto quality criteria: what applies here

The [car app quality guidelines](https://developer.android.com/docs/quality-guidelines/car-app-quality)
attach every row to an app category. AA Torque is a custom projection app with no supported
category, so most of the checklist cannot be evaluated rather than being failed. This page
records the rows that do apply, and the checks worth doing even though they are not required.

## Rows that apply

| ID | Requirement | Status |
|---|---|---|
| `PC-1` | The app must not include features outside the app types intended for cars. | **Fails.** A free-form gauge dashboard is not a supported category. This is the reason Play cannot list the app; see `docs/aa-install-gate.md`. |
| `EP-1` | The app must work as expected or as described in its Play Store listing. | **Partial.** The app works, but a listing would have to state the Torque Pro dependency and the KingInstaller install step. |
| `AR-1` | Interactive elements must not be obstructed by system bars or cutouts. | **Not applicable.** The row covers activities on Android Automotive OS, and this app has no AAOS support. |

## Rows that do not apply

Every other row names its categories and excludes this app: `AC-1`, `DD-1` to `DD-4`,
`DR-1` to `DR-3`, `VD-1` to `VD-3`, `UX-1` to `UX-3`, `ST-1`, `IU-1`, `AD-1`, `SA-1`,
`TH-1`, `GB-1`, `PA-1`, `NA-1`, `IN-1` to `IN-2`, `MA-1`, `MF-1` to `MF-5`, `NF-1` to
`NF-9`, `PF-1`, `IT-1`, `WE-1` to `WE-5`, `MR-1`, `MC-1`, `DL-1` to `DL-2`, `MFT-1` to
`MFT-3`, `TMF-1`, `CF-1` to `CF-3`, `AN-1`, `SD-1` to `SD-2`.

The guidelines are therefore not a useful acceptance test for this app. They become relevant
again only if the car UI moves to a supported category, such as the media card.

## Checks worth doing anyway

These are not required, but they measure what a driver feels:

| Check | Reference | Current state |
|---|---|---|
| Crash-free sessions | `AC-1` (title, not the five-screen rule) | The JVM tests render themes; no test covers the Android Auto surface. |
| Launch and content within 10 s, buttons within 2 s | `DR-1` to `DR-3` | Not measured. The surface binds to Torque, so the first values depend on Torque. |
| Contrast of text and gauges against the theme background | `VD-1` | Not checked. The themes are user-selectable and hand-defined. |
| Touch targets of the surface controls at least 64 dp, 24 dp apart | `UX-1`, `UX-2` | Not measured. The surface has next, previous, chart and gesture areas. |

## What would need fixing

Nothing here fixes `PC-1`; the custom UI is the product. The quality follow-ups are a theme
contrast check in `tools/check_themes.py`, a bench measurement of launch and button response,
and a touch-target pass over the surface controls.
