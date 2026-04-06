import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { signal } from '@angular/core';
import { firstValueFrom, of, throwError } from 'rxjs';

import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  /** Writable signal for isAuthenticated state — reset in beforeEach. */
  const isAuthenticatedSignal = signal<boolean>(false);

  /** Helper: invoke the guard as Angular would */
  function runGuard(url = '/dashboard') {
    const routeSnapshot = {} as ActivatedRouteSnapshot;
    const stateSnapshot = { url } as RouterStateSnapshot;

    return TestBed.runInInjectionContext(() =>
      authGuard(routeSnapshot, stateSnapshot)
    );
  }

  beforeEach(() => {
    isAuthenticatedSignal.set(false);

    authService = jasmine.createSpyObj<AuthService>(
      'AuthService',
      ['hasRefreshToken', 'refresh'],
      { isAuthenticated: isAuthenticatedSignal }
    );
    router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router,      useValue: router      },
      ],
    });
  });

  it('returns true when user is authenticated', () => {
    isAuthenticatedSignal.set(true);

    const result = runGuard();

    expect(result).toBe(true);
  });

  it('redirects to /login when not authenticated', () => {
    authService.hasRefreshToken.and.returnValue(false);
    const loginTree = {} as ReturnType<Router['createUrlTree']>;
    router.createUrlTree.and.returnValue(loginTree);

    const result = runGuard('/protected');

    expect(result).toBe(loginTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(
      ['/login'],
      { queryParams: { returnUrl: '/protected' } }
    );
  });

  it('preserves the intended return URL in redirect query params', () => {
    authService.hasRefreshToken.and.returnValue(false);
    router.createUrlTree.and.returnValue({} as any);

    runGuard('/allocation-rules');

    expect(router.createUrlTree).toHaveBeenCalledWith(
      ['/login'],
      { queryParams: { returnUrl: '/allocation-rules' } }
    );
  });

  it('does not redirect when authenticated', () => {
    isAuthenticatedSignal.set(true);

    runGuard('/dashboard');

    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('attempts silent refresh when refresh token exists', async () => {
    authService.hasRefreshToken.and.returnValue(true);
    authService.refresh.and.returnValue(of('new-access-token'));

    const result = runGuard('/ledger');

    expect(typeof result).not.toBe('boolean');
    await expectAsync(firstValueFrom(result as any)).toBeResolvedTo(true);
  });

  it('redirects to /login if silent refresh fails', async () => {
    const loginTree = {} as ReturnType<Router['createUrlTree']>;
    router.createUrlTree.and.returnValue(loginTree);
    authService.hasRefreshToken.and.returnValue(true);
    authService.refresh.and.returnValue(throwError(() => new Error('refresh failed')));

    const result = runGuard('/ledger');

    await expectAsync(firstValueFrom(result as any)).toBeResolvedTo(loginTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(
      ['/login'],
      { queryParams: { returnUrl: '/ledger' } }
    );
  });
});
