import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { readApiErrorMessage } from '../http/http-error.util';
import { NotificationService } from './notification.service';
import { ResourceCostDto } from '../models/api.models';

/**
 * Signal-based store for the Cost Ledger page.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Load the list of resource costs from the backend ({@code /api/v1/resource-costs}).</li>
 *   <li>Create a single new cost (POST JSON body).</li>
 *   <li>Bulk-import costs from a plain-text CSV body.</li>
 * </ul>
 *
 * State is expressed entirely as Angular Signals so components can react
 * to changes without subscribing to Observables or using NgRx.
 *
 * Errors are surfaced both as a {@link error} signal (inline in the form) and
 * as a global toast via {@link NotificationService} for ambient visibility.
 *
 * @example
 * ```ts
 * protected readonly ledger = inject(CostLedgerStore);
 * ngOnInit() { this.ledger.load(); }
 * ```
 */
@Injectable({ providedIn: 'root' })
export class CostLedgerStore {
  private readonly http   = inject(HttpClient);
  private readonly notify = inject(NotificationService);

  /** Current list of resource costs, updated after every successful mutation. */
  readonly costs = signal<ResourceCostDto[]>([]);

  /** `true` while a GET request is in flight. */
  readonly loading = signal(false);

  /** `true` while a POST/import request is in flight. */
  readonly saving = signal(false);

  /** Last API error message for inline display, or `null` when no error is present. */
  readonly error = signal<string | null>(null);

  /** Fetches all resource costs from the API and updates {@link costs}. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .get<ResourceCostDto[]>('/api/v1/resource-costs')
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: rows => this.costs.set(rows),
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  /**
   * Creates a new resource cost and refreshes the list on success.
   *
   * @param label        human-readable GL line description
   * @param amount       non-negative monetary value
   * @param currencyCode ISO 4217 code; defaults to `USD` if falsy
   * @param onSuccess    optional callback invoked after the list refreshes
   */
  create(
    label: string,
    amount: number,
    currencyCode: string,
    onSuccess?: () => void,
  ): void {
    this.saving.set(true);
    this.error.set(null);
    this.http
      .post<ResourceCostDto>('/api/v1/resource-costs', {
        label,
        amount,
        currencyCode: currencyCode || 'USD',
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notify.success(`Cost "${label}" created.`);
          this.load();
          onSuccess?.();
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  /**
   * Imports costs from a plain-text CSV string and refreshes the list on success.
   *
   * @param csv       raw CSV text (Content-Type: text/plain)
   * @param onSuccess optional callback invoked after the list refreshes
   */
  importCsv(csv: string, onSuccess?: () => void): void {
    this.saving.set(true);
    this.error.set(null);
    this.http
      .post<ResourceCostDto[]>('/api/v1/resource-costs/import', csv, {
        headers: { 'Content-Type': 'text/plain' },
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: rows => {
          this.notify.success(`Imported ${rows.length} cost${rows.length !== 1 ? 's' : ''} successfully.`);
          this.load();
          onSuccess?.();
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }
}
