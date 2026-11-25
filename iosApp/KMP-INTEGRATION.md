# KMP integration — iOS app ↔ shared module

Status 2026-11-25: `shared/` exists and was read in full (Kotlin 2.0.21,
package `com.god.notifypush`). The Swift code is written against the API as
actually implemented there. All KMP calls live in one seam,
`NotifyPush/SharedBackend.swift` (+ `SharedMapping.swift`,
`SuspendHelpers.swift`), so a later shared change means editing those files
only.

## 0. BLOCKER — no Apple framework is produced yet

`shared/build.gradle.kts` declares `iosArm64()` and `iosSimulatorArm64()`
targets but has **no `binaries { framework { ... } }` block**. That means no
XCFramework is produced and the `./gradlew
:shared:embedAndSignAppleFrameworkForXcode` task does not exist. Before this
app can link on a Mac, the shared sibling must add, e.g.:

```kotlin
iosArm64()
iosSimulatorArm64()

kotlin {
    // ...
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            baseName = "NotifyPushShared"
        }
    }
}
```

(or an equivalent `binaries.framework` declaration). The assumed framework
name throughout this app is `NotifyPushShared`; if the sibling picks a
different `baseName`, update the `import NotifyPushShared` line in
`SharedBackend.swift` / `SharedMapping.swift` and the Xcode link in §1.

## 1. Build the shared framework and link it (on a Mac)

```sh
cd <repo root>
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

Then, in Xcode:

1. Open `iosApp/NotifyPush.xcodeproj`.
2. Target NotifyPush → General → Frameworks, Libraries, and Embedded Content →
   add `NotifyPushShared.xcframework`, Embed & Sign.
3. Add the App Groups capability (`group.com.god.pushnotification`) — required
   only for the App Intent handoff path (`NotifyPushIntent` writes the payload
   into the group defaults; without the capability the intent path silently
   does nothing and the URL-scheme path still works).
4. Build & run on a device or simulator (iOS 17+).

## 2. Verified shared API (as implemented in shared/)

| Type | Members used by iOS |
|---|---|
| `NotificationPayload` | data class, 9 fields; `receivedAt` is an ISO-8601 **String** (kotlinx.datetime `Instant.toString()`), not epoch ms; companion `fromNotification(sourceApp:bundleId:title:subtitle:body:device:)` |
| `Destination` | data class, 7 fields; `matches(bundleId:appName:)` is used internally by the Dispatcher |
| `Dispatcher(httpClient, store)` | `store` is a `DestinationStore`; `suspend fun dispatch(payload): List<DispatchResult>`; 2xx or 422 = delivered, other HTTP = rejected, exception = failed |
| `DispatchResult` | `destinationId`, `destinationName`, `status`, `httpCode: Int?`, `error: String?` |
| `DispatchStatus` | enum `DELIVERED` / `REJECTED` / `FAILED` |
| `OtpSkipFilter` | Kotlin `object`; `shouldSkip(payload): Boolean` (checks title + body) |
| `TestPayloadBuilder` | Kotlin `object`; `sample(device): NotificationPayload` |
| `HistoryEntry` | `id`, `payload`, `results`, `recordedAtEpochMs: Long` |
| `HistoryStore` | interface, all suspend: `record(entry)`, `recent(limit = 100)`, `clear()` |
| `DestinationStore` | interface, all suspend: `load()`, `save(destinations)` |
| `RetryQueue` | interface, all suspend: `enqueue(destinationId, payload)`, `pending(): List<QueuedDispatch>`, `remove(id)`, `markAttempt(id, nextRetryAtEpochMs)` |
| `QueuedDispatch` | `id`, `destinationId`, `payload`, `attempts`, `nextRetryAtEpochMs` |
| Top-level factories | `createDestinationStore(directoryPath)`, `createHistoryStore(directoryPath)`, `createRetryQueue(directoryPath)`, `httpClientFactory()` (Darwin engine + JSON), `nextBackoffMs(attempts): Long` (30s × 2^attempts, capped at 1h) |

Swift interop names assumed (Kotlin/Native defaults, Kotlin 2.0.21 — confirm
against the generated header on first build): top-level functions surface as
`DestinationStoreKt` / `HistoryStoreKt` / `RetryQueueKt` /
`HttpClientFactoryKt`; Kotlin objects as `.shared` (`OtpSkipFilter.shared`,
`TestPayloadBuilder.shared`); the companion as `.companion`
(`NotificationPayload.companion.fromNotification`); suspend functions as
`completionHandler:` variants; Kotlin `Int` → `Int32`, `Long` → `Int64`,
`Int?` → `NSNumber?`.

Stores live in `Documents/NotifyPush/` (`destinations.json`,
`history.json`, `retry-queue.json`), mirroring Android's `filesDir/notifypush/`
layout. An empty directory path would fall back to the Documents root on iOS;
the app passes an explicit subfolder instead.

## 3. Deviations from the original brief (all verified against shared/)

- **D1 — no framework yet.** See §0. The task's integration command
  (`./gradlew :shared:embedAndSignAppleFrameworkForXcode`) cannot run until
  the framework block lands.
- **D2 — `receivedAt` is a String, not a Long.** The brief said epoch ms.
  The app parses/formats ISO-8601 across the boundary (`SharedMapping.swift`).
  The Shortcut still sends ISO-8601, and the URL-scheme decoder also accepts
  epoch milliseconds.
- **D3 — the Dispatcher's `store` is the DestinationStore, not the
  HistoryStore.** The Dispatcher does not record history. The app records a
  `HistoryEntry` itself after each ingested dispatch — exactly mirroring
  Android's `NotificationForwarder` (UUID id, `recordedAtEpochMs` = now).
- **D4 — no `dispatch(payload, destination)` overload.** The brief's
  per-service "Send test" has no shared call to use, and Swift cannot
  implement the Kotlin `DestinationStore` interface the way Android's
  `TestSender` does (`SingleDestinationStore`). The iOS app instead builds a
  throwaway file-backed store in a temp dir holding only that destination
  (enabled) and dispatches through a second `Dispatcher` over it, then
  deletes the temp dir. Same observable behaviour as Android. If the shared
  sibling adds a `dispatch(payload, destination)` overload or a
  single-destination dispatcher factory, delete the temp-dir dance in
  `dispatchToDestination`.
- **D5 — stores are suspend interfaces behind `create*` factories, not
  `.shared` singletons.** All store calls are async (bridged in
  `SuspendHelpers.swift`).
- **D6 — the retry queue is per-destination with attempts and a due time.**
  Enqueue is `enqueue(destinationId, payload)`; retry only processes items
  whose `nextRetryAtEpochMs` has passed, removes the item when its
  destination stops failing, and otherwise calls
  `markAttempt(id, now + nextBackoffMs(attempts))` — all mirroring Android's
  `RetryProcessor`. `nextBackoffMs` is a top-level function, not a member.
- **D7 — the HTTP client comes from `httpClientFactory()`** (Darwin engine,
  JSON content negotiation installed), not a bare `HttpClient()`.
- **D8 — test dispatches are NOT recorded in history.** Mirrors Android:
  only forwarded (ingested) payloads land in `HistoryStore`; test results
  show on the Test screen only. Retries also add no new history entries (the
  original entry is already there).

## 4. iOS background retry — hard platform limit

iOS has no WorkManager equivalent. A suspended iOS app cannot run a retry
timer in the background. The app therefore retries in exactly two places:

- **On launch** (`AppStore.startup()` → `retryPending()`): every due queued
  item is dispatched once. Delivered/rejected items leave the queue;
  still-failing ones stay with an increased backoff (D6).
- **Manual "Retry now"** button in History (shown with the pending count).

`nextBackoffMs` is consumed by the retry path; a future `BGAppRefreshTask`
could drive it in the background, but it would still only fire at the
system's discretion.

## 5. URL scheme and App Intent

- `pushnotification://ingest?payload=<URL-encoded JSON>` — decoded by
  `IngestHandler`, OTP-filtered, dispatched, recorded in history, and confirmed
  with an in-app banner. `receivedAt` accepts epoch milliseconds or ISO-8601.
- `NotifyPushIntent` (App Intent, "Ingest Notification") — the Shortcut can
  pass the payload JSON to this intent instead of the URL scheme. It writes the
  JSON to the App Group defaults; the app consumes it when it foregrounds.
  Requires the App Groups capability (step 1.3).

## 6. Info.plist notes

- `CFBundleURLSchemes`: `pushnotification`.
- `ITSAppUsesNonExemptEncryption`: `false` (no custom encryption; only HTTPS
  via the shared HTTP client).
- `NSAppTransportSecurity` / `NSAllowsArbitraryLoads`: `true` — the app is a
  tester for local webhooks, which are commonly plain HTTP (`http://localhost`,
  LAN IPs). Narrow this with `NSExceptionDomains` before any public release.

## 7. What is NOT verified

- The Xcode project has never been compiled: there is no macOS/Xcode on this
  Linux VM and no Swift toolchain (`which swift` → not found). First build
  must happen on a Mac after the framework block (§0) lands.
- The Swift interop names in §2 (`.shared` / `.companion` / `*Kt` classes /
  completionHandler shapes) are Kotlin/Native defaults for 2.0.21, not yet
  confirmed against the generated `NotifyPushShared` header.
- The temp-dir single-destination test path (D4) has never run.
- The App Intent has never run; App Groups behaviour on a real device is
  unverified.
- The generated `.shortcut` file has never been imported on a real iPhone.
