import { useEffect, useRef, useState, type CSSProperties } from "react";
import { Heart, Link2, Radio } from "lucide-react";
import { api, ApiError, dateTime, number } from "../lib/api";
import {
  VStack,
  HStack,
  Heading,
  Text,
  Button,
  PageTitle,
  ErrorMessage,
} from "../components/ui";

type Sample = {
  sample_id: string;
  timestamp: string;
  received_at: string;
  heart_rate: number;
};
type LiveState = {
  id: string;
  status: "waiting" | "connected" | "receiving" | "stale";
  device_label: string | null;
  expires_at: string;
  last_received_at: string | null;
  samples: Sample[];
};
type Pairing = {
  id: string;
  pairing_code: string;
  pair_by: string;
  expires_at: string;
};

export function Live() {
  const [pairing, setPairing] = useState<Pairing | null>(null);
  const [session, setSession] = useState<LiveState | null>(null);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());
  const [copied, setCopied] = useState(false);
  const current = useRef<string | null>(null);
  useEffect(() => {
    let active = true;
    api<LiveState | null>("/live/current")
      .then((state) => {
        if (active && state && !current.current) {
          setSession(state);
          setPairing({
            id: state.id,
            pairing_code: "",
            pair_by: state.expires_at,
            expires_at: state.expires_at,
          });
        }
      })
      .catch((e) => {
        if (active) setError((e as Error).message);
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    if (!pairing) return;
    const controller = new AbortController();
    current.current = pairing.id;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const state = await api<LiveState>("/live/sessions/" + pairing!.id, {
          signal: controller.signal,
        });
        if (!controller.signal.aborted) {
          setSession(state);
          setError("");
        }
      } catch (e) {
        if (controller.signal.aborted) return;
        setError((e as Error).message);
        if (e instanceof ApiError && e.status === 404) {
          setPairing(null);
          setSession(null);
          return;
        }
      }
      if (!controller.signal.aborted) timer = setTimeout(poll, 1000);
    }
    void poll();
    return () => {
      controller.abort();
      clearTimeout(timer);
      current.current = null;
    };
  }, [pairing]);
  async function start() {
    setBusy(true);
    setError("");
    setCopied(false);
    try {
      const result = await api<Pairing>("/live/sessions", { method: "POST" });
      setSession(null);
      setPairing(result);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function stop() {
    if (!pairing) return;
    setBusy(true);
    try {
      await api("/live/sessions/" + pairing.id, { method: "DELETE" });
      setSession(null);
      setPairing(null);
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function copy() {
    if (!pairing) return;
    try {
      await navigator.clipboard.writeText(pairing.pairing_code);
      setCopied(true);
    } catch {
      setError("Select and copy the pairing code manually.");
    }
  }
  const last = session?.samples.at(-1);
  const age = last
    ? Math.max(
        0,
        Math.floor((now - new Date(last.received_at).getTime()) / 1000),
      )
    : null;
  const fresh =
    !!last &&
    age !== null &&
    age <= 10 &&
    !error &&
    !!pairing &&
    now < new Date(pairing.expires_at).getTime();
  const paired = !!session && session.status !== "waiting";
  return (
    <VStack gap={8}>
      <PageTitle
        title="A live look at your heart rate."
        description="ET488 → Vigil on your phone → this viewer. Only new watch measurements appear here."
        action={
          pairing ? (
            <Button
              label="End showcase"
              variant="secondary"
              onClick={stop}
              isLoading={busy}
            />
          ) : undefined
        }
      />
      {error && <ErrorMessage message={error} />}
      {!pairing ? (
        <VStack gap={5} className="live-intro">
          <Radio size={34} strokeWidth={1.5} />
          <Heading level={2}>Connect your companion.</Heading>
          <Text color="secondary">
            Wear the watch and open Vigil. Start a showcase here, then enter its
            code in Vigil’s Live heart-rate showcase screen.
          </Text>
          <HStack>
            <Button
              label="Start showcase"
              variant="primary"
              onClick={start}
              isLoading={busy}
            />
          </HStack>
          <Text className="small-muted">
            This is a temporary live view. It keeps the latest 300 samples in
            memory and ends after 30 minutes or a server restart. It does not
            save a recording.
          </Text>
        </VStack>
      ) : (
        <>
          {!paired && (
            <section className="pairing-panel">
              <VStack gap={4}>
                <HStack gap={2} vAlign="center">
                  <Link2 size={20} />
                  <Heading level={2}>Pair Vigil with this view.</Heading>
                </HStack>
                <Text>Open Vigil → Your data → Live heart-rate showcase.</Text>
                <HStack gap={4} wrap="wrap">
                  {pairing.pairing_code && (
                    <code className="pairing-code">{pairing.pairing_code}</code>
                  )}
                  <Button
                    label={
                      pairing.pairing_code
                        ? copied
                          ? "Code copied"
                          : "Copy pairing code"
                        : "Generate new code"
                    }
                    variant="secondary"
                    onClick={pairing.pairing_code ? copy : start}
                  />
                </HStack>
                <Text color="secondary">
                  Use this code once within 10 minutes. Keep it private: it
                  authorises this phone to send readings to your showcase.
                </Text>
                <details>
                  <summary>Local USB connection</summary>
                  <Text className="small-muted">
                    With USB debugging connected, run{" "}
                    <code>adb reverse tcp:8000 tcp:8000</code> on your computer.
                    Use <code>http://127.0.0.1:8000</code> as the server address
                    in the debug Vigil app. USB carries the phone-to-computer
                    traffic; Bluetooth still connects the watch to the phone.
                  </Text>
                </details>
              </VStack>
            </section>
          )}
          <section
            className={"live-reading" + (fresh ? " is-receiving" : "")}
            style={
              {
                "--beat-period": last ? `${60 / last.heart_rate}s` : "1s",
              } as CSSProperties
            }
          >
            <Heart
              className="live-heart"
              size={110}
              strokeWidth={1.3}
              aria-hidden="true"
            />
            <Text className="live-bpm">
              {last ? number(last.heart_rate) : "—"}
              <Text as="span"> bpm</Text>
            </Text>
            <Text role="status" className="live-status">
              {fresh
                ? "Receiving watch readings"
                : last
                  ? "No fresh reading — waiting for the watch"
                  : paired
                    ? "Phone paired — waiting for a watch reading"
                    : "Waiting for your phone"}
            </Text>
            <Text color="secondary">
              {session?.device_label ?? "ET488 watch"}
              {age !== null ? ` · Updated ${age}s ago` : ""}
            </Text>
            <Text className="small-muted">
              The heart is a visual illustration paced by the latest BPM, not an
              ECG or a beat-by-beat signal. It pauses when updates stop. Reduced
              motion keeps it still.
            </Text>
          </section>
          <section>
            <Heading level={2}>Readings as they arrive</Heading>
            <Text className="small-muted">
              {session?.samples.length ?? 0} samples in this temporary view.
              Recorded watch data, not synthetic readings. UTC timestamps.
            </Text>
            <section
              className="table-scroll"
              tabIndex={0}
              role="region"
              aria-label="Live readings"
            >
              <table>
                <thead>
                  <tr>
                    <th>Captured (UTC)</th>
                    <th>Heart rate (bpm)</th>
                    <th>Received (UTC)</th>
                  </tr>
                </thead>
                <tbody>
                  {session?.samples
                    .slice(-20)
                    .reverse()
                    .map((s) => (
                      <tr key={s.sample_id}>
                        <td>{dateTime(s.timestamp)}</td>
                        <td>{s.heart_rate}</td>
                        <td>{dateTime(s.received_at)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </section>
          </section>
        </>
      )}
    </VStack>
  );
}
