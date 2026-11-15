"""Boundary and format tests for the canonical CSV contract."""

import unittest
from datetime import UTC, datetime

from app.parsers.csv import MAX_FILE_BYTES, CanonicalCsvParser, CsvValidationError

NOW = datetime(2026, 11, 15, 12, tzinfo=UTC)


class CsvParserTests(unittest.TestCase):
    def parse(self, body):
        return CanonicalCsvParser().parse(body, NOW)

    def test_valid_bom_reordered_headers_quotes_and_sorting(self):
        rows = self.parse(
            b'\xef\xbb\xbfheart_rate,timestamp\r\n"78","2026-11-01T09:02:00+01:00"\r\n\r\n072,2026-11-01T08:00:00Z\r\n'
        )
        self.assertEqual([row.heart_rate for row in rows], [72, 78])
        self.assertEqual(rows[0].timestamp.hour, 8)

    def test_headers_and_structure(self):
        for data in (
            b"",
            b"timestamp,bpm\n",
            b"timestamp,timestamp\n",
            b"timestamp,heart_rate,extra\n",
            b'timestamp,heart_rate\n"unterminated',
            b"timestamp,heart_rate\n2026-11-01T08:00:00Z,72,extra",
        ):
            with self.subTest(data=data), self.assertRaises(CsvValidationError):
                self.parse(data)

    def test_bpm_bounds_and_invalid_values(self):
        for bpm in ("0", "301", "-1", "72.5", "NaN", "", "9" * 5000):
            with self.subTest(bpm=bpm[:20]), self.assertRaises(CsvValidationError):
                self.parse(
                    ("timestamp,heart_rate\n2026-11-01T08:00:00Z," + bpm).encode()
                )
        for bpm in ("1", "300"):
            self.assertEqual(
                self.parse(
                    ("timestamp,heart_rate\n2026-11-01T08:00:00Z," + bpm).encode()
                )[0].heart_rate,
                int(bpm),
            )

    def test_invalid_timestamps_future_precision_and_offset(self):
        for stamp in (
            "2026-11-01T08:00:00",
            "2026-02-30T08:00:00Z",
            "2026-11-01T08:00:00.1234567Z",
            "2026-11-01T08:00:00+01:99",
            "2026-11-15T12:05:01Z",
        ):
            with self.subTest(stamp=stamp), self.assertRaises(CsvValidationError):
                self.parse(("timestamp,heart_rate\n" + stamp + ",72").encode())
        self.assertEqual(
            len(self.parse(b"timestamp,heart_rate\n2026-11-15T12:05:00Z,72")), 1
        )

    def test_duplicate_utc_instants(self):
        with self.assertRaises(CsvValidationError) as error:
            self.parse(
                b"timestamp,heart_rate\n2026-11-01T09:00:00+01:00,72\n2026-11-01T08:00:00Z,75"
            )
        self.assertEqual(error.exception.errors[0]["record"], 3)

    def test_byte_and_row_limits_and_bounded_errors(self):
        with self.assertRaises(CsvValidationError) as error:
            self.parse(b"x" * (MAX_FILE_BYTES + 1))
        self.assertEqual(error.exception.status, 413)
        body = b"timestamp,heart_rate\n" + b"bad,bad\n" * 10001
        with self.assertRaises(CsvValidationError) as error:
            self.parse(body)
        self.assertEqual(len(error.exception.errors), 50)
        self.assertGreater(error.exception.total_errors, 50)
        # A limit-sized file is accepted by byte validation; padding is whitespace.
        base = b"timestamp,heart_rate\n2026-11-01T08:00:00Z,72"
        body = base + b" " * (MAX_FILE_BYTES - len(base))
        with self.assertRaises(CsvValidationError) as error:
            self.parse(body)
        self.assertEqual(error.exception.status, 422)  # Parser field limit, not bytes.

    def test_exact_row_limit(self):
        from datetime import timedelta

        body = "timestamp,heart_rate\n" + "".join(
            f"{(NOW - timedelta(seconds=i + 1)).isoformat()},72\n" for i in range(10000)
        )
        self.assertEqual(len(self.parse(body.encode())), 10000)
        with self.assertRaises(CsvValidationError):
            self.parse((body + "2026-10-01T00:00:00Z,72\n").encode())


if __name__ == "__main__":
    unittest.main()
