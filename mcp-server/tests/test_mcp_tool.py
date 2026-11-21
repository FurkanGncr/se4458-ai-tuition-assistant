"""The tool through the real MCP protocol, with the SDK's in-memory client (no network)."""

import pytest
from mcp import Client

from policy_mcp.server import build_server

pytestmark = pytest.mark.anyio


async def test_server_lists_search_tool_with_schema(index):
    async with Client(build_server(index)) as client:
        result = await client.list_tools()

    tools = {t.name: t for t in result.tools}
    assert set(tools) == {"search_tuition_policies"}
    tool = tools["search_tuition_policies"]
    assert tool.input_schema["required"] == ["query"]
    assert tool.input_schema["properties"]["top_k"]["default"] == 3
    assert "matches" in tool.output_schema["properties"]


async def test_call_tool_returns_sections_with_ids(index):
    async with Client(build_server(index)) as client:
        result = await client.call_tool("search_tuition_policies", {"query": "refund withdrawal"})

    assert not result.is_error
    body = result.structured_content
    assert body["document"] == "University_Tuition_Policies.md"
    assert body["matches"][0]["section"] == "6.1"
    assert len(body["matches"]) == 3
    assert '"section"' in result.content[0].text  # also returned as JSON text for plain clients


async def test_call_tool_with_empty_query_is_an_error(index):
    async with Client(build_server(index)) as client:
        result = await client.call_tool("search_tuition_policies", {"query": ""})

    assert result.is_error
