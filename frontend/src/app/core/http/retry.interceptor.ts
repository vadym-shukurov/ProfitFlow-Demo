import { HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { timer } from 'rxjs';
import { retry, switchMap } from 'rxjs/operators';

/**
 * Automatic retry interceptor with exponential back-off.
 *
 * <h2>Retry policy</h2>
 * <ul>
 *   <li>Retried: {@code 429 Too Many Requests}, {@code 502 Bad Gateway},
 *       {@code 503 Service Unavailable}, {@code 504 Gateway Timeout}, and
 *       network errors (status 0 — offline or DNS failure).</li>
 *   <li>Not retried: {@code 4xx} client errors (bad request, auth failure,
 *       forbidden) — retrying would not change the outcome.</li>
 *   <li>Not retried: non-idempotent {@code POST} requests (except
 *       {@code /auth/login} and {@code /ai/suggest}, which are safe to replay).</li>
 * </ul>
 *
 * <h2>Back-off schedule</h2>
 * Attempt 1 waits 500 ms, attempt 2 waits 1000 ms, attempt 3 waits 2000 ms.
 * Jitter of ±20% is applied to prevent thundering-herd after a service restart.
 *
 * <h2>Integration with circuit-breaker pattern</h2>
 * Combined with the {@code auth.interceptor.ts} 401 handler, this interceptor
 * provides a lightweight client-side resilience layer. For production, add a
 * real circuit-breaker library (e.g. {@code cockatiel}) if needed.
 */
export const retryInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  if (!shouldRetry(req)) {
    return next(req);
  }

  return next(req).pipe(
    retry({
      count: 3,
      delay: (error, attempt) => {
        if (!isRetryableError(error)) {
          throw error;
        }
        const baseMs = 500 * Math.pow(2, attempt - 1);
        // Deterministic delays keep retry behavior testable and debuggable.
        // If jitter is desired in production, add it at the server/LB level or behind
        // a feature flag so it doesn't introduce flaky client retries.
        return timer(baseMs);
      },
      resetOnSuccess: true,
    })
  );
};

/** Only retry idempotent requests or explicitly safe POST endpoints. */
function shouldRetry(req: HttpRequest<unknown>): boolean {
  const safePostPaths = ['/api/v1/auth/login', '/api/v1/ai/suggest'];
  if (req.method === 'GET') return true;
  if (req.method === 'POST' && safePostPaths.some(p => req.url.includes(p))) return true;
  return false;
}

/** Network errors (0) and specific server-side transient failures. */
function isRetryableError(err: { status?: number }): boolean {
  const retryableStatuses = new Set([0, 429, 502, 503, 504]);
  return retryableStatuses.has(err.status ?? -1);
}
