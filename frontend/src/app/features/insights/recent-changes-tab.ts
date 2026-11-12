import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { SortEvent } from 'primeng/api';
import { TableModule } from 'primeng/table';
import { EMPTY, catchError, switchMap } from 'rxjs';

import { AnalyticsApi } from '../../core/analytics.api';
import { RecentChanges } from '../../core/models';
import { reasonLabel } from '../../shared/labels';
import { MoneyPipe } from '../../shared/money.pipe';
import { PAGE_REPORT, TABLE_PAGE_SIZE } from '../../shared/paging';

interface RecentChangesSort {
  field: 'effectiveDate' | 'changePercent';
  /** 1 ascending, -1 descending (PrimeNG's convention). */
  order: 1 | -1;
}

/** Salary changes in the last N days, to track a review cycle (US-15). */
@Component({
  selector: 'app-recent-changes-tab',
  imports: [
    FormsModule,
    RouterLink,
    CardModule,
    SelectModule,
    TableModule,
    MoneyPipe,
    DecimalPipe,
    DatePipe,
  ],
  templateUrl: './recent-changes-tab.html',
  styles: `
    .period {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 12px 24px;
    }
    .period .field {
      width: 200px;
    }
    .period .caption {
      flex: 1 1 320px;
      padding-bottom: 8px;
    }
    /* Reasons beside the list of changes on wide screens, above it on narrow ones. */
    .split {
      display: grid;
      grid-template-columns: minmax(280px, 1fr) 3fr;
      gap: var(--app-gap);
      align-items: start;
    }
    @media (max-width: 1200px) {
      .split {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class RecentChangesTab {
  protected readonly periodOptions = [30, 90, 180, 365].map((value) => ({ value, label: `Last ${value} days` }));
  protected readonly pageSize = TABLE_PAGE_SIZE;
  protected readonly pageReport = PAGE_REPORT;
  protected readonly reasonLabel = reasonLabel;

  protected readonly days = signal(30);
  /** Newest first by default, like the server. Only the % change and the date can be sorted: amounts mix currencies. */
  protected readonly sort = signal<RecentChangesSort>({ field: 'effectiveDate', order: -1 });
  /** Back to the first page whenever the period or the sort changes. */
  protected readonly page = linkedSignal({ source: () => [this.days(), this.sort()], computation: () => 0 });
  protected readonly data = signal<RecentChanges | null>(null);


  constructor() {
    const api = inject(AnalyticsApi);
    toObservable(computed(() => ({ days: this.days(), page: this.page(), sort: this.sort() })))
      .pipe(
        switchMap(({ days, page, sort }) =>
          api
            .recentChanges(days, page, this.pageSize, `${sort.field},${sort.order === 1 ? 'asc' : 'desc'}`)
            .pipe(catchError(() => EMPTY)),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((data) => this.data.set(data));
  }

  /**
   * The table reports every sort, including when it applies the one it was given, so only a real change
   * is stored (a new object would reload the page for nothing).
   */
  protected setSort(event: SortEvent): void {
    const field = event.field === 'changePercent' ? 'changePercent' : 'effectiveDate';
    const order = event.order === 1 ? 1 : -1;
    const current = this.sort();
    if (current.field !== field || current.order !== order) {
      this.sort.set({ field, order });
    }
  }

  protected signed(value: number | null): string {
    return value !== null && value > 0 ? '+' : '';
  }
}
