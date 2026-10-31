**Severity:** security (release build visibility/debugging)

**Location:** `app/build.gradle`, `buildTypes.release` (line 68).

**Problem:** The `release` build type sets `debuggable true`. This flag marks the app as debuggable on release builds, which (a) permits a debugger/JDWP attachment on a supposedly-production build, and (b) disables certain runtime optimizations. Release builds must be `debuggable false`.

The `debuggable true` there is almost certainly a copy-paste of the `release_debug` variant, which intentionally keeps debugging enabled. The `debug` build type also has `debuggable true` (correct, it is a debug variant), but the plain `release` build type should not.

**Proposed fix:** change `release { debuggable true` to `release { debuggable false`. Leave `release_debug { debuggable true` as is (it is an explicit debug-flavoured build).

**Impact:** Minor security hardening. No functional change to normal users; fixes a checklist/security-audit finding for a signed release build.
