/**
 * Auth0 Post-Login Action for Ribbon + Supabase Integration
 * 
 * Zero external npm dependencies — uses Node.js standard built-in crypto module.
 * 
 * How to setup in Auth0:
 * 1. Auth0 Dashboard -> Actions -> Library -> Build Custom
 * 2. Name: "Supabase Claims", Trigger: "Login / Post Login"
 * 3. Paste this code into the editor and click Deploy
 * 4. Go to Actions -> Flows -> Login
 * 5. Drag "Supabase Claims" from the right sidebar into the flow between Start and Complete
 * 6. Click Apply
 */

const crypto = require('crypto');

// Fixed DNS namespace for deterministic UUIDv5 generation matching Ribbon's Postgres helper
const RIBBON_NAMESPACE = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';

function computeUuidV5(name, namespace = RIBBON_NAMESPACE) {
  const ns = Buffer.from(namespace.replace(/-/g, ''), 'hex');
  const hash = crypto.createHash('sha1').update(ns).update(name, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x50; // Version 5
  hash[8] = (hash[8] & 0x3f) | 0x80; // Variant RFC 4122
  const hex = hash.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

exports.onExecutePostLogin = async (event, api) => {
  // 1. Supabase requires the 'role' claim in the ID token to match the Postgres role
  api.idToken.setCustomClaim('role', 'authenticated');

  // 2. Compute a stable, deterministic UUID for this user from their Auth0 user_id
  const userUuid = computeUuidV5(event.user.user_id);
  api.idToken.setCustomClaim('user_uuid', userUuid);
};

