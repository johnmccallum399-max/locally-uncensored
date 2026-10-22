# LCDR — Personal Device Assistant (Android)

Private-use Android native app. Not for Play Store distribution.

## Build prerequisites

- Full JDK **17**, including `javac` and `jlink` (set `JAVA_HOME`; CI uses Temurin 17). A JRE alone cannot build the APK.
- Android SDK command-line tools **12.0 / 11076708**, platform **35**, Build Tools **34.0.0**.
- Gradle **8.9**, downloaded and SHA-256 verified by the committed wrapper. No system Gradle or Android Studio is required.
- Internet access for the first toolchain/dependency download. A backend, API keys, device, and release keystore are **not** needed for JVM tests or debug APK compilation.

Android Gradle Plugin 8.7.0, Kotlin/Compose 2.0.21 and KSP 2.0.21-1.0.28 are pinned in `gradle/libs.versions.toml`. Build Tools 34.0.0 is AGP 8.7's default; compile/target SDK remains 35, minimum device API 26.

`gradle.properties` explicitly selects KSP2 for Kotlin 2.0; the older KSP1 processing path produced duplicate generated classes even during clean builds. Room runtime/compiler/ktx are aligned at 2.7.2 for KSP2 support.

Install the SDK with Android Studio's SDK Manager or the official command-line tools. With `sdkmanager` on PATH:

```sh
sdkmanager --licenses
sdkmanager 'platforms;android-35' 'build-tools;34.0.0' 'platform-tools'
export ANDROID_HOME=/absolute/path/to/android-sdk
# Alternatively put sdk.dir=/absolute/path/to/android-sdk in android/local.properties.
```

Review and accept the SDK licenses as prompted. Do not commit `local.properties`, SDK/cache directories or signing material.

## Repeatable verification

From the repository root, using the checked-in launchers (Windows: `gradlew.bat`):

```sh
cd android
javac -version
./gradlew --version
./gradlew --no-daemon --console=plain --stacktrace :app:testDebugUnitTest
./gradlew --no-daemon --console=plain --stacktrace :app:assembleDebug
./gradlew --no-daemon --console=plain --stacktrace :app:lintDebug
```

For a clean rebuild, run `./gradlew clean` first. Do not generate the wrapper or install a separate Gradle on each checkout. Its distribution checksum is in `gradle/wrapper/gradle-wrapper.properties`; update launchers/JAR/properties together when intentionally upgrading.

| Check | Evidence | What it verifies |
| --- | --- | --- |
| `testDebugUnitTest` | `app/build/test-results/testDebugUnitTest/` XML; `app/build/reports/tests/testDebugUnitTest/` HTML | JVM tests of production helpers, compiled with the actual Android app and dependencies. Fake speech output does not produce audio. |
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | Full debug app compilation, generated DI/database code, resources, dexing and APK packaging. Does not run the app. |
| `lintDebug` | `app/build/reports/lint-results-debug.*` | Android static analysis; fails on lint errors. Does not exercise lifecycle or audio. |
| Device checks | Completed evidence table in `TTS_AUDIT.md` | Actual interruption handling, offline speech, voice identity and lifecycle behavior. Required separately. |

`.github/workflows/android.yml` runs on Android-related pull requests **including drafts**, pushes to `master`, and manual dispatch once present on the default branch. It installs the SDK, validates the wrapper, runs the same three commands and saves reports and a debug APK as short-lived Actions artifacts. Failures stay red; device testing is explicitly marked not run. CI needs no app secrets and does not install, deploy, merge, or mark a PR ready.

Keep PR #3 draft until a successful Android verification run and the required ViewModel/lifecycle and actual-device checks in `TTS_AUDIT.md` have recorded evidence. Passing helper tests or producing an APK alone is insufficient. The historical 26-test result used a standalone Kotlin/JUnit runner; limited compilation with temporary signatures was **not** an APK build.

## Running locally

Open `android/` in Android Studio and select the `app` target. A physical device is preferred for device APIs; API 26+ is supported. Runtime chat needs `https://ai-assistant-backend-dhi6.onrender.com` or your configured `API_BASE_URL`.

## Architecture

```
com.lcdr.assistant/
├── LcdrApplication.kt          Hilt entry point
├── MainActivity.kt             Biometric gate + Compose host
├── di/                         Hilt modules (Network, Database)
├── data/
│   ├── local/                  Room DB + DAOs + entities
│   ├── remote/                 Retrofit ApiService + SseClient
│   ├── prefs/                  EncryptedSharedPreferences wrapper
│   └── repository/             Auth / Chat / Memory repositories
├── domain/model/               Pure Kotlin domain models
├── tools/                      Tool framework + device integrations
│   └── impl/                   SMS, Contacts, Calendar, File, System
├── service/                    VoiceForegroundService, BriefingWorker, TileService
└── ui/
    ├── navigation/             NavGraph + Screen sealed class
    ├── login/                  JWT auth screen
    ├── chat/                   Streaming chat + tool call chips
    ├── voice/                  On-device STT/TTS modal
    ├── hub/                    Orchestration hub
    ├── memory/                 Long-term memory CRUD
    ├── settings/               Per-tool toggles, persona, biometric, briefing
    ├── device/                 Quick-action dashboard
    └── theme/                  Dark navy/gold color scheme
```

## Backend API contract

The app expects these endpoints on the backend:

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/login` | Returns `{token, user}` |
| GET  | `/api/memory` | Returns `{memories:[{id,key,value}]}` |
| POST | `/api/memory` | `{key,value}` → saves entry |
| DELETE | `/api/memory/:id` | Deletes entry |
| POST | `/api/chat` (SSE) | Streaming chat. Sends tool_call events |
| POST | `/api/chat/tool-results` (SSE) | Continue after tool execution |
| GET  | `/api/hub/agents` | Returns `{agents:[…]}` |
| POST | `/api/hub/sessions` | Creates orchestration session |
| GET  | `/api/hub/sessions/:id/stream` | SSE session progress |

### SSE event format expected

```
data: {"type":"content_block_delta","delta":"Hello"}
data: {"type":"tool_call_complete","tool_name":"read_sms","tool_id":"tc_001","arguments":"{\"limit\":5}"}
data: [DONE]
```

The `SseClient` also handles simpler formats:
```
data: Hello world
event: done
```

## Permissions

All dangerous permissions are requested at first use with a plain-language explanation.

| Permission | Used by |
|------------|---------|
| READ_SMS / SEND_SMS | `read_sms`, `send_sms` tools |
| READ_CONTACTS / WRITE_CONTACTS | `read_contacts`, `write_contact` tools |
| READ_CALENDAR / WRITE_CALENDAR | `list_events`, `create_event`, `delete_event` |
| ACCESS_FINE_LOCATION | `get_location` tool |
| CAMERA | `take_photo` (future) |
| RECORD_AUDIO | Voice screen STT |
| POST_NOTIFICATIONS | Daily briefing, tool notifications |
| PACKAGE_USAGE_STATS | `get_running_apps` — must be granted manually in Settings |

## Security

- JWT stored in `EncryptedSharedPreferences` (AES-256-GCM)
- Optional biometric gate on every app open
- No API keys on device — all LLM calls proxied through backend
- All tool executions logged to local Room DB (Settings → Action History)

## Daily briefing

The `BriefingWorker` (WorkManager) fires at the configured time, calls the chat API with a briefing prompt, and posts a full-text notification. Schedule it from Settings by setting the briefing time and restarting the app (auto-schedules on next launch).

## Quick-tile

Add "Ask LCDR" from the Quick Settings panel. Tapping it opens the app directly into the voice screen.

## Changing the backend URL

Edit `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://your-backend.example.com\"")
```

## Building for release

```bash
./gradlew assembleRelease
```

Sign the APK with your own keystore. This app is never intended for Play Store distribution.
