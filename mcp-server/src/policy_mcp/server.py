"""MCP server exposing search_tuition_policies(query) over Streamable HTTP.

Run on the host:   uv run policy-mcp          (http://127.0.0.1:8000/mcp)
Environment:
    POLICY_DOC_PATH  path to University_Tuition_Policies.md (default: the repo's docs/ copy)
    MCP_HOST         bind address (default 127.0.0.1; the container uses 0.0.0.0)
    MCP_PORT         port (default 8000)
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Annotated

from mcp.server import MCPServer
from pydantic import Field

from policy_mcp.search import DEFAULT_TOP_K, MAX_TOP_K, PolicyIndex, SearchResponse

logger = logging.getLogger(__name__)

DEFAULT_DOC_PATH = Path(__file__).resolve().parents[3] / "docs" / "University_Tuition_Policies.md"

INSTRUCTIONS = (
    "Searches the university's general tuition policies (deadlines, installments, late fees, "
    "scholarships, refunds, banking/payment rules). It returns policy excerpts with section ids. "
    "It does not contain any student's balance or payments; use the Tuition REST API for those."
)


def build_server(index: PolicyIndex) -> MCPServer:
    """Create the MCP server around an index that is already built."""
    server = MCPServer("tuition-policy-mcp", instructions=INSTRUCTIONS)

    @server.tool()
    def search_tuition_policies(
        query: Annotated[
            str,
            Field(min_length=1, max_length=500, description="Natural-language question or keywords, e.g. 'installment eligibility'"),
        ],
        top_k: Annotated[
            int,
            Field(ge=1, le=MAX_TOP_K, description="Maximum number of sections to return"),
        ] = DEFAULT_TOP_K,
    ) -> SearchResponse:
        """Search the University Tuition Policies document (full-text BM25).

        Returns the most relevant sections with their section id (e.g. "3.2"), heading and text.
        Cite the section ids in answers. The document holds general policy only, never
        student-specific balances, payments or scholarship records.
        """
        return index.search(query, top_k)

    return server


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    doc_path = Path(os.environ.get("POLICY_DOC_PATH", DEFAULT_DOC_PATH))
    host = os.environ.get("MCP_HOST", "127.0.0.1")
    port = int(os.environ.get("MCP_PORT", "8000"))

    # The index is built once here, before the server accepts any query.
    index = PolicyIndex.from_file(doc_path)
    server = build_server(index)
    logger.info("Serving search_tuition_policies on http://%s:%d/mcp", host, port)
    server.run(
        "streamable-http",
        host=host,
        port=port,
        streamable_http_path="/mcp",
        stateless_http=True,
        json_response=True,
    )


if __name__ == "__main__":
    main()
