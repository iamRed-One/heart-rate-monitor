import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { TextInput } from "@astryxdesign/core/TextInput";
import { Dialog, DialogHeader } from "@astryxdesign/core/Dialog";
import { Pencil, Trash2, ArrowUpRight } from "lucide-react";
import { api, dateTime, number, sourceName, type Recording } from "../lib/api";
import {
  ActionLink,
  PageTitle,
  VStack,
  HStack,
  Text,
  Button,
  Empty,
  Loading,
  ErrorMessage,
} from "../components/ui";
// table-page scaffold: page header, dense data rows and pagination footer.
export function Recordings() {
  const [items, setItems] = useState<Recording[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [target, setTarget] = useState<{
    record: Recording;
    mode: "rename" | "delete";
  } | null>(null);
  const [label, setLabel] = useState("");
  const [dialogError, setDialogError] = useState("");
  async function load(next?: string) {
    setLoading(true);
    setError("");
    try {
      const result = await api<{
        items: Recording[];
        next_cursor: string | null;
      }>(
        "/recordings?limit=20" +
          (next ? "&cursor=" + encodeURIComponent(next) : ""),
      );
      setItems((old) => (next ? [...old, ...result.items] : result.items));
      setCursor(result.next_cursor);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  function open(record: Recording, mode: "rename" | "delete") {
    setTarget({ record, mode });
    setLabel(record.label);
    setDialogError("");
  }
  async function change(e: FormEvent) {
    e.preventDefault();
    if (!target) return;
    setBusy(true);
    setDialogError("");
    try {
      if (target.mode === "delete") {
        await api("/recordings/" + target.record.id, { method: "DELETE" });
        setItems((old) => old.filter((x) => x.id !== target.record.id));
        setNotice("Recording deleted.");
      } else {
        const updated = await api<Recording>(
          "/recordings/" + target.record.id,
          { method: "PATCH", body: JSON.stringify({ label: label.trim() }) },
        );
        setItems((old) => old.map((x) => (x.id === updated.id ? updated : x)));
        setNotice("Recording renamed.");
      }
      setTarget(null);
    } catch (e) {
      setDialogError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <VStack gap={8}>
      <PageTitle
        title="Your collection."
        description="Every import, kept in one place. Open a recording to see its readings."
        action={<ActionLink to="/import">Import data</ActionLink>}
      />
      {notice && (
        <Text className="success-message" role="status">
          {notice}
        </Text>
      )}
      {error && <ErrorMessage message={error} retry={() => void load()} />}{" "}
      {!items.length && loading ? (
        <Loading text="Loading your recordings…" />
      ) : !items.length && !error ? (
        <Empty title="Your first recording belongs here.">
          <Text color="secondary">
            Import a CSV to start building your collection.
          </Text>
          <ActionLink to="/import">Import a recording</ActionLink>
        </Empty>
      ) : (
        <>
            <Text className="table-scroll-hint">Scroll horizontally for recording details and actions.</Text>
          <section
            className="table-scroll"
            tabIndex={0}
            role="region"
            aria-label="Your recordings"
          >
            <table className="recordings-table">
              <caption>
                {items.length} loaded recording{items.length === 1 ? "" : "s"} ·
                Times shown in UTC
              </caption>
              <thead>
                <tr>
                  <th>Recording</th>
                  <th>Recorded on</th>
                  <th>Readings</th>
                  <th>Average</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <Link
                        className="recording-link"
                        to={"/recordings/" + r.id}
                      >
                        {r.label}
                        <ArrowUpRight size={15} />
                      </Link>
                      <Text className="small-muted">
                        {sourceName(r.source_type)}
                        {r.device_label ? " · " + r.device_label : ""}
                      </Text>
                    </td>
                    <td>{dateTime(r.start_at)}</td>
                    <td>{number(r.count)}</td>
                    <td>{number(r.average_bpm)} bpm</td>
                    <td>
                      <HStack gap={1}>
                        <Button
                          label={"Rename " + r.label}
                          icon={<Pencil size={16} />}
                          isIconOnly
                          tooltip="Rename recording"
                          variant="ghost"
                          onClick={() => open(r, "rename")}
                        />
                        <Button
                          label={"Delete " + r.label}
                          icon={<Trash2 size={16} />}
                          isIconOnly
                          tooltip="Delete recording"
                          variant="ghost"
                          onClick={() => open(r, "delete")}
                        />
                      </HStack>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
          {cursor && (
            <HStack>
              <Button
                label="Load more recordings"
                variant="secondary"
                onClick={() => void load(cursor)}
                isLoading={loading}
              />
            </HStack>
          )}
        </>
      )}
      <Dialog
        isOpen={!!target}
        onOpenChange={(v) => {
          if (!v && !busy) setTarget(null);
        }}
        purpose="form"
        width="30rem"
      >
        <DialogHeader
          title={
            target?.mode === "delete"
              ? "Delete this recording?"
              : "Rename recording"
          }
          onOpenChange={(v) => {
            if (!v && !busy) setTarget(null);
          }}
        />
        <form onSubmit={change}>
          <VStack gap={5}>
            {target?.mode === "delete" ? (
              <Text>
                “{target.record.label}” and all {number(target.record.count)}{" "}
                readings will be permanently deleted. This cannot be undone.
              </Text>
            ) : (
              <TextInput
                label="Recording name"
                value={label}
                onChange={setLabel}
                isRequired
                description="Between 1 and 100 characters."
              />
            )}
            {dialogError && <ErrorMessage message={dialogError} />}
            <HStack gap={3}>
              <Button
                label="Cancel"
                variant="secondary"
                onClick={() => setTarget(null)}
                isDisabled={busy}
              />
              <Button
                label={
                  target?.mode === "delete" ? "Delete recording" : "Save name"
                }
                variant={target?.mode === "delete" ? "destructive" : "primary"}
                type="submit"
                isLoading={busy}
                isDisabled={
                  target?.mode === "rename" &&
                  (!label.trim() || label.trim().length > 100)
                }
              />
            </HStack>
          </VStack>
        </form>
      </Dialog>
    </VStack>
  );
}
