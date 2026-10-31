# Troubleshooting

Find the symptom, then follow the cause. Two of these have their own files.

## Symptom to cause

| Symptom | Cause | Fix |
|---|---|---|
| App is not in the Android Auto launcher | Install record is wrong | [aa-install-gate.md](aa-install-gate.md) |
| No update prompt in Settings | No newer published release, the 24 h throttle, or a network failure | Check the newest release tag against the version in the app; the check is silent on failure by design |
| App disappeared from Android Auto after an update | The update overwrote the install record | Reinstall through KingInstaller with Shizuku |
| Gauge stays at `0`, other gauges work | The gauge binds a PID the car does not answer | [pid-filtering.md](pid-filtering.md) |
| Status line: `Torque bound, no adapter` | Torque has no OBD-II adapter connected | Pair the adapter in Torque |
| Torque is not detected at all | Torque not installed, or never opened once | Install Torque Pro and open it, then start AA Torque |
| Debug build shows values with no adapter | Debug builds set `SIMULATE_METRICS=true` | Judge the binding with a release build |
| Settings refuse to save | Storage permission denied | Grant all permissions the settings screen asks for |
| Theme changes do not apply | The car activity recreates only on theme change | Reopen the app; if it persists, check `selectedTheme` in preferences |
| Night mode is wrong | Theme and Android Auto day/night disagree | Check the `values-night` resources for the theme |

## Torque is not detected

AA Torque binds Torque's service, which starts Torque's background service. On Android 12
and newer a bound service that promotes itself to a foreground service is killed when the
app is in the background. Open Torque once before you start AA Torque.

If the settings screen keeps asking you to open Torque, do exactly that: open Torque, leave
it running, return to AA Torque.

## Reading logs

Timber writes to Logcat. Filter by the app:

```
adb logcat | grep -iE "aatorque|torque"
```

Useful signals:

- `Torque bound` — the AIDL binding is up.
- `CAT line ... registry=NNN` — the PID catalogue arrived with `NNN` entries.
- Exceptions around `bindService` — Torque is missing or was killed.

Crash reports go through ACRA, which offers a mail or toast prompt depending on the build.
ACRA is the only component that sends data off the phone.

## Install fails

- **Package already exists / signature mismatch:** an upstream build or an older fork build
  is installed with a different key. Uninstall it, then install again through KingInstaller.
- **Parse error:** the APK is truncated. Download it again from the release page.
- **Blocked by Play Protect:** allow the install, then reinstall through KingInstaller.

## Build fails

| Error | Meaning |
|---|---|
| `SDK location not found` | No `sdk.dir` in `local.properties` and no `ANDROID_HOME` |
| `Project with path ':speedviewlib' could not be found` | Submodule not initialised; see [building.md](building.md) |
| `Permission denied (publickey)` during submodule init | `.gitmodules` uses SSH; switch that submodule to HTTPS |
| Dependency resolution fails on `jcenter()` | `jcenter` is shut down; some transitive artifacts resolve only from `mavenCentral` or `google()` |

## Android Auto version matters

Very old Android Auto builds ignore the install record and use an internal allow list. If the
phone runs an Android Auto older than 2023, update it before you debug anything else.
