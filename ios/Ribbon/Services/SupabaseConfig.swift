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

    /// Remote sync is dark until the sign-in thread ships; the app is
    /// local-first either way, and flipping this on is the last step of
    /// wiring accounts, not the first.
    static let remoteEnabled = false
}
