import Foundation

/// Configuration for Auth0 Third-Party Authentication with Supabase.
///
/// Once you have created an Auth0 tenant and a "Ribbon Mobile" Native application
/// in the Auth0 Dashboard, fill in your domain and client ID here.
/// See /supabase/auth0/README.md for the step-by-step setup guide.
enum Auth0Config {
    /// Your Auth0 tenant domain, e.g. "ribbon.us.auth0.com" or "dev-xxxx.us.auth0.com".
    static let domain = "dev-m45drxnu73cdtjcn.us.auth0.com"

    /// Your Auth0 Native Application Client ID.
    static let clientId = "6AVTQwedXsaH7RrtDbhHOMKME8hazHjC"

    /// The custom URL scheme registered for Auth0 redirection.
    static let scheme = "bible.ribbon.app"

    /// Returns true when Auth0 credentials have been supplied.
    static var isConfigured: Bool {
        !domain.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !clientId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
