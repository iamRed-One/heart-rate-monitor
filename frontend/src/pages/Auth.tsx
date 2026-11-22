import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { Grid } from "@astryxdesign/core/Grid";
import { TextInput } from "@astryxdesign/core/TextInput";
import { Eye, EyeOff } from "lucide-react";
import { api, setCsrf, type User } from "../lib/api";
import {
  VStack,
  HStack,
  Heading,
  Text,
  Button,
  ErrorMessage,
} from "../components/ui";
import { useAuth } from "../App";
// The login-split scaffold uses an edge-to-edge frame at the user's request.
export function AuthPage({ register = false }: { register?: boolean }) {
  const { user, setUser, ready } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const passwordError =
    register &&
    password.length > 0 &&
    (password.length < 12 || password.length > 128)
      ? "Use between 12 and 128 characters."
      : "";
  if (ready && user) return <Navigate to="/dashboard" replace />;
  async function submit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    setError("");
    if (register && (password.length < 12 || password.length > 128)) return;
    setBusy(true);
    try {
      if (register) {
        await api("/auth/register", {
          method: "POST",
          body: JSON.stringify({ email, password }),
        });
        setPassword("");
        navigate("/login", {
          replace: true,
          state: { notice: "Account created. Sign in to open your workspace." },
        });
      } else {
        const session = await api<{ user: User; csrf_token: string }>(
          "/auth/login",
          { method: "POST", body: JSON.stringify({ email, password }) },
        );
        setCsrf(session.csrf_token);
        setUser(session.user);
        const from = location.state?.from;
        navigate(
          typeof from === "string" &&
            from.startsWith("/") &&
            !from.startsWith("//")
            ? from
            : "/dashboard",
          { replace: true },
        );
      }
    } catch (e) {
      setError((e as Error).message);
      requestAnimationFrame(() =>
        document.getElementById("auth-error")?.focus(),
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <main id="main-content" className="auth-page">
      <Grid
        columns={{ minWidth: 240, repeat: "fit" }}
        gap={0}
        align="stretch"
        className="auth-grid"
      >
        <figure className="auth-photo">
          <img
            src="/images/watch-editorial.webp"
            alt="Illustrative smartwatch with a green woven strap"
          />
          <figcaption>
            <Heading level={2}>
              Every reading.
              <br />A little more context.
            </Heading>
            <Text>Your personal collection of recorded heart-rate data.</Text>
          </figcaption>
        </figure>
        <VStack
          gap={6}
          className="auth-form-panel"
          key={register ? "register" : "login"}
        >
          <VStack gap={3}>
            <Heading level={1}>
              {register ? "Make it your own." : "Good to see you."}
            </Heading>
            <Text color="secondary">
              {register
                ? "Create an account to start your collection."
                : "Sign in to pick up where you left off."}
            </Text>
          </VStack>
          {location.state?.notice && (
            <Text className="success-message" role="status">
              {location.state.notice}
            </Text>
          )}
          <form onSubmit={submit}>
            <VStack gap={5}>
              <TextInput
                label="Email address"
                type="email"
                value={email}
                onChange={setEmail}
                htmlName="email"
                autoComplete="email"
                isRequired
                size="lg"
              />
              <VStack gap={2}>
                <TextInput
                  label="Password"
                  type={show ? "text" : "password"}
                  value={password}
                  onChange={setPassword}
                  htmlName="password"
                  autoComplete={register ? "new-password" : "current-password"}
                  isRequired
                  size="lg"
                  description={register ? "Between 12 and 128 characters." : ""}
                  status={
                    passwordError ||
                    (submitted && register && password.length < 12)
                      ? {
                          type: "error",
                          message:
                            passwordError || "Use at least 12 characters.",
                        }
                      : undefined
                  }
                />
                <HStack>
                  <Button
                    label={show ? "Hide password" : "Show password"}
                    icon={show ? <EyeOff size={16} /> : <Eye size={16} />}
                    variant="ghost"
                    size="sm"
                    onClick={() => setShow(!show)}
                  />
                </HStack>
              </VStack>
              {error && (
                <section id="auth-error" tabIndex={-1}>
                  <ErrorMessage message={error} />
                </section>
              )}
              <Button
                label={register ? "Create account" : "Sign in"}
                variant="primary"
                type="submit"
                size="lg"
                width="100%"
                isLoading={busy}
                isDisabled={!ready}
              />
            </VStack>
          </form>
          <Text color="secondary">
            {register ? "Already have an account? " : "New here? "}
            <Link
              className="inline-link"
              to={register ? "/login" : "/register"}
              onClick={() => {
                setError("");
                setSubmitted(false);
                setPassword("");
              }}
            >
              {register ? "Sign in" : "Create an account"}
            </Link>
          </Text>
          <Text className="auth-note">
            Your data stays in your account. This viewer does not provide
            medical advice.
          </Text>
        </VStack>
      </Grid>
    </main>
  );
}
