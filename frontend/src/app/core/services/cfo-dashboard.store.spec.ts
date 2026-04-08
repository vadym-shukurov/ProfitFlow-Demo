import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { CfoDashboardStore } from './cfo-dashboard.store';
import { NotificationService } from './notification.service';

describe('CfoDashboardStore', () => {
  let store: CfoDashboardStore;
  let httpMock: HttpTestingController;
  let notify: jasmine.SpyObj<NotificationService>;

  const mockResources = [{ id: 'r1', label: 'Servers', amount: 5000, currencyCode: 'USD' }];
  const mockActivities = [{ id: '4bea881f-c8e7-4353-9a28-70d3b6126d6c', name: 'IT Support' }];
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
      activityCosts:  { '4bea881f-c8e7-4353-9a28-70d3b6126d6c': 5000 },
      productCosts:   { p1: 5000 },
      flows: [
        { fromKind: 'RESOURCE', fromId: 'r1', toKind: 'ACTIVITY', toId: '4bea881f-c8e7-4353-9a28-70d3b6126d6c', amount: 5000, currencyCode: 'USD' },
        { fromKind: 'ACTIVITY', fromId: '4bea881f-c8e7-4353-9a28-70d3b6126d6c', toKind: 'PRODUCT',  toId: 'p1', amount: 5000, currencyCode: 'USD' },
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

    it('formats missing Activity → Product rule errors with activity names', () => {
      store.loadCatalogs();
      flushCatalogs();

      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(
        {
          message:
            'The following activities are targeted by resource rules but have no product allocation rules: '
            + '[4bea881f-c8e7-4353-9a28-70d3b6126d6c]',
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      expect(store.error()).toContain('Allocation can’t run yet');
      expect(store.error()).toContain('IT Support');
      expect(notify.error).toHaveBeenCalled();
    });

    it('formats missing rules error even when no UUIDs are present', () => {
      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(
        {
          message:
            'The following activities are targeted by resource rules but have no product allocation rules.',
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      expect(store.error()).toContain('Allocation can’t run yet');
      expect(notify.error).toHaveBeenCalled();
    });

    it('formats missing rules error with a +N more summary when many activities are missing', () => {
      const a2 = '11111111-1111-1111-1111-111111111111';
      const a3 = '22222222-2222-2222-2222-222222222222';
      const a4 = '33333333-3333-3333-3333-333333333333';

      store.loadCatalogs();
      httpMock.expectOne('/api/v1/resource-costs').flush(mockResources);
      httpMock.expectOne('/api/v1/activities').flush([
        mockActivities[0],
        { id: a2, name: 'Marketing' },
        { id: a3, name: 'Support' },
        { id: a4, name: 'Ops' },
      ]);
      httpMock.expectOne('/api/v1/products').flush(mockProducts);

      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(
        {
          message:
            'The following activities are targeted by resource rules but have no product allocation rules: '
            + `[${mockActivities[0].id}, ${a2}, ${a3}, ${a4}]`,
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      expect(store.error()).toContain('(+1 more)');
      expect(store.error()).toContain('Marketing');
      expect(store.error()).toContain('Support');
    });

    it('falls back to raw message when error is not the missing-rules domain case', () => {
      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(
        { message: 'Some other domain rule' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      expect(store.error()).toBe('Some other domain rule');
    });

    it('includes raw IDs when an activity is unknown to the catalog', () => {
      store.loadCatalogs();
      flushCatalogs();

      const unknown = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush(
        {
          message:
            'The following activities are targeted by resource rules but have no product allocation rules: '
            + `[${unknown}]`,
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      expect(store.error()).toContain(unknown);
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

  // ── computed fallbacks ────────────────────────────────────────────────────

  describe('computed fallbacks', () => {
    it('sankeyData is empty when no result exists', () => {
      const sankey = store.sankeyData();
      expect(sankey.nodes).toEqual([]);
      expect(sankey.links).toEqual([]);
    });

    it('productRows returns empty when no result exists', () => {
      expect(store.productRows()).toEqual([]);
    });

    it('productRows pct is 0 when total is 0', () => {
      store.loadCatalogs();
      flushCatalogs();

      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush({
        activityCosts:  {},
        productCosts:   { p1: 0 },
        flows: [],
        unallocatedResourceIds: [],
      });
      flushCatalogs();

      expect(store.productRows()[0].pct).toBe(0);
    });

    it('productRows uses USD when no flows exist', () => {
      store.loadCatalogs();
      flushCatalogs();

      store.runAllocation();
      httpMock.expectOne('/api/v1/allocations/run').flush({
        activityCosts:  {},
        productCosts:   { p1: 123 },
        flows: [],
        unallocatedResourceIds: [],
      });
      flushCatalogs();

      expect(store.productRows()[0].currencyCode).toBe('USD');
    });
  });
});
