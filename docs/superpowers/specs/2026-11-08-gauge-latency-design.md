# Gauge latency: faster needles, same sample rate

Status: approved, implemented on `fix/gauge-latency`.
Scope: small change. `stats/TorqueRefresher.kt`, `stats/TorqueGauge.kt`,
`docs/architecture.md`, plus one test.

## Intent

The gauges feel late. The needle does not start moving until well after the engine has
changed, and the gauge does not move as soon as the value arrives. The request is to make the
gauges react faster while the rest of the app keeps its slow, cheap cadence.

## What happened before

`TorqueRefresher.makeExecutors` gave every gauge its own scheduled task on a 7-thread pool:

* Task *i* started at `300/N * i` ms and repeated every 300 ms. Gauge *i* therefore sampled
  on its own phase, so no two gauges updated together and the newest value on screen was
  between 0 and 300 ms old.
* Each task called `getPIDValuesAsDouble(arrayOf(pid))` for its own PID, so a six-gauge
  dashboard made 20 Binder round-trips per second into Torque.
* Each result posted its own runnable to the main looper, so the gauges were applied on
  different frames, one after another.
* `TorqueGauge.onUpdate` then animated the needle for a full `REFRESH_INTERVAL` (300 ms) with
  `DecelerateInterpolator`. A new target arrived exactly when the animation would end, so the
  view animated the whole time and the needle never settled.

Torque's own documentation says `getPIDValuesAsDouble` returns the value that its adapter
loop already fetched, and that the request list — not the call rate — sets the adapter load.
So the stagger added latency and wakeups without adding data.

Summed up: sample phase (0-300 ms) + one frame per gauge + a 300 ms animation, roughly
450 ms of trail behind the engine.

## Goals

* Every gauge on the dashboard is read and applied in the same tick and the same frame.
* Visible reaction time drops by roughly 200-300 ms.
* No increase in Binder traffic, wakeups or drawing; ideally a decrease.

## Non-goals

* Changing Torque's OBD scan rate or the number of PIDs shown.
* Predicting future values from past samples.
* Per-gauge rate settings, or a new preference.
* Sharing one data cache between the Android Auto card and the dashboard.

## Approaches considered

**A. One batched tick, all gauges on the same phase.** *(chosen)*
One scheduled task per `REFRESH_INTERVAL` builds the PID array, makes one
`getPIDValuesAsDouble(ids)` call, distributes the values, and posts one runnable that
notifies every gauge. The stagger disappears, all needles move on the same frame, and N
Binder calls per tick become one. The needle animation is shortened through a named constant.

**B. Adaptive rate: 300 ms idle, ~100 ms while values move.**
Catches a transient sooner, but costs three times the Binder traffic during changes, needs
change-detection state per gauge, and is still capped by Torque's cache. Rejected: more code
for a smaller gain than A.

**C. Extrapolate the target from the last two samples.**
Best on ramps, overshoots on steps, and a wrong needle on a step change is worse than a late
one. Rejected.

## What was built

* `TorqueRefresher` keeps `REFRESH_INTERVAL = 300L` and gains a single-threaded scheduler and
  one task for the whole dashboard. The per-`TorqueData` `refreshTimer` stops being the
  scheduling mechanism; `stopExecutors` cancels the one task.
* `refreshDashboard` takes a snapshot of the active `TorqueData` entries, skips entries whose
  `pid` is null, and calls Torque once with the whole array. Every gauge then gets one
  `sendNotifyUpdate` from a single `handler.post`.
* The first read also moves off the main thread: the task is scheduled with a 0 ms delay
  instead of being called inline from `makeExecutors`, which used to block the caller with one
  Binder call per gauge.
* If Torque returns fewer values than requested, the tick falls back to one call per PID for
  that tick and logs a warning. Order is assumed to match the request, as the AIDL documents.
* `applyValues` keeps the rule from issue #158: every successful read is published, including
  `0.0`, and only a non-zero read arms the alarm gate.
* A guard around the scheduled body stops an exception from cancelling the task for good.
  `data` is guarded with its own monitor, so a dashboard edit on the main thread cannot
  corrupt the tick's snapshot.
* `TorqueGauge.onUpdate` uses `NEEDLE_ANIM_MS = 200L`, a new constant in `TorqueRefresher`,
  instead of the sample interval. 200 ms removes most of the creeping tail of the decelerate
  curve while keeping a small hold before the next sample, which avoids a visibly stepped
  needle on ramps. This is the one value to tune on the bench.

## Verification

* `BatchedRefreshTest` drives the tick with a dynamic-proxy fake of `ITorqueService`: one call
  carries both PIDs, each value lands on its own gauge, a `0.0` reading still reaches the
  gauge, and a short result falls back to one call per gauge.
* `ZeroValueTest` still pins the per-PID path used by that fallback.
* CI: `ktlintCheck`, `verifyRoborazziDebug`, `lintRelease`. This machine has no Android SDK,
  so CI is the only compiler here.
* Bench: install the debug APK on aa36, run the dashboard, and watch the debug log. Real
  latency measurement needs the DHU and a screen recording, which is out of scope.

## Follow-up

The Android Auto card polls Torque on its own 300 ms loop in the unmerged card branch. The
same batching, and a slower interval because the card is only text, belong to that pull
request, not this one.
