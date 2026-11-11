# TODO: Refactor ManyDrive with a Flexible Feature-First Architecture

> Scope: Kotlin + Jetpack Compose in the existing `:app` module. The goal is to make the codebase easier to maintain around real user-facing workflows, not to impose a rigid Clean Architecture template.
> Status: IN PROGRESS — feature packages, shared components and account/browser/action/transfer controllers are implemented; baseline and verification are recorded in [docs/refactor-verification.md](docs/refactor-verification.md). Mark an item `[x]` only after implementing and verifying it.

## Locale migration — 2026-11-11

- Replaced the custom locale catalog and matching logic with Android string resources in `app/src/main/res/values-*`. Resource names use lowercase English and underscores, as required by Android. Formatted strings use positional arguments.
- Android resource selection now determines the language, including system and per-app language preferences; instrumentation coverage exercises locale fallback, Chinese regional locales and formatted arguments.

## Latest Verified Increment — 2026-11-11

- The main source package root contains only `MainActivity.kt`; shell/platform bridges are under `app`, workflows under `feature`, and reusable components under `shared`.
- Split the large shell, browser/file-action/Photos/viewer UI, media service, and localization catalog by responsibility. Translation packs preserve the original ordered strings verbatim.
- Extracted account connection/profile persistence, browser loading/navigation/cache, file/listing mutations, and transfer orchestration from MainActivity. Launchers and cross-feature lifecycle/authorization coordination remain in the Activity.
- `Model` is a shared listing snapshot because browser, Photos and System Files consume it; browser state itself is owned by `BrowserController`. Its token/path semantics are unchanged. `StorageSession` is an account-owned snapshot used by file actions and transfers.
- Added stale account/tab/path request, token propagation, unsupported S3 tab and account-cache isolation JVM checks. Added instrumentation coverage for moved Android entry points and the shared local-media resolver.
- Rejected stale Drive authorization and path results before applying them, propagated cancellation in browser loading/search, and captured the source account for listing-mutation cache invalidation. JVM tests and debug/instrumentation APK builds passed with JDK 17; instrumentation was not run.
- No connected device/emulator: instrumentation execution, provider smoke tests and rotation/process recreation remain open. See [architecture](docs/architecture.md) and [verification](docs/refactor-verification.md).
- Next: device regression checks, account-token ownership improvements, and provider/storage ownership review. Remaining unchecked items are not implied complete by this structural refactor.

## Principles

- [ ] Keep a single Gradle module (`:app`) for now. Split into modules only when ownership boundaries, build performance, or dependency isolation provide a clear reason.
- [ ] Define a feature as a user workflow or a group of code that tends to change together. Do not automatically treat every screen, API, or storage provider as a separate feature.
- [ ] Do not require `domain/data/presentation`, UseCases, Repositories, ViewModels, or a DI container in every feature. Introduce abstractions when there are multiple consumers, meaningful duplication, testability problems, or real coupling.
- [ ] Distinguish two concerns: features (`accounts`, `browser`, `fileactions`, `transfers`, `photos`, `viewer/playback`, `systemfiles`, `settings`) and integrations/providers (Google Drive, S3, Service Account authentication, Google Photos). Do not move everything into `shared` or `core`.
- [ ] Keep code used by only one feature near that feature. Move it to `shared` only when it serves at least two independent features or represents a clear platform-level concern.
- [ ] Refactor in small PRs, preferably moving code without changing behavior first. Do not mix structural changes with UX updates, SDK upgrades, persistence migrations, OAuth changes, or cache redesign.
- [ ] Preserve the namespace/applicationId, Android manifest contracts, credential encryption, preference keys, and existing stored account data.

## Current Codebase and Main Pain Points

- `MainActivity.kt` (~1,703 lines): Android launchers/lifecycle, Google/Photos authorization, account management, browser state/cache, Drive/S3/Service Account calls, uploads/downloads, file mutations, and viewer coordination.
- `MainScreen.kt` (~1,307 lines): app shell, drawer, Files/Shared pager, Trash, Photos, System Files, Settings, search/navigation state, dialogs, and viewer overlay.
- `MainModels.kt`: mixes browser `Model`, `ViewerState`, `AccountType`/`AccountEntry`/`AccountUi`, `Tab`, `PhotosFolderUploadMode`, and `FileActionCallbacks`.
- `data/`: mixes SDK implementations, account stores, encryption, file-list cache, document upload/download, and local-file access.
- `ViewerCoordinator`, `PhotosLibraryController`, `DocumentUploads`, `DocumentDownloads`, `MediaPlaybackService`, and `LocalFileAccess` already have reasonably clear responsibilities. Preserve their working logic; do not rewrite them just to satisfy a naming or layering convention.
- `AppLanguageCatalog.kt` is large (~4,315 lines), but localization is cross-cutting. Do not split it into many feature-specific files without a concrete maintenance benefit.

## Suggested Structure — Not a Mandatory Directory Template

- `app/`: `MainActivity`, `AppRoot`/`MainScreen` (shell + routing), Android `ActivityResult` bridges.
- `feature/accounts/`: `AccountSwitcherUi`, account state/selection, import/connect/remove workflows; coordinates OAuth through an Android bridge.
- `feature/browser/`: Files + Shared, folder navigation, loading/search/cache state, `FileBrowserUi`.
- `feature/fileactions/`: `FileActionsUi`, sharing/permissions, rename, copy/move, trash/restore/delete.
- `feature/transfers/`: upload, download, cloud ↔ local, cloud → Google Photos, progress/notification orchestration.
- `feature/photos/`: `GooglePhotosPage`, `PhotosLibraryController`, library reading and authorization state.
- `feature/viewer/`: `ViewerCoordinator`, preview UI, playback queue/service, external media opening, media caching/prefetch. Split viewer and playback into separate features only if their lifecycles or consumers justify it.
- `feature/systemfiles/`: `SystemFilesPage`, `LocalFileActions`, local access and navigation.
- `feature/settings/`: `SettingsPage`, theme/preferences/clear cache.
- `provider/drive/`, `provider/s3/`, `provider/photos/`: SDK wrappers/clients when multiple features use them. Service Account auth may belong partly in accounts and partly in the Drive integration, depending on actual dependencies.
- `shared/`: file models genuinely shared across features, reusable UI components, theme, i18n, and cache primitives with multiple consumers.

Do **not** create all these directories upfront. Introduce a package when code with a clear owner is moved into it. Do not replace `MainActivity` with another giant `MainController`.

## P0 — Establish a Baseline and Protect Against Regressions (First)

- [x] Record the existing behavior of Google, S3, and Service Account providers. Preserve currently unsupported capabilities; do not silently add them during refactoring.
- [x] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`; save the baseline results. Document blockers if the Android SDK/JDK is unavailable.
- [x] Run Android instrumentation tests when a device/emulator is available (content URIs, Media3 playback, Trash navigation, S3 playback sources, progress persistence).
  - Star 4, Android 11: all 18 current instrumentation tests passed, including resource locale tests, with the device unlocked; see [verification](docs/refactor-verification.md).
- [ ] Smoke-test account add/switch/remove/restore; Files/Shared navigation, search, refresh/cache; Trash actions; file/folder uploads, downloads, copy/move, cloud ↔ local; Photos listing and upload; image/text/audio/video previews; mini-player/background playback; `ACTION_VIEW` with `content://` and `file://`; settings and cache clearing.
- [ ] Add tests for stale requests completing after an account/tab/path change, account-scoped cache invalidation, errors/cancellation, safe S3/local paths, and capability-based action visibility.
  - [x] Cover stale account/tab/path results, account-scoped cache invalidation, and existing path-safety cases in JVM tests.
  - [x] Check the enabled tab matrix for Google, Service Account, S3 and no account in a JVM test.
  - [ ] Complete error/cancellation and capability-based action visibility coverage.
- [ ] Verify rotation/process recreation with permission pickers and pending uploads; preserve the `onSaveInstanceState` contract.

## P1 — Move Code to Its Owner Without Changing Behavior

- [x] Move `AccountType`, `AccountEntry`, and `AccountUi` from `MainModels.kt` to accounts; `ViewerState` and preview helpers to viewer; `PhotosFolderUploadMode` to photos/transfers; `FileActionCallbacks` to fileactions.
- [x] Give browser state a browser-owned controller. Keep the unchanged `Model` snapshot under `shared/model` because Photos and System Files also consume it; preserve token/path semantics.
- [x] Relocate `AccountSwitcherUi`, `FileBrowserUi`, `FileActionsUi`, `GooglePhotosPage`, `SystemFilesPage`, `SettingsPage`, and viewer components according to feature ownership.
  - Completed: feature UI, viewer/playback packages, shell drawer, shared file items/media helpers/theme/i18n. External Activity compatibility is retained by a manifest alias; instrumentation tests compile, but execution needs a device.
- [ ] Relocate `DriveApi`/`GoogleApiClient`, `S3Api`, `PhotosApi`, and storage helpers based on actual usage. In move-only PRs, limit changes to packages/imports, not APIs or behavior.
- [ ] If `ExternalMediaActivity` or `MediaPlaybackService` changes package, update `AndroidManifest.xml`, service/intent declarations, and external-intent playback tests.
  - [x] Update manifest, service/intent declarations, and instrumentation test sources; compile the instrumentation APK.
  - [x] Run the existing external-intent entry-point and local resolver tests on a device.
- [ ] Keep account JSON storage, AES-GCM key aliases, selected account IDs, preference keys, and file-cache keys unchanged.
  - [x] Compare persistence declarations and keys against the pre-refactor snapshot; no changes found.
  - [ ] Verify restored account data and cache behavior on a device.
- [ ] For every PR: compile, run relevant tests, and smoke-test affected flows. Do not reformat the entire translation catalog.

## P2 — Accounts and Browser: Reduce the Largest Sources of Coupling

- [ ] Extract account orchestration from `MainActivity`: restore active account, choose Google account, connect S3, import Service Account, select/remove/sign out. Keep `ActivityResultLauncher` in the activity/bridge when appropriate.
  - Saved-account restore/connect/import/remove/profile logic now lives in `AccountController`. Picker/authorization bridges and cross-feature reset on activation/sign-out remain in MainActivity; full session ownership is still pending.
- [ ] Make sessions/tokens account-scoped; avoid permanently storing the Google access token inside the browser `Model`. Keep Google access tokens in memory, Service Account tokens tied to their credentials, and Google Photos permissions separately scoped.
- [x] Extract `refresh`/`loadDrive`/`loadS3`/`loadService`/`searchDrive`/`openFolder`/`goUp`/cache logic from `MainActivity` into a browser-owned controller or suitably small state holder.
- [ ] Preserve `BrowserRefreshTarget(accountKey, tab, path, requestId)` or equivalent behavior: stale async results must never overwrite a different account/tab/path. Files and Shared must retain independent snapshots when switching tabs.
  - [x] Check account, tab request identity, and path before applying listing results; cover stale results and independent tab snapshots in JVM tests.
  - [ ] Verify the complete asynchronous flow with provider authorization and live requests.
- [ ] Preserve cache keys scoped by account + location, cache-first rendering, and forced network refresh.
- [ ] Preserve provider behavior: Google/Service Account support Files and Shared subject to permissions; S3 supports Files only; Google Photos and Trash remain available only where currently supported.
- [x] Separate the app shell (drawer, top/bottom bars, overlay, navigation) from Browser/Trash/Photos/SystemFiles/Settings page content. Do not force Navigation Compose if existing navigation is sufficient.
- [ ] Reduce the number of unrelated callbacks in the `App` composable. Group state/callbacks into **feature contracts** when that improves readability or testing; do not create an all-purpose facade.
- [ ] Test switching accounts during loading, switching tabs during refresh, folder back navigation, account restoration, token propagation, and Photos authorization changes.

## P3 — Give File Actions and Transfers Clear Boundaries

- [ ] Separate `FileActionsUi` from mutations: rename, share, permissions, copy/move, trash/restore/delete. Keep UI decisions in fileactions and provider calls in the appropriate integration.
- [x] Document the existing capability matrix instead of pretending all providers behave identically:
  - Google Drive: Files/Shared/Trash/Search and Drive mutations, subject to permissions.
  - S3: Files, upload/download, direct deletion; no Shared/Trash/Drive-wide search; copying within the same S3 account is currently unsupported.
  - Service Account: Drive API with its own credentials/token; not equivalent to user Google OAuth. Root and shared-folder access depend on permissions and account capabilities.
- [ ] Add small interfaces or `StorageCapabilities` only if repeated `when(account.type)` branches across browser/fileactions/transfers warrant it. Do not force S3 to implement Drive-specific operations.
- [x] Extract `upload()`, `downloadFile()`, `uploadLocalFile()`, `transferLocalFileToCloud()`, `transferCloudToLocal()`, and Photos upload orchestration from `MainActivity` according to their workflows.
- [ ] Reuse `DocumentUploads`, `DocumentDownloads`, `LocalFileAccess`, and `UploadNotifications`; preserve streaming, temporary-file cleanup, folder recursion, progress, and cancellation.
- [ ] During local/cloud moves, preserve path validation, safe file names, no-overwrite rules, and deletion of the source only after success. Do not suppress per-file errors.
- [ ] Keep the account and path/tab snapshot captured when an upload/mutation starts. Completion callbacks must not refresh or mutate another account's UI; invalidate only the correct account's cache.
  - [x] Capture the source account for listing-mutation cache invalidation, including partially completed Trash operations.
  - [ ] Verify all transfer and mutation completion paths against account and location changes.
- [ ] Test unsupported actions, partial success/failure, folder deletion/copy, cancellation, and retry behavior.

## P4 — Viewer and Playback: Refactor Around Lifecycle, Not File Count

- [x] Group `ViewerCoordinator`, `FileViewerPage`, `ExpandableMediaPlayer`, `PagedPlaybackPlayer`, `MediaPlaybackService`, `VideoCache`, `ThumbnailRepository`, `LocalMediaPlayback`, `LocalMediaTypes`, and `ExternalMediaActivity` according to their viewer/playback responsibilities.
- [ ] Preserve the `MediaSession`/`MediaController` lifecycle, background playback, notifications, mini-player/fullscreen behavior, queue/swipe/seek/next/previous, playback progress restoration, and preview caching.
- [ ] Extract playback-source resolution for Drive/S3/Photos/local only where it meaningfully reduces the viewer's SDK dependencies; preserve handling of expired URLs/credentials.
- [ ] Preserve `ACTION_VIEW` content-URI permissions, file-URI support, opening media without sign-in, and playback restoration.
- [ ] Extend existing instrumentation tests. Do not upgrade or replace the Media3 stack in the same PR solely for architectural consistency.

## P5 — Google Photos, System Files, and Settings

- [ ] Move Photos library UI, read state, and authorization into photos. Keep Photos library read scope separate from Photos upload scope and Drive scope.
- [ ] For Drive/S3/Service Account → Google Photos transfers, retain the cross-feature workflow under transfers/photos. Preserve correct Google destination selection, folder RAW/ALBUM modes, recursive traversal, media filtering, temporary-file cleanup, progress, and failure counts.
- [ ] Let System Files own local browsing, folders, search, and local actions. Viewer and transfers should use small entry points/interfaces instead of duplicating playback or upload logic.
- [ ] Let Settings own theme/`superDark`/`showHiddenSystemFiles`/cache clearing if doing so simplifies state ownership. Preserve preference keys and behavior.
- [x] Migrate translations to Android string resources, retaining the existing translations and formatted placeholders.

## P6 — Conditional Cleanup and Final Verification

- [ ] Review dependency direction: feature → shared/provider where appropriate; minimize feature → another feature's internals. Extract shared code only when multiple consumers justify it.
- [ ] Remove obsolete imports, callback forwarding, and old state after replacements work and tests pass. Avoid two sources of truth.
- [ ] Consider renaming `DriveFile` to a provider-neutral model **only after** demonstrating that its current name causes real confusion in S3/Photos/local flows. Do not introduce elaborate per-provider domain mappings by default.
- [x] Update the README with the new code layout, test commands, provider capabilities, and SDK requirements (README mentions SDK 35, while `build.gradle.kts` uses `compileSdk 36`).
- [ ] Re-run all relevant tests and the P0 smoke checklist across providers, including rotation, stale requests, playback, and external intents.
- [ ] Keep the final PR focused on cleanup, documentation, and regression fixes; do not bundle new features.

## Suggested PR Sequence

1. Baseline tests and straightforward code moves (P0/P1).
2. Accounts and authorization/session boundaries.
3. Browser state, refresh/cache, and app shell.
4. File actions, transfers, and minimal capability checks.
5. Viewer/playback as a separate PR.
6. Photos, System Files, and Settings (split further if needed).
7. Cleanup, README, and regression verification.

**Definition of Done for each PR:** the build and relevant tests pass (or blockers are documented); stored data remains compatible; existing provider/permission behavior is preserved; stale state and callbacks cannot affect the wrong account; code ownership is clearer; and the diff is small enough to review or revert independently.

**Avoid refactoring for appearances:** forcing three layers into every feature; adding interfaces with only one implementation; premature Gradle modularization; dumping helpers into `core`; renaming every class; or changing SDKs/navigation and architecture in the same PR.
