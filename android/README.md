# LCDR — Personal Device Assistant (Android)

Private-use Android native app. Not for Play Store distribution.

## Requirements

- Android Studio Ladybug (2024.2+) or newer
- JDK 17+
- Android device / emulator API 26+
- Backend running at `https://ai-assistant-backend-dhi6.onrender.com`
  (or your own instance — change `API_BASE_URL` in `app/build.gradle.kts`)

## Setup

```bash
# 1. Open android/ as the project root in Android Studio
# 2. Let Gradle sync (first sync downloads ~500 MB of dependencies)
# 3. Connect a physical device (preferred — several device APIs don't work in emulator)
# 4. Run the :app target
```

### Gradle wrapper (first time)

If the `gradlew` binary isn't present, generate it from inside the `android/` directory:

```bash
cd android
gradle wrapper --gradle-version 8.9
```

Or open in Android Studio — it will offer to download the wrapper automatically.

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
