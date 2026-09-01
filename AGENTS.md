# Implementation Plan — SMS Forwarder

## Project Root: `C:\Dev\Projects\msgforwarder`
## Package: `com.personal.msgforwarder`

---

## Agent 1: Project Scaffolding

Creates the Gradle build system, manifest, and project config.

### Files:

| # | File | Purpose |
|---|---|---|
| 1 | `settings.gradle.kts` | Declares project name, plugin repos, dependency repos |
| 2 | `build.gradle.kts` (root) | Project-level Gradle — applies Android + Kotlin + Google Services plugins |
| 3 | `app/build.gradle.kts` | App-level Gradle — dependencies: Compose, Firebase (Database, Messaging), WorkManager, Navigation Compose |
| 4 | `gradle.properties` | JVM args, AndroidX opt-in, Kotlin code style |
| 5 | `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.x wrapper config |
| 6 | `app/src/main/AndroidManifest.xml` | Permissions (RECEIVE_SMS, READ_SMS, INTERNET, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE), declares BroadcastReceivers (SMS + Boot), FcmService, MainActivity |
| 7 | `app/proguard-rules.pro` | Empty proguard file (needed by Gradle) |

---

## Agent 2: Data Layer

Creates Firebase helper, local preferences, and FCM token management.

### Files:

| # | File | Path | Purpose |
|---|---|---|---|
| 1 | `PreferencesHelper.kt` | `.../data/PreferencesHelper.kt` | SharedPreferences wrapper. Stores: `pairingCode`, `role` (sender/receiver), `isActive`, `fcmToken`, `partnerFcmToken`. All reads are local = zero battery cost. |
| 2 | `FirebaseHelper.kt` | `.../data/FirebaseHelper.kt` | Singleton that handles all Firebase Realtime Database operations: `setActive(code, active)`, `pushMessage(code, sender, body, timestamp)`, `listenForMessages(code, callback)`, `writeHeartbeat(code, timestamp)`, `listenForActivation(code, callback)`. Uses path `channels/<pairingCode>/`. |
| 3 | `FcmTokenManager.kt` | `.../data/FcmTokenManager.kt` | On app start, gets FCM token via `FirebaseMessaging.getInstance().token`. Writes it to Firebase under `channels/<code>/devices/<role>` so the other phone knows where to send pushes. |
| 4 | `MessageData.kt` | `.../data/MessageData.kt` | Simple data class: `sender: String`, `body: String`, `timestamp: Long`. |

---

## Agent 3: Services Layer

Creates the SMS receiver, boot receiver, FCM service, and heartbeat worker.

### Files:

| # | File | Path | Purpose |
|---|---|---|---|
| 1 | `SmsBroadcastReceiver.kt` | `.../receiver/SmsBroadcastReceiver.kt` | Manifest-registered receiver for `SMS_RECEIVED`. On trigger: reads `isActive` from PreferencesHelper (local check). If active → extracts sender + body from SMS PDU → calls `FirebaseHelper.pushMessage()` → sends FCM notification to partner device. If inactive → returns immediately. **This is the core of the battery efficiency — no service, just event-driven.** |
| 2 | `BootReceiver.kt` | `.../receiver/BootReceiver.kt` | Manifest-registered receiver for `BOOT_COMPLETED`. On phone restart → checks PreferencesHelper for `isActive`. If active, schedules heartbeat worker. That's it — the SMS receiver is manifest-registered so it auto-survives reboots. |
| 3 | `FcmService.kt` | `.../service/FcmService.kt` | Extends `FirebaseMessagingService`. Handles two types of incoming FCM messages: (a) `type=activate` → saves `isActive=true` to PreferencesHelper, starts heartbeat worker. (b) `type=deactivate` → saves `isActive=false`, stops heartbeat. (c) `type=message` → shows a notification with sender + body (for the receiver phone). Also handles `onNewToken()` → updates token in Firebase. |
| 4 | `HeartbeatWorker.kt` | `.../worker/HeartbeatWorker.kt` | Extends `CoroutineWorker`. Scheduled via `PeriodicWorkRequest` every 6 hours. Writes `lastSeen: System.currentTimeMillis()` to Firebase under `channels/<code>/heartbeat`. Enqueued when activated, cancelled when deactivated. |
| 5 | `NotificationHelper.kt` | `.../util/NotificationHelper.kt` | Creates notification channel on Android 8+. Helper to build and show notifications for: (a) incoming forwarded SMS on receiver phone, (b) optional "forwarding active" status on sender phone. |

---

## Agent 4: UI Layer

Creates Compose screens, navigation, theme, and MainActivity.

### Files:

| # | File | Path | Purpose |
|---|---|---|---|
| 1 | `Color.kt` | `.../ui/theme/Color.kt` | Minimal color palette — primary green, background white, error red. |
| 2 | `Theme.kt` | `.../ui/theme/Theme.kt` | Material3 light theme using the colors above. No dark theme (keep it simple). |
| 3 | `AppNavigation.kt` | `.../ui/AppNavigation.kt` | NavHost with 3 routes: `pairing`, `sender`, `receiver`. On launch → check PreferencesHelper → if already paired, skip to sender/receiver based on saved role. |
| 4 | `PairingScreen.kt` | `.../ui/screens/PairingScreen.kt` | TextField for 6-digit pairing code. Two radio buttons: "Sender (Mom's phone)" / "Receiver (My phone)". Connect button → saves to PreferencesHelper + writes FCM token to Firebase → navigates to sender or receiver screen. |
| 5 | `SenderScreen.kt` | `.../ui/screens/SenderScreen.kt` | Shows: role label, active/inactive status (reads from PreferencesHelper, updates via Firebase listener), last forwarded message, heartbeat status. A "Troubleshoot" button linking to dontkillmyapp.com. No action buttons — mom doesn't need to press anything. |
| 6 | `ReceiverScreen.kt` | `.../ui/screens/ReceiverScreen.kt` | Big Activate/Deactivate toggle button. On tap → writes to Firebase + sends FCM to partner. Below: list of forwarded messages (read from Firebase `channels/<code>/messages`, ordered by timestamp desc). Each message card shows sender, body, time ago. Heartbeat indicator: "Mom's phone last seen: X ago ✅/⚠️". |
| 7 | `MainActivity.kt` | `.../MainActivity.kt` | Single Activity, `setContent { AppTheme { AppNavigation() } }`. Requests SMS + notification permissions on launch (runtime permission flow for Android 13+). |

---

## File Tree (Final)

```
msgforwarder/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        └── main/
            ├── AndroidManifest.xml
            └── java/com/personal/msgforwarder/
                ├── MainActivity.kt
                ├── data/
                │   ├── MessageData.kt
                │   ├── PreferencesHelper.kt
                │   ├── FirebaseHelper.kt
                │   └── FcmTokenManager.kt
                ├── receiver/
                │   ├── SmsBroadcastReceiver.kt
                │   └── BootReceiver.kt
                ├── service/
                │   └── FcmService.kt
                ├── worker/
                │   └── HeartbeatWorker.kt
                ├── util/
                │   └── NotificationHelper.kt
                └── ui/
                    ├── AppNavigation.kt
                    ├── theme/
                    │   ├── Color.kt
                    │   └── Theme.kt
                    └── screens/
                        ├── PairingScreen.kt
                        ├── SenderScreen.kt
                        └── ReceiverScreen.kt
```

**Total: 20 files** (7 config + 13 Kotlin)

---

## Build Order

Agents can work in parallel since there are no cross-file dependencies during creation:

```
Agent 1 (Scaffolding)  ──► 7 files  ─┐
Agent 2 (Data Layer)   ──► 4 files  ─┼──► All files created ──► Build & Test
Agent 3 (Services)     ──► 5 files  ─┤
Agent 4 (UI)           ──► 7 files  ─┘
```

> [!NOTE]
> After all files are created, you'll need to:
> 1. Place your `google-services.json` (from Firebase Console) into the `app/` folder
> 2. Open the project in Android Studio
> 3. Sync Gradle
> 4. Build APK and install on both phones
