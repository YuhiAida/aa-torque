# Architecture

How data gets from the car to the head unit. Keep this file short and path-based: name the
files, do not copy the code. The machine-written file list is
[generated/inventory.md](generated/inventory.md).

## Where the app lives

AA Torque is a single Android module, `app/`, with application id `com.aatorque.stats`. It
runs as an Android Auto **projection** app: Android Auto starts a service in the app and the
app draws inside the Android Auto surface. Nothing is rendered by the car.

Sources are split into four packages under `app/src/main/java/com/aatorque/`:

Path convention: every source path in this file is relative to
`app/src/main/java/com/aatorque/` unless it starts with `app/`.

| Package | Responsibility |
|---|---|
| `stats/` | Android Auto entry, Torque binding, refresh loop, gauges and charts, dashboard assembly. |
| `prefs/` | Settings screens, the DataStore/Protobuf preference store, the settings view model. |
| `ui/theme/` | Compose theme used by the settings screens. |
| `utils/` | Small helpers: animation, countdown latch, lifecycle-aware observer. |

`app/src/main/java/org/prowl/torque/remote/` is not ours. It mirrors Torque's AIDL
interface (`app/src/main/aidl/org/prowl/torque/remote/ITorqueService.aidl`) so the app can
bind to Torque. Treat it as third-party code.

## The data path

```
CarService (CarActivityService)
   └─ MainCarActivity (CarActivity)          projection surface + menu + theme
        └─ DashboardFragment (FRAGMENT_CAR)  builds one dashboard from preferences
             └─ TorqueGauge / TorqueChart / TorqueSpeedometer   custom views
                  ▲
                  │ LiveData
             TorqueData                      formats one value, evaluates expressions
                  ▲
                  │ cache per gauge, 300 ms tick
             TorqueRefresher                 ScheduledThreadPoolExecutor, ConnectStatus flow
                  ▲
                  │ AIDL
             TorqueServiceWrapper (Service)  binds org.prowl.torque.remote.ITorqueService
                  ▲
             Torque Pro ── OBD-II adapter ── car
```

The media card is a second, independent read path. It cannot go through the dashboard
fragments, because Android Auto asks for it while no `CarActivity` exists:

```
CardService (MediaBrowserService)
   └─ MediaSession, always PLAYING          no audio, no foreground service
        └─ CardFrames                       last 64 published frames, by frame id
             └─ CardArtworkProvider         content://com.aatorque.stats.cardart/f/<id>
                  └─ CardArtwork            numbers only, one dial, two dials
   ▲
   │ getPIDValuesAsDouble(), isConnectedToECU()
TorqueServiceWrapper
```

Details, measurements and the routes that were rejected: [aa-media-card.md](aa-media-card.md).

Read it in this order:

1. `stats/CarService.java` — one method. It returns `MainCarActivity` to the Android Auto
   SDK. If the app never appears in the car, this is not the file to fix; see
   [aa-install-gate.md](aa-install-gate.md).
2. `stats/MainCarActivity.kt` — creates the fragments, switches between dashboard,
   stopwatch and credits, reads the selected theme from the preference store and recreates
   itself when the theme changes. Key presses are rebroadcast over `LocalBroadcastManager`.
3. `stats/DashboardFragment.kt` — reads the selected dashboard from the preference store and
   inflates one view per gauge. It owns the gesture handling, the album-art background and
   the status bar title.
4. `stats/TorqueRefresher.kt` — the clock of the app. A `ScheduledThreadPoolExecutor` with
   7 threads ticks every 300 ms, asks Torque for the values of the subscribed gauges, and
   publishes `ConnectStatus` (`CONNECTING_TORQUE`, `CONNECTING_ECU`, `CONNECTED`,
   `SETUP_GAUGE`) as a flow. It keeps a cache per dashboard so a switch does not blank the
   screen.
5. `stats/TorqueData.kt` — one instance per gauge. It formats the value against the unit,
   precision and min/max from the preferences, evaluates custom EvalEx expressions, and
   exposes the result as `MutableLiveData`.
6. `stats/TorqueServiceWrapper.kt` — the binding. It starts Torque, binds
   `ITorqueService`, retries on disconnect, and loads the PID list once (`ListPids`).
   `stats/TorqueService.kt` is the small helper that owns the connection callbacks;
   do not confuse the two.
7. `stats/TorqueGauge.kt`, `TorqueChart.kt`, `TorqueSpeedometer.kt`, `TorqueDisplay.kt` —
   the drawing. `ViewAdapter.kt` maps a gauge type to its view.
8. `stats/CardService.kt` — the Android Auto media card. A `MediaBrowserService` with a session
   that is always playing, publishing the dashboard's first five gauges as card artwork. It polls
   on the same 300 ms tick and gates every value on `isConnectedToECU`. See
   [aa-media-card.md](aa-media-card.md).

Supporting pieces:

- `stats/NotiService.kt` — a `NotificationListenerService`. It supplies media metadata for
  the album-art background.
- `stats/AlbumArt.kt` — the background artwork.
- `stats/App.kt` — `Application`. Installs Timber, ACRA crash reporting and the Android 14
  permission fix.
- `stats/CacheLogTree.kt` — Timber tree that keeps recent log lines.

## Preferences

Preferences are a Protobuf message, not key-value XML.

- Schema: `app/src/main/proto/user_prefs.proto`, generated into `com.aatorque.datastore`
  (`UserPreference`, `Screen`, `Display`, `Coloring`, `Operation`).
- Store: `prefs/PrefStore.kt` — a DataStore with a Protobuf serializer, plus
  `DEFAULT_SETTINGS`, a text-format default dashboard that seeds a fresh install.
- Consumers: `prefs/SettingsViewModel.kt` exposes flows; `MainCarActivity`,
  `DashboardFragment` and the settings screens collect them.

A preference change does not restart the app. The car activity observes the theme and
recreates, and the fragments observe the selected screen.

## Threading

- The main thread only draws. `TorqueRefresher` ticks on its own executor and posts results
  back through a `Handler(Looper.getMainLooper())`.
- `TorqueServiceWrapper` guards the binding with a `ReentrantLock`.
- `connectStatus` is a `StateFlow`; gauge values are `LiveData` observed from fragments.

## Where to change what

| Change | File |
|---|---|
| Add a gauge type or drawing style | `stats/TorqueGauge.kt`, `ViewAdapter.kt` |
| Change the refresh rate | `stats/TorqueRefresher.kt` (`REFRESH_INTERVAL`) |
| Change which PIDs are offered | `stats/TorqueServiceWrapper.kt` — read [pid-filtering.md](pid-filtering.md) first |
| Change the status line text | `prefs/SettingsFragment.kt` and the `ConnectStatus` mapping |
| Add a setting | `proto/user_prefs.proto`, `PrefStore.kt`, `SettingsFragment.kt` |
| Change the default dashboard | `PrefStore.kt` (`DEFAULT_SETTINGS`) |
| Change the Android Auto entry point | `stats/CarService.java`, `AndroidManifest.xml` |
| Change crash reporting | `stats/App.kt` |

## Rules that keep this file true

A change that moves a data path, adds a module or renames a subsystem updates this file in
the same pull request. Each link and path above must resolve; a stale path is a bug in the
docs.
