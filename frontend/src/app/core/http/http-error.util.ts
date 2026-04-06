import { HttpErrorResponse } from '@angular/common/http';

import { ApiErrorDto } from '../models/api.models';

/**
 * Extracts a user-facing error message from an Angular {@link HttpErrorResponse}.
 *
 * <h2>Priority chain</h2>
 * <ol>
 *   <li>The {@code message} field from the API's standard {@link ApiErrorDto} body.</li>
 *   <li>A plain-string response body.</li>
 *   <li>A human-readable description derived from the HTTP status code.</li>
 *   <li>The Angular error message string as a last resort.</li>
 * </ol>
 *
 * The function never exposes raw server stack traces or internal error details
 * to the caller — that information lives in the backend logs identified by
 * the {@code correlationId} from the {@code X-Correlation-Id} response header.
 */
export function readApiErrorMessage(err: HttpErrorResponse): string {
  // 1. Structured API error body
  const body = err.error as ApiErrorDto | string | null | undefined;
  if (body && typeof body === 'object' && typeof body.message === 'string') {
    return body.message;
  }

  // 2. Plain string body
  if (typeof body === 'string' && body.trim().length > 0) {
    return body.trim();
  }

  // 3. Status-code fallback messages (user-friendly)
  return httpStatusMessage(err.status) ?? err.message ?? 'Request failed';
}

/**
 * Returns the correlation ID from the response headers if the backend sent one.
 * Clients can quote this ID in support tickets to enable exact log lookup.
 */
export function readCorrelationId(err: HttpErrorResponse): string | null {
  return err.headers?.get('X-Correlation-Id') ?? null;
}

/**
 * Returns a user-friendly description for well-known HTTP status codes, or
 * {@code null} for codes that should fall back to the raw Angular error message.
 */
function httpStatusMessage(status: number): string | null {
  switch (status) {
    case 0:   return 'Could not reach the server — check your internet connection.';
    case 400: return 'The request was invalid. Please check your input and try again.';
    case 401: return 'Your session has expired. Please sign in again.';
    case 403: return 'You do not have permission to perform this action.';
    case 404: return 'The requested resource was not found.';
    case 409: return 'This operation conflicts with the current server state.';
    case 413: return 'The file is too large. Maximum upload size is 5 MB.';
    case 422: return null; // Domain errors have meaningful messages — use them directly
    case 429: return 'Too many requests — please wait a moment before trying again.';
    case 500: return 'An unexpected server error occurred. Please try again shortly.';
    case 502: return 'The server is temporarily unavailable. Retrying…';
    case 503: return 'Service is temporarily down for maintenance. Please try again soon.';
    case 504: return 'The server took too long to respond. Please try again.';
    default:  return null;
  }
}
