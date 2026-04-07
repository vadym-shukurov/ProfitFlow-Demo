import { expect, test } from '@playwright/test';

import { e2eAdminPassword, e2eAdminUser, e2eAnalystPassword } from '../fixtures/credentials';

/**
 * API acceptance checks — auth, catalogue, allocation, AI, token lifecycle, RBAC.
 * Uses native fetch (Node 20+) so requests hit the Spring app reliably; see docs/testing/COVERAGE.md.
 */
const DEFAULT_API_PORT = process.env.API_PORT ?? '18080';
const API_BASE = process.env.PLAYWRIGHT_API_BASE_URL ?? `http://127.0.0.1:${DEFAULT_API_PORT}`;
if (process.env.CI && !process.env.PLAYWRIGHT_API_BASE_URL) {
  // In CI we always start the API on a dedicated port (see e2e/scripts/ci-with-servers.sh).
  // Falling back to :8080 can accidentally hit a developer machine's stale process and produce
  // misleading auth failures (401) even though the E2E API is healthy.
  throw new Error('Missing PLAYWRIGHT_API_BASE_URL; refusing to default to :8080 in CI');
}

/** Spring {@code CookieCsrfTokenRepository} — cookie + matching header on mutating requests. */
const cookieJar = new Map<string, string>();

function mergeSetCookies(res: Response): void {
  const raw = res.headers as unknown as { getSetCookie?: () => string[] };
  const list = typeof raw.getSetCookie === 'function' ? raw.getSetCookie() : [];
  for (const c of list) {
    const pair = c.split(';')[0]?.trim() ?? '';
    const eq = pair.indexOf('=');
    if (eq > 0) {
      cookieJar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
    }
  }
}

function cookieHeader(): Record<string, string> {
  if (cookieJar.size === 0) {
    return {};
  }
  return {
    Cookie: [...cookieJar.entries()].map(([k, v]) => `${k}=${v}`).join('; '),
  };
}

function csrfHeaders(): Record<string, string> {
  const token = cookieJar.get('XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token } : {};
}

function authHeader(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

async function apiGet(
    path: string,
    headers?: Record<string, string>,
): Promise<Response> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { ...cookieHeader(), ...headers },
  });
  mergeSetCookies(res);
  return res;
}

async function apiPost(
    path: string,
    body?: unknown,
    headers?: Record<string, string>,
): Promise<Response> {
  const h: Record<string, string> = {
    ...cookieHeader(),
    ...csrfHeaders(),
    ...headers,
  };
  if (body !== undefined) {
    h['Content-Type'] = 'application/json';
  }
  const init: RequestInit = { method: 'POST', headers: h };
  if (body !== undefined) {
    init.body = JSON.stringify(body);
  }
  const res = await fetch(`${API_BASE}${path}`, init);
  mergeSetCookies(res);
  return res;
}

test.describe.serial('API — critical operations', () => {
  let accessToken: string;
  let refreshToken: string;
  let runNumber: number;
  let analystAccessToken: string;

  test('01 GET /actuator/health', async () => {
    const res = await apiGet('/actuator/health');
    expect(res.status).toBe(200);
    const body = await res.json() as { status: string };
    expect(body.status).toBe('UP');
  });

  test('02 POST /api/v1/auth/login rejects bad credentials', async () => {
    const res = await apiPost('/api/v1/auth/login', {
      username: 'no-such-user',
      password: 'wrong-password',
    });
    expect(res.status).toBe(401);
  });

  test('03 POST /api/v1/auth/login accepts demo admin', async () => {
    const res = await apiPost('/api/v1/auth/login', {
      username: e2eAdminUser(),
      password: e2eAdminPassword(),
    });
    expect(res.status).toBe(200);
    const body = await res.json() as { accessToken: string; refreshToken: string };
    expect(body.accessToken).toBeTruthy();
    expect(body.refreshToken).toBeTruthy();
    accessToken = body.accessToken;
    refreshToken = body.refreshToken;
  });

  test('04 GET /api/v1/activities without JWT → 401', async () => {
    const res = await apiGet('/api/v1/activities');
    expect(res.status).toBe(401);
  });

  test('05 GET /api/v1/activities', async () => {
    const res = await apiGet('/api/v1/activities', authHeader(accessToken));
    expect(res.status).toBe(200);
    expect(Array.isArray(await res.json())).toBe(true);
  });

  test('06 GET /api/v1/products', async () => {
    const res = await apiGet('/api/v1/products', authHeader(accessToken));
    expect(res.status).toBe(200);
  });

  test('07 GET /api/v1/resource-costs', async () => {
    const res = await apiGet('/api/v1/resource-costs', authHeader(accessToken));
    expect(res.status).toBe(200);
  });

  test('08 GET /api/v1/rules/resource-to-activity', async () => {
    const res = await apiGet('/api/v1/rules/resource-to-activity', authHeader(accessToken));
    expect(res.status).toBe(200);
  });

  test('09 GET /api/v1/rules/activity-to-product', async () => {
    const res = await apiGet('/api/v1/rules/activity-to-product', authHeader(accessToken));
    expect(res.status).toBe(200);
  });

  test('10 POST /api/v1/allocations/run', async () => {
    const res = await apiPost('/api/v1/allocations/run', undefined, authHeader(accessToken));
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toBeTruthy();
  });

  test('11 GET /api/v1/allocations/runs', async () => {
    const res = await apiGet('/api/v1/allocations/runs', authHeader(accessToken));
    expect(res.status).toBe(200);
    const list = await res.json() as Array<{ runNumber: number }>;
    expect(list.length).toBeGreaterThan(0);
    runNumber = list[0].runNumber;
  });

  test('12 GET /api/v1/allocations/runs/{runNumber}', async () => {
    const res = await apiGet(`/api/v1/allocations/runs/${runNumber}`, authHeader(accessToken));
    expect(res.status).toBe(200);
  });

  test('13 POST /api/v1/ai/suggest', async () => {
    const res = await apiPost(
        '/api/v1/ai/suggest',
        { text: 'E2E probe: IT hosting spend' },
        authHeader(accessToken),
    );
    expect(res.status).toBe(200);
    const body = await res.json() as { suggestedActivityName: string };
    expect(body.suggestedActivityName).toBeTruthy();
  });

  test('14 POST /api/v1/auth/refresh rotates tokens', async () => {
    const res = await apiPost('/api/v1/auth/refresh', { refreshToken });
    expect(res.status).toBe(200);
    const body = await res.json() as { accessToken: string; refreshToken: string };
    accessToken = body.accessToken;
    refreshToken = body.refreshToken;
  });

  test('15 POST /api/v1/activities (create)', async () => {
    const name = `e2e-activity-${Date.now()}`;
    const res = await apiPost('/api/v1/activities', { name }, authHeader(accessToken));
    expect(res.status).toBe(201);
  });

  test('16 POST /api/v1/products (create)', async () => {
    const name = `e2e-product-${Date.now()}`;
    const res = await apiPost('/api/v1/products', { name }, authHeader(accessToken));
    expect(res.status).toBe(201);
  });

  test('17 GET /v3/api-docs (contract reachable for ADMIN)', async () => {
    const res = await apiGet('/v3/api-docs', authHeader(accessToken));
    expect(res.status).toBe(200);
    const text = await res.text();
    expect(text).toContain('openapi');
  });

  test('18 POST /api/v1/auth/logout', async () => {
    const res = await apiPost('/api/v1/auth/logout', { refreshToken }, authHeader(accessToken));
    expect(res.status).toBe(204);
  });

  test('19 GET /api/v1/activities with revoked access → 401', async () => {
    const res = await apiGet('/api/v1/activities', authHeader(accessToken));
    expect(res.status).toBe(401);
  });

  test('20 POST /api/v1/auth/login again after logout', async () => {
    const res = await apiPost('/api/v1/auth/login', {
      username: e2eAdminUser(),
      password: e2eAdminPassword(),
    });
    expect(res.status).toBe(200);
  });

  test('21 POST /api/v1/auth/login as analyst (read-only)', async () => {
    const res = await apiPost('/api/v1/auth/login', {
      username: 'analyst',
      password: e2eAnalystPassword(),
    });
    expect(res.status).toBe(200);
    const body = await res.json() as { accessToken: string };
    expect(body.accessToken).toBeTruthy();
    analystAccessToken = body.accessToken;
  });

  test('22 POST /api/v1/allocations/run forbidden for analyst (RBAC)', async () => {
    const res = await apiPost(
        '/api/v1/allocations/run',
        undefined,
        authHeader(analystAccessToken),
    );
    expect(res.status).toBe(403);
  });
});
