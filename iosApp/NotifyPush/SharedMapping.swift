import Foundation
import NotifyPushShared

// Mapping between Swift-side value types and the shared KMP types.
// receivedAt crosses the boundary as an ISO-8601 string (kotlinx.datetime
// Instant format, e.g. 2026-11-25T10:00:00.123Z).

private let isoFormatter: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter
}()

func dateFromIso(_ text: String) -> Date {
    isoFormatter.date(from: text) ?? Date()
}

func isoString(from date: Date) -> String {
    isoFormatter.string(from: date)
}

extension AppPayload {
    init(from shared: NotificationPayload) {
        self.init(
            schema: shared.schema,
            sourceApp: shared.sourceApp,
            bundleId: shared.bundleId,
            title: shared.title,
            subtitle: shared.subtitle,
            body: shared.body,
            receivedAt: dateFromIso(shared.receivedAt),
            rawText: shared.rawText,
            device: shared.device
        )
    }

    func toShared() -> NotificationPayload {
        NotificationPayload(
            schema: schema,
            sourceApp: sourceApp,
            bundleId: bundleId,
            title: title,
            subtitle: subtitle,
            body: body,
            receivedAt: isoString(from: receivedAt),
            rawText: rawText,
            device: device
        )
    }
}

extension AppDestination {
    init(from shared: Destination) {
        self.init(
            id: shared.id,
            name: shared.name,
            url: shared.url,
            headerName: shared.headerName,
            headerValue: shared.headerValue,
            enabled: shared.enabled,
            appsFilter: shared.appsFilter
        )
    }

    func toShared() -> Destination {
        Destination(
            id: id,
            name: name,
            url: url,
            headerName: headerName,
            headerValue: headerValue,
            enabled: enabled,
            appsFilter: appsFilter
        )
    }
}

extension AppDispatchOutcome {
    init(from shared: DispatchResult) {
        self.init(
            destinationName: shared.destinationName,
            status: AppDispatchStatus(from: shared.status),
            detail: shared.error ?? shared.httpCode.map { "HTTP \($0)" } ?? ""
        )
    }
}

extension AppDispatchStatus {
    init(from shared: DispatchStatus) {
        switch shared {
        case .delivered: self = .delivered
        case .rejected: self = .rejected
        case .failed: self = .failed
        default: self = .failed
        }
    }
}

extension AppHistoryRecord {
    init(from shared: HistoryEntry) {
        self.init(
            id: shared.id,
            payload: AppPayload(from: shared.payload),
            outcomes: shared.results.map { AppDispatchOutcome(from: $0) },
            recordedAt: Date(timeIntervalSince1970: TimeInterval(shared.recordedAtEpochMs) / 1000)
        )
    }
}
