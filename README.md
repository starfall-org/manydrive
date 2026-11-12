# ManyDrive

ManyDrive is a native Android file-manager starter built with Kotlin and Jetpack Compose.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK Platform 36

## Run

Open this repository directly in Android Studio, then run the `app` configuration; or use:

```bash
./gradlew :app:assembleDebug
```

The project has no Flutter or Dart runtime. It uses a Kotlin/Jetpack Compose Material 3 interface, Google account picker, Google Identity authorization, and the official Google Drive Java SDK.

## Development and checks

The app remains a single Gradle module. Under `app/src/main/kotlin/com/starfall/gsadrive`, only `MainActivity.kt` lives at the package root:

- `app`: Compose shell, drawer, top bars, previews and Android platform bridges.
- `feature/accounts`: saved-account/controller workflows, account UI and the in-memory `StorageSession` snapshot used by file actions and transfers.
- `feature/browser`: listing/controller, independent Files/Shared snapshots, search, browser UI and Trash UI.
- `feature/fileactions`: sheets, permissions, copy/move, folder creation and trash/delete orchestration.
- `feature/transfers`: document uploads, Photos uploads, cloud downloads, local ↔ cloud transfers and upload notifications.
- `feature/photos`, `feature/systemfiles`, `feature/settings`: feature UI and their existing controllers/actions.
- `feature/viewer`: viewer coordination/preview cache, `ui` for previews/media controls, and `playback` for sources, player, service, notifications and progress.
- `shared`: listing model, reusable file rows/cards, theme/gestures/loading, media metadata/thumbnails and Android string-resource access. Localized strings live in `app/src/main/res/values-*`.
- `data`: existing provider clients, persistence and storage helpers; their APIs and stored formats remain unchanged.

`BrowserController` owns listing state; its unchanged `Model` snapshot is shared with Photos and System Files. System Files reuses browser presentation. MainActivity retains launchers, lifecycle, permission/authorization bridges and coordination between features. See [architecture](docs/architecture.md) for boundaries and the file-size comparison.

The application ID and launcher component remain unchanged. The moved external-media Activity retains its previous explicit component name through a manifest alias; the playback service declaration and references use its new package. Preference/cache keys and credential storage are unchanged.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Use JDK 17 (`JAVA_HOME`) and point `ANDROID_HOME` or `local.properties` at Android SDK 36. The instrumentation suite needs a connected Android device or emulator; building its APK does not execute the tests. See [refactor verification](docs/refactor-verification.md) for recorded results and pending device checks. Sign-in, provider transfers, Photos and background playback also require manual checks with suitable accounts and media.

## Release builds on Codemagic

1. Open **Team settings → codemagic.yaml settings → Code signing identities → Android keystores**.
2. Upload your release keystore and enter its keystore password, key alias, and key password.
3. Set its **Reference name** to `ManyDrive`, matching `environment.android_signing` in `codemagic.yaml`. If you already uploaded it with another reference name, update the YAML to match.
4. Create a Codemagic environment variable group named `github_release`. Add `GITHUB_TOKEN` as a **Secret**: a GitHub fine-grained personal access token with access to `starfall-org/manydrive` and **Contents: Read and write** (approve it for the organization if required). The release scripts also accept the legacy `GH_TOKEN` name.
5. Run the `android-release` workflow. After the signed APKs build successfully, it publishes each APK together with its `.sha1` checksum file to GitHub Release `v<versionName>` (for example, `v3.0.202`).

Android versions are generated from Git history: `versionCode` is the result of `git rev-list --count HEAD`, and `versionName` is `3.0.<commit-count>` (for example, `3.0.202` at commit count 202). Codemagic fetches full history before building to prevent incorrect counts from shallow clones. Local builds should also use a full Git checkout.

The publishing script derives its tag from the APK output metadata. New releases target the build commit and remain drafts until APKs and SHA-1 checksum files upload successfully. On subsequent builds, it reuses an existing tag and release, replacing assets of the same name instead of moving the Git tag. If an existing tag points to a different commit, it warns and leaves the tag unchanged. If building from a tag, it must match `v<versionName>`. Debug builds do not publish releases or require a GitHub release token.

See the [GitHub CLI release documentation](https://cli.github.com/manual/gh_release_create) and [Codemagic environment variables](https://docs.codemagic.io/yaml-basic-configuration/configuring-environment-variables/).

Codemagic supplies `CM_KEYSTORE_PATH`, `CM_KEYSTORE_PASSWORD`, `CM_KEY_ALIAS`, and `CM_KEY_PASSWORD` to Gradle. Release signing requires these variables; it no longer uses the debug keystore. For local release builds, provide the same environment variables. Debug builds do not require them.

See [Codemagic's Android signing guide](https://docs.codemagic.io/yaml-code-signing/signing-android/).

## Release builds on CircleCI

The `.circleci/config.yml` workflow runs for tags matching `vX.Y.Z` (including prerelease suffixes), builds a signed release APK, and publishes it to the GitHub Release with the existing `scripts/publish-github-release.sh` script. Create a CircleCI context named `github-release` and add these secret environment variables:

- `GH_TOKEN`: GitHub token with **Contents: Read and write** for `starfall-org/manydrive`.
- `CM_KEYSTORE_BASE64`: base64-encoded release keystore (`base64 -w 0 release.jks`).
- `CM_KEYSTORE_PASSWORD`, `CM_KEY_ALIAS`, and `CM_KEY_PASSWORD`: release signing credentials.

Push a tag that matches the version generated from the full Git history, for example `v3.0.202`. CircleCI fetches full history before building and rejects a tag that does not match the APK `versionName`.

## Configure Google sign-in and Drive

1. In Google Cloud Console, enable **Google Drive API** and **Photos Library API** for the project.
2. Configure the OAuth consent screen and add test users if the app is in testing.
3. Create an OAuth 2.0 **Android** client for package `com.starfall.gsadrive` and the SHA-1 of the signing certificate. Configure each debug/release certificate used to install the app (including the Play app-signing certificate when applicable).

The app opens Google's account picker to select an account already on the device, then requests access directly with `AuthorizationClient`, bound to that account. It does not require a Web OAuth client ID or Firebase Authentication. Only the selected account email is persisted; access tokens stay in memory. Signing out clears the local session without removing the Google account from the device or revoking consent.

The app requests the full Google Drive scope to list both My Drive and shared files. This is a restricted Google scope; production release may require OAuth verification/security assessment.

## Accounts and storage providers

The profile button opens one account list for **Google**, **S3**, and **Service Account**. **Thêm tài khoản** offers these three connection types. Selecting any account replaces the active storage provider on the Files screen; there is no separate S3 tab. The selected account is restored on restart. Files and Shared remain visible. Google enables both; S3 enables only Files; Service Account enables Files and Shared. Unsupported tabs are disabled. For Google and Service Account, Files starts at the account's own Drive root and only lists items owned by that account there; incoming shared folders and files appear in Shared. A service account with only incoming shares therefore has an empty Files tab. Opening a folder in Shared lists its children by parent ID, including children accessible through inherited permissions.

- **Google:** choose a Google account on the device and grant Drive access. Photos upload permission is requested from the “Tải lên Google Photos” file menu.
- **S3:** enter a name, HTTPS endpoint, bucket, region and access keys in a dialog. The app verifies bucket access before saving. Multiple connections are supported.
- **Service Account:** choose a Google service-account JSON key with Android's file picker. The app validates its type, email and RSA private key, obtains an OAuth token, and verifies Drive access before saving. Reimporting the same service-account email replaces its saved key. Enable Drive API in that account's project and share files with its `client_email`, or grant it access to a shared drive. Tokens refresh when needed during browsing. Domain-wide impersonation is not configured.

S3 supports file listing, upload, download and direct deletion; it does not provide Shared, Trash, Drive-wide search or internal S3 copy. Service Account uses its own Drive credentials and is subject to the permissions granted to it. Service accounts have no personal Drive storage quota, so the app does not offer uploads into their root. See [Google's service account OAuth guide](https://developers.google.com/identity/protocols/oauth2/service-account) and [shared drive guidance](https://developers.google.com/workspace/drive/api/guides/about-shareddrives).

Access keys and imported private keys are encrypted with AES-GCM using device-bound Android Keystore keys, in the app's no-backup directory. The JSON source is read once; its path is not retained. Existing S3 connections remain compatible with the encrypted storage format. Removing an account deletes its saved connection without deleting remote files. Signing out clears the active session while keeping saved accounts available in the account list. Reinstalling or moving to another device requires importing credentials again.

## API clients

Drive file operations use `google-api-services-drive`, with Google Auth Library for service-account JWT signing and token exchange. S3 uses the official AWS SDK for Kotlin. Access tokens are supplied separately for each Google client; upload/download content is streamed by the SDK.

Google Photos uses the [Library REST API](https://developers.google.com/photos/library/guides/upload-media) through the official Google HTTP Client with `photoslibrary.appendonly`. The Google Photos sidebar browser requests the separate `photoslibrary.readonly.appcreateddata` scope and lists all pages of media created by this OAuth app. Enable the Library API and include that scope in the OAuth consent configuration. Media from other apps is not accessible. Photos open in the app viewer; videos use the existing playback queue, which skips images during automatic advance. Video URLs and authorization are resolved again when playback opens or seeks, since Photos base URLs expire. No Picker is used. The “Tải lên Google Photos” menu is available for images, videos, and folders from Google Drive, S3, and Service Accounts. Google Drive uploads to the active Google account; other sources prompt for a connected Google destination. A folder creates an album with the same name and uploads images/videos recursively, skipping other files. Individual media files upload directly to the library. Transfers use temporary files, remove them after each upload, and report success/failure counts.

## License

[![License: Starfall Contributor Commercial Copyleft](https://img.shields.io/badge/license-Starfall%20Contributor%20Commercial%20Copyleft-blue.svg)](LICENSE)

This project is **source-available, not OSI open source**. Non-commercial use, modification, and redistribution are allowed under the copyleft terms in [LICENSE](LICENSE). Commercial use is allowed only for a person or organization that has made a qualifying code contribution accepted and merged into this repository, or that has separate written permission from the relevant copyright holders. Distribution of modified or binary versions must keep the same license and make the corresponding source available.

### Opening media from Android

ManyDrive accepts `ACTION_VIEW` for `image/*`, `video/*`, and `audio/*` over `content://` and `file://`. File managers can offer it in **Open with**. Content URIs use the caller's granted read permission; opening a file does not require a cloud account or broad storage access. Audio has a dedicated title/status display, persistent playback controls, seeking, and previous/next track controls.

Validation: JVM tests cover Photos pagination, URL parameters, media metadata and processing status. Android instrumentation tests cover content URI seek resolution; end-to-end Photos authorization/playback requires a device with Google Play services and app-created media.
