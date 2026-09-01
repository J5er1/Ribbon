import Foundation

// The Ribbon backend: a Supabase project (Postgres + row-level security +
// Realtime presence + storage). The schema lives in /supabase/migrations.
//
// The publishable key is safe to ship in the app — every table is guarded
// by row-level security, and storage by per-room policies. Sign-in is an
// emailed code; there are no passwords (§6.10).

enum SupabaseConfig {
    static let url = URL(string: "https://noyccfkaotuvhhaoccck.supabase.co")!
    static let publishableKey = "sb_publishable_ZokpZ0nlXW2IQfNDGnvNKQ_mPF63qGU"

    /// The sign-in thread is wired: accounts (emailed code), invites,
    /// joining, and the room surface — rooms, members, profiles, readings,
    /// fires, quiet days. Notes, highlights and positions still travel
    /// with the full sync engine, which is the next piece of work
    /// (docs/deviations.md). The app is local-first either way.
    static let remoteEnabled = true
}
