import Foundation

// Bridges Kotlin suspend functions (completionHandler form) to Swift async.
func awaitValue<T>(_ call: (@escaping (T?, Error?) -> Void) -> Void) async -> T? {
    await withCheckedContinuation { continuation in
        call { value, _ in continuation.resume(returning: value) }
    }
}

func awaitVoid(_ call: (@escaping (Error?) -> Void) -> Void) async {
    await withCheckedContinuation { continuation in
        call { _ in continuation.resume() }
    }
}
