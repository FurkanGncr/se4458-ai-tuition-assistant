"""Agent settings from environment variables, with the repo root .env file as a fallback.

Only the variables below are read from .env; other entries there (DB password, JWT secret)
are not loaded into the agent process.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import dotenv_values

REPO_ROOT_ENV = Path(__file__).resolve().parents[3] / ".env"

DEFAULT_MODEL = "gpt-6-luna"
# gpt-6-luna supports function calling in Chat Completions only with reasoning_effort "none".
DEFAULT_REASONING_EFFORT = "none"
DEFAULT_GATEWAY_URL = "http://localhost:8090"
DEFAULT_MCP_URL = "http://localhost:8091/mcp"


class ConfigError(Exception):
    """A required setting is missing."""


@dataclass(frozen=True)
class Settings:
    openai_api_key: str
    openai_model: str
    reasoning_effort: str | None  # None: the parameter is not sent
    gateway_url: str
    mcp_url: str


def load_settings(env_file: Path | None = REPO_ROOT_ENV, environ: dict[str, str] | None = None) -> Settings:
    """Real environment variables win over values in the .env file."""
    file_values = dotenv_values(env_file) if env_file is not None and env_file.is_file() else {}
    env = os.environ if environ is None else environ

    def get(name: str, default: str = "") -> str:
        value = env.get(name)
        if value is None:
            value = file_values.get(name)
        return default if value is None else value.strip()

    api_key = get("OPENAI_API_KEY")
    if not api_key:
        raise ConfigError("OPENAI_API_KEY is not set. Add it to the repo root .env file (see .env.example).")

    return Settings(
        openai_api_key=api_key,
        openai_model=get("OPENAI_MODEL") or DEFAULT_MODEL,
        # An explicitly empty OPENAI_REASONING_EFFORT means "do not send the parameter".
        reasoning_effort=get("OPENAI_REASONING_EFFORT", DEFAULT_REASONING_EFFORT) or None,
        gateway_url=(get("AGENT_GATEWAY_URL") or DEFAULT_GATEWAY_URL).rstrip("/"),
        mcp_url=get("AGENT_MCP_URL") or DEFAULT_MCP_URL,
    )
