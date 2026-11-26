# Alfaaz

Time-synced lyrics on Android Auto and on the phone for whatever is playing (Spotify, YouTube Music, or any
active media session). Lyrics come from [LRCLIB](https://lrclib.net).

*Alfaaz* (الفاظ) is Urdu, from the Arabic *alfāẓ*, "words".

**Safety:** do not read lyrics while driving. The car screen is for passengers or a parked car.

## Requirements

- macOS or Linux, JDK 17
- Android SDK: `platforms;android-37.1`, `build-tools;36.0.0`
- Android 8.0 (API 26) or newer phone with Android Auto

## Build

On macOS with Homebrew:

```bash
brew install openjdk@17
brew install --cask android-commandlinetools
```

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-37.1" "build-tools;36.0.0"
```

```bash
./gradlew assembleDebug testDebugUnitTest
```

## License

[Apache-2.0](LICENSE)
