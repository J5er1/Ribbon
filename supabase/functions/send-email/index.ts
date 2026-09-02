// send-email — the send_email auth hook. Ribbon's sign-in mail leaves here.
//
// Why it exists: Supabase's stock cap is two auth emails an hour, which is a
// guard on their shared relay rather than a policy we chose. Taking delivery
// over lifts it — and hands us the duty it was doing. This function is that
// duty. It is the only thing standing between "anyone who learns this URL"
// and "Ribbon sends mail to whoever they name, as often as they like," so
// the order of business below is: prove the caller is GoTrue, prove the
// recipient has not been sent too much already, and only then send.
//
// It runs with verify_jwt = false, because the hook fires before any JWT
// exists — the Standard Webhooks signature is the whole of the caller's
// authentication. That is why a missing secret is a hard stop rather than a
// warning: unverified, this endpoint is a mail cannon.
//
// Delivery is plain SMTP, spoken from here rather than through a provider's
// HTTP API, so Ribbon's mail needs no account anywhere but the mail host it
// already has. The trade is latency: a TCP connect, a TLS upgrade and an
// AUTH round trip all have to fit inside the hook's budget, where an HTTP
// API would be one request. Hence the timeout below — and if a host ever
// makes that budget untenable, sendMail() is the only function that has to
// change.
//
// Deploy:
//   supabase functions deploy send-email --no-verify-jwt
//   supabase secrets set --env-file supabase/functions/.env
// Secrets it reads (SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected
// by the platform):
//   SEND_EMAIL_HOOK_SECRET   required — "v1,whsec_<base64>" from the
//                            dashboard's Authentication → Hooks, or
//                            config.toml's [auth.hook.send_email].secrets
//   SMTP_HOST/USER/PASS      required — the mail host
//   SMTP_PORT                optional, default 587 (or 465 for implicit TLS)
//   SEND_EMAIL_FROM          required — e.g. "Ribbon <hello@ribbon.app>"
//   EMAIL_THROTTLE_PEPPER    recommended — see hashRecipient below
//   EMAIL_MAX_PER_HOUR       optional, default 6

import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";
import { SMTPClient } from "https://deno.land/x/denomailer@1.6.0/mod.ts";
import { codeEmail } from "./_templates/code-email.ts";

const HOOK_SECRET = Deno.env.get("SEND_EMAIL_HOOK_SECRET") ?? "";
const SEND_FROM = Deno.env.get("SEND_EMAIL_FROM") ?? "";
const SMTP_HOST = Deno.env.get("SMTP_HOST") ?? "";
const SMTP_USER = Deno.env.get("SMTP_USER") ?? "";
const SMTP_PASS = Deno.env.get("SMTP_PASS") ?? "";
const SMTP_PORT = clampInt(Deno.env.get("SMTP_PORT"), 587, 1, 65535);
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const THROTTLE_PEPPER = Deno.env.get("EMAIL_THROTTLE_PEPPER") ?? "";

// The hook's whole invocation has about five seconds, and the throttle call
// wants some of it, so a mail host that has stopped answering must be given
// up on rather than waited out. Failing here returns a retry-able 503.
const SMTP_TIMEOUT_MS = clampInt(Deno.env.get("SMTP_TIMEOUT_MS"), 3500, 500, 10000);

const MAX_PER_HOUR = clampInt(Deno.env.get("EMAIL_MAX_PER_HOUR"), 6, 1, 60);
const WINDOW_SECONDS = 3600;
// The floor between two codes for one address. GoTrue's max_frequency says
// the same thing and runs first, but this is the copy that is ours: without
// it, an hourly ceiling alone would still allow six emails in six seconds.
const MIN_INTERVAL_SECONDS = clampInt(Deno.env.get("EMAIL_MIN_INTERVAL_SECONDS"), 60, 0, 3600);

// Mirrors config.toml's auth.email.otp_expiry, for the line in the mail that
// tells the reader how long they have.
const OTP_EXPIRY_MINUTES = clampInt(Deno.env.get("OTP_EXPIRY_MINUTES"), 10, 1, 60);

// The hook is documented to cap payloads at 20KB; hold to roughly that
// ourselves so a stuffed body is refused before it is hashed or parsed.
// Characters rather than bytes, which is near enough for a sanity guard and
// errs toward refusing.
const MAX_BODY_CHARS = 20 * 1024;

// The sign-in thread is all Ribbon asks GoTrue for: a code to a new address
// (signup) or a returning one (magiclink). Anything else means an auth
// feature was switched on without teaching this function to write the mail
// for it, and silently dropping a security email is the worst way to find
// that out.
const HANDLED_ACTIONS = new Set(["signup", "magiclink"]);

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method !== "POST") {
    return hookError(405, "method_not_allowed");
  }

  // Fail closed on configuration. Each of these, absent, turns the function
  // into something worse than broken.
  if (!HOOK_SECRET) return hookError(500, "hook secret not configured");
  if (!SMTP_HOST || !SMTP_USER || !SMTP_PASS || !SEND_FROM) {
    return hookError(500, "mail host not configured");
  }
  // Port 25 is plaintext by convention and blocked by most hosts anyway.
  // Refusing it here means a misconfiguration surfaces as a clear error
  // rather than as credentials offered to a connection that never upgrades.
  if (SMTP_PORT === 25) return hookError(500, "refusing SMTP on port 25; use 587 or 465");
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) return hookError(500, "throttle backend not configured");

  const raw = await req.text();
  if (raw.length > MAX_BODY_CHARS) {
    return hookError(400, "payload too large");
  }

  // 1. Is this GoTrue? The signature covers the raw body, so it has to be
  //    verified before the body is parsed or trusted for anything. The
  //    library does the constant-time compare and the timestamp window;
  //    hand-rolling either is how this goes wrong quietly.
  let payload: HookPayload;
  try {
    const webhook = new Webhook(HOOK_SECRET.replace("v1,whsec_", ""));
    payload = webhook.verify(raw, Object.fromEntries(req.headers)) as HookPayload;
  } catch (_error) {
    // Deliberately uninformative: a caller who cannot sign learns only that
    // they could not sign.
    return hookError(401, "invalid signature");
  }

  // 2. Is it the shape we agreed on?
  const recipient = payload?.user?.email?.trim();
  const code = payload?.email_data?.token;
  const action = payload?.email_data?.email_action_type;

  if (!recipient || !recipient.includes("@")) {
    return hookError(400, "no recipient");
  }
  // GoTrue's own schema says six digits. Checking it here is what lets the
  // template interpolate the code without further thought.
  if (typeof code !== "string" || !/^[0-9]{6}$/.test(code)) {
    return hookError(400, "unexpected token format");
  }
  if (!HANDLED_ACTIONS.has(action)) {
    console.error(`send-email: refusing unhandled action ${JSON.stringify(action)}`);
    return hookError(500, `unhandled email action: ${action}`);
  }

  // 3. Is this address owed another code yet?
  //
  //    The server-side cooldown, and the check the built-in limit used to
  //    stand in for. Both halves of it — a minute between codes, six an
  //    hour — are per-recipient, which nothing else in the stack is:
  //    everything in [auth.rate_limit] is project-wide, so without this one
  //    inbox could be buried using the whole project's allowance. It does
  //    not trust the app to have counted; the app's copy of the same window
  //    only exists so the button can say why it is waiting.
  //
  //    The slot is taken immediately before the send and is not refunded if
  //    the send fails: a provider hiccup costs one of six rather than the
  //    hour, and a refund path is one more thing an attacker could aim at.
  let verdict: ThrottleVerdict;
  try {
    verdict = await takeSendSlot(recipient);
  } catch (error) {
    // Cannot count means cannot promise. 503 is retry-able, so a blip
    // resolves itself on GoTrue's retry rather than surfacing to the person
    // signing in.
    console.error(`send-email: throttle unavailable: ${errorMessage(error)}`);
    return hookError(503, "throttle unavailable", { retryable: true });
  }

  if (!verdict.allowed) {
    // A refusal moves nothing, so GoTrue's three retries re-read the same
    // answer rather than pushing the next legitimate send further out.
    // Logged by digest — the address itself never reaches the log.
    console.warn(
      `send-email: refused ${verdict.digest} (${verdict.reason}, ${verdict.sent_in_window} in window)`,
    );
    const message = verdict.reason === "hourly_limit"
      ? "Too many sign-in codes for this address. Try again later."
      : "A sign-in code was just sent to this address.";
    return hookError(429, message, {
      retryable: true,
      retryAfterSeconds: verdict.retry_after_seconds,
    });
  }

  // 4. Send it.
  const mail = codeEmail({
    code,
    recipient,
    minutesValid: OTP_EXPIRY_MINUTES,
  });

  try {
    await sendMail({ to: recipient, ...mail });
  } catch (error) {
    // Note what this does NOT do: give the slot back. If the send half
    // succeeded — delivered, then timed out on the way home — GoTrue's retry
    // arrives inside the minute and the throttle refuses it, so a timeout
    // cannot turn into two codes in one inbox.
    console.error(`send-email: mail host rejected send: ${errorMessage(error)}`);
    return hookError(503, "could not send the code", { retryable: true });
  }

  // Note what left, never to whom and never the code itself. A log line
  // carrying a live one-time code is a credential in a place nobody is
  // guarding.
  console.log(`send-email: sent ${action} code to ${verdict.digest}`);

  return new Response(JSON.stringify({}), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
});

// --------------------------------------------------------------- throttle

interface ThrottleVerdict {
  allowed: boolean;
  reason: "ok" | "too_soon" | "hourly_limit";
  sent_in_window: number;
  remaining: number;
  retry_after_seconds: number;
  digest: string;
}

/** Claims one send against the address's cooldown — both the minute
 *  between codes and the hourly ceiling, decided in one atomic call. */
async function takeSendSlot(recipient: string): Promise<ThrottleVerdict> {
  const hash = await hashRecipient(recipient);

  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/note_auth_email_send`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
    },
    // The p_ prefixes are the function's parameter names, and PostgREST
    // matches these keys to them exactly — see the migration for why they
    // are spelled that way.
    body: JSON.stringify({
      p_recipient_hash: hash,
      p_window_seconds: WINDOW_SECONDS,
      p_max_in_window: MAX_PER_HOUR,
      p_min_interval_seconds: MIN_INTERVAL_SECONDS,
    }),
  });

  if (!response.ok) {
    throw new Error(`rpc ${response.status}: ${await response.text()}`);
  }

  const verdict = await response.json() as Omit<ThrottleVerdict, "digest">;
  if (typeof verdict?.allowed !== "boolean") {
    throw new Error("rpc returned an unreadable verdict");
  }
  // Eight characters is plenty to correlate two log lines and far too few to
  // walk back to an address.
  return { ...verdict, digest: hash.slice(0, 8) };
}

/**
 * The key the throttle counts against: HMAC-SHA256 of the address, so the
 * counters table is a list of opaque keys rather than a second copy of who
 * uses Ribbon.
 *
 * The pepper is recommended, not required, and its absence degrades to a
 * plain digest rather than stopping sign-in. That is a deliberate line:
 * refusing to send mail because a hardening secret is unset would trade a
 * real outage for a small privacy gain, and auth.users holds every one of
 * these addresses in the clear regardless. The pepper's actual job is
 * narrower — that this table, leaked ALONE through a stray grant or an old
 * backup, gives up nothing. (Compare the hook secret above, which is
 * authentication and does hard-stop.)
 *
 * Normalization is lowercase and trim, nothing cleverer. Folding away Gmail
 * dots or plus-tags would let one person's throttle shut another person out.
 */
async function hashRecipient(recipient: string): Promise<string> {
  const normalized = recipient.trim().toLowerCase();
  const encoder = new TextEncoder();

  if (!THROTTLE_PEPPER) {
    const digest = await crypto.subtle.digest("SHA-256", encoder.encode(normalized));
    return toHex(digest);
  }

  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(THROTTLE_PEPPER),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(normalized));
  return toHex(signature);
}

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

// ------------------------------------------------------------------ mail

/**
 * Hands one message to the mail host over SMTP.
 *
 * A connection per invocation, opened and closed here: edge invocations are
 * frozen between calls, so a pooled socket would be resumed dead. (denomailer
 * pools only when asked; it is not asked.)
 *
 * On TLS — port 465 is implicit TLS, and 587 connects in the clear and
 * upgrades with STARTTLS, which the client does on its own. What is never
 * set is `debug.allowUnsecure`: without it the client refuses to authenticate
 * over a connection that never became TLS, so the password cannot be handed
 * to a server that declined to upgrade.
 */
async function sendMail(
  message: { to: string; subject: string; html: string; text: string },
): Promise<void> {
  const client = new SMTPClient({
    connection: {
      hostname: SMTP_HOST,
      port: SMTP_PORT,
      tls: SMTP_PORT === 465,
      auth: { username: SMTP_USER, password: SMTP_PASS },
    },
  });

  try {
    await withTimeout(
      client.send({
        from: SEND_FROM,
        to: message.to,
        subject: message.subject,
        content: message.text,
        html: message.html,
      }),
      SMTP_TIMEOUT_MS,
    );
  } finally {
    // Always, including on the timeout path — an abandoned socket would be
    // held open across the freeze.
    await client.close().catch(() => {});
  }
}

/** Rejects if the work outlasts the budget. The work itself cannot be
 *  cancelled; closing the client is what actually lets go of the socket. */
function withTimeout<T>(work: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`timed out after ${ms}ms`)), ms);
    work.then(resolve, reject).finally(() => clearTimeout(timer));
  });
}

// --------------------------------------------------------------- plumbing

interface HookPayload {
  user?: { email?: string };
  email_data?: { token?: string; email_action_type?: string };
}

/**
 * GoTrue's error envelope. The Content-Type is not optional — without it
 * Supabase Auth reads the response as a failure and returns a 500 whatever
 * we meant to say.
 *
 * 429 and 503 are the retry-able codes (three tries, two seconds apart,
 * inside a five-second budget); they need a non-empty retry-after header to
 * be treated as such. 400 and 403 are NOT passed through — Supabase turns
 * them into a 500 for the client — which is why the throttle refusal above
 * is a 429 and not a 403.
 */
function hookError(
  status: number,
  message: string,
  options: { retryable?: boolean; retryAfterSeconds?: number } = {},
): Response {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (options.retryable) {
    headers["retry-after"] = String(options.retryAfterSeconds ?? 60);
  }
  return new Response(
    JSON.stringify({ error: { http_code: status, message } }),
    { status, headers },
  );
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function clampInt(
  raw: string | undefined,
  fallback: number,
  min: number,
  max: number,
): number {
  const parsed = Number.parseInt(raw ?? "", 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}
