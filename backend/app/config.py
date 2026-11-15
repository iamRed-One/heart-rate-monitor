"""Database settings shared with the repository-root Docker Compose .env."""

from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


PROJECT_ROOT = Path(__file__).resolve().parents[2]


class DatabaseSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
        hide_input_in_errors=True,
    )

    postgres_host: str = Field(default="127.0.0.1", min_length=1)
    postgres_port: int = Field(default=5433, ge=1, le=65535)
    postgres_user: str = Field(default="heart_rate", min_length=1)
    postgres_password: SecretStr = Field(min_length=1)
    postgres_db: str = Field(default="heart_rate_monitor", min_length=1)
