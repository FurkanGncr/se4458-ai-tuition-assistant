"""Tools offered to the LLM and their execution by the agent.

No tool takes a student number: every REST call uses the logged-in session's student number.
No tool pays: request_tuition_payment only prepares a payment, and the Agent submits it after
the user types an explicit confirmation.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal, InvalidOperation
from typing import Any

from tuition_agent.policy_search import PolicySearchClient, PolicySearchError
from tuition_agent.session import Session, SessionExpiredError
from tuition_agent.tuition_api import ApiError, TuitionApiClient

TERM_PATTERN = r"^\d{4}-(FALL|SPRING)$"
_TERM_RE = re.compile(TERM_PATTERN)
_CENT = Decimal("0.01")

SEARCH_POLICIES = "search_tuition_policies"
GET_MY_TUITION = "get_my_tuition"
REQUEST_PAYMENT = "request_tuition_payment"

TOOL_SCHEMAS: list[dict[str, Any]] = [
    {
        "type": "function",
        "function": {
            "name": SEARCH_POLICIES,
            "description": (
                "Search the official University Tuition Policies document (deadlines, installments, late fees, "
                "scholarships, refunds, banking/payment rules). Returns excerpts with section ids to cite. "
                "Contains no student-specific data."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Keywords or a short question, e.g. 'installment eligibility'"},
                },
                "required": ["query"],
                "additionalProperties": False,
            },
            "strict": True,
        },
    },
    {
        "type": "function",
        "function": {
            "name": GET_MY_TUITION,
            "description": (
                "Get the logged-in student's live tuition records: amount, paid amount and balance per term, "
                "plus totals per currency. Only the logged-in student's own records are available."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "term": {
                        "type": ["string", "null"],
                        "description": "A term like 2026-FALL or 2027-SPRING, or null for all terms and the overall balance",
                    },
                },
                "required": ["term"],
                "additionalProperties": False,
            },
            "strict": True,
        },
    },
    {
        "type": "function",
        "function": {
            "name": REQUEST_PAYMENT,
            "description": (
                "Prepare a tuition payment for the logged-in student. It does NOT pay: the system then shows the "
                "details and the user must type an explicit confirmation. Call it only when the user clearly "
                "instructs a payment of a specific amount; a question like 'Can I pay $500?' is not an instruction."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "term": {"type": "string", "description": "The term to pay, e.g. 2026-FALL"},
                    "amount": {"type": "number", "description": "Amount in the term's currency, e.g. 500"},
                },
                "required": ["term", "amount"],
                "additionalProperties": False,
            },
            "strict": True,
        },
    },
]

_ALLOWED_ARGS = {
    SEARCH_POLICIES: {"query"},
    GET_MY_TUITION: {"term"},
    REQUEST_PAYMENT: {"term", "amount"},
}


@dataclass(frozen=True)
class PendingPayment:
    student_no: str
    full_name: str | None
    term: str
    amount: Decimal
    currency: str
    balance: Decimal


@dataclass(frozen=True)
class ToolOutcome:
    result: dict[str, Any]  # sent back to the LLM and shown in the trace
    pending_payment: PendingPayment | None = None


class ToolArgumentError(Exception):
    pass


def _error(message: str, **extra: Any) -> ToolOutcome:
    return ToolOutcome({"error": {"message": message, **extra}})


def _money(value: Any) -> Decimal:
    return Decimal(str(value)).quantize(_CENT, rounding=ROUND_HALF_UP)


def parse_arguments(name: str, raw: str) -> dict[str, Any]:
    try:
        args = json.loads(raw or "{}")
    except ValueError as exc:
        raise ToolArgumentError("Arguments are not valid JSON") from exc
    if not isinstance(args, dict):
        raise ToolArgumentError("Arguments must be a JSON object")
    unknown = sorted(set(args) - _ALLOWED_ARGS[name])
    if unknown:
        raise ToolArgumentError(
            f"Unknown argument(s): {', '.join(unknown)}. Tools act only on the logged-in student's own records."
        )
    return args


def _parse_term(value: Any, *, required: bool) -> str | None:
    if value is None or value == "":
        if required:
            raise ToolArgumentError("term is required, e.g. 2026-FALL")
        return None
    term = str(value).strip().upper()
    if not _TERM_RE.match(term):
        raise ToolArgumentError(f"Invalid term '{value}'. Use YYYY-FALL or YYYY-SPRING, e.g. 2026-FALL")
    return term


def _parse_amount(value: Any) -> Decimal:
    if isinstance(value, bool) or not isinstance(value, (int, float, str)):
        raise ToolArgumentError("amount must be a number")
    try:
        amount = _money(value)
    except InvalidOperation as exc:
        raise ToolArgumentError(f"Invalid amount '{value}'") from exc
    if not amount.is_finite() or amount <= 0:
        raise ToolArgumentError("amount must be greater than 0")
    return amount


class ToolExecutor:
    def __init__(self, api: TuitionApiClient, policy: PolicySearchClient, session: Session) -> None:
        self._api = api
        self._policy = policy
        self.session = session

    async def execute(self, name: str, raw_arguments: str, *, allow_payment: bool = True) -> ToolOutcome:
        """Run one tool call from the LLM. Errors become an {"error": ...} result for the LLM.

        Raises SessionExpiredError on a 401, so the CLI can ask the user to log in again.
        """
        if name not in _ALLOWED_ARGS:
            return _error(f"Unknown tool '{name}'. Available tools: {', '.join(_ALLOWED_ARGS)}")
        try:
            args = parse_arguments(name, raw_arguments)
            if name == SEARCH_POLICIES:
                return await self._search(args)
            if name == GET_MY_TUITION:
                return await self._get_my_tuition(args)
            if not allow_payment:
                return _error("Only one payment can be prepared at a time.")
            return await self._request_payment(args)
        except ToolArgumentError as exc:
            return _error(str(exc))
        except ApiError as exc:
            if exc.status == 401:
                raise SessionExpiredError(exc.title) from exc
            return ToolOutcome({"error": exc.to_dict()})
        except PolicySearchError as exc:
            return _error(str(exc))

    async def _search(self, args: dict[str, Any]) -> ToolOutcome:
        query = str(args.get("query") or "").strip()
        if not query:
            raise ToolArgumentError("query must not be empty")
        return ToolOutcome(await self._policy.search(query[:500]))

    async def _get_my_tuition(self, args: dict[str, Any]) -> ToolOutcome:
        term = _parse_term(args.get("term"), required=False)
        body = await self._api.get_tuition(self.session.access_token, self.session.student_no, term)
        return ToolOutcome(body)

    async def _request_payment(self, args: dict[str, Any]) -> ToolOutcome:
        term = _parse_term(args.get("term"), required=True)
        amount = _parse_amount(args.get("amount"))
        # The current balance is read live, so the confirmation shows real numbers (policy §7.3).
        body = await self._api.get_tuition(self.session.access_token, self.session.student_no, term)
        record = next((t for t in body.get("terms", []) if t.get("term") == term), None)
        if record is None:
            return _error(f"No tuition record for {term}.")
        balance = _money(record["balance"])
        currency = str(record["currency"])
        if balance <= 0:
            return _error(f"{term} is already fully paid; there is nothing to pay.", balance=f"{balance} {currency}")
        if amount > balance:
            return _error(
                f"Amount {amount} {currency} exceeds the current balance of {balance} {currency} for {term}. "
                "Overpayments are not allowed.",
            )
        pending = PendingPayment(
            student_no=self.session.student_no,
            full_name=body.get("fullName"),
            term=term,
            amount=amount,
            currency=currency,
            balance=balance,
        )
        return ToolOutcome(
            {
                "status": "AWAITING_USER_CONFIRMATION",
                "studentNo": pending.student_no,
                "term": term,
                "amount": str(amount),
                "currency": currency,
                "currentBalance": str(balance),
                "note": "Payment NOT submitted. The user must confirm it explicitly.",
            },
            pending_payment=pending,
        )

    async def submit_payment(self, pending: PendingPayment, transaction_id: str) -> dict[str, Any]:
        """Called by the Agent only after the user's explicit confirmation. Raises ApiError on failure."""
        try:
            return await self._api.pay(
                self.session.access_token, pending.student_no, pending.term, pending.amount, transaction_id
            )
        except ApiError as exc:
            if exc.status == 401:
                raise SessionExpiredError(exc.title) from exc
            raise
