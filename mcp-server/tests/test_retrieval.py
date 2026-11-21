"""Retrieval tests: three distinct policy questions plus edge cases."""

import pytest

from policy_mcp.search import PolicyIndex


def sections(response) -> list[str]:
    return [m.section for m in response.matches]


# --- The three distinct retrieval queries (assignment: "Demonstrate three distinct retrieval queries") ---


def test_query_1_payment_deadline_returns_deadline_sections(index: PolicyIndex):
    response = index.search("When is the payment deadline?")

    top = sections(response)
    assert len(top) == 3
    assert top[0].startswith("2.")
    assert {"2.1", "2.2"} <= set(top)
    fall = next(m for m in response.matches if m.section == "2.1")
    assert "September 30, 2026" in fall.text


def test_query_2_installments_returns_installment_policy(index: PolicyIndex):
    response = index.search("Can I pay my balance in installments?")

    top = sections(response)
    assert top[0] in {"3.1", "3.2", "3.3", "3.4", "8.2"}
    assert "3.2" in top
    assert all(s.startswith("3.") or s == "8.2" for s in top)


def test_query_3_refund_after_withdrawal_returns_refund_sections(index: PolicyIndex):
    response = index.search("refund after withdrawal")

    top = sections(response)
    assert all(s.startswith("6.") for s in top)
    assert "6.1" in top
    early = next(m for m in response.matches if m.section == "6.1")
    assert "80%" in early.text
    assert early.parent_heading == "Refunds and withdrawals"


# --- Response shape and edge cases ---


def test_response_has_policy_section_9_shape(index: PolicyIndex):
    response = index.search("late fee").model_dump()

    assert response["document"] == "University_Tuition_Policies.md"
    assert response["document_id"] == "UNI-TUITION-2026-01"
    assert response["query"] == "late fee"
    first = response["matches"][0]
    assert set(first) == {"section", "heading", "parent_heading", "text", "score"}
    assert first["section"] == "4.1"
    scores = [m["score"] for m in response["matches"]]
    assert scores == sorted(scores, reverse=True)


def test_top_k_limits_the_number_of_matches(index: PolicyIndex):
    assert len(index.search("installments", top_k=1).matches) == 1
    assert len(index.search("installments", top_k=5).matches) == 5


def test_unrelated_query_returns_no_matches(index: PolicyIndex):
    response = index.search("pizza menu")

    assert response.matches == []


def test_word_forms_match_through_stemming(index: PolicyIndex):
    # "withdrawals" in the query matches "withdraws" in section 6.1.
    assert "6.1" in sections(index.search("withdrawals"))


@pytest.mark.parametrize("query", ["", "   "])
def test_empty_query_is_rejected(index: PolicyIndex, query: str):
    with pytest.raises(ValueError, match="query must not be empty"):
        index.search(query)


@pytest.mark.parametrize("top_k", [0, 6])
def test_top_k_out_of_range_is_rejected(index: PolicyIndex, top_k: int):
    with pytest.raises(ValueError, match="top_k"):
        index.search("deadline", top_k=top_k)


def test_index_is_built_once_and_not_per_query(index: PolicyIndex, monkeypatch):
    from policy_mcp import search as search_module

    def fail(*args, **kwargs):
        raise AssertionError("the BM25 index must not be rebuilt for a query")

    monkeypatch.setattr(search_module, "BM25", fail)
    monkeypatch.setattr(search_module, "chunk_file", fail)

    assert index.search("payment deadline").matches
    assert index.search("refund").matches
