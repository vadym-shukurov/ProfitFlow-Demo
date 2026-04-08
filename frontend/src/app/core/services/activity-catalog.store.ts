import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { ActivityDto } from '../models/api.models';
import { readApiErrorMessage } from '../http/http-error.util';
import { NotificationService } from './notification.service';

@Injectable({ providedIn: 'root' })
export class ActivityCatalogStore {
  private readonly http = inject(HttpClient);
  private readonly notify = inject(NotificationService);

  readonly activities = signal<ActivityDto[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http
      .get<ActivityDto[]>('/api/v1/activities')
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: rows => this.activities.set(rows),
        error: (err: HttpErrorResponse) => {
          const msg = readApiErrorMessage(err);
          this.error.set(msg);
          this.notify.error(msg);
        },
      });
  }

  create(name: string, onSuccess?: () => void): void {
    this.saving.set(true);
    this.error.set(null);
    this.http
      .post<ActivityDto>('/api/v1/activities', { name })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notify.success(`Activity "${name}" created.`);
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

