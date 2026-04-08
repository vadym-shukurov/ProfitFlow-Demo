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

    it('does not flip loading flag in silent mode', () => {
      store.loadAll({ silent: true });
      expect(store.loading()).toBeFalse();
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

  describe('ensureLoaded()', () => {
    it('loads only once', () => {
      store.ensureLoaded();
      flushLoadAll();

      store.ensureLoaded();
      httpMock.expectNone('/api/v1/activities');
      httpMock.expectNone('/api/v1/products');
      httpMock.expectNone('/api/v1/resource-costs');
      httpMock.expectNone('/api/v1/rules/resource-to-activity');
      httpMock.expectNone('/api/v1/rules/activity-to-product');

      // Ensure the spec has an explicit expectation (avoid Jasmine "no expectations" warning)
      expect(store.activities().length).toBe(1);
    });
  });

  // ── saveResourceActivityRules ─────────────────────────────────────────────

  describe('saveResourceActivityRules()', () => {
    it('sends PUT and shows success toast', () => {
      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
      ]);
      store.saveResourceActivityRules();

      const req = httpMock.expectOne('/api/v1/rules/resource-to-activity');
      expect(req.request.body).toEqual([{ resourceId: 'r1', activityId: 'a1', driverWeight: 1 }]);
      req.flush(null);
      flushLoadAll(); // triggered by loadAll() after success
      expect(notify.success).toHaveBeenCalledWith('Resource → Activity rules saved.');
    });

    it('shows warning and blocks PUT when resourceId is blank', () => {
      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: '', activityId: 'a1', driverWeight: 1 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectNone('/api/v1/rules/resource-to-activity');
      expect(notify.warning).toHaveBeenCalled();
    });

    it('normalises null/object IDs to blank and blocks PUT', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: null as any, activityId: {} as any, driverWeight: 1 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectNone('/api/v1/rules/resource-to-activity');
      expect(notify.warning).toHaveBeenCalled();
      expect(store.error()).toContain('please select a value');
    });

    it('shows warning when driver weight is zero', () => {
      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: 'r1', activityId: 'a1', driverWeight: 0 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectNone('/api/v1/rules/resource-to-activity');
      expect(notify.warning).toHaveBeenCalled();
    });

    it('shows error toast on PUT failure', () => {
      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
      ]);
      store.saveResourceActivityRules();

      httpMock.expectOne('/api/v1/rules/resource-to-activity')
        .flush({ message: 'Conflict' }, { status: 409, statusText: 'Conflict' });

      expect(notify.error).toHaveBeenCalled();
    });

    it('warns when stage-2 rules are missing for targeted activities', () => {
      // Setup catalogs for warning name resolution
      store.activities.set([{ id: 'a1', name: 'IT Support' }]);

      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
      ]);
      store.activityProductRules.set([]); // missing stage-2 coverage

      store.saveResourceActivityRules();
      httpMock.expectOne('/api/v1/rules/resource-to-activity').flush(null);
      flushLoadAll();

      expect(notify.warning).toHaveBeenCalled();
    });

    it('does not warn when stage-2 rules cover all targeted activities', () => {
      store.activities.set([{ id: 'a1', name: 'IT Support' }]);

      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
      ]);
      store.activityProductRules.set([
        { rowId: 'ap-1', activityId: 'a1', productId: 'p1', driverWeight: 1 },
      ]);

      store.saveResourceActivityRules();
      httpMock.expectOne('/api/v1/rules/resource-to-activity').flush(null);
      flushLoadAll();

      expect(notify.warning).not.toHaveBeenCalled();
    });

    it('does not warn when there are no resource→activity rules (target set is empty)', () => {
      store.resourceActivityRules.set([]);

      store.saveResourceActivityRules();
      httpMock.expectOne('/api/v1/rules/resource-to-activity').flush(null);
      flushLoadAll();

      expect(notify.warning).not.toHaveBeenCalled();
    });

    it('summarises long missing-activity lists in the warning toast', () => {
      store.activities.set([
        { id: 'a1', name: 'IT Support' },
        { id: 'a2', name: 'Marketing' },
        { id: 'a3', name: 'Ops' },
        { id: 'a4', name: 'QA' },
      ]);

      store.resourceActivityRules.set([
        { rowId: 'ra-1', resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
        { rowId: 'ra-2', resourceId: 'r1', activityId: 'a2', driverWeight: 1 },
        { rowId: 'ra-3', resourceId: 'r1', activityId: 'a3', driverWeight: 1 },
        { rowId: 'ra-4', resourceId: 'r1', activityId: 'a4', driverWeight: 1 },
      ]);
      store.activityProductRules.set([]); // nothing covered

      store.saveResourceActivityRules();
      httpMock.expectOne('/api/v1/rules/resource-to-activity').flush(null);
      flushLoadAll();

      const msg = notify.warning.calls.mostRecent()?.args?.[0] ?? '';
      expect(msg).toContain('(+1 more)');
    });
  });

  // ── saveActivityProductRules ──────────────────────────────────────────────

  describe('saveActivityProductRules()', () => {
    it('sends PUT and shows success toast', () => {
      store.activityProductRules.set([
        { rowId: 'ap-1', activityId: 'a1', productId: 'p1', driverWeight: 1 },
      ]);
      store.saveActivityProductRules();

      const req = httpMock.expectOne('/api/v1/rules/activity-to-product');
      expect(req.request.body).toEqual([{ activityId: 'a1', productId: 'p1', driverWeight: 1 }]);
      req.flush(null);
      flushLoadAll();
      expect(notify.success).toHaveBeenCalledWith('Activity → Product rules saved.');
    });

    it('shows warning when productId is blank', () => {
      store.activityProductRules.set([
        { rowId: 'ap-1', activityId: 'a1', productId: '', driverWeight: 1 },
      ]);
      store.saveActivityProductRules();

      httpMock.expectNone('/api/v1/rules/activity-to-product');
      expect(notify.warning).toHaveBeenCalled();
    });

    it('shows warning when driverWeight is a boolean false (normalised to 0)', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      store.activityProductRules.set([
        { rowId: 'ap-1', activityId: 'a1', productId: 'p1', driverWeight: false as any },
      ]);

      store.saveActivityProductRules();

      httpMock.expectNone('/api/v1/rules/activity-to-product');
      expect(notify.warning).toHaveBeenCalled();
      expect(store.error()).toContain('driver weight must be a positive number');
    });

    it('accepts numeric IDs (normalised to strings) and proceeds to PUT', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      store.activityProductRules.set([
        { rowId: 'ap-1', activityId: 123 as any, productId: 456 as any, driverWeight: 1 },
      ]);

      store.saveActivityProductRules();
      httpMock.expectOne('/api/v1/rules/activity-to-product').flush(null);
      flushLoadAll();

      expect(notify.success).toHaveBeenCalledWith('Activity → Product rules saved.');
    });

    it('accepts boolean IDs (normalised to strings) and proceeds to PUT', () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      store.activityProductRules.set([
        { rowId: 'ap-1', activityId: true as any, productId: false as any, driverWeight: 1 },
      ]);

      store.saveActivityProductRules();
      httpMock.expectOne('/api/v1/rules/activity-to-product').flush(null);
      flushLoadAll();

      expect(notify.success).toHaveBeenCalledWith('Activity → Product rules saved.');
    });
  });

  describe('rehydration', () => {
    it('preserves rowId for identical rules after reload', () => {
      store.resourceActivityRules.set([
        { rowId: 'ra-keep', resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
      ]);

      store.loadAll();
      httpMock.expectOne('/api/v1/activities').flush([{ id: 'a1', name: 'IT Support' }]);
      httpMock.expectOne('/api/v1/products').flush([{ id: 'p1', name: 'Product A' }]);
      httpMock.expectOne('/api/v1/resource-costs').flush([{ id: 'r1', label: 'Servers', amount: 1000, currencyCode: 'USD' }]);
      httpMock.expectOne('/api/v1/rules/resource-to-activity').flush([{ resourceId: 'r1', activityId: 'a1', driverWeight: 2 }]);
      httpMock.expectOne('/api/v1/rules/activity-to-product').flush([]);

      expect(store.resourceActivityRules()[0].rowId).toBe('ra-keep');
      expect(store.resourceActivityRules()[0].driverWeight).toBe(2);
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
        { rowId: 'ra-1', resourceId: 'r1', activityId: 'a1', driverWeight: 1 },
        { rowId: 'ra-2', resourceId: 'r2', activityId: 'a2', driverWeight: 1 },
      ]);
      store.removeResourceActivityRow(0);
      expect(store.resourceActivityRules().length).toBe(1);
      expect(store.resourceActivityRules()[0].resourceId).toBe('r2');
    });

    it('patchResourceActivityRow updates a field', () => {
      store.resourceActivityRules.set([{ rowId: 'ra-1', resourceId: '', activityId: '', driverWeight: 1 }]);
      store.patchResourceActivityRow(0, { resourceId: 'r9' });
      expect(store.resourceActivityRules()[0].resourceId).toBe('r9');
    });

    it('addActivityProductDraft appends an empty row', () => {
      store.addActivityProductDraft();
      expect(store.activityProductRules().length).toBe(1);
    });

    it('patchActivityProductRow updates a field', () => {
      store.activityProductRules.set([{ rowId: 'ap-1', activityId: '', productId: '', driverWeight: 1 }]);
      store.patchActivityProductRow(0, { productId: 'p5' });
      expect(store.activityProductRules()[0].productId).toBe('p5');
    });
  });
});
