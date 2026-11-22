import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  Link,
  useLocation,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { Selector } from "@astryxdesign/core/Selector";
import {
  api,
  dateTime,
  number,
  sourceName,
  type Recording,
  type Reading,
  type Summary,
  type ChartData,
} from "../lib/api";
import {
  PageTitle,
  VStack,
  HStack,
  Heading,
  Text,
  Button,
  ActionLink,
  Empty,
  ErrorMessage,
  Loading,
} from "../components/ui";
import { HeartChart } from "../components/HeartChart";
// Dashboard scaffold: page header, filters, summary strip, chart, exact rows.
export function Dashboard() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const recording = id ?? params.get("recording_id") ?? "";
  const start = params.get("start_date") ?? "";
  const end = params.get("end_date") ?? "";
  const [draftStart, setDraftStart] = useState(start);
  const [draftEnd, setDraftEnd] = useState(end);
  const [records, setRecords] = useState<Recording[]>([]);
  const [record, setRecord] = useState<Recording | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [chart, setChart] = useState<ChartData | null>(null);
  const [readings, setReadings] = useState<Reading[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [more, setMore] = useState(false);
  const [error, setError] = useState("");
  const [rowError, setRowError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [filterError, setFilterError] = useState("");
  const [showTable, setShowTable] = useState(true);
  const query = new URLSearchParams();
  if (recording) query.set("recording_id", recording);
  if (start) query.set("start_date", start);
  if (end) query.set("end_date", end);
  const key = query.toString();
  const activeQuery = useRef(key);
  activeQuery.current = key;
  useEffect(() => {
    setDraftStart(start);
    setDraftEnd(end);
  }, [start, end]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setRowError("");
    const options = { signal: controller.signal };
    Promise.all([
      api<Summary>("/summary?" + key, options),
      api<ChartData>("/chart?" + key, options),
      api<{ items: Reading[]; next_cursor: string | null }>(
        "/readings?" + key + "&limit=50",
        options,
      ),
      api<{ items: Recording[] }>("/recordings?limit=100", options),
      recording
        ? api<Recording>("/recordings/" + recording, options)
        : Promise.resolve(null),
    ])
      .then(([s, c, r, list, selected]) => {
        setSummary(s);
        setChart(c);
        setReadings(r.items);
        setCursor(r.next_cursor);
        setRecords(list.items);
        setRecord(selected);
      })
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [key, recording, attempt]);
  async function loadMore() {
    if (!cursor) return;
    const captured = key;
    setMore(true);
    setRowError("");
    try {
      const result = await api<{
        items: Reading[];
        next_cursor: string | null;
      }>(
        "/readings?" +
          captured +
          "&limit=50&cursor=" +
          encodeURIComponent(cursor),
      );
      if (captured === activeQuery.current) {
        setReadings((old) => [...old, ...result.items]);
        setCursor(result.next_cursor);
      }
    } catch (e) {
      if (captured === activeQuery.current) setRowError((e as Error).message);
    } finally {
      setMore(false);
    }
  }
  function filter(e: FormEvent) {
    e.preventDefault();
    if (draftStart && draftEnd && draftStart > draftEnd) {
      setFilterError("The end date must be on or after the start date.");
      return;
    }
    setFilterError("");
    const next = new URLSearchParams(params);
    if (draftStart) next.set("start_date", draftStart);
    else next.delete("start_date");
    if (draftEnd) next.set("end_date", draftEnd);
    else next.delete("end_date");
    setParams(next);
  }
  return (
    <VStack gap={8}>
      <PageTitle
        title={record?.label ?? "A look at your rhythm."}
        description={
          id
            ? "Explore this recording. All timestamps are displayed in UTC."
            : "A clear view of your recorded readings, from the first beat to the latest import."
        }
        action={<ActionLink to="/import">Import data</ActionLink>}
      />
      {location.state?.notice && (
        <Text className="success-message" role="status">
          {location.state.notice}
        </Text>
      )}
      {id && (
        <Link className="inline-link" to="/recordings">
          Back to your recordings
        </Link>
      )}
      <form className="filters" onSubmit={filter}>
        {!id && (
          <Selector
            label="Recording"
            value={recording}
            onChange={(v) => {
              const next = new URLSearchParams(params);
              if (v) next.set("recording_id", v);
              else next.delete("recording_id");
              setParams(next);
            }}
            options={[
              { value: "", label: "All recordings" },
              ...records.map((r) => ({ value: r.id, label: r.label })),
            ]}
            hasSearch
            presentation="adaptive"
          />
        )}
        <label>
          From (UTC)
          <input
            type="date"
            value={draftStart}
            onChange={(e) => setDraftStart(e.target.value)}
          />
        </label>
        <label>
          To (UTC)
          <input
            type="date"
            value={draftEnd}
            onChange={(e) => setDraftEnd(e.target.value)}
          />
        </label>
        <Button label="Apply dates" variant="secondary" type="submit" />
        <Button
          label="Reset"
          variant="ghost"
          onClick={() => {
            setParams({});
            setDraftStart("");
            setDraftEnd("");
            setFilterError("");
          }}
        />
      </form>
      {records.length === 100 && !id && (
        <Text className="small-muted">
          Showing the 100 most recent recordings in this filter. Open older
          recordings from your collection.
        </Text>
      )}
      {filterError && <ErrorMessage message={filterError} />}{" "}
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorMessage message={error} retry={() => setAttempt((x) => x + 1)} />
      ) : summary && chart ? (
        summary.count === 0 ? (
          <Empty title="No readings in view.">
            <Text color="secondary">
              {start || end || recording
                ? "Try a different date range or recording."
                : "Import your first CSV to see your heart-rate data here."}
            </Text>
            {start || end ? (
              <Button
                label="Clear date filters"
                variant="secondary"
                onClick={() => {
                  const next = new URLSearchParams(params);
                  next.delete("start_date");
                  next.delete("end_date");
                  setParams(next);
                }}
              />
            ) : (
              <ActionLink to="/import">Import a recording</ActionLink>
            )}
          </Empty>
        ) : (
          <>
            <section className="summary-strip" aria-label="Reading summary">
              <VStack gap={2}>
                <Text color="secondary">Readings</Text>
                <Text className="stat-value">{number(summary.count)}</Text>
              </VStack>
              <VStack gap={2}>
                <Text color="secondary">Average heart rate</Text>
                <Text className="stat-value">
                  {number(summary.average_bpm)}
                  <Text as="span" className="stat-unit">
                    {" "}
                    bpm
                  </Text>
                </Text>
              </VStack>
              <VStack gap={2}>
                <Text color="secondary">Lowest</Text>
                <Text className="stat-value">
                  {number(summary.minimum_bpm)}
                  <Text as="span" className="stat-unit">
                    {" "}
                    bpm
                  </Text>
                </Text>
              </VStack>
              <VStack gap={2}>
                <Text color="secondary">Highest</Text>
                <Text className="stat-value">
                  {number(summary.maximum_bpm)}
                  <Text as="span" className="stat-unit">
                    {" "}
                    bpm
                  </Text>
                </Text>
              </VStack>
            </section>
            <section className="chart-section">
              <header className="section-heading">
                <Heading level={2}>Heart rate over time</Heading>
                <Text className="source-label">
                  {sourceName(summary.source_type)} data
                </Text>
              </header>
              <HeartChart data={chart} />
            </section>
            <section>
              <header className="section-heading">
                <VStack gap={2}>
                  <Heading level={2}>Behind the chart</Heading>
                  <Text color="secondary">
                    The exact readings, in time order.
                  </Text>
                </VStack>
                <Button
                  label={showTable ? "Hide readings" : "Show readings"}
                  variant="ghost"
                  onClick={() => setShowTable(!showTable)}
                />
              </header>
              {showTable && (
                <>
                    <Text className="table-scroll-hint">Scroll horizontally for all reading details.</Text>
                  <section
                    className="table-scroll"
                    tabIndex={0}
                    role="region"
                    aria-label="Exact readings"
                  >
                    <table>
                      <caption>
                        {readings.length} of {number(summary.count)} readings ·
                        Times shown in UTC
                      </caption>
                      <thead>
                        <tr>
                          <th>Timestamp (UTC)</th>
                          <th>Heart rate (bpm)</th>
                          <th>Source</th>
                          <th>Recording</th>
                        </tr>
                      </thead>
                      <tbody>
                        {readings.map((r) => (
                          <tr key={r.id}>
                            <td>{dateTime(r.timestamp)}</td>
                            <td>{r.heart_rate}</td>
                            <td>{sourceName(r.source_type)}</td>
                            <td>
                              <Link
                                className="inline-link"
                                to={"/recordings/" + r.recording_id}
                              >
                                {records.find((x) => x.id === r.recording_id)
                                  ?.label ??
                                  record?.label ??
                                  "View recording"}
                              </Link>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </section>
                  {rowError && <ErrorMessage message={rowError} />}{" "}
                  {cursor && (
                    <Button
                      label="Load more readings"
                      variant="secondary"
                      isLoading={more}
                      onClick={loadMore}
                    />
                  )}
                </>
              )}
            </section>
          </>
        )
      ) : null}
    </VStack>
  );
}
