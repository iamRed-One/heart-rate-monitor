import { useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { TextInput } from "@astryxdesign/core/TextInput";
import { Upload, FileCheck2 } from "lucide-react";
import {
  api,
  ApiError,
  dateTime,
  sourceName,
  number,
  type Preview,
  type Recording,
} from "../lib/api";
import {
  PageTitle,
  VStack,
  HStack,
  Heading,
  Text,
  Button,
  ErrorMessage,
} from "../components/ui";
// The scaffolded form flow keeps its stacked sections and review-before-save frame.
export function Imports() {
  const navigate = useNavigate();
  const [label, setLabel] = useState("");
  const [device, setDevice] = useState("");
  const [source, setSource] = useState("recorded");
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [details, setDetails] = useState<string[]>([]);
  const [now, setNow] = useState(Date.now());
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  useEffect(() => {
    if (!preview) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [preview]);
  const seconds = preview
    ? Math.max(
        0,
        Math.floor((new Date(preview.expires_at).getTime() - now) / 1000),
      )
    : 0;
  function choose(candidate: File | undefined) {
    setError("");
    setDetails([]);
    if (!candidate) return;
    if (candidate.size > 5 * 1024 * 1024) {
      setError(
        "This file is larger than 5 MiB. Split it into smaller CSV files.",
      );
      return;
    }
    if (!candidate.name.toLowerCase().endsWith(".csv")) {
      setError("Choose a .csv file containing timestamps and heart rates.");
      return;
    }
    setFile(candidate);
    if (!label) setLabel(candidate.name.replace(/\.csv$/i, "").slice(0, 100));
  }
  async function upload(e: FormEvent) {
    e.preventDefault();
    setError("");
    setDetails([]);
    if (!file) {
      setError("Choose a CSV file before previewing.");
      input.current?.focus();
      return;
    }
    if (!label.trim() || label.trim().length > 100) {
      setError("Give the recording a name between 1 and 100 characters.");
      return;
    }
    setBusy(true);
    const body = new FormData();
    body.append("file", file);
    body.append("label", label.trim());
    body.append("source_type", source);
    if (device.trim()) body.append("device_label", device.trim());
    try {
      const result = await api<Preview>("/imports/preview", {
        method: "POST",
        body,
      });
      setNow(Date.now());
      setPreview(result);
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError) {
        const rows = e.data.errors ?? e.data.row_errors;
        if (Array.isArray(rows))
          setDetails(
            rows
              .map(
                (r: any) =>
                  `Row ${r.record ?? r.row ?? r.row_number ?? "—"}: ${r.message ?? r.reason ?? "Check this row."}`,
              )
              .slice(0, 50),
          );
      }
    } finally {
      setBusy(false);
    }
  }
  async function confirm() {
    if (!preview) return;
    setBusy(true);
    setError("");
    try {
      const result = await api<{ outcome: string; recording: Recording }>(
        "/imports/" + preview.preview_id + "/confirm",
        { method: "POST" },
      );
      navigate("/recordings/" + result.recording.id, {
        state: {
          notice:
            result.outcome === "created"
              ? "Recording imported. Your readings are ready to explore."
              : "This file was already imported. Here is the existing recording.",
        },
      });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function discard() {
    if (!preview) return;
    setBusy(true);
    setError("");
    try {
      await api("/imports/" + preview.preview_id, { method: "DELETE" });
      setPreview(null);
    } catch (e) {
      if (e instanceof ApiError && [404, 410].includes(e.status))
        setPreview(null);
      else setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <VStack gap={8}>
      <PageTitle
        title="A new perspective starts here."
        description="Import a recorded or synthetic CSV. Check the preview before saving."
      />
      <section className="import-layout">
        <section className="import-main">
          <ol className="steps" aria-label="Import progress">
            <li aria-current={!preview ? "step" : undefined}>
              1. Choose your file
            </li>
            <li aria-current={preview ? "step" : undefined}>
              2. Review & save
            </li>
          </ol>
          {error && <ErrorMessage message={error} />}{" "}
          {!!details.length && (
            <ul className="validation-list">
              {details.map((d, i) => (
                <li key={i}>{d}</li>
              ))}
            </ul>
          )}
          {!preview ? (
            <form onSubmit={upload}>
              <VStack gap={6}>
                <section
                  className={"drop-zone" + (dragging ? " dragging" : "")}
                  onDragOver={(e) => {
                    e.preventDefault();
                    setDragging(true);
                  }}
                  onDragLeave={() => setDragging(false)}
                  onDrop={(e) => {
                    e.preventDefault();
                    setDragging(false);
                    choose(e.dataTransfer.files[0]);
                  }}
                >
                  <Upload size={30} strokeWidth={1.5} />
                  <Heading level={2}>
                    {file ? file.name : "Bring your CSV here"}
                  </Heading>
                  <Text color="secondary">
                    {file
                        ? `${number(file.size / 1024)} KiB · File selected`
                      : "Drop a file, or choose one from your computer."}
                  </Text>
                  <label className="file-button">
                    {file ? "Choose another file" : "Choose CSV file"}
                    <input
                      ref={input}
                      type="file"
                      accept=".csv,text/csv"
                      onChange={(e) => choose(e.target.files?.[0])}
                    />
                  </label>
                  <Text className="small-muted">
                    Up to 5 MiB and 10,000 readings per file.
                  </Text>
                </section>
                <TextInput
                  label="Recording name"
                  value={label}
                  onChange={setLabel}
                  isRequired
                  description="A name you’ll recognise later. Up to 100 characters."
                />
                <fieldset className="source-field">
                  <legend>Data source</legend>
                  <label>
                    <input
                      type="radio"
                      name="source"
                      value="recorded"
                      checked={source === "recorded"}
                      onChange={() => setSource("recorded")}
                    />
                    Recorded
                    <Text as="span">Readings exported from a device.</Text>
                  </label>
                  <label>
                    <input
                      type="radio"
                      name="source"
                      value="synthetic"
                      checked={source === "synthetic"}
                      onChange={() => setSource("synthetic")}
                    />
                    Synthetic
                    <Text as="span">Example or generated test readings.</Text>
                  </label>
                </fieldset>
                <TextInput
                  label="Device name"
                  value={device}
                  onChange={setDevice}
                  isOptional
                  description="For example, the name of your watch. Up to 100 characters."
                />
                <HStack>
                  <Button
                    label="Preview readings"
                    variant="primary"
                    type="submit"
                    isLoading={busy}
                    isDisabled={!label.trim() || device.trim().length > 100}
                  />
                </HStack>
              </VStack>
            </form>
          ) : (
            <VStack gap={6}>
              <HStack gap={3} vAlign="center">
                <FileCheck2 size={28} />
                <Heading level={2}>Ready for a closer look.</Heading>
              </HStack>
              <Text>
                <strong>{preview.label}</strong> ·{" "}
                {sourceName(preview.source_type)} · {number(preview.count)}{" "}
                readings
              </Text>
              <Text color="secondary">
                {dateTime(preview.start_at)} — {dateTime(preview.end_at)} UTC
              </Text>
              <section
                className="table-scroll"
                tabIndex={0}
                role="region"
                aria-label="Preview readings"
              >
                <table>
                  <caption>Sample of validated readings</caption>
                  <thead>
                    <tr>
                      <th>Timestamp (UTC)</th>
                      <th>Heart rate (bpm)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {preview.sample_rows.map((r, i) => (
                      <tr key={i}>
                        <td>{dateTime(r.timestamp)}</td>
                        <td>{r.heart_rate}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
              <Text
                role={seconds === 0 ? "status" : undefined}
                color="secondary"
              >
                {seconds
                  ? `Preview expires in ${Math.floor(seconds / 60)}m ${seconds % 60}s. Confirm to save this recording.`
                  : "This preview has expired. Go back and preview the file again."}
              </Text>
              <HStack gap={3} wrap="wrap">
                <Button
                  label="Save recording"
                  variant="primary"
                  onClick={confirm}
                  isLoading={busy}
                  isDisabled={seconds === 0}
                />
                <Button
                  label="Back to file"
                  variant="secondary"
                  onClick={discard}
                  isDisabled={busy}
                />
              </HStack>
            </VStack>
          )}
        </section>
        <aside className="import-help">
          <Heading level={2}>A simple file format.</Heading>
          <Text color="secondary">
            Two columns, one reading per row. Timestamps need a timezone; heart
            rates must be whole numbers from 1 to 300 bpm.
          </Text>
          <pre>
            timestamp,heart_rate{"\n"}2026-11-01T08:00:00Z,72{"\n"}
            2026-11-01T08:01:00Z,75
          </pre>
          <Text className="small-muted">
            Z means UTC. Offsets such as +01:00 also work. These example values
            are synthetic.
          </Text>
          <a
            className="inline-link"
            href="/samples/synthetic-heart-rate.csv"
            download
          >
            Download a sample CSV
          </a>
        </aside>
      </section>
    </VStack>
  );
}
