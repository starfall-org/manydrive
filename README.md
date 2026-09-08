# ManyDrive

ManyDrive is a native Android file-manager starter built with Kotlin and Jetpack Compose.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK Platform 35

## Run

Open this repository directly in Android Studio, then run the `app` configuration; or use:

```bash
./gradlew :app:assembleDebug
```

The project has no Flutter or Dart runtime. It uses a Kotlin/Jetpack Compose Material 3 interface, Firebase Authentication, Credential Manager, Google Identity authorization, and the Google Drive REST API.

## Release builds on Codemagic

1. Open **Team settings → codemagic.yaml settings → Code signing identities → Android keystores**.
2. Upload your release keystore and enter its keystore password, key alias, and key password.
3. Set its **Reference name** to `manydrive_release`, matching `environment.android_signing` in `codemagic.yaml`. If you already uploaded it with another reference name, update the YAML to match.
4. Run the `android-release` workflow to produce signed APK and AAB artifacts.

Codemagic supplies `CM_KEYSTORE_PATH`, `CM_KEYSTORE_PASSWORD`, `CM_KEY_ALIAS`, and `CM_KEY_PASSWORD` to Gradle. Release signing requires these variables; it no longer uses the debug keystore. For local release builds, provide the same environment variables. Debug builds do not require them.

See [Codemagic's Android signing guide](https://docs.codemagic.io/yaml-code-signing/signing-android/).

## Configure Google sign-in and Drive

1. In Firebase Console, enable **Authentication → Sign-in method → Google** and add the SHA-1 of the debug/release signing key.
2. In Google Cloud Console for the same project, enable **Google Drive API** and create or locate the OAuth 2.0 **Web application** client ID.
3. Put that client ID in [auth.xml](app/src/main/res/values/auth.xml), replacing `REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com`.

The app requests the full Google Drive scope to list both My Drive and shared files. This is a restricted Google scope; production release may require OAuth verification/security assessment.
