import { DatePipe, formatDate } from '@angular/common';
import { Component, LOCALE_ID, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CardModule } from 'primeng/card';
import { PaginatorModule } from 'primeng/paginator';
import { EMPTY, catchError, switchMap } from 'rxjs';

import { EmployeesApi } from '../../core/employees.api';
import { AuditAction, AuditEntry, JobLevel, Page, SalaryChangeReason } from '../../core/models';
import { actionLabel, fieldLabel, levelLabel, reasonLabel } from '../../shared/labels';
import { formatMoney } from '../../shared/money.pipe';
import { PAGE_REPORT } from '../../shared/paging';

const ACTION_ICONS: Record<AuditAction, string> = {
  CREATE: 'pi-user-plus',
  IMPORT: 'pi-upload',
  UPDATE: 'pi-pencil',
  DEACTIVATE: 'pi-user-minus',
  SALARY_CHANGE: 'pi-wallet',
};

interface ChangeLine {
  field: string;
  from?: string;
  to?: string;
}

const MONEY_FIELDS = new Set(['salary', 'amount']);
const DATE_FIELDS = new Set(['hireDate', 'effectiveDate', 'terminationDate']);

/** Who changed what and when (US-10), newest first, ten entries per page. */
@Component({
  selector: 'app-audit-tab',
  imports: [CardModule, PaginatorModule, DatePipe],
  template: `
    <p-card>
      <ol class="timeline">
        @for (entry of page()?.content ?? []; track entry.id) {
          <li>
            <span class="dot" aria-hidden="true"><i class="pi {{ icon(entry.action) }}"></i></span>
            <div class="entry">
              <div class="meta">
                <strong>{{ actionLabel(entry.action) }}</strong>
                <span class="caption">{{ entry.performedAt | date: 'd MMM y, HH:mm' }} · {{ entry.performedBy }}</span>
              </div>
              <ul class="changes">
                @for (line of lines(entry); track line.field) {
                  <li>
                    <span class="field">{{ line.field }}</span>
                    @if (line.from !== undefined) {
                      <span class="from">{{ line.from }}</span>
                      <i class="pi pi-arrow-right arrow" aria-label="changed to"></i>
                      <span>{{ line.to ?? '–' }}</span>
                    } @else {
                      <span>{{ line.to }}</span>
                    }
                  </li>
                }
              </ul>
            </div>
          </li>
        } @empty {
          @if (page()) {
            <li class="empty">No changes recorded.</li>
          }
        }
      </ol>
      @if ((page()?.totalElements ?? 0) > pageSize) {
        <p-paginator [rows]="pageSize" [totalRecords]="page()?.totalElements ?? 0" [first]="pageIndex() * pageSize"
                     (onPageChange)="pageIndex.set($event.page ?? 0)" [showCurrentPageReport]="true"
                     [currentPageReportTemplate]="pageReport" />
      }
    </p-card>
  `,
  styles: `
    .timeline {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .timeline > li {
      position: relative;
      display: flex;
      gap: 16px;
      padding-bottom: 20px;
    }
    /* The line joining one entry's dot to the next. */
    .timeline > li:not(:last-child)::before {
      content: '';
      position: absolute;
      top: 34px;
      bottom: 0;
      left: 15px;
      width: 2px;
      background: var(--p-content-border-color);
    }
    .dot {
      display: grid;
      place-items: center;
      flex: none;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--p-primary-50);
      color: var(--p-primary-700);
      font-size: 13px;
    }
    .entry {
      flex: 1;
      min-width: 0;
      padding-top: 5px;
    }
    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 12px;
      align-items: baseline;
    }
    .changes {
      list-style: none;
      margin: 8px 0 0;
      padding: 0;
      display: grid;
      gap: 4px;
    }
    .changes li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
    }
    .field {
      min-width: 120px;
      color: var(--p-text-muted-color);
    }
    .from {
      color: var(--p-text-muted-color);
      text-decoration: line-through;
    }
    .arrow {
      font-size: 11px;
      color: var(--p-text-muted-color);
    }
  `,
})
export class AuditTab {
  private readonly api = inject(EmployeesApi);
  private readonly locale = inject(LOCALE_ID);

  readonly employeeId = input.required<number>();
  readonly refreshKey = input(0);

  protected readonly pageSize = 10;
  /** Resets to the first page whenever the employee or refresh key changes. */
  protected readonly pageIndex = linkedSignal({
    source: () => [this.employeeId(), this.refreshKey()],
    computation: () => 0,
  });
  protected readonly page = signal<Page<AuditEntry> | null>(null);
  protected readonly actionLabel = actionLabel;
  protected readonly pageReport = PAGE_REPORT;

  constructor() {
    toObservable(computed(() => ({ id: this.employeeId(), page: this.pageIndex(), key: this.refreshKey() })))
      .pipe(
        switchMap(({ id, page }) => this.api.audit(id, page, this.pageSize).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.page.set(page));
  }

  protected icon(action: AuditAction): string {
    return ACTION_ICONS[action];
  }

  /** Turns the stored {field: {from, to}} map into readable lines, e.g. "Level: L3 Senior → L4 Lead". */
  protected lines(entry: AuditEntry): ChangeLine[] {
    const currency = entry.changes['currency']?.to as string | undefined;
    return Object.entries(entry.changes).map(([field, change]) => ({
      field: fieldLabel(field),
      from: 'from' in change ? this.format(field, change.from, currency) : undefined,
      to: 'to' in change ? this.format(field, change.to, currency) : undefined,
    }));
  }

  private format(field: string, value: unknown, currency: string | undefined): string {
    if (value === null || value === undefined) return '–';
    if (MONEY_FIELDS.has(field) && currency && typeof value === 'number') return formatMoney(value, currency);
    if (DATE_FIELDS.has(field) && typeof value === 'string') return formatDate(value, 'd MMM y', this.locale);
    if (field === 'jobLevel') return levelLabel(value as JobLevel);
    if (field === 'reason') return reasonLabel(value as SalaryChangeReason);
    if (field === 'status') return value === 'ACTIVE' ? 'Active' : 'Terminated';
    return String(value);
  }
}
