// The invite page (S16): a person, not a product. The token is the last
// path segment; the backend's invite_preview answers who is inviting —
// callable before any account exists, by design.

(async () => {
  const line = document.getElementById("invite-line");
  const body = document.getElementById("invite-body");
  const openBtn = document.getElementById("open-app-btn");
  const copyBtn = document.getElementById("copy-token-btn");
  const copiedToast = document.getElementById("copied-toast");

  const token = window.location.pathname.split("/").filter(Boolean).pop();
  const fallback = "Someone wants to read with you.";

  const SUPABASE_URL = "https://noyccfkaotuvhhaoccck.supabase.co";
  const SUPABASE_KEY = "sb_publishable_ZokpZ0nlXW2IQfNDGnvNKQ_mPF63qGU";

  if (!token || !/^[0-9a-f-]{36}$/i.test(token)) {
    if (line) line.textContent = fallback;
    if (openBtn) openBtn.style.display = "none";
    return;
  }

  const appUrl = `ribbon://i/${token}`;
  if (openBtn) {
    openBtn.href = appUrl;
  }

  if (copyBtn) {
    copyBtn.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(window.location.href);
        if (copiedToast) {
          copiedToast.style.display = "inline";
          setTimeout(() => {
            copiedToast.style.display = "none";
          }, 2000);
        }
      } catch {
        const input = document.createElement("input");
        input.value = window.location.href;
        document.body.appendChild(input);
        input.select();
        document.execCommand("copy");
        document.body.removeChild(input);
        if (copiedToast) {
          copiedToast.style.display = "inline";
          setTimeout(() => {
            copiedToast.style.display = "none";
          }, 2000);
        }
      }
    });
  }

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  let preview = null;
  let attempts = 0;
  while (attempts < 3) {
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
      if (response.ok) {
        const rows = await response.json();
        const found = Array.isArray(rows) ? rows[0] : rows;
        if (found && (found.inviter_name || found.expired !== undefined)) {
          preview = found;
          break;
        }
      }
    } catch {
      // Network hiccup; retry
    }
    attempts += 1;
    if (attempts < 3) {
      await sleep(1200);
    }
  }

  if (!preview) {
    if (line) line.textContent = fallback;
    return;
  }
  if (preview.expired) {
    if (line) line.textContent = "This invite has expired.";
    if (body) body.textContent = "Ask for a new invite link to start reading together.";
    if (openBtn) openBtn.style.display = "none";
    return;
  }
  const first = String(preview.inviter_name || "Someone").split(" ")[0];
  if (line) line.textContent = `${first} wants to read with you.`;
  document.title = `${first} invited you · Ribbon`;
})();
