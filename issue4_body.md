**Files:**
- `app/src/main/java/com/aatorque/stats/TorqueChart.kt` (line 39: `private val series = HashMap<TorqueData, LineGraphSeries<DataPointInterface>>()`)
- `app/src/main/java/com/aatorque/stats/TorqueRefresher.kt` (line 23: `val data = HashMap<Int, TorqueData>()`)

**Problem:** `TorqueData` does not override `equals()` or `hashCode()`, so these `HashMap`s use **reference equality** as the key. Two `TorqueData` instances that are identical in content (same PID, same display settings) are treated as different keys. In practice the chart still updates because the same object reference is used as both key and lookup, but this is accidental: any code path that creates a *new* `TorqueData` with the same content (dashboard reset, re-parse, refactoring) will silently fail to match an existing entry, dropping chart updates or stale data.

**Proposed fix:** add `equals()`/`hashCode()` to `TorqueData` based on its meaningful identity (PID + display fields), and consider switching these HashMaps to `equals/hashCode`-consistent keys so lookups are by value.

**Impact:** High — latent and environment-dependent. Not a crash, but the chart/`TorqueRefresher` data model is not robust to object re-creation. Fixes a real correctness risk in the refresh/chart pipeline.
