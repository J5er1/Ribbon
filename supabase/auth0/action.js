/**
 * Auth0 Post-Login Action for Ribbon + Supabase Integration
 * 
 * Required Auth0 Action Dependencies:
 *   - uuid (version ^9.0.0 or latest)
 * 
 * How to setup in Auth0:
 * 1. Auth0 Dashboard -> Actions -> Library -> Build Custom
 * 2. Name: "Supabase Claims", Trigger: "Login / Post Login"
 * 3. In the left sidebar of the action editor, click Dependencies (+) and add "uuid"
 * 4. Paste this code into the editor and click Deploy
 * 5. Go to Actions -> Flows -> Login
 * 6. Drag "Supabase Claims" from the right sidebar into the flow between Start and Complete
 * 7. Click Apply
 */

const { v5: uuidv5 } = require('uuid');

// Fixed DNS namespace for deterministic UUIDv5 generation matching Ribbon's Postgres helper
const RIBBON_NAMESPACE = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';

exports.onExecutePostLogin = async (event, api) => {
  // 1. Supabase requires the 'role' claim in the ID token to match the Postgres role
  api.idToken.setCustomClaim('role', 'authenticated');

  // 2. Compute a stable, deterministic UUID for this user from their Auth0 user_id
  const userUuid = uuidv5(event.user.user_id, RIBBON_NAMESPACE);
  api.idToken.setCustomClaim('user_uuid', userUuid);
};
