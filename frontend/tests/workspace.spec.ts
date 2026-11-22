import { test, expect } from "@playwright/test";

test("public pages fit a phone and password focus stays inside its control", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  for (const path of ["/", "/login", "/register"]) {
    await page.goto(path);
    await expect(page.locator("h1")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }
  await expect(page.locator('header')).toHaveCount(0);
  await expect(page.locator('.astryx-card')).toHaveCount(0);
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  const password = page.locator("input[type=password]");
  await password.focus();
  const geometry = await password.evaluate((input) => {
    const control = input.closest(".astryx-text-input")!;
    const inner = input.getBoundingClientRect();
    const outer = control.getBoundingClientRect();
    return {
      contained: inner.top >= outer.top && inner.bottom <= outer.bottom,
      outline: getComputedStyle(input).outlineStyle,
    };
  });
  expect(geometry.contained).toBe(true);
  expect(geometry.outline).toBe("none");
});

test("registration explains the password requirement before any request", async ({
  page,
}) => {
  let registrations = 0;
  page.on("request", (request) => {
    if (request.url().endsWith("/api/auth/register")) registrations++;
  });
  await page.goto("/register");
  await page.getByLabel("Email address").fill("validation@example.com");
  await page.locator("input[type=password]").fill("short");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(
    page
      .getByText("Use between 12 and 128 characters.", { exact: true })
      .first(),
  ).toBeVisible();
  expect(registrations).toBe(0);
});

test("import, inspect, rename and delete only a temporary synthetic recording", async ({
  page,
}) => {
  test.skip(
    !process.env.HR_TEST_EMAIL || !process.env.HR_TEST_PASSWORD,
    "Set HR_TEST_EMAIL and HR_TEST_PASSWORD for a local test account.",
  );
  const label = "Browser verification " + Date.now();
  let createdId: string | undefined;
  let csrf = "";
  const base = "http://127.0.0.1:5173";
  try {
    await page.goto("/login");
    await page.getByLabel("Email address").fill(process.env.HR_TEST_EMAIL!);
    await page
      .locator("input[type=password]")
      .fill(process.env.HR_TEST_PASSWORD!);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.waitForURL("**/dashboard");
    csrf = (await (await page.request.get("/api/auth/me")).json()).csrf_token;
    await page.goto("/dashboard?start_date=2099-01-01&end_date=2099-01-02");
    await expect(
      page.getByRole("heading", { name: "No readings in view." }),
    ).toBeVisible();
    await page.goto("/import");
    await page
      .locator("input[type=file]")
      .setInputFiles({
        name: "invalid.csv",
        mimeType: "text/csv",
        buffer: Buffer.from("timestamp,heart_rate\nbad-time,999\n"),
      });
    await page
      .getByRole("button", { name: "Preview readings", exact: true })
      .click();
    await expect(page.locator(".error-message")).toBeVisible();
    const now = Date.now();
    const csv = `timestamp,heart_rate\n${new Date(now - 100000).toISOString()},73\n${new Date(now - 40000).toISOString()},81\n`;
    await page
      .locator("input[type=file]")
      .setInputFiles({
        name: "browser-verification.csv",
        mimeType: "text/csv",
        buffer: Buffer.from(csv),
      });
    await page.getByLabel("Recording name").fill(label);
    await page.getByRole("radio", { name: /Synthetic/ }).check();
    await page
      .getByRole("button", { name: "Preview readings", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Ready for a closer look." }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Save recording", exact: true })
      .click();
    await page.waitForURL("**/recordings/*");
    createdId = page.url().split("/").pop();
    await expect(
      page.getByRole("heading", { name: label, exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("2 of 2 readings", { exact: false }),
    ).toBeVisible();
    await page.goto("/recordings");
    await page
      .getByRole("button", { name: "Rename " + label, exact: true })
      .click();
    await page
      .getByRole("dialog")
      .getByLabel("Recording name")
      .fill(label + " renamed");
    await page.getByRole("button", { name: "Save name", exact: true }).click();
    await page
      .getByRole("button", {
        name: "Delete " + label + " renamed",
        exact: true,
      })
      .click();
    await page.getByRole("button", { name: "Cancel", exact: true }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();
    await page
      .getByRole("button", {
        name: "Delete " + label + " renamed",
        exact: true,
      })
      .click();
    await page
      .getByRole("button", { name: "Delete recording", exact: true })
      .click();
    await expect(
      page.getByText("Recording deleted.", { exact: true }),
    ).toBeVisible();
    createdId = undefined;
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await page.waitForURL("**/login");
    await page.goto("/dashboard");
    await page.waitForURL("**/login");
  } finally {
    if (createdId && csrf)
      await page.request.delete("/api/recordings/" + createdId, {
        headers: { Origin: base, "X-CSRF-Token": csrf },
      });
    if (csrf)
      await page.request.post("/api/auth/logout", {
        headers: { Origin: base, "X-CSRF-Token": csrf },
      });
  }
});
