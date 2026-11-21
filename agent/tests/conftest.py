"""Offline test doubles: a scripted fake LLM, a fake gateway/API (httpx.MockTransport) and an
in-process MCP server that speaks the real MCP protocol. No network, no live LLM."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any

import httpx
import pytest
from mcp.server import MCPServer

from tuition_agent.agent import Agent
from tuition_agent.llm import LLMReply, ToolCall, Usage
from tuition_agent.policy_search import PolicySearchClient
from tuition_agent.session import Session
from tuition_agent.tools import ToolExecutor
from tuition_agent.tuition_api import TuitionApiClient

STUDENT_NO = "20230001"
TOKEN = "test-token-20230001"
GATEWAY = "http://gateway.test"


# --- fake LLM ---------------------------------------------------------------------------------


def tool_reply(*calls: tuple[str, dict[str, Any]], content: str | None = None) -> LLMReply:
    return LLMReply(
        content=content,
        tool_calls=[ToolCall(id=f"call_{i}", name=name, arguments=json.dumps(args)) for i, (name, args) in enumerate(calls)],
        usage=Usage(100, 10),
    )


def text_reply(text: str) -> LLMReply:
    return LLMReply(content=text, usage=Usage(200, 30))


class FakeLLM:
    """Returns scripted replies in order and records every request it gets."""

    def __init__(self, *replies: LLMReply) -> None:
        self._replies = list(replies)
        self.requests: list[list[dict[str, Any]]] = []

    def script(self, *replies: LLMReply) -> None:
        self._replies.extend(replies)

    async def complete(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> LLMReply:
        self.requests.append(json.loads(json.dumps(messages)))
        if not self._replies:
            raise AssertionError("FakeLLM was called more often than scripted")
        return self._replies.pop(0)


# --- fake gateway + Tuition API ---------------------------------------------------------------


@dataclass
class FakeBackend:
    """In-memory tuition data for one student behind a fake gateway. Records every request."""

    terms: dict[str, dict[str, Any]] = field(
        default_factory=lambda: {
            "2026-FALL": {"amount": Decimal("5000.00"), "paid": Decimal("2000.00"), "currency": "USD"},
            "2027-SPRING": {"amount": Decimal("5000.00"), "paid": Decimal("0.00"), "currency": "USD"},
        }
    )
    requests: list[httpx.Request] = field(default_factory=list)
    payment_error: tuple[int, dict[str, Any]] | None = None
    expired: bool = False

    def payments(self) -> list[dict[str, Any]]:
        return [json.loads(r.content) for r in self.requests if r.method == "POST" and r.url.path.endswith("/payments")]

    def _term_json(self, term: str) -> dict[str, Any]:
        t = self.terms[term]
        return {
            "term": term,
            "amount": float(t["amount"]),
            "paidAmount": float(t["paid"]),
            "balance": float(t["amount"] - t["paid"]),
            "currency": t["currency"],
        }

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        path = request.url.path
        if self.expired:
            return httpx.Response(401, json={"exp": "token expired"})
        if request.headers.get("Authorization") != f"Bearer {TOKEN}":
            return httpx.Response(401, json={"message": "Unauthorized"})
        if request.method == "GET" and path.startswith("/api/v1/banking/tuition/"):
            student_no = path.rsplit("/", 1)[1]
            if student_no != STUDENT_NO:
                return httpx.Response(403, json={"title": "Forbidden", "status": 403, "detail": "Not your record"})
            term = request.url.params.get("term")
            if term and term not in self.terms:
                return httpx.Response(404, json={"title": "Not Found", "status": 404, "detail": f"No tuition for {term}"})
            terms = [self._term_json(t) for t in self.terms if term is None or t == term]
            return httpx.Response(
                200,
                json={"studentNo": STUDENT_NO, "fullName": "Ayse Yilmaz", "term": term, "terms": terms, "totals": []},
            )
        if request.method == "POST" and path == "/api/v1/banking/payments":
            if self.payment_error:
                status, body = self.payment_error
                return httpx.Response(status, json=body)
            body = json.loads(request.content)
            t = self.terms[body["term"]]
            amount = Decimal(str(body["amount"]))
            t["paid"] += amount
            return httpx.Response(
                201,
                json={
                    "status": "SUCCESS",
                    "transactionId": body["transactionId"],
                    "studentNo": body["studentNo"],
                    "term": body["term"],
                    "paidAmount": float(amount),
                    "totalPaid": float(t["paid"]),
                    "remainingBalance": float(t["amount"] - t["paid"]),
                    "currency": t["currency"],
                    "paidAt": "2026-11-23T10:00:00Z",
                },
            )
        return httpx.Response(404, json={"message": "no Route matched with those values"})


# --- fake MCP policy server (in-process, real MCP protocol) ----------------------------------

POLICY_MATCHES = {
    "deadline": [{"section": "2.1", "heading": "Fall term", "text": "Fall tuition is due September 30, 2026."}],
    "installment": [
        {"section": "3.1", "heading": "Eligibility", "text": "Balances of at least $300 may request installments."},
        {"section": "3.4", "heading": "Partial payments versus installment approval", "text": "Not an approval."},
    ],
}


def build_fake_policy_server() -> MCPServer:
    server = MCPServer("fake-policy")
    server.queries = []  # type: ignore[attr-defined]

    @server.tool()
    def search_tuition_policies(query: str, top_k: int = 3) -> dict[str, Any]:
        """Fake search with canned matches."""
        if not query.strip():
            raise ValueError("query must not be empty")
        server.queries.append(query)  # type: ignore[attr-defined]
        matches = [m for key, ms in POLICY_MATCHES.items() if key in query.lower() for m in ms][:top_k]
        return {"document": "University_Tuition_Policies.md", "query": query, "matches": matches}

    return server


# --- fixtures ---------------------------------------------------------------------------------


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.fixture
def backend() -> FakeBackend:
    return FakeBackend()


@pytest.fixture
async def api(backend: FakeBackend):
    async with httpx.AsyncClient(base_url=GATEWAY, transport=httpx.MockTransport(backend.handler)) as http:
        yield TuitionApiClient(http)


@pytest.fixture
def policy_server() -> MCPServer:
    return build_fake_policy_server()


@pytest.fixture
def session() -> Session:
    return Session(username=STUDENT_NO, student_no=STUDENT_NO, access_token=TOKEN, expires_in=3600)


@pytest.fixture
def executor(api: TuitionApiClient, policy_server: MCPServer, session: Session) -> ToolExecutor:
    return ToolExecutor(api, PolicySearchClient(policy_server), session)


@pytest.fixture
def llm() -> FakeLLM:
    return FakeLLM()


@pytest.fixture
def agent(llm: FakeLLM, executor: ToolExecutor) -> Agent:
    return Agent(llm, executor)
