import {
  createContext,
  useContext,
  useEffect,
  useState,
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
    <section className="app-shell">
      <aside className="sidebar">
        <Brand />
        <nav aria-label="Workspace">
          <NavLink to="/dashboard">
            <LayoutDashboard size={19} />
            Overview
          </NavLink>
          <NavLink to="/import">
            <Upload size={19} />
            Import data
          </NavLink>
          <NavLink to="/recordings">
            <Library size={19} />
            Recordings
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
          Recorded data only. For exploration, not medical diagnosis. All times
          shown in UTC.
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
