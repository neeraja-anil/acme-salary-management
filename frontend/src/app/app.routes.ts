import { Routes } from '@angular/router';

import { unsavedChangesGuard } from './shared/unsaved-changes.guard';

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
  {
    path: 'employees/new',
    title: 'Add employee · ACME',
    loadComponent: () => import('./features/employees/employee-form.page').then((m) => m.EmployeeFormPage),
    canDeactivate: [unsavedChangesGuard],
  },
  {
    path: 'employees/:id',
    title: 'Employee · ACME',
    loadComponent: () => import('./features/employee-detail/profile.page').then((m) => m.ProfilePage),
  },
  {
    path: 'employees/:id/edit',
    title: 'Edit employee · ACME',
    loadComponent: () => import('./features/employees/employee-form.page').then((m) => m.EmployeeFormPage),
    canDeactivate: [unsavedChangesGuard],
  },
  { path: '**', redirectTo: 'dashboard' },
];
