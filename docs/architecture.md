# Source architecture

The app uses a single `:app` Gradle module. Only `MainActivity.kt` is placed directly in the main source package root. Android namespace and application ID remain `com.starfall.gsadrive`.

```text
com/starfall/gsadrive/
├── MainActivity.kt
├── app/
│   ├── MainScreen.kt, AppNavigationDrawer.kt, AppPageTopBar.kt, AppTab.kt
│   ├── platform/  # document picker and system-bar bridge
│   └── preview/   # design previews and preview-only sample UI
├── feature/
│   ├── accounts/    # account controller, connection dialogs, models, StorageSession
│   ├── browser/     # listing controller, request targets, search, pager/toolbars, Trash
│   ├── fileactions/ # sheet routing, permissions, transfer panels, mutations
│   ├── transfers/   # document/Photos uploads, downloads, local-cloud transfer
│   ├── photos/      # library UI, mosaic, item actions and authorization controller
│   ├── systemfiles/ # device browsing, local actions and toolbar
│   ├── settings/    # settings UI
│   └── viewer/
│       ├── ViewerCoordinator.kt, ViewerState.kt, PreviewFiles.kt
│       ├── ui/       # image/text/media previews and expandable player
│       └── playback/ # source resolution, service, page player, cache, progress, notifications
├── shared/
│   ├── model/ # unchanged listing snapshot Model
│   ├── media/ # thumbnails, MIME detection and local media URI metadata
│   ├── ui/    # reusable file items, errors, loading, gestures, theme
│   └── i18n/  # Android string-resource access for non-UI code
└── data/      # existing provider clients, cache, encrypted stores, document/local IO
```

## Ownership and reuse

- MainActivity owns Android lifecycle/launchers, permission/OAuth bridges and coordination between features. It forwards saved-instance keys and captured operation state without changing persisted formats.
- AccountController owns saved connections, restoration and profile lookup. StorageSession captures the account and credentials at the start of an operation; file actions and transfers consume this account-owned contract.
- BrowserController owns the current listing, selected tab, independent tab snapshots, request IDs and account-scoped cache access. Authorization requests return through the Activity bridge. Model remains a shared snapshot because Photos and local browsing also consume it; moving it does not change the existing token/path fields.
- FileMutations performs permission/copy/move/rename operations. ListingMutations coordinates mutations that affect browser loading/error state; account activation/sign-out invalidate its generation.
- Transfer workflow classes receive immutable account snapshots and completion callbacks. The Activity checks the source account/location before refreshing UI; upload streams, folder traversal, notifications and cleanup keep their existing behavior.
- File actions and transfers resolve captured credentials through `StorageSessionAccess`. `data/StorageAccess` supplies Drive and S3 implementations for listing, folder creation, upload, download sources and removal. S3 keeps prefix-based keys and direct deletion; Drive keeps parent IDs and Trash. Drive-only copy and permission operations remain explicit.
- `shared/operations` supplies cancellation-aware IO results and batch processing. Batches deduplicate file IDs, continue after individual failures, and propagate cancellation immediately; callers retain their own cache invalidation, notifications and refresh rules. Transfers with cancellation notifications finalize those notifications before rethrowing cancellation.
- `shared/cache` supplies the existing SHA-256 cache-key format, staged writes with cleanup on failure, and byte/age trimming for listing, thumbnail, preview and playback caches. Callers retain directory names, entry formats, size limits and TTLs. Separate staging files prevent preview and playback writes from sharing the same temporary path.
- Cloud and local folder toolbars share `FolderSearchField`; transfer panels share their destination-folder list within the file-actions feature. Account, browser, Photos and external/local-file workflows also use the shared IO result helper.
- Shared file rows/cards serve browser and Trash; local browsing reuses browser presentation. Shared local-media resolution serves both Activities, and thumbnails serve browser, Photos and playback notifications.
- Shared packages have no imports from feature packages. Cross-feature access uses feature contracts; ListingMutations intentionally consumes the public browser-controller API. Provider/persistence helpers remain in data pending a separate ownership review.

## Large files split by responsibility

Counts compare the workspace before this reorganization with the remaining primary file; extracted responsibilities live in adjacent feature/shared files.

| Primary file | Before (lines) | After (lines) |
| --- | ---: | ---: |
| MainActivity | 1,712 | 693 |
| MainScreen / app shell | 1,324 | 575 |
| AppLanguageCatalog | 4,315 | 369 |
| FileViewerPage | 786 | 228 |
| FileActionsUi | 697 | 247 |
| FileBrowserUi | 688 | 362 |
| MediaPlaybackService | 554 | 111 |
| ViewerCoordinator | 547 | 498 |

Localized strings now live in Android resource directories; formatted translations use positional arguments. The original catalog split was an intermediate refactor. The shell retains routing and coordination; Activity retains Android lifecycle/authorization work, so neither was split by an arbitrary line limit.

## Android compatibility

MainActivity remains the launcher. The playback service now uses `feature.viewer.playback.MediaPlaybackService`, and all MediaController connections reference it. External media intent filters target `feature.viewer.ExternalMediaActivity`; the old `com.starfall.gsadrive.ExternalMediaActivity` component is retained as an exported alias for explicit callers. `ACTION_VIEW` MIME types, URI schemes and read-permission handling remain unchanged.

No SDK/library upgrades, preference/cache key changes, account-storage migration or credential encryption changes are included. See [verification](refactor-verification.md) for checks and device limitations.
