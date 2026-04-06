import { Component, inject } from '@angular/core';

import { AiAllocatorStore } from '../core/services/ai-allocator.store';

/**
 * AI Allocator page — provides a natural-language interface for classifying
 * an expense description into an activity category and allocation driver.
 *
 * The user types a free-text description (e.g. "Zendesk subscription") and
 * submits it to `POST /api/v1/ai/suggest`, which returns a suggested activity
 * and driver powered by the backend AI adapter.
 */
@Component({
  selector: 'app-ai-allocator-page',
  standalone: true,
  templateUrl: './ai-allocator.page.html',
})
export class AiAllocatorPage {
  protected readonly store = inject(AiAllocatorStore);
}
