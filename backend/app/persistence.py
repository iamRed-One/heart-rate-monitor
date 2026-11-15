"""Lazy PostgreSQL engine and request transaction dependencies."""

from functools import lru_cache

from sqlalchemy import URL, create_engine
from sqlalchemy.orm import Session

from app.config import DatabaseSettings


def database_url(settings: DatabaseSettings) -> URL:
    return URL.create(
        "postgresql+psycopg",
        username=settings.postgres_user,
        password=settings.postgres_password.get_secret_value(),
        host=settings.postgres_host,
        port=settings.postgres_port,
        database=settings.postgres_db,
    )


@lru_cache
def get_engine():
    return create_engine(
        database_url(DatabaseSettings()),
        pool_pre_ping=True,
        pool_size=5,
        max_overflow=5,
        pool_timeout=5,
        hide_parameters=True,
        connect_args={
            "connect_timeout": 3,
            "options": "-c timezone=UTC -c statement_timeout=5000",
        },
    )


def get_session():
    with Session(get_engine()) as session:
        yield session
