import { ErrorHandler } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';
import { retryInterceptor } from './core/http/retry.interceptor';
import { GlobalErrorHandler } from './core/http/global-error-handler';

/**
 * Root application configuration.
 *
 * <h2>HTTP pipeline (interceptors execute in order)</h2>
 * <ol>
 *   <li>{@code authInterceptor} — adds Bearer token; handles 401/403 globally.</li>
 *   <li>{@code retryInterceptor} — retries transient failures with exponential back-off.</li>
 * </ol>
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withFetch(),
      withInterceptors([authInterceptor, retryInterceptor])
    ),
    // Replace Angular's default (console-only) error handler with toast-based one
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
  ],
};
