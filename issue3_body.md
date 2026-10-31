**File:** `app/src/main/java/com/aatorque/stats/TorqueChart.kt`, in `notifyUpdate()` (line 166).

**Problem:** The chart value is computed as:

```kotlin
val scaled = (data.lastData / (data.display.maxValue - data.display.minValue)) * 100f
```

There is no check for `maxValue == minValue` (or a zero/negative range). When a gauge's configured range is zero (for example a PID the car does not answer, or a user-set equal min/max), `scaled` becomes `NaN` (0/0) or `Infinity` (nonzero/0). `LineGraphSeries.appendData()` does not handle `NaN`/`Infinity` cleanly, which can leave the chart series empty or crash the UI.

**Context:** `TorqueGauge.setMinMax()` already guards against an equal min/max (it bumps `max` by 1). `TorqueChart` does not, so the two code paths disagree about a legitimate edge case.

**Proposed fix:** guard the division, e.g.:

```kotlin
val range = data.display.maxValue - data.display.minValue
val scale = if (range == 0.0) 0f else (data.lastData / range) * 100f
```

**Impact:** chart data can vanish or the app can crash when rendering a zero-range gauge. Affects any user with a disabled/inactive PID that still appears on the dashboard.
