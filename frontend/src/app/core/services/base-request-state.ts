import { signal } from '@angular/core';

/**
 * Factory that creates a standard set of reactive signals for tracking the state
 * of an asynchronous HTTP operation.
 *
 * <h2>Rationale</h2>
 * Every Angular signal-based store in ProfitFlow follows the same pattern:
 * a {@code loading} flag, a {@code saving} flag, and an inline {@code error} message.
 * Centralising this pattern eliminates boilerplate duplication and ensures a
 * consistent state-management contract across all stores.
 *
 * <h2>Usage</h2>
 * ```ts
 * export class MyStore {
 *   private readonly state = createRequestState();
 *   readonly loading = this.state.loading;
 *   readonly saving  = this.state.saving;
 *   readonly error   = this.state.error;
 * }
 * ```
 */
export function createRequestState() {
  return {
    /** `true` while a GET (read) request is in flight. */
    loading: signal(false),

    /** `true` while a POST/PUT (write) request is in flight. */
    saving:  signal(false),

    /** Last API error message for inline display, or `null` when no error is present. */
    error:   signal<string | null>(null),
  };
}

/** Type returned by {@link createRequestState}. */
export type RequestState = ReturnType<typeof createRequestState>;
