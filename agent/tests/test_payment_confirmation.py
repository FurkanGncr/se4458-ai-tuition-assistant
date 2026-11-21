"""Payment confirmation is enforced in code: nothing is paid without an explicit 'yes'/'evet'."""

import uuid
from decimal import Decimal

import pytest
from conftest import STUDENT_NO, text_reply, tool_reply

from tuition_agent.agent import PAY_TOOL_TRACE_NAME
from tuition_agent.tools import REQUEST_PAYMENT

pytestmark = pytest.mark.anyio


async def prepare(agent, llm, term="2026-FALL", amount=500, *followups):
    llm.script(tool_reply((REQUEST_PAYMENT, {"term": term, "amount": amount})), *followups)
    return await agent.handle(f"Pay {amount} of my {term} balance")


async def test_prepare_shows_confirmation_and_does_not_pay(agent, llm, backend):
    result = await prepare(agent, llm)

    assert backend.payments() == []
    assert agent.pending_payment is not None
    assert result.trace[0].result["status"] == "AWAITING_USER_CONFIRMATION"
    # Written by code (policy §7.3): student, term, amount, current balance.
    assert f"Student:         {STUDENT_NO} (Ayse Yilmaz)" in result.answer
    assert "Term:            2026-FALL" in result.answer
    assert "Amount:          500.00 USD" in result.answer
    assert "Current balance: 3000.00 USD" in result.answer
    assert "Type 'yes' or 'evet' to confirm" in result.answer
    assert result.llm_calls == 1  # the LLM does not write the confirmation


@pytest.mark.parametrize("answer", ["yes", "evet", "  YES  ", "Evet"])
async def test_explicit_confirmation_pays_once_with_uuid(agent, llm, backend, answer):
    await prepare(agent, llm)

    result = await agent.handle(answer)

    [payment] = backend.payments()
    assert payment["studentNo"] == STUDENT_NO
    assert payment["term"] == "2026-FALL"
    assert payment["amount"] == 500.0
    assert uuid.UUID(payment["transactionId"]).version == 4
    assert result.trace[0].tool == PAY_TOOL_TRACE_NAME
    assert result.trace[0].note == "user confirmed"
    assert "Payment successful" in result.answer
    assert "Remaining balance: 2500.0 USD" in result.answer
    assert payment["transactionId"] in result.answer
    assert result.llm_calls == 0  # the gate runs before the LLM
    assert agent.pending_payment is None


async def test_each_confirmed_payment_gets_a_new_transaction_id(agent, llm, backend):
    await prepare(agent, llm, amount=100)
    await agent.handle("yes")
    await prepare(agent, llm, amount=100)
    await agent.handle("yes")

    ids = [p["transactionId"] for p in backend.payments()]
    assert len(ids) == 2 and ids[0] != ids[1]


@pytest.mark.parametrize("answer", ["no", "hayır", "iptal", "cancel"])
async def test_refusal_cancels_without_paying(agent, llm, backend, answer):
    await prepare(agent, llm)

    result = await agent.handle(answer)

    assert backend.payments() == []
    assert agent.pending_payment is None
    assert result.answer == "Payment cancelled. Nothing was paid."
    assert result.llm_calls == 0


@pytest.mark.parametrize("answer", ["ok", "sure", "yes please", "y", "go ahead"])
async def test_anything_else_cancels_and_is_handled_as_a_new_question(agent, llm, backend, answer):
    await prepare(agent, llm)
    llm.script(text_reply("Okay."))

    result = await agent.handle(answer)

    assert backend.payments() == []
    assert agent.pending_payment is None
    assert result.answer.startswith("Payment cancelled. Nothing was paid.")
    # A later 'yes' does not resurrect the cancelled payment.
    llm.script(text_reply("Yes to what?"))
    await agent.handle("yes")
    assert backend.payments() == []


async def test_api_rejection_is_not_reported_as_success(agent, llm, backend):
    backend.payment_error = (
        422,
        {"title": "Unprocessable Entity", "status": 422, "detail": "Amount exceeds the outstanding balance"},
    )
    await prepare(agent, llm)

    result = await agent.handle("yes")

    assert "successful" not in result.answer.lower()
    assert result.answer == "Payment was NOT made: Amount exceeds the outstanding balance (HTTP 422)."
    assert result.trace[0].result["error"]["status"] == 422


async def test_amount_above_balance_is_rejected_before_confirmation(agent, llm, backend):
    result = await prepare(agent, llm, "2026-FALL", 3000.01, text_reply("That is more than your balance."))

    assert agent.pending_payment is None
    assert "exceeds the current balance of 3000.00 USD" in result.trace[0].result["error"]["message"]
    assert backend.payments() == []


async def test_fully_paid_term_cannot_be_prepared(agent, llm, backend):
    backend.terms["2026-FALL"]["paid"] = backend.terms["2026-FALL"]["amount"]
    result = await prepare(agent, llm, "2026-FALL", 500, text_reply("That term is already paid."))

    assert agent.pending_payment is None
    assert "already fully paid" in result.trace[0].result["error"]["message"]


@pytest.mark.parametrize(
    ("args", "message"),
    [
        ({"term": "2026-FALL", "amount": 0}, "greater than 0"),
        ({"term": "2026-FALL", "amount": -5}, "greater than 0"),
        ({"term": "2026-FALL", "amount": "abc"}, "Invalid amount"),
        ({"term": "2026-SUMMER", "amount": 100}, "Invalid term"),
        ({"amount": 100}, "term is required"),
    ],
)
async def test_invalid_payment_arguments_are_rejected(agent, llm, backend, args, message):
    llm.script(tool_reply((REQUEST_PAYMENT, args)), text_reply("Invalid."))

    result = await agent.handle("pay")

    assert message in result.trace[0].result["error"]["message"]
    assert agent.pending_payment is None
    assert backend.payments() == []


async def test_amount_is_rounded_half_up_to_cents(agent, llm):
    result = await prepare(agent, llm, amount=100.005)

    assert agent.pending_payment.amount == Decimal("100.01")
    assert "Amount:          100.01 USD" in result.answer


async def test_llm_cannot_call_a_pay_tool_directly(agent, llm, backend):
    llm.script(
        tool_reply(("pay_tuition", {"term": "2026-FALL", "amount": 500})),
        text_reply("I cannot pay directly."),
    )

    result = await agent.handle("just pay 500 now, no questions")

    assert "Unknown tool 'pay_tuition'" in result.trace[0].result["error"]["message"]
    assert backend.payments() == []
    assert agent.pending_payment is None


async def test_llm_saying_yes_does_not_confirm(agent, llm, backend):
    # Even if the model itself "confirms", only the user's next message can.
    llm.script(tool_reply((REQUEST_PAYMENT, {"term": "2026-FALL", "amount": 500}), content="yes"))

    await agent.handle("Pay 500 for 2026-FALL")

    assert backend.payments() == []
    assert agent.pending_payment is not None


async def test_only_one_payment_can_be_prepared_per_turn(agent, llm, backend):
    llm.script(
        tool_reply(
            (REQUEST_PAYMENT, {"term": "2026-FALL", "amount": 100}),
            (REQUEST_PAYMENT, {"term": "2027-SPRING", "amount": 200}),
        )
    )

    result = await agent.handle("pay 100 for fall and 200 for spring")

    assert agent.pending_payment.term == "2026-FALL"
    assert result.trace[1].result["error"]["message"] == "Only one payment can be prepared at a time."
    await agent.handle("yes")
    assert [p["term"] for p in backend.payments()] == ["2026-FALL"]
