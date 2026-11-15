"""Password verification and opaque, revocable browser sessions."""

import hashlib
import hmac
import secrets
from datetime import UTC, datetime, timedelta
from uuid import uuid4

from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

from app.repositories.auth import AuthRepository

hasher = PasswordHasher()
DUMMY_HASH = hasher.hash(secrets.token_urlsafe(32))
SESSION_SECONDS = 12 * 60 * 60
COOKIE_NAME = "hr_session"


class AuthError(Exception):
    def __init__(self, status, code, message, retry_after=None):
        self.status, self.code, self.message = status, code, message
        self.retry_after = retry_after


def digest(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def csrf_token(token: str) -> str:
    # The raw session token is HttpOnly; this derived token is exposed via /me.
    return digest("csrf:" + token)


def enforce_limit(repo, key, now, limit, seconds):
    retry = repo.consume_rate_limit(digest(key), now, limit, seconds)
    if retry:
        raise AuthError(
            429, "rate_limited", "Too many attempts. Try again later.", retry
        )


def register(session, credentials, ip):
    repo = AuthRepository(session)
    enforce_limit(repo, "register:" + ip, datetime.now(UTC), 10, 600)
    user = repo.add_user(
        {
            "id": uuid4(),
            "email": str(credentials.email),
            "password_hash": hasher.hash(credentials.password.get_secret_value()),
        }
    )
    if not user:
        raise AuthError(409, "email_unavailable", "This email is unavailable.")
    return user


def login(session, credentials, ip, old_token):
    now = datetime.now(UTC)
    repo = AuthRepository(session)
    rate_key = "login:" + str(credentials.email) + ":" + ip
    enforce_limit(repo, rate_key, now, 5, 900)
    user = repo.find_user(str(credentials.email))
    try:
        hasher.verify(
            user["password_hash"] if user else DUMMY_HASH,
            credentials.password.get_secret_value(),
        )
        valid = user is not None
    except (VerificationError, InvalidHashError):
        valid = False
    if not valid:
        raise AuthError(401, "invalid_credentials", "Invalid email or password.")
    if hasher.check_needs_rehash(user["password_hash"]):
        repo.rehash_password(
            user["id"], hasher.hash(credentials.password.get_secret_value())
        )
    repo.reset_rate_limit(digest(rate_key))
    if old_token:
        repo.delete_session(digest(old_token))
    token = secrets.token_urlsafe(32)
    csrf = csrf_token(token)
    repo.add_session(
        {
            "token_digest": digest(token),
            "user_id": user["id"],
            "csrf_digest": digest(csrf),
            "created_at": now,
            "expires_at": now + timedelta(seconds=SESSION_SECONDS),
        }
    )
    return user, token, csrf


def authenticate(session, token):
    if not token or len(token) > 256:
        raise AuthError(401, "not_authenticated", "Sign in to continue.")
    user = AuthRepository(session).find_session(digest(token), datetime.now(UTC))
    if not user:
        raise AuthError(401, "not_authenticated", "Sign in to continue.")
    return user


def verify_csrf(user, supplied):
    if (
        not supplied
        or len(supplied) > 256
        or not hmac.compare_digest(digest(supplied), user["csrf_digest"])
    ):
        raise AuthError(403, "csrf_failed", "Invalid CSRF token.")
