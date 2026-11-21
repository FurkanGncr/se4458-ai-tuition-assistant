"""Tokenizer and a small Okapi BM25 implementation.

BM25 scores a document D for a query Q as::

    score(D, Q) = sum over query terms t of
        IDF(t) * tf(t, D) * (k1 + 1) / (tf(t, D) + k1 * (1 - b + b * |D| / avgdl))

    IDF(t) = ln(1 + (N - df(t) + 0.5) / (df(t) + 0.5))

- tf(t, D): how often term t occurs in D (more occurrences -> higher score, with diminishing returns via k1)
- df(t): number of documents containing t (rare terms -> higher IDF -> count more)
- |D| / avgdl: document length relative to the average (b normalises long documents)
- k1 = 1.5 and b = 0.75 are the usual defaults.

Everything that depends only on the corpus (term frequencies, IDF, lengths) is computed
once in ``BM25.__init__``. ``score`` only reads those tables, so queries never rebuild the index.
"""

from __future__ import annotations

import math
import re
from collections import Counter

import snowballstemmer

TOKEN_RE = re.compile(r"[a-z0-9]+")

# Small English stop-word list: words that carry no meaning for policy search.
STOP_WORDS = frozenset(
    """
    a an and are as at be been but by can could do does for from had has have how i if in
    into is it its may me might must my not of on or our so such than that the their them
    then there these they this to was we were what when where which while who why will with
    would you your
    """.split()
)

_stemmer = snowballstemmer.stemmer("english")


def tokenize(text: str) -> list[str]:
    """Lower-case, split into words, drop stop words, and stem (installments -> instal)."""
    words = [w for w in TOKEN_RE.findall(text.lower()) if w not in STOP_WORDS]
    return _stemmer.stemWords(words)


class BM25:
    """Okapi BM25 over a fixed list of tokenized documents."""

    def __init__(self, corpus: list[list[str]], k1: float = 1.5, b: float = 0.75) -> None:
        if not corpus:
            raise ValueError("BM25 corpus must not be empty")
        self.k1 = k1
        self.b = b
        self.doc_count = len(corpus)
        self.term_freqs = [Counter(doc) for doc in corpus]
        self.doc_lengths = [len(doc) for doc in corpus]
        self.avg_doc_length = sum(self.doc_lengths) / self.doc_count

        doc_freq: Counter[str] = Counter()
        for tf in self.term_freqs:
            doc_freq.update(tf.keys())
        self.idf = {
            term: math.log(1 + (self.doc_count - df + 0.5) / (df + 0.5))
            for term, df in doc_freq.items()
        }

    def score(self, query_tokens: list[str]) -> list[float]:
        """Return one BM25 score per document, in corpus order."""
        terms = set(query_tokens)
        scores = []
        for tf, length in zip(self.term_freqs, self.doc_lengths):
            norm = self.k1 * (1 - self.b + self.b * length / self.avg_doc_length)
            total = 0.0
            for term in terms:
                freq = tf.get(term, 0)
                if freq:
                    total += self.idf[term] * freq * (self.k1 + 1) / (freq + norm)
            scores.append(total)
        return scores
