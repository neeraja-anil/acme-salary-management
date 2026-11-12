import { Routes } from '@angular/router';

import { signedInGuard, signedOutGuard, superAdminGuard } from './core/auth.guards';
import { unsavedChangesGuard } from './shared/unsaved-changes.guard';

/**
 * Screens and routes from ui-design.md section 1. Each page is loaded on first visit.
 * Order matters: `employees/new` comes before `employees/:id`, or "new" would be read as an id.
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · ACME',
    canActivate: [signedOutGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    // Every other screen sits inside the shell (side menu and top bar) and needs a signed-in user.
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell), // not needed by the sign-in page
    canActivate: [signedInGuard],
    children: [
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
      {
        path: 'insights',
        title: 'Pay insights · ACME',
        loadComponent: () => import('./features/insights/insights.page').then((m) => m.InsightsPage),
      },
      {
        path: 'users',
        title: 'Users · ACME',
        canActivate: [superAdminGuard],
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
