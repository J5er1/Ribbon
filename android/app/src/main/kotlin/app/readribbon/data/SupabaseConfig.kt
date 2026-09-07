package app.readribbon.data

// The Ribbon backend: a Supabase project (Postgres + row-level security +
// Realtime presence + storage). The schema lives in /supabase/migrations,
// and it is the same project the iOS app talks to — the whole point of the
// Android build is that a couple on two different platforms is still one
// room, one fire, one shelf.
//
// The publishable key is safe to ship in the app — every table is guarded by
// row-level security, and storage by per-room policies. Sign-in is an
// emailed code; there are no passwords (§6.10).

object SupabaseConfig {
    const val URL = "https://noyccfkaotuvhhaoccck.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_ZokpZ0nlXW2IQfNDGnvNKQ_mPF63qGU"

    /**
     * The sign-in thread is wired: accounts (emailed code), invites,
     * joining, and the room surface — rooms, members, profiles, readings,
     * fires, quiet days. Notes, highlights and positions still travel with
     * the full sync engine, which is the next piece of work
     * (docs/deviations.md). The app is local-first either way.
     */
    const val REMOTE_ENABLED = true

    /** The domain the room owns; invite links point at it (S15). */
    const val INVITE_HOST = "readribbon.app"
}
