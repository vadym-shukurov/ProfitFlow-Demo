import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/services/auth.service';
import { NotificationToastComponent } from '../shared/notification-toast.component';

interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly description: string;
}

/**
 * Application shell — the authenticated outer layout wrapping all protected pages.
 *
 * Renders the sidebar navigation, sticky page header, main content outlet, and the
 * global {@link NotificationToastComponent} overlay. Placed as the component for the
 * top-level authenticated route so that all child routes share a single guard and
 * consistent chrome.
 */
@Component({
  selector: 'app-shell-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationToastComponent],
  template: `
    <!-- Global toast overlay — rendered outside all layout flows -->
    <pf-notification-toast />

    <div class="min-h-screen md:flex md:bg-surface-muted">
      <!-- Sidebar navigation -->
      <aside
        class="border-b border-divider-nav bg-surface-nav text-content-nav
               md:flex md:w-64 md:flex-col md:border-b-0 md:border-r md:border-divider-nav md:shadow-shell"
      >
        <!-- Logo block -->
        <div class="flex items-start justify-between gap-4 px-5 py-5 md:block md:px-6 md:py-8">
          <div>
            <div class="text-lg font-semibold tracking-tight text-white">ProfitFlow</div>
            <p class="mt-1 text-xs leading-snug text-content-subtle">
              Activity-based costing for finance teams
            </p>
          </div>
        </div>

        <nav
          class="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:px-3 md:pb-6 md:pt-0"
          aria-label="Primary navigation"
        >
          @for (item of navItems(); track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="bg-white/10 text-white ring-1 ring-white/10"
              [routerLinkActiveOptions]="{ exact: item.path === '/dashboard' }"
              class="shrink-0 rounded-lg px-3 py-2 text-sm font-medium text-content-nav transition
                     hover:bg-white/5 hover:text-white md:shrink"
            >
              <span class="block">{{ item.label }}</span>
              <span class="hidden text-xs font-normal text-content-subtle md:block">
                {{ item.description }}
              </span>
            </a>
          }
        </nav>

        <!-- User info + logout (pinned to bottom of sidebar) -->
        <div class="mt-auto border-t border-divider-nav px-4 py-4 md:block hidden">
          <div class="flex items-center justify-between gap-3">
            <div class="min-w-0">
              <p class="text-xs font-medium text-white truncate">{{ auth.username() }}</p>
              <p class="text-xs text-content-subtle truncate">Signed in</p>
            </div>
            <button
              (click)="auth.logout()"
              class="shrink-0 rounded-md px-2 py-1.5 text-xs font-medium text-content-nav
                     hover:bg-white/10 hover:text-white transition"
              title="Sign out"
              aria-label="Sign out"
            >
              Sign out
            </button>
          </div>
        </div>
      </aside>

      <!-- Main content area -->
      <div class="min-w-0 flex-1">
        <header
          class="sticky top-0 z-10 border-b border-divider bg-surface-overlay px-4 py-4 backdrop-blur md:px-10"
        >
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p class="text-xs font-medium uppercase tracking-wide text-brand-600">ProfitFlow</p>
              <h1 class="text-xl font-semibold tracking-tight text-content-primary md:text-2xl">
                Enterprise cost intelligence
              </h1>
            </div>
            <!-- Mobile logout -->
            <button
              (click)="auth.logout()"
              class="text-sm text-content-muted hover:text-content-primary transition md:hidden"
              aria-label="Sign out"
            >
              Sign out
            </button>
          </div>
        </header>

        <main class="px-4 py-8 md:px-10 md:py-10">
          <div class="rounded-2xl border border-divider bg-surface p-6 shadow-shell md:p-8">
            <router-outlet />
          </div>
        </main>
      </div>
    </div>
  `,
})
export class ShellLayoutComponent {
  protected readonly auth = inject(AuthService);

  protected readonly navItems = signal<NavItem[]>([
    {
      path: '/dashboard',
      label: 'CFO Dashboard',
      description: 'Sankey & profitability view',
    },
    {
      path: '/ledger',
      label: 'Cost Ledger',
      description: 'Resource costs & CSV import',
    },
    {
      path: '/rules',
      label: 'Allocation Rules',
      description: 'Drivers & stage mappings',
    },
    {
      path: '/ai',
      label: 'AI Allocator',
      description: 'Mock smart suggestions',
    },
  ]);
}
