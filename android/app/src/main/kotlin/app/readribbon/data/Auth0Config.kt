package app.readribbon.data

/**
 * Configuration for Auth0 Third-Party Authentication with Supabase.
 *
 * Once you have created an Auth0 tenant and a "Ribbon Mobile" Native application
 * in the Auth0 Dashboard, fill in your domain and client ID here.
 * See /supabase/auth0/README.md for the step-by-step setup guide.
 */
object Auth0Config {
    /** Your Auth0 tenant domain, e.g. "ribbon.us.auth0.com" or "dev-xxxx.us.auth0.com". */
    const val DOMAIN = "dev-m45drxnu73cdtjcn.us.auth0.com"

    /** Your Auth0 Native Application Client ID. */
    const val CLIENT_ID = ""

    /** The custom scheme configured for Android redirects. */
    const val SCHEME = "app.readribbon.debug"

    /** Returns true when Auth0 credentials have been supplied. */
    val isConfigured: Boolean
        get() = DOMAIN.isNotBlank() && CLIENT_ID.isNotBlank()
}
