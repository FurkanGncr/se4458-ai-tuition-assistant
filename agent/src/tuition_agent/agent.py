"""The agent: LLM tool selection, tool execution, and the payment confirmation gate.

Payment safety is enforced here in code, not by the prompt:
  * the LLM has no tool that pays; request_tuition_payment only prepares a PendingPayment;
  * the confirmation text (student, term, amount, current balance) is written by this code (policy §7.3);
  * only the user's next message, if it is exactly "yes" or "evet", submits the payment;
  * the transactionId is a fresh UUID, and success is reported only from the API's answer (policy §7.4).
"""

from __future__ import annotations

import json
import uuid
from dataclasses import dataclass, field
from typing import Any

from tuition_agent.llm import LLMClient, LLMReply
from tuition_agent.session import Session
from tuition_agent.tools import TOOL_SCHEMAS, PendingPayment, ToolExecutor
from tuition_agent.tuition_api import ApiError

CONFIRM_WORDS = frozenset({"yes", "evet"})
CANCEL_WORDS = frozenset({"no", "hayır", "hayir", "cancel", "iptal"})
PAY_TOOL_TRACE_NAME = "pay_tuition"

SYSTEM_PROMPT = """You are the university's AI Tuition Assistant. You help the logged-in student {student_no} \
with tuition deadlines, installments, late fees, scholarships, refunds, payment rules, balances and payments.

Tools:
- search_tuition_policies: the official tuition policy document. Use it for every policy or rule question; \
never answer policy questions from memory.
- get_my_tuition: the logged-in student's live tuition records (amount, paid, balance per term).
- request_tuition_payment: prepares a payment that the user must then confirm; it never pays. Call it only \
when the user clearly instructs a payment of a specific amount. "Can I pay $500?" is a question, not an \
instruction. If the term is not given and more than one term has a balance, ask which term.
Use both search_tuition_policies and get_my_tuition when the answer needs the rules and the student's own \
balance (e.g. installment eligibility).

Rules:
- Base answers only on tool results. Cite policy sections like "(Policy §3.2)".
- You can access only the logged-in student's own records. If asked about another student number, refuse \
and do not call tools for it.
- Never say an installment plan is approved (§3.4). Never infer that a student has a scholarship (§5.2).
- Never say a payment succeeded; the system reports payment results itself.
- Be concise. Reply in the user's language."""


@dataclass
class TraceEntry:
    tool: str
    input: Any
    result: Any
    note: str | None = None


@dataclass
class TurnResult:
    answer: str
    trace: list[TraceEntry] = field(default_factory=list)
    llm_calls: int = 0
    input_tokens: int = 0
    output_tokens: int = 0


def _assistant_tool_message(reply: LLMReply) -> dict[str, Any]:
    return {
        "role": "assistant",
        "content": reply.content,
        "tool_calls": [
            {"id": c.id, "type": "function", "function": {"name": c.name, "arguments": c.arguments}}
            for c in reply.tool_calls
        ],
    }


def _parsed_or_raw(raw: str) -> Any:
    try:
        return json.loads(raw)
    except ValueError:
        return raw


def confirmation_text(p: PendingPayment) -> str:
    student = f"{p.student_no} ({p.full_name})" if p.full_name else p.student_no
    return (
        "Please confirm this payment:\n"
        f"  Student:         {student}\n"
        f"  Term:            {p.term}\n"
        f"  Amount:          {p.amount} {p.currency}\n"
        f"  Current balance: {p.balance} {p.currency}\n"
        f"  Balance after:   {p.balance - p.amount} {p.currency}\n"
        "Type 'yes' or 'evet' to confirm. Anything else cancels."
    )


class Agent:
    def __init__(
        self,
        llm: LLMClient,
        executor: ToolExecutor,
        *,
        max_tool_rounds: int = 5,
        history_turns: int = 6,
    ) -> None:
        self._llm = llm
        self._executor = executor
        self._max_tool_rounds = max_tool_rounds
        self._history_turns = history_turns
        self._history: list[list[dict[str, Any]]] = []  # one message list per finished turn
        self._pending: PendingPayment | None = None

    @property
    def pending_payment(self) -> PendingPayment | None:
        return self._pending

    def start_session(self, session: Session) -> None:
        """After a (re-)login: forget the conversation and any pending payment."""
        self._executor.session = session
        self._history.clear()
        self._pending = None

    async def handle(self, user_text: str) -> TurnResult:
        if self._pending is None:
            return await self._llm_turn(user_text)

        pending, self._pending = self._pending, None
        word = user_text.strip().lower()
        if word in CONFIRM_WORDS:
            return await self._submit(pending, user_text)

        cancelled = "Payment cancelled. Nothing was paid."
        if word in CANCEL_WORDS:
            self._remember([{"role": "user", "content": user_text}, {"role": "assistant", "content": cancelled}])
            return TurnResult(answer=cancelled)
        # Any other message cancels the payment and is then handled as a normal question.
        self._remember([{"role": "assistant", "content": cancelled}])
        result = await self._llm_turn(user_text)
        result.answer = f"{cancelled}\n{result.answer}"
        return result

    # --- normal turn: LLM chooses tools, the agent executes them -------------------------------

    async def _llm_turn(self, user_text: str) -> TurnResult:
        system = {"role": "system", "content": SYSTEM_PROMPT.format(student_no=self._executor.session.student_no)}
        history = [m for turn in self._history[-self._history_turns :] for m in turn]
        turn: list[dict[str, Any]] = [{"role": "user", "content": user_text}]
        result = TurnResult(answer="")

        for round_no in range(self._max_tool_rounds + 1):
            reply = await self._llm.complete([system, *history, *turn], TOOL_SCHEMAS)
            result.llm_calls += 1
            result.input_tokens += reply.usage.input_tokens
            result.output_tokens += reply.usage.output_tokens

            if not reply.tool_calls:
                result.answer = (reply.content or "").strip() or "Sorry, I have no answer to that."
                turn.append({"role": "assistant", "content": result.answer})
                self._remember(turn)
                return result
            if round_no == self._max_tool_rounds:
                break

            turn.append(_assistant_tool_message(reply))
            pending: PendingPayment | None = None
            for call in reply.tool_calls:
                outcome = await self._executor.execute(call.name, call.arguments, allow_payment=pending is None)
                result.trace.append(TraceEntry(call.name, _parsed_or_raw(call.arguments), outcome.result))
                turn.append(
                    {"role": "tool", "tool_call_id": call.id, "content": json.dumps(outcome.result, ensure_ascii=False)}
                )
                pending = pending or outcome.pending_payment

            if pending is not None:
                # The confirmation prompt comes from code, not from the LLM.
                self._pending = pending
                result.answer = confirmation_text(pending)
                turn.append({"role": "assistant", "content": result.answer})
                self._remember(turn)
                return result

        result.answer = "Sorry, I could not complete that request. Please rephrase it."
        turn.append({"role": "assistant", "content": result.answer})
        self._remember(turn)
        return result

    # --- confirmed payment ------------------------------------------------------------------------

    async def _submit(self, pending: PendingPayment, user_text: str) -> TurnResult:
        transaction_id = str(uuid.uuid4())
        request = {
            "studentNo": pending.student_no,
            "term": pending.term,
            "amount": str(pending.amount),
            "transactionId": transaction_id,
        }
        try:
            response = await self._executor.submit_payment(pending, transaction_id)
        except ApiError as exc:
            trace_result: Any = {"error": exc.to_dict()}
            if exc.status is None:
                answer = (
                    "Could not confirm the payment: the gateway did not answer. Do not assume it succeeded; "
                    "ask for your balance before trying again."
                )
            else:
                answer = f"Payment was NOT made: {exc.detail or exc.title} (HTTP {exc.status})."
        else:
            trace_result = response
            if response.get("status") == "SUCCESS":
                cur = response.get("currency", pending.currency)
                answer = (
                    f"Payment successful: {response.get('paidAmount')} {cur} for {response.get('term')}. "
                    f"Remaining balance: {response.get('remainingBalance')} {cur}. "
                    f"Transaction ID: {response.get('transactionId')}."
                )
            else:
                answer = "The API did not confirm the payment. Do not assume it succeeded; ask for your balance."

        self._remember([{"role": "user", "content": user_text}, {"role": "assistant", "content": answer}])
        return TurnResult(
            answer=answer,
            trace=[TraceEntry(PAY_TOOL_TRACE_NAME, request, trace_result, note="user confirmed")],
        )

    def _remember(self, messages: list[dict[str, Any]]) -> None:
        self._history.append(messages)
        del self._history[: -self._history_turns]
