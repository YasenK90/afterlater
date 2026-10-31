# Changelog

Notable changes to AFTERLATER. Dates use the release year and month.

## Unreleased

- Auto-categorization: saved links are suggested into **DESIGN**, **CODING**,
  **OFFER**, or **AI** — from the domain at capture time, sharpened by the
  page's title and description once the preview is fetched. The suggestion
  arrives pre-selected in the clipboard card's TAG IT? stage (untick it if
  it's wrong), and shows up in the archive's TAG filter like any tag.
- Capture is fast everywhere: the clipboard reader now keeps trying for
  ~3.4 seconds (instead of ~0.2) with growing pauses, click-driven reads are
  deferred instead of dropped, and the copy tap is recognized in 150 ms —
  the card now appears within about a second even in apps that write the
  link late (Instagram, Twitter, TikTok).
- The capture card is glassy (~60% transparent) with a single SAVE button;
  the card leaves via SAVE or its own timer, and pressing SAVE behaves
  exactly as before.
- Smart sorting: a shared ranking — deadline urgency first, then never-opened
  links, then whatever you have ignored longest — now drives both the daily
  batch and a new default **SMART** ordering in the archive.
- Search is now full-text: a new index (database v4) matches whole-word
  prefixes across titles, descriptions, notes, domains, and URLs, so
  "watchlat" finds "watch later" and results stay instant as the archive
  grows. Entries saved before the update are searchable too — the migration
  backfills the index, and this is verified by migration tests.

## 1.2 — 2026-10

- Compose UI tests now run on the JVM (Robolectric): the snooze dialog's
  presets and date picker, the tag editor's toggling and confirm order, and
  the archive's status pills are exercised as real UI — the suite grew from
  149 to 161 tests.
- The daily batch can now go up to five entries (three remains the default),
  so the Settings stepper actually has room to grow, and snoozing gains a
  **Pick a date** option alongside tomorrow and next week — a picked date
  returns the entry at the start of that day.
- Crash capture: uncaught crashes are kept locally (the newest five) and can
  be shared from Settings → About as a debug report with the app version,
  device, and traces. Nothing is uploaded automatically; clear the log from
  the same place.
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
