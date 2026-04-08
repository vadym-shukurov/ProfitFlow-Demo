import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { AllocationRulesStore } from './allocation-rules.store';
import { NotificationService } from './notification.service';

describe('AllocationRulesStore', () => {
  let store: AllocationRulesStore;
  let httpMock: HttpTestingController;
  let notify: jasmine.SpyObj<NotificationService>;

  const flushLoadAll = () => {
    httpMock.expectOne('/api/v1/activities').flush([{ id: 'a1', name: 'IT Support' }]);
    httpMock.expectOne('/api/v1/products').flush([{ id: 'p1', name: 'Product A' }]);
    httpMock.expectOne('/api/v1/resource-costs').flush([{ id: 'r1', label: 'Servers', amount: 1000, currencyCode: 'USD' }]);
    httpMock.expectOne('/api/v1/rules/resource-to-activity').flush([]);
    httpMock.expectOne('/api/v1/rules/activity-to-product').flush([]);
  };

  beforeEach(() => {
    notify = jasmine.createSpyObj('NotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AllocationRulesStore,
        { provide: NotificationService, useValue: notify },
      ],
    });

    store    = TestBed.inject(AllocationRulesStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  // ── loadAll ──────────────────────────────────────────────────────────────

  describe('loadAll()', () => {
    it('populates all signals on success', () => {
      store.loadAll();
      flushLoadAll();

      expect(store.activities()).toEqual([{ id: 'a1', name: 'IT Support' }]);
      expect(store.products()).toEqual([{ id: 'p1', name: 'Product A' }]);
      expect(store.resources()[0].label).toBe('Servers');
    });

    it('sets loading to false after completion', () => {
      store.loadAll();
      expect(store.loading()).toBeTrue();
      flushLoadAll();
      expect(store.loading()).toBeFalse();
    });

    it('sets error and shows toast on failure', () => {
      store.loadAll();
      httpMock.expectOne('/api/v1/activities')
        .flush({ message: 'Server error' }, { status: 500, statusText: 'Error' });
      // forkJoin cancels sibling requests on first error — drain without flushing
      httpMock.match('/api/v1/products');
      httpMock.match('/api/v1/resource-costs');
      httpMock.match('/api/v1/rules/resource-to-activity');
      httpMock.match('/api/v1/rules/activity-to-product');

      expect(store.error()).toBeTruthy();
      expect(notify.error).toHaveBeenCalled();
    });
  });

  // ── saveResourceActivityRules ─────────────────────────────────────────────

  describe('saveResourceActivityRules()', () => {
    it('sends PUT and shows success toast', () => {
      store.resourceActivityRules.set([
        { resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectOne('/api/v1/rules/resource-to-activity').flush(null);
      flushLoadAll(); // triggered by loadAll() after success
      expect(notify.success).toHaveBeenCalledWith('Resource → Activity rules saved.');
    });

    it('shows warning and blocks PUT when resourceId is blank', () => {
      store.resourceActivityRules.set([
        { resourceId: '', activityId: 'a1', driverWeight: 1 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectNone('/api/v1/rules/resource-to-activity');
      expect(notify.warning).toHaveBeenCalled();
    });

    it('shows warning when driver weight is zero', () => {
      store.resourceActivityRules.set([
        { resourceId: 'r1', activityId: 'a1', driverWeight: 0 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectNone('/api/v1/rules/resource-to-activity');
      expect(notify.warning).toHaveBeenCalled();
    });

    it('shows error toast on PUT failure', () => {
      store.resourceActivityRules.set([
        { resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectOne('/api/v1/rules/resource-to-activity')
        .flush({ message: 'Conflict' }, { status: 409, statusText: 'Conflict' });

      expect(notify.error).toHaveBeenCalled();
    });
  });

  // ── saveActivityProductRules ──────────────────────────────────────────────

  describe('saveActivityProductRules()', () => {
    it('sends PUT and shows success toast', () => {
      store.activityProductRules.set([
        { activityId: 'a1', productId: 'p1', driverWeight: 1 },
      ]);
      store.saveActivityProductRules();

      httpMock.expectOne('/api/v1/rules/activity-to-product').flush(null);
      flushLoadAll();
      expect(notify.success).toHaveBeenCalledWith('Activity → Product rules saved.');
    });

    it('shows warning when productId is blank', () => {
      store.activityProductRules.set([
        { activityId: 'a1', productId: '', driverWeight: 1 },
      ]);
      store.saveActivityProductRules();

      httpMock.expectNone('/api/v1/rules/activity-to-product');
      expect(notify.warning).toHaveBeenCalled();
    });

    it('shows warning when driverWeight is a boolean false (normalised to 0)', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      store.activityProductRules.set([
        { activityId: 'a1', productId: 'p1', driverWeight: false as any },
      ]);

      store.saveActivityProductRules();

      httpMock.expectNone('/api/v1/rules/activity-to-product');
      expect(notify.warning).toHaveBeenCalled();
      expect(store.error()).toContain('driver weight must be a positive number');
    });

    it('accepts numeric IDs (normalised to strings) and proceeds to PUT', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      store.activityProductRules.set([
        { activityId: 123 as any, productId: 456 as any, driverWeight: 1 },
      ]);

      store.saveActivityProductRules();
      httpMock.expectOne('/api/v1/rules/activity-to-product').flush(null);
      flushLoadAll();

      expect(notify.success).toHaveBeenCalledWith('Activity → Product rules saved.');
    });
  });

  // ── In-memory editing helpers ─────────────────────────────────────────────

  describe('in-memory editing', () => {
    it('addResourceActivityDraft appends an empty row', () => {
      store.addResourceActivityDraft();
      expect(store.resourceActivityRules().length).toBe(1);
      expect(store.resourceActivityRules()[0].resourceId).toBe('');
    });

    it('removeResourceActivityRow removes the correct index', () => {
      store.resourceActivityRules.set([
        { resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
        { resourceId: 'r2', activityId: 'a2', driverWeight: 1 },
      ]);
      store.removeResourceActivityRow(0);
      expect(store.resourceActivityRules().length).toBe(1);
      expect(store.resourceActivityRules()[0].resourceId).toBe('r2');
    });

    it('patchResourceActivityRow updates a field', () => {
      store.resourceActivityRules.set([{ resourceId: '', activityId: '', driverWeight: 1 }]);
      store.patchResourceActivityRow(0, { resourceId: 'r9' });
      expect(store.resourceActivityRules()[0].resourceId).toBe('r9');
    });

    it('addActivityProductDraft appends an empty row', () => {
      store.addActivityProductDraft();
      expect(store.activityProductRules().length).toBe(1);
    });

    it('patchActivityProductRow updates a field', () => {
      store.activityProductRules.set([{ activityId: '', productId: '', driverWeight: 1 }]);
      store.patchActivityProductRow(0, { productId: 'p5' });
      expect(store.activityProductRules()[0].productId).toBe('p5');
    });
  });
});
