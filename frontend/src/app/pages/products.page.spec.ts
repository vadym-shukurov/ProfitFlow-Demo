import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { ProductsPage } from './products.page';
import { ProductCatalogStore } from '../core/services/product-catalog.store';

describe('ProductsPage', () => {
  let fixture: ComponentFixture<ProductsPage>;
  let store: jasmine.SpyObj<ProductCatalogStore>;

  beforeEach(async () => {
    store = jasmine.createSpyObj<ProductCatalogStore>(
      'ProductCatalogStore',
      ['load', 'create'],
      {
        products: signal([]),
        loading: signal(false),
        saving: signal(false),
        error: signal<string | null>(null),
      },
    );

    await TestBed.configureTestingModule({
      imports: [ProductsPage],
      providers: [{ provide: ProductCatalogStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductsPage);
  });

  it('loads products on init', () => {
    fixture.detectChanges(); // triggers ngOnInit
    expect(store.load).toHaveBeenCalled();
  });

  it('submits trimmed name and clears on success', () => {
    const cmp = fixture.componentInstance;
    (cmp as any).name.set('  ProfitFlow Cloud  ');

    store.create.and.callFake((_name: string, onSuccess?: () => void) => onSuccess?.());

    cmp.onSubmit(new Event('submit'));

    expect(store.create).toHaveBeenCalledWith('ProfitFlow Cloud', jasmine.any(Function));
    expect((cmp as any).name()).toBe('');
  });

  it('does not submit when name is blank after trimming', () => {
    const cmp = fixture.componentInstance;
    (cmp as any).name.set('   ');

    cmp.onSubmit(new Event('submit'));

    expect(store.create).not.toHaveBeenCalled();
  });
});

