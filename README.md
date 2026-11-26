# Alfaaz

Time-synced lyrics for whatever is playing, on the phone and on Android Auto. It follows Spotify, YouTube
Music or any other active media session. Lyrics come from [LRCLIB](https://lrclib.net).

*Alfaaz* (الفاظ) is Urdu, from the Arabic *alfāẓ*, "words".

**Safety:** do not read lyrics while driving. The car screen is for passengers or a parked car.

## What it does

- Detects the current track and follows play, pause and seek without polling.
- Fetches synced lyrics, falls back to plain lyrics, and caches both. "Not found" is retried after a day.
- Shows the album art, artist and album the player reports, and controls the player: previous, play or
  pause, next.
- Phone: one dark screen over the blurred album art. The current line stays in the middle; tap any line
  to jump there (when the player allows seeking). A per-song timing offset and a refresh are at hand.
  Turned sideways, or on a tablet, the cover and controls sit beside the lyrics.
- Car: one update per lyric line, at most once a second. Switching to Maps and back shows the lyrics at
  once.
  - Most cars: a panel with the album art, four rows (*Now playing*, *Now*, *Next*, *Then*) and Pause and
    Next buttons. Only the text changes, so the car's five-screen limit is not used up.
  - Cars that report *app-driven refresh*: a scrolling list that marks the current line with `►`.

## Permissions and privacy

| Permission | Why |
|---|---|
| Internet | Query LRCLIB. |
| Notification access (granted in system settings) | The only way Android lets an app see other apps' media sessions. Notifications are never read. |

For each new song the app sends the title, artist, album and length to `lrclib.net`, identifying itself by
app name and version. Lyrics are cached on the phone. There are no accounts and no analytics.

Album art comes from the player. When the player offers only a web link to the image, the app downloads it
from that link, which is the player's own image server. The app also asks Android to see which installed
apps are media players, only to show the player's name and icon.

## Requirements

- macOS or Linux, JDK 17
- Android SDK: `platforms;android-37.1`, `build-tools;36.0.0`
- Android 8.0 (API 26) or newer phone with Android Auto (car app API level 7 or newer)

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

## Test the car screen (Desktop Head Unit)

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

4. Useful head unit console commands: `restrict all` (simulate driving), `restrict none`, `night`, `day`.

Debug builds have two switches under **Settings** (the gear icon): *Demo mode* (a pretend player with
placeholder songs and album art whose buttons work, no real player needed) and *Fixed car rows* (force the
panel layout). `adb logcat -s Alfaaz` shows what the car host supports.

## Use in a real car

Android Auto's *Unknown sources* switch does not apply to apps built with the Car App Library. The app must
come from Google Play, for example through
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

- Android Auto shows one app at a time. While Alfaaz is open, turn-by-turn directions and the music player
  stay visible only as Android Auto's own widgets on its bar; turn them on in Android Auto's settings if you
  don't see them. No app can place itself beside the map.
- Android Auto draws the highlighted lyric line in its own accent colour, whatever colour the app asks for.
- Album art is whatever the player provides; Spotify's is sometimes low resolution.

- No Android Auto app category covers lyrics, and Google's car quality guidelines forbid automatically
  scrolling text. The app is declared as `IOT` for personal testing; expect a public car release to be
  rejected.
- Android Auto 18.1 on a Galaxy S22+ does not report app-driven refresh (checked with the Desktop Head
  Unit), so the car shows the fixed rows, not the scrolling list.
- Ads and podcast episodes are skipped.
- Lyrics are community-contributed and may be missing or mistimed.

## License

[Apache-2.0](LICENSE)
