# Frontend connection guide

The exact integration depends on the frontend code in the video. A recording cannot show all function signatures, package names, view models, and navigation code, so this guide shows the service calls to wire into your existing screens.

## Suggested screen-to-service mapping
- Register screen → `AuthService.register(name, email, password)`
- Login screen → `AuthService.login(email, password)`
- Logout button → `AuthService.logout()`
- Profile screen → `UserProfileService.getProfile(uid)` / `updateName(uid, name)`
- New incident screen → `ReportService.submitReport(...)`
- My Reports screen → `ReportService.getMyReports(uid)`
- Report detail screen → `ReportService.getReport(reportId)`
- Add evidence photo → `PhotoUploadService.uploadEvidence(reportId, imageUri)`
- Admin dashboard/list → first call `AdminService.isCurrentUserAdmin()`, then `getAllReports()`
- Admin status action → `AdminService.updateReportStatus(reportId, status, adminNote)`

## Kotlin usage example (inside a coroutine / lifecycleScope)

```kotlin
val authService = AuthService()

lifecycleScope.launch {
    when (val result = authService.login(email, password)) {
        is FirebaseResult.Success -> {
            val profile = result.data
            // Navigate according to profile.role: "admin" or "user"
        }
        is FirebaseResult.Failure -> {
            // Show result.message to the user
        }
    }
}
```

Add the relevant imports from `com.incidex.backend.*`. Keep network calls in a coroutine; these methods are suspend functions. Do not call them directly on the main thread.

## Create an admin account safely
1. Register an account normally through the app (this creates `role = user`).
2. In Firebase Console → Firestore → `users` → document whose ID equals that account's UID, change `role` to `admin`.
3. Never add an “admin” choice to public registration. For a production system, prefer a trusted server or Firebase custom claims for role assignment.

## Important photo upload sequence
1. Submit a report first to obtain its `reportId`.
2. Upload the selected image using that report ID.
3. The service appends the download URL to that report's `photoUrls` list.

## Security note
These starter rules are examples, not a complete security audit. In particular, Storage reads are limited to signed-in users in this template; adjust access to your actual privacy requirements. Test all rules before using real reports.
