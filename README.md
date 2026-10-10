# J.A.R.V.I.S.

**Just A Rather Very Intelligent System** — a fully on-device, voice-first AI butler for Android. No cloud LLM. No data leaving the handset. A local Gemma-3 language core, wake-word activation, streaming speech recognition, spoken replies, and a JSON tool-calling agent loop that controls the device.

```
 ┌─────────────────────────────  ANDROID APP (Kotlin + Compose)  ─────────────────────────────┐
 │                                                                                            │
 │   "Jarvis." ──► Porcupine Wake Word ──► HUD auto-listen ──► SpeechRecognizer (on-device)   │
 │                                                        │                                   │
 │                                                        ▼                                   │
 │                                              AgentOrchestrator                             │
 │                                               │            │                               │
 │                    CommandFastPath (deterministic)        LLM turn (Gemma-3 1B, MediaPipe) │
 │                    alarms · timers · torch                persona + tool manifest prompt   │
 │                         │                                       │                          │
 │                         ▼                                       ▼                          │
 │                   ToolRegistry  ◄──────────────  JsonActionParser ({"tool": ...})          │
 │                    alarms/timers/reminders · flashlight · volume · brightness              │
 │                    weather (Open-Meteo) · calendar · briefing · app launch                 │
 │                    web search · calculator · Korean tutor                                  │
 │                         │                                       │                          │
 │                         ▼                                       ▼                          │
 │                 device effects                    tool synthesis pass → streamed text      │
 │                                                                            │               │
 │                                                                            ▼               │
 │                                                            JarvisTtsEngine (sentence-queue)│
 └────────────────────────────────────────────────────────────────────────────────────────────┘
```

## Capabilities

| Area | Detail |
|---|---|
| **Voice loop** | Tap the orb or say **"Jarvis"** (wake word) → on-device streaming ASR → streamed LLM reply → sentence-queued TTS (prefers en-GB) |
| **Language core** | Gemma-3 1B IT (4-bit `.task` bundle) via Google AI Edge / MediaPipe GenAI. GPU backend with silent CPU fallback. Entirely local |
| **Agent loop** | Compact persona prompt + tool manifest → JSON tool-call extraction (markdown-fence & prose tolerant) → execution → second-pass in-character synthesis |
| **Fast path** | High-frequency commands ("set an alarm for 7", "torch on", "timer for 5 minutes", "weather in Seoul") skip the LLM entirely: <100 ms, works with **no model loaded** |
| **Device control** | Alarm/timer/reminder scheduling (exact alarms, boot-persistent), flashlight, volume, brightness, system panels, app launching, battery telemetry |
| **Briefing** | Time + battery + calendar (next 12 h) + weather, rendered in persona |
| **Korean tutor** | Scaffolded mini-lessons (Hangul → romanization → breakdown → dialogue → practice) from the local model |
| **Modes** | `HUD` (terse executive), `TACTICAL` (deep analysis), `AUTONOMOUS` (proactive) — switchable in-app, changes both prompt and tone |

## Requirements

- **Android Studio** Ladybug or newer (AGP 8.7.3, Gradle 8.10.2, Kotlin 2.0.21)
- **Device:** Android 9 (API 28)+. On-device LLM inference realistically wants a **Pixel 8 / Galaxy S23 class or newer** (8 GB+ RAM). Emulators are *not* supported by the MediaPipe GenAI runtime.
- **JDK 17** (bundled with Android Studio)

## Setup

### 1. Open & build

```bash
git clone <this repo>
# Open in Android Studio and let it sync, or:
./gradlew :app:assembleDebug
```

### 2. Install the language core (Gemma-3 1B)

1. Download **Gemma-3 1B IT, 4-bit** in `.task` (LiteRT/MediaPipe) format from
   [`litert-community/Gemma3-1B-IT`](https://huggingface.co/litert-community/Gemma3-1B-IT) on Hugging Face.
2. Push it to the device **or** import it through the app:
   - *Via app:* Settings → **Import model** → pick the downloaded `.task` file. It is copied into app-private storage and registered.
   - *Via adb (faster for 600 MB+ files):*
     ```bash
     adb push gemma3-1b-it-int4.task /data/local/tmp/llm/
     ```
     then Settings → import is unnecessary — the engine can load any path you register; for `/data/local/tmp` builds, use the app import or point `model_path` at the file your debug build can read (app-private import is the reliable path on scoped-storage devices).
3. First inference after load takes a few seconds; the HUD status line reads **CORE ONLINE · GPU** (or CPU) when ready.

### 3. Wake word (optional)

The built-in **"Jarvis"** keyword runs on [Picovoice Porcupine](https://picovoice.ai) — free tier, fully on-device:

1. Get a free **AccessKey** at [console.picovoice.ai](https://console.picovoice.ai).
2. Settings → Wake Word → paste the key → enable the switch.
3. A persistent low-priority notification appears while listening (Android foreground-service requirement). Say **"Jarvis"** — the HUD auto-starts listening when the app is open.

Without a key, everything except the wake word still works via tap-to-talk.

### 4. Permissions

| Permission | Why | When asked |
|---|---|---|
| `RECORD_AUDIO` | speech input | on first launch |
| `POST_NOTIFICATIONS` | alarms, service notice | on first launch (13+) |
| `SCHEDULE_EXACT_ALARM` | precise alarms/timers | Settings → *Exact alarm permission* button |
| `READ_CALENDAR` | briefings & scheduling queries | first calendar/briefing use |
| Location (coarse) | local weather fallback | optional — default city setting suffices |
| `WRITE_SETTINGS` | brightness control | via system screen when requested |
| `QUERY_ALL_PACKAGES` | "open Spotify" by name | manifest-declared; personal-build convenience |

## Project layout

```
app/src/main/java/com/jarvis/ai/
├── JarvisApplication.kt          # process root: DI init, channels, model pre-load
├── MainActivity.kt               # single-activity Compose host + permission gate
├── di/AppContainer.kt            # explicit hand-rolled DI graph
├── core/
│   ├── AgentModels.kt            # AgentMode · AgentState · ChatMessage · AgentEvent
│   ├── agent/
│   │   ├── AgentOrchestrator.kt  # THE loop: fast path → LLM → tools → synthesis
│   │   ├── PersonaPrompts.kt     # compact butler system prompts
│   │   ├── ConversationMemory.kt # rolling dialogue window
│   │   ├── JsonActionParser.kt   # brace-matching, fence-tolerant tool-call extraction
│   │   ├── TimeParser.kt         # "quarter to six", "19:30", "5 minutes" → instants
│   │   └── CommandFastPath.kt    # deterministic no-LLM command intents
│   ├── llm/LlmEngine.kt          # backend-agnostic interface (+ MediaPipe impl)
│   ├── asr/AndroidSpeechRecognizer.kt
│   ├── tts/JarvisTtsEngine.kt    # sentence-queued streaming speech
│   ├── wakeword/PorcupineWakeWordEngine.kt
│   ├── tools/                    # Tool, ToolRegistry, evaluator + impl/ suites
│   └── services/WeatherService.kt# Open-Meteo, keyless, geocode-cached
├── scheduling/                   # AlarmScheduler · PersistedAlarms · receivers · notifications
├── service/WakeWordService.kt    # foreground mic service (FGS type: microphone)
├── data/SettingsRepository.kt    # DataStore preferences
└── ui/                           # theme + HUD + settings (Compose, Material 3)
app/src/test/                     # JUnit: parser, time, arithmetic, fast-path suites
```

### Design notes

- **Why a fast path?** A 1B model is a brilliant conversationalist and an unreliable alarm clerk. Anything with a deterministic grammar (times, timers, toggles) never touches the model. This is the single highest-leverage reliability decision in the codebase.
- **Why hand-rolled DI?** The graph is ~10 lazy singletons. Hilt's annotation-processor cost buys nothing at this scale and slows every clean build.
- **Streaming semantics:** MediaPipe `generateResponseAsync` delivers **incremental deltas** (verified against Google's reference sample) — the orchestrator appends them, the HUD renders a live cursor, TTS speaks per sentence before generation completes.
- **Graceful degradation ladder:** model loaded → full agent. Model missing → fast path + canned butler lines. No mic → text input. No network → everything except weather/search.

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

Covers the pure-Kotlin core: `JsonActionParser` (adversarial LLM output), `TimeParser` (meridiem edges, word times, durations), `ArithmeticEvaluator` (precedence, right-associative exponent, malformed input), `CommandFastPath` (intent routing and negative cases).

## Roadmap

- [ ] LiteRT-LM migration (MediaPipe LLM Inference API is maintenance-mode; `LlmEngine` isolates the swap)
- [ ] Vosk/Whisper.cpp ASR option behind the existing recognizer interface
- [ ] Persistent conversation memory + Korean vocabulary spaced-repetition ledger
- [ ] Autonomous multi-step chains (briefing → calendar conflict resolution → suggested actions)
- [ ] Lock-screen/quick-tile orb; Wear OS companion
- [ ] Function-calling via constrained decoding once LiteRT-LM lands

## License & model terms

App code: MIT. **Gemma is governed by Google's Gemma Terms of Use** — you are responsible for complying with them when downloading/running the model. Picovoice Porcupine requires their free-tier terms (one AccessKey per developer).
