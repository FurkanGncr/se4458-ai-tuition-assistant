# syntax=docker/dockerfile:1
# Build: docker compose build mcp-server
# The policy document comes from the "policy" build context (the repo's docs/ folder),
# so there is a single copy of it in the repository.

FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:0.13.9 /uv /usr/local/bin/uv

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    UV_PROJECT_ENVIRONMENT=/app/.venv
WORKDIR /app

# Dependencies first so they are cached between source changes.
COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --frozen --no-dev --no-install-project

COPY src/ src/
RUN uv sync --frozen --no-dev --no-editable

COPY --from=policy University_Tuition_Policies.md /app/data/University_Tuition_Policies.md

RUN useradd --system --uid 10001 --no-create-home appuser
USER appuser

ENV PATH="/app/.venv/bin:$PATH" \
    POLICY_DOC_PATH=/app/data/University_Tuition_Policies.md \
    MCP_HOST=0.0.0.0 \
    MCP_PORT=8000
EXPOSE 8000
# The BM25 index is built once at startup, before the server accepts requests.
CMD ["policy-mcp"]
