# AFTERLATER

[![CI](https://github.com/YasenK90/afterlater/actions/workflows/ci.yml/badge.svg)](https://github.com/YasenK90/afterlater/actions/workflows/ci.yml)

AFTERLATER is a local-first Android link archive. Save a link from any app, let it settle for a day, and review a small daily batch when it is ready.

## What it does

- **Save from anywhere.** Use the Android share sheet, paste a link in the app, or (experimentally) get a card when TikTok exposes a video link in its share sheet, or when you copy a link in any app.
- **Decide now, or later.** When the card saves a link you can say whether it should surface today and whether it expires at the end of today or the week. An expiring entry jumps the daily batch so it cannot die unseen.
- **A small daily batch.** Up to five due entries per day (you choose, three by default), picked with domain diversity. Opening one resets it for a week; snooze pushes it to tomorrow, next week, or a date you pick.
- **A searchable archive.** Full-text search across titles, notes, domains, and URLs, with filters for the undecided pool (in rotation), kept links (useful), and archived entries.
- **Local only.** Everything is stored on the device in Room. The only network use is an optional, best-effort metadata preview for saved pages. Export and restore use a readable local JSON file.
- **Optional reminders.** One quiet notification a day when something is due. Off by default.

## Requirements

- Android 8.0 (API 26) or newer.
- Android 13+ requires notification permission for daily reminders.
- No account is required.

## Install

### Debug build

```bash
export JAVA_HOME="$HOME/.local/lib/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release build

A release APK must be signed before Android will install it. The repository does not contain a signing key and will not generate one for you.

Create `keystore.properties` in the repository root with your own release keystore:

```properties
storeFile=/absolute/path/to/afterlater-release.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

`keystore.properties`, `*.jks`, and `*.keystore` are git-ignored. Keep the keystore and passwords backed up: future beta updates must use the same signing key.

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties`, Gradle still builds the release variant but produces the non-installable `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Experimental capture

Both features below are off by default, share one Android AccessibilityService and the **Display over other apps** permission, and never save without a tap. No screen text and no clipboard text leaves the device.

### TikTok share sheet

This uses the accessibility tree to inspect TikTok's share sheet. It only offers a prompt when TikTok exposes a complete video URL; it never constructs a URL from a video id.

1. Install the debug build with the commands above.
2. Open **Settings → TikTok auto-detect** and turn it on.
3. Open **Accessibility** settings and enable **AFTERLATER link capture**.
4. Allow **Display over other apps** for AFTERLATER.
5. Open TikTok, open a video, and tap **Share**. If TikTok exposes the video link, a small **SAVE TO AFTERLATER?** card appears over the app.

### Clipboard card

1. Open **Settings → Clipboard card** and turn on **Watch the clipboard**.
2. Copy a link in any app. A card slides in from the right edge.
3. Tap **SAVE**, then answer **SURFACE IT?** (today / queue / not for me) and **EXPIRES?** (end of today / seven days / none). Both questions are optional; the card closes on its own.

Because Android only hands clipboard contents to an app that currently has input focus, the service briefly takes focus through an invisible 1×1 window before reading, then gives it straight back. The keyboard stays attached to the app underneath, and the card itself never takes focus.

The clipboard is read only while the toggle is on and the service is connected. Clips longer than 2,000 characters, clips that are not single links, and repeats of the same URL within ten minutes are ignored.

The Save button uses the existing capture path, so the normal canonical URL and duplicate checks still apply.

### Debugging detection

```bash
adb logcat -c
adb logcat -s AFTERLATER-DETECT:D
```

Debug builds log the event, the visible accessibility text, and the relevant view ids under the `AFTERLATER-DETECT` tag. Release builds do not log that text.

If no prompt appears:

- Confirm both permissions are enabled in **Settings → TikTok auto-detect**.
- Confirm the installed package is `com.zhiliaoapp.musically` or `com.ss.android.ugc.trill`.
- Open the share sheet and check the log. Some TikTok versions do not expose a copyable URL through accessibility. In that case, no prompt is the correct result.
- On Android 13+, a sideloaded app may need **Allow restricted settings** before its accessibility service can be enabled.

## Data and updates

Saved items, review state, and reminders are stored locally. Updating the app keeps this data when the package name and signing key stay the same. Uninstalling the app removes it.

Android cloud backup is disabled deliberately. Use **Settings → Export** to create a local JSON backup, and **Settings → Restore** to import one. Restore is add-only: it never deletes or overwrites existing entries.

## Known limitations

- There is no cloud sync, account system, or cross-device sync.
- Metadata previews are best-effort. A capture still succeeds when the page cannot be fetched; the original URL is kept.
- Daily reminders are approximate. Android battery restrictions can delay WorkManager jobs, and notifications remain silent when permission is denied or the channel is disabled.
- Restoring a backup does not remove entries that are already in the archive.
- TikTok auto-detect is experimental. It depends on TikTok exposing a URL in its accessibility tree, and a TikTok UI change can break detection. It never guesses or fabricates a link.
- Clipboard capture depends on the accessibility service being connected and on Android delivering clipboard changes to a background app. Some vendor ROMs (MIUI in particular) show their own clipboard prompt and can swallow the first read; the retry usually gets the second.
- A deadline that passes removes the entry from review, but nothing moves it out of rotation automatically: it stays in the archive until you archive it.
- This is a closed beta. Please report crashes, broken captures, and confusing behavior.

## Development

The build needs JDK 17 or newer; the commands below assume JDK 21.

```bash
export JAVA_HOME="$HOME/.local/lib/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:lintDebug           # Android lint
./gradlew :app:assembleDebug       # debug APK
```

CI (GitHub Actions) runs the same three tasks on every push to `main` and on
every pull request; the workflow lives in `.github/workflows/ci.yml`.

Unit tests are plain JVM tests: domain rules, the link parser, backup
encode/decode, restore planning, and view models run against fakes. Database tests
run under Robolectric against an in-memory Room database, and the exported schemas
in `app/schemas/` are put on the test classpath so the v1 → v2 migration is checked
against the current entities rather than by eye.

There are no instrumented tests, so anything that needs a real device — the
accessibility service, the clipboard card, the overlay, daily reminders — still has
to be checked on a device.

Notable changes are recorded in `CHANGELOG.md`.

## Report a bug

Open an issue at https://github.com/YasenK90/afterlater/issues and include:

- AFTERLATER version from **Settings**
- Android version and device model
- What you did, what happened, and what you expected
- A screenshot or a short screen recording if it helps

Do not paste private links or personal notes into a public issue.
