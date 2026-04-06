import { TestBed, fakeAsync, tick, flush, discardPeriodicTasks } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { retryInterceptor } from './retry.interceptor';

describe('retryInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([retryInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http     = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── shouldRetry() ─────────────────────────────────────────────────────────

  it('retries GET requests on transient errors', fakeAsync(() => {
    let completed = false;
    http.get('/api/v1/resource-costs').subscribe({ complete: () => (completed = true) });

    // Fail twice with 503 (transient)
    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 503, statusText: 'Service Unavailable' });
    tick(700);
    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 503, statusText: 'Service Unavailable' });
    tick(1500);

    // Third attempt succeeds
    httpMock.expectOne('/api/v1/resource-costs').flush([]);
    tick();

    expect(completed).toBeTrue();
  }));

  it('does NOT retry non-safe POST requests', fakeAsync(() => {
    let errorReceived = false;
    http.post('/api/v1/products', {}).subscribe({ error: () => (errorReceived = true) });

    httpMock.expectOne('/api/v1/products').flush(
      '', { status: 503, statusText: 'Service Unavailable' });

    // No second request should be made
    httpMock.expectNone('/api/v1/products');
    tick();

    expect(errorReceived).toBeTrue();
  }));

  it('retries safe POST to /api/v1/auth/login on transient error', fakeAsync(() => {
    let completed = false;
    http.post('/api/v1/auth/login', {}).subscribe({ complete: () => (completed = true) });

    httpMock.expectOne('/api/v1/auth/login').flush(
      '', { status: 502, statusText: 'Bad Gateway' });
    tick(700);

    httpMock.expectOne('/api/v1/auth/login').flush({ accessToken: 'tok' });
    tick();

    expect(completed).toBeTrue();
  }));

  // ── isRetryableError() ────────────────────────────────────────────────────

  it('does NOT retry 4xx client errors', fakeAsync(() => {
    let error: HttpErrorResponse | undefined;
    http.get('/api/v1/resource-costs').subscribe({
      error: (e: HttpErrorResponse) => (error = e),
    });

    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 400, statusText: 'Bad Request' });

    httpMock.expectNone('/api/v1/resource-costs');
    tick();

    expect(error?.status).toBe(400);
  }));

  it('does NOT retry 401 responses (auth interceptor handles those)', fakeAsync(() => {
    let error: HttpErrorResponse | undefined;
    http.get('/api/v1/resource-costs').subscribe({
      error: (e: HttpErrorResponse) => (error = e),
    });

    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone('/api/v1/resource-costs');
    tick();

    expect(error?.status).toBe(401);
  }));

  it('retries on network error (status 0)', fakeAsync(() => {
    let completed = false;
    http.get('/api/v1/resource-costs').subscribe({ complete: () => (completed = true) });

    // Network failure (offline / DNS)
    const req = httpMock.expectOne('/api/v1/resource-costs');
    req.error(new ProgressEvent('error'), { status: 0 });
    tick(700);

    httpMock.expectOne('/api/v1/resource-costs').flush([]);
    tick();

    expect(completed).toBeTrue();
  }));

  it('retries 429 Too Many Requests', fakeAsync(() => {
    let completed = false;
    http.get('/api/v1/resource-costs').subscribe({ complete: () => (completed = true) });

    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 429, statusText: 'Too Many Requests' });
    tick(700);

    httpMock.expectOne('/api/v1/resource-costs').flush([]);
    tick();

    expect(completed).toBeTrue();
  }));

  it('stops after 3 retries (4 total attempts)', fakeAsync(() => {
    let finalError: HttpErrorResponse | undefined;
    http.get('/api/v1/resource-costs').subscribe({
      error: (e) => (finalError = e),
    });

    // 1 original + 3 retries = 4 total attempts (retry count: 3 in RxJS).
    // The interceptor backoff is deterministic: 500ms, 1000ms, 2000ms.
    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 503, statusText: 'Service Unavailable' });
    tick(500);

    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 503, statusText: 'Service Unavailable' });
    tick(1000);

    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 503, statusText: 'Service Unavailable' });
    tick(2000);

    httpMock.expectOne('/api/v1/resource-costs').flush(
      '', { status: 503, statusText: 'Service Unavailable' });

    // Let the error propagate and ensure no further retries are scheduled.
    tick();
    expect(finalError?.status).toBe(503);

    // Advance time beyond the full backoff window to prove a 5th request never occurs.
    tick(10_000);
    expect(httpMock.match('/api/v1/resource-costs').length).toBe(0);
    discardPeriodicTasks();
  }));
});
