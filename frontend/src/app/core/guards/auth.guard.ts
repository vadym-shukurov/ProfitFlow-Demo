import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from '../services/auth.service';

/**
 * Route guard that redirects unauthenticated users to the login page.
 *
 * Applied to the shell layout route, protecting all child routes (dashboard,
 * ledger, rules, AI allocator) behind a single guard definition.
 *
 * @example
 * ```ts
 * { path: '', component: ShellLayoutComponent, canActivate: [authGuard], children: [...] }
 * ```
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  // If the tab was refreshed we intentionally lose the in-memory access token.
  // Recover seamlessly using the refresh token stored in sessionStorage.
  if (auth.hasRefreshToken()) {
    return auth.refresh().pipe(
      map(() => true),
      catchError(() =>
        of(router.createUrlTree(['/login'], {
          queryParams: { returnUrl: state.url },
        }))
      )
    );
  }

  // Preserve the intended URL so we can redirect back after login
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};
