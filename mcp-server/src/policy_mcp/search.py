"""The policy index: section chunks + a BM25 index built once, and the search response model."""

from __future__ import annotations

import logging
from pathlib import Path

from pydantic import BaseModel, Field

from policy_mcp.bm25 import BM25, tokenize
from policy_mcp.chunking import Chunk, chunk_file

logger = logging.getLogger(__name__)

DEFAULT_TOP_K = 3
MAX_TOP_K = 5


class PolicyMatch(BaseModel):
    """One matching policy section."""

    section: str = Field(description="Section id in the policy document, e.g. '3.2'")
    heading: str = Field(description="Section heading, e.g. 'Number and size of installments'")
    parent_heading: str | None = Field(description="Heading of the parent section, e.g. 'Installment policy'")
    text: str = Field(description="Full text of the section")
    score: float = Field(description="BM25 relevance score (higher is more relevant)")


class SearchResponse(BaseModel):
    """Result of search_tuition_policies, shaped as described in policy section 9."""

    document: str = Field(description="Source document file name")
    document_id: str | None = Field(description="Document id, e.g. 'UNI-TUITION-2026-01'")
    query: str = Field(description="The query that was searched")
    matches: list[PolicyMatch] = Field(description="Best matching sections, most relevant first; empty if none match")


class PolicyIndex:
    """Chunks of one policy document plus their BM25 index. Build it once, query it many times."""

    def __init__(self, chunks: list[Chunk]) -> None:
        if not chunks:
            raise ValueError("Policy document produced no chunks")
        self.chunks = chunks
        self.document = chunks[0].document
        self.document_id = chunks[0].document_id
        # Headings are indexed together with the body so heading words are searchable too.
        corpus = [tokenize(" ".join(filter(None, [c.parent_heading, c.heading, c.text]))) for c in chunks]
        self._bm25 = BM25(corpus)

    @classmethod
    def from_file(cls, path: str | Path) -> PolicyIndex:
        index = cls(chunk_file(path))
        logger.info("Indexed %d chunks from %s", len(index.chunks), path)
        return index

    def search(self, query: str, top_k: int = DEFAULT_TOP_K) -> SearchResponse:
        """Return up to ``top_k`` sections with a positive BM25 score, best first."""
        if not query or not query.strip():
            raise ValueError("query must not be empty")
        if not 1 <= top_k <= MAX_TOP_K:
            raise ValueError(f"top_k must be between 1 and {MAX_TOP_K}")

        scores = self._bm25.score(tokenize(query))
        # Sort by score (desc); ties keep document order because sorted() is stable.
        ranked = sorted(range(len(self.chunks)), key=lambda i: scores[i], reverse=True)
        matches = [
            PolicyMatch(
                section=self.chunks[i].section,
                heading=self.chunks[i].heading,
                parent_heading=self.chunks[i].parent_heading,
                text=self.chunks[i].text,
                score=round(scores[i], 4),
            )
            for i in ranked[:top_k]
            if scores[i] > 0
        ]
        return SearchResponse(
            document=self.document,
            document_id=self.document_id,
            query=query,
            matches=matches,
        )
