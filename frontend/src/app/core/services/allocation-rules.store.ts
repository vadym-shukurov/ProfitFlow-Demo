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

type RowId = string;
type ResourceActivityRuleRowVm = ResourceActivityRuleDto & { rowId: RowId };
type ActivityProductRuleRowVm = ActivityProductRuleDto & { rowId: RowId };

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
  readonly resourceActivityRules = signal<ResourceActivityRuleRowVm[]>([]);
  readonly activityProductRules  = signal<ActivityProductRuleRowVm[]>([]);
  readonly loading              = signal(false);
  readonly saving               = signal(false);
  readonly error                = signal<string | null>(null);

  private rowIdSeq = 0;
  private loadedOnce = false;

  /** Loads all reference data and rule sets in a single parallel batch. */
  loadAll(opts?: { silent?: boolean }): void {
    const silent = opts?.silent === true;
    if (!silent) {
      this.loading.set(true);
    }
    this.error.set(null);
    forkJoin({
      activities: this.http.get<ActivityDto[]>('/api/v1/activities'),
      products:   this.http.get<ProductDto[]>('/api/v1/products'),
      resources:  this.http.get<ResourceCostDto[]>('/api/v1/resource-costs'),
      ra:         this.http.get<ResourceActivityRuleDto[]>('/api/v1/rules/resource-to-activity'),
      ap:         this.http.get<ActivityProductRuleDto[]>('/api/v1/rules/activity-to-product'),
    })
      .pipe(finalize(() => !silent && this.loading.set(false)))
      .subscribe({
        next: bundle => {
          const prevRa = this.resourceActivityRules();
          const prevAp = this.activityProductRules();

          this.activities.set(bundle.activities);
          this.products.set(bundle.products);
          this.resources.set(bundle.resources);
          this.resourceActivityRules.set(this.rehydrateResourceActivityRows(bundle.ra, prevRa));
          this.activityProductRules.set(this.rehydrateActivityProductRows(bundle.ap, prevAp));
          this.loadedOnce = true;
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  /**
   * Loads data only the first time the page is visited. This keeps in-progress
   * edits (dropdown selections, weights) when navigating away and back.
   */
  ensureLoaded(): void {
    if (this.loadedOnce) {
      return;
    }
    this.loadAll();
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
      .put<void>(
        '/api/v1/rules/resource-to-activity',
        this.resourceActivityRules().map(({ rowId: _rowId, ...dto }) => dto),
      )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notify.success('Resource → Activity rules saved.');
          this.warnIfMissingProductAllocationRules();
          this.loadAll({ silent: true });
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
      .put<void>(
        '/api/v1/rules/activity-to-product',
        this.activityProductRules().map(({ rowId: _rowId, ...dto }) => dto),
      )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notify.success('Activity → Product rules saved.');
          this.loadAll({ silent: true });
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
      ...rows,
      { rowId: this.newRowId(), resourceId: '', activityId: '', driverWeight: 1 },
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
      ...rows,
      { rowId: this.newRowId(), activityId: '', productId: '', driverWeight: 1 },
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
    const normalizeCell = (rawValue: unknown): string => {
      if (rawValue == null) {
        return '';
      }
      if (typeof rawValue === 'string') {
        return rawValue;
      }
      if (typeof rawValue === 'number' || typeof rawValue === 'boolean') {
        return String(rawValue);
      }
      return '';
    };

    const validateRow = (rowIndex: number, row: Record<string, unknown>): string | null => {
      for (const field of idFields) {
        const value = normalizeCell(row[field]);
        if (value.trim() === '') {
          return `Row ${rowIndex + 1}: please select a value for "${field}".`;
        }
      }
      if (Number(row['driverWeight']) <= 0) {
        return `Row ${rowIndex + 1}: driver weight must be a positive number.`;
      }
      return null;
    };

    for (let i = 0; i < rows.length; i++) {
      const err = validateRow(i, rows[i] as Record<string, unknown>);
      if (err) {
        return err;
      }
    }
    return null;
  }

  private warnIfMissingProductAllocationRules(): void {
    const targetedActivityIds = new Set(this.resourceActivityRules().map(r => r.activityId).filter(Boolean));
    if (targetedActivityIds.size === 0) return;

    const coveredActivityIds = new Set(this.activityProductRules().map(r => r.activityId).filter(Boolean));
    const missingIds = [...targetedActivityIds].filter(id => !coveredActivityIds.has(id));
    if (missingIds.length === 0) return;

    const activityNameById = new Map(this.activities().map(a => [a.id, a.name] as const));
    const names = missingIds.map(id => activityNameById.get(id) ?? id);
    const list =
      names.length <= 3
        ? names.join(', ')
        : `${names.slice(0, 3).join(', ')} (+${names.length - 3} more)`;

    this.notify.warning(
      `Heads up: Allocation will fail until you add Activity → Product rules for ${list}.`,
    );
  }

  private newRowId(): RowId {
    this.rowIdSeq += 1;
    return `rule-row-${this.rowIdSeq}`;
  }

  private rehydrateResourceActivityRows(
    serverRows: ResourceActivityRuleDto[],
    prevRows: ResourceActivityRuleRowVm[],
  ): ResourceActivityRuleRowVm[] {
    const key = (r: Pick<ResourceActivityRuleDto, 'resourceId' | 'activityId'>) =>
      `${r.resourceId}::${r.activityId}`;
    const prevByKey = new Map(prevRows.map(r => [key(r), r.rowId] as const));

    return serverRows.map(r => ({
      ...r,
      rowId: prevByKey.get(key(r)) ?? this.newRowId(),
    }));
  }

  private rehydrateActivityProductRows(
    serverRows: ActivityProductRuleDto[],
    prevRows: ActivityProductRuleRowVm[],
  ): ActivityProductRuleRowVm[] {
    const key = (r: Pick<ActivityProductRuleDto, 'activityId' | 'productId'>) =>
      `${r.activityId}::${r.productId}`;
    const prevByKey = new Map(prevRows.map(r => [key(r), r.rowId] as const));

    return serverRows.map(r => ({
      ...r,
      rowId: prevByKey.get(key(r)) ?? this.newRowId(),
    }));
  }
}
