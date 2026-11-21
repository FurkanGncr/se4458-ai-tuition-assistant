"""MCP client through the real MCP protocol, against an in-process fake server (no network)."""

import pytest

from tuition_agent.policy_search import PolicySearchClient, PolicySearchError

pytestmark = pytest.mark.anyio


async def test_search_returns_structured_matches(policy_server):
    result = await PolicySearchClient(policy_server).search("installment rules")

    assert result["document"] == "University_Tuition_Policies.md"
    assert [m["section"] for m in result["matches"]] == ["3.1", "3.4"]


async def test_top_k_is_passed_to_the_server(policy_server):
    result = await PolicySearchClient(policy_server, top_k=1).search("installment rules")

    assert len(result["matches"]) == 1


async def test_tool_error_raises(policy_server):
    with pytest.raises(PolicySearchError):
        await PolicySearchClient(policy_server).search("   ")


async def test_unreachable_server_raises():
    with pytest.raises(PolicySearchError, match="MCP server unavailable"):
        await PolicySearchClient("http://127.0.0.1:9/mcp").search("deadline")


async def test_mcp_failure_reaches_the_llm_as_a_tool_error(llm, api, session):
    from conftest import text_reply, tool_reply

    from tuition_agent.agent import Agent
    from tuition_agent.tools import SEARCH_POLICIES, ToolExecutor

    agent = Agent(llm, ToolExecutor(api, PolicySearchClient("http://127.0.0.1:9/mcp"), session))
    llm.script(tool_reply((SEARCH_POLICIES, {"query": "deadline"})), text_reply("The policy service is down."))

    result = await agent.handle("When is the deadline?")

    assert "MCP server unavailable" in result.trace[0].result["error"]["message"]
    assert result.answer == "The policy service is down."
