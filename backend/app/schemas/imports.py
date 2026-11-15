"""Import metadata and responses shared by Swagger and the future React UI."""

from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field, field_validator


class ImportMetadata(BaseModel):
    label: str = Field(min_length=1, max_length=100)
    source_type: Literal["recorded", "synthetic"]
    device_label: str | None = Field(default=None, max_length=100)

    @field_validator("label", "device_label", mode="before")
    @classmethod
    def trim(cls, value):
        return value.strip() if isinstance(value, str) else value


class ReadingResponse(BaseModel):
    timestamp: datetime
    heart_rate: int


class PreviewResponse(BaseModel):
    preview_id: UUID
    expires_at: datetime
    label: str
    source_type: Literal["recorded", "synthetic"]
    count: int
    start_at: datetime
    end_at: datetime
    sample_rows: list[ReadingResponse]


class RecordingResponse(BaseModel):
    id: UUID
    label: str
    source_type: Literal["recorded", "synthetic"]
    device_label: str | None
    original_filename: str
    imported_at: datetime
    count: int
    start_at: datetime | None
    end_at: datetime | None
    minimum_bpm: int | None
    maximum_bpm: int | None
    average_bpm: float | None


class ConfirmResponse(BaseModel):
    outcome: Literal["created", "existing_recording"]
    recording: RecordingResponse


class RecordingListResponse(BaseModel):
    items: list[RecordingResponse]
    next_cursor: str | None
