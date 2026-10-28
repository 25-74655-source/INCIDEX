# Incidex Firebase-Ready Backend (Android/Kotlin)

This package is a Firebase-backed backend/service layer intended to be added to an existing Android Kotlin app. It is **not a standalone APK or a complete Android Studio project**. Firebase credentials and the frontend source are not included.

## Included
- Email/password registration and login using Firebase Authentication
- User profile documents in Cloud Firestore (`users`)
- Incident report create/list/detail/status update in Cloud Firestore (`reports`)
- Evidence image upload to Firebase Storage, with URL metadata saved to the report
- Admin checks and admin report management
- Starter Firestore/Storage security rules and setup checklist

## Important limitations
- The video is a screen recording, not the frontend source code. Therefore these service method names cannot be guaranteed to match the frontend's existing functions/screens until the actual frontend project is provided.
- This code has not been built or run inside your Android app because the Android project, package name, Gradle files, and Firebase project configuration were not supplied.
- Admin privileges must be granted securely (for example, by editing a trusted admin user document in Firebase Console or using a trusted server/Cloud Function). The app's public registration method always creates a regular user.
- Do not use the sample rules unchanged without reviewing them for your app. Test rules with Firebase Emulator/Rules Playground before production.

## Add to an existing Android app
1. Copy `src/main/kotlin/com/incidex/backend/` into your app's Kotlin source tree. If your app uses a different package, update the package declarations and imports.
2. In Firebase Console, create a project and register the Android app using the exact application ID from the app module's Gradle file.
3. Download `google-services.json` and place it in the Android app module folder (usually `app/google-services.json`).
4. Enable **Authentication → Email/Password** and create a **Cloud Firestore** database and **Cloud Storage** bucket.
5. Apply the Firebase Android BoM and dependencies listed in `firebase/gradle-dependencies.txt`. Also configure the Google Services Gradle plugin using Firebase's current Android setup instructions.
6. Review and deploy the example rules in `firebase/firestore.rules` and `firebase/storage.rules`.
7. Connect each frontend screen/view model to the relevant service method. See `docs/FRONTEND_INTEGRATION.md`.
8. Build and test on a device/emulator. Firebase features will not work until the app is configured with your Firebase project.

## Firestore schema
- `users/{uid}`: `uid`, `name`, `email`, `role`, `createdAt`
- `reports/{reportId}`: report details, reporter UID, status, photo URLs, coordinates, timestamps

Passwords are handled by Firebase Authentication and must **never** be stored in Firestore.
