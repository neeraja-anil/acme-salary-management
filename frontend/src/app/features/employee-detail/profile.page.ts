import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnChanges, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { TabsModule } from 'primeng/tabs';

import { problemOf } from '../../core/api-error';
import { EmployeesApi } from '../../core/employees.api';
import { EmployeeDetail } from '../../core/models';
import { Notify } from '../../core/notify';
import { Confirmations } from '../../shared/confirm-dialog';
import { levelLabel } from '../../shared/labels';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';
import { AuditTab } from './audit-tab';
import { ChangeSalaryDialog, DialogOutcome } from './change-salary.dialog';
import { DeactivateDialog } from './deactivate.dialog';
import { SalaryHistoryTab } from './salary-history-tab';

/** Employee profile (US-04) with salary history (US-09) and audit log (US-10). */
@Component({
  selector: 'app-profile-page',
  imports: [
    RouterLink,
    DatePipe,
    AvatarModule,
    ButtonModule,
    CardModule,
    MessageModule,
    TabsModule,
    MoneyPipe,
    StatusChip,
    SalaryHistoryTab,
    AuditTab,
    ChangeSalaryDialog,
    DeactivateDialog,
  ],
  templateUrl: './profile.page.html',
  styleUrl: './profile.page.scss',
})
export class ProfilePage implements OnChanges {
  private readonly api = inject(EmployeesApi);
  private readonly notify = inject(Notify);
  private readonly confirmations = inject(Confirmations);
  private readonly destroyRef = inject(DestroyRef);

  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly employee = signal<EmployeeDetail | null>(null);
  protected readonly notFound = signal(false);
  /** Bumped after every change so the history and audit tabs reload too. */
  protected readonly refreshKey = signal(0);
  protected readonly levelLabel = levelLabel;
  /** Which dialog is open, if any. */
  protected readonly dialog = signal<'salary' | 'deactivate' | null>(null);
  /** Initials in the brand colour, a quiet anchor for the record. */
  protected readonly avatarTokens = {
    root: { background: '{primary.100}', color: '{primary.700}' },
    xl: { fontSize: '1.5rem' },
  };

  ngOnChanges(): void {
    this.employee.set(null);
    this.notFound.set(false);
    this.load();
  }

  protected initials(employee: EmployeeDetail): string {
    return `${employee.firstName.charAt(0)}${employee.lastName.charAt(0)}`.toUpperCase();
  }

  protected afterDialog(outcome: DialogOutcome | undefined, successMessage: string): void {
    this.dialog.set(null);
    if (outcome === 'saved') {
      this.notify.success(successMessage);
      this.load();
    } else if (outcome === 'stale') {
      this.confirmations.staleVersion().subscribe(() => this.load());
    }
  }

  private load(): void {
    this.api
      .get(Number(this.id()))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (employee) => {
          this.employee.set(employee);
          this.refreshKey.update((k) => k + 1);
        },
        error: (error: unknown) => {
          const status = problemOf(error)?.status;
          if (status === 404 || status === 400) this.notFound.set(true);
        },
      });
  }
}
