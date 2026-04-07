import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap, catchError, EMPTY, map, firstValueFrom, of, switchMap } from 'rxjs';

import { XSRF_HEADER } from '../http/xsrf.interceptor';
import { XsrfTokenStore } from '../http/xsrf-token.store';

/**
 * Authentication service — manages access and refresh token lifecycle.
 *
 * ## Security design
 * - **Access token** is stored in a private signal (JavaScript heap), NOT in
 *   `localStorage` or `sessionStorage`, which are accessible to any script on
 *   the page (XSS attack surface). Lost on page refresh by design.
 * - **Refresh token** is stored in `sessionStorage` only (not `localStorage`),
 *   so it is cleared when the browser tab/window is closed. This is an acceptable
 *   trade-off: it avoids forcing re-login on every page refresh while ensuring
 *   tokens are not persisted across browser sessions.
 * - The `Authorization` header is added by `AuthInterceptor`, not here.
 * - On logout, the refresh token is revoked server-side before clearing local state.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http   = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly xsrf   = inject(XsrfTokenStore);

  // Stored in memory only — cleared on page refresh
  private readonly _token    = signal<string | null>(null);
  private readonly _username = signal<string | null>(null);
  private readonly _roles    = signal<string[]>([]);

  // ── Public read-only selectors ────────────────────────────────────────────

  /** Current JWT access token, or {@code null} if not authenticated. */
  readonly token = computed(() => this._token());

  /** Display name of the authenticated user. */
  readonly username = computed(() => this._username());

  /** {@code true} when a valid access token is held in memory. */
  readonly isAuthenticated = computed(() => this._token() !== null);

  /** {@code true} when the current user has write-access (FINANCE_MANAGER or ADMIN). */
  readonly canWrite = computed(() =>
    this._roles().some(r => r === 'ROLE_FINANCE_MANAGER' || r === 'ROLE_ADMIN')
  );

  /** {@code true} when the current user is an admin. */
  readonly isAdmin = computed(() => this._roles().includes('ROLE_ADMIN'));

  // ── Actions ───────────────────────────────────────────────────────────────

  /**
   * Submits credentials to the login endpoint and stores the returned tokens.
   * Returns the Observable so callers can subscribe and react to success/error.
   */
  login(username: string, password: string): Observable<AuthResponse> {
    return this.primeCsrfIfNeeded().pipe(
      switchMap(() =>
        this.http.post<AuthResponse>('/api/v1/auth/login', { username, password })
      ),
      tap(response => this.applyAuthResponse(response))
    );
  }

  /**
   * Uses the stored refresh token to obtain a new access token without prompting
   * the user for credentials.
   *
   * Updates internal state (access token, roles) on success.
   * Throws if the refresh fails — callers should handle the error.
   *
   * @returns Observable that emits the new access token string, or EMPTY if no
   *          refresh token is stored (e.g. page was just loaded).
   */
  refresh(): Observable<string> {
    const storedRefreshToken = this.getStoredRefreshToken();
    if (!storedRefreshToken) {
      return EMPTY;
    }
    return this.primeCsrfIfNeeded().pipe(
      switchMap(() =>
        this.http.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken: storedRefreshToken })
      ),
      tap(response => this.applyAuthResponse(response)),
      map(response => response.accessToken)
    );
  }

  /**
   * Returns {@code true} if a refresh token is available in sessionStorage.
   * Used by the auth interceptor to decide whether to attempt a silent refresh.
   */
  hasRefreshToken(): boolean {
    return !!this.getStoredRefreshToken();
  }

  /**
   * Revokes the refresh token server-side, clears local state, and navigates to login.
   *
   * The server call is awaited before clearing state so that the access token's JTI
   * is added to the server-side denylist before the client navigates away.
   * If the revocation request fails (network error, 401), local state is still cleared
   * to ensure the user is logged out locally — the server-side revocation is not
   * a blocking dependency for local session termination.
   */
  logout(): void {
    const refreshToken = this.getStoredRefreshToken();
    if (refreshToken) {
      // Await server-side revocation so the access token JTI denylist is updated
      // before we clear local state. Falls back gracefully on any network error.
      firstValueFrom(
        this.http.post('/api/v1/auth/logout', { refreshToken }).pipe(
          catchError(() => EMPTY)
        )
      ).finally(() => {
        this.clearLocalState();
        this.router.navigate(['/login']);
      });
    } else {
      this.clearLocalState();
      this.router.navigate(['/login']);
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private applyAuthResponse(response: AuthResponse): void {
    this._token.set(response.accessToken);
    this._username.set(response.username);
    this._roles.set(response.roles ? response.roles.split(',').map(r => r.trim()) : []);
    if (response.refreshToken) {
      sessionStorage.setItem('pf_rt', response.refreshToken);
    }
  }

  private getStoredRefreshToken(): string | null {
    return sessionStorage.getItem('pf_rt');
  }

  private clearLocalState(): void {
    this._token.set(null);
    this._username.set(null);
    this._roles.set([]);
    sessionStorage.removeItem('pf_rt');
    this.xsrf.clear();
  }

  /**
   * Ensures we have a CSRF header value before the first mutating call (e.g. login/refresh
   * can run before any other API GET). Uses public health — same origin as the API.
   */
  private primeCsrfIfNeeded(): Observable<void> {
    if (this.xsrf.get()) {
      return of(void 0);
    }
    return this.http.get('/actuator/health', { observe: 'response' }).pipe(
      tap(res => {
        const h = res.headers.get(XSRF_HEADER);
        if (h) {
          this.xsrf.set(h);
        }
      }),
      map(() => void 0)
    );
  }
}

interface AuthResponse {
  accessToken:  string;
  refreshToken: string;
  tokenType:    string;
  expiresIn:    number;
  username:     string;
  roles:        string;
}
