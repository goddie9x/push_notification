import Foundation
import NotifyPushShared

// Single seam between the SwiftUI app and the shared KMP module.
// Every KMP call lives here; see KMP-INTEGRATION.md for the verified API
// and the deviations from the original brief.
protocol SharedBackend {
    func loadDestinations() async -> [AppDestination]
    func saveDestinations(_ destinations: [AppDestination]) async
    func loadHistory() async -> [AppHistoryRecord]
    func clearHistory() async
    func isOtp(_ payload: AppPayload) -> Bool
    func samplePayload(device: String) -> AppPayload
    func dispatch(_ payload: AppPayload) async -> [AppDispatchOutcome]
    func dispatchTest(_ payload: AppPayload) async -> [AppDispatchOutcome]
    func dispatchToDestination(_ payload: AppPayload, destination: AppDestination) async -> [AppDispatchOutcome]
    func pendingRetryCount() async -> Int
    func retryPending() async -> [AppDispatchOutcome]
}

final class KmpSharedBackend: SharedBackend {
    private let httpClient: HttpClient
    private let destinationStore: DestinationStore
    private let historyStore: HistoryStore
    private let retryQueue: RetryQueue
    private let dispatcher: Dispatcher

    init() {
        let directory = Self.storeDirectory()
        self.httpClient = HttpClientFactoryKt.httpClientFactory()
        self.destinationStore = DestinationStoreKt.createDestinationStore(directoryPath: directory)
        self.historyStore = HistoryStoreKt.createHistoryStore(directoryPath: directory)
        self.retryQueue = RetryQueueKt.createRetryQueue(directoryPath: directory)
        self.dispatcher = Dispatcher(httpClient: httpClient, store: destinationStore)
    }

    static func storeDirectory() -> String {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
        return documents?.appendingPathComponent("NotifyPush").path ?? ""
    }

    func loadDestinations() async -> [AppDestination] {
        let loaded: [Destination]? = await awaitValue { destinationStore.load(completionHandler: $0) }
        return (loaded ?? []).map { AppDestination(from: $0) }
    }

    func saveDestinations(_ destinations: [AppDestination]) async {
        let shared = destinations.map { $0.toShared() }
        await awaitVoid { destinationStore.save(destinations: shared, completionHandler: $0) }
    }

    func loadHistory() async -> [AppHistoryRecord] {
        let entries: [HistoryEntry]? = await awaitValue { historyStore.recent(limit: 100, completionHandler: $0) }
        return (entries ?? [])
            .map { AppHistoryRecord(from: $0) }
            .sorted { $0.recordedAt > $1.recordedAt }
    }

    func clearHistory() async {
        await awaitVoid { historyStore.clear(completionHandler: $0) }
    }

    func isOtp(_ payload: AppPayload) -> Bool {
        OtpSkipFilter.shared.shouldSkip(payload: payload.toShared())
    }

    func samplePayload(device: String) -> AppPayload {
        AppPayload(from: TestPayloadBuilder.shared.sample(device: device))
    }

    func dispatch(_ payload: AppPayload) async -> [AppDispatchOutcome] {
        let shared = payload.toShared()
        let resolved = await runSharedDispatch(shared)
        let outcomes = resolved.map { AppDispatchOutcome(from: $0) }
        await recordHistory(payload: shared, results: resolved)
        for result in resolved where result.status == .failed {
            await awaitVoid { retryQueue.enqueue(destinationId: result.destinationId, payload: shared, completionHandler: $0) }
        }
        return outcomes
    }

    func dispatchTest(_ payload: AppPayload) async -> [AppDispatchOutcome] {
        await runSharedDispatch(payload.toShared()).map { AppDispatchOutcome(from: $0) }
    }

    func dispatchToDestination(_ payload: AppPayload, destination: AppDestination) async -> [AppDispatchOutcome] {
        // No dispatch(payload, destination) overload exists in shared, and
        // Swift cannot implement the Kotlin DestinationStore interface the
        // way Android's TestSender does. A throwaway file-backed store holding
        // only this destination gives the same single-destination dispatch.
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
        let singleStore = DestinationStoreKt.createDestinationStore(directoryPath: directory)
        var single = destination
        single.enabled = true
        await awaitVoid { singleStore.save(destinations: [single.toShared()], completionHandler: $0) }
        let singleDispatcher = Dispatcher(httpClient: httpClient, store: singleStore)
        let results: [DispatchResult]? = await awaitValue {
            singleDispatcher.dispatch(payload: payload.toShared(), completionHandler: $0)
        }
        try? FileManager.default.removeItem(atPath: directory)
        guard let results else {
            return [AppDispatchOutcome(destinationName: destination.name, status: .failed, detail: "Dispatcher failed")]
        }
        if results.isEmpty {
            return [AppDispatchOutcome(destinationName: destination.name, status: .failed, detail: "Destination filter excludes the test payload")]
        }
        return results.map { AppDispatchOutcome(from: $0) }
    }

    func pendingRetryCount() async -> Int {
        await pendingItems().count
    }

    func retryPending() async -> [AppDispatchOutcome] {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var all: [AppDispatchOutcome] = []
        for item in await pendingItems() where item.nextRetryAtEpochMs <= now {
            let resolved = await runSharedDispatch(item.payload)
            all.append(contentsOf: resolved.map { AppDispatchOutcome(from: $0) })
            if let result = resolved.first(where: { $0.destinationId == item.destinationId }),
               result.status != .failed {
                await awaitVoid { retryQueue.remove(id: item.id, completionHandler: $0) }
            } else {
                let next = now + RetryQueueKt.nextBackoffMs(attempts: item.attempts)
                await awaitVoid { retryQueue.markAttempt(id: item.id, nextRetryAtEpochMs: next, completionHandler: $0) }
            }
        }
        return all
    }

    private func runSharedDispatch(_ payload: NotificationPayload) async -> [DispatchResult] {
        let results: [DispatchResult]? = await awaitValue { dispatcher.dispatch(payload: payload, completionHandler: $0) }
        return results ?? []
    }

    private func pendingItems() async -> [QueuedDispatch] {
        let items: [QueuedDispatch]? = await awaitValue { retryQueue.pending(completionHandler: $0) }
        return items ?? []
    }

    private func recordHistory(payload: NotificationPayload, results: [DispatchResult]) async {
        let entry = HistoryEntry(
            id: UUID().uuidString,
            payload: payload,
            results: results,
            recordedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        await awaitVoid { historyStore.record(entry: entry, completionHandler: $0) }
    }
}
