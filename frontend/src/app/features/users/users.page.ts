import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { filter, switchMap } from 'rxjs';

import { isUnexpected, messageOf } from '../../core/api-error';
import { User } from '../../core/models';
import { Notify } from '../../core/notify';
import { UsersApi } from '../../core/users.api';
import { Confirmations } from '../../shared/confirm-dialog';
import { ClientPager, PAGE_REPORT } from '../../shared/paging';
import { AddUserDialog } from './add-user.dialog';
import { ResetPasswordDialog } from './reset-password.dialog';

/**
 * User management for super admins (US-22): who can sign in, add HR managers, disable or enable them,
 * and reset their passwords. Super admin accounts can't be changed here.
 */
@Component({
  selector: 'app-users-page',
  imports: [DatePipe, ButtonModule, CardModule, TableModule, TagModule, AddUserDialog, ResetPasswordDialog],
  template: `
    <div class="page-header">
      <div>
        <h1>Users</h1>
        <p class="subtitle">Add HR managers and control who can sign in.</p>
      </div>
      <div class="actions">
        <p-button label="Add HR user" icon="pi pi-user-plus" (onClick)="adding.set(true)" />
      </div>
    </div>

    <p-card>
      <p-table [value]="users()" [loading]="!loaded()" dataKey="id" [paginator]="true" [rows]="pager.size()"
               [first]="pager.first()" (firstChange)="pager.first.set($event)"
               (rowsChange)="pager.size.set($event)" [rowsPerPageOptions]="pager.sizes"
               [showCurrentPageReport]="true" [currentPageReportTemplate]="pageReport">
        <ng-template #header>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Role</th>
            <th scope="col">Status</th>
            <th scope="col">Added</th>
            <th scope="col" class="num">Actions</th>
          </tr>
        </ng-template>
        <ng-template #body let-user>
          <tr>
            <td>
              <div class="name">{{ user.displayName }}</div>
              <div class="caption">{{ user.username }}</div>
            </td>
            <td>
              <p-tag [value]="user.role === 'SUPER_ADMIN' ? 'Super admin' : 'HR manager'"
                     [severity]="user.role === 'SUPER_ADMIN' ? 'info' : 'secondary'" [rounded]="true" />
            </td>
            <td>
              <p-tag [value]="user.enabled ? 'Active' : 'Disabled'" [severity]="user.enabled ? 'success' : 'danger'"
                     [rounded]="true" />
            </td>
            <td class="muted">{{ user.createdAt | date: 'd MMM y' }}</td>
            <td class="num">
              @if (user.role === 'SUPER_ADMIN') {
                <span class="caption">Can't be changed here</span>
              } @else {
                <div class="row-actions">
                  <p-button label="Reset password" icon="pi pi-key" [text]="true" severity="secondary" size="small"
                            (onClick)="resetting.set(user)" />
                  @if (user.enabled) {
                    <p-button label="Disable" icon="pi pi-ban" [text]="true" severity="danger" size="small"
                              (onClick)="setEnabled(user, false)" />
                  } @else {
                    <p-button label="Enable" icon="pi pi-check" [text]="true" size="small"
                              (onClick)="setEnabled(user, true)" />
                  }
                </div>
              }
            </td>
          </tr>
        </ng-template>
      </p-table>
    </p-card>

    @if (adding()) {
      <app-add-user-dialog (closed)="afterAdd($event)" />
    }
    @if (resetting(); as user) {
      <app-reset-password-dialog [user]="user" (closed)="afterReset(user, $event)" />
    }
  `,
  styles: `
    .name {
      font-weight: 600;
      color: var(--p-surface-900);
    }
    .row-actions {
      display: inline-flex;
      gap: 4px;
    }
  `,
})
export class UsersPage {
  private readonly api = inject(UsersApi);
  private readonly notify = inject(Notify);
  private readonly confirmations = inject(Confirmations);

  protected readonly users = signal<User[]>([]);
  protected readonly loaded = signal(false);
  protected readonly pager = new ClientPager(this.users);
  protected readonly pageReport = PAGE_REPORT;
  protected readonly adding = signal(false);
  protected readonly resetting = signal<User | null>(null);

  constructor() {
    this.api
      .list()
      .pipe(takeUntilDestroyed())
      .subscribe((users) => {
        this.users.set(users);
        this.loaded.set(true);
      });
  }

  protected afterAdd(user: User | undefined): void {
    this.adding.set(false);
    if (user) {
      this.users.update((users) => [...users, user]);
      this.notify.success(`${user.displayName} can now sign in as ${user.username}`);
    }
  }

  protected afterReset(user: User, changed: boolean): void {
    this.resetting.set(null);
    if (changed) {
      this.notify.success(`Password reset for ${user.displayName}`);
    }
  }

  /** Disabling asks first, because it signs the person out straight away; enabling doesn't need to. */
  protected setEnabled(user: User, enabled: boolean): void {
    const request$ = enabled
      ? this.api.setEnabled(user.id, true)
      : this.confirmations
          .ask({
            title: `Disable ${user.displayName}?`,
            message: `${user.displayName} won't be able to sign in, and is signed out straight away if they are using the app now. You can enable them again later.`,
            confirmLabel: 'Disable',
            cancelLabel: 'Cancel',
            warn: true,
          })
          .pipe(
            filter(Boolean),
            switchMap(() => this.api.setEnabled(user.id, false)),
          );
    request$.subscribe({
      next: (updated) => {
        this.users.update((users) => users.map((u) => (u.id === updated.id ? updated : u)));
        this.notify.success(`${updated.displayName} is ${updated.enabled ? 'enabled' : 'disabled'}`);
      },
      error: (error: unknown) => {
        if (!isUnexpected(error)) this.notify.error(messageOf(error));
      },
    });
  }
}
