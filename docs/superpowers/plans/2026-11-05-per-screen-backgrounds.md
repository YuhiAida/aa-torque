# Per-dashboard background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each dashboard screen can use a bundled drawable or a photo from the phone as its
background, and inherit the app-wide choice when it sets none.

**Architecture:** A `Screen` proto field holds one prefixed string (`resource:` or `file:`).
A pure parser turns it into a source. A small file store copies, downsamples and lists
images in private storage. `DashboardFragment` maps the source to the existing `ImageView`,
keeping album art on top. A custom preference renders the picker list, including stored
images and a "Choose image…" row.

**Tech Stack:** Kotlin, Protobuf DataStore, AndroidX Preference, Robolectric JVM tests,
ktlint, Roborazzi goldens.

**Spec:** docs/superpowers/specs/2026-11-05-per-screen-backgrounds-design.md

## Global Constraints

- minSdk 28, targetSdk 34, compileSdk 34, Java 19.
- No new dependency.
- No image decode and no file IO on the main thread.
- Stored image: long edge at most 2048 px, JPEG quality 90, thumbnail 128 px.
- Value format: empty = inherit, `resource:<drawable>`, `file:<name>`. Unknown input = inherit.
- Export and import stay a raw proto. Never change `SettingsActivity`.
- Precedence: album art, screen background, app-wide background, `background_incar_black`.
- Smallest diff. ktlint style. No reformatting of untouched code.
- Build offline: `./gradlew --offline ...`. The SDK is at /home/yuhi/Android/Sdk.

## Review Focus

- A `file:` value whose file is missing (deleted, imported from another phone) must render
  the app-wide background, not a blank or a crash.
- A corrupt or non-image file picked from a document provider must leave the setting
  unchanged and show no crash.
- A 50 MP photo must not blow the head unit's memory; only the downsampled copy is read.
- Switching dashboards quickly must not show the previous screen's background.
- Two screens may reference the same file; the sweep must never delete a referenced file.

---

### Task 1: Background source parser

**Files:**
- Create: `app/src/main/java/com/aatorque/prefs/BackgroundSource.kt`
- Test: `app/src/test/java/com/aatorque/prefs/BackgroundSourceTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface BackgroundSource { data object Inherit; data class Drawable(val name: String); data class Stored(val name: String) }`
  - `fun parseBackground(value: String?): BackgroundSource`
  - `fun encodeDrawable(name: String): String` → `resource:<name>`
  - `fun encodeFile(name: String): String` → `file:<name>`

- [ ] **Step 1: Write the failing test**

`BackgroundSourceTest.kt` covers: empty string, null, `resource:background_incar_vw`,
`file:bg-1a2b.jpg`, `resource:` with no name, `blah:x`, a value with a leading space, and
both encoders. Assert `parseBackground("") == Inherit`, and every malformed input returns
`Inherit`.

- [ ] **Step 2: Run it, expect a compile failure**

Run: `./gradlew --offline testDebugUnitTest --tests "com.aatorque.prefs.BackgroundSourceTest"`

- [ ] **Step 3: Implement the parser**

Split on the first `:`. A missing separator, an empty name, or an unknown prefix returns
`Inherit`. Do not throw.

- [ ] **Step 4: Run the test, expect PASS**

- [ ] **Step 5: Commit**

`feat(prefs): parse a background source string`

---

### Task 2: Image store

**Files:**
- Create: `app/src/main/java/com/aatorque/prefs/BackgroundStore.kt`
- Test: `app/src/test/java/com/aatorque/prefs/BackgroundStoreTest.kt`

**Interfaces:**
- Produces:
  - `object BackgroundStore`
  - `fun dir(context: Context): File`
  - `fun list(context: Context): List<String>` — file names ending in `.jpg`, newest first
  - `fun sweep(context: Context, referenced: Set<String>): Int` — deletes unreferenced `.jpg`
  - `fun newName(): String` — `bg-<8 hex>.jpg`
  - `fun import(context: Context, uri: Uri, maxEdge: Int = 2048): String?` — stores the
    image and its thumbnail, returns the file name, or null when the stream is not an image
  - `fun resolve(context: Context, name: String, reqHeight: Int): Bitmap?`
  - `fun thumbnail(context: Context, name: String): Bitmap?`

**Notes for the implementer:** `import` decodes bounds first, then decodes with
`inSampleSize` so the long edge is at most `maxEdge`, writes JPEG 90, and writes
`<name>.thumb.jpg` at 128 px. It runs on the caller's IO dispatcher. `sweep` ignores the
thumbnail suffix when computing the referenced set, and never deletes a file in
`referenced`.

- [ ] **Step 1: Write the failing test**

`BackgroundStoreTest` (Robolectric) covers: `newName()` matches `bg-[0-9a-f]{8}\.jpg`;
`sweep` deletes only unreferenced images and keeps referenced ones; `list` returns newest
first; `resolve` of a missing name returns null. Use
`ApplicationProvider.getApplicationContext<Context>()` and write dummy `.jpg` files with
`dir(context)`.

- [ ] **Step 2: Run it, expect a compile failure**

- [ ] **Step 3: Implement the store**

- [ ] **Step 4: Run the test, expect PASS**

- [ ] **Step 5: Commit**

`feat(prefs): store, sweep and decode background images`

---

### Task 3: Proto field and rendering

**Files:**
- Modify: `app/src/main/proto/user_prefs.proto` — add `string background = 8;` to `Screen`
- Modify: `app/src/main/java/com/aatorque/stats/DashboardFragment.kt`
- Test: `app/src/test/java/com/aatorque/prefs/BackgroundSourceTest.kt` stays the logic gate

**Interfaces:**
- Consumes: `parseBackground`, `BackgroundStore.resolve`, `BackgroundStore.thumbnail`.
- Produces: `DashboardFragment.applyBackground(screen: String, global: String)`, private.

- [ ] **Step 1: Add the proto field**, then build to confirm codegen:
  `./gradlew --offline compileDebugKotlin`

- [ ] **Step 2: Replace the `selectedBackground` collector** with one flow that maps the
  current screen to a `Pair(screen.background, prefs.selectedBackground)` and collects
  `distinctUntilChanged`. Keep the existing setting applied through the resource path.

- [ ] **Step 3: Implement `applyBackground`** — `Drawable` → resource id, `Stored` → decode on
  `Dispatchers.IO` with `reqHeight` from the view, then set the binding on the main thread;
  `Inherit` or an unreadable file → `selectedBackground`. Cache the last decoded bitmap by
  file name. Clear `blurEffect`, `colorFilter` and `displayingArtwork` as the current
  resource path does.

- [ ] **Step 4: Point the album-art fallback at the screen value.** `onMediaChanged` must
  call `applyBackground(currentScreen, currentGlobal)` instead of reusing `lastBackground`.

- [ ] **Step 5: Verify** `./gradlew --offline testDebugUnitTest ktlintCheck`

- [ ] **Step 6: Commit**

`feat(dashboard): render a background per screen`

---

### Task 4: Picker in the per-dashboard settings

**Files:**
- Create: `app/src/main/java/com/aatorque/prefs/BackgroundPreference.kt`
- Create: app/src/main/res/layout/list_divider.xml
- Modify: `app/src/main/res/xml/display_setting.xml`
- Modify: `app/src/main/java/com/aatorque/prefs/SettingsDashboard.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: app/src/test/java/com/aatorque/prefs/BackgroundListTest.kt — the row model only

**Interfaces:**
- Produces: `data class BackgroundRow(val kind: Kind, val title: String, val value: String)`
  with `enum class Kind { DEFAULT, DRAWABLE, FILE, CHOOSE, DIVIDER }`, and
  `fun buildRows(bundled: List<Pair<String, String>>, stored: List<String>, current: String): List<BackgroundRow>`.

- [ ] **Step 1: Write the failing test** for `buildRows`: default first, bundled next, a
  divider, stored files (title "Custom image 1..n"), then `CHOOSE` last; `current` marks the
  matching row.

- [ ] **Step 2: Run it, expect a compile failure**

- [ ] **Step 3: Implement `buildRows` and the row model**

- [ ] **Step 4: Implement `BackgroundPreference`** — a `Preference` that opens an
  `AlertDialog` with the rows over `icon_list_row`, writes the chosen value through a
  listener, and starts the image picker for `CHOOSE`.

- [ ] **Step 5: Wire `SettingsDashboard`** — the row in `display_setting.xml` (persistent
  false, title `pref_background`), summary from the current screen value, DataStore write on
  pick, `ActivityResultContracts.OpenDocument` with `image/*`, import on
  `Dispatchers.IO`, sweep with every screen's file names, then refresh the summary.

- [ ] **Step 6: Verify** `./gradlew --offline testDebugUnitTest ktlintCheck lintDebug`

- [ ] **Step 7: Commit**

`feat(settings): pick a custom background per dashboard`

---

### Task 5: Docs, push, CI, draft PR

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/troubleshooting.md` only if a user-visible symptom needs a line

- [ ] **Step 1: Document** the `background` field, the precedence and the image store in
  the preferences section of `docs/architecture.md`.

- [ ] **Step 2:** `python3 tools/check_docs.py`

- [ ] **Step 3: Push the branch** and read the run: `gh run list -R YuhiAida/aa-torque -L 5`

- [ ] **Step 4: Fix whatever the gates report.** All three jobs must pass.

- [ ] **Step 5: Open a draft PR** that references #138, states what CI checked and what it
  did not (head-unit decode, document picker).
