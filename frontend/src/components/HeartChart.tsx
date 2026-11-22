import { useEffect, useId, useRef, useState } from "react";
import { type ChartData, number } from "../lib/api";
import { Text, VStack } from "./ui";
export function HeartChart({ data }: { data: ChartData }) {
  const id = useId();
  const svg = useRef<SVGSVGElement>(null);
  const [compact, setCompact] = useState(false);
  useEffect(() => {
    const observer = new ResizeObserver((entries) =>
      setCompact(entries[0].contentRect.width < 520),
    );
    if (svg.current) observer.observe(svg.current);
    return () => observer.disconnect();
  }, []);
  const aggregated = data.mode === "aggregated";
  const values = aggregated
    ? data.buckets.map((b) => ({
        time: new Date(b.first_reading_at).getTime(),
        min: b.minimum_bpm,
        max: b.maximum_bpm,
        value: b.average_bpm,
      }))
    : data.points.map((p) => ({
        time: new Date(p.timestamp).getTime(),
        min: p.heart_rate,
        max: p.heart_rate,
        value: p.heart_rate,
      }));
  if (!values.length) return null;
  const width = compact ? 360 : 900;
  const right = width - 20;
  const bottom = compact ? 200 : 240;
  const low = Math.max(
    0,
    Math.floor((Math.min(...values.map((x) => x.min)) - 10) / 10) * 10,
  );
  const high =
    Math.ceil((Math.max(...values.map((x) => x.max)) + 10) / 10) * 10;
  const start = Math.min(...values.map((x) => x.time));
  const end = Math.max(...values.map((x) => x.time));
  const x = (t: number) =>
    45 +
    (end === start
      ? (right - 45) / 2
      : ((t - start) / (end - start)) * (right - 45));
  const y = (v: number) => bottom - ((v - low) / (high - low)) * (bottom - 30);
  const ticks = [low, Math.round((low + high) / 2), high];
  // Only join readings from the same recording across gaps of at most 5 minutes.
  const paths: string[] = [];
  if (!aggregated) {
    let current = "";
    data.points.forEach((p, i) => {
      const prev = data.points[i - 1];
      const join =
        prev &&
        prev.recording_id === p.recording_id &&
        new Date(p.timestamp).getTime() - new Date(prev.timestamp).getTime() <=
          300000;
      if (!join && current) {
        paths.push(current);
        current = "";
      }
      current +=
        (join ? " L " : " M ") +
        x(new Date(p.timestamp).getTime()) +
        " " +
        y(p.heart_rate);
    });
    if (current) paths.push(current);
  }
  const time = (t: number) =>
    new Intl.DateTimeFormat("en-GB", {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      timeZone: "UTC",
    }).format(t);
  return (
    <VStack gap={3}>
      <svg
        ref={svg}
        className="heart-chart"
        viewBox={`0 0 ${width} ${bottom + 45}`}
        role="img"
        aria-labelledby={id + "-title " + id + "-desc"}
      >
        <title id={id + "-title"}>Heart rate over time</title>
        <desc id={id + "-desc"}>
          {number(data.count)} readings.{" "}
          {aggregated
            ? "Each vertical line is the minimum to maximum for a time bucket; the dot is its average."
            : "Points show exact readings. Lines connect the same recording only when readings are at most five minutes apart."}{" "}
          See the readings table for exact values.
        </desc>
        {ticks.map((t) => (
          <g key={t}>
            <line
              x1="45"
              x2={right}
              y1={y(t)}
              y2={y(t)}
              className="chart-grid"
            />
            <text x="32" y={y(t) + 4} textAnchor="end">
              {t}
            </text>
          </g>
        ))}
        <text x="5" y="15">
          bpm
        </text>
        {aggregated ? (
          values.map((v, i) => (
            <g key={i}>
              <line
                x1={x(v.time)}
                x2={x(v.time)}
                y1={y(v.min)}
                y2={y(v.max)}
                className="chart-range"
              />
              <circle
                cx={x(v.time)}
                cy={y(v.value)}
                r="2.5"
                className="chart-point"
              >
                <title>
                  {time(v.time)} UTC: {v.min}–{v.max} bpm, average{" "}
                  {number(v.value)}
                </title>
              </circle>
            </g>
          ))
        ) : (
          <>
            {paths.map((d, i) => (
              <path key={i} d={d} className="chart-line" />
            ))}
            {data.points.map((p) => (
              <circle
                key={p.id}
                cx={x(new Date(p.timestamp).getTime())}
                cy={y(p.heart_rate)}
                r="3.5"
                className="chart-point"
              >
                <title>
                  {time(new Date(p.timestamp).getTime())} UTC: {p.heart_rate}{" "}
                  bpm
                </title>
              </circle>
            ))}
          </>
        )}
        <text x="45" y={bottom + 30}>
          {time(start)}
        </text>
        {start !== end && (
          <text x={right} y={bottom + 30} textAnchor="end">
            {time(end)}
          </text>
        )}
      </svg>
      <Text className="small-muted">
        {aggregated
          ? `${number(data.buckets.length)} time buckets · Range and average shown · Exact readings available below.`
          : "Exact readings · Gaps over 5 minutes and separate recordings are not connected."}{" "}
        Times in UTC.
      </Text>
    </VStack>
  );
}
