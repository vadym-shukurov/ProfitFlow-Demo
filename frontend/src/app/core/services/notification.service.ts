import { Injectable, signal } from '@angular/core';

/**
 * Severity levels for user-visible notifications.
 * Ordered by importance: {@code error} > {@code warning} > {@code success} > {@code info}.
 */
export type NotificationSeverity = 'success' | 'error' | 'warning' | 'info';

/** A single toast notification entry. */
export interface Notification {
  readonly id: number;
  readonly message: string;
  readonly severity: NotificationSeverity;
  /** Auto-dismiss delay in ms (0 = sticky until manually dismissed). */
  readonly duration: number;
}

/**
 * Application-wide toast notification bus.
 *
 * <h2>Usage in a service or component</h2>
 * <pre>{@code
 * private readonly notify = inject(NotificationService);
 * notify.success('Allocation run completed.');
 * notify.error('Failed to load data — please try again.');
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * Notifications are auto-dismissed after {@link DEFAULT_DURATION_MS} unless
 * {@code duration: 0} is explicitly requested (sticky errors).
 * At most {@link MAX_NOTIFICATIONS} notifications are shown simultaneously to
 * prevent the screen from being buried.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private static readonly DEFAULT_DURATION_MS = 4000;
  private static readonly ERROR_DURATION_MS = 8000;
  private static readonly MAX_NOTIFICATIONS = 5;

  private counter = 0;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  /** Live list of active notifications (reactive signal). */
  readonly notifications = signal<Notification[]>([]);

  /** Shows a success toast. */
  success(message: string, duration = NotificationService.DEFAULT_DURATION_MS): void {
    this.push({ message, severity: 'success', duration });
  }

  /** Shows an error toast. Longer duration by default to ensure it is read. */
  error(message: string, duration = NotificationService.ERROR_DURATION_MS): void {
    this.push({ message, severity: 'error', duration });
  }

  /** Shows a warning toast. */
  warning(message: string, duration = NotificationService.DEFAULT_DURATION_MS): void {
    this.push({ message, severity: 'warning', duration });
  }

  /** Shows an info toast. */
  info(message: string, duration = NotificationService.DEFAULT_DURATION_MS): void {
    this.push({ message, severity: 'info', duration });
  }

  /** Manually removes a notification before it auto-dismisses. */
  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this.notifications.update(list => list.filter(n => n.id !== id));
  }

  private push(opts: Omit<Notification, 'id'>): void {
    const id = ++this.counter;
    const notification: Notification = { id, ...opts };

    this.notifications.update(list => {
      const trimmed = list.length >= NotificationService.MAX_NOTIFICATIONS
        ? list.slice(-(NotificationService.MAX_NOTIFICATIONS - 1))
        : list;
      return [...trimmed, notification];
    });

    if (opts.duration > 0) {
      const timer = setTimeout(() => this.dismiss(id), opts.duration);
      this.timers.set(id, timer);
    }
  }
}
