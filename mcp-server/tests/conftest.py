from pathlib import Path

import pytest

from policy_mcp.search import PolicyIndex

POLICY_DOC = Path(__file__).resolve().parents[2] / "docs" / "University_Tuition_Policies.md"


@pytest.fixture(scope="session")
def policy_doc() -> Path:
    assert POLICY_DOC.is_file(), f"policy document not found at {POLICY_DOC}"
    return POLICY_DOC


@pytest.fixture(scope="session")
def index(policy_doc: Path) -> PolicyIndex:
    """Built once for the whole test session, like the server does at startup."""
    return PolicyIndex.from_file(policy_doc)


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"
