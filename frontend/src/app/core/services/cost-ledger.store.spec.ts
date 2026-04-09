import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { CostLedgerStore } from './cost-ledger.store';
import { NotificationService } from './notification.service';

describe('CostLedgerStore', () => {
  let store: CostLedgerStore;
  let httpMock: HttpTestingController;
  let notify: jasmine.SpyObj<NotificationService>;

  const mockCosts = [
    { id: 'id-1', label: 'Servers', amount: 5000, currencyCode: 'USD' },
    { id: 'id-2', label: 'Rent',    amount: 2000, currencyCode: 'USD' },
  ];

  beforeEach(() => {
    notify = jasmine.createSpyObj('NotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        CostLedgerStore,
        { provide: NotificationService, useValue: notify },
      ],
    });

    store    = TestBed.inject(CostLedgerStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  describe('load()', () => {
    it('sets costs signal on success', () => {
      store.load();
      httpMock.expectOne('/api/v1/resource-costs').flush(mockCosts);
      expect(store.costs()).toEqual(mockCosts as any);
    });

    it('sets error signal on failure', () => {
      store.load();
      httpMock.expectOne('/api/v1/resource-costs')
        .flush({ message: 'DB error' }, { status: 500, statusText: 'Error' });
      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });

    it('sets loading to false after completion', () => {
      store.load();
      expect(store.loading()).toBeTrue();
      httpMock.expectOne('/api/v1/resource-costs').flush([]);
      expect(store.loading()).toBeFalse();
    });
  });

  describe('create()', () => {
    it('calls POST and refreshes list on success', () => {
      store.create('New Server', 1000, 'USD');

      httpMock.expectOne({ method: 'POST', url: '/api/v1/resource-costs' })
        .flush({ id: 'new-id', label: 'New Server', amount: 1000, currencyCode: 'USD' });

      // After create, load() is called to refresh
      httpMock.expectOne({ method: 'GET', url: '/api/v1/resource-costs' }).flush(mockCosts);

      expect(notify.success).toHaveBeenCalled();
    });

    it('sets error on failure', () => {
      store.create('Bad', -1, 'USD');

      httpMock.expectOne('/api/v1/resource-costs')
        .flush({ message: 'Validation failed' }, { status: 400, statusText: 'Bad Request' });

      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });

  describe('importCsv()', () => {
    it('calls POST with text/plain and refreshes on success', () => {
      const callback = jasmine.createSpy('onSuccess');
      store.importCsv('Servers,5000\nRent,2000', callback);

      const req = httpMock.expectOne('/api/v1/resource-costs/import');
      expect(req.request.headers.get('Content-Type')).toBe('text/plain');
      req.flush(mockCosts);

      httpMock.expectOne('/api/v1/resource-costs').flush([]);
      expect(callback).toHaveBeenCalled();
      expect(notify.success).toHaveBeenCalledWith(jasmine.stringContaining('2 costs'));
    });
  });

  describe('delete()', () => {
    it('calls DELETE and refreshes list on success', () => {
      store.delete('id-1', 'Servers');

      httpMock.expectOne({ method: 'DELETE', url: '/api/v1/resource-costs/id-1' }).flush(null);
      httpMock.expectOne({ method: 'GET', url: '/api/v1/resource-costs' }).flush(mockCosts);

      expect(notify.success).toHaveBeenCalledWith(jasmine.stringContaining('removed'));
    });

    it('sets error on failure', () => {
      store.delete('id-1', 'Servers');

      httpMock.expectOne('/api/v1/resource-costs/id-1')
        .flush({ message: 'In use' }, { status: 409, statusText: 'Conflict' });

      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });
});
