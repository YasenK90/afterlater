# Changelog

Notable changes to AFTERLATER. Dates use the release year and month.

## Unreleased

- Release builds are now minified with R8 (roughly half the size of before)
  while keeping line numbers readable in reported stack traces. Keep the
  generated `mapping.txt` for each release to deobfuscate crash reports.
- First-run welcome: a one-time screen that explains the archive in three
  lines and walks through turning on copy detection, including the
  accessibility and overlay permissions it needs.
- Automatic local backup: pick a folder once (system picker) and AFTERLATER
  overwrites afterlater-archive-auto.json there weekly, so a lost phone no
  longer means a lost archive. Nothing is uploaded; restoring still happens
  through the existing Restore button.
- Tags are now usable everywhere: the entry page shows an entry's tags with an
  **Edit tags** dialog (preset labels plus every tag in the archive), and the
  archive gains a **TAG** filter once any entry carries a tag.
- Clipboard card: a third optional question, **TAG IT?**, offers preset labels
  (useful, offer ending today, watch later, recipe, buy it) after SURFACE and
  EXPIRES. Tags are stored locally, are optional, and the card still closes on
  its own if you skip.
- Clipboard card: copy a link in any app and a card slides in from the right with
  **SAVE**, then two optional questions — **SURFACE IT?** (today / queue / not for
  me) and **EXPIRES?** (tonight / seven days / none). Off by default and shares
  the existing accessibility service and overlay permission. See
  [Experimental capture](#experimental-capture).
- Deadlines: an entry with a deadline is pulled into the next daily batch instead
  of waiting out its settle period, and is no longer offered once the deadline
  passes. The deadline appears in the archive and on the entry page, where it can
  be set, changed, or cleared — including for links saved through the share sheet
  or pasted in the app, which the clipboard card never asked about.
- The accessibility service now observes copies anywhere, not just TikTok's share
  sheet. It still reads the accessibility tree only on the TikTok share sheet, and
  clipboard text is read only while the clipboard toggle is on.
- Tests: the database is now covered on the JVM. The v1 → v2 migration runs under
  Robolectric and is checked column by column against the current entities, and the
  due queries are tested against an in-memory Room database, so the deadline filter
  no longer relies on reading the SQL by hand.

## 1.1 — 2026-10

- Daily reminders: one quiet notification at a chosen time, only when Today has
  something due. Optional, off by default (Android 13+ asks for notification
  permission).
- Local JSON backup: **Settings → Export** writes a readable copy of every entry
  and review event; **Settings → Restore** shows exactly what would be added
  before writing anything. Restore is add-only and never overwrites or deletes.
- Experimental TikTok auto-detect: with both permissions granted, AFTERLATER
  offers a save prompt when TikTok exposes a video link in its share sheet. It
  never guesses a link and never saves without a tap. Off by default.
- Archive: new **IN ROTATION** filter for the undecided pool (new, ready, and
  snoozed entries) alongside Useful and Archived.
- Accessibility: the reminder and auto-detect switches are now labeled for
  screen readers and their whole row is tappable; the daily-limit buttons
  describe themselves.
- Fixed the archive's oldest-first ordering, which could fail on some devices
  due to an invalid sort expression.
- Tests: restore planning (what a backup adds versus skips) is now covered by
  unit tests; CI runs tests, lint, and a debug build on every push.

## 1.0 — 2026-10

- Initial release. Local-first link archive: save from any app's share sheet or
  paste manually, daily review batches of up to three entries with domain
  diversity, snooze, keep-useful, archive, full-text search across titles,
  notes, domains, and URLs, and best-effort metadata previews.
