import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { ActivityCatalogStore } from './activity-catalog.store';
import { NotificationService } from './notification.service';

describe('ActivityCatalogStore', () => {
  let store: ActivityCatalogStore;
  let httpMock: HttpTestingController;
  let notify: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    notify = jasmine.createSpyObj('NotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        ActivityCatalogStore,
        { provide: NotificationService, useValue: notify },
      ],
    });

    store = TestBed.inject(ActivityCatalogStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  describe('load()', () => {
    it('populates activities on success', () => {
      store.load();
      const req = httpMock.expectOne('/api/v1/activities');
      expect(req.request.method).toBe('GET');
      req.flush([{ id: 'a1', name: 'Support' }]);

      expect(store.activities()).toEqual([{ id: 'a1', name: 'Support' }]);
      expect(store.loading()).toBeFalse();
      expect(store.error()).toBeNull();
    });

    it('sets error and shows toast on failure', () => {
      store.load();
      httpMock.expectOne('/api/v1/activities')
        .flush({ message: 'Server error' }, { status: 500, statusText: 'Error' });

      expect(store.loading()).toBeFalse();
      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });

  describe('create()', () => {
    it('POSTs activity, shows success, reloads list and calls onSuccess', () => {
      const onSuccess = jasmine.createSpy('onSuccess');

      store.create('Customer Support', onSuccess);

      const post = httpMock.expectOne('/api/v1/activities');
      expect(post.request.method).toBe('POST');
      expect(post.request.body).toEqual({ name: 'Customer Support' });
      post.flush({ id: 'a2', name: 'Customer Support' });

      const reload = httpMock.expectOne('/api/v1/activities');
      reload.flush([{ id: 'a2', name: 'Customer Support' }]);

      expect(notify.success).toHaveBeenCalledWith('Activity "Customer Support" created.');
      expect(onSuccess).toHaveBeenCalled();
      expect(store.saving()).toBeFalse();
    });

    it('sets error and shows toast on POST failure', () => {
      store.create('Bad');
      httpMock.expectOne('/api/v1/activities')
        .flush({ message: 'Conflict' }, { status: 409, statusText: 'Conflict' });

      expect(store.saving()).toBeFalse();
      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });
});

