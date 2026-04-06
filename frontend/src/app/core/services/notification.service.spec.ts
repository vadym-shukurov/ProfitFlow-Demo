import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [NotificationService] });
    service = TestBed.inject(NotificationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('success() adds a notification with severity=success', () => {
    service.success('Saved successfully.');
    const list = service.notifications();
    expect(list.length).toBe(1);
    expect(list[0].severity).toBe('success');
    expect(list[0].message).toBe('Saved successfully.');
  });

  it('error() adds a notification with severity=error', () => {
    service.error('Something went wrong.');
    expect(service.notifications()[0].severity).toBe('error');
  });

  it('warning() adds a notification with severity=warning', () => {
    service.warning('Check your input.');
    expect(service.notifications()[0].severity).toBe('warning');
  });

  it('info() adds a notification with severity=info', () => {
    service.info('New update available.');
    expect(service.notifications()[0].severity).toBe('info');
  });

  it('dismiss() removes the notification by id', () => {
    service.success('Hello');
    const id = service.notifications()[0].id;
    service.dismiss(id);
    expect(service.notifications().length).toBe(0);
  });

  it('auto-dismisses after the configured duration', fakeAsync(() => {
    service.success('Auto-dismiss test', 100);
    expect(service.notifications().length).toBe(1);
    tick(100);
    expect(service.notifications().length).toBe(0);
  }));

  it('sticky notification (duration=0) does not auto-dismiss', fakeAsync(() => {
    service.error('Sticky error', 0);
    tick(60_000); // Advance a long time
    expect(service.notifications().length).toBe(1);
  }));

  it('caps at MAX_NOTIFICATIONS by trimming oldest entries', () => {
    // Push 6 notifications; max is 5
    for (let i = 0; i < 6; i++) {
      service.info(`Message ${i}`);
    }
    expect(service.notifications().length).toBe(5);
  });

  it('assigns unique ids to all notifications', () => {
    service.success('A');
    service.success('B');
    service.success('C');
    const ids = service.notifications().map(n => n.id);
    const uniqueIds = new Set(ids);
    expect(uniqueIds.size).toBe(ids.length);
  });
});
