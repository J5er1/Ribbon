# Setting up Auth0 with Ribbon & Supabase

This guide walks through configuring **Auth0** as the external authentication provider for Ribbon's Supabase backend.

---

## 1. Auth0 Dashboard Setup

### A. Create the Native Mobile Application
1. Log in to your [Auth0 Dashboard](https://manage.auth0.com/).
2. Navigate to **Applications → Applications → Create Application**.
3. Name: `Ribbon Mobile`.
4. Choose **Native** (iOS, Android) and click **Create**.
5. Under the **Settings** tab, configure the following:
   * **Allowed Callback URLs**:
     ```text
     bible.ribbon.app://YOUR_AUTH0_DOMAIN/ios/bible.ribbon.app/callback,
     app.readribbon.debug://YOUR_AUTH0_DOMAIN/android/app.readribbon.debug/callback
     ```
     *(Replace `YOUR_AUTH0_DOMAIN` with your actual domain, e.g. `dev-abc123.us.auth0.com`)*
   * **Allowed Logout URLs**:
     ```text
     bible.ribbon.app://YOUR_AUTH0_DOMAIN/ios/bible.ribbon.app/callback,
     app.readribbon.debug://YOUR_AUTH0_DOMAIN/android/app.readribbon.debug/callback
     ```
6. Click **Save Changes** at the bottom.
7. Note down your **Domain** and **Client ID**.

---

### B. Create the Post-Login Action (Mandatory)
Supabase requires incoming tokens to have the `role: "authenticated"` claim and a valid `user_uuid` to pass PostgreSQL Row-Level Security.

1. In Auth0 Dashboard, navigate to **Actions → Library → Build Custom**.
2. Settings:
   * **Name**: `Supabase Claims`
   * **Trigger**: `Login / Post Login`
   * **Runtime**: `Node 18` or `Node 22`
3. Click **Create**.
4. In the left panel of the Action code editor, click the **Dependencies** icon (`+`):
   * Add package: `uuid` (version: `^9.0.0` or `latest`).
5. Replace the editor contents with the code from `supabase/auth0/action.js`:
   ```javascript
   const { v5: uuidv5 } = require('uuid');
   const RIBBON_NAMESPACE = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';

   exports.onExecutePostLogin = async (event, api) => {
     api.idToken.setCustomClaim('role', 'authenticated');
     const userUuid = uuidv5(event.user.user_id, RIBBON_NAMESPACE);
     api.idToken.setCustomClaim('user_uuid', userUuid);
   };
   ```
6. Click **Deploy** at the top right.
7. Go to **Actions → Flows → Login**.
8. In the right sidebar under "Custom", find **Supabase Claims** and drag it between **Start** and **Complete**.
9. Click **Apply** at the top right.

---

## 2. Supabase Dashboard Setup

1. Log in to your [Supabase Dashboard](https://supabase.com/dashboard) and select your Ribbon project.
2. Go to **Authentication → Third-Party Auth** (or **Settings → Authentication**).
3. Find **Auth0** and toggle it **On** (or click **Add Provider → Auth0**).
4. Enter your Auth0 tenant domain:
   * **Tenant / Domain**: `YOUR_AUTH0_DOMAIN` (e.g. `dev-abc123.us.auth0.com`).
5. Click **Save**.
6. Run the migration `supabase/migrations/20260908000000_ribbon_auth0_support.sql` in Supabase's SQL Editor (or via `supabase db push`) to enable deterministic UUID mapping and allow Auth0 profiles.

---

## 3. How the Apps Connect

Once authenticated with Auth0, the app receives the **ID token** (`credentials.idToken`). It passes this token directly in the `Authorization: Bearer <idToken>` header to Supabase PostgREST endpoints.

Because Supabase is configured with your Auth0 tenant, Supabase automatically:
1. Validates the signature using Auth0's OIDC Discovery JWKS (`https://YOUR_AUTH0_DOMAIN/.well-known/jwks.json`).
2. Reads the `role: "authenticated"` claim and grants standard authenticated Postgres privileges.
3. Resolves `auth.uid()` to the deterministic `user_uuid` claim, allowing all existing RLS policies and table relations to operate without changes.
