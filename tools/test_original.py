#!/usr/bin/env python3
"""Tests for tools/original_to_json.py and tools/pivot_align.py.

Run from the repo root:  python3 -m unittest tools/test_original.py
Stdlib only. The corpus checks read the committed Scripture JSON (and the
generated original/ and align/ files when they exist) and skip otherwise.
"""

import json
import os
import subprocess
import sys
import unicodedata
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True

import original_to_json as otj  # noqa: E402
import pivot_align as pa  # noqa: E402

SCRIPTURE = otj.SCRIPTURE


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def fake_row(lang, orig, translit="x", strongs="", parse="", english=" x "):
    x = [""] * 23
    x[otj.LANG] = lang
    x[otj.ORIG] = orig
    x[otj.HEB_SORT] = "1"
    x[otj.GRK_SORT] = "1"
    x[otj.TRANSLIT] = translit
    x[otj.STR_GRK if lang == "Greek" else otj.STR_HEB] = strongs
    x[otj.PARSE] = parse
    x[otj.ENGLISH] = english
    return otj.Row(x)


class HebrewCleanerTests(unittest.TestCase):
    def test_strips_cantillation_meteg_and_sof_pasuq(self):
        self.assertEqual(otj.clean_hebrew("מִֽי־"), "מִי־")
        self.assertEqual(otj.clean_hebrew("חַסְדּֽוֹ׃"), "חַסְדּוֹ")

    def test_strips_trailing_paragraph_marker(self):
        self.assertEqual(otj.clean_hebrew("יִשְׂרָאֵֽ֑ל פ"), "יִשְׂרָאֵל")
        self.assertEqual(otj.clean_hebrew("הָאָֽרֶץ׃ ס"), "הָאָרֶץ")

    def test_strips_paseq_and_zero_width_joiners(self):
        self.assertEqual(otj.clean_hebrew("מִפְּנֵ֤י׀"), "מִפְּנֵי")
        self.assertEqual(otj.clean_hebrew("כֹּ‍הֵן"), "כֹּהֵן")

    def test_keeps_points_dagesh_shin_dot_and_maqaf(self):
        word = "בְּרֵאשִׁית"
        self.assertEqual(otj.clean_hebrew(word), word)
        self.assertEqual(otj.clean_hebrew("אֶת־"), "אֶת־")

    def test_greek_is_nfc_without_undertie(self):
        self.assertEqual(otj.clean_greek("μή‿"), "μή")
        decomposed = unicodedata.normalize("NFD", "λόγος")
        self.assertEqual(otj.clean_greek(decomposed), "λόγος")


class TranslitTests(unittest.TestCase):
    def test_decomposed_with_macron_below(self):
        t = otj.clean_translit("bə·rê·šîṯ")
        self.assertIn("ṯ", t)
        self.assertNotIn("ṯ", t)
        self.assertEqual(t, unicodedata.normalize("NFD", t))

    def test_low_line_becomes_macron_below(self):
        self.assertEqual(otj.clean_translit("k̲"), "ḵ")

    def test_not_recomposed(self):
        t = otj.clean_translit("’ā·ḇîw")
        self.assertNotEqual(t, unicodedata.normalize("NFC", t))


class TupleTests(unittest.TestCase):
    def test_hebrew_tuple(self):
        r = fake_row("Hebrew", "אֱלֹהִ֑ים", "’ĕ·lō·hîm", "0430", "N-mp")
        self.assertEqual(otj.word_tuple(r), ["אֱלֹהִים", unicodedata.normalize("NFD", "’ĕ·lō·hîm"), "H430", "N-mp"])

    def test_aramaic_tuple_has_fifth_element(self):
        r = fake_row("Aramaic", "מַלְכָּא", "mal·kā", "4430", "N-msd")
        t = otj.word_tuple(r)
        self.assertEqual(len(t), 5)
        self.assertEqual(t[4], "a")

    def test_greek_and_missing_strongs(self):
        self.assertEqual(otj.word_tuple(fake_row("Greek", "Λόγος", "Logos", "3056", "N-NSM"))[2], "G3056")
        self.assertEqual(otj.word_tuple(fake_row("Hebrew", "ב֖וֹ", "bōw", "", "Prep | 3ms"))[2], "")

    def test_groups_follow_the_tables_conventions(self):
        rows = [fake_row("Greek", "a", english=" vvv "), fake_row("Greek", "b", english=" shall not perish "),
                fake_row("Greek", "c", english=" - "), fake_row("Greek", "d", english=" seed-bearing "),
                fake_row("Greek", "e", english=" . . . "), fake_row("Greek", "f", english=" [the] {will} beginning ")]
        index_of = {id(r): i for i, r in enumerate(rows)}
        self.assertEqual(otj.groups(rows, index_of),
                         [("shall not perish", [0, 1]), ("seed-bearing", [3, 4]), ("the will beginning", [5])])

    def test_bundled_tuples(self):
        path = os.path.join(SCRIPTURE, "original", "JHN.json")
        if not os.path.exists(path):
            self.skipTest("original/ not generated")
        doc = load(path)
        first = doc["chapters"][0]["verses"][0]
        self.assertEqual(first["v"], 1)
        self.assertEqual(first["w"][0], ["Ἐν", "En", "G1722", "Prep"])
        for ch in doc["chapters"]:
            for v in ch["verses"]:
                for w in v["w"]:
                    self.assertIn(len(w), (4, 5))
                    self.assertTrue(all(isinstance(x, str) for x in w))
        dan = load(os.path.join(SCRIPTURE, "original", "DAN.json"))
        aramaic = [w for ch in dan["chapters"] for v in ch["verses"] for w in v["w"] if len(w) == 5]
        self.assertTrue(aramaic and all(w[4] == "a" for w in aramaic))


class OwnTextTests(unittest.TestCase):
    def own(self, translation, book, c, v):
        path = os.path.join(SCRIPTURE, translation, f"{book}.json")
        doc = load(path)
        ch = next(ch for ch in doc["chapters"] if ch["n"] == c)
        return pa.own_texts(ch)[v]

    def test_bsb_psalm_3_1_is_63(self):
        text, breaks = self.own("bsb", "PSA", 3, 1)
        self.assertEqual(len(pa.utf16_units(text)), 63)
        self.assertEqual(breaks, [35])

    def test_bsb_john_1_1_is_80(self):
        text, breaks = self.own("bsb", "JHN", 1, 1)
        self.assertEqual(len(pa.utf16_units(text)), 80)
        self.assertEqual(breaks, [])

    def test_both_readings_of_the_rule_agree(self):
        doc = load(os.path.join(SCRIPTURE, "web", "SNG.json"))
        for ch in doc["chapters"]:
            own = {v: len(pa.utf16_units(t)) for v, (t, _) in pa.own_texts(ch).items()}
            self.assertEqual(own, otj.own_text_lengths_as_the_page_builds_them(ch))


class PivotTests(unittest.TestCase):
    def test_porter_paper_examples(self):
        self.assertTrue(pa.selftest())

    def test_irregular_values_meet_their_regular_forms(self):
        self.assertEqual(pa.stem("said"), pa.stem("saying"))
        self.assertEqual(pa.stem("children"), pa.stem("child"))
        self.assertEqual(pa.stem("spake"), pa.stem("speaks"))

    def test_tokens_break_at_spans_and_keep_inner_apostrophes(self):
        toks = pa.tokens("O LORD, how my foes have increased!How many", [35])
        self.assertEqual([t.norm for t in toks][-2:], ["how", "many"])
        toks = pa.tokens("God’s Jesus’ rock-solid")
        self.assertEqual([(t.start, t.end, t.norm) for t in toks],
                         [(0, 5, "gods"), (6, 11, "jesus"), (13, 17, "rock"), (18, 23, "solid")])

    def test_tokens_count_utf16_units(self):
        toks = pa.tokens("😀 God")
        self.assertEqual((toks[0].start, toks[0].end), (3, 6))

    def test_align_is_deterministic_across_hash_seeds(self):
        code = ("import sys,json; sys.path.insert(0,%r); import pivot_align as pa; "
                "print(json.dumps(pa.align('Who is like you, O Yahweh, among the gods?', [], "
                "'Who among the gods is like You, O LORD?', [], "
                "[[0,3,[0]],[4,9,[3]],[10,13,[4]],[14,18,[4]],[19,21,[1]],[22,30,[1]],[34,38,[2]]])))") % HERE
        outs = set()
        for seed in ("0", "1", "4242"):
            env = dict(os.environ, PYTHONHASHSEED=seed, PYTHONDONTWRITEBYTECODE="1")
            outs.add(subprocess.run([sys.executable, "-c", code], env=env, check=True,
                                    capture_output=True, text=True).stdout)
        self.assertEqual(len(outs), 1)
        links = json.loads(outs.pop())
        self.assertTrue(links)

    def test_stop_word_keep_rule(self):
        links = pa.align("the Word of God", [], "the Word of God", [],
                         [[0, 3, [0]], [4, 8, [1]], [9, 11, [2]], [12, 15, [3]]])
        self.assertEqual(links, [[4, 8, [1]], [9, 11, [2]], [12, 15, [3]]])

    def test_fixture_matches_the_reference(self):
        path = pa.FIXTURE_OUT
        if not os.path.exists(path):
            self.skipTest("fixture not generated")
        doc = load(path)
        for w, s in doc["stems"]:
            self.assertEqual(pa.stem(w), s, w)
        for case in doc["tokens"]:
            got = [[t.start, t.end, t.norm, t.stem, t.is_content]
                   for t in pa.tokens(case["text"], case["breaks"])]
            self.assertEqual(got, case["tokens"], case["text"])
        for case in doc["align"]:
            got = pa.align(case["reader"], case["readerBreaks"], case["pivot"],
                           case["pivotBreaks"], case["pivotLinks"])
            self.assertEqual(got, case["expected"], case["name"])


if __name__ == "__main__":
    unittest.main()
