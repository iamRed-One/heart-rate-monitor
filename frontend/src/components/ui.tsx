import { type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Activity, ArrowRight, AlertCircle } from "lucide-react";
import { VStack, HStack } from "@astryxdesign/core/Layout";
import { Heading } from "@astryxdesign/core/Heading";
import { Text } from "@astryxdesign/core/Text";
import { Button } from "@astryxdesign/core/Button";
export { VStack, HStack, Heading, Text, Button };
export function Brand() {
  return (
    <Link className="brand" to="/" aria-label="Heart Rate home">
      <Activity size={26} strokeWidth={1.8} />
      Heart Rate
      <Text as="span" className="brand-dot">
        .
      </Text>
    </Link>
  );
}
export function ActionLink({
  to,
  children,
  secondary = false,
}: {
  to: string;
  children: ReactNode;
  secondary?: boolean;
}) {
  return (
    <Link className={"action-link" + (secondary ? " secondary" : "")} to={to}>
      {children}
      <ArrowRight size={18} />
    </Link>
  );
}
export function ErrorMessage({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <section role="alert" className="error-message">
      <HStack gap={2} vAlign="center">
        <AlertCircle size={18} />
        <Text>{message}</Text>
      </HStack>
      {retry && (
        <Button label="Try again" variant="secondary" onClick={retry} />
      )}
    </section>
  );
}
export function Loading({
  text = "Loading your readings…",
}: {
  text?: string;
}) {
  return (
    <section className="loading" role="status">
      <Text>{text}</Text>
    </section>
  );
}
export function PageTitle({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <header className="page-title">
      <VStack gap={2}>
        <Heading level={1}>{title}</Heading>
        <Text color="secondary">{description}</Text>
      </VStack>
      {action}
    </header>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <VStack gap={4} className="empty">
      <Activity size={32} strokeWidth={1.5} />
      <Heading level={2}>{title}</Heading>
      {children}
    </VStack>
  );
}
