import type { Page } from '@playwright/test';

import { e2eAdminPassword, e2eAdminUser } from '../fixtures/credentials';

export async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/login');

  // E2E is intentionally "bursty" (many logins quickly). Also, the backend demo seeding
  // can complete moments after health goes UP. Retry a few times to avoid flakes.
  let lastStatus: number | null = null;
  let lastUrl: string | null = null;
  let lastBody: string | null = null;
  for (let attempt = 1; attempt <= 5; attempt++) {
    await page.locator('#username').fill(e2eAdminUser());
    await page.locator('#password').fill(e2eAdminPassword());

    const loginResponse = page.waitForResponse(resp =>
      resp.url().includes('/api/v1/auth/login') && resp.request().method() === 'POST'
    );

    await page.getByRole('button', { name: 'Sign in' }).click();
    const resp = await loginResponse;

    if (resp.ok()) {
      await page.getByRole('heading', { name: 'CFO Dashboard' })
        .waitFor({ state: 'visible', timeout: 30000 });
      return;
    }
    lastStatus = resp.status();
    lastUrl = resp.url();
    try {
      lastBody = await resp.text();
    } catch {
      lastBody = null;
    }

    // Small backoff before retry; the UI clears the password on failure.
    await page.waitForTimeout(300 * attempt);
  }

  throw new Error(
    `Admin login failed after retries. url=${lastUrl ?? 'n/a'} status=${lastStatus ?? 'n/a'} body=${lastBody ?? 'n/a'}`
  );
}
