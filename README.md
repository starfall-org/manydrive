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

## Configure Google sign-in and Drive

1. In Firebase Console, enable **Authentication → Sign-in method → Google** and add the SHA-1 of the debug/release signing key.
2. In Google Cloud Console for the same project, enable **Google Drive API** and create or locate the OAuth 2.0 **Web application** client ID.
3. Put that client ID in [auth.xml](app/src/main/res/values/auth.xml), replacing `REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com`.

The app requests the full Google Drive scope to list both My Drive and shared files. This is a restricted Google scope; production release may require OAuth verification/security assessment.
