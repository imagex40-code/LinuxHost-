# LinuxHost - Full Project Audit

> Generated from a comprehensive review of all source files, build configs, CI, and project structure.

---

## Project Overview

| Field | Value |
|-------|-------|
| **Project** | LinuxHost - Android app for running Ubuntu via PRoot |
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **DI** | Koin |
| **Database** | Room |
| **Build** | Gradle with version catalogs |
| **CI** | GitHub Actions |
| **Source Files** | 14 Kotlin files, 39 total project files |

---

## CRITICAL: SECURITY (3)

### 1. GitHub PAT Exposed in Plain Text
- **File:** `report.txt:58`
- **Severity:** CRITICAL
- **Detail:** A GitHub Personal Access Token was committed to the repository in plain text. Anyone with access to the repo had full push access. **Token has been revoked and file deleted.**

### 2. Auth Workflow Documented in Repo
- **File:** `report.txt:54-56`
- **Severity:** CRITICAL
- **Detail:** The report described the pattern for injecting tokens into git remotes. This workflow guide exposes the auth pattern and should never be committed. **File has been deleted.**

### 3. Rootfs Download Over Plaintext HTTP
- **File:** `ProotEngine.kt:55`
- **Severity:** HIGH
- **Detail:** The 4th rootfs mirror URL uses `http://` instead of `https://`:
  ```
  http://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-$a.tar.gz
  ```
  Downloading a root filesystem over plaintext is vulnerable to MITM attacks. While `usesCleartextTraffic="true"` is set in the manifest to allow this, the real fix is to use HTTPS for all mirrors.

---

## BUGS (7)

### 4. Coroutine Scope Leak in TerminalSession
- **File:** `TerminalSession.kt:36`
- **Detail:** `startSession()` creates a new `CoroutineScope(Dispatchers.IO + SupervisorJob())` but `stopSession()` only calls `readerJob?.cancel()`. The scope and its `SupervisorJob` are never cancelled, leaking coroutines.
- **Fix:** Store the scope as a class member and cancel it in `stopSession()`.

### 5. Silent Exception Swallowing in ManagerFeature
- **File:** `ManagerFeature.kt:82`
- **Detail:** The install flow catches all exceptions with `catch (_: Exception) {}` and silently ignores them. The progress dialog closes and the user gets no feedback that installation failed.
- **Fix:** Show an error dialog or Snackbar with the exception message.

### 6. Database Never Populated After Install
- **File:** `ProotEngine.kt:128-153` (missing call)
- **Detail:** After `installRootfs()` completes successfully, the `UbuntuInstance` entity is never inserted into the Room database. `instanceDao().upsert(...)` is never called anywhere in the codebase after installation. The DB remains empty.
- **Fix:** Call `LinuxHostDatabase.get(context).instanceDao().upsert(...)` after successful rootfs extraction.

### 7. UbuntuService Is Never Started
- **File:** `UbuntuService.kt` + `AndroidManifest.xml:32-35`
- **Detail:** The `UbuntuService` foreground service is declared in the manifest but no code in the entire app ever calls `startService()` with the appropriate intent. It is dead code.
- **Fix:** Either wire it up to start when Ubuntu is launched, or remove it.

### 8. `du` Command Unavailable on Stock Android
- **File:** `ProotEngine.kt:346-362`
- **Detail:** The `getStorageBreakdown()` method relies on the system `du` command to calculate directory sizes. Stock Android does not include `du`. This will silently return 0 for all storage categories, making the StorageCard on the Dashboard always show "0 B used".
- **Fix:** Use Java/Kotlin file walking (`File.walkTopDown()`) or `StatFs` instead of shelling out to `du`.

### 9. Settings Don't Persist
- **File:** `SettingsFeature.kt` (all toggle/click components)
- **Detail:** All settings (Dark Theme, Terminal Font Size, Keep Awake, Auto-Update, Backup Schedule) use local `mutableStateOf` for state. Every setting resets to its default value when the app restarts. There is no SharedPreferences or DataStore integration.
- **Fix:** Add Jetpack DataStore or SharedPreferences and read/write settings through it.

### 10. Settings Are Purely Visual
- **File:** `SettingsFeature.kt:170-186` (ToggleSwitch) + `Theme.kt:54-61`
- **Detail:** The Dark Theme toggle doesn't change `LinuxHostTheme` (which always reads `isSystemInDarkTheme()`). The Terminal Font Size setting doesn't affect the terminal. Keep Awake doesn't acquire a `WakeLock`. Auto-Update doesn't schedule anything. All settings are non-functional.
- **Fix:** Wire each setting to its corresponding behavior. At minimum, store the preference and apply it.

---

## CODE QUALITY (9)

### 11. `formatBytes` Duplicated
- **Files:** `DashboardFeature.kt:397-402` + `InstallFeature.kt:489-494`
- **Detail:** The `formatBytes` function is defined twice with different formatting precision (`%.1f` in Dashboard vs `%.2f` in Install). This violates DRY and produces inconsistent formatting in the same app.
- **Fix:** Extract to a single shared utility function.

### 12. Architecture Hardcoded in UI
- **Files:** `DashboardFeature.kt:147` + `ManagerFeature.kt:142`
- **Detail:** The UI hardcodes `"aarch64"` as the architecture string:
  ```kotlin
  // DashboardFeature.kt:147
  "Resolute Raccoon \u00B7 aarch64"
  
  // ManagerFeature.kt:142
  InfoRow("Architecture", "aarch64")
  ```
  This ignores the actual `engine.arch` value. On an x86_64 emulator or armv7l device, it will still show "aarch64".

### 13. Ubuntu Version Hardcoded in 5+ Places
- **Files:** `Database.kt:31,34` + `DashboardFeature.kt:142` + `ManagerFeature.kt:140` + `SettingsFeature.kt:84`
- **Detail:** The string "26.04" or "Ubuntu 26.04" is hardcoded independently in multiple locations. Changing the target version requires editing 5+ files. Should be a single constant or read from the DB entity.

### 14. No Package Organization
- **All source files** in `app/src/main/java/com/linuxhost/`
- **Detail:** All 14 Kotlin files are in the root package `com.linuxhost`. There is no separation into subpackages like `data/`, `ui/`, `engine/`, `di/`. This makes the codebase harder to navigate as it grows.

### 15. ProotEngine Is a God Class
- **File:** `ProotEngine.kt` (394 lines)
- **Detail:** This single class handles:
  - PRoot binary downloading
  - Rootfs downloading (with mirror fallback)
  - Rootfs extraction
  - PRoot process launching/stopping
  - Package count querying
  - Pending updates checking
  - Storage breakdown calculation
  - Command execution
  - `du` output parsing
  
  This violates the Single Responsibility Principle. It should be split into 3-4 focused classes.

### 16. Zero Tests
- **Entire project**
- **Detail:** There are no unit tests, no instrumented tests, no UI tests. No test directories exist. No test dependencies beyond what's bundled. No test scripts or CI test steps.

### 17. No `@Preview` Annotations
- **All Composable functions**
- **Detail:** None of the 20+ Composable functions have `@Preview` annotations. This makes iterative UI development in Android Studio much slower since you can't preview changes without building and deploying.

### 18. No Release Build Type
- **File:** `app/build.gradle.kts:20-25`
- **Detail:** Only the `debug` build type is configured. There is no `release` build type with minification, obfuscation, or signing configuration. The app cannot be properly released.

### 19. No ProGuard/R8 Rules
- **Entire project**
- **Detail:** There is no `proguard-rules.pro` file. When a release build type is eventually added with R8 enabled, reflection-heavy libraries like Room, Koin, and Kotlin serialization will likely crash without proper keep rules.

---

## ARCHITECTURE (5)

### 20. Missing ViewModels for Most Screens
- **Files:** `DashboardFeature.kt`, `TerminalFeature.kt`, `ManagerFeature.kt`, `SettingsFeature.kt`
- **Detail:** Only `InstallScreen` has a dedicated `InstallViewModel`. The other four screens inject `ProotEngine` directly via `koinInject` and manage all state inside `@Composable` functions. Business logic, data fetching, and state management live in the UI layer.

### 21. Singleton TerminalSession With No Recovery
- **File:** `AppModule.kt:16` + `TerminalSession.kt`
- **Detail:** `TerminalSession` is registered as a Koin `single` (singleton). It holds a `Process` reference and terminal lines. If the Android app process is killed (by the system or user), all terminal state is lost with no recovery mechanism.

### 22. No Auto-Navigation After Install
- **File:** `Navigation.kt:52-55`
- **Detail:** `startRoute` is calculated once when `AppNavigation` is first composed based on `engine.status`. If the user completes installation, the status changes to `INSTALLED`, but the NavHost doesn't restart. The user must manually navigate to the Dashboard. The `onLaunchDashboard` callback on `InstallScreen` handles this, but only for the Install -> Dashboard transition, not for the initial routing.

### 23. SharedFlow Replay Can Miss Events
- **File:** `ManagerFeature.kt:51-55` + `ProotEngine.kt:62-63`
- **Detail:** `engine.progress` is a `SharedFlow` with `replay = 1`. In `ManagerFeature.kt`, the collector starts inside a `LaunchedEffect(showProgress)`. If progress events are emitted before the collector starts (e.g., during the brief `showProgress = true` setup), those events are missed. `StateFlow` would be more appropriate here.

### 24. Dashboard Runs 4 Sequential Network Calls on Every Composition
- **File:** `DashboardFeature.kt:56-61`
- **Detail:** Every time the Dashboard composable enters composition, it runs 4 sequential suspending calls:
  ```kotlin
  engine.checkStatus()
  breakdown = engine.getStorageBreakdown()
  pkgCount = engine.getPackageCount()
  pendingUpdates = engine.getPendingUpdates()
  ```
  These run sequentially (not in parallel), have no caching, no debounce, and no loading state indicator. Navigating away and back triggers them all again.

---

## UI/UX (4)

### 25. "Resolute Raccoon" Codename May Be Wrong
- **File:** `DashboardFeature.kt:147`
- **Detail:** The UI displays "Resolute Raccoon" as Ubuntu 26.04's codename, and the DB default says "Ubuntu 26.04". These are currently correct (26.04 = Resolute Raccoon), but they're hardcoded separately and could drift out of sync if the target version changes.

### 26. No Loading State on Dashboard
- **File:** `DashboardFeature.kt:56-61`
- **Detail:** When the Dashboard's `LaunchedEffect` is running its 4 network calls, the UI shows the last known state (or empty cards). There's no loading spinner, skeleton, or shimmer effect to indicate data is being fetched.

### 27. No Terminal Reconnection
- **File:** `TerminalFeature.kt`
- **Detail:** If the terminal session dies (process killed, error, etc.), there's no clear way for the user to reconnect. The terminal just stops receiving output. The user would need to restart the entire app.

### 28. Install Button State Gap
- **File:** `ManagerFeature.kt:189`
- **Detail:** The "Install" button is only enabled when `status == InstanceStatus.NOT_INSTALLED`. If an install fails partway through and the status ends up as `ERROR`, the Install button remains disabled. There's no "Retry Install" or "Reinstall" option. The user would need to use "Remove" first (if that even works in ERROR state).

---

## BUILD/CI (3)

### 29. Sensitive Files Committed to Repo
- **Files:** `report.txt`, `index.html`
- **Detail:** `report.txt` contains credentials and should never be in the repository. `index.html` is a UI mockup that doesn't belong in an Android project root. Neither file is in `.gitignore`.

### 30. Configuration Cache May Break Plugins
- **File:** `gradle.properties:4`
- **Detail:** `org.gradle.configuration-cache=true` is enabled, but this can break KSP (used for Room annotation processing) and some Koin plugins. If builds fail with obscure serialization errors, this is the likely culprit.

### 31. `.gitignore` Missing Entries
- **File:** `.gitignore`
- **Detail:** The `.gitignore` does not include:
  - `report.txt` (contains secrets)
  - `*.html` (mockups)
  - `local.properties` is listed twice (lines 3 and 10)
  - No entry for `AUDIT.md` or other documentation files

---

## Summary

| Category | Count | Severity Range |
|----------|-------|----------------|
| Security | 3 | CRITICAL - HIGH |
| Bugs | 7 | HIGH - MEDIUM |
| Code Quality | 9 | MEDIUM - LOW |
| Architecture | 5 | MEDIUM |
| UI/UX | 4 | LOW |
| Build/CI | 3 | MEDIUM |
| **Total** | **31** | |

---

## Top 5 Priority Fixes

| Priority | Issue | Impact |
|----------|-------|--------|
| **1** | ~~Revoke exposed GitHub token and remove `report.txt`~~ DONE | Full repo compromise |
| **2** | Fix `TerminalSession` coroutine scope leak | Memory leak, zombie coroutines |
| **3** | Populate DB after install (`instanceDao().upsert(...)`) | Dashboard/Manager show wrong state |
| **4** | Add error feedback in `ManagerFeature` (stop swallowing exceptions) | User has no idea when install fails |
| **5** | Make settings persistent with DataStore | All user preferences lost on restart |

---

*This audit covers all 14 Kotlin source files, build configuration, CI pipeline, manifest, resources, and project structure as of the review date.*
