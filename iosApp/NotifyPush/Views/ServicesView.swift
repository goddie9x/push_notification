import SwiftUI

struct ServicesView: View {
    @EnvironmentObject private var store: AppStore
    @State private var editing: AppDestination?
    @State private var adding = false

    var body: some View {
        NavigationStack {
            Group {
                if store.destinations.isEmpty {
                    ContentUnavailableView(
                        "No services yet",
                        systemImage: "server.rack",
                        description: Text("Add a webhook destination to forward notifications to.")
                    )
                } else {
                    List {
                        ForEach(store.destinations) { destination in
                            destinationRow(destination)
                        }
                        .onDelete { indexSet in
                            for index in indexSet {
                                let destination = store.destinations[index]
                                Task { await store.deleteDestination(destination) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Services")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { adding = true } label: {
                        Label("Add service", systemImage: "plus")
                    }
                }
            }
            .sheet(item: $editing) { destination in
                DestinationFormView(destination: destination) { updated in
                    Task { await store.saveDestination(updated) }
                }
            }
            .sheet(isPresented: $adding) {
                DestinationFormView(destination: AppDestination.blank()) { created in
                    Task { await store.saveDestination(created) }
                }
            }
        }
    }

    private func destinationRow(_ destination: AppDestination) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(destination.name).font(.headline)
                Text(destination.url)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                if !destination.appsFilter.isEmpty {
                    Text("Apps: \(destination.appsFilter.joined(separator: ", "))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Button("Test") {
                Task { await store.sendTestToDestination(destination, title: "NotifyPush test", body: "Test from iOS") }
            }
            .buttonStyle(.bordered)
            .disabled(store.isBusy || !destination.enabled)
            Toggle("", isOn: Binding(
                get: { destination.enabled },
                set: { enabled in
                    Task { await store.setEnabled(destination, enabled: enabled) }
                }
            ))
            .labelsHidden()
        }
        .contentShape(Rectangle())
        .onTapGesture { editing = destination }
    }
}
