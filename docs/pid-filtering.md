# Which PIDs the app offers

Torque knows hundreds of PIDs. Your car answers a small subset of them. The app must offer
only what the car answers, otherwise a user can bind a gauge to a value that never moves.

## The two lists are not the same

- `ITorqueService.listAllPIDs()` returns Torque's **dictionary**: every PID definition
  Torque ships, whether or not the connected car implements it. On a bench without an
  adapter it still returns the full set, around 200 entries.
- `ITorqueService.listECUSupportedPIDs()` returns the PIDs the **ECU answered** during the
  current session. Empty when no adapter is connected.
- `ITorqueService.isConnectedToECU()` tells you whether an adapter answers queries at all.

A gauge bound to a dictionary PID that the car does not implement reads `0` forever. No UI
warns. Voltage and phone-GPS gauges keep working, so the failure looks like a broken gauge,
not a broken binding.

## The rule

User-facing PID lists come from the supported set, plus Torque's own synthetic metrics:

```
pids = listAllPIDs() filtered by ( supported.contains(id) || id.startsWith("ff") )
```

- `ff*` ids are Torque's calculated metrics — GPS speed, acceleration, power. They never
  come from the ECU, so they are absent from `listECUSupportedPIDs()` and must be kept.
- When the supported set is **empty** — no adapter, or Torque not ready — the app falls back
  to the full list. This lets a user configure dashboards off-car.
- An exception from `listECUSupportedPIDs()` is treated as an empty set, not as a crash.

The implementation is `ListPids` in `app/src/main/java/com/aatorque/stats/TorqueServiceWrapper.kt`.

## Why this matters for reviews

`listAllPIDs()` alone is the obvious call and it looks correct on a desk emulator, because
Torque there reports a full dictionary. It breaks on a real car with a sparse PID set. Do
not "simplify" the filter back to a single call.

## Symptoms and what they mean

| Symptom | Meaning |
|---|---|
| Gauge reads `0`, other gauges work | The gauge binds a PID this car does not answer. Reconfigure it against the supported list. |
| Status line reads `Torque bound, no adapter` | Torque has no adapter. Values will not move, whatever the PID. |
| Status line reads `ECU live - N PIDs` | Adapter answers. `N` is the supported set size. Small `N` is normal for older cars. |
| PID picker shows very few entries | Correct behaviour when an adapter is connected to a car with a small PID set. |

## PID id format

Torque ids look like `05,0`, `0c,0`, `11,0` — a hex PID and an instance index. A hardcoded
list of bare numbers (`5,12,13`) resolves to nothing. Store and compare the full string.

The app prefixes ids with `torque_` when it stores them in preferences (`TorqueData.PREFIX`),
so a preference value reads `torque_0c,0`.
