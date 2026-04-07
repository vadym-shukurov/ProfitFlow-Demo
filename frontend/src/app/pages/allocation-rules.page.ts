import { Component, OnInit, inject } from '@angular/core';

import { AllocationRulesStore } from '../core/services/allocation-rules.store';

/**
 * Allocation Rules page — lets Finance Managers configure the two-stage
 * ABC rule tables (Resource → Activity and Activity → Product).
 *
 * Both rule tables are edited inline and submitted atomically via PUT to
 * avoid partial saves. Client-side validation (non-empty IDs, positive weights)
 * runs before the API call.
 */
@Component({
  selector: 'app-allocation-rules-page',
  standalone: true,
  templateUrl: './allocation-rules.page.html',
})
export class AllocationRulesPage implements OnInit {
  protected readonly rules = inject(AllocationRulesStore);

  ngOnInit(): void {
    this.rules.loadAll();
  }

  protected num(raw: string): number {
    const v = Number(raw);
    return Number.isFinite(v) ? v : 0;
  }

  protected saveResourceActivity(): void {
    const rows = this.rules.resourceActivityRules();
    const invalid = rows.some(
      (r) => !r.resourceId || !r.activityId || r.driverWeight <= 0,
    );
    if (invalid) {
      this.rules.error.set('Each resource row needs a resource, activity, and a positive driver weight.');
      return;
    }
    this.rules.error.set(null);
    this.rules.saveResourceActivityRules();
  }

  protected saveActivityProduct(): void {
    const rows = this.rules.activityProductRules();
    const invalid = rows.some(
      (r) => !r.activityId || !r.productId || r.driverWeight <= 0,
    );
    if (invalid) {
      this.rules.error.set('Each product row needs an activity, product, and a positive driver weight.');
      return;
    }
    this.rules.error.set(null);
    this.rules.saveActivityProductRules();
  }
}
