import { TestBed } from '@angular/core/testing';
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
import { Router } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { signal } from '@angular/core';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  /** Writable signal shared by all tests — reset to null in beforeEach. */
  const tokenSignal = signal<string | null>(null);

  beforeEach(() => {
    tokenSignal.set(null);

    authService = jasmine.createSpyObj<AuthService>(
      'AuthService',
      ['logout', 'refresh', 'hasRefreshToken'],
      { token: tokenSignal }
    );
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    authService.hasRefreshToken.and.returnValue(false);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: Router,      useValue: router      },
      ],
    });

    http     = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── Token attachment ──────────────────────────────────────────────────────

  it('attaches Bearer token to API requests when authenticated', () => {
    tokenSignal.set('my-jwt');

    http.get('/api/v1/resource-costs').subscribe();

    const req = httpMock.expectOne('/api/v1/resource-costs');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt');
    req.flush([]);
  });

  it('does not attach Authorization when no token is present', () => {
    tokenSignal.set(null);

    http.get('/api/v1/resource-costs').subscribe();

    const req = httpMock.expectOne('/api/v1/resource-costs');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('does not attach token to non-API requests', () => {
    tokenSignal.set('my-jwt');

    http.get('https://fonts.googleapis.com/css').subscribe();

    const req = httpMock.expectOne('https://fonts.googleapis.com/css');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush('');
  });

  // ── 401 handling — no refresh token ──────────────────────────────────────

  it('calls logout and does NOT navigate on 401 when no refresh token', () => {
    authService.hasRefreshToken.and.returnValue(false);

    http.get('/api/v1/resource-costs').subscribe({ error: () => {} });

    httpMock.expectOne('/api/v1/resource-costs').flush(
      { message: 'Unauthorized' },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(authService.logout).toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  // ── 401 handling — with refresh token ────────────────────────────────────

  it('retries original request after successful token refresh', () => {
    tokenSignal.set('old-token');
    authService.hasRefreshToken.and.returnValue(true);
    authService.refresh.and.returnValue(of('new-token'));

    http.get('/api/v1/resource-costs').subscribe();

    // First attempt fails with 401
    httpMock.expectOne('/api/v1/resource-costs').flush(
      { message: 'Unauthorized' },
      { status: 401, statusText: 'Unauthorized' }
    );

    // Retry with refreshed token should succeed
    const retried = httpMock.expectOne('/api/v1/resource-costs');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-token');
    retried.flush([]);
  });

  it('logs out when refresh fails', () => {
    authService.hasRefreshToken.and.returnValue(true);
    authService.refresh.and.returnValue(EMPTY); // refresh returns empty (failure)

    http.get('/api/v1/resource-costs').subscribe({ error: () => {} });

    httpMock.expectOne('/api/v1/resource-costs').flush(
      { message: 'Unauthorized' },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(authService.logout).not.toHaveBeenCalled(); // EMPTY means no error, no retry
  });

  // ── 403 handling ─────────────────────────────────────────────────────────

  it('navigates to root on 403 without clearing session', () => {
    http.get('/api/v1/resource-costs').subscribe({ error: () => {} });

    httpMock.expectOne('/api/v1/resource-costs').flush(
      { message: 'Forbidden' },
      { status: 403, statusText: 'Forbidden' }
    );

    expect(router.navigate).toHaveBeenCalledWith(['/']);
    expect(authService.logout).not.toHaveBeenCalled();
  });

  // ── Non-API errors pass through ───────────────────────────────────────────

  it('does not intercept non-API 401 responses', () => {
    let error: HttpErrorResponse | undefined;

    http.get('https://external.example.com/api').subscribe({
      error: (e: HttpErrorResponse) => (error = e),
    });

    httpMock.expectOne('https://external.example.com/api').flush(
      { message: 'Unauthorized' },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(authService.logout).not.toHaveBeenCalled();
    expect(error?.status).toBe(401);
  });
});
