import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { ActivitiesPage } from './activities.page';
import { ActivityCatalogStore } from '../core/services/activity-catalog.store';

describe('ActivitiesPage', () => {
  let fixture: ComponentFixture<ActivitiesPage>;
  let store: jasmine.SpyObj<ActivityCatalogStore>;

  beforeEach(async () => {
    store = jasmine.createSpyObj<ActivityCatalogStore>(
      'ActivityCatalogStore',
      ['load', 'create'],
      {
        activities: signal([]),
        loading: signal(false),
        saving: signal(false),
        error: signal<string | null>(null),
      },
    );

    await TestBed.configureTestingModule({
      imports: [ActivitiesPage],
      providers: [{ provide: ActivityCatalogStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(ActivitiesPage);
  });

  it('loads activities on init', () => {
    fixture.detectChanges(); // triggers ngOnInit
    expect(store.load).toHaveBeenCalled();
  });

  it('submits trimmed name and clears on success', () => {
    const cmp = fixture.componentInstance;
    (cmp as any).name.set('  Support  ');

    store.create.and.callFake((_name: string, onSuccess?: () => void) => onSuccess?.());

    cmp.onSubmit(new Event('submit'));

    expect(store.create).toHaveBeenCalledWith('Support', jasmine.any(Function));
    expect((cmp as any).name()).toBe('');
  });

  it('does not submit when name is blank after trimming', () => {
    const cmp = fixture.componentInstance;
    (cmp as any).name.set('   ');

    cmp.onSubmit(new Event('submit'));

    expect(store.create).not.toHaveBeenCalled();
  });
});

