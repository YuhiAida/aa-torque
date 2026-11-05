# Custom background per dashboard

Issue: [#138](https://github.com/YuhiAida/aa-torque/issues/138)

## Intent

Today the dashboard background is one choice for the whole app, picked from a fixed list of
bundled drawables. A user cannot use their own image, and cannot give each dashboard a
different look. This design adds both: a per-dashboard background, which may be a bundled
drawable or an image from the phone.

## Goals

- A user picks an image from the phone and sees it behind the gauges on the Android Auto
  surface.
- Each dashboard screen keeps its own background.
- Screens that never set one keep using the app-wide background, which keeps working.

## Non-goals

- The Android Auto media card artwork. It stays theme-driven.
- Video, animated, or remote (URL) backgrounds.
- Editing, cropping, or colour-grading the image inside the app.
- Per-screen album-art settings. Album art is still one app-wide switch.
- Carrying custom images through dashboard export. The export format stays a raw proto. An
  imported screen whose image is not on the phone falls back to the app-wide background.

## Anti-goals

- A background that needs more than one image decode per screen change.
- Any main-thread file or bitmap work. Issue #99 was an ANR from exactly that.
- A new dependency for image work. The platform decoders cover everything here.
- A schema change that breaks an existing install or an exported file.

## Constraints

- Preferences are a Protobuf message in DataStore, rewritten on every change. Image bytes
  must not live there.
- `DashboardFragment` resolves the background through a resource id only. A file needs a
  second path into the same `ImageView`.
- Android Auto runs the app on a head unit with a small memory budget. Photos from a phone
  camera are far too large to decode whole.
- The settings screens under a dashboard are built after Torque loads its PID list. Issue #83
  tracks that they are unusable without Torque. The new control must not inherit that gate.
- `lintRelease`, `ktlintCheck` and `verifyRoborazziDebug` gate every change.
- Markdown is checked by the docs tool, so this file must not link to paths that do not
  exist yet.

## Approaches considered

### A. A field on `Screen`, global value as the fallback (chosen)

Add one string field to the `Screen` message. Empty means "use the app-wide background".
A prefixed value names the source. The picker lives in the per-dashboard settings screen,
which already edits a single screen.

- Smallest diff: one proto field, one settings row pair, one branch in the renderer.
- Existing installs and exported files stay valid: an absent field parses as empty.
- Trade-off: two places can set a background, so the model is "default plus override". The
  appearance screen keeps its picker and gains a sentence saying so.

### B. Move the background choice entirely into each screen

- One model, one place. No override rules to explain.
- Requires a migration that copies the current global value into every screen, and a
  changed appearance screen. Bigger diff, more ways to lose a user's setting, and it
  changes behaviour for people who never asked for this.

### C. A map of screen index to background on `UserPreference`

- No change to the `Screen` message.
- Rejected: screens are addressed by position, and the screen count control truncates the
  list. Index keys rot silently.

## Design

### The stored value

The `Screen` message gains one field:

```
string background = 8;
```

The value is a source, not a file name:

| Value | Meaning |
|---|---|
| empty | Use the app-wide `selectedBackground` |
| `resource:<drawable name>` | A bundled drawable, e.g. `resource:background_incar_vw` |
| `file:<file name>` | A copy in the app's own storage, e.g. `file:bg-1a2b3c.jpg` |

A small pure function parses the string into a sealed result: `Inherit`, `Drawable(name)`,
`File(name)`, or `Invalid` (falls back to `Inherit`). Unknown prefixes are treated as
invalid, not as a crash. This is the piece the JVM tests cover.

### The image on disk

Picked images are copied into a backgrounds directory inside the app's private files
directory. The copy is not the original bytes:

1. Read the picked stream on the IO dispatcher.
2. Decode bounds first, then decode with `inSampleSize` so the long edge is at most 2048 px.
3. Re-encode as JPEG at quality 90 and write `bg-<random hex>.jpg`.

2048 px keeps a 16:9 crop under about 6 MB decoded and about 400 KB on disk. The head unit
never sees the full-resolution photo.

The source URI is not persisted. A deleted gallery file or a revoked grant must not blank
the dashboard. `ACTION_OPEN_DOCUMENT` needs no runtime permission, so there is no permission
flow to add.

### Orphan cleanup

Replacing or clearing a background leaves the old file behind. One sweep deletes files in
the backgrounds directory that no screen references. It runs on the IO dispatcher after a
change and when the settings screen opens. A file that is still referenced is never deleted.

### Rendering

`DashboardFragment` gets one source of truth for the current screen, so it can react to both
a screen change and a background change:

- Collect `currentScreen` and that screen's `background` as one derived flow.
- `Drawable(name)` → the existing resource path.
- `File(name)` → decode on the IO dispatcher into a bitmap, then set it on the binding. Cache
  the last decoded bitmap by file name so a redraw does not decode again.
- `Inherit` → the current path against `selectedBackground`.
- A missing or unreadable file is logged and rendered as the app-wide background.

Precedence, highest first:

1. Album art, when the media switch is on and a player publishes artwork.
2. The screen's own background.
3. The app-wide background.
4. The built-in black drawable.

The album-art path already returns to the last background when playback stops. It re-reads
the screen value instead of the app-wide one.

### Settings

One **Background** row in the per-dashboard settings screen, declared in the XML so it
exists before Torque connects (issue #83). Tapping it opens a dialog list:

- **App default** — inherits the app-wide value. Its summary shows the inherited drawable,
  which tells the user what they get.
- **Bundled images** — the same drawables the appearance screen offers, with their
  thumbnails.
- A divider.
- **Stored images** — every custom image on the device, with a 128 px thumbnail.
- **Choose image…** — opens the document picker.

Picking a bundled entry writes `resource:<name>`. Picking a stored entry writes
`file:<name>`. Choosing a new image copies it, writes the value, and refreshes the list.

The library is device-wide: an image added from one dashboard is offered to every
dashboard, and the file is written once. Deleting is not part of this change. The sweep
removes unreferenced files, and an explicit delete action would be a follow-up.

The existing image list preference reads static resource arrays and throws when entries,
values and thumbnails differ in size, so it cannot hold the dynamic entries. A sibling
preference class owns the same dialog with a dynamic adapter. It reuses the existing row
layout, plus one divider row.

The app-wide picker on the appearance screen is unchanged. Its summary gains a note that a
dashboard can override it.

### Export and import

Unchanged. Export stays a raw `UserPreference` proto, and import keeps replacing the whole
store with `parseFrom`.

The one consequence: a `file:` value names an image that exists only on the phone that
created it. On import, a screen whose file is absent falls back to the app-wide background,
and the app logs a warning. No dialog. The user still gets a working dashboard, and the
per-dashboard choice shows as "app default" in the settings screen.

### Documentation

`docs/architecture.md` gains the background source line, because the renderer now has two
sources for one image. The preference section mentions the new field.

## Verification

- JVM tests: the source parser (all prefixes, empty, garbage), and the resolver: a `file:`
  value whose file is absent resolves to `Inherit`, using a temporary directory.
- The Roborazzi theme goldens are unchanged. Custom images are user data and have no golden.
- CI: `ktlintCheck`, `verifyRoborazziDebug`, `lintRelease`.
- Not verified by CI: the decode on a real head unit, and the document picker. Those need
  the DHU bench or the car, and the handoff must say so.

## Decisions left to the builder

- The exact JPEG quality, within 85 to 92, if 90 proves heavy on the oldest supported phone.
- Whether the sweep also runs at app start. It is cheap; the builder may add it.
- The divider row: a disabled preference or a view type in the adapter.
