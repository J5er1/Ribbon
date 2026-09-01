// The Ribbon web build (§12.3) — a static generator, no framework.
//
// The web is the one platform with no native language to honour, so it
// goes furthest toward the printed book: every chapter of both bundled
// translations is pre-rendered to plain HTML at build time — text-first,
// readable before any script runs. Presence and accounts arrive later
// over the same pages; nothing here waits for them.
//
//   node build.mjs        → dist/
//
// Vercel runs this via vercel.json (buildCommand + outputDirectory).

import { cpSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..");
const scripture = join(repo, "ios", "Ribbon", "Resources", "Scripture");
const dist = join(here, "dist");

const TRANSLATIONS = [
  { id: "bsb", name: "Berean Standard" },
  { id: "web", name: "World English" },
];

// Canonical order and sections, mirrored from RibbonCore's Bible table.
const SECTIONS = [
  ["The Law", ["GEN", "EXO", "LEV", "NUM", "DEU"]],
  ["History", ["JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH", "2CH", "EZR", "NEH", "EST"]],
  ["Poetry & Wisdom", ["JOB", "PSA", "PRO", "ECC", "SNG"]],
  ["The Prophets", ["ISA", "JER", "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL"]],
  ["Gospels & Acts", ["MAT", "MRK", "LUK", "JHN", "ACT"]],
  ["Letters & Revelation", ["ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL", "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN", "JUD", "REV"]],
];

const GOOD_PLACES = ["MRK", "RUT", "PHP", "JHN", "PSA"];

const esc = (s) =>
  s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

const page = ({ title, body, head = "", bodyClass = "" }) => `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="theme-color" content="#0B0B0A">
<title>${esc(title)}</title>
<link rel="icon" href="/icon.png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:ital,opsz,wght@0,7..72,400;0,7..72,500;1,7..72,400&family=Alegreya+Sans:wght@400;500&family=Alegreya+Sans+SC:wght@400;500&display=swap">
<link rel="stylesheet" href="/ribbon.css">
${head}
</head>
<body class="${bodyClass}">
${body}
</body>
</html>
`;

// ---------------------------------------------------------------- load text

const books = new Map(); // id -> {id, name, chapters: {bsb: [...], web: [...]}}
for (const t of TRANSLATIONS) {
  for (const file of readdirSync(join(scripture, t.id))) {
    const doc = JSON.parse(readFileSync(join(scripture, t.id, file), "utf8"));
    if (!books.has(doc.id)) {
      books.set(doc.id, { id: doc.id, name: doc.name, chapters: {} });
    }
    books.get(doc.id).chapters[t.id] = doc.chapters;
  }
}

// ------------------------------------------------------------------ output

mkdirSync(dist, { recursive: true });
cpSync(join(repo, "mark", "ribbon-wordmark-cesso.png"), join(dist, "wordmark.png"));
cpSync(join(repo, "mark", "ribbon-icon-cesso-2048.png"), join(dist, "icon.png"));
cpSync(join(repo, "ios", "Ribbon", "Resources", "PaperGrain.png"), join(dist, "grain.png"));
cpSync(join(here, "ribbon.css"), join(dist, "ribbon.css"));
cpSync(join(here, "reading.js"), join(dist, "reading.js"));

// Home (S26's smallest honest version until the full site: the wordmark,
// the line, and the book itself).
writeFileSync(
  join(dist, "index.html"),
  page({
    title: "Ribbon",
    bodyClass: "home",
    body: `
<main class="home-main">
  <img class="wordmark" src="/wordmark.png" alt="Ribbon.">
  <p class="tagline">Read it together.</p>
  <p><a class="way-in" href="/read/">Open the book</a></p>
</main>
`,
  }),
);

// The chooser.
const chooserSections = SECTIONS.map(
  ([label, ids]) => `
<section>
  <h2 class="sc">${esc(label)}</h2>
  <ul class="books">
    ${ids
      .map((id) => {
        const book = books.get(id);
        return `<li><a href="/read/bsb/${id}/1.html">${esc(book.name)}</a></li>`;
      })
      .join("\n    ")}
  </ul>
</section>`,
).join("\n");

writeFileSync(
  join(dist, "read.html"),
  page({
    title: "Read · Ribbon",
    bodyClass: "chooser",
    body: `
<main class="chooser-main">
  <p class="sc crumb"><a href="/">Ribbon</a></p>
  <section>
    <h2 class="sc">Good places to start together</h2>
    <ul class="books starters">
      ${GOOD_PLACES.map((id) => `<li><a href="/read/bsb/${id}/1.html">${esc(books.get(id).name)}</a></li>`).join("\n      ")}
    </ul>
  </section>
  ${chooserSections}
</main>
`,
  }),
);
mkdirSync(join(dist, "read"), { recursive: true });
writeFileSync(join(dist, "read", "index.html"), readFileSync(join(dist, "read.html")));

// Chapter pages.
const renderBlock = (block) => {
  if (block.s === "b") return `<div class="stanza"></div>`;
  const spans = block.x
    .map((span) => {
      const verse = span.v ? `<sup class="v" id="v${span.v}">${span.v}</sup>` : "";
      const cls = span.w ? ` class="wj"` : "";
      return `${verse}<span${cls} data-v="${span.v ?? ""}">${esc(span.t)}</span>`;
    })
    .join("");
  return `<p class="${block.s}">${spans}</p>`;
};

let pageCount = 0;
for (const t of TRANSLATIONS) {
  const other = TRANSLATIONS.find((o) => o.id !== t.id);
  for (const book of books.values()) {
    const chapters = book.chapters[t.id] ?? [];
    // The book is "Psalms"; a single psalm is headed "Psalm 23".
    const headName = book.id === "PSA" ? "Psalm" : book.name;
    const dir = join(dist, "read", t.id, book.id);
    mkdirSync(dir, { recursive: true });

    // A book's chapter index — addresses, not progress.
    writeFileSync(
      join(dir, "index.html"),
      page({
        title: `${book.name} · Ribbon`,
        bodyClass: "chooser",
        body: `
<main class="chooser-main">
  <p class="sc crumb"><a href="/read/">All books</a></p>
  <h1 class="display">${esc(book.name)}</h1>
  <ul class="chapters">
    ${chapters.map((c) => `<li><a href="/read/${t.id}/${book.id}/${c.n}.html">${c.n}</a></li>`).join("\n    ")}
  </ul>
</main>
`,
      }),
    );

    for (const chapter of chapters) {
      const prev = chapter.n > 1 ? `${chapter.n - 1}.html` : null;
      const next = chapter.n < chapters.length ? `${chapter.n + 1}.html` : null;
      const body = `
<header class="running-head sc">${esc(headName)} ${chapter.n}</header>
<main class="measure" data-book="${book.id}" data-chapter="${chapter.n}">
${chapter.blocks.map(renderBlock).join("\n")}
<nav class="passage-end">
  ${next ? `<a class="continue" href="${next}">${esc(headName)} ${chapter.n + 1}</a>` : `<a class="continue" href="/read/">The shelf of books</a>`}
  <a class="sc quiet" href="/read/">Close the book</a>
</nav>
</main>
<footer class="page-foot sc">
  ${prev ? `<a href="${prev}">← ${chapter.n - 1}</a>` : `<span></span>`}
  <a href="/read/${t.id}/${book.id}/">${esc(book.name)}</a>
  <a href="/read/${other.id}/${book.id}/${chapter.n}.html" title="${esc(other.name)}">${other.id === "bsb" ? "Berean" : "World English"}</a>
  ${next ? `<a href="${next}">${chapter.n + 1} →</a>` : `<span></span>`}
</footer>
<script src="/reading.js" defer></script>
`;
      writeFileSync(
        join(dir, `${chapter.n}.html`),
        page({ title: `${headName} ${chapter.n} · Ribbon`, body, bodyClass: "reading" }),
      );
      pageCount += 1;
    }
  }
}

// The invite page (S16) — one static shell; the token rides the URL and
// the page asks the backend who is inviting. This is the surface Law 3
// governs hardest: a person, not a product.
writeFileSync(
  join(dist, "invite.html"),
  page({
    title: "Ribbon",
    bodyClass: "invite",
    head: `<script defer src="/invite.js"></script>`,
    body: `
<main class="invite-main">
  <p id="invite-line" class="invite-line">&nbsp;</p>
  <p class="sc invite-sub">Ribbon · read it together</p>
  <p><a id="invite-action" class="way-in" href="/read/bsb/MRK/1.html">Open the book</a></p>
  <p class="quiet-line">Joining the room from the browser is on its way. Reading works now.</p>
</main>
`,
  }),
);
cpSync(join(here, "invite.js"), join(dist, "invite.js"));

console.log(`built ${pageCount} chapter pages into dist/`);
