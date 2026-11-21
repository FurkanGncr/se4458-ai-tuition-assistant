"""MCP client for the policy server's search_tuition_policies tool (Streamable HTTP)."""

from __future__ import annotations

import json
from typing import Any

from mcp import Client

TOOL_NAME = "search_tuition_policies"
DEFAULT_TOP_K = 3


class PolicySearchError(Exception):
    """The MCP server could not be reached or returned a tool error."""


class PolicySearchClient:
    def __init__(self, server: Any, top_k: int = DEFAULT_TOP_K) -> None:
        """`server` is the MCP URL (e.g. http://localhost:8091/mcp), or an in-process server in tests."""
        self._server = server
        self._top_k = top_k

    async def search(self, query: str) -> dict[str, Any]:
        # One short connection per search: the server is stateless, and no session sits idle
        # while the user is typing.
        try:
            async with Client(self._server) as client:
                result = await client.call_tool(TOOL_NAME, {"query": query, "top_k": self._top_k})
        except Exception as exc:  # connection errors arrive as ExceptionGroups from the SDK's task groups
            raise PolicySearchError(f"MCP server unavailable: {type(exc).__name__}: {exc}") from exc

        text = next((c.text for c in result.content if getattr(c, "type", None) == "text"), "")
        if result.is_error:
            raise PolicySearchError(text or "search_tuition_policies returned an error")
        if isinstance(result.structured_content, dict):
            return result.structured_content
        try:
            return json.loads(text)
        except ValueError as exc:
            raise PolicySearchError("search_tuition_policies returned no JSON result") from exc
