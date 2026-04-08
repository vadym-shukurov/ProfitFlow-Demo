import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

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
  imports: [FormsModule],
  templateUrl: './allocation-rules.page.html',
})
export class AllocationRulesPage implements OnInit {
  protected readonly rules = inject(AllocationRulesStore);

  ngOnInit(): void {
    this.rules.ensureLoaded();
  }

  protected num(raw: string): number {
    const v = Number(raw);
    return Number.isFinite(v) ? v : 0;
  }

  protected saveResourceActivity(): void {
    this.rules.saveResourceActivityRules();
  }

  protected saveActivityProduct(): void {
    this.rules.saveActivityProductRules();
  }
}
