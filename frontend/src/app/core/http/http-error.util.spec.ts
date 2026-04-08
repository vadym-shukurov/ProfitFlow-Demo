import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

import { readApiErrorMessage, readCorrelationId } from './http-error.util';

function makeError(status: number, body?: unknown, headers?: Record<string, string>): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: body,
    url:   '/test',
    headers: headers ? new HttpHeaders(headers) : undefined,
  });
}

describe('readApiErrorMessage', () => {
  it('returns the message from a structured ApiErrorDto body', () => {
    const err = makeError(400, { message: 'Label is required.' });
    expect(readApiErrorMessage(err)).toBe('Label is required.');
  });

  it('returns a plain string body', () => {
    const err = makeError(400, 'Bad request payload');
    expect(readApiErrorMessage(err)).toBe('Bad request payload');
  });

  it('returns 401 user-friendly message for status 401', () => {
    const err = makeError(401, null);
    expect(readApiErrorMessage(err)).toContain('session has expired');
  });

  it('returns 403 user-friendly message for status 403', () => {
    const err = makeError(403, null);
    expect(readApiErrorMessage(err)).toContain('permission');
  });

  it('returns network error message for status 0', () => {
    const err = makeError(0, null);
    expect(readApiErrorMessage(err)).toContain('server');
  });

  it('returns 429 user-friendly message', () => {
    const err = makeError(429, null);
    expect(readApiErrorMessage(err)).toContain('Too many requests');
  });

  it('returns 500 user-friendly message', () => {
    const err = makeError(500, null);
    expect(readApiErrorMessage(err)).toContain('unexpected server error');
  });

  it('returns 404 user-friendly message for status 404 when body is empty', () => {
    const err = makeError(404, null);
    expect(readApiErrorMessage(err)).toContain('not found');
  });

  it('returns 503 user-friendly message for status 503 when body is empty', () => {
    const err = makeError(503, null);
    expect(readApiErrorMessage(err)).toContain('maintenance');
  });

  it('falls back to Angular error message for unknown status', () => {
    const err = makeError(418, null);
    // 418 has no mapping; falls back to err.message
    expect(typeof readApiErrorMessage(err)).toBe('string');
  });
});

describe('readCorrelationId', () => {
  it('returns the X-Correlation-Id header value', () => {
    const err = makeError(500, null, { 'X-Correlation-Id': 'abc-123' });
    expect(readCorrelationId(err)).toBe('abc-123');
  });

  it('returns null when the header is absent', () => {
    const err = makeError(500, null);
    expect(readCorrelationId(err)).toBeNull();
  });
});
