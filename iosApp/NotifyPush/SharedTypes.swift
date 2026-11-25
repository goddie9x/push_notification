import Foundation

enum AppDispatchStatus: String {
    case delivered
    case rejected
    case failed

    var label: String {
        switch self {
        case .delivered: return "Delivered"
        case .rejected: return "Rejected"
        case .failed: return "Failed"
        }
    }
}

struct AppPayload: Equatable {
    var schema: String = "notify-push/v1"
    var sourceApp: String
    var bundleId: String
    var title: String
    var subtitle: String
    var body: String
    var receivedAt: Date
    var rawText: String
    var device: String

    func withTitle(_ title: String, body: String) -> AppPayload {
        var copy = self
        copy.title = title
        copy.body = body
        copy.rawText = [title, body].filter { !$0.isEmpty }.joined(separator: "\n")
        return copy
    }
}

struct AppDestination: Identifiable, Equatable {
    var id: String
    var name: String
    var url: String
    var headerName: String
    var headerValue: String
    var enabled: Bool
    var appsFilter: [String]

    var appsFilterText: String {
        appsFilter.joined(separator: ", ")
    }

    static func appsFilter(from text: String) -> [String] {
        text.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    static func blank() -> AppDestination {
        AppDestination(
            id: UUID().uuidString,
            name: "",
            url: "",
            headerName: "X-Api-Key",
            headerValue: "",
            enabled: true,
            appsFilter: []
        )
    }
}

struct AppDispatchOutcome: Identifiable, Equatable {
    var id: String { destinationName }
    var destinationName: String
    var status: AppDispatchStatus
    var detail: String
}

struct AppHistoryRecord: Identifiable, Equatable {
    var id: String
    var payload: AppPayload
    var outcomes: [AppDispatchOutcome]
    var recordedAt: Date

    var headline: String {
        payload.title.isEmpty ? payload.sourceApp : payload.title
    }

    var summary: String {
        let delivered = outcomes.filter { $0.status == .delivered }.count
        let rejected = outcomes.filter { $0.status == .rejected }.count
        let failed = outcomes.filter { $0.status == .failed }.count
        return "\(delivered) delivered · \(rejected) rejected · \(failed) failed"
    }
}
