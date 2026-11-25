# TAK Bridge 0.5.0-beta.1

[Download the APK and guides](https://github.com/DroneWuKong/MultiProtocol-UAS-TAK-Bridge/releases/tag/v0.5.0-beta.1) · [Quick start](QUICK_START.md) · [User manual](USER_MANUAL.md)

## Install and update

Download `TAK-Bridge-0.5.0-beta.1.apk` from the release's **Assets**, open it on Android 8.0 or later, and follow the installation prompts. If asked, allow the browser or file manager you used to install this app. Device policies and Android versions can change the prompts; keep normal device protections enabled. The automatic source ZIP/TAR assets are for developers.

Launch **TAK Bridge Beta**. The package is `com.dronewukong.takbridge.beta`, version code **8**. It installs separately from `com.dronewukong.takbridge` and has separate preferences. Do not uninstall an existing app to try this beta. Export needed diagnostics and record settings before removing any installation; uninstalling erases that app's data.

Later beta updates require the same beta signing certificate and a higher version code. Installing an update over the beta retains its data. Production signing and package identity are managed separately. This beta does not reuse a disposable debug key, and its manifest has debugging disabled.

## Check your download

Release assets include the APK, quick-start PDF, manual PDF, `beta-validation.json`, `BETA-SIGNING-CERT.txt` and `SHA256SUMS`. Download them into one directory, then on a system with `sha256sum`:

```sh
sha256sum -c SHA256SUMS
```

The public beta certificate SHA-256 is:

```text
7491c62e3a8aed38bce8d9e6ad28e523689e42cc268d9a4f613621f69fd2ff64
```

A hash comparison detects download changes. APK signature verification also checks the signer. With Android SDK tools installed:

```sh
python3 tools/verify_beta.py TAK-Bridge-0.5.0-beta.1.apk \
  --expected-certificate 7491c62e3a8aed38bce8d9e6ad28e523689e42cc268d9a4f613621f69fd2ff64
```

The verification checks application ID, version, version code, minimum Android version, non-debuggable manifest, APK signature and optional expected certificate. The signing private key and passwords are never release assets.

## What is validated

- 56 native tests cover parsing, CoT, GPS freshness, publication expiry, settings, simulation isolation, lifecycle, loopback TCP and mutual TLS behavior.
- Debug and signed beta APK assembly; lint has no errors (existing warnings remain).
- Browser checks cover bundled tool panels, phone/tablet navigation and calculator behavior.
- The installed signed beta is exercised on an Android emulator. Each documentation screenshot is a raw `adb exec-out screencap -p` PNG. The capture script pulls the installed APK and requires its SHA-256 to match the release APK before saving an image. [Capture manifest](guide-assets/beta-0.5.0/captures.json).
- `beta-validation.json` in the release records the exact source commit, artifact hashes and emulator observations.

The demo uses synthetic GPS and does not publish it to a network. No physical USB source, aircraft, TAK Server, receiving ATAK device or camera was attached for the screenshots. Physical USB/GPS, screen-off endurance, server routing, marker/video association and playback remain bench checks. Socket writes are not delivery receipts. The public app shares an RTSP address; it does not host, relay or display the video feed.

## Emulator observations

The screenshots were captured on Android 11 (API 30), 480 × 960 pixels at 180 dpi, with the image's bundled WebView 83.0.4103.120. Normal launch, local demo, settings, Tools, same-APK reinstall with a retained video setting, and disabling/clearing that setting were exercised. A loopback video URL was rejected and the example LAN URL was accepted. This is not a full Android-version compatibility matrix.

Two UI limitations were observed during capture preparation:

- At the narrower 384-dp emulator configuration, the long diagnostics dialog stacked its actions and the lower actions were clipped. The Android Back action closes it; the documented 427-dp configuration displays all actions. Narrow-screen diagnostics still need improvement.
- Rapidly changing emulator display size/density while force-stopping and relaunching triggered one WebView data-directory lock exception on the old bundled WebView. A subsequent restart succeeded; the later reinstall and normal launch passed. This condition has not been reproduced on a physical device or a current WebView.

These observations are retained rather than treating successful screenshots as proof of universal device compatibility. Use your device's supported, updated Android System WebView where available.

## Build a beta

Prerequisites: JDK 17, Android SDK 35 with build tools and command-line tools, Python 3.10+, and the repository Gradle wrapper. Set `JAVA_HOME` and `ANDROID_HOME` for your installation. The beta build type is separate from production release signing.

Provide these values through the environment or an owner-only JSON file **outside the checkout**:

- `TAK_BETA_KEYSTORE`: absolute path to the persistent beta keystore.
- `TAK_BETA_STORE_PASSWORD`: keystore password.
- `TAK_BETA_KEY_ALIAS`: signing alias.
- `TAK_BETA_KEY_PASSWORD`: key password.

Keep the key and credentials outside Git, retain a secure backup, and never put them in screenshots, command output or release assets. The published beta signing identity must stay stable for updates; a contributor's own key creates a separate signing lineage.

```sh
python3 tools/build_beta.py --credentials /private/path/beta-credentials.json
```

The JSON object's keys are the four names above. The script rejects credential files inside the checkout or readable by other users. It runs tests, builds debug and beta APKs, runs lint, and verifies build evidence plus the beta artifact. Output: `app/build/outputs/apk/beta/app-beta.apk`. Running `assembleBeta` without signing values fails rather than producing a distributable unsigned beta.

Before publication, run `tools/verify_beta.py` with the expected certificate above. CI compiles and lints the beta variant without receiving the signing key; maintainer signing happens separately. Rebuild from the reviewed release commit, validate the artifact, and publish as a GitHub **prerelease**. Use fixed-version download links because GitHub's `latest` endpoint does not select prereleases.

## Recreate the illustrated guides

Install the signed beta on an emulator, navigate to a documented state, then capture it:

```sh
python3 tools/capture_beta_screenshot.py \
  --serial emulator-5554 --apk app/build/outputs/apk/beta/app-beta.apk \
  --name home --scenario 'First launch; no telemetry source connected'
python3 -m venv .venv-docs
.venv-docs/bin/pip install -r tools/requirements-docs.txt
.venv-docs/bin/python tools/generate_guides.py
```

Install DejaVu Sans fonts if absent. Supported capture names are home, demo, connection, destination, video, diagnostics and tools. Preserve unmodified screenshots, distinguish synthetic data from live input, and inspect every generated PDF page for clipping before uploading. Earlier software-test renders are retained as historical assets and are not used as current beta screenshots.

See [Android app signing](https://developer.android.com/studio/publish/app-signing) and [Android installation help](https://support.google.com/android/answer/9457058?hl=en) for platform details. Historical qualification and signing records remain in [v1 readiness](V1_READINESS.md).
