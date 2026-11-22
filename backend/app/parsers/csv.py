"""Canonical CSV validation; no network, database or wearable dependencies."""

import csv
import io
import re
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Protocol

MAX_FILE_BYTES = 5 * 1024 * 1024
MAX_ROWS = 10_000
STAMP = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$"
)


class CsvValidationError(Exception):
    def __init__(self, errors, total_errors=None, status=422):
        self.errors = errors[:50]
        self.total_errors = total_errors if total_errors is not None else len(errors)
        self.status = status


@dataclass(frozen=True)
class ParsedReading:
    timestamp: datetime
    heart_rate: int


class ImportParser(Protocol):
    def parse(self, content: bytes, now: datetime) -> list[ParsedReading]: ...


class CanonicalCsvParser:
    def parse(self, content: bytes, now: datetime) -> list[ParsedReading]:
        if len(content) > MAX_FILE_BYTES:
            raise CsvValidationError(
                [{"record": None, "field": "file", "reason": "File exceeds 5 MiB."}],
                status=413,
            )
        try:
            text = content.decode("utf-8-sig")
        except UnicodeDecodeError:
            raise CsvValidationError(
                [{"record": None, "field": "file", "reason": "Use UTF-8 encoding."}]
            ) from None
        reader = csv.reader(io.StringIO(text, newline=""), strict=True)
        errors, results, seen = [], [], set()
        error_count = 0

        def error(record, field, reason):
            nonlocal error_count
            error_count += 1
            if len(errors) < 50:
                errors.append({"record": record, "field": field, "reason": reason})

        record_number = 0
        data_count = 0
        try:
            header = next((row for row in reader if row), None)
            if header is None:
                error(1, "file", "The file is empty.")
                raise CsvValidationError(errors)
            header = [cell.strip() for cell in header]
            if len(header) != 2 or set(header) != {"timestamp", "heart_rate"}:
                error(
                    1,
                    "header",
                    "Use exactly timestamp and heart_rate headers, each once.",
                )
                raise CsvValidationError(errors)
            for record_number, row in enumerate(reader, start=2):
                if not row:
                    continue
                data_count += 1
                if data_count > MAX_ROWS:
                    error(record_number, "file", "Limit is 10,000 data records.")
                    break
                if len(row) != 2:
                    error(
                        record_number,
                        "row",
                        "Each record must have exactly two fields.",
                    )
                    continue
                values = dict(zip(header, (value.strip() for value in row)))
                stamp, bpm = None, None
                value = values["timestamp"]
                try:
                    if not STAMP.fullmatch(value):
                        raise ValueError
                    # fromisoformat normalises some invalid offsets; validate separately.
                    if not value.endswith("Z") and (
                        int(value[-5:-3]) > 23 or int(value[-2:]) > 59
                    ):
                        raise ValueError
                    stamp = datetime.fromisoformat(
                        value.replace("Z", "+00:00")
                    ).astimezone(UTC)
                    if stamp > now + timedelta(minutes=5):
                        raise ValueError
                except ValueError:
                    error(
                        record_number,
                        "timestamp",
                        "Use a valid ISO date-time with seconds and offset, up to six fractional digits, not over five minutes in the future.",
                    )
                    stamp = None
                raw_bpm = values["heart_rate"]
                significant = raw_bpm.lstrip("0") or "0"
                if (
                    re.fullmatch(r"[0-9]+", raw_bpm)
                    and len(significant) <= 3
                    and 1 <= int(significant) <= 300
                ):
                    bpm = int(significant)
                else:
                    error(
                        record_number,
                        "heart_rate",
                        "Use a whole number from 1 to 300 BPM.",
                    )
                if stamp is not None:
                    if stamp in seen:
                        error(
                            record_number,
                            "timestamp",
                            "Duplicate timestamp after UTC conversion.",
                        )
                        stamp = None
                    else:
                        seen.add(stamp)
                if stamp is not None and bpm is not None:
                    results.append(ParsedReading(stamp, bpm))
        except csv.Error:
            error(
                record_number or 1,
                "file",
                "Malformed CSV or field exceeds parser limits.",
            )
        if not data_count and not error_count:
            error(2, "file", "At least one data record is required.")
        if error_count:
            raise CsvValidationError(errors, error_count)
        return sorted(results, key=lambda row: row.timestamp)
