"""OpenAI adapter with a fake SDK client (no network), and settings loading."""

from types import SimpleNamespace

import openai
import pytest

from tuition_agent.config import DEFAULT_MODEL, ConfigError, load_settings
from tuition_agent.llm import LLMError, OpenAIChatLLM

pytestmark = pytest.mark.anyio


class FakeCompletions:
    def __init__(self, response=None, error=None):
        self.kwargs = None
        self._response = response
        self._error = error

    async def create(self, **kwargs):
        self.kwargs = kwargs
        if self._error:
            raise self._error
        return self._response


def fake_client(completions):
    return SimpleNamespace(chat=SimpleNamespace(completions=completions))


def response(content=None, tool_calls=None):
    message = SimpleNamespace(content=content, refusal=None, tool_calls=tool_calls)
    return SimpleNamespace(
        choices=[SimpleNamespace(message=message)],
        usage=SimpleNamespace(prompt_tokens=120, completion_tokens=15),
    )


async def test_tool_calls_are_mapped():
    call = SimpleNamespace(
        id="call_1", type="function", function=SimpleNamespace(name="get_my_tuition", arguments='{"term": null}')
    )
    completions = FakeCompletions(response(tool_calls=[call]))
    llm = OpenAIChatLLM(fake_client(completions), "gpt-6-luna", "none")

    reply = await llm.complete([{"role": "user", "content": "balance?"}], [{"type": "function"}])

    assert reply.content is None
    assert [(c.id, c.name, c.arguments) for c in reply.tool_calls] == [("call_1", "get_my_tuition", '{"term": null}')]
    assert (reply.usage.input_tokens, reply.usage.output_tokens) == (120, 15)
    assert completions.kwargs["model"] == "gpt-6-luna"
    assert completions.kwargs["reasoning_effort"] == "none"
    assert completions.kwargs["tools"] == [{"type": "function"}]


async def test_reasoning_effort_is_omitted_when_not_set():
    completions = FakeCompletions(response(content="Hi"))

    reply = await OpenAIChatLLM(fake_client(completions), "some-model", None).complete([], [])

    assert reply.content == "Hi"
    assert reply.tool_calls == []
    assert "reasoning_effort" not in completions.kwargs


async def test_sdk_errors_become_llm_errors():
    completions = FakeCompletions(error=openai.APIConnectionError(request=None))

    with pytest.raises(LLMError):
        await OpenAIChatLLM(fake_client(completions), "m", None).complete([], [])


def test_settings_defaults(tmp_path):
    settings = load_settings(env_file=None, environ={"OPENAI_API_KEY": "sk-test"})

    assert settings.openai_model == DEFAULT_MODEL
    assert settings.reasoning_effort == "none"
    assert settings.gateway_url == "http://localhost:8090"
    assert settings.mcp_url == "http://localhost:8091/mcp"


def test_settings_from_env_file_and_overrides(tmp_path):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "OPENAI_API_KEY=sk-file\nOPENAI_MODEL=gpt-5.6-luna\nOPENAI_REASONING_EFFORT=\n"
        "AGENT_GATEWAY_URL=https://gw.example/\nJWT_SECRET=not-for-the-agent\n"
    )

    settings = load_settings(env_file=env_file, environ={"OPENAI_MODEL": "from-env"})

    assert settings.openai_api_key == "sk-file"
    assert settings.openai_model == "from-env"  # real env wins
    assert settings.reasoning_effort is None  # empty means "do not send"
    assert settings.gateway_url == "https://gw.example"
    assert not hasattr(settings, "jwt_secret")


def test_missing_api_key_is_a_config_error():
    with pytest.raises(ConfigError, match="OPENAI_API_KEY"):
        load_settings(env_file=None, environ={})
