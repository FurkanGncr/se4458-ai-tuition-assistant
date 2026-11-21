"""The three required interactions (MCP-only, REST-only, combined), driven by a fake LLM."""

import pytest
from conftest import STUDENT_NO, text_reply, tool_reply

from tuition_agent.tools import GET_MY_TUITION, SEARCH_POLICIES, TOOL_SCHEMAS
from tuition_agent.trace import format_turn

pytestmark = pytest.mark.anyio


async def test_mcp_only_policy_question(agent, llm, backend, policy_server):
    llm.script(
        tool_reply((SEARCH_POLICIES, {"query": "payment deadline"})),
        text_reply("Fall tuition is due September 30, 2026 (Policy §2.1)."),
    )

    result = await agent.handle("When is the payment deadline?")

    assert [e.tool for e in result.trace] == [SEARCH_POLICIES]
    assert result.trace[0].input == {"query": "payment deadline"}
    assert result.trace[0].result["matches"][0]["section"] == "2.1"
    assert policy_server.queries == ["payment deadline"]
    assert backend.requests == []  # no REST call
    assert "§2.1" in result.answer
    assert result.llm_calls == 2


async def test_rest_only_balance_question(agent, llm, backend, policy_server):
    llm.script(tool_reply((GET_MY_TUITION, {"term": None})), text_reply("Your balance is 8000.00 USD."))

    result = await agent.handle("What is my balance?")

    assert [e.tool for e in result.trace] == [GET_MY_TUITION]
    [request] = backend.requests
    assert request.url.path == f"/api/v1/banking/tuition/{STUDENT_NO}"
    assert "term" not in request.url.params
    assert policy_server.queries == []  # no MCP call
    assert result.answer == "Your balance is 8000.00 USD."


async def test_combined_installment_question(agent, llm, backend, policy_server):
    llm.script(
        tool_reply((GET_MY_TUITION, {"term": None}), (SEARCH_POLICIES, {"query": "installment eligibility"})),
        text_reply("Your 2026-FALL balance is 3000.00 USD, so you may request installments (Policy §3.1, §3.4)."),
    )

    result = await agent.handle("Can I pay my outstanding balance in installments?")

    assert [e.tool for e in result.trace] == [GET_MY_TUITION, SEARCH_POLICIES]
    assert len(backend.requests) == 1
    assert policy_server.queries == ["installment eligibility"]
    # The second LLM call receives both tool results, answered by tool_call_id.
    tool_messages = [m for m in llm.requests[1] if m["role"] == "tool"]
    assert [m["tool_call_id"] for m in tool_messages] == ["call_0", "call_1"]
    assert '"balance": 3000.0' in tool_messages[0]["content"]


async def test_llm_receives_system_prompt_with_student_and_the_three_tools(agent, llm):
    llm.script(text_reply("Hello!"))

    await agent.handle("hi")

    [messages] = llm.requests
    assert messages[0]["role"] == "system"
    assert STUDENT_NO in messages[0]["content"]
    assert {t["function"]["name"] for t in TOOL_SCHEMAS} == {
        "search_tuition_policies",
        "get_my_tuition",
        "request_tuition_payment",
    }
    for tool in TOOL_SCHEMAS:  # no tool lets the LLM choose a student
        assert "studentNo" not in tool["function"]["parameters"]["properties"]


async def test_history_is_sent_on_the_next_turn(agent, llm):
    llm.script(text_reply("First answer."), text_reply("Second answer."))

    await agent.handle("first question")
    await agent.handle("second question")

    contents = [m.get("content") for m in llm.requests[1]]
    assert contents[1:] == ["first question", "First answer.", "second question"]


async def test_tool_errors_are_returned_to_the_llm(agent, llm):
    llm.script(tool_reply((GET_MY_TUITION, {"term": "2030-FALL"})), text_reply("There is no record for 2030-FALL."))

    result = await agent.handle("balance for 2030-FALL?")

    assert result.trace[0].result == {
        "error": {"status": 404, "title": "Not Found", "source": "api", "detail": "No tuition for 2030-FALL"}
    }
    assert result.answer == "There is no record for 2030-FALL."


async def test_tool_loop_is_bounded(llm, executor):
    from tuition_agent.agent import Agent

    agent = Agent(llm, executor, max_tool_rounds=2)
    llm.script(*[tool_reply((SEARCH_POLICIES, {"query": "deadline"})) for _ in range(3)])

    result = await agent.handle("loop forever")

    assert len(result.trace) == 2
    assert result.llm_calls == 3
    assert "could not complete" in result.answer


async def test_trace_shows_tool_input_result_and_answer(agent, llm):
    llm.script(tool_reply((SEARCH_POLICIES, {"query": "payment deadline"})), text_reply("Due September 30 (Policy §2.1)."))

    text = format_turn(await agent.handle("When is the payment deadline?"))

    assert "[1] search_tuition_policies" in text
    assert 'input : {"query": "payment deadline"}' in text
    assert '"section": "2.1"' in text
    assert "Due September 30 (Policy §2.1)." in text
    assert "llm calls: 2 · tokens in/out: 300/40" in text
