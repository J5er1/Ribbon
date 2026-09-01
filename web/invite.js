// The invite page (S16): a person, not a product. The token is the last
// path segment; the backend's invite_preview answers who is inviting —
// callable before any account exists, by design.

(async () => {
  const line = document.getElementById("invite-line");
  const token = window.location.pathname.split("/").filter(Boolean).pop();
  const fallback = "Someone wants to read with you.";

  const SUPABASE_URL = "https://noyccfkaotuvhhaoccck.supabase.co";
  const SUPABASE_KEY = "sb_publishable_ZokpZ0nlXW2IQfNDGnvNKQ_mPF63qGU";

  if (!token || !/^[0-9a-f-]{36}$/.test(token)) {
    line.textContent = fallback;
    return;
  }

  try {
    const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/invite_preview`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        apikey: SUPABASE_KEY,
        authorization: `Bearer ${SUPABASE_KEY}`,
      },
      body: JSON.stringify({ invite_token: token }),
    });
    const rows = await response.json();
    const preview = Array.isArray(rows) ? rows[0] : rows;
    if (!response.ok || !preview || !preview.inviter_name) {
      line.textContent = fallback;
      return;
    }
    if (preview.expired) {
      line.textContent = "This invite has expired. Ask for a new one.";
      return;
    }
    const first = String(preview.inviter_name).split(" ")[0];
    line.textContent = `${first} wants to read with you.`;
  } catch {
    line.textContent = fallback;
  }
})();
