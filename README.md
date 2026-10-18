# WiFi TV Remote — Android starter v0.2

A native Android app project written in Kotlin + Jetpack Compose, tailored for a Walton-branded Android TV.

## What changed in v0.2

- Reworked the interface into a dark, teal-and-yellow mobile layout.
- Searches specifically for Android TV Remote v2 and legacy Android TV Remote DNS-SD service types instead of generic DNS-SD services.
- Resolves discovered service names to local IP addresses and shows possible TV services.
- Adds manual IPv4 entry and checks whether TCP ports 6467 (pairing) and 6466 (remote session) are reachable.
- Remote controls are clearly disabled as previews. The app does not claim a TV is paired or pretend button presses were sent.
- Adds a GitHub Actions workflow to build a debug APK after the project is uploaded to GitHub.

## Important limitation

This is **not yet a fully working remote**. It currently implements service discovery and a connectivity check, but not the Android TV Remote v2 TLS pairing handshake, saved client certificates, protobuf command channel, or actual key/text sending. A successful port check only indicates network reachability, not successful pairing or compatibility. The TV brand logo and Android TV startup animation suggest a Walton-branded Android TV, but the exact model and Android TV Remote Service version remain unknown.

The next implementation step is the Android TV Remote v2 protocol:
- pairing port TCP 6467
- remote-control port TCP 6466
- secure certificate-based pairing with the code shown on the TV
- protobuf-framed key events and text input

## Build with GitHub Actions

1. Upload the contents of this folder to your public GitHub repository (upload the files inside the folder, not the enclosing ZIP as a single file).
2. Open the repository's **Actions** tab.
3. Enable Actions if GitHub asks.
4. Select **Build Android APK**, then **Run workflow**. A push to the default branch also triggers it.
5. Open the completed workflow run and download the `wifi-tv-remote-debug-apk` artifact.

The workflow builds a debug APK. It does not publish the app to Google Play.

## Build locally

Open this folder in Android Studio with JDK 17 and Android SDK 35 installed, then run `:app:assembleDebug`.

## TV setup tips

- Turn the TV fully on while scanning; boot logos alone don't guarantee the remote service is ready.
- Phone and TV should be on the same non-guest Wi-Fi network.
- If discovery fails, use the TV's Settings → Network page to find its IP address and try the manual connection check.
- Some routers block device-to-device traffic or multicast discovery.
