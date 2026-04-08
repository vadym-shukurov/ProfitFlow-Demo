import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { ProductCatalogStore } from './product-catalog.store';
import { NotificationService } from './notification.service';

describe('ProductCatalogStore', () => {
  let store: ProductCatalogStore;
  let httpMock: HttpTestingController;
  let notify: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    notify = jasmine.createSpyObj('NotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        ProductCatalogStore,
        { provide: NotificationService, useValue: notify },
      ],
    });

    store = TestBed.inject(ProductCatalogStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  describe('load()', () => {
    it('populates products on success', () => {
      store.load();
      const req = httpMock.expectOne('/api/v1/products');
      expect(req.request.method).toBe('GET');
      req.flush([{ id: 'p1', name: 'ProfitFlow' }]);

      expect(store.products()).toEqual([{ id: 'p1', name: 'ProfitFlow' }]);
      expect(store.loading()).toBeFalse();
      expect(store.error()).toBeNull();
    });

    it('sets error and shows toast on failure', () => {
      store.load();
      httpMock.expectOne('/api/v1/products')
        .flush({ message: 'Server error' }, { status: 500, statusText: 'Error' });

      expect(store.loading()).toBeFalse();
      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });

  describe('create()', () => {
    it('POSTs product, shows success, reloads list and calls onSuccess', () => {
      const onSuccess = jasmine.createSpy('onSuccess');

      store.create('ProfitFlow Cloud', onSuccess);

      const post = httpMock.expectOne('/api/v1/products');
      expect(post.request.method).toBe('POST');
      expect(post.request.body).toEqual({ name: 'ProfitFlow Cloud' });
      post.flush({ id: 'p2', name: 'ProfitFlow Cloud' });

      const reload = httpMock.expectOne('/api/v1/products');
      reload.flush([{ id: 'p2', name: 'ProfitFlow Cloud' }]);

      expect(notify.success).toHaveBeenCalledWith('Product "ProfitFlow Cloud" created.');
      expect(onSuccess).toHaveBeenCalled();
      expect(store.saving()).toBeFalse();
    });

    it('sets error and shows toast on POST failure', () => {
      store.create('Bad');
      httpMock.expectOne('/api/v1/products')
        .flush({ message: 'Conflict' }, { status: 409, statusText: 'Conflict' });

      expect(store.saving()).toBeFalse();
      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });
});

