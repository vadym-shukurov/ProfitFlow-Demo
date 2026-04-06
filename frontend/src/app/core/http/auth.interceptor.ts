import {
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
  HttpErrorResponse
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

/**
 * HTTP interceptor that manages JWT access tokens and handles token expiry silently.
 *
 * ## Behaviour
 * - Adds `Authorization: Bearer <token>` to every API request when a token is present.
 * - On **401 Unauthorized**:
 *   1. If a refresh token is available, attempts a silent token refresh.
 *   2. If the refresh succeeds, retries the original request with the new access token.
 *   3. If the refresh fails, or no refresh token exists, clears the session and
 *      redirects to the login page.
 * - On **403 Forbidden**: navigates to the dashboard root without clearing the
 *   session (the user is authenticated but lacks permission for this action).
 * - Passes through all non-API requests (e.g. Google Fonts) untouched.
 *
 * ## Security note
 * The refresh request itself is not intercepted (it goes directly to the backend),
 * preventing an infinite retry loop on a failed refresh.
 */
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  const isApiRequest     = req.url.startsWith('/api/');
  const isRefreshRequest = req.url === '/api/v1/auth/refresh';

  const outbound = attachToken(req, auth.token(), isApiRequest);

  return next(outbound).pipe(
    catchError((err: HttpErrorResponse) => {
      if (!isApiRequest) {
        return throwError(() => err);
      }

      if (err.status === 401 && !isRefreshRequest && auth.hasRefreshToken()) {
        // Silent token refresh: swap the expired access token for a new one
        return auth.refresh().pipe(
          switchMap(newAccessToken => {
            // Retry the original request with the freshly issued access token
            const retried = attachToken(req, newAccessToken, true);
            return next(retried);
          }),
          catchError(refreshErr => {
            // Refresh failed — force re-authentication
            auth.logout();
            return throwError(() => refreshErr);
          })
        );
      }

      if (err.status === 401) {
        auth.logout();
      } else if (err.status === 403) {
        router.navigate(['/']);
      }

      return throwError(() => err);
    })
  );
};

/** Clones the request and adds the Authorization header when applicable. */
function attachToken(
  req: HttpRequest<unknown>,
  token: string | null,
  isApiRequest: boolean
): HttpRequest<unknown> {
  if (token && isApiRequest) {
    return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return req;
}
