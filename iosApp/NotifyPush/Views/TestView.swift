import SwiftUI

struct TestView: View {
    @EnvironmentObject private var store: AppStore
    @State private var title = "NotifyPush test"
    @State private var body = "Hello from the iOS test screen."

    var body: some View {
        NavigationStack {
            Form {
                Section("Test notification") {
                    TextField("Title", text: $title)
                    TextField("Body", text: $body, axis: .vertical)
                        .lineLimit(3...)
                }
                Section {
                    Button("Send test notification") {
                        Task { await store.sendTest(title: title, body: body) }
                    }
                    .disabled(store.isBusy || title.isEmpty && body.isEmpty)
                } footer: {
                    Text("Dispatches a sample payload to every enabled destination that matches it.")
                }
                if store.isBusy {
                    Section { ProgressView() }
                }
                if !store.testResults.isEmpty {
                    Section("Results") {
                        ForEach(store.testResults) { outcome in
                            OutcomeRow(outcome: outcome)
                        }
                    }
                }
            }
            .navigationTitle("Test")
        }
    }
}

struct OutcomeRow: View {
    let outcome: AppDispatchOutcome

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(outcome.destinationName).font(.headline)
                if !outcome.detail.isEmpty {
                    Text(outcome.detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text(outcome.status.label)
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(statusColor.opacity(0.15))
                .foregroundStyle(statusColor)
                .clipShape(Capsule())
        }
    }

    private var statusColor: Color {
        switch outcome.status {
        case .delivered: return .green
        case .rejected: return .orange
        case .failed: return .red
        }
    }
}
