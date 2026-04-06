import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';

import { readApiErrorMessage } from '../http/http-error.util';
import { NotificationService } from './notification.service';
import {
  ActivityDto,
  AllocationRunResultDto,
  ProductDto,
  ResourceCostDto,
} from '../models/api.models';

// ── View-model types ──────────────────────────────────────────────────────────

/** A named node in the ECharts Sankey diagram. */
export interface SankeyNode {
  name: string;
}

/** A directed cost-flow edge in the ECharts Sankey diagram. */
export interface SankeyLink {
  source: string;
  target: string;
  value: number;
}

/** The complete Sankey graph: nodes + directed links. */
export interface SankeyData {
  nodes: SankeyNode[];
  links: SankeyLink[];
}

/** A row in the product-profitability table on the CFO Dashboard. */
export interface ProductCostRow {
  name: string;
  cost: number;
  currencyCode: string;
  /** Cost as a percentage of the total product spend (0–100). */
  pct: number;
}

// ── Store ─────────────────────────────────────────────────────────────────────

/**
 * Signal-based store for the CFO Dashboard page.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Load catalog data (resources, activities, products) on page init.</li>
 *   <li>Trigger and track the allocation run.</li>
 *   <li>Expose derived computed signals for the Sankey diagram and
 *       product-profitability table, resolving backend IDs to human-readable names.</li>
 * </ul>
 *
 * <h2>Name resolution</h2>
 * The Sankey diagram must show human-readable labels, but the backend allocation
 * result only carries IDs. This store maintains a name map keyed by {@code "KIND:id"}
 * (e.g. {@code "RESOURCE:uuid-1"}) and resolves it inside the {@link sankeyData} and
 * {@link productRows} computed signals.
 */
@Injectable({ providedIn: 'root' })
export class CfoDashboardStore {
  private readonly http   = inject(HttpClient);
  private readonly notify = inject(NotificationService);

  // Raw catalog signals (private — exposed only through computed views)
  private readonly resources  = signal<ResourceCostDto[]>([]);
  private readonly activities = signal<ActivityDto[]>([]);
  private readonly products   = signal<ProductDto[]>([]);
  private readonly runResult  = signal<AllocationRunResultDto | null>(null);

  /** `true` while catalog data is being fetched. */
  readonly loading = signal(false);

  /** `true` while the allocation engine is executing on the backend. */
  readonly running = signal(false);

  /** Last API error message for inline display, or `null` when no error is present. */
  readonly error = signal<string | null>(null);

  // ── Derived computed signals ──────────────────────────────────────────────

  /** Sum of all resource cost amounts (used as the Sankey total). */
  readonly totalResourceSpend = computed(() =>
    this.resources().reduce((sum, r) => sum + r.amount, 0),
  );

  /** `true` when an allocation result is available. */
  readonly hasResult = computed(() => this.runResult() !== null);

  /** Number of resource IDs that had no allocation rule in the last run. */
  readonly unallocatedCount = computed(
    () => this.runResult()?.unallocatedResourceIds.length ?? 0,
  );

  /**
   * Product profitability rows, sorted by cost descending.
   * ID references are resolved to human-readable names using the catalog.
   */
  readonly productRows = computed((): ProductCostRow[] => {
    const result = this.runResult();
    if (!result) return [];

    const nameMap = this.buildNameMap();
    const costs   = result.productCosts;
    const total   = Object.values(costs).reduce((s, v) => s + v, 0);

    return Object.entries(costs)
      .map(([id, cost]) => ({
        name:         nameMap.get(`PRODUCT:${id}`) ?? id,
        cost,
        currencyCode: result.flows[0]?.currencyCode ?? 'USD',
        pct:          total > 0 ? (cost / total) * 100 : 0,
      }))
      .sort((a, b) => b.cost - a.cost);
  });

  /**
   * Sankey diagram data in ECharts-compatible format (nodes + links).
   * Node names are resolved from the catalog. Flows with zero value are
   * included since ECharts handles them gracefully.
   */
  readonly sankeyData = computed((): SankeyData => {
    const result = this.runResult();
    if (!result || result.flows.length === 0) {
      return { nodes: [], links: [] };
    }

    const nameMap  = this.buildNameMap();
    const nodeNames = new Set<string>();
    const links: SankeyLink[] = [];

    for (const flow of result.flows) {
      const fromName = nameMap.get(`${flow.fromKind}:${flow.fromId}`) ?? flow.fromId;
      const toName   = nameMap.get(`${flow.toKind}:${flow.toId}`)   ?? flow.toId;
      nodeNames.add(fromName);
      nodeNames.add(toName);
      links.push({ source: fromName, target: toName, value: flow.amount });
    }

    return {
      nodes: [...nodeNames].map(name => ({ name })),
      links,
    };
  });

  // ── Actions ───────────────────────────────────────────────────────────────

  /**
   * Loads all catalog data in parallel (resources, activities, products).
   * Should be called on page init so the name-resolution maps are populated
   * before the first allocation run.
   */
  loadCatalogs(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      resources:  this.http.get<ResourceCostDto[]>('/api/v1/resource-costs'),
      activities: this.http.get<ActivityDto[]>('/api/v1/activities'),
      products:   this.http.get<ProductDto[]>('/api/v1/products'),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ resources, activities, products }) => {
          this.resources.set(resources);
          this.activities.set(activities);
          this.products.set(products);
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  /**
   * Triggers a full ABC allocation run on the backend.
   * On success, refreshes catalog data so the name-resolution maps stay in sync
   * with any new entities the allocation may have created.
   */
  runAllocation(): void {
    this.running.set(true);
    this.error.set(null);
    this.http
      .post<AllocationRunResultDto>('/api/v1/allocations/run', {})
      .pipe(finalize(() => this.running.set(false)))
      .subscribe({
        next: result => {
          this.runResult.set(result);
          this.notify.success('Allocation run completed.');
          // Refresh catalog to keep name-map current after potential data changes
          this.loadCatalogs();
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /**
   * Builds a lookup map from {@code "KIND:id"} to a human-readable display name.
   * Called lazily inside computed signals; result is not memoised since the signal
   * graph already handles re-computation.
   */
  private buildNameMap(): Map<string, string> {
    const map = new Map<string, string>();
    for (const r of this.resources())  map.set(`RESOURCE:${r.id}`,  r.label);
    for (const a of this.activities()) map.set(`ACTIVITY:${a.id}`,  a.name);
    for (const p of this.products())   map.set(`PRODUCT:${p.id}`,   p.name);
    return map;
  }
}
