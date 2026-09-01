import Foundation
import RibbonCore

// Licensed translations stream through Ribbon's own proxy — a Supabase
// Edge Function (`bible-proxy`) that holds the API.Bible key server-side,
// so the key never ships in the app and the license's terms are enforced
// in exactly one place. The client caches the book being read and nothing
// more; the bundled public-domain translations remain whole and offline.

protocol RemoteScriptureProvider: Sendable {
    func fetchChapter(bookID: String, chapter: Int, translation: Translation) async throws -> ScriptureChapter
}

enum RemoteScriptureError: Error {
    case notConfigured
    case unavailable
    case badPayload
}

struct APIBibleProvider: RemoteScriptureProvider {
    var proxyURL: URL = SupabaseConfig.url.appending(path: "functions/v1/bible-proxy")

    func fetchChapter(bookID: String, chapter: Int, translation: Translation) async throws -> ScriptureChapter {
        guard case .apiBible(let bibleID) = translation.source, !bibleID.isEmpty else {
            throw RemoteScriptureError.notConfigured
        }
        var components = URLComponents(url: proxyURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "bible", value: bibleID),
            URLQueryItem(name: "chapter", value: "\(bookID).\(chapter)"),
        ]
        var request = URLRequest(url: components.url!)
        request.setValue(SupabaseConfig.publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(SupabaseConfig.publishableKey)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw RemoteScriptureError.unavailable }
        guard http.statusCode != 503 else { throw RemoteScriptureError.notConfigured }
        guard (200..<300).contains(http.statusCode) else { throw RemoteScriptureError.unavailable }
        guard let converted = APIBibleContent.chapter(number: chapter, from: data) else {
            throw RemoteScriptureError.badPayload
        }
        return converted
    }
}

extension ScriptureStore {
    /// The on-device cache policy for licensed text: the book being read,
    /// and nothing older. Enforced here so a future provider can't
    /// accidentally widen it.
    static let licensedCacheDirectory: URL = {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LicensedScripture", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }()

    /// A chapter of a licensed translation, if it has already streamed.
    func cachedRemoteChapter(_ address: VerseAddress, translation: Translation) -> ScriptureChapter? {
        let url = Self.licensedChapterURL(address, translation: translation)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(ScriptureChapter.self, from: data)
    }

    /// Stream a chapter through the proxy and keep it for this reading.
    /// Chapters from other books of the same translation are dropped —
    /// "kept on the phone for the current reading" is the whole policy.
    func ensureRemoteChapter(
        _ address: VerseAddress, translation: Translation,
        provider: RemoteScriptureProvider = APIBibleProvider()
    ) async -> ScriptureChapter? {
        if let cached = cachedRemoteChapter(address, translation: translation) {
            return cached
        }
        guard let chapter = try? await provider.fetchChapter(
            bookID: address.bookID, chapter: address.chapter, translation: translation)
        else { return nil }

        pruneLicensedCache(keeping: address.bookID, translation: translation)
        if let data = try? JSONEncoder().encode(chapter) {
            try? data.write(
                to: Self.licensedChapterURL(address, translation: translation), options: .atomic)
        }
        return chapter
    }

    private static func licensedChapterURL(_ address: VerseAddress, translation: Translation) -> URL {
        licensedCacheDirectory
            .appendingPathComponent("\(translation.id.rawValue)-\(address.bookID)-\(address.chapter).json")
    }

    private func pruneLicensedCache(keeping bookID: String, translation: Translation) {
        let keep = "\(translation.id.rawValue)-\(bookID)-"
        let files = (try? FileManager.default.contentsOfDirectory(
            at: Self.licensedCacheDirectory, includingPropertiesForKeys: nil)) ?? []
        for file in files
        where file.lastPathComponent.hasPrefix("\(translation.id.rawValue)-")
            && !file.lastPathComponent.hasPrefix(keep) {
            try? FileManager.default.removeItem(at: file)
        }
    }
}
