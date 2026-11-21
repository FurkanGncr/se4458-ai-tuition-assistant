"""CLI chat: log in as a student through the gateway, then chat with the assistant.

Run:  cd agent && uv run tuition-agent
Needs the stack running (docker compose up -d --build --wait) and OPENAI_API_KEY in the repo root .env.
"""

from __future__ import annotations

import getpass
import sys

import anyio
import httpx
from openai import AsyncOpenAI

from tuition_agent.agent import Agent
from tuition_agent.config import ConfigError, Settings, load_settings
from tuition_agent.llm import LLMError, OpenAIChatLLM
from tuition_agent.policy_search import PolicySearchClient
from tuition_agent.session import NotAStudentError, Session, SessionExpiredError, open_student_session
from tuition_agent.tools import ToolExecutor
from tuition_agent.trace import format_turn
from tuition_agent.tuition_api import ApiError, TuitionApiClient

EXIT_WORDS = {"exit", "quit"}
MAX_LOGIN_ATTEMPTS = 3

# REST calls go to the gateway, which is always running, so connecting is fast. In the cloud the
# Tuition API scales to zero when idle, and its cold start (container start + Spring + Flyway) can
# take 30-60 s. The read timeout is a bit longer than the gateway's upstream read_timeout (120 s,
# gateway/kong.yml), so a failed cold start shows the gateway's error instead of a client timeout.
HTTP_TIMEOUT = httpx.Timeout(130.0, connect=10.0)
COLD_START_NOTE = "Note: the first request can take up to a minute while cloud services wake up."


class LoginFailed(Exception):
    pass


async def _ask(prompt: str, *, secret: bool = False) -> str:
    reader = getpass.getpass if secret else input
    return await anyio.to_thread.run_sync(reader, prompt)


async def login(api: TuitionApiClient, gateway_url: str) -> Session:
    for _ in range(MAX_LOGIN_ATTEMPTS):
        username = (await _ask("Username (student number): ")).strip()
        password = await _ask("Password: ", secret=True)
        try:
            session = await open_student_session(api, username, password)
        except ApiError as exc:
            if exc.status is None:
                raise LoginFailed(f"Cannot reach the gateway at {gateway_url}. Is docker compose running?") from exc
            print(f"Login failed: {exc.detail or exc.title}")
            continue
        except NotAStudentError as exc:
            print(exc)
            continue
        print(f"Logged in as student {session.student_no}.")
        return session
    raise LoginFailed("Too many failed login attempts.")


async def chat(settings: Settings) -> None:
    async with httpx.AsyncClient(base_url=settings.gateway_url, timeout=HTTP_TIMEOUT) as http:
        api = TuitionApiClient(http)
        print(COLD_START_NOTE)
        session = await login(api, settings.gateway_url)
        llm = OpenAIChatLLM(
            AsyncOpenAI(api_key=settings.openai_api_key), settings.openai_model, settings.reasoning_effort
        )
        executor = ToolExecutor(api, PolicySearchClient(settings.mcp_url), session)
        agent = Agent(llm, executor)
        print(f"Model: {settings.openai_model} · gateway: {settings.gateway_url} · MCP: {settings.mcp_url}")
        print("Ask about tuition deadlines, installments, your balance or payments. Type 'exit' to quit.\n")

        while True:
            try:
                text = await _ask("You: ")
            except EOFError:
                break
            if not text.strip():
                continue
            if text.strip().lower() in EXIT_WORDS:
                break
            try:
                result = await agent.handle(text)
            except SessionExpiredError:
                print("Your session expired. Nothing was submitted. Please log in again.")
                agent.start_session(await login(api, settings.gateway_url))
                continue
            except LLMError as exc:
                print(f"LLM error, please try again: {exc}")
                continue
            print(format_turn(result))
            print()


def main() -> None:
    try:
        settings = load_settings()
    except ConfigError as exc:
        print(exc, file=sys.stderr)
        sys.exit(2)
    try:
        anyio.run(chat, settings)
    except LoginFailed as exc:
        print(exc, file=sys.stderr)
        sys.exit(1)
    except (KeyboardInterrupt, EOFError):
        print()
    print("Goodbye.")


if __name__ == "__main__":
    main()
