import AppIntents
import Foundation

// Lets the NotifyPush Shortcut hand a payload to the app without the URL scheme:
// the Shortcut adds this intent action and passes the payload JSON.
// The app consumes the pending payload when it foregrounds.
struct NotifyPushIntent: AppIntent {
    static var title: LocalizedStringResource = "Ingest Notification"
    static var description = IntentDescription("Hand a notification payload to NotifyPush for dispatch.")
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Payload JSON", description: "A notify-push/v1 JSON object.")
    var payloadJson: String

    func perform() async throws -> some IntentResult {
        PendingIngestStore.save(payloadJson)
        return .result(dialog: "Saved — open NotifyPush to dispatch it.")
    }
}
