"""Tuition REST API client. Every call goes through the Kong gateway (base URL = gateway URL)."""

from __future__ import annotations

from decimal import Decimal
from typing import Any

import httpx

LOGIN_PATH = "/api/v1/auth/login"
BANKING_TUITION_PATH = "/api/v1/banking/tuition/{student_no}"
BANKING_PAYMENTS_PATH = "/api/v1/banking/payments"


class ApiError(Exception):
    """A non-2xx answer from the API or the gateway, or no answer at all (status None)."""

    def __init__(self, status: int | None, title: str, detail: str | None = None, source: str = "api") -> None:
        super().__init__(f"{status} {title}" + (f": {detail}" if detail else ""))
        self.status = status
        self.title = title
        self.detail = detail
        self.source = source  # "api" (RFC 7807), "gateway" (Kong JSON) or "network"

    def to_dict(self) -> dict[str, Any]:
        body: dict[str, Any] = {"status": self.status, "title": self.title, "source": self.source}
        if self.detail:
            body["detail"] = self.detail
        return body


def _error_from_response(response: httpx.Response) -> ApiError:
    try:
        body = response.json()
    except ValueError:
        body = None
    if isinstance(body, dict):
        if "title" in body:  # RFC 7807 Problem Details from the Spring API
            return ApiError(response.status_code, str(body["title"]), body.get("detail"))
        if "message" in body:  # Kong's own errors, e.g. {"message": "Unauthorized"}
            return ApiError(response.status_code, str(body["message"]), source="gateway")
        if body:  # other Kong plugin errors, e.g. {"exp": "token expired"}
            return ApiError(response.status_code, str(next(iter(body.values()))), source="gateway")
    return ApiError(response.status_code, response.reason_phrase or "HTTP error", response.text[:200] or None)


def _amount_to_json(amount: Decimal) -> float:
    # The amount has at most 2 decimals; float repr keeps those digits exactly, and the API parses
    # the JSON number text into a BigDecimal.
    return float(amount)


class TuitionApiClient:
    def __init__(self, http: httpx.AsyncClient) -> None:
        """`http` must have base_url set to the gateway URL."""
        self._http = http

    async def _send(self, method: str, path: str, *, token: str | None = None, **kwargs: Any) -> dict[str, Any]:
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        try:
            response = await self._http.request(method, path, headers=headers, **kwargs)
        except httpx.HTTPError as exc:
            raise ApiError(None, "Gateway unreachable", f"{type(exc).__name__}: {exc}", source="network") from exc
        if response.is_success:
            return response.json()
        raise _error_from_response(response)

    async def login(self, username: str, password: str) -> dict[str, Any]:
        return await self._send("POST", LOGIN_PATH, json={"username": username, "password": password})

    async def get_tuition(self, token: str, student_no: str, term: str | None = None) -> dict[str, Any]:
        params = {"term": term} if term else None
        return await self._send("GET", BANKING_TUITION_PATH.format(student_no=student_no), token=token, params=params)

    async def pay(
        self, token: str, student_no: str, term: str, amount: Decimal, transaction_id: str
    ) -> dict[str, Any]:
        body = {
            "studentNo": student_no,
            "term": term,
            "amount": _amount_to_json(amount),
            "transactionId": transaction_id,
        }
        return await self._send("POST", BANKING_PAYMENTS_PATH, token=token, json=body)
