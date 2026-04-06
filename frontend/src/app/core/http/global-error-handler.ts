import { ErrorHandler, Injectable, Injector, NgZone, inject } from '@angular/core';

import { NotificationService } from '../services/notification.service';

/**
 * Global Angular error handler that catches uncaught runtime exceptions and
 * surfaces them as user-visible error toasts rather than crashing silently.
 *
 * ## What this catches
 * - Unhandled Promise rejections inside Angular zones.
 * - Errors thrown synchronously in component lifecycle hooks (`ngOnInit`,
 *   `ngOnChanges`, etc.) that are not caught by component-level try/catch blocks.
 * - Template evaluation errors.
 *
 * ## What this does NOT catch
 * - HTTP errors — handled per-store via `readApiErrorMessage`.
 * - Auth/authorisation failures — handled in `auth.interceptor.ts`.
 *
 * ## Injection pattern
 * `NotificationService` is resolved lazily through `Injector` to avoid a
 * potential circular dependency during the DI graph construction phase.
 * `Injector` itself is captured at construction time (a valid injection context),
 * while `NotificationService` is only resolved on the first call to `handleError`.
 *
 * ## Production note
 * Replace `console.error` with a real observability integration
 * (e.g. Sentry, Datadog RUM) to capture front-end errors with full user context.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  // Captured during construction — valid injection context
  private readonly zone = inject(NgZone);
  private readonly injector = inject(Injector);

  private notify?: NotificationService;

  handleError(error: unknown): void {
    console.error('[GlobalErrorHandler]', error);

    // Lazily resolve NotificationService on first error to avoid circular DI
    if (!this.notify) {
      this.notify = this.injector.get(NotificationService);
    }

    const message = extractMessage(error);

    // Run inside the Angular zone so signal updates trigger change detection
    this.zone.run(() => {
      this.notify!.error(message, 10_000);
    });
  }
}

function extractMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message || 'An unexpected error occurred.';
  }
  if (typeof error === 'string') {
    return error;
  }
  return 'An unexpected application error occurred. Please refresh the page.';
}
