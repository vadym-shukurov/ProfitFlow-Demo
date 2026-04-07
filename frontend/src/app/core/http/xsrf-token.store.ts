import { Injectable } from '@angular/core';

/**
 * In-memory CSRF token from {@code X-XSRF-TOKEN} response headers. The matching
 * {@code XSRF-TOKEN} cookie stays HttpOnly on the server.
 */
@Injectable({ providedIn: 'root' })
export class XsrfTokenStore {
  private token: string | null = null;

  get(): string | null {
    return this.token;
  }

  set(value: string | null): void {
    this.token = value;
  }

  clear(): void {
    this.token = null;
  }
}
