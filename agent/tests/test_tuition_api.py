"""REST client against a mocked gateway: paths, headers, bodies and both error formats."""

import json
from decimal import Decimal

import httpx
import pytest
from conftest import GATEWAY

from tuition_agent.tuition_api import ApiError, TuitionApiClient

pytestmark = pytest.mark.anyio


def client_for(handler) -> TuitionApiClient:
    return TuitionApiClient(httpx.AsyncClient(base_url=GATEWAY, transport=httpx.MockTransport(handler)))


async def test_login_posts_credentials_without_a_token():
    seen = {}

    def handler(request):
        seen["request"] = request
        return httpx.Response(200, json={"accessToken": "t", "role": "STUDENT", "studentNo": "20230001"})

    body = await client_for(handler).login("20230001", "Student123!")

    assert body["accessToken"] == "t"
    request = seen["request"]
    assert request.method == "POST"
    assert request.url.path == "/api/v1/auth/login"
    assert json.loads(request.content) == {"username": "20230001", "password": "Student123!"}
    assert "Authorization" not in request.headers


async def test_get_tuition_sends_bearer_token_and_term():
    seen = {}

    def handler(request):
        seen["request"] = request
        return httpx.Response(200, json={"studentNo": "20230001", "terms": [], "totals": []})

    await client_for(handler).get_tuition("tok", "20230001", "2026-FALL")

    request = seen["request"]
    assert request.url.path == "/api/v1/banking/tuition/20230001"
    assert request.url.params["term"] == "2026-FALL"
    assert request.headers["Authorization"] == "Bearer tok"


async def test_pay_sends_the_payment_body():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return httpx.Response(201, json={"status": "SUCCESS"})

    await client_for(handler).pay("tok", "20230001", "2026-FALL", Decimal("1234.56"), "tx-1")

    assert seen["body"] == {"studentNo": "20230001", "term": "2026-FALL", "amount": 1234.56, "transactionId": "tx-1"}


async def test_rfc7807_error_is_parsed():
    def handler(request):
        return httpx.Response(
            409,
            headers={"Content-Type": "application/problem+json"},
            json={"type": "urn:tuition:problem:duplicate-transaction", "title": "Conflict", "status": 409, "detail": "Duplicate"},
        )

    with pytest.raises(ApiError) as exc:
        await client_for(handler).pay("tok", "20230001", "2026-FALL", Decimal("1"), "tx-1")

    assert (exc.value.status, exc.value.title, exc.value.detail, exc.value.source) == (409, "Conflict", "Duplicate", "api")


@pytest.mark.parametrize(
    ("status", "body", "title"),
    [
        (401, {"message": "Unauthorized"}, "Unauthorized"),
        (429, {"message": "API rate limit exceeded"}, "API rate limit exceeded"),
        (401, {"exp": "token expired"}, "token expired"),
    ],
)
async def test_kong_error_is_parsed(status, body, title):
    with pytest.raises(ApiError) as exc:
        await client_for(lambda r: httpx.Response(status, json=body)).get_tuition("tok", "20230001")

    assert (exc.value.status, exc.value.title, exc.value.source) == (status, title, "gateway")


async def test_non_json_error_is_parsed():
    with pytest.raises(ApiError) as exc:
        await client_for(lambda r: httpx.Response(502, text="Bad Gateway")).get_tuition("tok", "20230001")

    assert exc.value.status == 502


async def test_unreachable_gateway_is_a_network_error():
    def handler(request):
        raise httpx.ConnectError("connection refused")

    with pytest.raises(ApiError) as exc:
        await client_for(handler).get_tuition("tok", "20230001")

    assert exc.value.status is None
    assert exc.value.source == "network"
