# Install FAQ

## Read this first

AA Torque shows Torque Pro data as gauges on Android Auto. You need four things:
Torque Pro, an OBD-II adapter paired in Torque, the APK from Releases, and
Shizuku plus KingInstaller. The one fact that causes most failures: Android
Auto accepts the app from its **install record**, not from the "Unknown sources"
developer toggle. That toggle admits media, navigation and similar apps. It
excludes projection apps, and AA Torque is a projection app, so the toggle does
nothing. Both install-record fields must read `com.google.android.packageinstaller`
and `com.android.vending`. A plain `adb install` or a normal tap install sets
only one field. Use KingInstaller with Shizuku instead.

Android Auto classifies apps by category. The "Unknown sources" setting admits
MEDIA, NAVIGATION, NOTIFICATION, SMS, SERVICE and OEM apps. It excludes the
PROJECTION category. AA Torque registers through
com.google.android.gms.car.category.CATEGORY_PROJECTION, so it is a projection
app and the setting cannot admit it. Toggling it has no effect on this app.

For a projection app, Android Auto inspects how the package was installed. The
two fields are `initiatingPackageName` and `installerPackageName`. Both must be
right. One right and the other wrong is the normal failure:

- `adb install`, and some file managers, set the installer field and leave the
  initiator as `com.android.shell`. Android Auto rejects the app.
- KingInstaller in its plain mode does the opposite: it fixes the initiator and
  leaves the installer.

`pm set-installer` cannot repair the record from adb. Android 13 and newer raise
a `SecurityException`. KingInstaller with Shizuku is the supported repair.

## Symptom, cause, fix

| Symptom | Cause | Fix |
|---|---|---|
| App is not in the Android Auto launcher | The install record is wrong | Repair it with KingInstaller and Shizuku; see [aa-install-gate.md](aa-install-gate.md) |
| The "Unknown sources" toggle did nothing | The toggle does not admit projection apps | Repair the install record; the toggle cannot help this app |
| The APK installs but Android Auto says it is not compatible | The install record is wrong, or the signing key differs | Reinstall through KingInstaller with Shizuku; uninstall first on a signature mismatch |
| `adb install` works but the app is invisible in Android Auto | `adb` sets the installer field but leaves the initiator as `com.android.shell` | Reinstall through KingInstaller with Shizuku |
| Gauges show `-` or "No ECU" | Torque has no adapter, or the gauge binds a PID the car does not answer | Pair the adapter in Torque; see [pid-filtering.md](pid-filtering.md) |
| App disappeared from Android Auto after an update | The update overwrote the install record | Reinstall through KingInstaller with Shizuku |
| Update refuses to install with a signature mismatch | An upstream build or an older fork build uses a different key | Uninstall, then install again through KingInstaller |
| Settings, dashboards or themes lost after an update | The update was an uninstall plus reinstall, or the signing key changed | Keep the same key and update in place; back up the config first |
| Pixel: install fails or the record stays wrong | The Google PackageInstaller is out of date | Reinstall or update Google PackageInstaller, then install through KingInstaller |
| Zlink or an aftermarket head unit behaves differently | Some units keep their own allow list | Enable AAWireless developer mode as a workaround; check the unit's settings |

## How the record is set

The installer field is not sticky. An update rewritten by another path clears it.
That is why every update must go through KingInstaller. Measured on Android 16:

| Install path | installerPackageName | initiatingPackageName |
|---|---|---|
| `adb install -i com.android.vending` | `com.android.vending` | `com.android.shell` |
| Tap install through the system installer | `com.google.android.packageinstaller` | `com.google.android.packageinstaller` |
| Update without `-i` | `null` | `com.android.shell` |
| Update with `-i com.android.vending` | `com.android.vending` | `com.android.shell` |

The system installer drops `EXTRA_INSTALLER_PACKAGE_NAME` unless the calling app
is the installer itself or holds `INSTALL_PACKAGES`. An ordinary app cannot ask
for `com.android.vending`. Only KingInstaller with Shizuku sets both fields.

## Clean install

1. Install **Torque Pro** on the phone and pair your OBD-II adapter in Torque.
2. Download the newest APK from [Releases](https://github.com/YuhiAida/aa-torque/releases).
3. Install [KingInstaller](https://github.com/fcaronte/KingInstaller/releases) and
   [Shizuku](https://shizuku.rikka.app/) from the Play Store.
4. On OnePlus, OPPO or Realme, enable **Disable permission monitoring** in the
   developer options, at the bottom of the Apps section. The same switch over adb:
   `settings put global direct_control_permission_monitoring 0`
5. Start Shizuku with wireless debugging and grant the permission prompt.
6. In KingInstaller choose **Shizuku** as the installation method, select the APK,
   and install. Success reads `App installed successfully! (AA Compatible)`.
7. Force-stop Android Auto so it rebuilds its list:
   `adb shell am force-stop com.google.android.projection.gearhead`
8. Open the Android Auto settings on the phone:
   `adb shell am start -a android.settings.ANDROID_AUTO_SETTINGS`
   Look for AA Torque under **Add a shortcut to the Launcher**. Its presence proves
   the install record is correct. No car is needed for this check.
9. Read the two fields yourself to confirm:
   `adb shell dumpsys package com.aatorque.stats | grep -E "installerPackageName|initiatingPackageName"`

## Update an existing install

Updates keep your settings, dashboards and themes. The APK is signed with the
same key as every release, so a newer version installs over the old one without
uninstalling.

1. Download the newer APK from the release page. The in-app update check in
   Settings only links to that page; it does not install the APK itself.
2. Install it through KingInstaller with Shizuku, exactly as in the clean install.
   Any other install path overwrites the install record and the app vanishes from
   Android Auto again.
3. Force-stop Android Auto:
   `adb shell am force-stop com.google.android.projection.gearhead`

Two limits apply. Builds up to version 2.0.33 were signed with a per-run key and
cannot update in place: uninstall once, then install version 2.0.34 or later
normally. Moving from the upstream build to this fork also replaces the app,
because both use the package `com.aatorque.stats`: install the fork through
KingInstaller, not by tapping the APK.

## What it cannot do

- It takes the whole Android Auto screen. It cannot sit beside Maps or music in
  one app. Choosing AA Torque hides Maps and music; choosing Maps hides the
  gauges. A split view needs two apps and was rejected for this app.
- It needs Torque Pro.
- It is not on the Play Store. Google Play does not accept this app category, so
  sideloading through KingInstaller is the supported install path.
- It does not work where the phone blocks sideloading. Wireless Android Auto
  runs on the phone, so a phone that refuses unknown sources cannot set the
  install record.

## When it still does not appear

- Check both fields again. A phone update, or an app that reinstalls packages, can
  reset the record.
- Check that Android Auto itself is updated. Very old Android Auto versions ignore
  the install record entirely.
- Check the app is not disabled for Android Auto on the head unit. Some OEM units
  keep their own allow list.
- AAWireless users: enabling developer mode in AAWireless is a known workaround
  for phones that refuse the record.

## Where to get help

Open an issue at https://github.com/YuhiAida/aa-torque/issues. Include the
Android Auto version, the phone model, and the two install-record fields from:

`adb shell dumpsys package com.aatorque.stats | grep -E "installerPackageName|initiatingPackageName"`

Also read [troubleshooting.md](troubleshooting.md) and [aa-install-gate.md](aa-install-gate.md)
before you file. For install problems, read [building.md](building.md) only if you
build from source. For gauge problems, read [pid-filtering.md](pid-filtering.md).

If the app crashes, a crash dump lands in the app's external files directory, in a
subdirectory named crashes, or in the private files/crashes directory when
external storage is not mounted. Pull it with:

`adb pull /sdcard/Android/data/com.aatorque.stats/files/crashes`

The newest 10 dumps are kept. Settings -> Copy logs to clipboard gives the live
log tail for a kill by the system that has no dump.