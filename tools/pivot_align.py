#!/usr/bin/env python3
"""The reference for Ribbon's runtime word aligner, and the generator of
everything the two cores share with it.

A licensed version (NKJV, NIV, NASB) comes to the phone as plain text, with
no word data and no right to store a derived copy. So when someone reads one,
the phone lines that chapter up against the bundled BSB text of the same
verse, and the BSB's links to the Hebrew and Greek (Scripture/align/bsb)
carry over to the matching English words. Nothing is stored or uploaded; it
is recomputed from the chapter the phone already holds.

This file is that aligner in Python. The Swift core (PivotAligner.swift) and
the Kotlin core (PivotAligner.kt) are ports of it, case for case:

  tokens   A token is a maximal run of token characters, counted in UTF-16
           code units: ASCII letters and digits, and U+00C0-U+024F except
           U+00D7 and U+00F7. An apostrophe (U+0027 or U+2019) stays inside
           a token only when both its neighbours are token characters.
           Hyphens and everything else break. Span breaks (the own-text
           offsets where a span starts: poetry lines are glued with no
           space) always break. `norm` is the token lowercased by the simple
           per-character mapping in `lower_unit` (ASCII, Latin-1 and
           Latin Extended-A only) with apostrophes removed. A token is a
           content word when its norm is not in STOP_WORDS.
  stem     IRREGULAR[norm] if present, else Porter's 1980 algorithm (the
           published one, not Porter2 and not the later C departures).
           IRREGULAR's values are already Porter stems of the base form, so
           "said" and "saying" meet at "sai".
  align    A weighted longest common subsequence over stems (content match
           3, stop-word match 1), integers only, with a fixed traceback;
           paired reader tokens inherit the word set of the pivot link that
           contains their partner; stop words survive only between content
           links or beside a content link with the same words; runs of
           adjacent kept tokens with one word set merge into one link.

It writes:
  core/Sources/RibbonCore/PivotLexicon.generated.swift
  android/core/src/main/kotlin/app/readribbon/core/PivotLexicon.generated.kt
      the stop words and the irregular table, so neither app hand-copies them;
  core/Tests/RibbonCoreTests/Fixtures/pivot_cases.json
      test vectors both cores read: Porter stems, tokenisation cases, and
      real verse pairs (WEB and KJV pivoted onto BSB) with the links this
      reference produces.

Run (from the repo root):
  python3 tools/pivot_align.py
      regenerate the two PivotLexicon files only.
  python3 tools/pivot_align.py --kjv PATH/eng-kjv2006_usfx.xml
      also write the fixture (needs Scripture/align/bsb from
      tools/original_to_json.py) and print the quality measurement: WEB and
      KJV aligned to BSB through the pivot, content-word coverage, and for
      WEB the precision against the bundled method-C links (Scripture/align/web).
  python3 tools/pivot_align.py --selftest
      check the Porter implementation against the examples in Porter's paper.

The KJV is used only to measure and to make test cases; it is never bundled.
Source (public domain): https://ebible.org/Scriptures/eng-kjv2006_usfx.zip
Stdlib only, Python 3.11.
"""

import argparse
import collections
import json
import os
import random
import sys
import xml.etree.ElementTree as ET
from typing import NamedTuple

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTURE = os.path.join(ROOT, "ios", "Ribbon", "Resources", "Scripture")
SWIFT_OUT = os.path.join(ROOT, "core", "Sources", "RibbonCore", "PivotLexicon.generated.swift")
KOTLIN_OUT = os.path.join(ROOT, "android", "core", "src", "main", "kotlin",
                          "app", "readribbon", "core", "PivotLexicon.generated.kt")
FIXTURE_OUT = os.path.join(ROOT, "core", "Tests", "RibbonCoreTests", "Fixtures", "pivot_cases.json")

# ---------------------------------------------------------------------------
# The lexicon. Words are in `norm` form: lowercase, apostrophes removed.

# Function words: articles, pronouns (modern and archaic), prepositions,
# conjunctions, auxiliaries and modals, negation, and contractions as they
# norm. A stop word still pairs in the alignment (weight 1) but never makes
# a link on its own. Content-bearing words that translations render from a
# real original word ("all", "never", "now", "behold") are left out on
# purpose. Contractions that norm into ordinary words ("we'll" -> "well",
# "he'll" -> "hell", "I'll" -> "ill") are left out too.
STOP_WORDS = frozenset("""
a an the
i me my mine myself we us our ours ourselves
you your yours yourself yourselves thee thou thy thine thyself ye
he him his himself she her hers herself it its itself
they them their theirs themselves
who whom whose which what that this these those
whoever whomever whatever whichever
of to in into on onto at by with within without from for as
about above across after against along among amongst around
before behind below beneath beside besides between beyond
down during except near off out over past since through throughout
toward towards under underneath until till unto up upon
and or but nor so yet if then than because though although
while whereas whether unless lest when where whence whither
there here also even either neither both
be am is are was were been being art wast wert
have has had having hath hast hadst
do does did doing doth dost didst
shall shalt will wilt would should may might must can canst could
not no o oh let
dont doesnt didnt isnt arent wasnt werent wont cant couldnt wouldnt
shouldnt hasnt havent hadnt im ive youre youve youll youd
theyre theyve theyll weve thats theres whats whos lets hes shes
""".split())

# Irregular forms -> the base form whose Porter stem they should meet.
# The generated table maps each key to porter(base); entries Porter already
# handles (porter(key) == porter(base)) are dropped from it as redundant.
IRREGULAR_BASES = {
    # be, have, do, say, and the modals' archaic forms
    "am": "be", "is": "be", "are": "be", "was": "be", "were": "be",
    "been": "be", "being": "be", "art": "be", "wast": "be", "wert": "be",
    "has": "have", "had": "have", "hath": "have", "hast": "have", "hadst": "have",
    "does": "do", "did": "do", "done": "do", "doth": "do", "dost": "do",
    "didst": "do", "doeth": "do",
    "said": "say", "saith": "say", "sayest": "say", "saidst": "say",
    "shalt": "shall", "wilt": "will", "canst": "can", "couldest": "could",
    "wouldest": "would", "shouldest": "should", "mayest": "may", "mightest": "might",
    # archaic pronouns and prepositions
    "thou": "you", "thee": "you", "ye": "you", "thy": "your", "thine": "your",
    "thyself": "yourself", "unto": "to",
    # the divine name as some versions print it, where others print LORD
    "yahweh": "lord", "jehovah": "lord",
    # irregular verbs: past and participle forms (KJV forms included)
    "arose": "arise", "arisen": "arise", "awoke": "awake", "awoken": "awake",
    "bore": "bear", "borne": "bear", "born": "bear",
    "beaten": "beat", "became": "become", "began": "begin", "begun": "begin",
    "begat": "beget", "begot": "beget", "begotten": "beget", "beheld": "behold",
    "bent": "bend", "bade": "bid", "bidden": "bid", "bound": "bind",
    "bitten": "bite", "bled": "bleed", "blew": "blow", "blown": "blow",
    "broke": "break", "broken": "break", "brake": "break", "bred": "breed",
    "brought": "bring", "built": "build", "builded": "build", "burnt": "burn",
    "bought": "buy", "caught": "catch", "chose": "choose", "chosen": "choose",
    "clave": "cleave", "cleft": "cleave", "clung": "cling", "came": "come",
    "crept": "creep", "dealt": "deal", "dug": "dig", "digged": "dig",
    "drew": "draw", "drawn": "draw", "drank": "drink", "drunk": "drink",
    "drove": "drive", "driven": "drive", "drave": "drive", "dwelt": "dwell",
    "ate": "eat", "eaten": "eat", "fell": "fall", "fallen": "fall",
    "fed": "feed", "felt": "feel", "fought": "fight", "found": "find",
    "fled": "flee", "flew": "fly", "flown": "fly", "forbade": "forbid",
    "forbidden": "forbid", "forgot": "forget", "forgotten": "forget",
    "forgave": "forgive", "forgiven": "forgive", "forsook": "forsake",
    "forsaken": "forsake", "froze": "freeze", "frozen": "freeze",
    "got": "get", "gotten": "get", "gat": "get", "gave": "give", "given": "give",
    "went": "go", "gone": "go", "grew": "grow", "grown": "grow",
    "heard": "hear", "hid": "hide", "hidden": "hide", "held": "hold",
    "kept": "keep", "knelt": "kneel", "knew": "know", "known": "know",
    "laid": "lay", "lain": "lie", "led": "lead", "left": "leave", "lent": "lend",
    "lit": "light", "lost": "lose", "made": "make", "meant": "mean", "met": "meet",
    "paid": "pay", "rent": "rend", "rode": "ride", "ridden": "ride",
    "rang": "ring", "rung": "ring", "rose": "rise", "risen": "rise", "ran": "run",
    "saw": "see", "seen": "see", "sought": "seek", "sold": "sell", "sent": "send",
    "shook": "shake", "shaken": "shake", "shone": "shine", "shot": "shoot",
    "shown": "show", "shew": "show", "shewed": "show", "shewn": "show",
    "sang": "sing", "sung": "sing", "sank": "sink", "sunk": "sink", "sat": "sit",
    "slew": "slay", "slain": "slay", "slept": "sleep", "slid": "slide",
    "smote": "smite", "smitten": "smite", "sown": "sow",
    "spoke": "speak", "spoken": "speak", "spake": "speak", "spent": "spend",
    "spun": "spin", "spat": "spit", "sprang": "spring", "sprung": "spring",
    "stood": "stand", "stole": "steal", "stolen": "steal", "stuck": "stick",
    "stung": "sting", "stank": "stink", "strode": "stride",
    "struck": "strike", "stricken": "strike", "strove": "strive", "striven": "strive",
    "swore": "swear", "sworn": "swear", "sware": "swear", "swept": "sweep",
    "swam": "swim", "swum": "swim", "swung": "swing",
    "took": "take", "taken": "take", "taught": "teach", "tore": "tear", "torn": "tear",
    "told": "tell", "thought": "think", "threw": "throw", "thrown": "throw",
    "trod": "tread", "trodden": "tread", "understood": "understand",
    "woke": "wake", "woken": "wake", "wore": "wear", "worn": "wear",
    "wove": "weave", "woven": "weave", "wept": "weep", "won": "win",
    "withdrew": "withdraw", "withdrawn": "withdraw", "withheld": "withhold",
    "withstood": "withstand", "wrung": "wring", "wrote": "write", "written": "write",
    "abode": "abide", "wrought": "work", "holpen": "help",
    "overcame": "overcome", "overtook": "overtake", "overtaken": "overtake",
    # irregular plurals
    "men": "man", "women": "woman", "children": "child", "brethren": "brother",
    "feet": "foot", "teeth": "tooth", "geese": "goose", "mice": "mouse",
    "oxen": "ox", "lice": "louse", "kine": "cow", "wives": "wife",
    "knives": "knife", "loaves": "loaf", "wolves": "wolf", "calves": "calf",
    "halves": "half", "thieves": "thief", "sheaves": "sheaf",
    # the commonest KJV -eth forms of irregular verbs
    "cometh": "come", "goeth": "go", "giveth": "give", "taketh": "take",
    "maketh": "make", "seeth": "see", "knoweth": "know",
}


# ---------------------------------------------------------------------------
# Porter (1980), "An algorithm for suffix stripping", Program 14(3):130-137.
# The published algorithm exactly: step 2 has ABLI -> ABLE and no LOGI rule,
# and there is no short-word guard. Within a step the longest matching
# suffix is the only one tried; if its condition fails the step does
# nothing. Every non-vowel letter (digits and accented letters included) is
# a consonant; Y is a vowel only after a consonant.

def _is_cons(w, i):
    c = w[i]
    if c in "aeiou":
        return False
    if c == "y":
        return i == 0 or not _is_cons(w, i - 1)
    return True


def _measure(stem):
    """m in [C](VC)^m[V]."""
    m = 0
    i = 0
    n = len(stem)
    while i < n and _is_cons(stem, i):
        i += 1
    while i < n:
        while i < n and not _is_cons(stem, i):
            i += 1
        if i >= n:
            break
        while i < n and _is_cons(stem, i):
            i += 1
        m += 1
    return m


def _has_vowel(stem):
    return any(not _is_cons(stem, i) for i in range(len(stem)))


def _ends_double_cons(w):
    return len(w) >= 2 and w[-1] == w[-2] and _is_cons(w, len(w) - 1)


def _ends_cvc(w):
    n = len(w)
    return (n >= 3 and _is_cons(w, n - 3) and not _is_cons(w, n - 2)
            and _is_cons(w, n - 1) and w[-1] not in "wxy")


def _apply(w, rules):
    """rules: (suffix, replacement, condition on stem), longest suffix first."""
    for suffix, repl, cond in rules:
        if w.endswith(suffix):
            stem = w[: len(w) - len(suffix)]
            return stem + repl if cond(stem) else w
    return w


def _m_gt0(s):
    return _measure(s) > 0


def _m_gt1(s):
    return _measure(s) > 1


def step1a(w):
    if w.endswith("sses"):
        return w[:-2]
    if w.endswith("ies"):
        return w[:-2]
    if w.endswith("ss"):
        return w
    if w.endswith("s"):
        return w[:-1]
    return w


def step1b(w):
    if w.endswith("eed"):
        stem = w[:-3]
        return stem + "ee" if _measure(stem) > 0 else w
    for suffix in ("ed", "ing"):
        if w.endswith(suffix):
            stem = w[: len(w) - len(suffix)]
            if not _has_vowel(stem):
                return w
            if stem.endswith(("at", "bl", "iz")):
                return stem + "e"
            if _ends_double_cons(stem) and stem[-1] not in "lsz":
                return stem[:-1]
            if _measure(stem) == 1 and _ends_cvc(stem):
                return stem + "e"
            return stem
    return w


def step1c(w):
    if w.endswith("y") and _has_vowel(w[:-1]):
        return w[:-1] + "i"
    return w


_STEP2 = [(s, r, _m_gt0) for s, r in sorted([
    ("ational", "ate"), ("tional", "tion"), ("enci", "ence"), ("anci", "ance"),
    ("izer", "ize"), ("abli", "able"), ("alli", "al"), ("entli", "ent"),
    ("eli", "e"), ("ousli", "ous"), ("ization", "ize"), ("ation", "ate"),
    ("ator", "ate"), ("alism", "al"), ("iveness", "ive"), ("fulness", "ful"),
    ("ousness", "ous"), ("aliti", "al"), ("iviti", "ive"), ("biliti", "ble"),
], key=lambda r: -len(r[0]))]

_STEP3 = [(s, r, _m_gt0) for s, r in sorted([
    ("icate", "ic"), ("ative", ""), ("alize", "al"), ("iciti", "ic"),
    ("ical", "ic"), ("ful", ""), ("ness", ""),
], key=lambda r: -len(r[0]))]


def _ion_cond(s):
    return _measure(s) > 1 and s.endswith(("s", "t"))


_STEP4 = sorted(
    [(s, "", _m_gt1) for s in ("al", "ance", "ence", "er", "ic", "able", "ible", "ant",
                               "ement", "ment", "ent", "ou", "ism", "ate", "iti",
                               "ous", "ive", "ize")]
    + [("ion", "", _ion_cond)],
    key=lambda r: -len(r[0]))


def step2(w):
    return _apply(w, _STEP2)


def step3(w):
    return _apply(w, _STEP3)


def step4(w):
    return _apply(w, _STEP4)


def step5a(w):
    if w.endswith("e"):
        stem = w[:-1]
        m = _measure(stem)
        if m > 1 or (m == 1 and not _ends_cvc(stem)):
            return stem
    return w


def step5b(w):
    if _measure(w) > 1 and _ends_double_cons(w) and w.endswith("l"):
        return w[:-1]
    return w


def porter(word):
    """Porter's 1980 stemmer over an already-lowercased word."""
    w = word
    for step in (step1a, step1b, step1c, step2, step3, step4, step5a, step5b):
        w = step(w)
    return w


def _build_irregular():
    out = {}
    for key, base in IRREGULAR_BASES.items():
        target = porter(base)
        if porter(key) != target:
            out[key] = target
    return dict(sorted(out.items()))


IRREGULAR = _build_irregular()


def stem(norm):
    return IRREGULAR.get(norm) or porter(norm)


# ---------------------------------------------------------------------------
# Tokens, over UTF-16 code units.

APOSTROPHES = (0x27, 0x2019)


def utf16_units(s):
    b = s.encode("utf-16-le")
    return [b[i] | (b[i + 1] << 8) for i in range(0, len(b), 2)]


def is_token_unit(u):
    return (0x30 <= u <= 0x39 or 0x41 <= u <= 0x5A or 0x61 <= u <= 0x7A
            or (0xC0 <= u <= 0x24F and u != 0xD7 and u != 0xF7))


def lower_unit(u):
    """Unicode's simple lowercase mapping, applied to ASCII, Latin-1 and
    Latin Extended-A only (Extended-B passes through unchanged). Written
    out so Swift and Kotlin can carry the identical rule."""
    if 0x41 <= u <= 0x5A:
        return u + 0x20
    if 0xC0 <= u <= 0xDE and u != 0xD7:
        return u + 0x20
    if u == 0x130:
        return 0x69
    if 0x100 <= u <= 0x137 or 0x14A <= u <= 0x177:
        return u + 1 if u % 2 == 0 else u
    if 0x139 <= u <= 0x148 or 0x179 <= u <= 0x17E:
        return u + 1 if u % 2 == 1 else u
    if u == 0x178:
        return 0xFF
    return u


class Token(NamedTuple):
    start: int      # UTF-16 offset, inclusive
    end: int        # UTF-16 offset, exclusive
    norm: str
    stem: str
    is_content: bool


def tokens(text, span_breaks=()):
    units = utf16_units(text)
    n = len(units)
    breaks = set(span_breaks)
    out = []
    i = 0
    while i < n:
        if not is_token_unit(units[i]):
            i += 1
            continue
        start = i
        i += 1
        while i < n and i not in breaks:
            u = units[i]
            if is_token_unit(u):
                i += 1
            elif (u in APOSTROPHES and i + 1 < n and is_token_unit(units[i + 1])
                  and (i + 1) not in breaks):
                i += 1
            else:
                break
        norm = "".join(chr(lower_unit(u)) for u in units[start:i] if u not in APOSTROPHES)
        s = stem(norm)
        out.append(Token(start, i, norm, s, norm not in STOP_WORDS))
    return out


# ---------------------------------------------------------------------------
# The aligner. Links are (start, end, [word indices]) in UTF-16 offsets.

def _words_of(tok, links):
    for s, e, w in links:
        if s <= tok.start and tok.end <= e:
            return tuple(w)
    return None


def align(reader, reader_breaks, pivot, pivot_breaks, pivot_links):
    R = tokens(reader, reader_breaks)
    P = tokens(pivot, pivot_breaks)
    psets = [_words_of(t, pivot_links) for t in P]
    n, m = len(R), len(P)
    # D[i][j]: best weight aligning R[:i] with P[:j].
    D = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        ri = R[i - 1]
        w = 3 if ri.is_content else 1
        row, prev = D[i], D[i - 1]
        for j in range(1, m + 1):
            best = prev[j] if prev[j] >= row[j - 1] else row[j - 1]
            if ri.stem == P[j - 1].stem and prev[j - 1] + w > best:
                best = prev[j - 1] + w
            row[j] = best
    pairs = []
    i, j = n, m
    while i > 0 and j > 0:
        match = R[i - 1].stem == P[j - 1].stem
        w = 3 if R[i - 1].is_content else 1
        if match and D[i][j] == D[i - 1][j - 1] + w:
            pairs.append((i - 1, j - 1))
            i -= 1
            j -= 1
        elif D[i][j] == D[i - 1][j]:
            i -= 1
        else:
            j -= 1
    pairs.reverse()
    cand = {ri: psets[pj] for ri, pj in pairs if psets[pj] is not None}
    content = sorted(ri for ri in cand if R[ri].is_content)
    kept = set(content)
    lo = content[0] if content else None
    hi = content[-1] if content else None
    for ri in sorted(cand):
        if R[ri].is_content:
            continue
        between = lo is not None and lo < ri < hi
        beside = any(k in kept and R[k].is_content and cand[k] == cand[ri]
                     for k in (ri - 1, ri + 1))
        if between or beside:
            kept.add(ri)
    out = []
    last = None
    for ri in sorted(kept):
        ws = cand[ri]
        if out and last == ri - 1 and out[-1][2] == list(ws):
            out[-1][1] = R[ri].end
        else:
            out.append([R[ri].start, R[ri].end, list(ws)])
        last = ri
    return out


# ---------------------------------------------------------------------------
# Own text (DESIGN §1) and corpus loading, shared with original_to_json.py.

def own_texts(chapter):
    """{verse: (own text, [UTF-16 offsets where a non-first span starts])}."""
    texts = {}
    breaks = {}
    running = None
    for block in chapter["blocks"]:
        for span in block["x"]:
            if "v" in span:
                running = span["v"]
            if running is None or block["s"] in ("d", "b"):
                continue
            prev = texts.get(running, "")
            if running in texts:
                breaks.setdefault(running, []).append(len(utf16_units(prev)))
            texts[running] = prev + span["t"]
    return {v: (t, breaks.get(v, [])) for v, t in texts.items()}


def load_book_texts(translation, book):
    with open(os.path.join(SCRIPTURE, translation, f"{book}.json"), encoding="utf-8") as f:
        doc = json.load(f)
    return {ch["n"]: own_texts(ch) for ch in doc["chapters"]}


def load_alignment(translation, book):
    path = os.path.join(SCRIPTURE, "align", translation, f"{book}.json")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    return {(ch["n"], v["v"]): v["l"] for ch in doc["chapters"] for v in ch["verses"]}


def load_usfx_texts(path):
    """{(book, chapter, verse): (own text, breaks)} from an eBible USFX file,
    walked exactly the way tools/usfx_to_json.py walks the bundled ones."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from usfx_to_json import BOOK_IDS, convert_book
    out = {}
    for book_el in ET.parse(path).getroot().iter("book"):
        bid = book_el.get("id")
        if bid not in BOOK_IDS:
            continue
        for ch in convert_book(book_el):
            for v, tb in own_texts(ch).items():
                out[(bid, ch["n"], v)] = tb
    return out


def book_ids():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from usfx_to_json import BOOKS
    return [b[0] for b in BOOKS]


# ---------------------------------------------------------------------------
# Generated lexicon files.

HEADER = [
    "// Generated by tools/pivot_align.py — do not edit by hand. The words the",
    "// pivot aligner treats as function words, and the irregular forms it",
    "// stems by table before Porter's algorithm; both cores read these, so the",
    "// two apps cannot drift apart on what a word is.",
    "",
]


def _wrap(items, indent, width=96):
    lines, cur = [], indent
    for it in items:
        piece = it + ","
        if len(cur) + len(piece) + 1 > width and cur.strip():
            lines.append(cur.rstrip())
            cur = indent
        cur += piece + " "
    if cur.strip():
        lines.append(cur.rstrip())
    return lines


def write_lexicon():
    stops = sorted(STOP_WORDS)
    irr = sorted(IRREGULAR.items())
    swift = HEADER + ["public enum PivotLexicon {",
                      "    public static let stopWords: Set<String> = ["]
    swift += _wrap([f'"{w}"' for w in stops], " " * 8)
    swift += ["    ]", "", "    public static let irregular: [String: String] = ["]
    swift += _wrap([f'"{k}": "{v}"' for k, v in irr], " " * 8)
    swift += ["    ]", "}"]
    kotlin = HEADER + ["package app.readribbon.core", "", "object PivotLexicon {",
                       "    val stopWords: Set<String> = setOf("]
    kotlin += _wrap([f'"{w}"' for w in stops], " " * 8)
    kotlin += ["    )", "", "    val irregular: Map<String, String> = mapOf("]
    kotlin += _wrap([f'"{k}" to "{v}"' for k, v in irr], " " * 8)
    kotlin += ["    )", "}"]
    for path, lines in ((SWIFT_OUT, swift), (KOTLIN_OUT, kotlin)):
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        print(f"wrote {os.path.relpath(path, ROOT)}")
    print(f"  {len(stops)} stop words, {len(irr)} irregular forms")


# ---------------------------------------------------------------------------
# Self-test against the examples printed in Porter's paper.

PAPER_EXAMPLES = {
    step1a: [("caresses", "caress"), ("ponies", "poni"), ("ties", "ti"),
             ("caress", "caress"), ("cats", "cat")],
    step1b: [("feed", "feed"), ("agreed", "agree"), ("plastered", "plaster"),
             ("bled", "bled"), ("motoring", "motor"), ("sing", "sing"),
             ("conflated", "conflate"), ("troubled", "trouble"), ("sized", "size"),
             ("hopping", "hop"), ("tanned", "tan"), ("falling", "fall"),
             ("hissing", "hiss"), ("fizzed", "fizz"), ("failing", "fail"),
             ("filing", "file")],
    step1c: [("happy", "happi"), ("sky", "sky")],
    step2: [("relational", "relate"), ("conditional", "condition"),
            ("rational", "rational"), ("valenci", "valence"), ("hesitanci", "hesitance"),
            ("digitizer", "digitize"), ("conformabli", "conformable"),
            ("radicalli", "radical"), ("differentli", "different"), ("vileli", "vile"),
            ("analogousli", "analogous"), ("vietnamization", "vietnamize"),
            ("predication", "predicate"), ("operator", "operate"),
            ("feudalism", "feudal"), ("decisiveness", "decisive"),
            ("hopefulness", "hopeful"), ("callousness", "callous"),
            ("formaliti", "formal"), ("sensitiviti", "sensitive"),
            ("sensibiliti", "sensible")],
    step3: [("triplicate", "triplic"), ("formative", "form"), ("formalize", "formal"),
            ("electriciti", "electric"), ("electrical", "electric"),
            ("hopeful", "hope"), ("goodness", "good")],
    step4: [("revival", "reviv"), ("allowance", "allow"), ("inference", "infer"),
            ("airliner", "airlin"), ("gyroscopic", "gyroscop"),
            ("adjustable", "adjust"), ("defensible", "defens"), ("irritant", "irrit"),
            ("replacement", "replac"), ("adjustment", "adjust"),
            ("dependent", "depend"), ("adoption", "adopt"), ("homologou", "homolog"),
            ("communism", "commun"), ("activate", "activ"), ("angulariti", "angular"),
            ("homologous", "homolog"), ("effective", "effect"),
            ("bowdlerize", "bowdler")],
    step5a: [("probate", "probat"), ("rate", "rate"), ("cease", "ceas")],
    step5b: [("controll", "control"), ("roll", "roll")],
    porter: [("generalizations", "gener"), ("oscillators", "oscil")],
}


def selftest():
    bad = 0
    for fn, cases in PAPER_EXAMPLES.items():
        for word, want in cases:
            got = fn(word)
            if got != want:
                print(f"FAIL {fn.__name__}({word!r}) = {got!r}, want {want!r}")
                bad += 1
    print("porter self-test:", "ok" if not bad else f"{bad} failures")
    return bad == 0


# ---------------------------------------------------------------------------
# Fixture and measurement.

FIXTURE_VERSES = [
    ("GEN", 1, 1), ("GEN", 1, 2), ("GEN", 22, 2), ("EXO", 3, 14), ("EXO", 15, 11),
    ("LEV", 19, 18), ("NUM", 6, 24), ("DEU", 6, 5), ("JOS", 1, 9), ("RUT", 1, 16),
    ("1SA", 17, 45), ("1KI", 18, 21), ("2CH", 7, 14), ("NEH", 8, 10), ("EST", 4, 14),
    ("JOB", 19, 25), ("PSA", 3, 1), ("PSA", 23, 1), ("PSA", 23, 4), ("PSA", 51, 10),
    ("PSA", 119, 105), ("PRO", 3, 5), ("ECC", 3, 1), ("ISA", 9, 6), ("ISA", 40, 31),
    ("ISA", 53, 5), ("JER", 29, 11), ("LAM", 3, 22), ("EZK", 36, 26), ("DAN", 3, 17),
    ("MIC", 6, 8), ("MAL", 3, 10), ("MAT", 5, 3), ("MAT", 6, 9), ("MRK", 10, 45),
    ("LUK", 2, 14), ("LUK", 15, 20), ("JHN", 1, 1), ("JHN", 1, 14), ("JHN", 3, 16),
    ("JHN", 11, 35), ("ROM", 8, 28), ("1CO", 13, 4), ("GAL", 2, 20), ("EPH", 2, 8),
    ("PHP", 4, 13), ("HEB", 11, 1), ("JAS", 1, 5), ("1JN", 4, 8), ("REV", 21, 4),
]

TOKEN_CASES = [
    ("In the beginning was the Word, and the Word was with God.", []),
    ("God’s love isn't small; it's the LORD's.", []),
    ("'Tis the Lord’s doing — Jesus’ disciples' feet.", []),
    ("rock-solid, well-being and 12,000 men", []),
    ("O LORD, how my foes have increased!How many rise up against me!", [35]),
    ("to an idolor swear deceitfully", [11]),
    ("“Who is like You, O LORD?” he said.", []),
    ("Ésaïe naïve ÉLAN façade Œuvre ŒUVRE İstanbul Ÿ", []),
    ("a×b c÷d ǅemal ƀ", []),
    ("😀 God’s 😀word", []),
    ("don’t’", []),
    ("’’", []),
    ("", []),
    ("abc", [1, 2]),
    ("it’s", [2]),
    ("it’s", [3]),
]

EXTRA_STEM_WORDS = """
caresses ponies ties caress cats feed agreed plastered bled motoring sing conflated
troubled sized hopping tanned falling hissing fizzed failing filing happy sky
relational conditional rational valenci hesitanci digitizer conformabli radicalli
differentli vileli analogousli vietnamization predication operator feudalism
decisiveness hopefulness callousness formaliti sensitiviti sensibiliti triplicate
formative formalize electriciti electrical hopeful goodness revival allowance
inference airliner gyroscopic adjustable defensible irritant replacement adjustment
dependent adoption homologou communism activate angulariti homologous effective
bowdlerize probate rate cease controll roll generalizations oscillators
by yes eye y yy a i o abc 12 12th
""".split()


def _verses_for_stems():
    """Common corpus words, so the fixture exercises Porter on real input."""
    counts = collections.Counter()
    for book in ("GEN", "PSA", "ISA", "MAT", "JHN", "ROM", "REV"):
        for verses in load_book_texts("web", book).values():
            for text, br in verses.values():
                counts.update(t.norm for t in tokens(text, br))
    return [w for w, _ in counts.most_common(400)]


def write_fixture(kjv):
    bsb_links = {b: load_alignment("bsb", b) for b in {v[0] for v in FIXTURE_VERSES}}
    if any(v is None for v in bsb_links.values()):
        sys.exit("error: Scripture/align/bsb is missing; run tools/original_to_json.py first")
    words = set(IRREGULAR_BASES) | set(STOP_WORDS) | set(EXTRA_STEM_WORDS)
    words |= set(_verses_for_stems())
    stems = [[w, stem(w)] for w in sorted(words)]
    tok_cases = [{"text": t, "breaks": b,
                  "tokens": [[k.start, k.end, k.norm, k.stem, k.is_content]
                             for k in tokens(t, b)]} for t, b in TOKEN_CASES]
    cases = []
    for bk, c, v in FIXTURE_VERSES:
        ptext, pbr = load_book_texts("bsb", bk)[c][v]
        plinks = bsb_links[bk].get((c, v), [])
        readers = [("web", load_book_texts("web", bk)[c][v]), ("kjv", kjv[(bk, c, v)])]
        for name, (rtext, rbr) in readers:
            cases.append({"name": f"{name} {bk} {c}:{v}", "reader": rtext,
                          "readerBreaks": rbr, "pivot": ptext, "pivotBreaks": pbr,
                          "pivotLinks": plinks,
                          "expected": align(rtext, rbr, ptext, pbr, plinks)})
    # Edges: an empty reader, a pivot with no links, the stop-word keep rule.
    edge = [
        ("", [], "In the beginning", [], [[0, 16, [0]]]),
        ("In the beginning", [], "In the beginning", [], []),
        ("the Word of God", [], "the Word of God", [], [[0, 3, [0]], [4, 8, [1]], [9, 11, [2]], [12, 15, [3]]]),
        ("of the Word and of the God", [], "of the Word and of the God", [],
         [[0, 2, [9]], [3, 6, [8]], [7, 11, [1]], [12, 15, [5]], [16, 18, [7]], [19, 22, [6]], [23, 26, [2]]]),
        ("who is like you", [], "Who is like You", [], [[0, 6, [0]], [7, 15, [1]]]),
        ("and and and", [], "and", [], [[0, 3, [4]]]),
    ]
    for i, (r, rb, p, pb, pl) in enumerate(edge):
        cases.append({"name": f"edge {i + 1}", "reader": r, "readerBreaks": rb, "pivot": p,
                      "pivotBreaks": pb, "pivotLinks": pl, "expected": align(r, rb, p, pb, pl)})
    doc = {"stems": stems, "tokens": tok_cases, "align": cases}
    os.makedirs(os.path.dirname(FIXTURE_OUT), exist_ok=True)
    dump = lambda x: json.dumps(x, ensure_ascii=False, separators=(",", ":"))
    with open(FIXTURE_OUT, "w", encoding="utf-8") as f:
        f.write("{\n")
        f.write('"stems":[\n' + ",\n".join(dump(s) for s in stems) + "\n],\n")
        f.write('"tokens":[\n' + ",\n".join(dump(t) for t in tok_cases) + "\n],\n")
        f.write('"align":[\n' + ",\n".join(dump(a) for a in cases) + "\n]\n")
        f.write("}\n")
    print(f"wrote {os.path.relpath(FIXTURE_OUT, ROOT)}: {len(stems)} stems, "
          f"{len(tok_cases)} token cases, {len(cases)} alignment cases")


def measure(kjv):
    """Coverage of WEB and KJV through the pivot, and WEB precision against
    the bundled method-C links."""
    NT = set(book_ids()[39:])
    st = collections.Counter()
    for bk in book_ids():
        bsb_texts = load_book_texts("bsb", bk)
        bsb_links = load_alignment("bsb", bk)
        web_texts = load_book_texts("web", bk)
        web_links = load_alignment("web", bk) or {}
        t = "NT" if bk in NT else "OT"
        for c, verses in bsb_texts.items():
            for v, (ptext, pbr) in verses.items():
                plinks = bsb_links.get((c, v))
                for name, src in (("web", web_texts.get(c, {}).get(v)), ("kjv", kjv.get((bk, c, v)))):
                    if src is None:
                        continue
                    rtext, rbr = src
                    rt = tokens(rtext, rbr)
                    content = [k for k in rt if k.is_content]
                    st[(name, t, "content")] += len(content)
                    st[(name, t, "verses")] += 1
                    if plinks is None:
                        continue
                    got = align(rtext, rbr, ptext, pbr, plinks)
                    linked = [k for k in content if any(s <= k.start and k.end <= e for s, e, _ in got)]
                    st[(name, t, "linked")] += len(linked)
                    if name != "web":
                        continue
                    ref = web_links.get((c, v), [])
                    for k in content:
                        g = next((set(w) for s, e, w in got if s <= k.start and k.end <= e), None)
                        r = next((set(w) for s, e, w in ref if s <= k.start and k.end <= e), None)
                        if g is None:
                            continue
                        if r is None:
                            st[(t, "pivot_only")] += 1
                            continue
                        st[(t, "both")] += 1
                        if g == r:
                            st[(t, "same")] += 1
                        if g & r:
                            st[(t, "overlap")] += 1
    print("pivot quality (content words of the reader version):")
    for name in ("web", "kjv"):
        for t in ("OT", "NT", "ALL"):
            g = (lambda k: st[(name, "OT", k)] + st[(name, "NT", k)]) if t == "ALL" \
                else (lambda k, t=t: st[(name, t, k)])
            print(f"  {name.upper()} {t}: {g('verses')} verses, {g('content')} content words, "
                  f"linked {100 * g('linked') / max(1, g('content')):.1f}%")
    for t in ("OT", "NT", "ALL"):
        g = (lambda k: st[("OT", k)] + st[("NT", k)]) if t == "ALL" else (lambda k, t=t: st[(t, k)])
        both = g("both")
        print(f"  WEB {t} precision vs method C, over {both} content words both link: "
              f"same words {100 * g('same') / max(1, both):.1f}%, "
              f"overlapping words {100 * g('overlap') / max(1, both):.1f}%; "
              f"{g('pivot_only')} linked by the pivot only")
    return st


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--kjv", help="path to eng-kjv2006_usfx.xml: write the fixture and measure")
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--no-measure", action="store_true", help="with --kjv, skip the measurement")
    args = ap.parse_args()
    if not selftest():
        sys.exit(1)
    if args.selftest:
        return
    write_lexicon()
    if args.kjv:
        kjv = load_usfx_texts(args.kjv)
        write_fixture(kjv)
        if not args.no_measure:
            measure(kjv)


if __name__ == "__main__":
    main()
