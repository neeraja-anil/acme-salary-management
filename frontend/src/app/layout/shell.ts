import { BreakpointObserver } from '@angular/cdk/layout';
import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DrawerModule } from 'primeng/drawer';
import { MessageModule } from 'primeng/message';
import { ProgressBarModule } from 'primeng/progressbar';
import { ToastModule } from 'primeng/toast';
import { map } from 'rxjs';

import { LoadingState } from '../core/http.interceptors';
import { ReferenceDataService } from '../core/reference-data';

/**
 * Side navigation, the page area, and the app-wide toast and confirm dialog (ui-design.md section 3).
 * Below 900px the navigation moves into a drawer opened from a top bar.
 */
@Component({
  selector: 'app-shell',
  imports: [
    NgTemplateOutlet,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ButtonModule,
    ConfirmDialogModule,
    DrawerModule,
    MessageModule,
    ProgressBarModule,
    ToastModule,
  ],
  template: `
    <ng-template #brand>
      <a class="brand" routerLink="/dashboard">
        <span class="logo" aria-hidden="true"><i class="pi pi-wallet"></i></span>
        <span>
          <span class="brand-name">ACME</span>
          <span class="brand-sub">Salary management</span>
        </span>
      </a>
    </ng-template>

    <ng-template #nav>
      <nav aria-label="Main">
        @for (link of links; track link.path) {
          <a
            class="nav-link"
            [routerLink]="link.path"
            routerLinkActive="active"
            ariaCurrentWhenActive="page"
            (click)="navOpen.set(false)"
          >
            <i class="pi {{ link.icon }}" aria-hidden="true"></i>
            <span>{{ link.label }}</span>
          </a>
        }
      </nav>
    </ng-template>

    @if (!isNarrow()) {
      <aside class="sidebar">
        <ng-container [ngTemplateOutlet]="brand" />
        <ng-container [ngTemplateOutlet]="nav" />
      </aside>
    } @else {
      <header class="topbar">
        <p-button icon="pi pi-bars" [text]="true" severity="secondary" ariaLabel="Open navigation"
                  (onClick)="navOpen.set(true)" />
        <ng-container [ngTemplateOutlet]="brand" />
      </header>
      <p-drawer [visible]="navOpen()" (visibleChange)="navOpen.set($event)" ariaCloseLabel="Close navigation"
                styleClass="nav-drawer">
        <ng-template #header><ng-container [ngTemplateOutlet]="brand" /></ng-template>
        <ng-container [ngTemplateOutlet]="nav" />
      </p-drawer>
    }

    <div class="main">
      <div class="progress">
        @if (loading.loading()) {
          <p-progressbar mode="indeterminate" aria-label="Loading" />
        }
      </div>
      <main class="content">
        @if (reference.data()) {
          <router-outlet />
        } @else if (reference.loadFailed()) {
          <p-message severity="error" icon="pi pi-times-circle">
            Could not reach the server. Check that the backend is running, then reload the page.
          </p-message>
        }
      </main>
    </div>

    <p-toast position="top-right" />
    <p-confirmdialog [style]="{ width: '440px', maxWidth: 'calc(100vw - 32px)' }" />
  `,
  styles: `
    :host {
      display: flex;
      min-height: 100vh;
    }
    .sidebar {
      position: sticky;
      top: 0;
      flex: none;
      width: var(--app-sidebar-width);
      height: 100vh;
      box-sizing: border-box;
      padding: 20px 12px;
      background: var(--p-content-background);
      border-right: 1px solid var(--p-content-border-color);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 0 8px 24px;
      color: inherit;
      text-decoration: none;
    }
    .logo {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: var(--p-primary-color);
      color: var(--p-primary-contrast-color);
    }
    .brand-name {
      display: block;
      font-weight: 700;
      color: var(--p-surface-900);
      line-height: 1.2;
    }
    .brand-sub {
      display: block;
      font-size: 12px;
      color: var(--p-text-muted-color);
    }
    nav {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .nav-link {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      color: var(--p-text-color);
      font-weight: 500;
      text-decoration: none;
    }
    .nav-link .pi {
      color: var(--p-text-muted-color);
    }
    .nav-link:hover {
      background: var(--p-surface-100);
    }
    .nav-link.active {
      background: var(--p-highlight-background);
      color: var(--p-highlight-color);
      font-weight: 600;
    }
    .nav-link.active .pi {
      color: inherit;
    }
    .topbar {
      position: fixed;
      inset: 0 0 auto;
      z-index: 2;
      display: flex;
      align-items: center;
      gap: 4px;
      height: 56px;
      padding: 0 8px;
      background: var(--p-content-background);
      border-bottom: 1px solid var(--p-content-border-color);
    }
    .topbar .brand {
      padding: 0;
    }
    .main {
      position: relative;
      flex: 1;
      min-width: 0;
    }
    .progress {
      position: absolute;
      inset: 0 0 auto;
      height: 3px;
      --p-progressbar-height: 3px;
      --p-progressbar-border-radius: 0;
    }
    .content {
      max-width: 1400px;
      margin: 0 auto;
      padding: 32px;
    }
    @media (max-width: 900px) {
      .content {
        padding: 72px 16px 24px; /* clears the fixed top bar */
      }
    }
  `,
})
export class Shell {
  protected readonly loading = inject(LoadingState);
  protected readonly reference = inject(ReferenceDataService);
  protected readonly navOpen = signal(false);

  protected readonly isNarrow = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 900px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected readonly links = [
    { path: '/dashboard', label: 'Dashboard', icon: 'pi-th-large' },
    { path: '/employees', label: 'Employees', icon: 'pi-users' },
    { path: '/insights', label: 'Pay insights', icon: 'pi-chart-bar' },
  ];
}
