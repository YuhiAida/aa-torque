# Why the app does not appear in Android Auto

Symptom: the APK is installed, permissions are granted, the car is connected, and AA Torque
is not in the Android Auto launcher. The "Unknown sources" developer setting changes nothing.

This is not an app bug. Android Auto refuses the app because of its **install record**.

## Why "Unknown sources" is not enough

Android Auto classifies apps internally. The developer setting **Unknown sources** admits
MEDIA, NAVIGATION, NOTIFICATION, SMS, SERVICE and OEM apps. It excludes the **PROJECTION**
category, and AA Torque is a projection app (it draws its own surface, see
`app/src/main/java/com/aatorque/stats/CarService.java`). Toggling the setting therefore has no
effect on this app.

For a projection app, Android Auto inspects how the package was installed:

| Field | Required value |
|---|---|
| `initiatingPackageName` | `com.google.android.packageinstaller` (Requested by Package Installer) |
| `installerPackageName` | `com.android.vending` (Installed by Play Store) |

One field right and the other wrong is the normal failure:

- `adb install`, and some file managers, set `installerPackageName` and leave the initiator
  as `com.android.shell`. Android Auto rejects the app.
- KingInstaller in its plain mode does the opposite: it fixes the initiator and leaves the
  installer.

`pm set-installer` cannot repair the record from adb; Android 13 and newer raise a
`SecurityException`.

Read the two fields:

```
adb shell dumpsys package com.aatorque.stats | grep -E "installerPackageName|initiatingPackageName"
```

## Working recipe, no root

1. Install [KingInstaller](https://github.com/fcaronte/KingInstaller/releases).
2. Install [Shizuku](https://shizuku.rikka.app/) from the Play Store.
3. **OnePlus, OPPO, Realme only:** developer options, bottom of the Apps section, enable
   **Disable permission monitoring**. Without it Shizuku starts but reports that its ADB
   permission is limited, and privileged installs silently fall back to the wrong record.
   The same switch over adb:
   `settings put global direct_control_permission_monitoring 0`.
   If the row is missing, set the phone language to English (US), enable it, then switch
   back.
4. Start Shizuku with the wireless-debugging method and grant the permission prompt.
5. In KingInstaller choose **Shizuku** as the installation method, select the APK and
   install. It reports `App installed successfully! (AA Compatible)` when the record is
   right.
6. Force-stop Android Auto so it rebuilds its cache:
   `adb shell am force-stop com.google.android.projection.gearhead`
7. Open the Android Auto settings on the phone
   (`adb shell am start -a android.settings.ANDROID_AUTO_SETTINGS`) and look for AA Torque
   under **Add a shortcut to the Launcher**. Its presence is the proof. No car is needed.

## Maintenance rule

Any update installed by another path overwrites the install record, and the app disappears
from Android Auto again. Always update through KingInstaller with Shizuku.

This also applies when you move from the upstream build to this fork: both use the package
name `com.aatorque.stats`, so the fork replaces upstream and the record is rewritten. Install
the fork through KingInstaller, not by tapping the APK.

## When it still does not appear

- Check both fields again. A phone update, or an app that reinstalls packages, can reset the
  record.
- Check that Android Auto itself is updated. Very old Android Auto versions ignore the
  install record entirely.
- Check the app is not disabled for Android Auto on the head unit. Some OEM units keep their
  own allow list.
- AAWireless users: enabling developer mode in AAWireless is a known workaround for phones
  that refuse the record.

Verified October 2026 on a OnePlus CPH2551, Android 16 / OxygenOS 16, Android Auto
17.9.664044, with AA Torque 2.0.30.
