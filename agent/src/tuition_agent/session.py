"""The logged-in student. The student number used by every tool comes from here, never from the LLM."""

from __future__ import annotations

from dataclasses import dataclass

from tuition_agent.tuition_api import TuitionApiClient


class NotAStudentError(Exception):
    """The account is BANK or ADMIN; this assistant serves students only."""


class SessionExpiredError(Exception):
    """The gateway or API answered 401: the token expired or is no longer valid."""


@dataclass(frozen=True)
class Session:
    username: str
    student_no: str
    access_token: str
    expires_in: int

    def __repr__(self) -> str:  # never print the token
        return f"Session(username={self.username!r}, student_no={self.student_no!r})"


async def open_student_session(api: TuitionApiClient, username: str, password: str) -> Session:
    """Log in through the gateway. Raises ApiError (e.g. 401) or NotAStudentError."""
    body = await api.login(username, password)
    if body.get("role") != "STUDENT" or not body.get("studentNo"):
        raise NotAStudentError(
            f"'{username}' is a {body.get('role')} account. The tuition assistant is for students only."
        )
    return Session(
        username=username,
        student_no=str(body["studentNo"]),
        access_token=str(body["accessToken"]),
        expires_in=int(body.get("expiresIn", 0)),
    )
