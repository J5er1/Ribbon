#!/usr/bin/env python3
"""Build Ribbon's original-language layer: the Hebrew, Aramaic and Greek
words of every verse, and the links from the bundled English to them.

Produces (all under ios/Ribbon/Resources/Scripture/, siblings of bsb/ web/):
  original/<BOOKID>.json   each verse's original words, in original order:
                           [text, translit, strongs, parse] (+ "a" for Aramaic)
  original/strongs.json    Strong's own lemma, transliteration and definition
                           for every Strong's number the words use
  original/parsing.json    every short parsing code, spelled out
  align/bsb/<BOOKID>.json  links from the bundled BSB text to the words
  align/web/<BOOKID>.json  links from the bundled WEB text to the words

A link is [start, end, [word indices]]: a half-open range of UTF-16 code
units in a verse's own text (the concatenation of its spans, skipping d and
b blocks, exactly as the apps build it) and the indices of the original
words in that verse's list that the range renders. A mark keeps the indices,
so it follows its words into whichever version a person reads. The source
key ("bsbt-" + 8 hex of the word numbering) and each alignment's basis (12
hex of the English file it was measured against) let the apps tell when a
stored index or a bundled offset no longer means what it meant.

How the links are made:
  BSB  The tables are the BSB itself, chunk by chunk, so the bundled text is
       matched to the tables' English with difflib over tokens. "-" or blank
       is a word not rendered on its own; "vvv" joins the next chunk;
       ". . ." joins the previous one; []{}() are stripped.
  WEB  The WEB is aligned to the tables' BSB English (a weighted LCS over
       stems, then local repairs), inheriting each chunk's words; content
       words still unlinked take eBible's Strong's tag, but only when the
       tag's number is among that verse's own original words. Measured on a
       hand-checked sample: about 91% precise at 97% coverage. Verses whose
       WEB English lies partly in another verse of the tables (ROM 14:24-26,
       REV 13:1, PHP 1:16-17, ...: CROSS_VERSE) get no links, so marks there
       cover the whole verse.
  Psalm titles: the tables fold a psalm's superscription into verse 1. Its
  words are the rows before the verse-1 marker (the "reftext" span) in a
  psalm whose verse 1 opens a "pshdg" paragraph; they go in a verse 0 entry.
  Ribbon sets titles apart from the verses, so verse 0 never links.

Sources (all public domain; downloaded, never committed):
  https://bereanbible.com/bsb_tables.tsv
      sha256 09bbee6f9fe4fa22b5df28e8a9ffa99bf9c33435f4eb8c47c2dc221d855d35cb
  https://ebible.org/Scriptures/engwebp_usfx.zip  (for its Strong's tags)
      engwebp_usfx.xml sha256 7adf077836c2a5f5a99be502af00dd175baa540c23efdd8d9b544db6a08f7a15
  https://raw.githubusercontent.com/openscriptures/strongs/master/hebrew/StrongHebrewG.xml
      sha256 1f9659ea208f4c498843a0280dacb1448627c33ca77712642d8705793ab66061
  https://raw.githubusercontent.com/openscriptures/strongs/master/greek/StrongsGreekDictionaryXML_1.4/strongsgreek.xml
      sha256 df928f01b37632f8af9f16289ce58d10b958014cb5dbd1e1ea715a8d311a0625
  and the committed Scripture/{bsb,web}/<BOOKID>.json, so offsets are
  measured against exactly what the apps render.
  Only Strong's own words are used: the Hebrew note type="explanation" (not
  the <list> senses, which are Online Bible text, and not the TWOT numbers)
  and the Greek strongs_def (not kjv_def). Where the derivation (Greek
  strongs_derivation, Hebrew note type="exegesis") leaves a '(' open that
  the definition closes, the two are one sentence and are kept together.
  The tables' '¦' (a word the editions divide differently) is dropped, or
  made a space between two accented words.

Run (from anywhere; SRC holds the downloads):
  python3 tools/original_to_json.py SRC
      SRC/bsb_tables.tsv, SRC/engwebp_usfx/engwebp_usfx.xml,
      SRC/StrongHebrewG.xml, SRC/strongsgreek.xml
It checks itself as it goes and fails loudly: every link inside its verse's
own text, sorted, not overlapping, every index in range; the own text built
two ways agreeing for every bundled verse; every Strong's number defined;
every WEB verse that reads like its neighbour in the tables (swapped_verses)
listed in CROSS_VERSE; and a second run in a fresh process (different hash seed) producing the same
bytes. Then it prints coverage. Stdlib only, Python 3.11.
"""

import argparse
import collections
import csv
import difflib
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unicodedata
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pivot_align as pa  # noqa: E402  (one tokeniser and one stemmer in the repo)
from usfx_to_json import BOOKS, STRIP  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTURE = os.path.join(ROOT, "ios", "Ribbon", "Resources", "Scripture")
TSV_SHA256 = "09bbee6f9fe4fa22b5df28e8a9ffa99bf9c33435f4eb8c47c2dc221d855d35cb"

# Verses whose English lies partly in another verse of the tables' numbering
# (WEB has the Romans doxology at 14:24-26, the tables at 16:25-27; WEB's
# REV 13:1 opens with what the tables end 12:17 with, ACT 9:29 with what they
# end 9:28 with; WEB's 1KI 18:33 and ACT 3:19 end with what the tables begin
# the next verse with; WEB has PHP 1:16 and 1:17 in the other order). Links
# across verses are not made in v1: these get no entry, so a mark there
# covers the verse. swapped_verses() finds the PHP 1:16-17 kind and fails
# the run if one is missing here.
CROSS_VERSE = {"web": {("ROM", 14, 24), ("ROM", 14, 25), ("ROM", 14, 26), ("REV", 13, 1),
                       ("PHP", 1, 16), ("PHP", 1, 17), ("ACT", 9, 29), ("ACT", 3, 19),
                       ("1KI", 18, 33)}}

BOOK_ORDER = [b[0] for b in BOOKS]
NT = set(BOOK_ORDER[39:])
NAME_TO_ID = {name: bid for bid, name, _ in BOOKS}
NAME_TO_ID.update({"Psalm": "PSA", "Song of Solomon": "SNG"})

# TSV columns (0-based), from the header row.
HEB_SORT, GRK_SORT, LANG, ORIG, TRANSLIT, PARSE, PARSE_LONG = 0, 1, 4, 5, 7, 8, 9
STR_HEB, STR_GRK, VERSE_ID, PAR, BEGQ, ENGLISH = 10, 11, 12, 15, 17, 18


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def dumps(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


# ---------------------------------------------------------------------------
# Cleaning the original text.

HEBREW_DROP = re.compile("[֑-ֽ֯׀׃ׄ-׆‌‍]")


def clean_hebrew(s):
    """Strip cantillation, meteg, paseq, sof pasuq, the upper/lower dots and
    nun hafukha, ZWJ/ZWNJ, and a trailing paragraph marker (" פ" / " ס").
    Vowel points, dagesh, shin/sin dots, rafe and maqaf stay."""
    s = HEBREW_DROP.sub("", s).strip()
    s = re.sub(r"\s+[פס]$", "", s)
    return s.strip()


GREEK_ACCENT = re.compile("[\u0300\u0301\u0342]")


def split_reading(greek):
    """What the tables' '¦' becomes. They write a word the editions divide
    differently as e.g. ὅ¦τι, Μή¦Ποτε, Ἁρ¦μαγεδών: the halves join. Two
    words that each keep an accent (κάτω¦κύψας, ἀγαθὸν¦ποιῆσαι) part with
    a space instead."""
    halves = unicodedata.normalize("NFD", greek).split("¦")
    return " " if len(halves) > 1 and all(GREEK_ACCENT.search(h) for h in halves) else ""


def unsplit(s, sep):
    """s with each '¦' made sep; a joined word keeps only its first capital
    (Μή¦Ποτε -> Μήποτε, HO¦ti -> Hoti), as the editions print it."""
    if "¦" not in s:
        return s
    if sep:
        return s.replace("¦", sep)
    s = s.replace("¦", "")
    return s[:1] + s[1:].lower()


def clean_greek(s):
    s = unicodedata.normalize("NFC", s).replace("‿", "").strip()
    return unsplit(s, split_reading(s))


def clean_translit(s):
    """Decomposed, with U+0332 made U+0331: Literata has the combining
    macron below but not precomposed ḇ or ḵ. Deliberately left in NFD."""
    return unicodedata.normalize("NFD", s.strip()).replace("̲", "̱")


def strongs_key(prefix, raw):
    raw = raw.strip()
    if not raw:
        return ""
    if not raw.isdigit():
        raise SystemExit(f"error: unexpected Strong's number {raw!r}")
    return f"{prefix}{int(raw)}"


# ---------------------------------------------------------------------------
# The tables.

class Row:
    __slots__ = ("sort", "lang", "orig", "translit", "parse", "parse_long",
                 "strongs", "par", "begq", "english")

    def __init__(self, x):
        self.lang = x[LANG]
        self.orig = x[ORIG]
        greek = self.lang == "Greek"
        self.sort = float(x[GRK_SORT] if greek else x[HEB_SORT]) if self.orig else None
        self.translit = x[TRANSLIT]
        self.parse = x[PARSE].strip()
        self.parse_long = x[PARSE_LONG].strip()
        self.strongs = strongs_key("G", x[STR_GRK]) if greek else strongs_key("H", x[STR_HEB])
        self.par = x[PAR]
        self.begq = x[BEGQ]
        self.english = x[ENGLISH]


def read_tables(path):
    """{(book, chapter, verse): [Row]} in BSB English order, padding dropped."""
    verses = {}
    cur = None
    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE)
        header = next(reader)
        if len(header) != 23 or header[VERSE_ID] != "VerseId":
            raise SystemExit("error: the tables' columns have changed")
        for x in reader:
            if x[VERSE_ID]:
                name, cv = x[VERSE_ID].rsplit(" ", 1)
                c, v = cv.split(":")
                cur = (NAME_TO_ID[name], int(c), int(v))
                if cur in verses:
                    raise SystemExit(f"error: verse {cur} appears twice")
                verses[cur] = []
            if not x[ORIG] and not x[ENGLISH].strip():
                continue  # padding (and Num 7:59's empty "z" row)
            verses[cur].append(Row(x))
    return verses


def title_split(key, rows):
    """(title rows, verse rows). A psalm's superscription is every row
    before the verse-1 marker, when verse 1 opens a psalm-heading paragraph."""
    bk, c, v = key
    if bk != "PSA" or v != 1 or not any("pshdg" in r.par for r in rows):
        return [], rows
    marks = [i for i, r in enumerate(rows) if "reftext" in r.begq]
    if not marks:
        return [], rows
    return rows[:marks[0]], rows[marks[0]:]


def word_tuple(r):
    if r.lang == "Greek":
        text = clean_greek(r.orig)
        translit = unsplit(r.translit, split_reading(r.orig))
    else:
        text = clean_hebrew(r.orig)
        translit = r.translit
    t = [text, clean_translit(translit), r.strongs, r.parse]
    if r.lang == "Aramaic":
        t.append("a")
    return t


def original_order(rows):
    return sorted((r for r in rows if r.orig), key=lambda r: r.sort)


# ---------------------------------------------------------------------------
# English chunks -> groups of word indices (align2's rules).

def clean_chunk(e):
    e = re.sub(r"<[^>]+>", "", e)
    e = re.sub(r"\bvvv\b", "", e)
    return re.sub(r"[\[\]{}()]", "", e).strip()


def groups(rows, index_of):
    """[(english, [word indices])] in BSB order. "-" or blank: not rendered;
    "vvv": joins the next chunk; ". . .": joins the previous one."""
    out, pending = [], []
    for r in rows:
        e = r.english.strip()
        if not r.orig:
            if e:
                out.append([clean_chunk(e), []])  # Neh 7:68, English only
            continue
        i = index_of[id(r)]
        if e == "vvv" or (e.startswith("vvv") and not clean_chunk(e)):
            pending.append(i)
        elif e == ". . .":
            if pending:
                pending.append(i)
            elif out:
                out[-1][1].append(i)
            else:
                pending.append(i)
        elif e in ("-", ""):
            continue
        else:
            out.append([clean_chunk(e), pending + [i]])
            pending = []
    if pending and out:
        out[-1][1] += pending
    return [(e, sorted(set(ids))) for e, ids in out]


def rendered(groups_):
    """Word indices with an English rendering of their own."""
    return {i for e, ids in groups_ if pa.tokens(e) for i in ids}


# ---------------------------------------------------------------------------
# BSB: difflib over tokens.

def links_from_token_sets(toks, sets):
    """Adjacent tokens with the same word set make one link."""
    out = []
    last = None
    for k in sorted(sets):
        ws = list(sets[k])
        if out and last == k - 1 and out[-1][2] == ws:
            out[-1][1] = toks[k].end
        else:
            out.append([toks[k].start, toks[k].end, ws])
        last = k
    return out


def align_bsb(groups_, text, breaks):
    T = pa.tokens(text, breaks)
    A, owner = [], []
    for gi, (e, ids) in enumerate(groups_):
        for t in pa.tokens(e):
            A.append(t.norm)
            owner.append(gi)
    sm = difflib.SequenceMatcher(None, A, [t.norm for t in T], autojunk=False)
    hit = collections.defaultdict(list)
    for a, b, n in sm.get_matching_blocks():
        for k in range(n):
            hit[owner[a + k]].append(b + k)
    sets = {}
    full = part = miss = 0
    for gi, (e, ids) in enumerate(groups_):
        need = owner.count(gi)
        got = hit.get(gi, [])
        if not need:
            continue
        if len(got) == need:
            full += 1
        elif got:
            part += 1
        else:
            miss += 1
        if ids:
            for k in got:
                sets[k] = tuple(ids)
    covered = {k for v in hit.values() for k in v}
    return links_from_token_sets(T, sets), T, (full, part, miss, len(covered))


# ---------------------------------------------------------------------------
# WEB: method C.

CONTRACTIONS = {"n't": ["not"], "'ll": ["will"], "'ve": ["have"], "'re": ["are"],
                "'m": ["am"], "'d": ["would"], "'s": []}
SPECIAL = {"can't": ["can", "not"], "won't": ["will", "not"], "shan't": ["shall", "not"],
           "ain't": ["be", "not"]}
# Equivalences the WEB-to-BSB alignment was measured with, beyond the shared
# irregular table: senses the two translations render with different words.
METHOD_C_EXTRA = {"behold": "look", "lo": "look", "forever": "ever", "everyone": "every",
                  "whoever": "who", "nothing": "no", "lives": "life", "died": "die",
                  "dies": "die", "dead": "die", "death": "die", "lay": "lie"}
METHOD_C_EXTRA = {k: pa.porter(v) for k, v in METHOD_C_EXTRA.items()}


def sub_words(surface):
    w = surface.lower().replace("’", "'")
    if w in SPECIAL:
        return SPECIAL[w]
    for suffix, add in CONTRACTIONS.items():
        if w.endswith(suffix) and len(w) > len(suffix):
            return [w[: -len(suffix)]] + add
    return [w]


def c_word(w):
    """(norm, lemma, is_function)"""
    n = w.replace("'", "")
    n = "".join(chr(pa.lower_unit(ord(ch))) for ch in n)
    lemma = METHOD_C_EXTRA.get(n) or pa.stem(n)
    return n, lemma, n in pa.STOP_WORDS


ONES = ("zero one two three four five six seven eight nine ten eleven twelve thirteen "
        "fourteen fifteen sixteen seventeen eighteen nineteen").split()
TENS = "_ _ twenty thirty forty fifty sixty seventy eighty ninety".split()


def number_words(n):
    if n < 20:
        return ONES[n]
    if n < 100:
        return TENS[n // 10] + ("" if n % 10 == 0 else " " + ONES[n % 10])
    if n < 1000:
        return ONES[n // 100] + " hundred" + ("" if n % 100 == 0 else " " + number_words(n % 100))
    if n < 1000000:
        return number_words(n // 1000) + " thousand" + ("" if n % 1000 == 0 else " " + number_words(n % 1000))
    return str(n)


def web_side(text, breaks):
    toks = pa.tokens(text, breaks)
    W = []  # (token index, norm, lemma, is_function)
    for si, t in enumerate(toks):
        for w in sub_words(text[t.start:t.end]):
            n, lem, fn = c_word(w)
            if n:
                W.append((si, n, lem, fn))
    return toks, W


def bsb_side(groups_):
    """BSB English tokens in BSB order: (norm, lemma, is_function, group)."""
    out = []
    for gi, (e, ids) in enumerate(groups_):
        if not ids:
            continue
        e = re.sub(r"(?<=\d),(?=\d{3})", "", e)
        e = re.sub(r"\d+", lambda m: number_words(int(m.group())), e)
        for t in pa.tokens(e):
            for w in sub_words(e[t.start:t.end]):
                n, lem, fn = c_word(w)
                if n:
                    out.append((n, lem, fn, gi))
    return out


def lcs_pairs(W, B):
    """Weighted LCS (content 15, function 5, +1 when the words are the same
    word, not just the same stem), traced forward from the start."""
    n, m = len(W), len(B)
    S = [[0] * (m + 1) for _ in range(n + 1)]

    def score(i, j):
        if W[i][2] != B[j][1]:
            return None
        return (5 if W[i][3] else 15) + (1 if W[i][1] == B[j][0] else 0)

    for i in range(n - 1, -1, -1):
        Si, Si1 = S[i], S[i + 1]
        for j in range(m - 1, -1, -1):
            best = Si1[j] if Si1[j] > Si[j + 1] else Si[j + 1]
            sc = score(i, j)
            if sc is not None and Si1[j + 1] + sc > best:
                best = Si1[j + 1] + sc
            Si[j] = best
    pairs = []
    i = j = 0
    while i < n and j < m:
        sc = score(i, j)
        if sc is not None and S[i][j] == S[i + 1][j + 1] + sc:
            pairs.append((i, j))
            i += 1
            j += 1
        elif S[i][j] == S[i + 1][j]:
            i += 1
        else:
            j += 1
    return pairs


def method_b(W, B):
    """{sub-token index: group} from the LCS plus three repairs: a unique
    out-of-order content match, a gap between anchors that the BSB fills
    with one chunk, and a gap with as many content words as chunks."""
    pairs = lcs_pairs(W, B)
    link = {i: (B[j][3], "lcs") for i, j in pairs}
    used = {j for _, j in pairs}
    uw, ub = collections.defaultdict(list), collections.defaultdict(list)
    for i, t in enumerate(W):
        if i not in link and not t[3]:
            uw[t[2]].append(i)
    for j, t in enumerate(B):
        if j not in used and not t[2]:
            ub[t[1]].append(j)
    for lem in sorted(uw):
        iis = uw[lem]
        if len(iis) == 1 and len(ub.get(lem, [])) == 1:
            j = ub[lem][0]
            link[iis[0]] = (B[j][3], "move")
            used.add(j)
    anchors = sorted(p for p in pairs if link[p[0]][1] == "lcs")
    anchors = [(-1, -1)] + anchors + [(len(W), len(B))]
    for (i1, j1), (i2, j2) in zip(anchors, anchors[1:]):
        wg = [i for i in range(i1 + 1, i2) if i not in link and not W[i][3]]
        wf = [i for i in range(i1 + 1, i2) if i not in link]
        gs = []
        for j in range(j1 + 1, j2):
            if j not in used and B[j][3] not in gs:
                gs.append(B[j][3])
        if not wf or not gs:
            continue
        if not wg:
            if len(wf) <= 3 and len(gs) == 1:
                for i in wf:
                    link[i] = (gs[0], "gapF")
            continue
        if len(gs) == 1 and len(wg) <= 2:
            for i in wg:
                link[i] = (gs[0], "gap1")
        elif len(gs) == len(wg) and len(gs) <= 3:
            for i, g in zip(wg, gs):
                link[i] = (g, "gapN")
    return link


def method_a(toks, tag_toks, words):
    """eBible's tag on each token, used only when the tag's number is among
    the verse's words: the k-th run of number S is the k-th word with S."""
    tw = [n for n, _ in tag_toks]
    sm = difflib.SequenceMatcher(None, [t.norm for t in toks], tw, autojunk=False)
    tag_of = {}
    for a, b, size in sm.get_matching_blocks():
        for k in range(size):
            tag_of[a + k] = tag_toks[b + k][1]
    by_number = collections.defaultdict(list)
    for wi, w in enumerate(words):
        if w[2]:
            by_number[w[2]].append(wi)
    out = {}
    runs = collections.Counter()
    prev = None
    for i in range(len(toks)):
        s = tag_of.get(i)
        if s and s != prev:
            runs[s] += 1
        prev = s
        if s and s in by_number:
            cand = by_number[s]
            out[i] = cand[min(runs[s] - 1, len(cand) - 1)]
    return out


def align_web(groups_, words, text, breaks, tag_toks):
    toks, W = web_side(text, breaks)
    B = bsb_side(groups_)
    link = method_b(W, B)
    sets = {}
    for i, t in enumerate(W):
        if i in link:
            si = t[0]
            # A token's first content sub-word decides ("don't" -> "not").
            if si not in sets or (sets[si][1] and not t[3]):
                sets[si] = (tuple(groups_[link[i][0]][1]), t[3])
    sets = {k: v[0] for k, v in sets.items()}
    for i, wi in method_a(toks, tag_toks, words).items():
        if i not in sets and toks[i].is_content:
            sets[i] = (wi,)
    return links_from_token_sets(toks, sets), toks


def usfx_tags(path):
    """{(book, chapter, verse): [(norm, "H430" | None)]} from the WEB USFX,
    walked the way the converter walks it, keeping <w s="…">."""
    out = {}
    for book in ET.parse(path).getroot().iter("book"):
        bid = book.get("id")
        if bid not in NAME_TO_ID.values():
            continue
        chars = collections.defaultdict(list)
        st = {"c": None, "v": None}

        def add(text, tag):
            if text and st["c"] and st["v"]:
                chars[(bid, st["c"], st["v"])].extend((ch, tag) for ch in text)

        def walk(el, tag=None):
            for child in el:
                t = child.tag
                if t == "c":
                    st["c"] = int(child.get("id"))
                    st["v"] = None
                elif t == "v":
                    try:
                        st["v"] = int(re.sub(r"[^0-9].*$", "", child.get("id")))
                    except (TypeError, ValueError):
                        st["v"] = None
                elif t in STRIP or t in ("id", "h", "toc", "ide", "d", "s", "ve"):
                    pass
                else:
                    tg = child.get("s") if t == "w" else tag
                    add(child.text, tg)
                    walk(child, tg)
                add(child.tail, tag)

        walk(book)
        for key, cl in chars.items():
            text = "".join(ch for ch, _ in cl)
            toks = []
            for t in pa.tokens(text):
                tags = [cl[i][1] for i in range(t.start, t.end) if cl[i][1]]
                tag = None
                if tags:
                    m = re.fullmatch(r"([HG])0*(\d+)", tags[0].strip())
                    tag = f"{m.group(1)}{m.group(2)}" if m else None
                toks.append((t.norm, tag))
            out[key] = toks
    return out


# ---------------------------------------------------------------------------
# Strong's dictionaries.

OSIS = "{http://www.bibletechnologies.net/2003/OSIS/namespace}"


def squash(s):
    return re.sub(r"\s+", " ", s).strip()


def parens(s):
    """(how many '(' are left open at the end, whether a ')' comes first)"""
    depth, stray = 0, False
    for ch in s:
        if ch == "(":
            depth += 1
        elif ch == ")":
            if depth:
                depth -= 1
            else:
                stray = True
    return depth, stray


def mended(lead, body):
    """Strong's sometimes breaks one sentence across two elements: the
    derivation leaves a '(' open and the definition closes it (ἐκ: "... out
    (of place, time, or cause;" | "literal or figurative; direct or
    remote)"). Then the definition is the two read as one. A ')' with
    nothing open before it, or a '(' never closed, is otherwise left as
    Strong's has it."""
    if parens(lead)[0] and parens(body)[1]:
        return squash(lead + " " + body)
    return body


def hebrew_lexicon(path):
    def render(el):
        parts = [el.text or ""]
        for ch in el:
            if ch.tag == OSIS + "w":
                parts.append(f"H{int(ch.get('src'))}" if ch.get("src") else ch.get("lemma") or "")
            else:
                parts.append(render(ch))
            parts.append(ch.tail or "")
        return "".join(parts)

    out = {}
    for div in ET.parse(path).getroot().iter(OSIS + "div"):
        if div.get("type") != "entry":
            continue
        w = div.find(OSIS + "w")
        if w is None or not w.get("ID"):
            continue
        key = strongs_key("H", w.get("ID")[1:])
        expl = exeg = ""
        for note in div.findall(OSIS + "note"):
            if note.get("type") == "explanation":
                expl = squash("".join(note.itertext()))
            elif note.get("type") == "exegesis":
                exeg = squash(render(note))
        expl = mended(exeg, expl)
        # Strong's writes a vocal shewa as a superscript e, which Literata
        # lacks; the word transliterations write it ə, which it has.
        xlit = clean_translit(w.get("xlit") or "").replace("ᵉ", "ə")
        out[key] = [clean_hebrew(w.get("lemma") or ""), xlit, expl]
    return out


def greek_lexicon(path):
    def render(el):
        parts = [el.text or ""]
        for ch in el:
            if ch.tag == "strongsref":
                lang = "H" if ch.get("language") == "HEBREW" else "G"
                parts.append(f"{lang}{int(ch.get('strongs'))}")
            elif ch.tag == "greek":
                parts.append(ch.get("unicode") or "")
            elif ch.tag == "pronunciation":
                parts.append(ch.get("strongs") or "")
            else:
                parts.append(render(ch))
            parts.append(ch.tail or "")
        return "".join(parts)

    out = {}
    for e in ET.parse(path).getroot().iter("entry"):
        key = strongs_key("G", e.get("strongs"))
        g = e.find("greek")
        d = e.find("strongs_def")
        der = e.find("strongs_derivation")
        if d is None:
            # About twenty entries (ἐγώ, ἄν, …) carry Strong's definition in
            # the derivation element; it is still his text, not kjv_def.
            definition = squash(render(der)) if der is not None else ""
        else:
            definition = mended(squash(render(der)) if der is not None else "",
                                squash(render(d)))
        out[key] = [clean_greek(g.get("unicode") or "") if g is not None else "",
                    clean_translit(g.get("translit") or "") if g is not None else "",
                    definition]
    return out


# ---------------------------------------------------------------------------
# Verses the WEB numbers differently.

def swapped_verses(verse_rows, keys, web, margin=3):
    """[(key, neighbour, [lemmas])] for each WEB verse that shares at least
    margin more distinct content words with the tables' English of the verse
    before or after it (and not with its own) than with its own: the PHP
    1:16-17 kind, where WEB has two verses in the other order, or REV 13:1,
    where a verse opens with its neighbour's words. keys: the verses that
    have original words. web: committed("web")."""
    order = sorted(verse_rows, key=lambda k: (BOOK_ORDER.index(k[0]), k[1], k[2]))
    english = {k: {lem for _, lem, fn, _ in bsb_side(groups(*verse_rows[k])) if not fn}
               for k in order}
    out = []
    for i, key in enumerate(order):
        bk, c, v = key
        if key not in keys or v == 0 or (c, v) not in web[bk][1]:
            continue
        mine = {lem for _, _, lem, fn in web_side(*web[bk][1][(c, v)])[1] if not fn}
        own = mine & english[key]
        for j in (i - 1, i + 1):
            if 0 <= j < len(order) and order[j][0] == bk:
                foreign = (mine & english[order[j]]) - english[key]
                if len(foreign) >= len(own) + margin:
                    out.append((key, order[j], sorted(foreign)))
    return out


# ---------------------------------------------------------------------------
# Own text, two ways.

def own_text_lengths_as_the_page_builds_them(chapter):
    """A second, independent reading of the rule, shaped like the iOS page
    builder's loop (ChapterTextView): the running verse moves on any span
    with a number; only non-d blocks add their UTF-16 length."""
    lengths = {}
    running = None
    for block in chapter["blocks"]:
        if block["s"] == "b":
            continue
        for span in block["x"]:
            if span.get("v") is not None:
                running = span["v"]
            if running is not None and block["s"] != "d":
                lengths[running] = lengths.get(running, 0) + len(span["t"].encode("utf-16-le")) // 2
    return lengths


def committed(translation):
    """{book: (basis, {(c, v): (text, breaks)})}, checked two ways."""
    out = {}
    for bk in BOOK_ORDER:
        path = os.path.join(SCRIPTURE, translation, f"{bk}.json")
        with open(path, "rb") as f:
            raw = f.read()
        doc = json.loads(raw)
        verses = {}
        for ch in doc["chapters"]:
            own = pa.own_texts(ch)
            other = own_text_lengths_as_the_page_builds_them(ch)
            if {v: len(pa.utf16_units(t)) for v, (t, _) in own.items()} != other:
                raise SystemExit(f"error: own text disagrees with the page rule in {translation} {bk} {ch['n']}")
            for v, (t, br) in own.items():
                if len(t) != len(pa.utf16_units(t)):
                    raise SystemExit(f"error: {translation} {bk} {ch['n']}:{v} has astral characters")
                verses[(ch["n"], v)] = (t, br)
        out[bk] = (hashlib.sha256(raw).hexdigest()[:12], verses)
    return out


# ---------------------------------------------------------------------------
# Checks and output.

def check_links(where, links, text, nwords):
    n = len(pa.utf16_units(text))
    prev_end = 0
    for s, e, ws in links:
        if not (0 <= s < e <= n):
            raise SystemExit(f"error: {where}: link {s}..{e} outside 0..{n}")
        if s < prev_end:
            raise SystemExit(f"error: {where}: links unsorted or overlapping at {s}")
        prev_end = e
        if not ws or ws != sorted(set(ws)) or not all(0 <= w < nwords for w in ws):
            raise SystemExit(f"error: {where}: bad word indices {ws} (verse has {nwords})")
        if text[s].isspace() or text[e - 1].isspace():
            raise SystemExit(f"error: {where}: link {s}..{e} has edge whitespace")


def chapters_doc(entries, key):
    """entries: {(c, v): value} -> [{"n": c, "verses": [{"v": v, key: value}]}]"""
    by_ch = collections.defaultdict(list)
    for (c, v) in sorted(entries):
        by_ch[c].append({"v": v, key: entries[(c, v)]})
    return [{"n": c, "verses": by_ch[c]} for c in sorted(by_ch)]


def pct(a, b):
    return f"{100 * a / b:.2f}%" if b else "n/a"


def build(args, out_root, quiet):
    log = (lambda *a: None) if quiet else print
    tsv_hash = sha256_file(args.tsv)
    log(f"bsb_tables.tsv sha256 {tsv_hash}")
    if tsv_hash != TSV_SHA256:
        log("note: the tables differ from the release this script documents; "
            "the source key will change and stored marks will fall back to offsets")
    tables = read_tables(args.tsv)

    # Original words, in original order, with the psalm titles apart.
    words = {}          # (bk, c, v) -> [tuple]
    verse_rows = {}     # (bk, c, v) -> (rows in English order, {id(row): index})
    parse_long = collections.defaultdict(collections.Counter)
    for key, rows in tables.items():
        title, body = title_split(key, rows)
        bk, c, v = key
        for part, vv in ((title, 0), (body, v)):
            ordered = original_order(part)
            if ordered:
                words[(bk, c, vv)] = [word_tuple(r) for r in ordered]
                if vv:
                    verse_rows[key] = (part, {id(r): i for i, r in enumerate(ordered)})
        for r in rows:
            if r.orig and r.parse:
                parse_long[r.parse][r.parse_long] += 1
    for key in tables:
        if key not in verse_rows:
            verse_rows[key] = (tables[key], {})

    log("psalm titles (verse 0 | first words of verse 1):")
    for ps in (3, 18, 51, 60, 119, 145):
        t_rows, b_rows = title_split(("PSA", ps, 1), tables[("PSA", ps, 1)])
        t_en = " ".join(r.english.strip() for r in t_rows)
        b_en = " ".join(r.english.strip() for r in b_rows[:4])
        log(f"  Ps {ps}: {t_en or '(no title)'} | {b_en} …")
        if ps == 119 and t_rows:
            raise SystemExit("error: Psalm 119 should have no title")
    for key, rows in tables.items():
        title, body = title_split(key, rows)
        if title and body:
            t_max = max((r.sort for r in title if r.orig), default=None)
            b_min = min((r.sort for r in body if r.orig), default=None)
            if t_max is not None and b_min is not None and t_max > b_min:
                log(f"  note: {key} title words interleave with the verse in Hebrew order")

    canonical = sorted(words, key=lambda k: (BOOK_ORDER.index(k[0]), k[1], k[2]))
    h = hashlib.sha256()
    for bk, c, v in canonical:
        h.update(f"{bk}.{c}.{v}:{'|'.join(w[0] for w in words[(bk, c, v)])}\n".encode("utf-8"))
    source = "bsbt-" + h.hexdigest()[:8]
    log(f"source key {source}")

    # Lexicon and parsing.
    lex = hebrew_lexicon(args.hebrew)
    lex.update(greek_lexicon(args.greek))
    used = sorted({w[2] for ws in words.values() for w in ws if w[2]},
                  key=lambda s: (s[0], int(s[1:])))
    missing = [s for s in used if s not in lex]
    if missing:
        print(f"error: {len(missing)} Strong's numbers have no entry: {missing[:50]}")
        sys.exit(1)
    empty = [s for s in used if not lex[s][2]]
    log(f"strong's: {len(used)} numbers used, all defined; {len(empty)} with an empty definition "
        f"{empty[:12]}")
    strongs_doc = {s: lex[s] for s in used}
    parsing_doc = {}
    for short in sorted(parse_long):
        common = parse_long[short].most_common()
        parsing_doc[short] = common[0][0]
        if len(common) > 1:
            log(f"  parsing conflict {short!r}: {common}")
    log(f"parsing: {len(parsing_doc)} short codes")

    files = {}
    by_book = collections.defaultdict(dict)
    for (bk, c, v), ws in words.items():
        by_book[bk][(c, v)] = ws
    for bk in BOOK_ORDER:
        files[f"original/{bk}.json"] = dumps({"id": bk, "source": source,
                                              "chapters": chapters_doc(by_book[bk], "w")})
    files["original/strongs.json"] = dumps(strongs_doc)
    files["original/parsing.json"] = dumps(parsing_doc)

    # Alignments.
    texts = {t: committed(t) for t in ("bsb", "web")}
    golden = {("bsb", "PSA", 3, 1): 63, ("bsb", "JHN", 1, 1): 80}
    for (t, bk, c, v), n in golden.items():
        got = len(pa.utf16_units(texts[t][bk][1][(c, v)][0]))
        if got != n:
            raise SystemExit(f"error: own text of {t} {bk} {c}:{v} is {got} long, expected {n}")
    tags = usfx_tags(args.web_usfx)

    swapped = swapped_verses(verse_rows, words, texts["web"])
    log("WEB verses closer to a neighbouring verse of the tables than to their own:")
    for (bk, c, v), (_, nc, nv), foreign in swapped:
        log(f"  {bk} {c}:{v} has {nc}:{nv}'s {' '.join(foreign)}")
    unlisted = sorted({k for k, _, _ in swapped} - CROSS_VERSE["web"])
    if unlisted:
        raise SystemExit(f"error: WEB verses that look moved are not in CROSS_VERSE: {unlisted}")

    for t in ("bsb", "web"):
        st = collections.Counter()
        for bk in BOOK_ORDER:
            basis, verses = texts[t][bk]
            tt = "NT" if bk in NT else "OT"
            entries = {}
            for (c, v), (text, breaks) in verses.items():
                key = (bk, c, v)
                if key not in words or v == 0 or key in CROSS_VERSE.get(t, ()):
                    continue
                rows, index_of = verse_rows[key]
                ws = words[key]
                gs = groups(rows, index_of)
                if t == "bsb":
                    links, toks, (full, part, miss, covered) = align_bsb(gs, text, breaks)
                    st[(tt, "groups")] += full + part + miss
                    st[(tt, "groups_full")] += full
                    perfect = part == 0 and miss == 0 and covered == len(toks)
                else:
                    links, toks = align_web(gs, ws, text, breaks, tags.get(key, []))
                    if [k.norm for k in toks] != [n for n, _ in tags.get(key, [])]:
                        st["tag_text_differs"] += 1
                    perfect = all(any(s <= k.start and k.end <= e for s, e, _ in links)
                                  for k in toks if k.is_content)
                check_links(f"{t} {bk} {c}:{v}", links, text, len(ws))
                st[(tt, "verses")] += 1
                st[(tt, "perfect")] += perfect
                ren = rendered(gs)
                linked = {w for _, _, l in links for w in l}
                st[(tt, "rendered")] += len(ren)
                st[(tt, "rendered_linked")] += len(ren & linked)
                content = [k for k in toks if k.is_content]
                st[(tt, "content")] += len(content)
                st[(tt, "content_linked")] += sum(
                    1 for k in content if any(s <= k.start and k.end <= e for s, e, _ in links))
                if links:
                    entries[(c, v)] = links
            files[f"align/{t}/{bk}.json"] = dumps({
                "id": bk, "translation": t, "source": source, "basis": basis,
                "chapters": chapters_doc(entries, "l")})
        log(f"{t} alignment coverage:")
        for tt in ("OT", "NT", "ALL"):
            g = (lambda k: st[("OT", k)] + st[("NT", k)]) if tt == "ALL" else (lambda k, tt=tt: st[(tt, k)])
            line = (f"  {tt}: {g('verses')} verses, perfect {pct(g('perfect'), g('verses'))}; "
                    f"rendered original words linked {pct(g('rendered_linked'), g('rendered'))}; "
                    f"English content tokens linked {pct(g('content_linked'), g('content'))}")
            if t == "bsb":
                line += f"; chunks fully found {pct(g('groups_full'), g('groups'))}"
            log(line)
        if t == "web":
            log(f"  (eBible's tagged USFX words differ from the bundled WEB text in "
                f"{st['tag_text_differs']} verses; tags there are matched by difflib)")

    for rel, data in files.items():
        path = os.path.join(out_root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(data)
    log(f"wrote {len(files)} files under {out_root}")
    return files


def main():
    ap = argparse.ArgumentParser(description="Build Scripture/original and Scripture/align.")
    ap.add_argument("src", help="directory holding the downloads")
    ap.add_argument("--out", default=SCRIPTURE, help="Scripture root to write into")
    ap.add_argument("--no-recheck", action="store_true",
                    help="skip the second run that proves the output is deterministic")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()
    args.tsv = os.path.join(args.src, "bsb_tables.tsv")
    args.web_usfx = os.path.join(args.src, "engwebp_usfx", "engwebp_usfx.xml")
    args.hebrew = os.path.join(args.src, "StrongHebrewG.xml")
    args.greek = os.path.join(args.src, "strongsgreek.xml")
    for p in (args.tsv, args.web_usfx, args.hebrew, args.greek):
        if not os.path.exists(p):
            sys.exit(f"error: missing input {p}")
    files = build(args, args.out, args.quiet)
    if args.no_recheck:
        return
    tmp = tempfile.mkdtemp(prefix="original_to_json_")
    try:
        env = dict(os.environ, PYTHONHASHSEED="12345", PYTHONDONTWRITEBYTECODE="1")
        subprocess.run([sys.executable, os.path.abspath(__file__), args.src, "--out", tmp,
                        "--no-recheck", "--quiet"], check=True, env=env)
        for rel, data in files.items():
            with open(os.path.join(tmp, rel), encoding="utf-8") as f:
                if f.read() != data:
                    sys.exit(f"error: {rel} differs between two runs")
        print(f"deterministic: a second run (PYTHONHASHSEED=12345) wrote the same {len(files)} files")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
