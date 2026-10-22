# LCDR TTS audit · T8V2 · 2026-10-21

Audited current `master` at `e45b96fd9ccd5a2e3df6a40e27807b61501bcaee`, the merge of [PR #2](https://github.com/johnmccallum399-max/locally-uncensored/pull/2). No Android device or emulator was used. Findings below describe source behavior and deterministic JVM tests, not observed phone behavior.

## Findings and corrections

| Priority | Reproduction / source finding | Correction |
| --- | --- | --- |
| High | While PROCESSING, tap Stop or Close. The original `stopListening()` stops STT/TTS but leaves the response coroutine collecting; a later Done still speaks. `SpeechRecognizer.stopListening()` can also deliver a final result after Stop. This is a pre-existing interruption defect, not introduced by PR #2. | Invalidate the turn before canceling recognition; destroy the old recognizer, cancel response collection, and reject stale callbacks. |
| High | Response A and B reuse `lcdr_last`. A delayed A completion during B, LISTENING or PROCESSING can set the interface to IDLE. Errors from any old utterance do the same. Source establishes this callback collision; it does not prove a particular engine delivers that ordering. | Unique per-playback IDs, active-ID filtering, invalidation before stop/flush, and Main-thread callback dispatch. |
| High | A rejected `speak()` or silence enqueue returns ERROR, but the original ignores it. If the final utterance is never accepted there need not be a completion callback. An asynchronous earlier-sentence error sets IDLE while remaining queued speech can continue. `onStop` is not handled. | Check every enqueue result; fail and stop the whole active queue; handle both error overloads and onStop. Ignore callbacks from canceled queues. |
| Medium | Given equal-quality local/cloud voices, the new ranking selects cloud; higher-quality cloud also wins. Filtering by language alone allows a different regional accent. Equal-rank ties depend on the engine's set iteration order. | Exclude network-required/uninstalled voices, prefer exact locale then country/script, quality, and stable voice name. No eligible voice means a visible error, not implicit cloud fallback. |
| Medium | A single sentence longer than `getMaxSpeechInputLength()` still becomes one request. Sentence splitting alone does not honor the engine limit. Quoted sentence endings also fail the old split regex. | Locale-aware sentence boundaries plus bounded splitting at spaces, with a surrogate-safe hard split for long tokens. |
| Medium | Unclosed backtick fences or tilde fences expose code to speech. Closed code blocks can produce a stray extra full stop. Parenthesized link targets leave dangling punctuation; heading/table markers and formatting-only input can reach speech. | Omit open/closed fences, including quote/list containers; improve link/heading/table/list cleanup and empty-input handling. Preserve readable labels and text. This remains a lightweight cleanup, not a complete CommonMark parser. |
| Medium | Initialization marks TTS ready without checking language/voice/rate/listener setup. No installed matching voice can silently leave an engine default in place. Null voice feature sets are dereferenced. | Readiness follows successful explicit offline voice, rate, pitch and listener setup; tolerate null features; show an actionable unavailable/startup error. Post initialization onto Main after constructor assignment. |
| Medium | The original collector can speak repeatedly on duplicate Done events, restart after an Error followed by Done, or remain PROCESSING after a thrown repository error. These are surrounding, pre-existing response-state gaps. | Consume one terminal event, catch response failures, and preserve coroutine cancellation. The shared chat repository is unchanged. |

## Policy fit

The Android README describes an **on-device STT/TTS modal**. Offline-only TTS is the conservative interpretation of that intent. The prior network bonus conflicts with it. The patch relies on engine-reported voice metadata; it is not a network sandbox or a proof that an engine never transmits data. No installed voice inventory was inspected here.

The app exposes voice mode and persona settings, but no pinned TTS engine, voice name, gender, accent or timbre. Quality ranking cannot establish a particular LCDR vocal character. Selection is now stable for an unchanged engine, locale and installed inventory; engine updates or voice installations may still change the winner. A cross-device character voice would need a separately specified voice package and pinning policy. No such identity was invented in this patch.

Rate **0.97**, pitch **1.0**, text response display, sentence order, and **120 ms** silence requests between chunks are preserved. Perceived pacing still depends on the engine. Speech still begins after the completed chat response; splitting does not make model output stream to speech sooner.

The backend chat remains network-dependent. The Android recognizer remains the existing default `SpeechRecognizer`; offline STT is not guaranteed. No assistant tools, model behavior, avatar code, or GYPZ work was changed. Canceling the voice collection is not a rollback of server work or device actions already executed.

## Verification

- **26 JUnit tests passed** on JDK 17 using Kotlin 2.0.21 and JUnit 4.13.2. Tests execute the production SpeechPolicy, SpeechPlayback and VoiceTurn helpers. The fake speech output injects queue failures and callbacks; it does not synthesize audio.
- The final policy tests were also run against a temporary adapter containing the original cleanup/split functions and original ranking expression: **12 of 15 tests failed** before the final nested-container test was added. This proves those algorithm-level failures, not Android engine behavior.
- Coverage: offline/missing/wrong-language voices; locale and deterministic ties; Markdown/fences/tables/links; quoted endings and decimals; oversized Unicode text; ordering/pauses/final-only completion; old/duplicate/null callbacks; cancellation/replacement; speech/silence rejection; asynchronous errors and recovery; turn invalidation and one-time recognition acceptance.
- A limited Kotlin compilation of VoiceViewModel and helpers against Android 15 `android-all` API classes and coroutines 1.9.0 passed. Temporary signatures stood in for lifecycle, Hilt/inject annotations, and ChatRepository. **This is not a full Android application compilation or lifecycle integration test.**
- `git diff --check` passed.
- Full build/test attempt: `gradle -p android :app:testDebugUnitTest :app:assembleDebug` failed with `gradle: command not found`. The checkout has wrapper properties/JAR upstream but no `gradlew` launcher. There is no configured Android SDK/device here. No APK was built or installed. No app-wide suite or instrumented test result is claimed.
- A separate source reviewer found no critical/important issue in the corrections and identified the nested-fence gap; its regression test now passes.

The build follow-up commits the wrapper launchers and adds Android CI. The results above remain the historical T8V2 source-level validation. Follow [README.md](README.md#repeatable-verification) for prerequisites and the current repeatable procedure; see [BUILD_VERIFICATION.md](BUILD_VERIFICATION.md) for the subsequent real-build results.

On an Android build host, run from `android/` using the committed wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

These helpers are standard `app/src/test` JUnit tests; no new test dependency is needed. Main-thread/lifecycle integration, actual repository cancellation, engine initialization and audio remain device/instrumentation gates.

## Required Android checks before merging

Record phone/Android version, TTS engine/version, device locale, selected voice name/locale, network-required flag, installed status and whether network is available. Test the user's device first, plus an API 26 emulator/device and a recent Android version where available.

| Check | Procedure and expected result |
| --- | --- |
| Voice identity | Reopen voice mode repeatedly with the same inventory; the selected voice stays the same. Audition the same short passage for acceptable accent/timbre and rate. There is no claim that quality metadata matches the user's taste. |
| Offline playback | With downloaded local voice data, compare the same response online and offline. Isolate TTS from backend/STT (e.g. a debug harness or disable connectivity after response receipt). Confirm speech still works and inspect engine traffic if a no-transmission guarantee is required. |
| No eligible voice | Remove local voice data or use a locale with only network voices. Expect actionable text error and IDLE, no cloud fallback and no hang. Restore data and recreate voice mode. |
| Startup / configuration | Enter voice mode during slow/failed engine startup; request a reply before readiness. Expect a visible error and IDLE, then successful speech once ready. Test engine rejection/missing language data. |
| Long response | Send several sentences including a >4,000-character sentence, a long unbroken token, emoji, quoted endings and abbreviations. Verify no truncation, repeat, reordered sentence or stuck SPEAKING state. Listen for natural boundaries; JVM and Android locale boundary rules may differ. |
| Stop | Stop during recognition, processing, the first sentence, a pause, and the final sentence. Audio stops and a delayed result/response cannot restart it. |
| Consecutive turns | Stop A, immediately start B; repeat rapidly. B stays LISTENING/PROCESSING/SPEAKING as appropriate despite A's delayed callbacks, with no old transcript or voice leaking into B. |
| Completion/error | Finish a normal multi-sentence answer; IDLE only after the last chunk. Inject queue rejection, synthesis error and engine stop with a debug/fake engine. The remaining queue stops, error is shown where applicable, and a subsequent request works. |
| Navigation/lifecycle | Close or use system Back during recognition, processing and speech; reopen. No speech resumes from the discarded turn and no old recognizer remains. Also check rotation, background/foreground and screen lock; no new background-audio policy was introduced. |
| Cleanup | Read headings, bullets, tables, inline code, ordinary/parenthesized links, and open/closed backtick/tilde code fences including quote/list containers. Labels remain intelligible and code/URL bodies are omitted. Review unfamiliar Markdown constructs manually. |

Build follow-up: full debug APK compilation, actual Gradle tests and lint results are now recorded in [BUILD_VERIFICATION.md](BUILD_VERIFICATION.md). ViewModel/lifecycle integration and the device checks above remain merge gates. Keep the correction in draft until their evidence is recorded.
