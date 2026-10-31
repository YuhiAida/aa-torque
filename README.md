![CI Status](https://github.com/YuhiAida/aa-torque/actions/workflows/ci.yml/badge.svg?branch=master)
![downloads](https://img.shields.io/github/downloads/YuhiAida/aa-torque/total.svg)
![license](https://img.shields.io/github/license/YuhiAida/aa-torque)
[![support](https://img.shields.io/badge/support-tip-yellow?logo=revolut)](https://revolut.me/bernarfhfo)

# Torque Plugin for Android Auto

AA Torque shows the data from [Torque Pro](https://play.google.com/store/apps/details?id=org.prowl.torque)
as gauges, charts and a speedometer on your Android Auto head unit. You need Torque Pro and
a cheap OBD-II adapter. The app reads every PID Torque exposes, including custom ones.

This repository is a maintained fork of [agronick/aa-torque](https://github.com/agronick/aa-torque).
Upstream is unmaintained. The fork keeps the package name `com.aatorque.stats`, so it
**replaces** the upstream build in place. You cannot install both.

- [Features](#features)
- [Install](#install)
- [The app does not show up in Android Auto](#the-app-does-not-show-up-in-android-auto)
- [Use](#use)
- [Build from source](#build-from-source)
- [Help](#help)
- [Credits](#credits)

## Features

- Show any Torque PID, including custom PIDs, on the Android Auto screen
- Up to 10 dashboards, many themes, fonts, icons and backgrounds
- Rotary dial support
- Custom expressions through [EvalEx](https://ezylang.github.io/EvalEx/references/functions.html)
- Backup, restore and share your configuration

<img src="https://github.com/agronick/aa-torque/assets/2042303/e31b0598-25ec-4003-bcee-11e5e108d3a4" width="100%" alt="Gauges" />

## Install

1. Install **Torque Pro** on your phone and pair your OBD-II adapter in Torque.
2. Download the newest published APK from [Releases](https://github.com/YuhiAida/aa-torque/releases).
3. Install the APK with your normal package installer. Android may warn about an unknown
   source. Allow it.
4. Continue with the next section. A plain install does **not** make the app visible in
   Android Auto.

Do not rely on the "Unknown sources" switch in the Android Auto developer options. It does
not admit this app. The reason is in [docs/aa-install-gate.md](docs/aa-install-gate.md).

## The app does not show up in Android Auto

Android Auto decides which apps it accepts from the **install record**, not from the
developer options. For this app, both fields must read:

| Field | Required value |
|---|---|
| `initiatingPackageName` | `com.google.android.packageinstaller` |
| `installerPackageName` | `com.android.vending` |

`adb install` and most file managers set one field and not the other. Kingstaller's normal
mode has the same problem. Use **Kingstaller with Shizuku** instead.

### Repair the install record

1. Install [KingInstaller](https://github.com/fcaronte/KingInstaller/releases).
2. Install [Shizuku](https://shizuku.rikka.app/) from the Play Store.
3. Start Shizuku and grant the permission it asks for. Wireless debugging is the usual
   method.
4. In KingInstaller, choose **Shizuku** as the installation method, select the AA Torque
   APK, and install. Success reads `App installed successfully! (AA Compatible)`.
5. Force-stop Android Auto so it rebuilds its list:
   `adb shell am force-stop com.google.android.projection.gearhead`
6. Open the Android Auto settings on the phone, then look for AA Torque under
   **Add a shortcut to the Launcher**. If it is there, the install record is correct. You
   do not need the car for this check.

To read the two fields yourself:

```
adb shell dumpsys package com.aatorque.stats | grep -E "installerPackageName|initiatingPackageName"
```

### Phone notes

- **OnePlus, OPPO, Realme:** enable **Disable permission monitoring** in the developer
  options, at the bottom of the Apps section. Without it Shizuku starts but reports that
  its ADB permission is limited, and the install silently falls back to the wrong record.
  The same switch over adb: `settings put global direct_control_permission_monitoring 0`.
- **Pixel:** reinstall or update the Google PackageInstaller as described in the
  KingInstaller README, then install through KingInstaller.
- **Android 14 and newer:** uninstall the old version first when an earlier build worked.
  An AAWireless dongle with developer mode enabled is a known workaround.

### After an update

Any update installed by another path overwrites the install record, and the app disappears
from Android Auto again. Always update through KingInstaller with Shizuku. The same applies
when you move from the upstream build to this fork.

AA Torque checks for a newer release when you open its settings and offers a link to the release
page. It does not install the APK itself — install it with KingInstaller, as above.

## Use

1. Open **AA Torque Settings** on the phone and grant every permission it asks for. The app
   does not work without them.
2. Connect the phone to the car and start Android Auto.
3. Open the app drawer in Android Auto. AA Torque sits there with its own icon.

The status line in the settings screen tells you what the app sees:

- `Torque bound, no adapter` — the app talks to Torque, but Torque has no OBD-II adapter.
  No car data flows yet.
- `ECU live - N PIDs` — the adapter answers queries and the gauges show real values.

A gauge that stays at zero usually binds a PID your car does not answer. See
[docs/pid-filtering.md](docs/pid-filtering.md).

## Build from source

The build needs a JDK (19 or newer), an Android SDK and the `speedviewlib` submodule. The
full steps, including the two common traps, are in [docs/building.md](docs/building.md).

```
git submodule update --init --recursive
./gradlew assembleDebug
```

Every APK in Releases is built and signed by CI with the same debug key, restored from the
`DEBUG_KEYSTORE` secret, so releases install over each other. Releases up to 2.0.33 were signed
with a per-run generated key and cannot update in place — uninstall once, then install 2.0.34 or
later normally.

## Help

- **Something is broken:** open an [issue](https://github.com/YuhiAida/aa-torque/issues).
- **Install problems:** read [docs/aa-install-gate.md](docs/aa-install-gate.md) and
  [docs/troubleshooting.md](docs/troubleshooting.md) first.
- **Working on the app:** [docs/](docs/) for reference, [CONTRIBUTING.md](CONTRIBUTING.md)
  for themes and translations, [AGENTS.md](AGENTS.md) for coding agents.
- **Support:** this fork is maintained in my spare time. If it saves you a headache, you can
  [help cover the AI tokens behind it](https://revolut.me/bernarfhfo). No obligation, ever.

## Credits

AA Torque derives from [Chillout's Performance Monitor](https://github.com/jilleb/mqb-pm),
which derives from Martoreto's aa-stats. The original app targeted VAG cars. This fork keeps
the "any PID" approach. Licensed under GPL-3.0, see [LICENSE.md](LICENSE.md).
