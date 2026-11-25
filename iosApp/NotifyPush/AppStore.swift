import Foundation

@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var destinations: [AppDestination] = []
    @Published private(set) var history: [AppHistoryRecord] = []
    @Published private(set) var testResults: [AppDispatchOutcome] = []
    @Published private(set) var pendingRetryCount = 0
    @Published var isBusy = false
    @Published var ingestNotice: String?

    private let backend: SharedBackend

    init(backend: SharedBackend = KmpSharedBackend()) {
        self.backend = backend
    }

    func startup() async {
        destinations = await backend.loadDestinations()
        history = await backend.loadHistory()
        pendingRetryCount = await backend.pendingRetryCount()
        await retryPending()
        consumePendingIntentPayload()
    }

    func consumePendingIntentPayload() {
        guard let payload = PendingIngestStore.consume() else { return }
        Task { await ingest(payload) }
    }

    func handleIncomingURL(_ url: URL) async {
        guard let payload = IngestHandler.payload(from: url) else {
            ingestNotice = "Could not read the notification payload from that link."
            return
        }
        await ingest(payload)
    }

    func ingest(_ payload: AppPayload) async {
        if backend.isOtp(payload) {
            ingestNotice = "Skipped: this looks like a one-time code."
            return
        }
        isBusy = true
        let outcomes = await backend.dispatch(payload)
        isBusy = false
        await refreshAfterDispatch()
        ingestNotice = ingestSummary(outcomes, sourceApp: payload.sourceApp)
    }

    func sendTest(title: String, body: String) async {
        let payload = backend.samplePayload(device: "ios").withTitle(title, body: body)
        isBusy = true
        testResults = await backend.dispatchTest(payload)
        isBusy = false
        await refreshAfterDispatch()
    }

    func sendTestToDestination(_ destination: AppDestination, title: String, body: String) async {
        let payload = backend.samplePayload(device: "ios").withTitle(title, body: body)
        isBusy = true
        testResults = await backend.dispatchToDestination(payload, destination: destination)
        isBusy = false
        await refreshAfterDispatch()
    }

    func saveDestination(_ destination: AppDestination) async {
        if let index = destinations.firstIndex(where: { $0.id == destination.id }) {
            destinations[index] = destination
        } else {
            destinations.append(destination)
        }
        await backend.saveDestinations(destinations)
    }

    func deleteDestination(_ destination: AppDestination) async {
        destinations.removeAll { $0.id == destination.id }
        await backend.saveDestinations(destinations)
    }

    func setEnabled(_ destination: AppDestination, enabled: Bool) async {
        var updated = destination
        updated.enabled = enabled
        await saveDestination(updated)
    }

    func retryPending() async {
        guard await backend.pendingRetryCount() > 0 else { return }
        isBusy = true
        _ = await backend.retryPending()
        isBusy = false
        await refreshAfterDispatch()
    }

    func clearHistory() async {
        await backend.clearHistory()
        history = []
    }

    private func refreshAfterDispatch() async {
        history = await backend.loadHistory()
        pendingRetryCount = await backend.pendingRetryCount()
    }

    private func ingestSummary(_ outcomes: [AppDispatchOutcome], sourceApp: String) -> String {
        if outcomes.isEmpty { return "No enabled destination matched \(sourceApp)." }
        let delivered = outcomes.filter { $0.status == .delivered }.count
        let failed = outcomes.filter { $0.status == .failed }.count
        if failed == 0 { return "Forwarded \(sourceApp) to \(delivered) destination(s)." }
        return "Forwarded \(sourceApp): \(delivered) delivered, \(failed) failed — queued for retry."
    }
}
