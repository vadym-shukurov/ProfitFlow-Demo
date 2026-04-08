import { Routes } from '@angular/router';

import { ShellLayoutComponent } from './shell/shell-layout.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  // Public route — accessible without authentication
  {
    path: 'login',
    loadComponent: () => import('./pages/login.page').then(m => m.LoginPage),
  },

  // Protected application shell — all children require authentication
  {
    path: '',
    component: ShellLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./pages/cfo-dashboard.page').then(m => m.CfoDashboardPage),
      },
      {
        path: 'ledger',
        loadComponent: () =>
          import('./pages/cost-ledger.page').then(m => m.CostLedgerPage),
      },
      {
        path: 'activities',
        loadComponent: () =>
          import('./pages/activities.page').then(m => m.ActivitiesPage),
      },
      {
        path: 'products',
        loadComponent: () =>
          import('./pages/products.page').then(m => m.ProductsPage),
      },
      {
        path: 'rules',
        loadComponent: () =>
          import('./pages/allocation-rules.page').then(m => m.AllocationRulesPage),
      },
      {
        path: 'ai',
        loadComponent: () =>
          import('./pages/ai-allocator.page').then(m => m.AiAllocatorPage),
      },
    ],
  },

  { path: '**', redirectTo: '' },
];
