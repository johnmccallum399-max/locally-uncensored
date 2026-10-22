# Android build verification · T8K4 · 2026-10-22

Build follow-up to draft PR #3, based on `606569f89742487f867f99721555bae5333a2a82`. The original 26-test standalone Kotlin result and temporary-signature compilation are historical source-level evidence in [TTS_AUDIT.md](TTS_AUDIT.md).

## Reproducible build changes

- Commit generated Gradle 8.9 POSIX/Windows launchers, preserving the existing official wrapper JAR. Both JAR and distribution match Gradle's published SHA-256 values. The distribution checksum is enforced in wrapper properties.
- Enable `android.useAndroidX=true`; otherwise `checkDebugAarMetadata` fails before app compilation. Set a 2 GiB Gradle heap.
- Select KSP2 and pin KSP 2.0.21-1.0.28. KSP1 reproduced duplicate `byRounds` generated classes even after clean builds with incremental processing disabled. KSP2 avoids that shadow-output path; the .28 patch provides the processor-option API required by Hilt 2.55.
- Align Room runtime/compiler/ktx at 2.7.2, whose release line supports Kotlin 2.0/KSP2. Room 2.6.1's processor crashed on the existing suspend DAO methods with `unexpected jvm signature V` under KSP2. Database entities and schema version remain unchanged; no destructive migration was introduced.
- Align the Hilt plugin/runtime/compiler at 2.55 for KSP2 task configuration. The older plugin did not pass the required Android entry-point processor option to KSP2 and failed despite being applied.

Hilt 2.55's [own plugin build](https://github.com/google/dagger/blob/dagger-2.55/java/dagger/hilt/android/plugin/build.gradle) pins Kotlin 2.0.21 and KSP 2.0.21-1.0.28. The [KSP .28 release](https://github.com/google/ksp/releases/tag/2.0.21-1.0.28) adds `KspAATask.commandLineArgumentProviders`. Kotlin/Compose, AGP and Gradle versions remain unchanged.
- Correct the nonexistent framework theme `android:Theme.Material.NoTitleBar` to the SDK's no-action-bar theme `android:Theme.Material.NoActionBar`; otherwise AAPT resource linking fails.
- Use the existing AndroidX `FragmentActivity` base instead of the unresolved `AppCompatActivity`. It supports Compose, Hilt and the existing `BiometricPrompt` without adding an AppCompat theme/dependency.
- Repair Room's invalid conversation-ID aggregate query with `GROUP BY conversationId`, preserving the intended distinct IDs ordered by their latest message. SQLite independently reproduced the old `misuse of aggregate: MAX()` error and verified ordering/deduplication for the corrected production query.
- Match the existing `MasterKey`/encrypted-preferences API to `security-crypto` 1.1.0 (1.0.0 lacks that API); use Retrofit's generic `Response` in the SSE parser; declare the tool-success content property override; add missing animation and `viewModelScope` imports. These are compiler-reported API/type/import failures, not voice redesigns.
- Add Android Actions verification for drafts/PRs and `master`: real SDK/JDK setup, wrapper validation, JVM tests, debug APK packaging, lint and retained reports/APK. No fake Android/dependency signatures or ignored failures.
- Document identical local commands and separate device gates in [README.md](README.md#repeatable-verification).

The TTS implementation and its existing tests are unchanged by this build follow-up.

## Verification host

Linux x86_64; full Temurin JDK 17.0.21+9; Gradle 8.9; Android SDK command-line tools 12.0 (11076708); Android API 35 platform revision 2; Build Tools 34.0.0; AGP 8.7.0; Kotlin/Compose 2.0.21; KSP 2.0.21-1.0.28 (KSP2); Room 2.7.2; Hilt 2.55. Dependencies resolve from the declared Google/Maven/Gradle repositories. Host-specific proxy/CA settings are outside the repository. The preinstalled Java runtime lacked `javac`/`jlink` and was insufficient; the complete Temurin archive was downloaded and checksum-verified before the final build.

## Results

Final successful verification, run from `android/` with the complete SDK/JDK:

```sh
./gradlew --no-daemon --console=plain --stacktrace :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

| Verification | Outcome |
| --- | --- |
| Actual Gradle JVM tests | **26 passed**, 0 failures/errors/skips: SpeechPolicy 16, SpeechPlayback 7, VoiceTurn 3. |
| Full debug APK | **Compiled and packaged**, including real Android resources, Room/Hilt generation, Kotlin/Java and DEX. `app/build/outputs/apk/debug/app-debug.apk`, approximately 21 MiB. |
| APK inspection | `aapt dump badging` confirms `com.lcdr.assistant.debug`, min SDK 26, target/compile SDK 35. `apksigner verify` exits 0. |
| Android lint | **Passed: 0 errors, 15 warnings.** Three Navigation 2.8.5 custom-check registries are skipped because their lint analysis API is obsolete. This is an explicit coverage limit, not complete Navigation lint coverage. |
| Workflow/launcher/static diff | `actionlint`, `sh -n android/gradlew` and staged `git diff --check` pass. POSIX launcher is committed as mode 100755; attributes preserve launcher line endings. |
| Device/emulator/runtime audio | **Not run.** No installation, speech synthesis or live backend request performed. |

The final command exited 0: `BUILD SUCCESSFUL in 2m 27s`, 60 tasks (24 executed, 36 up-to-date). The prior clean run produced passing tests and a full APK, then exposed six lint errors; the final run regenerated processing output and rebuilt after the lint fixes below.

The tests use the actual application's Gradle dependencies and production helper sources. They remain JVM tests and do not validate Android scheduling, callbacks, lifecycle or audible voice identity. The TTS sources and test files are byte-for-byte unchanged from the original PR head.

## Lint fixes and limits

The initial lint run found six errors. Remove unused exact-alarm permissions (briefing uses WorkManager, alarm tools use `AlarmClock` intents); declare telephony optional; remove only WorkManager's default startup initializer so the application's existing custom `Configuration.Provider`/Hilt worker factory is used. Other AndroidX startup initializers remain. This fixes worker initialization and needs a device smoke test.

The tile already uses PendingIntent on API 34+ and Intent below 34. A scoped lint suppression documents the necessary legacy overload without changing its version guard or behavior. No lint baseline or blanket error suppression was added.

Warnings remain visible: three obsolete Navigation custom-check registries, scoped/selected-photo storage behavior, newer dependency availability, and icon/resource advisories. Compiler deprecation warnings also remain. No library-wide upgrade or storage migration was attempted merely to silence warnings.

## Remaining merge gates

- A successful GitHub Actions run for the final PR revision, with reports retained.
- ViewModel/lifecycle integration validation and actual-device checks from [TTS_AUDIT.md](TTS_AUDIT.md#required-android-checks-before-merging), including stop during processing/speech, stale callbacks, offline voice identity and recovery.
- App startup, biometric gate, saved preferences and existing database read/write, Quick Settings tile and briefing worker initialization smoke checks after the dependency compatibility updates. Compilation does not prove those runtime paths.
- No physical device, emulator, speech synthesis, app installation or live backend request was used for this build work. API 26 behavior and real audio remain unverified.

PR #3 must remain draft until the required verification has recorded evidence. This work does not authorize merging, deployment or installation.
