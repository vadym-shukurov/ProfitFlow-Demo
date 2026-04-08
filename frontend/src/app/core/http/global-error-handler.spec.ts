import { TestBed } from '@angular/core/testing';
import { Injector, NgZone } from '@angular/core';

import { GlobalErrorHandler } from './global-error-handler';
import { NotificationService } from '../services/notification.service';

describe('GlobalErrorHandler', () => {
  let handler: GlobalErrorHandler;
  let zone: NgZone;
  let injector: Injector;
  let notify: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    notify = jasmine.createSpyObj('NotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      providers: [
        GlobalErrorHandler,
        { provide: NotificationService, useValue: notify },
      ],
    });

    handler = TestBed.inject(GlobalErrorHandler);
    zone = TestBed.inject(NgZone);
    injector = TestBed.inject(Injector);
    spyOn(injector, 'get').and.callThrough();
    spyOn(console, 'error'); // avoid noise + assert it was called
  });

  it('surfaces Error.message via toast and resolves NotificationService lazily', () => {
    const err = new Error('boom');
    handler.handleError(err);

    expect(console.error).toHaveBeenCalled();
    expect(injector.get).toHaveBeenCalledWith(NotificationService);
    expect(notify.error).toHaveBeenCalledWith('boom', 10_000);
  });

  it('runs toast emission inside Angular zone', () => {
    spyOn(zone, 'run').and.callThrough();
    handler.handleError(new Error('x'));
    expect(zone.run).toHaveBeenCalled();
  });

  it('handles string errors', () => {
    handler.handleError('plain');
    expect(notify.error).toHaveBeenCalledWith('plain', 10_000);
  });

  it('uses a safe fallback message for unknown types', () => {
    handler.handleError({ what: 'ever' });
    expect(notify.error).toHaveBeenCalled();
    const [msg] = notify.error.calls.mostRecent().args;
    expect(typeof msg).toBe('string');
  });
});

