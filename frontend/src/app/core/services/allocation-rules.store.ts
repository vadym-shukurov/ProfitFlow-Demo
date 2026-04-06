import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { forkJoin, finalize } from 'rxjs';

import { readApiErrorMessage } from '../http/http-error.util';
import { NotificationService } from './notification.service';
import {
  ActivityDto,
  ActivityProductRuleDto,
  ProductDto,
  ResourceActivityRuleDto,
  ResourceCostDto,
} from '../models/api.models';

/**
 * Signal-based store for the Allocation Rules page.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Load reference data (activities, products, resource costs) and both
 *       rule sets in a single parallel batch request.</li>
 *   <li>Allow the user to edit rule rows in memory, then save with a single PUT.</li>
 *   <li>Validate driver weights are positive numbers before saving.</li>
 * </ul>
 *
 * All mutations surface toast notifications via {@link NotificationService}.
 */
@Injectable({ providedIn: 'root' })
export class AllocationRulesStore {
  private readonly http   = inject(HttpClient);
  private readonly notify = inject(NotificationService);

  readonly activities           = signal<ActivityDto[]>([]);
  readonly products             = signal<ProductDto[]>([]);
  readonly resources            = signal<ResourceCostDto[]>([]);
  readonly resourceActivityRules = signal<ResourceActivityRuleDto[]>([]);
  readonly activityProductRules  = signal<ActivityProductRuleDto[]>([]);
  readonly loading              = signal(false);
  readonly saving               = signal(false);
  readonly error                = signal<string | null>(null);

  /** Loads all reference data and rule sets in a single parallel batch. */
  loadAll(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      activities: this.http.get<ActivityDto[]>('/api/v1/activities'),
      products:   this.http.get<ProductDto[]>('/api/v1/products'),
      resources:  this.http.get<ResourceCostDto[]>('/api/v1/resource-costs'),
      ra:         this.http.get<ResourceActivityRuleDto[]>('/api/v1/rules/resource-to-activity'),
      ap:         this.http.get<ActivityProductRuleDto[]>('/api/v1/rules/activity-to-product'),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: bundle => {
          this.activities.set(bundle.activities);
          this.products.set(bundle.products);
          this.resources.set(bundle.resources);
          this.resourceActivityRules.set(bundle.ra);
          this.activityProductRules.set(bundle.ap);
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  /**
   * Validates and saves the resource-to-activity rules.
   * Emits a success toast on completion and re-loads from the API to confirm.
   */
  saveResourceActivityRules(onDone?: () => void): void {
    const validationError = this.validateRules(
      this.resourceActivityRules(),
      ['resourceId', 'activityId'],
    );
    if (validationError) {
      this.error.set(validationError);
      this.notify.warning(validationError);
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    this.http
      .put<void>('/api/v1/rules/resource-to-activity', this.resourceActivityRules())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notify.success('Resource → Activity rules saved.');
          this.loadAll();
          onDone?.();
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  /**
   * Validates and saves the activity-to-product rules.
   * Emits a success toast on completion and re-loads from the API to confirm.
   */
  saveActivityProductRules(onDone?: () => void): void {
    const validationError = this.validateRules(
      this.activityProductRules(),
      ['activityId', 'productId'],
    );
    if (validationError) {
      this.error.set(validationError);
      this.notify.warning(validationError);
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    this.http
      .put<void>('/api/v1/rules/activity-to-product', this.activityProductRules())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notify.success('Activity → Product rules saved.');
          this.loadAll();
          onDone?.();
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  // ── In-memory editing helpers ─────────────────────────────────────────────

  addResourceActivityDraft(): void {
    this.resourceActivityRules.update(rows => [
      ...rows, { resourceId: '', activityId: '', driverWeight: 1 },
    ]);
  }

  removeResourceActivityRow(index: number): void {
    this.resourceActivityRules.update(rows => rows.filter((_, i) => i !== index));
  }

  patchResourceActivityRow(index: number, patch: Partial<ResourceActivityRuleDto>): void {
    this.resourceActivityRules.update(rows =>
      rows.map((row, i) => (i === index ? { ...row, ...patch } : row)),
    );
  }

  addActivityProductDraft(): void {
    this.activityProductRules.update(rows => [
      ...rows, { activityId: '', productId: '', driverWeight: 1 },
    ]);
  }

  removeActivityProductRow(index: number): void {
    this.activityProductRules.update(rows => rows.filter((_, i) => i !== index));
  }

  patchActivityProductRow(index: number, patch: Partial<ActivityProductRuleDto>): void {
    this.activityProductRules.update(rows =>
      rows.map((row, i) => (i === index ? { ...row, ...patch } : row)),
    );
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /**
   * Validates that all rows have non-empty ID fields and a positive driver weight.
   * Returns an error message if invalid, or `null` if valid.
   */
  private validateRules(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rows: any[],
    idFields: string[],
  ): string | null {
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i] as Record<string, unknown>;
      for (const field of idFields) {
        const val = row[field];
        if (!val || String(val).trim() === '') {
          return `Row ${i + 1}: please select a value for "${field}".`;
        }
      }
      if (!(Number(row['driverWeight']) > 0)) {
        return `Row ${i + 1}: driver weight must be a positive number.`;
      }
    }
    return null;
  }
}
