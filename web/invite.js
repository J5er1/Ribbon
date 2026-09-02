// The invite page (S16): a person, not a product. The token is the last
// path segment; the backend's invite_preview answers who is inviting —
// callable before any account exists, by design.
//
// With the app installed, the universal link never reaches this page. When
// it does reach it (no app, or the link opened somewhere the association
// didn't fire), the page offers the book to read here and now, and the app
// once, quietly, never insisting (§15 — an invite that demands an install
// before showing anything is an invite a hesitant partner declines).

(async () => {
  const line = document.getElementById("invite-line");
  const sub = document.getElementById("invite-sub");
  const action = document.getElementById("invite-action");
  const appLink = document.getElementById("invite-app");
  const token = window.location.pathname.split("/").filter(Boolean).pop();

  const SUPABASE_URL = "https://noyccfkaotuvhhaoccck.supabase.co";
  const SUPABASE_KEY = "sb_publishable_ZokpZ0nlXW2IQfNDGnvNKQ_mPF63qGU";

  const say = (text) => { line.textContent = text; };
  const hide = (el) => { if (el) el.hidden = true; };

  if (!token || !/^[0-9a-f-]{36}$/.test(token)) {
    // Not an invite at all: say so (S25), and leave the book open.
    say("This invite has expired. Ask for a new one.");
    hide(appLink);
    return;
  }

  // The app, if it's here: the plain scheme opens it straight into the
  // join (S16). Offered once, beneath the book, never as a gate.
  if (appLink) {
    appLink.href = `ribbon://i/${token}`;
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
    if (!response.ok) {
      say("Can't reach Ribbon right now.");
      return;
    }
    if (!preview || !preview.inviter_name) {
      // No such invite: the same true line as an expired one.
      say("This invite has expired. Ask for a new one.");
      hide(appLink);
      return;
    }
    if (preview.expired) {
      say("This invite has expired. Ask for a new one.");
      hide(appLink);
      return;
    }
    if (preview.full) {
      say("This room is full. Ask them to start another.");
      hide(appLink);
      return;
    }
    const first = String(preview.inviter_name).split(" ")[0];
    say(`${first} wants to read with you.`);
    if (preview.room_name && sub) {
      sub.textContent = preview.room_name;
    }
  } catch {
    say("Can't reach Ribbon right now.");
  }
})();
