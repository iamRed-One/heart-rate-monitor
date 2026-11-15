"""Authentication endpoints; all ownership starts from the browser session."""

from typing import Annotated

from fastapi import APIRouter, Depends, Header, Request, Response
from sqlalchemy.orm import Session

from app.config import DatabaseSettings
from app.persistence import get_session
from app.repositories.auth import AuthRepository
from app.schemas.auth import Credentials, SessionResponse, UserResponse
from app.services import auth

router = APIRouter(prefix="/api/auth", tags=["authentication"])
DB = Annotated[Session, Depends(get_session)]


def require_origin(request: Request):
    allowed = {value.strip() for value in DatabaseSettings().allowed_origins.split(",")}
    if (
        request.headers.get("origin") not in allowed
        or request.headers.get("sec-fetch-site") == "cross-site"
    ):
        raise auth.AuthError(
            403, "origin_not_allowed", "Request origin is not allowed."
        )


def current_user(request: Request, session: DB):
    return auth.authenticate(session, request.cookies.get(auth.COOKIE_NAME))


def client_ip(request):
    # Do not trust client-supplied X-Forwarded-For without a trusted proxy policy.
    return request.client.host if request.client else "unknown"


@router.post(
    "/register",
    response_model=UserResponse,
    status_code=201,
    dependencies=[Depends(require_origin)],
)
def register(
    credentials: Credentials, request: Request, response: Response, session: DB
):
    try:
        user = auth.register(session, credentials, client_ip(request))
    except auth.AuthError:
        session.commit()  # Persist counters, including failed/duplicate attempts.
        raise
    session.commit()
    response.headers["Cache-Control"] = "no-store"
    return UserResponse(**user)


@router.post(
    "/login", response_model=SessionResponse, dependencies=[Depends(require_origin)]
)
def login(credentials: Credentials, request: Request, response: Response, session: DB):
    try:
        user, token, csrf = auth.login(
            session,
            credentials,
            client_ip(request),
            request.cookies.get(auth.COOKIE_NAME),
        )
    except auth.AuthError:
        session.commit()
        raise
    session.commit()
    response.set_cookie(
        auth.COOKIE_NAME,
        token,
        max_age=auth.SESSION_SECONDS,
        httponly=True,
        secure=DatabaseSettings().cookie_secure,
        samesite="lax",
        path="/",
    )
    response.headers["Cache-Control"] = "no-store"
    return SessionResponse(user=UserResponse(**user), csrf_token=csrf)


@router.get("/me", response_model=SessionResponse)
def me(request: Request, response: Response, user=Depends(current_user)):
    response.headers["Cache-Control"] = "no-store"
    return SessionResponse(
        user=UserResponse(**user),
        csrf_token=auth.csrf_token(request.cookies[auth.COOKIE_NAME]),
    )


@router.post("/logout", status_code=204, dependencies=[Depends(require_origin)])
def logout(
    request: Request,
    response: Response,
    session: DB,
    user=Depends(current_user),
    csrf_header: Annotated[str | None, Header(alias="X-CSRF-Token")] = None,
):
    auth.verify_csrf(user, csrf_header)
    AuthRepository(session).delete_session(
        auth.digest(request.cookies[auth.COOKIE_NAME])
    )
    session.commit()
    response.delete_cookie(
        auth.COOKIE_NAME,
        path="/",
        secure=DatabaseSettings().cookie_secure,
        httponly=True,
        samesite="lax",
    )
    response.headers["Cache-Control"] = "no-store"
