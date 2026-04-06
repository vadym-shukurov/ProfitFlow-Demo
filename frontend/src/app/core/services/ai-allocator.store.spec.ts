import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { AiAllocatorStore } from './ai-allocator.store';
import { NotificationService } from './notification.service';

describe('AiAllocatorStore', () => {
  let store: AiAllocatorStore;
  let httpMock: HttpTestingController;
  let notify: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    notify = jasmine.createSpyObj('NotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AiAllocatorStore,
        { provide: NotificationService, useValue: notify },
      ],
    });

    store   = TestBed.inject(AiAllocatorStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  it('suggest() sets error and does not call HTTP if prompt is blank', () => {
    store.setPrompt('');
    store.suggest();
    httpMock.expectNone('/api/v1/ai/suggest');
    expect(store.error()).toBeTruthy();
  });

  it('suggest() sets error if prompt exceeds 500 characters', () => {
    store.setPrompt('a'.repeat(501));
    store.suggest();
    httpMock.expectNone('/api/v1/ai/suggest');
    expect(store.error()).toContain('500 characters');
  });

  it('suggest() calls the API and updates result on success', () => {
    const response = { suggestedActivityName: 'IT', suggestedAllocationDriver: 'Headcount' };
    store.setPrompt('Cloud hosting costs');
    store.suggest();

    const req = httpMock.expectOne('/api/v1/ai/suggest');
    req.flush(response);

    expect(store.result()).toEqual(response);
    expect(store.loading()).toBeFalse();
    expect(notify.success).toHaveBeenCalled();
  });

  it('suggest() sets error signal on API failure', () => {
    store.setPrompt('Some cost description');
    store.suggest();

    const req = httpMock.expectOne('/api/v1/ai/suggest');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });

    expect(store.error()).toBeTruthy();
    expect(notify.error).toHaveBeenCalled();
  });

  it('reset() clears all state', () => {
    store.setPrompt('test');
    store.reset();

    expect(store.prompt()).toBe('');
    expect(store.result()).toBeNull();
    expect(store.error()).toBeNull();
  });
});
