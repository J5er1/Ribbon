#!/usr/bin/env python3
"""Convert ebible.org USFX sources into Ribbon's per-book Scripture JSON.

Produces:
  ios/Ribbon/Resources/Scripture/<web|bsb>/<BOOKID>.json
  core/Sources/RibbonCore/Books.generated.swift  (canonical table + WEB word counts)
  android/core/src/main/kotlin/app/readribbon/core/Books.generated.kt  (the same
    table for Android — one pass, one set of counts, so the two apps' fire
    scales cannot drift)

The JSON preserves what a printed page keeps: paragraphs, poetic lines with
their indent levels, psalm titles, and (where the source marks them) the
words of Jesus for the red-letter setting. Footnotes and cross-references
are stripped — the reading surface is Scripture, not apparatus.

Sources (public domain):
  https://ebible.org/Scriptures/engwebp_usfx.zip   World English Bible
  https://ebible.org/Scriptures/engbsb_usfx.zip    Berean Standard Bible
"""

import json
import os
import re
import sys
import xml.etree.ElementTree as ET

# Canonical 66-book table: (usfm id, name, section key)
BOOKS = [
    ("GEN", "Genesis", "law"), ("EXO", "Exodus", "law"), ("LEV", "Leviticus", "law"),
    ("NUM", "Numbers", "law"), ("DEU", "Deuteronomy", "law"),
    ("JOS", "Joshua", "history"), ("JDG", "Judges", "history"), ("RUT", "Ruth", "history"),
    ("1SA", "1 Samuel", "history"), ("2SA", "2 Samuel", "history"),
    ("1KI", "1 Kings", "history"), ("2KI", "2 Kings", "history"),
    ("1CH", "1 Chronicles", "history"), ("2CH", "2 Chronicles", "history"),
    ("EZR", "Ezra", "history"), ("NEH", "Nehemiah", "history"), ("EST", "Esther", "history"),
    ("JOB", "Job", "poetry"), ("PSA", "Psalms", "poetry"), ("PRO", "Proverbs", "poetry"),
    ("ECC", "Ecclesiastes", "poetry"), ("SNG", "Song of Songs", "poetry"),
    ("ISA", "Isaiah", "prophets"), ("JER", "Jeremiah", "prophets"),
    ("LAM", "Lamentations", "prophets"), ("EZK", "Ezekiel", "prophets"),
    ("DAN", "Daniel", "prophets"), ("HOS", "Hosea", "prophets"), ("JOL", "Joel", "prophets"),
    ("AMO", "Amos", "prophets"), ("OBA", "Obadiah", "prophets"), ("JON", "Jonah", "prophets"),
    ("MIC", "Micah", "prophets"), ("NAM", "Nahum", "prophets"),
    ("HAB", "Habakkuk", "prophets"), ("ZEP", "Zephaniah", "prophets"),
    ("HAG", "Haggai", "prophets"), ("ZEC", "Zechariah", "prophets"),
    ("MAL", "Malachi", "prophets"),
    ("MAT", "Matthew", "gospels"), ("MRK", "Mark", "gospels"), ("LUK", "Luke", "gospels"),
    ("JHN", "John", "gospels"), ("ACT", "Acts", "gospels"),
    ("ROM", "Romans", "letters"), ("1CO", "1 Corinthians", "letters"),
    ("2CO", "2 Corinthians", "letters"), ("GAL", "Galatians", "letters"),
    ("EPH", "Ephesians", "letters"), ("PHP", "Philippians", "letters"),
    ("COL", "Colossians", "letters"), ("1TH", "1 Thessalonians", "letters"),
    ("2TH", "2 Thessalonians", "letters"), ("1TI", "1 Timothy", "letters"),
    ("2TI", "2 Timothy", "letters"), ("TIT", "Titus", "letters"),
    ("PHM", "Philemon", "letters"), ("HEB", "Hebrews", "letters"),
    ("JAS", "James", "letters"), ("1PE", "1 Peter", "letters"),
    ("2PE", "2 Peter", "letters"), ("1JN", "1 John", "letters"),
    ("2JN", "2 John", "letters"), ("3JN", "3 John", "letters"),
    ("JUD", "Jude", "letters"), ("REV", "Revelation", "letters"),
]
BOOK_IDS = {b[0] for b in BOOKS}

# Block style mapping: USFX/USFM paragraph styles -> Ribbon block styles.
# Anything not listed (and not carrying verse text) is front matter,
# headings, or apparatus, and is skipped.
STYLE_MAP = {
    "p": "p", "pc": "p", "pi1": "p", "pi2": "p", "po": "p",
    "m": "m", "mi": "m", "nb": "m", "pmo": "m", "pm": "m", "pmc": "m", "pmr": "m",
    "q1": "q1", "li1": "q1",
    "q2": "q2", "q3": "q2", "q4": "q2", "qr": "q2", "li2": "q2", "li3": "q2",
    "qm1": "q1", "qm2": "q2", "qm3": "q2",
    "d": "d", "qa": "d", "sp": "d", "qd": "d", "cls": "m",
    "b": "b",
}

# Inline elements whose entire subtree is apparatus, not Scripture.
STRIP = {"f", "fe", "x", "note", "fig", "rq", "sts", "fm"}


def block_elements(book_el):
    """Yield (style, element) for verse-bearing block elements in order."""
    for el in book_el:
        tag = el.tag
        if tag in ("p", "q", "d", "b", "li", "mi"):
            style = el.get("style") or el.get("sfm") or tag
            yield style, el
        # <c> and <v> live inside/between blocks; chapters handled by caller.


def collect_spans(el, spans, state, wj=False):
    """Walk a block element, appending {v,t,w} spans. state carries verse."""

    def push(text, wj_flag):
        if text is None:
            return
        text = re.sub(r"\s+", " ", text)
        if not text:
            return
        # A pending verse marker always starts a fresh span — a verse that
        # begins mid-paragraph must keep its number, since notes and
        # highlights anchor to verse addresses.
        if (state["pending_verse"] is None and spans
                and spans[-1].get("_open") and spans[-1].get("w", False) == wj_flag):
            spans[-1]["t"] += text
        else:
            spans.append({"v": state["pending_verse"], "t": text, "w": wj_flag, "_open": True})
            state["pending_verse"] = None

    if el.text:
        push(el.text, wj)
    for child in el:
        tag = child.tag
        if tag == "v":
            vid = child.get("id")
            try:
                state["pending_verse"] = int(re.sub(r"[^0-9].*$", "", vid))
            except (TypeError, ValueError):
                state["pending_verse"] = None
        elif tag in STRIP:
            pass  # drop the whole subtree
        elif tag == "ve":
            pass
        elif tag == "wj":
            collect_spans(child, spans, state, wj=True)
        else:
            # <w>, <add>, <nd>, <qt>, <k>, <ord>, <sc>, <it>, <bd>, <tl>, <ref> …
            collect_spans(child, spans, state, wj=wj)
        if child.tail:
            push(child.tail, wj)


def convert_book(book_el):
    chapters = []
    blocks = []
    chapter_n = None
    state = {"pending_verse": None}

    def flush():
        nonlocal chapters, blocks, chapter_n
        if chapter_n is None or not blocks:
            blocks = []
            return
        cleaned = []
        for b in blocks:
            xs = []
            for i, s in enumerate(b["x"]):
                t = re.sub(r"\s+", " ", s["t"])
                if i == 0:
                    t = t.lstrip()
                if i == len(b["x"]) - 1:
                    t = t.rstrip()
                if not t:
                    continue
                out = {"t": t}
                if s["v"] is not None:
                    out["v"] = s["v"]
                if s.get("w"):
                    out["w"] = True
                xs.append(out)
            if xs:
                cleaned.append({"s": b["s"], "x": xs})
            elif b["s"] == "b" and cleaned and cleaned[-1]["s"] != "b":
                cleaned.append({"s": "b", "x": []})
        while cleaned and cleaned[-1]["s"] == "b":
            cleaned.pop()
        if cleaned:
            chapters.append({"n": chapter_n, "blocks": cleaned})
        blocks = []

    for el in book_el:
        if el.tag == "c":
            flush()
            try:
                chapter_n = int(el.get("id"))
            except (TypeError, ValueError):
                chapter_n = None
            continue
        if el.tag in ("p", "q", "d", "li", "mi", "b"):
            style = el.get("style") or el.get("sfm") or el.tag
            mapped = STYLE_MAP.get(style)
            if mapped is None:
                continue
            if mapped == "b":
                blocks.append({"s": "b", "x": []})
                continue
            spans = []
            collect_spans(el, spans, state)
            for s in spans:
                s.pop("_open", None)
            if spans:
                blocks.append({"s": mapped, "x": spans})
    flush()
    return chapters


def word_count(chapters):
    n = 0
    for ch in chapters:
        for b in ch["blocks"]:
            for s in b["x"]:
                n += len(s["t"].split())
    return n


def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    src_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    out_scripture = os.path.join(root_dir, "ios", "Ribbon", "Resources", "Scripture")
    sources = {
        "web": os.path.join(src_dir, "engwebp_usfx", "engwebp_usfx.xml"),
        "bsb": os.path.join(src_dir, "engbsb_usfx", "engbsb_usfx.xml"),
    }
    names = {b[0]: b[1] for b in BOOKS}
    stats = {}

    for trans, path in sources.items():
        tree = ET.parse(path)
        out_dir = os.path.join(out_scripture, trans)
        os.makedirs(out_dir, exist_ok=True)
        seen = set()
        for book_el in tree.getroot().iter("book"):
            bid = book_el.get("id")
            if bid not in BOOK_IDS:
                continue
            chapters = convert_book(book_el)
            if not chapters:
                print(f"warning: {trans}/{bid} produced no chapters", file=sys.stderr)
                continue
            seen.add(bid)
            doc = {"id": bid, "name": names[bid], "chapters": chapters}
            with open(os.path.join(out_dir, f"{bid}.json"), "w") as f:
                json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
            if trans == "web":
                stats[bid] = (len(chapters), word_count(chapters))
        missing = BOOK_IDS - seen
        if missing:
            print(f"error: {trans} missing books: {sorted(missing)}", file=sys.stderr)
            sys.exit(1)
        print(f"{trans}: wrote {len(seen)} books")

    # The same header stands over both generated tables.
    header = [
        "// Generated by tools/usfx_to_json.py from the World English Bible —",
        "// do not edit by hand. Word counts exist only to size each book's",
        "// fire; they are never shown to a person.",
        "",
    ]

    # Generate the Swift book table from WEB counts.
    lines = header + ["let generatedBooks: [BibleBook] = ["]
    for bid, name, section in BOOKS:
        ch, wc = stats[bid]
        lines.append(
            f'    BibleBook(id: "{bid}", name: "{name}", section: .{section}, '
            f"chapterCount: {ch}, wordCount: {wc}),"
        )
    lines.append("]")
    swift_path = os.path.join(root_dir, "core", "Sources", "RibbonCore", "Books.generated.swift")
    with open(swift_path, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {swift_path}")

    # The same table for Android, emitted from the same stats in the same
    # pass. Kept in lockstep with the Swift emitter above — neither app's
    # word counts are hand-copied, so the fire scale cannot drift between
    # them.
    lines = header + [
        "package app.readribbon.core",
        "",
        "internal val generatedBooks: List<BibleBook> = listOf(",
    ]
    for bid, name, section in BOOKS:
        ch, wc = stats[bid]
        lines.append(
            f'    BibleBook(id = "{bid}", name = "{name}", section = BibleSection.{section}, '
            f"chapterCount = {ch}, wordCount = {wc}),"
        )
    lines.append(")")
    kotlin_path = os.path.join(root_dir, "android", "core", "src", "main", "kotlin",
                               "app", "readribbon", "core", "Books.generated.kt")
    with open(kotlin_path, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {kotlin_path}")

    # Threshold sanity: print the smallest thirty books so the fire-scale
    # boundaries can be checked against the build book's examples.
    for bid, (ch, wc) in sorted(stats.items(), key=lambda kv: kv[1][1])[:30]:
        print(f"{wc:>7}  {names[bid]}")


if __name__ == "__main__":
    main()
