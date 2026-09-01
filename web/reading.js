// The reading page's keyboard (§12.3). J/K or arrows move by verse; Space
// is the browser's own page-down; Esc closes the book. Nothing here is
// needed to read — the page is complete text before this runs.

(() => {
  const verses = Array.from(document.querySelectorAll("sup.v"));
  if (!verses.length) return;

  const currentIndex = () => {
    const line = window.scrollY + 90;
    let index = 0;
    for (let i = 0; i < verses.length; i += 1) {
      if (verses[i].offsetTop <= line) index = i;
    }
    return index;
  };

  const go = (index) => {
    const verse = verses[Math.max(0, Math.min(verses.length - 1, index))];
    verse.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  document.addEventListener("keydown", (event) => {
    if (event.metaKey || event.ctrlKey || event.altKey) return;
    switch (event.key) {
      case "j":
      case "ArrowDown":
        if (event.key === "j") {
          event.preventDefault();
          go(currentIndex() + 1);
        }
        break;
      case "k":
      case "ArrowUp":
        if (event.key === "k") {
          event.preventDefault();
          go(currentIndex() - 1);
        }
        break;
      case "Escape": {
        const close = document.querySelector(".passage-end .quiet");
        if (close) window.location.href = close.href;
        break;
      }
      default:
        break;
    }
  });
})();
