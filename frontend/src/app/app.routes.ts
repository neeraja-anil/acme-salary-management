import { Routes } from '@angular/router';

/** Screens and routes from ui-design.md section 1. Each page is loaded on first visit. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard · ACME',
    loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'employees',
    title: 'Employees · ACME',
    loadComponent: () => import('./features/employees/directory.page').then((m) => m.DirectoryPage),
  },
  { path: '**', redirectTo: 'dashboard' },
];
