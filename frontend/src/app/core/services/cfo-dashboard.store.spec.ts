import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { CfoDashboardStore } from './cfo-dashboard.store';
import { NotificationService } from './notification.service';

describe('CfoDashboardStore', () => {
  let store: CfoDashboardStore;
  let httpMock: HttpTestingController;
  let notify: jasmine.SpyObj<NotificationService>;

  const mockResources = [{ id: 'r1', label: 'Servers', amount: 5000, currencyCode: 'USD' }];
  const mockActivities = [{ id: 'a1', name: 'IT Support' }];
  const mockProducts = [{ id: 'p1', name: 'Product Alpha' }];

  const flushCatalogs = () => {
    httpMock.expectOne('/api/v1/resource-costs').flush(mockResources);
    httpMock.expectOne('/api/v1/activities').flush(mockActivities);
    httpMock.expectOne('/api/v1/products').flush(mockProducts);
  };

  beforeEach(() => {
    notify = jasmine.createSpyObj('NotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        CfoDashboardStore,
        { provide: NotificationService, useValue: notify },
      ],
    });

    store    = TestBed.inject(CfoDashboardStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  // ── loadCatalogs ──────────────────────────────────────────────────────────

  describe('loadCatalogs()', () => {
    it('sets loading flag and clears it after completion', () => {
      store.loadCatalogs();
      expect(store.loading()).toBeTrue();
      flushCatalogs();
      expect(store.loading()).toBeFalse();
    });

    it('sets error and shows toast on HTTP failure', () => {
      store.loadCatalogs();
      httpMock.expectOne('/api/v1/resource-costs')
        .flush({ message: 'Timeout' }, { status: 503, statusText: 'Service Unavailable' });
      // forkJoin cancels sibling requests on first error — drain without flushing
      httpMock.match('/api/v1/activities');
      httpMock.match('/api/v1/products');

      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });

  // ── totalResourceSpend ────────────────────────────────────────────────────

  describe('totalResourceSpend()', () => {
    it('sums all resource amounts', () => {
      store.loadCatalogs();
      flushCatalogs();
      expect(store.totalResourceSpend()).toBe(5000);
    });

    it('returns 0 when no resources are loaded', () => {
      expect(store.totalResourceSpend()).toBe(0);
    });
  });

  // ── runAllocation ─────────────────────────────────────────────────────────

  describe('runAllocation()', () => {
    const mockResult = {
      activityCosts:  { a1: 5000 },
      productCosts:   { p1: 5000 },
      flows: [
        { fromKind: 'RESOURCE', fromId: 'r1', toKind: 'ACTIVITY', toId: 'a1', amount: 5000, currencyCode: 'USD' },
        { fromKind: 'ACTIVITY', fromId: 'a1', toKind: 'PRODUCT',  toId: 'p1', amount: 5000, currencyCode: 'USD' },
      ],
      unallocatedResourceIds: [],
    };

    it('sets running flag and clears it on success', () => {
      store.runAllocation();
      expect(store.running()).toBeTrue();
      httpMock.expectOne('/api/v1/allocations/run').flush(mockResult);
      flushCatalogs(); // catalog refresh after success
      expect(store.running()).toBeFalse();
    });

    it('sets hasResult to true after a successful run', () => {
      expect(store.hasResult()).toBeFalse();
      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(mockResult);
      flushCatalogs();
      expect(store.hasResult()).toBeTrue();
    });

    it('populates productRows computed signal', () => {
      store.loadCatalogs();
      flushCatalogs();

      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(mockResult);
      flushCatalogs();

      const rows = store.productRows();
      expect(rows.length).toBe(1);
      // Name should be resolved from catalog
      expect(rows[0].name).toBe('Product Alpha');
      expect(rows[0].cost).toBe(5000);
      expect(rows[0].pct).toBe(100);
    });

    it('populates sankeyData computed signal', () => {
      store.loadCatalogs();
      flushCatalogs();

      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(mockResult);
      flushCatalogs();

      const sankey = store.sankeyData();
      // 3 unique node names: Servers, IT Support, Product Alpha
      expect(sankey.nodes.length).toBe(3);
      expect(sankey.links.length).toBe(2);
    });

    it('sets error and shows toast on run failure', () => {
      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run')
        .flush({ message: 'Engine error' }, { status: 500, statusText: 'Error' });

      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });

    it('shows success toast on completion', () => {
      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(mockResult);
      flushCatalogs();
      expect(notify.success).toHaveBeenCalledWith('Allocation run completed.');
    });
  });

  // ── unallocatedCount ──────────────────────────────────────────────────────

  describe('unallocatedCount()', () => {
    it('returns 0 when no run result exists', () => {
      expect(store.unallocatedCount()).toBe(0);
    });

    it('returns count from run result', () => {
      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush({
        activityCosts: {},
        productCosts: {},
        flows: [],
        unallocatedResourceIds: ['r1', 'r2'],
      });
      flushCatalogs();
      expect(store.unallocatedCount()).toBe(2);
    });
  });
});
