# The Android Auto media card

Why the gauges are also a media app, and how that path works.

See [issue #60](https://github.com/YuhiAida/aa-torque/issues/60). That request is closed:
one app cannot do it, and the two-app arrangement was rejected. This document keeps the
record of the route.

## Status: closed after testing

One app cannot put the gauges beside Google Maps. The arrangement that works needs two
apps: one projection app for the gauges and one media app for the card. Two apps was
rejected by the maintainer. A single app that takes the whole screen remains the product
model. This document keeps the technical detail as history of the rejected investigation.

The record for that conclusion is the closing comment on
[issue #60](https://github.com/YuhiAida/aa-torque/issues/60). Bench: OnePlus CPH2551,
Android 16, Android Auto 17.9.664044, Desktop Head Unit 2.1, 2026-11-01. The build carried
both launcher entries, the projection app and the media browser. With both entries
present, the projection entry takes the whole screen and the media entry gives only the
rail card. With projection dropped, choosing the app opens an empty media screen, because
`CardService.onLoadChildren` returns an empty list. No screenshot was captured: the head
unit window landed off-screen, so the evidence is the two launcher entries and the card
metadata read on the bench.

The implementation was never merged. It lives on `feat/media-card-artwork`, head
`a7e0ec7`, which is closed pull request [#62](https://github.com/YuhiAida/aa-torque/pull/62).

The sections below record the rejected investigation; they are kept for history.

## The problem

A projection app owns the whole Android Auto screen. Choosing AA Torque hides Maps; choosing
Maps hides the gauges. Android Auto's split layout puts navigation in the hero pane and gives
everything else a narrow rail, and that rail admits **media, communication and navigation** apps.
A `CATEGORY_PROJECTION` app is never eligible, so no layout work inside the current screen can
put the gauges beside Google Maps.

## The route taken

Register as a media app. The session is a carrier for a card, not a player:

- `CardService` is a `MediaBrowserService` with a `MediaSession` that is always `PLAYING`.
- No audio is played, no audio focus is taken, no foreground service is started.
- Transport commands (`onPlay`, `onPause`, `onStop`) are ignored on purpose. A driver tapping
  Play/Pause must never freeze the gauges.
- The card shows artwork plus the title and subtitle lines Android Auto draws over it.

Routes that were rejected, with the reason, are in the issue: the overlay (outside AA, needs a
data path to the unit), the Car App Library map template (cannot host Google Maps), and the
Zlink split (not this repository).

## What the driver sees

- **Style** is a preference: numbers only, one dial plus numbers, or two dials plus numbers.
  `CardArtwork` draws all three at card size, reusing the real themed gauge view so the dial
  matches the chosen theme.
- **The bottom third is kept empty.** Android Auto paints the artwork full-bleed and lays its
  title, subtitle and playback controls over it. Anything important there would be covered.
- **No ECU, no values.** Every value is gated on `ITorqueService.isConnectedToECU()`. Without
  a live ECU the metrics render as `-` and the description reads `No ECU - open Torque`. A bench
  therefore never looks like live data.
- The card follows the currently selected dashboard, in gauge order, up to five metrics.

## Data path

```
CardService (MediaBrowserService)
   └─ MediaSession, always PLAYING
        └─ CardFrames              last 64 published frames, keyed by frame id
             └─ CardArtworkProvider  content://com.aatorque.stats.cardart/f/<id>
                  └─ CardArtwork    the three styles
   ▲
   │ getPIDValuesAsDouble(), isConnectedToECU()
TorqueServiceWrapper
```

`CardService` polls on the same 300 ms tick as the dashboard. It publishes a frame only when the
text actually changes, so a static coolant temperature costs nothing.

A frame is published under an id, and the artwork URI carries that id. When Android Auto asks for
an older frame it gets exactly the pixels it asked for, not a newer frame that would make the
gauge jump.

## How fast is fast enough

Measured on a real phone with Android Auto 17.9 and the Desktop Head Unit 2.1: a 20 frame/s offer
was accepted at **17.94 frame/s** over 30 s, p50 gap 56 ms. A 10 frame/s offer was accepted at
9.51 frame/s over 55 s. No throttling was observed in that range.

The measurement used a separate probe app, not AA Torque. The probe served flat 512x512 PNGs and
logged every artwork fetch from a `ContentProvider`. Android Auto asks for a frame only when it
draws it, so that log is the accepted rate. The probe never drew the app's renderer, so the figure
is a limit of the Android Auto artwork pipeline, not a measurement of the gauge needles. Method
and further caveats are in the issue.

That headroom is above what needles need, which is why the dial styles are offered at all. A dial
style also pays an encode cost on the phone for each frame; see Known limits.

## How to check it without a car

These checks ran on `feat/media-card-artwork`, head `a7e0ec7`, which is closed pull request
[#62](https://github.com/YuhiAida/aa-torque/pull/62). That branch holds `CardService`,
`CardFrames`, `CardArtworkProvider`, `CardArtwork`, `CardPreviewActivity` and the card tests.
`master` does not: this document is the only file that survived the closure, so the commands
below do not run on this checkout.

| Check | Command | What it proves |
|---|---|---|
| Card pixels | `./gradlew verifyRoborazziDebug` | the three styles against the goldens committed on that branch |
| Card text and frames | `./gradlew testDebugUnitTest` | subtitle, ECU gating, frame ring |
| On the phone | Settings → Appearance → **Preview the card**, below **Android Auto card** | the real renderer at real size, no AA |

The preview screen calls the same `CardArtwork.render` the service calls. If it looks wrong there,
it will look wrong in the car.

## Known limits

- The measurement was taken with the card **full-bleed at 800x480**, not in the 31% rail at
  1024x600. The artwork pipeline is the same, but the rail itself has not been measured.
- The card renders a themed dial per update. That encode cost is on the phone, not Android Auto,
  and has not been profiled on the head unit.
- Only the selected dashboard is mirrored. There is no separate card configuration.
"""