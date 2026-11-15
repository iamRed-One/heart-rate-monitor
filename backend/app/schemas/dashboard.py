"""Dashboard contracts with UTC instants and explicit chart aggregation."""

from datetime import date, datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

Source = Literal["recorded", "synthetic", "mixed", "none"]


class ReadingFilters(BaseModel):
    recording_id: UUID | None = None
    start_date: date | None = None
    end_date: date | None = None


class ReadingItem(BaseModel):
    id: int
    recording_id: UUID
    timestamp: datetime
    heart_rate: int
    source_type: Literal["recorded", "synthetic"]


class ReadingsResponse(BaseModel):
    items: list[ReadingItem]
    next_cursor: str | None


class SummaryResponse(BaseModel):
    count: int
    minimum_bpm: int | None
    maximum_bpm: int | None
    average_bpm: float | None
    start_at: datetime | None
    end_at: datetime | None
    latest_reading: ReadingItem | None
    source_type: Source


class ChartBucket(BaseModel):
    start_at: datetime
    end_at: datetime
    first_reading_at: datetime
    last_reading_at: datetime
    count: int
    minimum_bpm: int
    maximum_bpm: int
    average_bpm: float
    recording_count: int


class ChartResponse(BaseModel):
    mode: Literal["exact", "aggregated"]
    count: int
    source_type: Source
    bucket_width_seconds: float | None
    points: list[ReadingItem]
    buckets: list[ChartBucket]


class RenameRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    label: str = Field(min_length=1, max_length=100)

    @field_validator("label", mode="before")
    @classmethod
    def trim_label(cls, value):
        return value.strip() if isinstance(value, str) else value
