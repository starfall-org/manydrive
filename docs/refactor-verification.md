# Feature refactor verification

Date: 2026-11-10. Starting revision: `297c902`, with pre-existing uncommitted feature-model moves and README updates retained.

## Provider baseline

This is the existing behavior documented in the README and implemented by the current account/tab/action branches; live provider checks remain pending.

| Provider | Browsing | Transfers/actions | Constraints |
| --- | --- | --- | --- |
| Google | Files, Shared, Trash, Drive search | Drive mutations, uploads/downloads, Photos uploads | Subject to permissions; Photos read and upload permissions remain separate from Drive |
| S3 | Files | Upload/download, direct deletion, Photos uploads | No Shared, Trash, Drive-wide search or internal S3 copy |
| Service Account | Files and Shared, Drive operations subject to granted permissions | Separate credentials/token, Photos uploads to a connected Google destination | Own root can be empty; no upload to its root; no domain-wide impersonation |

System Files and external media opening remain usable without cloud sign-in. No provider capabilities were added in this increment.

## Baseline checks before UI relocation

- Default JDK 25.0.5: Gradle failed during startup with `25.0.5`.
- Existing JDK 17 at `/tmp/manydrive-jdk17`, SDK at `/opt/android-sdk`: `:app:testDebugUnitTest :app:assembleDebug --offline` passed (test outputs were up to date).
- `adb devices`: no connected devices; no emulator available for instrumentation or manual provider checks.

## Change scope

The initial model/UI relocations were followed by the requested source reorganization: only MainActivity remains at the main source package root. Shell/platform/previews moved into app; viewer/playback and account/browser/file-action/transfer orchestration now live under their features. Large UI files were split into adjacent components. Shared file items, loading, media metadata/thumbnails, theme and localization have multiple consumers or platform responsibilities.

AccountController owns saved connections/restoration/profile updates; BrowserController owns tab/listing/request/cache state. FileMutations and ListingMutations own provider mutations, with generation invalidation coordinated by the Activity. Transfer classes capture StorageSession at operation start and retain account/location checks on completion. Android launchers, OAuth bridges and saved-instance keys remain in MainActivity.

Model moved unchanged to shared/model because browser, Photos and System Files consume it. Its existing token/path semantics are retained; full account-token ownership is still a pending TODO. Shared source has no feature imports. System Files deliberately continues reusing browser presentation.

The localization catalog was split by language. All 12 exact/template lists were compared with the pre-reorganization source: ordered list literals are preserved verbatim. All provider/persistence declaration bodies were also compared with the snapshot, excluding package/import lines and blank lines: unchanged. Application ID, namespace, preference/cache keys and credential encryption/storage formats remain unchanged.

MediaPlaybackService and ExternalMediaActivity moved to feature packages. The manifest and MediaController references were updated; the previous explicit external-media Activity component remains as an alias. Android entry-point compatibility and shared content/file URI resolution have instrumentation coverage, but device execution remains pending.

See [architecture](architecture.md) for the current tree and before/after file sizes.

## Checks after relocation

Command (local toolchain paths are environment-specific):

```bash
JAVA_HOME=/tmp/manydrive-jdk17 ANDROID_HOME=/opt/android-sdk ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline
```

Results: **BUILD SUCCESSFUL**. All 48 JVM tests passed (0 failures/errors/skips); debug APKs and the instrumentation APK assembled successfully. The seven added JVM tests cover stale account/folder results, per-tab completion routing, account reactivation, token propagation, unsupported S3 tabs and account-scoped cache invalidation. Existing localization, provider, path-safety and gesture tests also pass. Existing compiler deprecation/nullability warnings remain; there were no compilation errors.

## Device checks still required

Instrumentation APK compilation does not execute tests. `adb devices` returned no attached device/emulator. An attempted `:app:connectedDebugAndroidTest --offline` failed while resolving the uncached UTP artifact `com.android.tools.utp:android-test-plugin-host-additional-test-output:31.9.1`; instrumentation tests were not executed. Run `:app:connectedDebugAndroidTest` with a device/emulator, then complete the P0 smoke checklist in `TODO.md` using suitable Google/S3/Service Account credentials and Photos media. Account switching during requests, rotation/process recreation, pickers/pending uploads, external content/file intents, mini-player/background playback and provider error/cancellation behavior remain unverified at runtime in this environment.

## Follow-up — 2026-11-11

Browser refreshes now check the captured path as well as account and request identity, and a stale Drive authorization result is rejected before token-related callbacks run. JVM checks cover both cases. Browser search and S3/Service Account loading propagate coroutine cancellation. Listing mutations capture the source account for cache invalidation, including partial Trash operations, and propagate cancellation.

With the JDK 17 and Android SDK paths above, `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline` passed. The default JDK 25 still fails at Gradle startup. `adb devices` again listed no devices, so instrumentation and provider smoke checks remain open.

An attempt to connect ADB over Tailscale to `star-4` (`100.118.179.58:43333`) did not complete; a bounded TCP connection also timed out, and `adb devices` remained empty. Tailscale TSMP ping did receive a reply, which confirms the peer is reachable at the Tailscale layer but does not establish that the Android ADB TCP service is reachable. Instrumentation tests were not run in this attempt.

The executor's Tailscale runs in userspace with a local SOCKS5 proxy; forwarding ADB through that proxy connected successfully to Star 4 (Android 11). `:app:connectedDebugAndroidTest` ran 16 tests: 12 passed and 4 failed. `AndroidEntryPointsTest` failed because its temporary file is outside the configured FileProvider cache root and because the expected activity resolution returned null. `MediaPagerTest` and `TrashNavigationTest` failed with no Compose hierarchy found. These failures require investigation and a rerun; the test run alone does not complete the device smoke checklist.

A subsequent `:app:connectedDebugAndroidTest --offline` rerun on the same device again ran 16 tests: 12 passed and the same 4 failed. No fixes were applied between these two runs.

The entry-point tests were then corrected to use the actual legacy activity alias and a file inside the configured `shared-exports` FileProvider root. JVM tests and instrumentation APK assembly passed. A further device run passed 14 of 16 tests, including both corrected entry-point tests; the Media Pager and Trash Compose tests still reported no hierarchy. The device reported `Dozing` and `isKeyguardShowing=true` before this run. An unlocked-device rerun is needed to establish whether that explains both remaining failures.

With Star 4 unlocked, Media Pager passed and Trash exposed a separate test issue: its selectors assumed English labels. After changing the test to use the app's translated labels, `:app:connectedDebugAndroidTest --offline` passed all 16 tests on Star 4 (Android 11). Manual provider smoke tests and rotation/process recreation remain open.

After the Android string-resource migration, `:app:connectedDebugAndroidTest --offline` passed all 18 tests on the same device, including the locale-resource tests. `:app:testDebugUnitTest --offline` also passed with an added provider tab capability matrix; debug and instrumentation APK assembly succeeded. These checks do not cover live provider accounts, real transfers, or pending picker/process recreation.
