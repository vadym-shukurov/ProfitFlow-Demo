import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ProductCatalogStore } from '../core/services/product-catalog.store';

@Component({
  selector: 'app-products-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 class="text-lg font-semibold text-content-primary">Products</h2>
        <p class="mt-1 text-sm text-content-muted">
          Create and manage the final cost objects used for allocation and the Sankey view.
        </p>
      </div>
    </div>

    <div class="mt-6 grid gap-6 lg:grid-cols-2">
      <section class="rounded-xl border border-divider bg-surface p-5">
        <h3 class="text-sm font-semibold text-content-primary">Create product</h3>

        <form class="mt-4 grid gap-3" (submit)="onSubmit($event)">
          <label class="grid gap-1.5">
            <span class="text-xs font-medium text-content-muted">Name</span>
            <input
              class="rounded-lg border border-divider px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-brand-500/30"
              [value]="name()"
              (input)="name.set($any($event.target).value || '')"
              placeholder="e.g. ProfitFlow Cloud"
              autocomplete="off"
              required
            />
          </label>

          @if (store.error()) {
            <div class="rounded-lg border border-danger-200 bg-danger-50 px-3 py-2 text-sm text-danger-700">
              {{ store.error() }}
            </div>
          }

          <div class="flex items-center gap-2">
            <button
              type="submit"
              class="rounded-lg bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60"
              [disabled]="store.saving() || !name().trim()"
            >
              Create
            </button>
            <button
              type="button"
              class="rounded-lg border border-divider px-3 py-2 text-sm text-content-muted hover:text-content-primary"
              (click)="name.set('')"
              [disabled]="store.saving()"
            >
              Clear
            </button>
          </div>
        </form>
      </section>

      <section class="rounded-xl border border-divider bg-surface p-5">
        <div class="flex items-center justify-between gap-3">
          <h3 class="text-sm font-semibold text-content-primary">All products</h3>
          <button
            class="rounded-lg border border-divider px-3 py-2 text-sm text-content-muted hover:text-content-primary disabled:opacity-60"
            (click)="store.load()"
            [disabled]="store.loading()"
          >
            Refresh
          </button>
        </div>

        @if (store.loading()) {
          <p class="mt-4 text-sm text-content-muted">Loading…</p>
        } @else if (store.products().length === 0) {
          <p class="mt-4 text-sm text-content-muted">No products yet.</p>
        } @else {
          <ul class="mt-4 grid gap-2">
            @for (p of store.products(); track p.id) {
              <li class="flex items-center justify-between gap-3 rounded-lg border border-divider px-3 py-2">
                <div class="min-w-0">
                  <div class="truncate text-sm font-medium text-content-primary">{{ p.name }}</div>
                  <div class="truncate text-xs text-content-muted">ID: {{ p.id }}</div>
                </div>
              </li>
            }
          </ul>
        }
      </section>
    </div>
  `,
})
export class ProductsPage implements OnInit {
  protected readonly store = inject(ProductCatalogStore);
  protected readonly name = signal('');

  ngOnInit(): void {
    this.store.load();
  }

  onSubmit(e: Event): void {
    e.preventDefault();
    const trimmed = this.name().trim();
    if (!trimmed) return;
    this.store.create(trimmed, () => this.name.set(''));
  }
}

