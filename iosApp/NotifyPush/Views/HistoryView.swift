import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var store: AppStore
    @State private var confirmingClear = false

    var body: some View {
        NavigationStack {
            Group {
                if store.history.isEmpty {
                    ContentUnavailableView(
                        "No history yet",
                        systemImage: "clock.arrow.circlepath",
                        description: Text("Dispatched notifications will appear here, newest first.")
                    )
                } else {
                    List(store.history) { record in
                        NavigationLink {
                            HistoryDetailView(record: record)
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(record.headline).font(.headline)
                                Text(record.payload.sourceApp)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(record.summary)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(record.recordedAt, style: .relative)
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("History")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if store.pendingRetryCount > 0 {
                        Button("Retry now (\(store.pendingRetryCount))") {
                            Task { await store.retryPending() }
                        }
                        .disabled(store.isBusy)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Clear") { confirmingClear = true }
                        .disabled(store.history.isEmpty)
                }
            }
            .confirmationDialog(
                "Clear all history?",
                isPresented: $confirmingClear,
                titleVisibility: .visible
            ) {
                Button("Clear history", role: .destructive) {
                    Task { await store.clearHistory() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This deletes every recorded payload and result. It cannot be undone.")
            }
        }
    }
}
