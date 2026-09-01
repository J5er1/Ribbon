// bible-proxy — the one place licensed Scripture enters Ribbon.
//
// Holds the API.Bible key as a function secret (API_BIBLE_KEY), so the key
// never ships in the app and every request from every device funnels
// through one enforcement point for the license's terms. Serves chapters
// only — GET ?bible=<api.bible id>&chapter=<USFM>.<n> — and answers 503
// until the key is configured, which the app reads as "this translation
// isn't offered yet."
//
// Set the secret once the API.Bible account and the NKJV license exist:
//   supabase secrets set API_BIBLE_KEY=... --project-ref noyccfkaotuvhhaoccck

Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return json({ error: "method_not_allowed" }, 405);
  }
  const url = new URL(req.url);
  const bible = url.searchParams.get("bible") ?? "";
  const chapter = url.searchParams.get("chapter") ?? "";

  // API.Bible ids are hex-ish tokens with optional suffix; chapters are
  // USFM book code dot chapter number ("MRK.4").
  if (!/^[a-zA-Z0-9-]{4,64}$/.test(bible) || !/^[A-Z0-9]{3}\.\d{1,3}$/.test(chapter)) {
    return json({ error: "bad_request" }, 400);
  }

  const key = Deno.env.get("API_BIBLE_KEY");
  if (!key) {
    return json({ error: "not_configured" }, 503);
  }

  const upstream = new URL(
    `https://api.scripture.api.bible/v1/bibles/${bible}/chapters/${chapter}`,
  );
  upstream.searchParams.set("content-type", "json");
  upstream.searchParams.set("include-notes", "false");
  upstream.searchParams.set("include-titles", "true");
  upstream.searchParams.set("include-chapter-numbers", "false");
  upstream.searchParams.set("include-verse-numbers", "true");
  upstream.searchParams.set("include-verse-spans", "false");

  const response = await fetch(upstream, { headers: { "api-key": key } });
  const body = await response.text();
  return new Response(body, {
    status: response.status,
    headers: {
      "content-type": "application/json",
      // A chapter of a fixed edition doesn't change; let the CDN help.
      "cache-control": "public, max-age=86400",
    },
  });
});

function json(payload: unknown, status: number): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "content-type": "application/json" },
  });
}
