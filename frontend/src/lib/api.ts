export type User = { id: string; email: string; created_at: string };
export type Recording = {
  id: string;
  label: string;
  source_type: string;
  device_label: string | null;
  original_filename: string;
  imported_at: string;
  count: number;
  start_at: string;
  end_at: string;
  minimum_bpm: number;
  maximum_bpm: number;
  average_bpm: number;
};
export type Reading = {
  id: string;
  recording_id: string;
  timestamp: string;
  heart_rate: number;
  source_type: string;
};
export type Summary = {
  count: number;
  minimum_bpm: number | null;
  maximum_bpm: number | null;
  average_bpm: number | null;
  start_at: string | null;
  end_at: string | null;
  source_type: string;
};
export type Bucket = {
  start_at: string;
  end_at: string;
  first_reading_at: string;
  last_reading_at: string;
  count: number;
  minimum_bpm: number;
  maximum_bpm: number;
  average_bpm: number;
  recording_count: number;
};
export type ChartData = {
  mode: "exact" | "aggregated";
  count: number;
  source_type: string;
  points: Reading[];
  buckets: Bucket[];
  bucket_width_seconds: number | null;
};
export type Preview = {
  preview_id: string;
  expires_at: string;
  label: string;
  source_type: string;
  count: number;
  start_at: string;
  end_at: string;
  sample_rows: { timestamp: string; heart_rate: number }[];
};
let csrf = "";
export function setCsrf(value: string) {
  csrf = value;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public data: Record<string, unknown>,
  ) {
    super(
      String(
        data.message ??
          (status === 422
            ? "Check the file or form details and try again."
            : status === 401
              ? "Your session has ended. Sign in again."
              : "The request could not be completed. Try again."),
      ),
    );
  }
}
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (options.method && !["GET", "HEAD"].includes(options.method) && csrf)
    headers.set("X-CSRF-Token", csrf);
  let response: Response;
  try {
    response = await fetch("/api" + path, {
      ...options,
      headers,
      credentials: "include",
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new Error(
      "Cannot reach the API. Check that the local server is running, then retry.",
    );
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    if (response.status === 401 && !path.startsWith("/auth/"))
      window.dispatchEvent(new Event("session-expired"));
    throw new ApiError(response.status, data);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
export const dateTime = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat("en-GB", {
        dateStyle: "medium",
        timeStyle: "medium",
        timeZone: "UTC",
      }).format(new Date(value))
    : "—";
export const sourceName = (value: string) =>
  ({
    recorded: "Recorded",
    synthetic: "Synthetic",
    mixed: "Mixed sources",
    none: "No data",
  })[value] ?? value;
export const number = (value: number | null | undefined) =>
  value == null
    ? "—"
    : new Intl.NumberFormat("en-GB", { maximumFractionDigits: 1 }).format(
        value,
      );
