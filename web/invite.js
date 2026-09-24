// The invite page (S16): a person, not a product — and, for somebody without
// the app, the whole of the join. "The link opens the web version of the
// same screen, and you can join and read immediately, in the browser,
// without installing anything. The app is offered afterwards, once, and
// never insisted on."
//
// So the steps are the app's: who is inviting and one control, Join; an
// emailed code (the same account the app signs into, so a person who later
// installs it finds their room already there); a name and, if they like, a
// face; then the room. The states are S16's too: expired, room full,
// already a member (straight in), and signed in as someone else — which is
// offered as a choice and never joined silently.
//
// The words are the app's own strings (Copy.swift), because a joiner who
// meets both should not meet two voices. Nothing here explains Ribbon,
// mentions the Bible first, or counts anything.

(() => {
  const SUPABASE_URL = "https://noyccfkaotuvhhaoccck.supabase.co";
  const SUPABASE_KEY = "sb_publishable_ZokpZ0nlXW2IQfNDGnvNKQ_mPF63qGU";
  const SESSION_KEY = "ribbon.session";
  const OFFERED_KEY = "ribbon.appOffered";
  const BOOKS = window.RIBBON_BOOKS || {};

  const COPY = {
    someone: "Someone wants to read with you.",
    wants: (name) => `${name} wants to read with you.`,
    joiningAs: (name) => `You'll join as ${name}.`,
    expired: "This invite has expired. Ask for a new one.",
    full: "This room is full. Ask them to start another.",
    notFound: "That invite isn't there any more. Ask for a new one.",
    codeWrong: "That code didn't work. Try again, or send a new one.",
    unreachable: "Can't reach Ribbon right now.",
    changePortrait: "Change your portrait",
    yourRoom: "Your room",
    read: (book) => `Read ${book}`,
  };

  const token = window.location.pathname.split("/").filter(Boolean).pop() || "";
  const $ = (id) => document.getElementById(id);
  const steps = Array.from(document.querySelectorAll("[data-step]"));
  const errorLine = $("error-line");

  // ------------------------------------------------------------ one step

  let current = "loading";
  const show = (name) => {
    current = name;
    say("");
    for (const step of steps) step.hidden = step.dataset.step !== name;
    const field = document.querySelector(`[data-step="${name}"] input:not([type=file])`);
    if (field) field.focus();
  };

  // Name what happened, name what didn't; never an apology, never a code.
  const say = (line) => {
    if (errorLine) errorLine.textContent = line;
  };

  const end = (line) => {
    $("ended-line").textContent = line;
    show("ended");
  };

  const firstName = (name) => String(name || "").trim().split(/\s+/)[0] || "";

  // ------------------------------------------------------------- the wire

  const call = async (path, { method = "GET", body, session, headers = {} } = {}) => {
    const response = await fetch(`${SUPABASE_URL}${path}`, {
      method,
      headers: {
        apikey: SUPABASE_KEY,
        authorization: `Bearer ${session ? session.access_token : SUPABASE_KEY}`,
        ...(body instanceof Blob ? {} : { "content-type": "application/json" }),
        ...headers,
      },
      body: body === undefined ? undefined : body instanceof Blob ? body : JSON.stringify(body),
    });
    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }
    return { ok: response.ok, status: response.status, data };
  };

  // --------------------------------------------------------- the session

  const stored = () => {
    try {
      return JSON.parse(window.localStorage.getItem(SESSION_KEY) || "null");
    } catch {
      return null;
    }
  };
  const keep = (session) => {
    try {
      window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } catch {
      // A private window: the session lives as long as the page does.
    }
  };
  const forget = () => {
    try {
      window.localStorage.removeItem(SESSION_KEY);
    } catch {
      // Nothing to forget.
    }
  };

  let session = null;

  /** The stored session, refreshed if it is about to lapse; null if dead. */
  const liveSession = async () => {
    const saved = stored();
    if (!saved || !saved.access_token) return null;
    const expiresAt = (saved.expires_at || 0) * 1000;
    if (expiresAt > Date.now() + 60_000) return saved;
    const refreshed = await call("/auth/v1/token?grant_type=refresh_token", {
      method: "POST",
      body: { refresh_token: saved.refresh_token },
    }).catch(() => null);
    if (!refreshed || !refreshed.ok) {
      if (refreshed && refreshed.status >= 400 && refreshed.status < 500) forget();
      return null;
    }
    keep(refreshed.data);
    return refreshed.data;
  };

  // ------------------------------------------------------------ the invite

  let preview = null;

  const loadPreview = async () => {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      const answer = await call("/rest/v1/rpc/invite_preview", {
        method: "POST",
        body: { invite_token: token },
      }).catch(() => null);
      if (answer && answer.ok) {
        const rows = Array.isArray(answer.data) ? answer.data : [answer.data];
        return rows[0] || "missing";
      }
      await new Promise((resolve) => setTimeout(resolve, 1200));
    }
    return null;
  };

  /** A member already: the invite row is readable only to its room. */
  const alreadyIn = async () => {
    if (!session) return false;
    const rows = await call(`/rest/v1/invites?id=eq.${token}&select=room_id`, { session }).catch(() => null);
    return Boolean(rows && rows.ok && Array.isArray(rows.data) && rows.data.length > 0);
  };

  // -------------------------------------------------------- who you are

  const whoAmI = async () => {
    const id = session && session.user && session.user.id;
    if (!id) return null;
    const rows = await call(`/rest/v1/profiles?id=eq.${id}&select=name,portrait_path`, { session }).catch(() => null);
    if (!rows || !rows.ok || !Array.isArray(rows.data)) return null;
    return rows.data[0] || null;
  };

  let portraitBlob = null;

  /** A face, made small and square before it goes anywhere. */
  const squareJPEG = (file) =>
    new Promise((resolve) => {
      const image = new Image();
      image.onload = () => {
        const side = 512;
        const canvas = document.createElement("canvas");
        canvas.width = side;
        canvas.height = side;
        const scale = Math.max(side / image.width, side / image.height);
        const width = image.width * scale;
        const height = image.height * scale;
        canvas.getContext("2d").drawImage(image, (side - width) / 2, (side - height) / 2, width, height);
        URL.revokeObjectURL(image.src);
        canvas.toBlob((blob) => resolve(blob), "image/jpeg", 0.85);
      };
      image.onerror = () => resolve(null);
      image.src = URL.createObjectURL(file);
    });

  // ------------------------------------------------------------ the join

  const enter = async () => {
    show("joining");
    const joined = await call("/rest/v1/rpc/accept_invite", {
      method: "POST",
      body: { invite_token: token },
      session,
    }).catch(() => null);
    if (!joined) {
      show("invite");
      say(COPY.unreachable);
      return;
    }
    if (!joined.ok) {
      const message = String((joined.data && joined.data.message) || "");
      if (message.includes("invite_expired")) return end(COPY.expired);
      if (message.includes("room_full")) return end(COPY.full);
      if (joined.status === 401 || message.includes("not_signed_in")) {
        forget();
        session = null;
        show("email");
        return;
      }
      show("invite");
      say(COPY.unreachable);
      return;
    }
    await room(String(joined.data));
  };

  /** The room: its name, the book it is reading, and the way into the book. */
  const room = async (roomID) => {
    const [rooms, readings, members] = await Promise.all([
      call(`/rest/v1/rooms?id=eq.${roomID}&select=name,translation`, { session }),
      call(
        `/rest/v1/readings?room_id=eq.${roomID}&finished_at=is.null&select=id,book_id,translation&order=started_at.desc&limit=1`,
        { session },
      ),
      call(`/rest/v1/memberships?room_id=eq.${roomID}&select=person_id`, { session }),
    ].map((request) => request.catch(() => ({ ok: false, data: null }))));

    const theRoom = rooms.ok && Array.isArray(rooms.data) ? rooms.data[0] : null;
    const reading = readings.ok && Array.isArray(readings.data) ? readings.data[0] : null;

    // Its own name, or its people's first names — the way the app names it.
    let title = theRoom && theRoom.name ? theRoom.name : "";
    if (!title && members.ok && Array.isArray(members.data) && members.data.length) {
      const ids = members.data.map((m) => m.person_id).join(",");
      const people = await call(`/rest/v1/profiles?id=in.(${ids})&select=name`, { session }).catch(() => null);
      const names = people && people.ok && Array.isArray(people.data)
        ? people.data.map((p) => firstName(p.name)).filter(Boolean)
        : [];
      title = names.join(" & ");
    }
    $("room-line").textContent = title || COPY.yourRoom;

    const read = $("read-link");
    if (reading && BOOKS[reading.book_id]) {
      const book = BOOKS[reading.book_id];
      $("room-book").textContent = book;
      $("room-book").hidden = false;
      // The room's own translation where the web has it; the web carries
      // the two public-domain ones.
      const wanted = String(reading.translation || (theRoom && theRoom.translation) || "bsb").toLowerCase();
      const translation = wanted === "web" ? "web" : "bsb";
      // Where the room is: the ribbon, if someone has set it down.
      let chapter = 1;
      let verse = null;
      const ribbon = await call(`/rest/v1/ribbons?reading_id=eq.${reading.id}&select=chapter,verse`, { session })
        .catch(() => null);
      if (ribbon && ribbon.ok && Array.isArray(ribbon.data) && ribbon.data[0]) {
        chapter = ribbon.data[0].chapter || 1;
        verse = ribbon.data[0].verse || null;
      }
      read.href = `/read/${translation}/${reading.book_id}/${chapter}.html${verse ? `#v${verse}` : ""}`;
      read.textContent = COPY.read(book);
    }

    // The app, offered afterwards, once, and never insisted on.
    let offered = true;
    try {
      offered = window.localStorage.getItem(OFFERED_KEY) === "1";
      window.localStorage.setItem(OFFERED_KEY, "1");
    } catch {
      offered = false;
    }
    $("app-offer").hidden = offered;
    document.title = `${$("room-line").textContent} · Ribbon`;
    show("room");
  };

  /** Signed in: a name and a face if there are none yet, then the room. */
  const afterSignIn = async () => {
    const me = await whoAmI();
    if (me && me.name) {
      await enter();
      return;
    }
    show("name");
  };

  // ------------------------------------------------------------ controls

  let email = "";

  const sendCode = async () => {
    say("");
    const answer = await call("/auth/v1/otp", {
      method: "POST",
      body: { email, create_user: true },
    }).catch(() => null);
    if (!answer || !answer.ok) {
      say(COPY.unreachable);
      return false;
    }
    return true;
  };

  const wire = () => {
    $("join-btn").addEventListener("click", async () => {
      if (session) {
        const me = await whoAmI();
        const who = (me && firstName(me.name)) || (session.user && session.user.email) || "";
        $("as-line").textContent = COPY.joiningAs(who);
        show("as");
      } else {
        show("email");
      }
    });

    $("join-as-btn").addEventListener("click", () => afterSignIn());

    // Signed in as someone else: switching is offered, never done silently.
    $("someone-else-btn").addEventListener("click", () => {
      forget();
      session = null;
      show("email");
    });

    for (const back of document.querySelectorAll("[data-back]")) {
      back.addEventListener("click", () => show("invite"));
    }

    document.querySelector('[data-step="email"]').addEventListener("submit", async (event) => {
      event.preventDefault();
      email = $("email").value.trim();
      if (!email.includes("@")) return;
      if (await sendCode()) show("code");
    });

    $("resend-btn").addEventListener("click", async () => {
      if (await sendCode()) $("code").focus();
    });

    document.querySelector('[data-step="code"]').addEventListener("submit", async (event) => {
      event.preventDefault();
      const code = $("code").value.trim();
      if (!code) return;
      const answer = await call("/auth/v1/verify", {
        method: "POST",
        body: { type: "email", email, token: code },
      }).catch(() => null);
      if (!answer) {
        say(COPY.unreachable);
        return;
      }
      if (!answer.ok || !answer.data || !answer.data.access_token) {
        say(COPY.codeWrong);
        return;
      }
      session = answer.data;
      keep(session);
      await afterSignIn();
    });

    $("portrait").addEventListener("change", async () => {
      const file = $("portrait").files && $("portrait").files[0];
      if (!file) return;
      portraitBlob = await squareJPEG(file);
      if (!portraitBlob) return;
      const preview = $("portrait-preview");
      preview.textContent = "";
      preview.style.backgroundImage = `url(${URL.createObjectURL(portraitBlob)})`;
      preview.classList.add("has-face");
      $("portrait-label").textContent = COPY.changePortrait;
    });

    $("name").addEventListener("input", () => {
      const preview = $("portrait-preview");
      if (!preview.classList.contains("has-face")) {
        preview.textContent = firstName($("name").value).charAt(0).toUpperCase();
      }
    });

    document.querySelector('[data-step="name"]').addEventListener("submit", async (event) => {
      event.preventDefault();
      const name = $("name").value.trim();
      if (!name) return;
      const id = session.user.id;
      // The face first, so the profile can name it; a face that will not
      // upload leaves the monogram, which is what skipping it gives anyway.
      let portraitPath = null;
      if (portraitBlob) {
        const uploaded = await call(`/storage/v1/object/portraits/${id}.jpg`, {
          method: "POST",
          body: portraitBlob,
          session,
          headers: { "content-type": "image/jpeg", "x-upsert": "true" },
        }).catch(() => null);
        if (uploaded && uploaded.ok) portraitPath = `${id}.jpg`;
      }
      // The person exists before the membership can: a seat names somebody.
      const saved = await call("/rest/v1/profiles?on_conflict=id", {
        method: "POST",
        body: [{ id, name, translation: "bsb", portrait_path: portraitPath }],
        session,
        headers: { prefer: "resolution=merge-duplicates,return=minimal" },
      }).catch(() => null);
      if (!saved || !saved.ok) {
        say(COPY.unreachable);
        return;
      }
      await enter();
    });
  };

  // ------------------------------------------------------------ the door

  const begin = async () => {
    wire();

    if (!/^[0-9a-f-]{36}$/i.test(token)) {
      end(COPY.notFound);
      return;
    }

    // The app, where there is one to open. A desktop has no app to hand to.
    const openApp = $("open-app-link");
    if (window.matchMedia && window.matchMedia("(pointer: coarse)").matches) {
      openApp.href = `ribbon://i/${token}`;
      openApp.hidden = false;
    }

    session = await liveSession();
    const [found, member] = await Promise.all([loadPreview(), alreadyIn()]);

    // Already a member: straight in, whatever else is true of the invite.
    if (member) {
      await enter();
      return;
    }
    if (found === "missing") {
      end(COPY.notFound);
      return;
    }
    if (found) {
      preview = found;
      if (preview.expired) return end(COPY.expired);
      if (preview.full) return end(COPY.full);
      const first = firstName(preview.inviter_name);
      if (first) {
        $("invite-line").textContent = COPY.wants(first);
        $("inviter-initial").textContent = first.charAt(0).toUpperCase();
        document.title = `${first} invited you · Ribbon`;
      }
      if (preview.room_name) {
        $("room-name").textContent = preview.room_name;
        $("room-name").hidden = false;
      }
    } else {
      // The server did not answer. The page still says the true small thing,
      // and Join tries again when it is asked to.
      $("invite-line").textContent = COPY.someone;
    }
    show("invite");
  };

  begin();
})();
