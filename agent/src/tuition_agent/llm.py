"""LLM provider interface. The agent depends only on LLMClient, so tests can use a fake LLM."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

import openai
from openai import AsyncOpenAI


@dataclass(frozen=True)
class ToolCall:
    id: str
    name: str
    arguments: str  # JSON text, exactly as produced by the model


@dataclass(frozen=True)
class Usage:
    input_tokens: int = 0
    output_tokens: int = 0


@dataclass(frozen=True)
class LLMReply:
    content: str | None
    tool_calls: list[ToolCall] = field(default_factory=list)
    usage: Usage = Usage()


class LLMError(Exception):
    """The LLM provider failed (network, auth, quota, bad request)."""


class LLMClient(Protocol):
    async def complete(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> LLMReply: ...


class OpenAIChatLLM:
    """OpenAI Chat Completions with function calling."""

    def __init__(self, client: AsyncOpenAI, model: str, reasoning_effort: str | None = None) -> None:
        self._client = client
        self.model = model
        self.reasoning_effort = reasoning_effort

    async def complete(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> LLMReply:
        kwargs: dict[str, Any] = {"model": self.model, "messages": messages, "tools": tools}
        if self.reasoning_effort:
            kwargs["reasoning_effort"] = self.reasoning_effort
        try:
            response = await self._client.chat.completions.create(**kwargs)
        except openai.OpenAIError as exc:
            raise LLMError(f"{type(exc).__name__}: {exc}") from exc

        message = response.choices[0].message
        tool_calls = [
            ToolCall(id=tc.id, name=tc.function.name, arguments=tc.function.arguments or "{}")
            for tc in (message.tool_calls or [])
            if tc.type == "function"
        ]
        usage = response.usage
        return LLMReply(
            content=message.content or message.refusal,
            tool_calls=tool_calls,
            usage=Usage(usage.prompt_tokens, usage.completion_tokens) if usage else Usage(),
        )
