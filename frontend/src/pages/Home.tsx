import { Link } from "react-router-dom";
import { ArrowUpRight, ArrowRight, MoveUpRight } from "lucide-react";
import {
  Brand,
  ActionLink,
  Heading,
  Text,
  VStack,
  HStack,
} from "../components/ui";
import { useAuth } from "../App";
// Adapted from the documentation template: header, two-column introduction,
// content sections and footer. Product copy replaces the template examples.
export function Home() {
  const { user } = useAuth();
  return (
    <section className="home">
      <header className="public-nav">
        <Brand />
        <nav aria-label="Main navigation">
          <a href="#how-it-works">How it works</a>
          <Link to={user ? "/dashboard" : "/login"}>
            {user ? "Open dashboard" : "Sign in"}
            <ArrowUpRight size={17} />
          </Link>
        </nav>
      </header>
      <main id="main-content">
        <section className="home-hero">
          <VStack gap={6} className="hero-copy">
            <Heading level={1}>
              Your readings,
              <br />
              in perspective.
            </Heading>
            <Text className="hero-description">
              There’s a story in your heart-rate data. Bring your recorded
              readings together, follow the changes, and take a closer look.
            </Text>
            <HStack gap={4} wrap="wrap">
              <ActionLink to={user ? "/dashboard" : "/register"}>
                {user ? "View your readings" : "Start with your data"}
              </ActionLink>
              <a className="text-link" href="#how-it-works">
                See how it works <ArrowRight size={17} />
              </a>
            </HStack>
            <Text className="hero-footnote">
              CSV imports. Clear charts. Your own collection.
            </Text>
          </VStack>
          <figure className="hero-photo">
            <img
              src="/images/watch-editorial.webp"
              alt="Illustrative silver smartwatch with a woven green strap resting on stone and fabric"
              fetchPriority="high"
            />
            <figcaption>A closer look at the rhythm of your day.</figcaption>
          </figure>
        </section>
        <section id="how-it-works" className="home-process">
          <VStack gap={4}>
            <Heading level={2}>
              From a file
              <br />
              to a fuller picture.
            </Heading>
            <Text color="secondary">
              Start with a CSV of recorded readings. A live connection or
              companion app isn’t needed.
            </Text>
            <a
              className="text-link"
              href="/samples/synthetic-heart-rate.csv"
              download
            >
              Download a sample CSV <MoveUpRight size={17} />
            </a>
          </VStack>
          <ol>
            <li>
              <Heading level={3}>Bring your readings</Heading>
              <Text color="secondary">
                Upload timestamps and heart rates. Add a name and tell us
                whether the data is recorded or synthetic.
              </Text>
            </li>
            <li>
              <Heading level={3}>Check before you save</Heading>
              <Text color="secondary">
                Preview the rows and date range. Confirm the import when
                everything looks right.
              </Text>
            </li>
            <li>
              <Heading level={3}>See what changes</Heading>
              <Text color="secondary">
                Explore the chart, filter by date, and inspect the exact
                readings behind the summary.
              </Text>
            </li>
          </ol>
        </section>
        <section className="home-close">
          <Heading level={2}>Make room for a little perspective.</Heading>
          <ActionLink to={user ? "/import" : "/register"}>
            {user ? "Import a recording" : "Create your account"}
          </ActionLink>
        </section>
      </main>
      <footer className="public-footer">
        <Brand />
        <Text>Wearable Heart-Rate Data Viewer</Text>
        <Text>For exploration, not medical diagnosis.</Text>
      </footer>
    </section>
  );
}
