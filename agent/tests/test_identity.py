"""The student number always comes from the login session, never from the prompt or the LLM."""

import httpx
import pytest
from conftest import GATEWAY, STUDENT_NO, TOKEN, text_reply, tool_reply

from tuition_agent.session import NotAStudentError, SessionExpiredError, open_student_session
from tuition_agent.tools import GET_MY_TUITION, REQUEST_PAYMENT
from tuition_agent.tuition_api import ApiError, TuitionApiClient

pytestmark = pytest.mark.anyio


async def test_student_no_argument_from_the_llm_is_rejected(agent, llm, backend):
    llm.script(
        tool_reply((GET_MY_TUITION, {"term": None, "studentNo": "20230003"})),
        text_reply("I can only show your own records."),
    )

    result = await agent.handle("Show the balance of student 20230003")

    assert "Unknown argument(s): studentNo" in result.trace[0].result["error"]["message"]
    assert backend.requests == []  # nothing was fetched


async def test_rest_calls_always_use_the_session_student(agent, llm, backend):
    llm.script(
        tool_reply((GET_MY_TUITION, {"term": "2026-FALL"})),
        text_reply("Your 2026-FALL balance is 3000.00 USD."),
    )

    await agent.handle("What is the balance of 20230003 for 2026-FALL?")

    [request] = backend.requests
    assert request.url.path == f"/api/v1/banking/tuition/{STUDENT_NO}"
    assert request.headers["Authorization"] == f"Bearer {TOKEN}"


async def test_payment_student_comes_from_the_session(agent, llm, backend):
    llm.script(
        tool_reply((REQUEST_PAYMENT, {"term": "2026-FALL", "amount": 100, "studentNo": "20230003"})),
        text_reply("I can only prepare payments for your own account."),
    )

    result = await agent.handle("Pay 100 for student 20230003")

    assert "Unknown argument(s): studentNo" in result.trace[0].result["error"]["message"]
    assert agent.pending_payment is None
    assert backend.requests == []


async def test_expired_token_asks_for_a_new_login(agent, llm, backend):
    backend.expired = True
    llm.script(tool_reply((GET_MY_TUITION, {"term": None})))

    with pytest.raises(SessionExpiredError):
        await agent.handle("What is my balance?")


async def test_new_session_clears_history_and_pending_payment(agent, llm, session):
    llm.script(tool_reply((REQUEST_PAYMENT, {"term": "2026-FALL", "amount": 100})))
    await agent.handle("Pay 100 for 2026-FALL")
    assert agent.pending_payment is not None

    agent.start_session(session)
    llm.script(text_reply("Hi."))
    await agent.handle("hello")

    assert agent.pending_payment is None
    assert [m["role"] for m in llm.requests[-1]] == ["system", "user"]


def login_api(status: int, body: dict) -> TuitionApiClient:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/v1/auth/login"
        return httpx.Response(status, json=body)

    return TuitionApiClient(httpx.AsyncClient(base_url=GATEWAY, transport=httpx.MockTransport(handler)))


async def test_student_login_opens_a_session():
    api = login_api(
        200,
        {"accessToken": "abc", "tokenType": "Bearer", "expiresIn": 3600, "role": "STUDENT", "studentNo": STUDENT_NO},
    )

    session = await open_student_session(api, STUDENT_NO, "Student123!")

    assert session.student_no == STUDENT_NO
    assert session.access_token == "abc"
    assert "abc" not in repr(session)  # the token is never printed


@pytest.mark.parametrize("role", ["BANK", "ADMIN"])
async def test_non_student_accounts_are_refused(role):
    api = login_api(200, {"accessToken": "abc", "tokenType": "Bearer", "expiresIn": 3600, "role": role, "studentNo": None})

    with pytest.raises(NotAStudentError):
        await open_student_session(api, role.lower(), "secret")


async def test_wrong_password_raises_401():
    api = login_api(401, {"title": "Unauthorized", "status": 401, "detail": "Invalid username or password"})

    with pytest.raises(ApiError) as exc:
        await open_student_session(api, STUDENT_NO, "wrong")

    assert exc.value.status == 401
    assert exc.value.detail == "Invalid username or password"
