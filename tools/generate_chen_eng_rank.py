#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Rank the bundled English dictionary by word frequency for chen_eng.

Run from any directory: python3 tools/generate_chen_eng_rank.py
Requires `pip install wordfreq==3.1.1`. Only words already in melt_eng (en_ext +
en) are emitted, so the dictionary vocabulary is unchanged; the output merely
orders it. The emitted file is a derivative of wordfreq's data and is therefore
distributed under CC BY-SA 4.0 (see its header).
"""

from pathlib import Path
import re

from wordfreq import zipf_frequency

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "app/src/main/assets/shared"
SOURCES = ("en_dicts/en_ext.dict.yaml", "en_dicts/en.dict.yaml")
DEST = SHARED / "en_dicts/chen_eng_rank.txt"
MIN_ZIPF = 3.0  # about one occurrence per million words
WORD = re.compile(r"[A-Za-z][A-Za-z']*")
HEADER = """\
# Chen IME English completion ranking for chen_eng (generated; do not edit).
# Regenerate with tools/generate_chen_eng_rank.py.
# One dictionary word per line, most frequent first: text[<TAB>code].
# Frequencies: wordfreq 3.1.1 by Robyn Speer, https://github.com/rspeer/wordfreq
# Data license: CC BY-SA 4.0, https://creativecommons.org/licenses/by-sa/4.0/
# wordfreq includes SUBTLEX (Brysbaert et al., freely available data),
# OpenSubtitles (http://www.opensubtitles.org/), and Google Books Ngram Viewer
# (http://books.google.com/ngrams) data. Words come from melt_eng (rime-ice).
"""


def entries():
    for name in SOURCES:
        body = (SHARED / name).read_text(encoding="utf-8").split("\n...\n", 1)[1]
        for line in body.splitlines():
            if not line or line.startswith("#"):
                continue
            fields = line.split("\t")
            if len(fields) >= 2:
                yield fields[0], fields[1]


def main():
    ranked = {}
    for text, code in entries():
        if not WORD.fullmatch(text) or code.lower() not in (text.lower(), text.lower().replace("'", "")):
            continue
        ranked.setdefault((text, code), zipf_frequency(text, "en"))
    lowercase = {text for text, _ in ranked if text == text.lower()}
    rows = [
        (-score, text.lower(), text, code)
        for (text, code), score in ranked.items()
        # A lowercase entry represents its capitalized/uppercase twin in the ranking.
        if score >= MIN_ZIPF and (text == text.lower() or text.lower() not in lowercase)
    ]
    rows.sort()
    with DEST.open("w", encoding="utf-8", newline="\n") as out:
        out.write(HEADER)
        for _, _, text, code in rows:
            out.write(text if code == text else f"{text}\t{code}")
            out.write("\n")
    print(f"{len(rows)} words -> {DEST.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
