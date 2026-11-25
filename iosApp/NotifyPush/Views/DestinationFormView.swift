import SwiftUI

struct DestinationFormView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var url: String
    @State private var headerName: String
    @State private var headerValue: String
    @State private var enabled: Bool
    @State private var appsFilterText: String

    private let destination: AppDestination
    private let onSave: (AppDestination) -> Void

    init(destination: AppDestination, onSave: @escaping (AppDestination) -> Void) {
        self.destination = destination
        self.onSave = onSave
        _name = State(initialValue: destination.name)
        _url = State(initialValue: destination.url)
        _headerName = State(initialValue: destination.headerName)
        _headerValue = State(initialValue: destination.headerValue)
        _enabled = State(initialValue: destination.enabled)
        _appsFilterText = State(initialValue: destination.appsFilterText)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Service") {
                    TextField("Name", text: $name)
                    TextField("Webhook URL", text: $url)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                    Toggle("Enabled", isOn: $enabled)
                }
                Section("Auth header") {
                    TextField("Header name", text: $headerName)
                        .textInputAutocapitalization(.never)
                    SecureField("Header value", text: $headerValue)
                }
                Section("Apps filter") {
                    TextField("Bundle IDs or app names, comma separated", text: $appsFilterText, axis: .vertical)
                        .textInputAutocapitalization(.never)
                    Text("Empty means all apps. A notification is only sent when its bundle ID or app name matches one entry.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle(destination.name.isEmpty ? "Add service" : "Edit service")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(!isValid)
                }
            }
        }
    }

    private var isValid: Bool {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty,
              let parsed = URL(string: url),
              let scheme = parsed.scheme?.lowercased(),
              ["http", "https"].contains(scheme),
              parsed.host != nil else { return false }
        return true
    }

    private func save() {
        var updated = destination
        updated.name = name.trimmingCharacters(in: .whitespaces)
        updated.url = url.trimmingCharacters(in: .whitespaces)
        updated.headerName = headerName.trimmingCharacters(in: .whitespaces)
        updated.headerValue = headerValue
        updated.enabled = enabled
        updated.appsFilter = AppDestination.appsFilter(from: appsFilterText)
        onSave(updated)
        dismiss()
    }
}
