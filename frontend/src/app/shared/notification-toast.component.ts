import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { NotificationService, Notification } from '../core/services/notification.service';

/**
 * Global toast notification host — rendered once in the shell layout.
 *
 * Displays all active {@link Notification} items from {@link NotificationService}
 * in a fixed corner overlay. Each toast has a coloured left border matching its
 * severity and a dismiss button.
 *
 * Toasts automatically disappear after their configured duration.
 */
@Component({
  selector: 'pf-notification-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none"
      role="status"
      aria-live="polite"
      aria-atomic="false"
    >
      @for (n of notify.notifications(); track n.id) {
        <div
          class="pointer-events-auto flex items-start gap-3 rounded-lg border border-divider
                 bg-surface-overlay px-4 py-3 shadow-lg
                 transition-all duration-300 animate-fade-in"
          [class]="severityBorderClass(n)"
          role="alert"
        >
          <!-- Severity icon -->
          <span class="mt-0.5 shrink-0" [class]="severityIconClass(n)" aria-hidden="true">
            @switch (n.severity) {
              @case ('success') { ✓ }
              @case ('error')   { ✗ }
              @case ('warning') { ⚠ }
              @default          { ℹ }
            }
          </span>

          <!-- Message -->
          <p class="flex-1 text-sm pf-text-primary leading-snug">{{ n.message }}</p>

          <!-- Dismiss button -->
          <button
            (click)="notify.dismiss(n.id)"
            class="shrink-0 text-sm pf-text-muted hover:pf-text-secondary transition ml-1"
            [attr.aria-label]="'Dismiss: ' + n.message"
          >×</button>
        </div>
      }
    </div>
  `,
})
export class NotificationToastComponent {
  protected readonly notify = inject(NotificationService);

  protected severityBorderClass(n: Notification): string {
    return {
      success: 'border-l-4 border-l-green-500',
      error:   'border-l-4 border-l-status-error',
      warning: 'border-l-4 border-l-status-warning',
      info:    'border-l-4 border-l-brand-500',
    }[n.severity];
  }

  protected severityIconClass(n: Notification): string {
    return {
      success: 'text-green-600 font-bold',
      error:   'text-status-error font-bold',
      warning: 'text-status-warning font-bold',
      info:    'text-brand-600 font-bold',
    }[n.severity];
  }
}
