import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { readApiErrorMessage } from '../http/http-error.util';
import { NotificationService } from './notification.service';
import { AiSuggestionDto } from '../models/api.models';

/**
 * Signal-based store for the AI Allocator page.
 *
 * Sends the user's free-text cost description to the backend's mock AI service
 * and surfaces the suggested activity name and allocation driver.
 */
@Injectable({ providedIn: 'root' })
export class AiAllocatorStore {
  private readonly http   = inject(HttpClient);
  private readonly notify = inject(NotificationService);

  /** The current free-text prompt entered by the user. */
  readonly prompt  = signal('');

  /** The most recent suggestion returned by the API. */
  readonly result  = signal<AiSuggestionDto | null>(null);

  /** `true` while the API call is in flight. */
  readonly loading = signal(false);

  /** Inline error message (displayed in the form), or `null`. */
  readonly error   = signal<string | null>(null);

  setPrompt(value: string): void {
    this.prompt.set(value);
  }

  /**
   * Submits the current prompt text to the AI suggestion API.
   * Validates that the prompt is not blank before sending.
   */
  suggest(): void {
    const text = this.prompt().trim();
    if (!text) {
      this.error.set('Describe the spend so the allocator can suggest a category.');
      return;
    }
    if (text.length > 500) {
      this.error.set('Description must be 500 characters or fewer.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);

    this.http
      .post<AiSuggestionDto>('/api/v1/ai/suggest', { text })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: dto => {
          this.result.set(dto);
          this.notify.success('Suggestion received.');
        },
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  /** Resets all transient state (prompt, result, error). */
  reset(): void {
    this.prompt.set('');
    this.result.set(null);
    this.error.set(null);
  }
}
