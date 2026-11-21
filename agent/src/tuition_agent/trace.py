"""Plain-text tool trace printed after every turn: tool names, inputs, results, final answer."""

from __future__ import annotations

import json
from typing import Any

from tuition_agent.agent import TurnResult

MAX_RESULT_CHARS = 900


def _compact(value: Any, limit: int | None = None) -> str:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, default=str)
    if limit is not None and len(text) > limit:
        return f"{text[:limit]}… (+{len(text) - limit} chars)"
    return text


def format_turn(result: TurnResult, max_result_chars: int = MAX_RESULT_CHARS) -> str:
    lines = ["── tool trace " + "─" * 46]
    if not result.trace:
        lines.append("  (no tools called)")
    for i, entry in enumerate(result.trace, start=1):
        note = f"  ({entry.note})" if entry.note else ""
        lines.append(f"  [{i}] {entry.tool}{note}")
        lines.append(f"      input : {_compact(entry.input)}")
        lines.append(f"      result: {_compact(entry.result, max_result_chars)}")
    lines.append("── answer " + "─" * 50)
    lines.append(result.answer)
    lines.append(f"   (llm calls: {result.llm_calls} · tokens in/out: {result.input_tokens}/{result.output_tokens})")
    return "\n".join(lines)
