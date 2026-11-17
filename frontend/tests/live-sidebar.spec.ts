import { test, expect } from "@playwright/test";

async function signedIn(page: any) {
  await page.route("**/api/auth/me", (route) =>
    route.fulfill({
      json: {
        user: {
          id: "test-viewer",
          email: "viewer@example.test",
          created_at: new Date().toISOString(),
        },
        csrf_token: "test-csrf",
      },
    }),
  );
}

test("sidebar animates pointer toggles, remembers collapse and respects keyboard/reduced motion", async ({
  page,
}) => {
  await signedIn(page);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/import");
  await page
    .getByRole("button", { name: "Collapse sidebar", exact: true })
    .click();
  expect(
    await page
      .locator(".workspace")
      .evaluate((e) =>
        e.getAnimations().some((a) => a.playState === "running"),
      ),
  ).toBe(true);
  await expect
    .poll(() =>
      page
        .locator(".workspace")
        .evaluate(
          (e) =>
            e.getAnimations().filter((a) => a.playState === "running").length,
        ),
    )
    .toBe(0);
  expect(
    await page
      .locator(".sidebar")
      .evaluate((e) => e.getBoundingClientRect().width),
  ).toBe(80);
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Expand sidebar", exact: true }),
  ).toHaveAttribute("aria-expanded", "false");
  await page
    .getByRole("button", { name: "Expand sidebar", exact: true })
    .focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("button", { name: "Collapse sidebar", exact: true }),
  ).toHaveAttribute("aria-expanded", "true");
  expect(
    await page
      .locator(".workspace")
      .evaluate(
        (e) =>
          e.getAnimations().filter((a) => a.playState === "running").length,
      ),
  ).toBe(0);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page
    .getByRole("button", { name: "Collapse sidebar", exact: true })
    .click();
  expect(
    await page
      .locator(".workspace")
      .evaluate(
        (e) =>
          e.getAnimations().filter((a) => a.playState === "running").length,
      ),
  ).toBe(0);
  await page.setViewportSize({ width: 375, height: 812 });
  await page
    .getByRole("button", { name: "Expand sidebar", exact: true })
    .click();
  await expect(
    page.getByRole("link", { name: "Live watch", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test("live view restores a session and stops illustrating a stale or failed feed", async ({
  page,
}) => {
  await signedIn(page);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  let stale = false,
    failed = false;
  const state = () => ({
    id: "test-session",
    status: stale ? "stale" : "receiving",
    device_label: "ET488 test fixture",
    expires_at: new Date(Date.now() + 600000).toISOString(),
    last_received_at: new Date(Date.now() - (stale ? 20000 : 0)).toISOString(),
    samples: [
      {
        sample_id: "sample-1",
        timestamp: new Date(Date.now() - (stale ? 20000 : 0)).toISOString(),
        received_at: new Date(Date.now() - (stale ? 20000 : 0)).toISOString(),
        heart_rate: 72,
      },
    ],
  });
  await page.route("**/api/live/current", (route) =>
    route.fulfill({ json: state() }),
  );
  await page.route("**/api/live/sessions/test-session", (route) =>
    failed
      ? route.fulfill({
          status: 503,
          json: { message: "Test connection unavailable" },
        })
      : route.fulfill({ json: state() }),
  );
  await page.goto("/live");
  await expect(
    page.getByText("Receiving watch readings", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".live-reading")).toHaveClass(/is-receiving/);
  expect(
    await page.locator(".live-heart").evaluate((e) => e.getAnimations().length),
  ).toBeGreaterThan(0);
  stale = true;
  await expect(
    page.getByText("No fresh reading — waiting for the watch", { exact: true }),
  ).toBeVisible();
  expect(
    await page.locator(".live-heart").evaluate((e) => e.getAnimations().length),
  ).toBe(0);
  failed = true;
  await expect(
    page.getByText("Test connection unavailable", { exact: true }),
  ).toBeVisible();
  expect(
    await page.locator(".live-heart").evaluate((e) => e.getAnimations().length),
  ).toBe(0);
});
