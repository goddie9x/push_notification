import SwiftUI

@main
struct NotifyPushApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .task { await store.startup() }
                .onOpenURL { url in
                    Task { await store.handleIncomingURL(url) }
                }
        }
    }
}

struct ContentView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            ServicesView()
                .tabItem { Label("Services", systemImage: "server.rack") }
            TestView()
                .tabItem { Label("Test", systemImage: "paperplane") }
            HistoryView()
                .tabItem { Label("History", systemImage: "clock.arrow.circlepath") }
        }
        .overlay(alignment: .top) {
            if let notice = store.ingestNotice {
                ingestBanner(notice)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                store.consumePendingIntentPayload()
            }
        }
    }

    private func ingestBanner(_ notice: String) -> some View {
        HStack {
            Text(notice)
                .font(.subheadline)
            Spacer()
            Button("Dismiss") { store.ingestNotice = nil }
        }
        .padding()
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding()
    }
}
