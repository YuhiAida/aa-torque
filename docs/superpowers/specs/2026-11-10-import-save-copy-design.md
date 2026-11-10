# Import confirmation: offer a recoverable copy

## Intent

The import dialog says the current settings are "saved to the app storage first". That copy
lives in the app's private files directory, has no restore action, keeps one slot, and dies
with clear-data or uninstall. The sentence promises a way back that the user does not have.

The dialog must state only what happens, and must offer a copy the user can actually keep.

## Goals

- The message names the scope of the replace and nothing more.
- A checkbox, checked by default, offers "Save a copy of the current settings first".
- A saved copy goes to a location the user picks (Downloads by default) through the
  existing export path, so it survives clear-data and uninstall.

## Non-goals

- No restore action inside the app.
- No new backup format: reuse the protobuf the export already writes.
- No change to the internal `settings-before-import.pb` copy, which stays as the atomicity
  guard for the replace.

## Approach (chosen: B, checkbox)

1. The picked file is parsed into memory, then the dialog opens with a custom view: the
   message and a `CheckBox` (`import_save_copy`), checked by default.
2. On **Replace** with the box checked, `exportFileLauncher` opens `ACTION_CREATE_DOCUMENT`
   (title `dashboards.pb`). The pending import is kept as protobuf bytes in
   `onSaveInstanceState`, because a protobuf message is not `Parcelable` and the picker can
   recreate the activity.
3. On export success the import runs. On cancel nothing happens. On export failure the
   import is abandoned and `export_failed` is shown, so the user is never left without the
   copy they asked for.
4. With the box clear, the import runs immediately.

Strings: `import_confirm_message` loses the storage claim, `import_save_copy` is new, base
locale only (the locale check does not charge keys added after the baseline; translations
follow in the usual sweep).

## Constraints

- No Android SDK on this machine, so no local build. CI (`lint`, `render`, `themes`, `docs`,
  `locales`) is the gate; it compiles but does not render. The dialog and the picker chain
  stay unverified at runtime.
- Smallest diff: one layout, one string, changes inside `SettingsActivity`.
