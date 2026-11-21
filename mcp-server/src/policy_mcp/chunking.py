"""Split the tuition policy Markdown document into section chunks.

Rules (one chunk per retrievable unit):
- Every ``###`` subsection (e.g. ``### 3.2 Number and size of installments``) is one chunk.
- A ``##`` section that has no ``###`` subsections (e.g. ``## 1. Scope and terminology``)
  is one chunk on its own.
- The preamble before the first ``##`` heading is not a chunk, but the document id
  (``**Document ID:** ...``) is read from it.
- Lines inside fenced code blocks are never treated as headings.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

HEADING_RE = re.compile(r"^(#{2,3})\s+(\d+(?:\.\d+)?)\.?\s+(.+?)\s*$")
DOCUMENT_ID_RE = re.compile(r"\*\*Document ID:\*\*\s*([^\s]+)")
FENCE_RE = re.compile(r"^\s*(```|~~~)")


@dataclass(frozen=True)
class Chunk:
    """One searchable section of the policy document."""

    section: str  # e.g. "3.2" or "1"
    heading: str  # e.g. "Number and size of installments"
    parent_section: str | None  # e.g. "3" for "3.2"; None for a top-level chunk
    parent_heading: str | None  # e.g. "Installment policy"
    document: str  # file name, e.g. "University_Tuition_Policies.md"
    document_id: str | None  # e.g. "UNI-TUITION-2026-01"
    text: str  # section body, Markdown emphasis removed


@dataclass
class _Section:
    level: int
    number: str
    heading: str
    lines: list[str]


def _clean(lines: list[str]) -> str:
    """Join body lines and strip bold/italic markers; keep code blocks and line structure."""
    text = "\n".join(lines).strip()
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


def chunk_markdown(markdown: str, document: str) -> list[Chunk]:
    """Return the section chunks of ``markdown`` in document order."""
    document_id: str | None = None
    sections: list[_Section] = []
    in_fence = False

    for line in markdown.splitlines():
        if FENCE_RE.match(line):
            in_fence = not in_fence
        match = None if in_fence else HEADING_RE.match(line)
        if match:
            hashes, number, heading = match.groups()
            sections.append(_Section(len(hashes), number, heading, []))
            continue
        if sections:
            sections[-1].lines.append(line)
        elif document_id is None:
            id_match = DOCUMENT_ID_RE.search(line)
            if id_match:
                document_id = id_match.group(1)

    chunks: list[Chunk] = []
    for i, sec in enumerate(sections):
        if sec.level == 2:
            has_subsections = i + 1 < len(sections) and sections[i + 1].level == 3
            if has_subsections:
                continue  # its subsections become the chunks
            chunks.append(
                Chunk(sec.number, sec.heading, None, None, document, document_id, _clean(sec.lines))
            )
        else:
            parent = next((s for s in reversed(sections[:i]) if s.level == 2), None)
            chunks.append(
                Chunk(
                    section=sec.number,
                    heading=sec.heading,
                    parent_section=parent.number if parent else None,
                    parent_heading=parent.heading if parent else None,
                    document=document,
                    document_id=document_id,
                    text=_clean(sec.lines),
                )
            )
    return chunks


def chunk_file(path: str | Path) -> list[Chunk]:
    """Read a Markdown file and return its section chunks."""
    path = Path(path)
    return chunk_markdown(path.read_text(encoding="utf-8"), document=path.name)
