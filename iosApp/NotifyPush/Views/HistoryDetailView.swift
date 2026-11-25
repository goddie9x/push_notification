import SwiftUI

struct HistoryDetailView: View {
    let record: AppHistoryRecord

    var body: some View {
        List {
            Section("Notification") {
                detailRow("App", record.payload.sourceApp)
                detailRow("Bundle ID", record.payload.bundleId)
                detailRow("Title", record.payload.title)
                detailRow("Subtitle", record.payload.subtitle)
                detailRow("Body", record.payload.body)
                detailRow("Received", record.payload.receivedAt.formatted(date: .abbreviated, time: .standard))
                detailRow("Device", record.payload.device)
            }
            Section("Raw text") {
                Text(record.payload.rawText)
                    .font(.system(.body, design: .monospaced))
                    .textSelection(.enabled)
            }
            Section("Results") {
                if record.outcomes.isEmpty {
                    Text("No destination matched this notification.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(record.outcomes) { outcome in
                        OutcomeRow(outcome: outcome)
                    }
                }
            }
        }
        .navigationTitle(record.headline)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .foregroundStyle(.secondary)
                .frame(width: 90, alignment: .leading)
            Text(value.isEmpty ? "—" : value)
                .textSelection(.enabled)
            Spacer()
        }
    }
}
