import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type MouseEvent,
  type ReactNode,
} from "react";
import {
  Routes,
  Route,
  Navigate,
  Outlet,
  NavLink,
  useLocation,
  useNavigate,
  Link,
} from "react-router-dom";
import {
  LayoutDashboard,
  Upload,
  Library,
  LogOut,
  ArrowUpRight,
  PanelLeftClose,
  PanelLeftOpen,
  Activity,
  HeartPulse,
} from "lucide-react";
import { api, setCsrf, type User } from "./lib/api";
import {
  Brand,
  Button,
  Text,
  VStack,
  Loading,
  ErrorMessage,
} from "./components/ui";
import { Home } from "./pages/Home";
import { AuthPage } from "./pages/Auth";
import { Dashboard } from "./pages/Dashboard";
import { Imports } from "./pages/Imports";
import { Recordings } from "./pages/Recordings";
import { Live } from "./pages/Live";
import { styles } from "./styles";
type Session = {
  user: User | null;
  ready: boolean;
  setUser: (user: User | null) => void;
};
const AuthContext = createContext<Session>(null!);
export const useAuth = () => useContext(AuthContext);
function Shell() {
  const { user, ready, setUser } = useAuth();
  const location = useLocation();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [sidebarMotion, setSidebarMotion] = useState(false);
  const shell = useRef<HTMLElement>(null);
  const sidebarAnimations = useRef<Animation[]>([]);
  const previousPositions = useRef<Map<string, DOMRect> | null>(null);
  const motionParts = [
    ".workspace",
    ".sidebar nav",
    ".sidebar-heading button",
    ".sidebar-bottom button",
    ".sidebar .brand svg",
  ];
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem("heart-rate.sidebar-collapsed") === "true";
    } catch {
      return false;
    }
  });
  function toggleSidebar(event: MouseEvent) {
    const animate =
      event.detail !== 0 &&
      !matchMedia("(prefers-reduced-motion: reduce)").matches;
    setSidebarMotion(animate);
    // Measure the visible positions before cancelling, so a quick reversal starts
    // where the previous transition currently is rather than jumping to its end.
    previousPositions.current = animate
      ? new Map(
          motionParts.flatMap((selector) => {
            const element = shell.current?.querySelector(selector);
            return element
              ? [[selector, element.getBoundingClientRect()] as const]
              : [];
          }),
        )
      : null;
    sidebarAnimations.current.forEach((animation) => animation.cancel());
    sidebarAnimations.current = [];
    try {
      localStorage.setItem("heart-rate.sidebar-collapsed", String(!collapsed));
    } catch {}
    setCollapsed(!collapsed);
  }
  useLayoutEffect(() => {
    const positions = previousPositions.current;
    previousPositions.current = null;
    if (!positions) return;
    for (const [selector, before] of positions) {
      const element = shell.current?.querySelector<HTMLElement>(selector);
      if (!element) continue;
      const after = element.getBoundingClientRect();
      if (!before.width || !after.width) continue;
      const dx = before.left - after.left,
        dy = before.top - after.top;
      if (Math.abs(dx) + Math.abs(dy) < 0.5) continue;
      sidebarAnimations.current.push(
        element.animate(
          [
            { transform: `translate(${dx}px, ${dy}px)` },
            { transform: "translate(0, 0)" },
          ],
          { duration: 240, easing: "cubic-bezier(0.23, 1, 0.32, 1)" },
        ),
      );
    }
  }, [collapsed]);
  useEffect(
    () => () =>
      sidebarAnimations.current.forEach((animation) => animation.cancel()),
    [],
  );
  if (!ready) return <Loading text="Checking your session…" />;
  if (!user)
    return (
      <Navigate
        to="/login"
        state={{ from: location.pathname + location.search }}
        replace
      />
    );
  async function logout() {
    setBusy(true);
    setError("");
    try {
      await api("/auth/logout", { method: "POST" });
      setCsrf("");
      setUser(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      ref={shell}
      className={
        "app-shell" +
        (collapsed ? " sidebar-collapsed" : "") +
        (sidebarMotion ? "" : " sidebar-instant")
      }
    >
      <aside className="sidebar">
        <section className="sidebar-heading">
          {collapsed ? (
            <Link
              className="brand compact-brand"
              to="/dashboard"
              aria-label="Heart Rate overview"
            >
              <Activity size={26} />
            </Link>
          ) : (
            <Brand />
          )}
          <Button
            label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            icon={
              collapsed ? (
                <PanelLeftOpen size={20} />
              ) : (
                <PanelLeftClose size={20} />
              )
            }
            isIconOnly
            tooltip={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            variant="ghost"
            onClick={toggleSidebar}
            aria-expanded={!collapsed}
            aria-controls="workspace-navigation"
          />
        </section>
        <nav id="workspace-navigation" aria-label="Workspace">
          <NavLink
            to="/dashboard"
            aria-label="Overview"
            title={collapsed ? "Overview" : undefined}
          >
            <LayoutDashboard size={19} />
            <Text className="nav-label">Overview</Text>
          </NavLink>
          <NavLink
            to="/import"
            aria-label="Import data"
            title={collapsed ? "Import data" : undefined}
          >
            <Upload size={19} />
            <Text className="nav-label">Import data</Text>
          </NavLink>
          <NavLink
            to="/recordings"
            aria-label="Recordings"
            title={collapsed ? "Recordings" : undefined}
          >
            <Library size={19} />
            <Text className="nav-label">Recordings</Text>
          </NavLink>
          <NavLink
            to="/live"
            aria-label="Live watch"
            title={collapsed ? "Live watch" : undefined}
          >
            <HeartPulse size={19} />
            <Text className="nav-label">Live watch</Text>
          </NavLink>
        </nav>
        <VStack gap={3} className="sidebar-bottom">
          <Text className="sidebar-note">
            Your readings.
            <br />A little more perspective.
          </Text>
          <Text className="account-email">{user.email}</Text>
          <Button
            label="Sign out"
            icon={<LogOut size={17} />}
            variant="secondary"
            onClick={logout}
            isLoading={busy}
            isIconOnly={collapsed}
            tooltip={collapsed ? "Sign out" : undefined}
          />
        </VStack>
      </aside>
      <section className="workspace">
        <header className="workspace-header">
          <Text>Wearable heart-rate data viewer</Text>
          <Link to="/">
            About this project <ArrowUpRight size={15} />
          </Link>
        </header>
        <main id="main-content" className="workspace-main">
          {error && <ErrorMessage message={error} />}
          <Outlet />
        </main>
        <footer className="workspace-footer">
          For exploration, not medical diagnosis. All times shown in UTC.
        </footer>
      </section>
    </section>
  );
}
function ScrollRestoration() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
    document.title =
      (pathname === "/login"
        ? "Sign in"
        : pathname === "/register"
          ? "Create account"
          : pathname === "/import"
            ? "Import data"
            : pathname === "/recordings"
              ? "Recordings"
              : pathname.startsWith("/dashboard")
                ? "Overview"
                : "Your readings, in perspective") + " · Heart Rate";
  }, [pathname]);
  return null;
}
export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [bootError, setBootError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const navigate = useNavigate();
  useEffect(() => {
    let alive = true;
    api<{ user: User; csrf_token: string }>("/auth/me")
      .then((s) => {
        if (alive) {
          setUser(s.user);
          setCsrf(s.csrf_token);
          setBootError("");
        }
      })
      .catch((e) => {
        if (alive && e.status !== 401) setBootError(e.message);
      })
      .finally(() => {
        if (alive) setReady(true);
      });
    return () => {
      alive = false;
    };
  }, [attempt]);
  useEffect(() => {
    const expired = () => {
      setUser(null);
      setCsrf("");
      navigate("/login", {
        state: { notice: "Your session ended. Sign in to continue." },
      });
    };
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, [navigate]);
  return (
    <AuthContext.Provider value={{ user, ready, setUser }}>
      <style>{styles}</style>
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <ScrollRestoration />
      {bootError && (
        <ErrorMessage
          message={bootError}
          retry={() => {
            setBootError("");
            setAttempt((x) => x + 1);
          }}
        />
      )}
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/home" element={<Home />} />
        <Route path="/login" element={<AuthPage />} />
        <Route path="/register" element={<AuthPage register />} />
        <Route element={<Shell />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/recordings/:id" element={<Dashboard />} />
          <Route path="/import" element={<Imports />} />
          <Route path="/recordings" element={<Recordings />} />
          <Route path="/live" element={<Live />} />
        </Route>
        <Route
          path="*"
          element={
            <main id="main-content" className="not-found">
              <Brand />
              <h1>That page isn’t here.</h1>
              <Link to="/">Return home</Link>
            </main>
          }
        />
      </Routes>
    </AuthContext.Provider>
  );
}
