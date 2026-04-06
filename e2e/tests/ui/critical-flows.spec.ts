import { expect, test } from '@playwright/test';

import { loginAsAdmin } from '../helpers/login';

/**
 * UI acceptance stories — denominator 20 (see docs/testing/COVERAGE.md).
 * Each authenticated test calls loginAsAdmin (bounded suite; avoids auth rate limits).
 */
const UI_STORY_IDS = [
  'login.page.visible',
  'login.branding',
  'login.invalid.credentials',
  'login.success',
  'shell.nav.visible',
  'route.dashboard',
  'route.ledger',
  'route.rules',
  'route.ai',
  'route.dashboard.return',
  'dashboard.run.button',
  'dashboard.primary.nav.a11y',
  'logout.sidebar',
  'login.after.logout',
  'ledger.content',
  'rules.content',
  'ai.content',
  'mobile.header.logout.present',
  'deep.login.url',
  'form.username.autocomplete',
] as const;

test.describe('UI — story index', () => {
  test(`defines ${UI_STORY_IDS.length} stories (95% gate = 19/20 minimum)`, () => {
    expect(UI_STORY_IDS.length).toBe(20);
  });
});

test.describe('UI — public / login', () => {
  test('login.page.visible — sign-in form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /Sign in to your account/i })).toBeVisible();
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
  });

  test('login.branding — product title', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'ProfitFlow', exact: true })).toBeVisible();
  });

  test('login.invalid.credentials — generic error', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('not-a-user');
    await page.locator('#password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.locator('[role="alert"]')).toBeVisible({ timeout: 15_000 });
  });

  test('deep.login.url', async ({ page }) => {
    await page.goto('/login');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('form.username.autocomplete', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('#username')).toHaveAttribute('autocomplete', 'username');
  });
});

test.describe('UI — authenticated shell', () => {
  test('login.success + route.dashboard', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page.getByRole('heading', { name: 'CFO Dashboard' })).toBeVisible();
  });

  test('shell.nav.visible — primary navigation', async ({ page }) => {
    await loginAsAdmin(page);
    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
    await expect(nav.getByRole('link', { name: 'CFO Dashboard' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Cost Ledger' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Allocation Rules' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'AI Allocator' })).toBeVisible();
  });

  test('route.ledger — Cost Ledger', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('navigation', { name: 'Primary navigation' })
        .getByRole('link', { name: 'Cost Ledger' }).click();
    await expect(page.getByRole('heading', { name: 'Cost Ledger' })).toBeVisible();
  });

  test('route.rules — Allocation Rules', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('navigation', { name: 'Primary navigation' })
        .getByRole('link', { name: 'Allocation Rules' }).click();
    await expect(page.getByRole('heading', { name: 'Allocation Rules' })).toBeVisible();
  });

  test('route.ai — AI Smart Allocator', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('navigation', { name: 'Primary navigation' })
        .getByRole('link', { name: 'AI Allocator' }).click();
    await expect(page.getByRole('heading', { name: 'AI Smart Allocator' })).toBeVisible();
  });

  test('route.dashboard.return', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('navigation', { name: 'Primary navigation' })
        .getByRole('link', { name: 'AI Allocator' }).click();
    await page.getByRole('navigation', { name: 'Primary navigation' })
        .getByRole('link', { name: 'CFO Dashboard' }).click();
    await expect(page.getByRole('heading', { name: 'CFO Dashboard' })).toBeVisible();
  });

  test('dashboard.run.button — Run allocation control', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page.getByRole('button', { name: /Run allocation/i })).toBeVisible();
  });

  test('dashboard.primary.nav.a11y — landmark', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toBeVisible();
  });

  test('ledger.content — add cost panel', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/ledger');
    await expect(page.getByRole('heading', { name: 'Add resource cost' })).toBeVisible();
  });

  test('rules.content — stage 1 section', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/rules');
    await expect(page.getByRole('heading', { name: 'Resource → activity' })).toBeVisible({
      timeout: 20_000,
    });
  });

  test('ai.content — expense textarea', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/ai');
    await expect(page.getByPlaceholder(/Zendesk/i)).toBeVisible();
  });

  test('mobile.header.logout.present', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await loginAsAdmin(page);
    await expect(page.getByRole('banner').getByRole('button', { name: 'Sign out' })).toBeVisible();
  });
});

test.describe('UI — logout', () => {
  test('logout.sidebar + login.after.logout', async ({ page }) => {
    await loginAsAdmin(page);
    await page.locator('aside').getByRole('button', { name: 'Sign out' }).click();
    await expect(page.getByRole('heading', { name: /Sign in to your account/i })).toBeVisible({
      timeout: 15_000,
    });
  });
});
