# Alfaaz

Time-synced lyrics for whatever is playing, on the phone and on Android Auto. It follows Spotify, YouTube
Music or any other active media session. Lyrics come from [LRCLIB](https://lrclib.net).

*Alfaaz* (الفاظ) is Urdu, from the Arabic *alfāẓ*, "words".

**Safety:** do not read lyrics while driving. The lyrics on the car screen are for passengers or a parked car.

## What it does

- Detects the current track and follows play, pause and seek without polling.
- Fetches synced lyrics, falls back to plain lyrics, and caches both. "Not found" is retried after a day.
- Shows the album art, artist and album the player reports, and controls the player: previous, play or
  pause, next.
- Phone: large lyrics over the blurred album art, in the manner of a music player.
  - The current line is sharp near the top; the others dim with distance, and blur on Android 12 or newer.
    Each line starts from its own reading side. Dots fill during long pauses in the singing.
  - Tap a line to jump there (when the player allows seeking).
  - While synced lyrics play, the controls fade after a few seconds; tap to bring them back. They stay when
    playback is paused, when the lyrics are not synced and with TalkBack on.
  - A per-song timing offset and a refresh are at hand. Turned sideways, or on a tablet, the cover and
    controls sit beside the lyrics.
- Car: lyrics on Android Auto's media card, beside the map or in its full *Now playing* view. The line being
  sung is drawn large over the middle of the album art, with the next one small and faded below it, and
  repeated in the card's bright text with the song and artist underneath. The buttons control the real player.
  See [Lyrics in the car](#lyrics-in-the-car).

## Lyrics in the car

1. On the phone, open Alfaaz's **Settings** and turn on **Lyrics on media card** (off by default).
2. Start a song in Spotify or YouTube Music.
3. On the car screen, pick **Alfaaz** from the music apps.

Alfaaz publishes its own media session that mirrors the player. It plays no sound, takes no audio focus and
passes play, pause, skip and seek on to the real player. To switch between the card beside the map and the
full view, use Android Auto's own buttons. With the setting off, Alfaaz is not listed in the car.

Android Auto decides where the card's texts go, so Alfaaz draws the lyrics onto the album art, with a soft
dark fade behind them. Without synced lyrics the art stays clean and the card's text says why.

If the card shows the player instead of Alfaaz, reconnect the car and open Alfaaz before starting any music.
This kept Alfaaz's card each time in the simulator; it did not help once the player had resumed by itself.

## Permissions and privacy

| Permission | Why |
|---|---|
| Internet | Query LRCLIB. |
| Notification access (granted in system settings) | The only way Android lets an app see other apps' media sessions. Notifications are never read. |

With **Lyrics on media card** on, Alfaaz also runs a media browser service that other apps can see. It
answers only Android Auto and refuses everyone else.

For each new song the app sends the title, artist, album and length to `lrclib.net`, identifying itself by
app name and version. Lyrics are cached on the phone. There are no accounts and no analytics.

Album art comes from the player. When the player offers only a web link to the image, the app downloads it
from that link, which is the player's own image server. The app also asks Android to see which installed
apps are media players, only to show the player's name and icon.

## Requirements

- macOS or Linux, JDK 17
- Android SDK: `platforms;android-37.1`, `build-tools;36.0.0`
- Android 8.0 (API 26) or newer phone; Android Auto for the car

## Build

On macOS with Homebrew:

```bash
brew install openjdk@17
brew install --cask android-commandlinetools android-platform-tools
```

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-37.1" "build-tools;36.0.0"
```

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Keep the project outside folders synced by iCloud Drive or similar services. They create copies such as
`Foo 2.class` in the build output, which breaks the build.

## Checks

Every pull request runs one GitHub check, `checks`: unit tests with two evals, lint, a debug build and the
shrunk (R8) release build. Changes reach `main` only through a pull request that passes it.

- **Lyrics matching.** About 30 songs, written the way players report them, are looked up against saved
  LRCLIB replies. Each song's outcome must match `app/src/test/resources/lyrics-eval/baseline.txt`. When a
  change in outcome is intended, replace that file with the text the failure prints. The saved replies hold
  titles, artists and lengths, never lyrics.
- **Car card legibility.** The car card is drawn for sample covers and invented lines with Android's own
  graphics (Robolectric), and its contrast, text placement, right-to-left reading and glyphs are checked.
  The first run downloads Android runtime files of several hundred megabytes.
- **Live report.** Once a week GitHub asks the real LRCLIB for the same songs and reports the hit rate. It
  never blocks a change.

The tests run on JDK 17. To refresh the saved replies:

```bash
./gradlew testDebugUnitTest -PlyricsEval=record
```

`record` fetches only requests that have no saved reply, `record-all` fetches every reply again, and `live`
only reports, to `app/build/reports/lyrics-eval/`.

## Run on a phone

1. Turn on USB debugging and connect the phone. On Samsung, turn off Auto Blocker if it blocks this.
2. Install:

   ```bash
   ./gradlew installDebug
   ```

3. Open Alfaaz. It starts on **Settings** until it has notification access: tap **Open settings** and allow
   it. If Android 13 or newer blocks the
   switch, open the app's *App info*, tap ⋮, choose **Allow restricted settings** and try again.

   If it stays blocked, grant the same access to your own development install from the computer (use
   `disallow_listener` to revoke it):

   ```bash
   adb shell cmd notification allow_listener io.github.mzainkh.alfaaz/io.github.mzainkh.alfaaz.media.MediaSessionListenerService
   ```

4. Play a song.

## Test in the car simulator (Desktop Head Unit)

1. Install the head unit simulator:

   ```bash
   sdkmanager --sdk_root="$ANDROID_HOME" "extras;google;auto"
   ```

2. On the phone, open Android Auto settings and tap **Version and permission info** 10 times. In the ⋮ menu
   choose **Start head unit server**.
3. Connect over USB and run:

   ```bash
   adb forward tcp:5277 tcp:5277
   "$ANDROID_HOME/extras/google/auto/desktop-head-unit"
   ```

4. Turn on **Unknown sources** in Android Auto's developer settings, then follow
   [Lyrics in the car](#lyrics-in-the-car).

Useful head unit console commands: `restrict all` (simulate driving), `restrict none`, `night`, `day`.

Debug builds have a *Demo mode* switch under **Settings** (the gear icon): a pretend player with placeholder
songs and album art whose buttons work, no real player needed. `adb logcat -s Alfaaz` shows when Android Auto
connects to the card.

## Use in a real car

For a build you installed yourself, turn on **Unknown sources** in Android Auto's developer settings. This
has not been tried in a real car yet.

The alternative is to install from Google Play, for example through
[Internal App Sharing](https://developer.android.com/training/cars/testing).

1. Create an upload keystore outside the repository:

   ```bash
   keytool -genkeypair -v -keystore "$HOME/alfaaz-upload.jks" -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties` in the project root (it is gitignored):

   ```properties
   storeFile=/absolute/path/to/alfaaz-upload.jks
   storePassword=
   keyAlias=upload
   keyPassword=
   ```

3. Build `app/build/outputs/bundle/release/app-release.aab` and upload it to Play:

   ```bash
   ./gradlew bundleRelease
   ```

## Limitations

- Android Auto may keep showing the real player's card instead of Alfaaz's, especially after reconnecting.
  This happened with YouTube Music, and picking Alfaaz again did not bring its card back.
- The card has been tried in the head unit simulator with YouTube Music. It has not been tried with Spotify
  or in a real car.
- Android Auto offers apps no lyrics slot beside the map. Using the media card this way is not documented by
  Google and may stop working.
- Android Auto lists one entry per app, so Alfaaz has no separate full-screen car app.
- The middle lyrics are part of a picture. Their size follows the card's artwork, and beside the map they
  look grey because Android Auto dims it. The line at the bottom stays bright. In the full view the current
  line shows twice.
- Apps that log listening history may record lyric lines as song names.
- Album art is whatever the player provides; Spotify's is sometimes low resolution.
- Google's car quality guidelines forbid automatically scrolling text, and this card is not a real music
  player. Expect a public car release to be rejected.
- Ads and podcast episodes are skipped.
- Lyrics are community-contributed and may be missing or mistimed.

## License

[Apache-2.0](LICENSE)
