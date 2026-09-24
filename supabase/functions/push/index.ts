// push — the notifications that reach a phone which is not open (S19), and
// the Live Activity that says who is in the book (S24).
//
// The database decides; this only delivers. Every push starts as a row in
// `push_outbox`, written by a trigger (a note, a card, a finished book) or by
// the phone itself (reading, left, thinking of you), and that insert wakes
// this function over pg_net. `push_claim()` answers with exactly who should
// hear what — switches, quiet hours, running Live Activities, all judged
// there against the tables — and this function turns each answer into an
// APNs or FCM request.
//
// Deployed with verify_jwt off, on purpose: pg_net calls it with no token,
// and it takes nothing from its caller but the knock. A stranger who finds
// the URL can only make it deliver what was already waiting, once.
//
//   POST {}   drain the outbox
//   GET       which transports are configured: {"ios": bool, "android": bool}
//             — the apps read this to know whether the server is saying a
//             thing, so a phone never says it twice
//
// Secrets (supabase secrets set ... --project-ref noyccfkaotuvhhaoccck):
//   APNS_KEY_ID, APNS_TEAM_ID  the .p8 key's id, and the team it belongs to
//   APNS_PRIVATE_KEY           the .p8 file's contents, PEM and all
//   APNS_TOPIC                 optional; the app's bundle id (bible.ribbon.app)
//   FCM_SERVICE_ACCOUNT        the Firebase service account JSON, whole
//
// Until a platform's secrets exist its pushes are dropped here, quietly, and
// the apps keep posting for themselves the way they always have.

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";

// ---------------------------------------------------------------- the words

// The same strings as Copy.swift and Copy.kt (§10.3). No count, no body of
// a note, nothing a lock screen should not show a stranger.
const BOOKS: Record<string, string> = {
  GEN: "Genesis", EXO: "Exodus", LEV: "Leviticus", NUM: "Numbers",
  DEU: "Deuteronomy", JOS: "Joshua", JDG: "Judges", RUT: "Ruth",
  "1SA": "1 Samuel", "2SA": "2 Samuel", "1KI": "1 Kings", "2KI": "2 Kings",
  "1CH": "1 Chronicles", "2CH": "2 Chronicles", EZR: "Ezra", NEH: "Nehemiah",
  EST: "Esther", JOB: "Job", PSA: "Psalms", PRO: "Proverbs",
  ECC: "Ecclesiastes", SNG: "Song of Songs", ISA: "Isaiah", JER: "Jeremiah",
  LAM: "Lamentations", EZK: "Ezekiel", DAN: "Daniel", HOS: "Hosea",
  JOL: "Joel", AMO: "Amos", OBA: "Obadiah", JON: "Jonah", MIC: "Micah",
  NAM: "Nahum", HAB: "Habakkuk", ZEP: "Zephaniah", HAG: "Haggai",
  ZEC: "Zechariah", MAL: "Malachi", MAT: "Matthew", MRK: "Mark", LUK: "Luke",
  JHN: "John", ACT: "Acts", ROM: "Romans", "1CO": "1 Corinthians",
  "2CO": "2 Corinthians", GAL: "Galatians", EPH: "Ephesians",
  PHP: "Philippians", COL: "Colossians", "1TH": "1 Thessalonians",
  "2TH": "2 Thessalonians", "1TI": "1 Timothy", "2TI": "2 Timothy",
  TIT: "Titus", PHM: "Philemon", HEB: "Hebrews", JAS: "James",
  "1PE": "1 Peter", "2PE": "2 Peter", "1JN": "1 John", "2JN": "2 John",
  "3JN": "3 John", JUD: "Jude", REV: "Revelation",
};

function bookName(id: string | null): string {
  return (id && BOOKS[id]) || id || "";
}

/** "Mark 4:9" — "Psalm 23:1" for the Psalter. */
function address(book: string, chapter: number, verse: number): string {
  const name = book === "PSA" ? "Psalm" : bookName(book);
  return `${name} ${chapter}:${verse}`;
}

/** The first word of a name, the way the apps say it in passing. */
function firstName(name: string | null): string {
  const trimmed = (name ?? "").trim();
  return trimmed.split(/\s+/)[0] || trimmed;
}

// ------------------------------------------------------------ what arrives

type Device = {
  token: string;
  platform: "ios" | "android";
  environment?: "sandbox" | "production";
  liveStart?: string | null;
  activity?: string | null;
  running?: boolean;
};

type Message = {
  kind: "note" | "cards" | "finished" | "reading" | "left" | "thinking";
  room: string;
  reading: string | null;
  actor: string | null;
  actorName: string | null;
  book: string | null;
  chapter?: number;
  verse?: number;
  several?: boolean;
  fresh: boolean;
  devices: Device[];
};

/** The app's own name for each kind (NotificationKind on both platforms). */
const NOTIFY: Record<string, string> = {
  note: "notesLeft",
  cards: "cardsOpen",
  finished: "bookFinished",
  reading: "inTheBook",
  thinking: "thinkingOfYou",
  left: "left",
};

type Said = {
  title: string;
  body?: string;
  /** Where a tap lands — the same keys `Destination` reads on both apps. */
  destination: Record<string, string>;
  /** What replaces what: one post per person per room for notes. */
  collapse: string;
  /** How long it is still true. "Ruth is reading Mark" is a lie by tomorrow. */
  lifetime: number;
  /** Only the first of a run makes a sound; the rest replace it quietly. */
  sound: boolean;
};

function short(id: string | null): string {
  return (id ?? "").replace(/-/g, "").slice(0, 12);
}

function say(m: Message): Said | null {
  const who = firstName(m.actorName);
  const room = m.room;
  switch (m.kind) {
    case "note": {
      if (!m.book || m.chapter == null || m.verse == null || !m.reading) return null;
      // One note names the verse, because the address is the invitation to
      // go; several name only the person, because listing them would be a
      // count in prose (§10.3).
      return {
        title: m.several
          ? `${who} left you a note`
          : `${who} left you a note at ${address(m.book, m.chapter, m.verse)}`,
        destination: {
          kind: "verse", room, reading: m.reading, book: m.book,
          chapter: String(m.chapter), verse: String(m.verse),
        },
        collapse: `note.${short(room)}.${short(m.actor)}`,
        lifetime: 86400,
        sound: !m.several,
      };
    }
    case "cards":
      if (!m.reading || m.chapter == null) return null;
      return {
        title: "The cards are open",
        destination: { kind: "cards", room, reading: m.reading, chapter: String(m.chapter) },
        collapse: `cards.${short(room)}`,
        lifetime: 86400,
        sound: true,
      };
    case "finished":
      if (!m.book) return null;
      return {
        title: "A book finished",
        body: `You finished ${bookName(m.book)} together`,
        destination: { kind: "room", room },
        collapse: `finished.${short(m.reading)}`,
        lifetime: 86400 * 3,
        sound: true,
      };
    case "reading":
      if (!m.book) return null;
      return {
        title: `${who} is reading ${bookName(m.book)}`,
        destination: { kind: "room", room },
        collapse: `reading.${short(room)}.${short(m.actor)}`,
        lifetime: 15 * 60,
        sound: m.fresh,
      };
    case "thinking":
      // A touch first, and only then a name (§4.3): the name is the whole
      // of the sentence.
      return {
        title: who,
        destination: { kind: "room", room },
        collapse: `thinking.${short(room)}.${short(m.actor)}`,
        lifetime: 10 * 60,
        sound: true,
      };
    default:
      return null;
  }
}

// -------------------------------------------------------------- the crypto

const encoder = new TextEncoder();

function base64url(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? encoder.encode(input) : input;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemBody(pem: string): ArrayBuffer {
  // Secrets pasted onto one line arrive with literal "\n"s.
  const text = pem.replace(/\\n/g, "\n")
    .replace(/-----[A-Z ]+-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(text);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out.buffer;
}

async function signedJWT(
  header: Record<string, unknown>,
  claims: Record<string, unknown>,
  key: CryptoKey,
  algorithm: AlgorithmIdentifier | EcdsaParams,
): Promise<string> {
  const input = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const signature = new Uint8Array(
    await crypto.subtle.sign(algorithm, key, encoder.encode(input)),
  );
  return `${input}.${base64url(signature)}`;
}

// ------------------------------------------------------------------- APNs

const APNS_KEY_ID = Deno.env.get("APNS_KEY_ID") ?? "";
const APNS_TEAM_ID = Deno.env.get("APNS_TEAM_ID") ?? "";
const APNS_PRIVATE_KEY = Deno.env.get("APNS_PRIVATE_KEY") ?? "";
const APNS_TOPIC = Deno.env.get("APNS_TOPIC") || "bible.ribbon.app";
const apnsConfigured = Boolean(APNS_KEY_ID && APNS_TEAM_ID && APNS_PRIVATE_KEY);

let apnsToken: { jwt: string; issued: number } | null = null;

/**
 * The provider token. APNs refuses one that changes more often than every
 * twenty minutes, so `iat` is pinned to the half hour and the token is kept
 * for as long as this instance lives.
 */
async function apnsJWT(): Promise<string> {
  const window = Math.floor(Date.now() / 1000 / 1800) * 1800;
  if (apnsToken && apnsToken.issued === window) return apnsToken.jwt;
  const key = await crypto.subtle.importKey(
    "pkcs8", pemBody(APNS_PRIVATE_KEY),
    { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"],
  );
  const jwt = await signedJWT(
    { alg: "ES256", kid: APNS_KEY_ID },
    { iss: APNS_TEAM_ID, iat: window },
    key,
    { name: "ECDSA", hash: "SHA-256" },
  );
  apnsToken = { jwt, issued: window };
  return jwt;
}

type Outcome = "sent" | "dead" | "failed";

async function apns(
  token: string,
  environment: string | undefined,
  payload: unknown,
  headers: Record<string, string>,
): Promise<Outcome> {
  const host = environment === "sandbox" ? "api.sandbox.push.apple.com" : "api.push.apple.com";
  try {
    const response = await fetch(`https://${host}/3/device/${token}`, {
      method: "POST",
      headers: {
        authorization: `bearer ${await apnsJWT()}`,
        "content-type": "application/json",
        ...headers,
      },
      body: JSON.stringify(payload),
    });
    if (response.ok) {
      await response.body?.cancel();
      return "sent";
    }
    const reason = await response.json().then((j) => j?.reason ?? "").catch(() => "");
    if (response.status === 410 || reason === "BadDeviceToken" || reason === "DeviceTokenNotForTopic") {
      return "dead";
    }
    if (response.status === 403) apnsToken = null;
    console.warn(`apns ${response.status} ${reason}`);
    return "failed";
  } catch (error) {
    console.warn(`apns unreachable: ${error}`);
    return "failed";
  }
}

// -------------------------------------------------------------------- FCM

const FCM_SERVICE_ACCOUNT = Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "";
type ServiceAccount = { project_id: string; client_email: string; private_key: string };
let serviceAccount: ServiceAccount | null = null;
try {
  if (FCM_SERVICE_ACCOUNT) serviceAccount = JSON.parse(FCM_SERVICE_ACCOUNT);
} catch {
  console.warn("FCM_SERVICE_ACCOUNT is not JSON");
}
const fcmConfigured = Boolean(
  serviceAccount?.project_id && serviceAccount?.client_email && serviceAccount?.private_key,
);

let fcmAccess: { token: string; expires: number } | null = null;

async function fcmToken(): Promise<string | null> {
  if (!serviceAccount) return null;
  const now = Math.floor(Date.now() / 1000);
  if (fcmAccess && fcmAccess.expires - 120 > now) return fcmAccess.token;
  const key = await crypto.subtle.importKey(
    "pkcs8", pemBody(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const assertion = await signedJWT(
    { alg: "RS256", typ: "JWT" },
    {
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    },
    key,
    { name: "RSASSA-PKCS1-v1_5" },
  );
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) {
    console.warn(`fcm oauth ${response.status}`);
    return null;
  }
  const grant = await response.json();
  fcmAccess = { token: grant.access_token, expires: now + (grant.expires_in ?? 3600) };
  return fcmAccess.token;
}

/**
 * A data message, always: the Android app builds the notification itself,
 * with its own channels and its own check of what is on screen, exactly as
 * it does for what it finds in a pull.
 */
async function fcm(token: string, data: Record<string, string>, ttlSeconds: number, collapse?: string): Promise<Outcome> {
  const access = await fcmToken().catch(() => null);
  if (!access || !serviceAccount) return "failed";
  try {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
      {
        method: "POST",
        headers: { authorization: `Bearer ${access}`, "content-type": "application/json" },
        body: JSON.stringify({
          message: {
            token,
            data,
            android: {
              priority: "HIGH",
              ttl: `${ttlSeconds}s`,
              ...(collapse ? { collapse_key: collapse } : {}),
            },
          },
        }),
      },
    );
    if (response.ok) {
      await response.body?.cancel();
      return "sent";
    }
    const detail = await response.text().catch(() => "");
    if (response.status === 404 || detail.includes("UNREGISTERED") ||
        (response.status === 400 && detail.includes("registration token"))) {
      return "dead";
    }
    if (response.status === 401) fcmAccess = null;
    console.warn(`fcm ${response.status}`);
    return "failed";
  } catch (error) {
    console.warn(`fcm unreachable: ${error}`);
    return "failed";
  }
}

// ------------------------------------------------------------ the delivery

type Graveyard = { devices: Set<string>; liveStarts: Set<string>; activities: Set<string> };

const LIVE_TOPIC = `${APNS_TOPIC}.push-type.liveactivity`;
/** Twenty minutes without a heartbeat and the Live Activity says so. */
const STALE_AFTER = 20 * 60;

async function deliver(m: Message, dead: Graveyard): Promise<void> {
  const said = say(m);
  const now = Math.floor(Date.now() / 1000);
  const jobs: Promise<void>[] = [];

  for (const device of m.devices) {
    if (device.platform === "ios") {
      if (!apnsConfigured) continue;

      if (m.kind === "left") {
        // The Live Activity ends when they leave (S24), and goes at once
        // rather than lingering for the system's four hours.
        if (!device.activity) continue;
        const activity = device.activity;
        jobs.push(apns(activity, device.environment, {
          aps: {
            timestamp: now,
            event: "end",
            "content-state": { book: bookName(m.book) },
            "dismissal-date": now - 1,
          },
        }, { "apns-push-type": "liveactivity", "apns-topic": LIVE_TOPIC, "apns-priority": "10" })
          .then((outcome) => { if (outcome === "dead") dead.activities.add(activity); }));
        continue;
      }

      if (m.kind === "reading" && device.liveStart && said) {
        const liveStart = device.liveStart;
        const state = { book: bookName(m.book) };
        if (!device.running || (m.fresh && !device.activity)) {
          // Started by push, with the alert the system requires: the
          // arrival is said, once, and then it sits on the lock screen.
          jobs.push(apns(liveStart, device.environment, {
            aps: {
              timestamp: now,
              event: "start",
              "content-state": state,
              "attributes-type": "ReadingActivityAttributes",
              attributes: {
                roomID: m.room,
                readerID: m.actor ?? "",
                readerName: firstName(m.actorName),
              },
              alert: m.fresh ? { title: said.title, sound: "default" } : { title: said.title },
              "stale-date": now + STALE_AFTER,
              "input-push-token": 1,
            },
          }, { "apns-push-type": "liveactivity", "apns-topic": LIVE_TOPIC, "apns-priority": "10" })
            .then((outcome) => { if (outcome === "dead") dead.liveStarts.add(liveStart); }));
        } else if (device.activity) {
          const activity = device.activity;
          jobs.push(apns(activity, device.environment, {
            aps: {
              timestamp: now,
              event: "update",
              "content-state": state,
              "stale-date": now + STALE_AFTER,
            },
          }, { "apns-push-type": "liveactivity", "apns-topic": LIVE_TOPIC, "apns-priority": "5" })
            .then((outcome) => { if (outcome === "dead") dead.activities.add(activity); }));
        }
        continue;
      }

      if (!said) continue;
      // A heartbeat is not news. Only an arrival is said aloud.
      if (m.kind === "reading" && !m.fresh) continue;
      const alert: Record<string, string> = { title: said.title };
      if (said.body) alert.body = said.body;
      const token = device.token;
      jobs.push(apns(token, device.environment, {
        aps: {
          alert,
          ...(said.sound ? { sound: "default" } : {}),
          "thread-id": m.room,
          "interruption-level": "active",
        },
        notify: NOTIFY[m.kind],
        ...said.destination,
      }, {
        "apns-push-type": "alert",
        "apns-topic": APNS_TOPIC,
        "apns-priority": "10",
        "apns-collapse-id": said.collapse,
        "apns-expiration": String(now + said.lifetime),
      }).then((outcome) => { if (outcome === "dead") dead.devices.add(token); }));
      continue;
    }

    // Android.
    if (!fcmConfigured) continue;
    const token = device.token;
    let data: Record<string, string>;
    let ttl = 86400;
    if (m.kind === "left") {
      data = { notify: "left", room: m.room, person: m.actor ?? "" };
      ttl = 15 * 60;
    } else {
      if (!said) continue;
      data = {
        notify: NOTIFY[m.kind],
        title: said.title,
        ...(said.body ? { body: said.body } : {}),
        person: m.actor ?? "",
        fresh: m.fresh ? "1" : "0",
        quiet: said.sound ? "0" : "1",
        ...said.destination,
        // `kind` in the destination is where a tap goes; the app reads it
        // as `dest` so it cannot be mistaken for what arrived.
        dest: said.destination.kind,
      };
      delete (data as Record<string, string | undefined>).kind;
      ttl = said.lifetime;
    }
    jobs.push(fcm(token, data, ttl, m.kind === "note" ? said?.collapse : undefined)
      .then((outcome) => { if (outcome === "dead") dead.devices.add(token); }));
  }

  await Promise.allSettled(jobs);
}

// ------------------------------------------------------------- the database

function serviceKey(): string {
  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (legacy) return legacy;
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}");
    return keys.default ?? Object.values(keys)[0] ?? "";
  } catch {
    return "";
  }
}

async function rpc(name: string, body: unknown): Promise<unknown> {
  const key = serviceKey();
  const headers: Record<string, string> = { apikey: key, "content-type": "application/json" };
  // A legacy service-role key is a JWT and goes in Authorization too; the
  // newer secret keys are not, and the gateway takes them as the apikey.
  if (key.split(".").length === 3) headers.authorization = `Bearer ${key}`;
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${name}: ${response.status} ${await response.text().catch(() => "")}`);
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

// ---------------------------------------------------------------- the door

Deno.serve(async (req: Request) => {
  if (req.method === "GET") {
    return Response.json({ ios: apnsConfigured, android: fcmConfigured });
  }
  if (req.method !== "POST") {
    return new Response(null, { status: 405 });
  }
  await req.body?.cancel();

  // A breath before claiming, so a burst — six notes pushed as a phone
  // comes back online — lands as one claim and one post per person.
  await new Promise((resolve) => setTimeout(resolve, 1200));

  let messages: Message[];
  try {
    messages = (await rpc("push_claim", {})) as Message[] ?? [];
  } catch (error) {
    console.error(`claim failed: ${error}`);
    return new Response(null, { status: 500 });
  }
  if (messages.length === 0) return new Response(null, { status: 204 });

  const dead: Graveyard = { devices: new Set(), liveStarts: new Set(), activities: new Set() };
  await Promise.allSettled(messages.map((m) => deliver(m, dead)));

  if (dead.devices.size || dead.liveStarts.size || dead.activities.size) {
    await rpc("push_forget", {
      dead_devices: [...dead.devices],
      dead_live_starts: [...dead.liveStarts],
      dead_activities: [...dead.activities],
    }).catch((error) => console.error(`forget failed: ${error}`));
  }
  console.log(`delivered ${messages.length} (${messages.map((m) => m.kind).join(", ")})`);
  return new Response(null, { status: 204 });
});
