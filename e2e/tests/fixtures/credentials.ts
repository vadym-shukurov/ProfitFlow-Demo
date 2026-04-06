/**
 * E2E credentials — supplied only via environment variables in CI.
 * Defaults match {@code DemoDataSeeder} non-production demo users (never use in prod).
 */
export function e2eAdminUser(): string {
  return process.env.E2E_ADMIN_USER ?? 'admin';
}

export function e2eAdminPassword(): string {
  const p = process.env.E2E_ADMIN_PASSWORD;
  if (p && p.length > 0) {
    return p;
  }
  // Matches DemoDataSeeder demo admin. CI workflows should export E2E_ADMIN_PASSWORD
  // (secret or default) before invoking Node — same value as local fallback.
  return 'Admin1234!';
}

/** Demo analyst — read-only role (DemoDataSeeder). */
export function e2eAnalystPassword(): string {
  const p = process.env.E2E_ANALYST_PASSWORD;
  if (p && p.length > 0) {
    return p;
  }
  if (process.env.CI) {
    return 'Analyst123!';
  }
  return 'Analyst123!';
}
