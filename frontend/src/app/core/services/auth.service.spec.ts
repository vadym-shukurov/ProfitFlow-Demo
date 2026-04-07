import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';
import { XsrfTokenStore } from '../http/xsrf-token.store';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  const mockLoginResponse = {
    accessToken:  'jwt-token-abc',
    refreshToken: 'refresh-token-xyz',
    tokenType:    'Bearer',
    expiresIn:    900,
    username:     'admin',
    roles:        'ROLE_CFO,ROLE_FINANCE_MANAGER',
  };

  beforeEach(() => {
    router = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: router },
      ],
    });

    service  = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    TestBed.inject(XsrfTokenStore).clear();

    // Clear sessionStorage between tests to avoid cross-test pollution
    sessionStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  function flushCsrfPriming(): void {
    const r = httpMock.expectOne('/actuator/health');
    expect(r.request.method).toBe('GET');
    r.flush(null, { headers: { 'X-XSRF-TOKEN': 'test-xsrf' } });
  }

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // ── Initial state ─────────────────────────────────────────────────────────

  it('starts unauthenticated', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.token()).toBeNull();
    expect(service.username()).toBeNull();
    expect(service.canWrite()).toBeFalse();
    expect(service.isAdmin()).toBeFalse();
    expect(service.hasRefreshToken()).toBeFalse();
  });

  // ── login() ───────────────────────────────────────────────────────────────

  describe('login()', () => {
    it('sends POST to /api/v1/auth/login with credentials', () => {
      service.login('admin', 'secret').subscribe();

      flushCsrfPriming();
      const req = httpMock.expectOne('/api/v1/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ username: 'admin', password: 'secret' });
      req.flush(mockLoginResponse);
    });

    it('sets token and authentication state on success', () => {
      service.login('admin', 'secret').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush(mockLoginResponse);

      expect(service.isAuthenticated()).toBeTrue();
      expect(service.token()).toBe('jwt-token-abc');
      expect(service.username()).toBe('admin');
    });

    it('stores refresh token in sessionStorage', () => {
      service.login('admin', 'secret').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush(mockLoginResponse);

      expect(service.hasRefreshToken()).toBeTrue();
    });

    it('parses comma-separated roles correctly', () => {
      service.login('admin', 'secret').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush(mockLoginResponse);

      expect(service.canWrite()).toBeTrue();
    });

    it('sets isAdmin for ROLE_ADMIN', () => {
      service.login('super', 'pass').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush({
        ...mockLoginResponse,
        roles: 'ROLE_ADMIN',
      });

      expect(service.isAdmin()).toBeTrue();
    });

    it('sets canWrite to false for ANALYST role only', () => {
      service.login('viewer', 'pass').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush({
        ...mockLoginResponse,
        roles: 'ROLE_ANALYST',
      });

      expect(service.canWrite()).toBeFalse();
    });
  });

  // ── refresh() ─────────────────────────────────────────────────────────────

  describe('refresh()', () => {
    beforeEach(() => {
      // Seed a refresh token (simulates previous login)
      service.login('admin', 'secret').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush(mockLoginResponse);
    });

    it('sends POST to /api/v1/auth/refresh with stored refresh token', () => {
      service.refresh().subscribe();

      const req = httpMock.expectOne('/api/v1/auth/refresh');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ refreshToken: 'refresh-token-xyz' });
      req.flush({ ...mockLoginResponse, accessToken: 'new-access-token', refreshToken: 'new-refresh' });
    });

    it('updates access token on successful refresh', () => {
      service.refresh().subscribe();
      httpMock.expectOne('/api/v1/auth/refresh').flush({
        ...mockLoginResponse,
        accessToken:  'new-access-token',
        refreshToken: 'new-refresh-token',
      });

      expect(service.token()).toBe('new-access-token');
      expect(service.hasRefreshToken()).toBeTrue();
    });

    it('returns EMPTY observable when no refresh token is stored', () => {
      sessionStorage.clear(); // Remove the refresh token
      let emitted = false;
      service.refresh().subscribe({ next: () => (emitted = true) });
      expect(emitted).toBeFalse();
    });
  });

  // ── logout() ──────────────────────────────────────────────────────────────

  describe('logout()', () => {
    it('clears authentication state and navigates to login', fakeAsync(() => {
      service.login('admin', 'secret').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush(mockLoginResponse);
      expect(service.isAuthenticated()).toBeTrue();

      service.logout();

      httpMock.expectOne('/api/v1/auth/logout').flush({});
      tick();

      expect(service.isAuthenticated()).toBeFalse();
      expect(service.token()).toBeNull();
      expect(service.username()).toBeNull();
      expect(service.hasRefreshToken()).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    }));

    it('clears session even when server-side logout fails', fakeAsync(() => {
      service.login('admin', 'secret').subscribe();
      flushCsrfPriming();
      httpMock.expectOne('/api/v1/auth/login').flush(mockLoginResponse);

      service.logout();

      httpMock.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
      tick();

      expect(service.isAuthenticated()).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    }));
  });
});
