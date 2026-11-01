# The Android Auto media card

Why the gauges are also a media app, and how that path works.

See [issue #60](https://github.com/YuhiAida/aa-torque/issues/60) for the decision and the
measurements.

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
9.51 frame/s over 55 s. No throttling was observed in that range. Method and caveats are in the
issue.

That is well above what needles need, which is why the dial styles are offered at all.

## How to check it without a car

| Check | Command | What it proves |
|---|---|---|
| Card pixels | `./gradlew verifyRoborazziDebug` | the three styles against committed goldens |
| Card text and frames | `./gradlew testDebugUnitTest` | subtitle, ECU gating, frame ring |
| On the phone | Settings → Android Auto card → Preview the card | the real renderer at real size, no AA |

The preview screen calls the same `CardArtwork.render` the service calls. If it looks wrong there,
it will look wrong in the car.

## Known limits

- The measurement was taken with the card **full-bleed at 800x480**, not in the 31% rail at
  1024x600. The artwork pipeline is the same, but the rail itself has not been measured.
- The card renders a themed dial per update. That encode cost is on the phone, not Android Auto,
  and has not been profiled on the head unit.
- Only the selected dashboard is mirrored. There is no separate card configuration.
