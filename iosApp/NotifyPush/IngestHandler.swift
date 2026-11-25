import Foundation

// Decodes the generic notify-push/v1 JSON carried by the URL scheme or the App Intent.
// receivedAt accepts epoch milliseconds or an ISO-8601 string; rawText defaults to body.
struct IngestPayloadDTO: Decodable {
    var schema: String = "notify-push/v1"
    var sourceApp: String = ""
    var bundleId: String = ""
    var title: String = ""
    var subtitle: String = ""
    var body: String = ""
    var receivedAtMillis: Int64?
    var rawText: String?
    var device: String = "ios"

    enum CodingKeys: String, CodingKey {
        case schema, sourceApp, bundleId, title, subtitle, body, receivedAt, rawText, device
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schema = try container.decodeIfPresent(String.self, forKey: .schema) ?? schema
        sourceApp = try container.decodeIfPresent(String.self, forKey: .sourceApp) ?? ""
        bundleId = try container.decodeIfPresent(String.self, forKey: .bundleId) ?? ""
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
        subtitle = try container.decodeIfPresent(String.self, forKey: .subtitle) ?? ""
        body = try container.decodeIfPresent(String.self, forKey: .body) ?? ""
        rawText = try container.decodeIfPresent(String.self, forKey: .rawText)
        device = try container.decodeIfPresent(String.self, forKey: .device) ?? device
        receivedAtMillis = IngestPayloadDTO.decodeReceivedAt(container)
    }

    private static func decodeReceivedAt(_ container: KeyedDecodingContainer<CodingKeys>) -> Int64? {
        if let millis = try? container.decodeIfPresent(Int64.self, forKey: .receivedAt) {
            return millis
        }
        guard let text = try? container.decodeIfPresent(String.self, forKey: .receivedAt),
              let date = ISO8601DateFormatter().date(from: text) else {
            return nil
        }
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    func toAppPayload() -> AppPayload {
        AppPayload(
            schema: schema,
            sourceApp: sourceApp,
            bundleId: bundleId,
            title: title,
            subtitle: subtitle,
            body: body,
            receivedAt: Date(timeIntervalSince1970: TimeInterval(receivedAtMillis ?? Int64(Date().timeIntervalSince1970 * 1000)) / 1000),
            rawText: rawText ?? body,
            device: device
        )
    }
}

enum IngestHandler {
    static func payload(from url: URL) -> AppPayload? {
        guard url.scheme == "pushnotification", url.host == "ingest" else { return nil }
        guard let item = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "payload" }),
              let json = item.value else { return nil }
        return payload(fromJSON: json)
    }

    static func payload(fromJSON json: String) -> AppPayload? {
        guard let data = json.data(using: .utf8),
              let dto = try? JSONDecoder().decode(IngestPayloadDTO.self, from: data) else {
            return nil
        }
        return dto.toAppPayload()
    }
}

// Pending payloads handed off by the App Intent live in the App Group defaults
// until the app foregrounds and consumes them.
enum PendingIngestStore {
    static let suiteName = "group.com.god.pushnotification"
    static let key = "pendingIngestPayloadJson"

    static func save(_ json: String) {
        UserDefaults(suiteName: suiteName)?.set(json, forKey: key)
    }

    static func consume() -> AppPayload? {
        guard let defaults = UserDefaults(suiteName: suiteName),
              let json = defaults.string(forKey: key) else { return nil }
        defaults.removeObject(forKey: key)
        return IngestHandler.payload(fromJSON: json)
    }
}
