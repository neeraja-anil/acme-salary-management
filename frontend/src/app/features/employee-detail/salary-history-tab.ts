import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { EMPTY, catchError, switchMap } from 'rxjs';

import { fromIsoDate, today } from '../../core/dates';
import { EmployeesApi } from '../../core/employees.api';
import { SalaryHistoryEntry } from '../../core/models';
import { cssColor, provideAppCharts } from '../../shared/charts';
import { reasonLabel } from '../../shared/labels';
import { MoneyPipe, formatMoney } from '../../shared/money.pipe';
import { ClientPager, PAGE_REPORT } from '../../shared/paging';

const DAY_MS = 24 * 60 * 60 * 1000;
const DAY_FORMAT = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
const MONTH_FORMAT = new Intl.DateTimeFormat('en-GB', { month: 'short', year: 'numeric' });

/** Salary history, newest first, with a chart of salary over time (US-09). */
@Component({
  selector: 'app-salary-history-tab',
  imports: [CardModule, TableModule, BaseChartDirective, MoneyPipe, DatePipe, DecimalPipe],
  // Chart.js is registered here rather than app-wide, so it loads only with the pages that draw charts.
  providers: [provideAppCharts()],
  template: `
    <div class="stack">
      @if (chart(); as c) {
        <p-card header="Salary over time">
          <div class="chart">
            <canvas baseChart type="line" [data]="c.data" [options]="c.options" aria-label="Salary over time"></canvas>
          </div>
        </p-card>
      }
      <p-card>
        <p-table [value]="entries()" [loading]="!loaded()" [paginator]="true" [rows]="pager.size()"
                 [first]="pager.first()" (firstChange)="pager.first.set($event)"
               (rowsChange)="pager.size.set($event)" [rowsPerPageOptions]="pager.sizes"
                 [showCurrentPageReport]="true" [currentPageReportTemplate]="pageReport">
          <ng-template #header>
            <tr>
              <th scope="col">Effective date</th>
              <th scope="col" class="num">Amount</th>
              <th scope="col" class="num">Change</th>
              <th scope="col">Reason</th>
              <th scope="col">Note</th>
              <th scope="col">Changed by</th>
              <th scope="col">Changed at</th>
            </tr>
          </ng-template>
          <ng-template #body let-h>
            <tr>
              <td>{{ h.effectiveDate | date: 'd MMM y' }}</td>
              <td class="num strong">{{ h.amount | money: h.currency }}</td>
              <td class="num" [class.positive]="h.changePercent > 0" [class.negative]="h.changePercent < 0">
                @if (h.changePercent !== null) {
                  {{ h.changePercent > 0 ? '+' : '' }}{{ h.changePercent | number: '1.2-2' }}%
                } @else {
                  <span class="muted">–</span>
                }
              </td>
              <td [class.muted]="!h.reason">{{ reasonLabel(h.reason) }}</td>
              <td class="note">{{ h.note }}</td>
              <td>{{ h.changedBy }}</td>
              <td class="muted">{{ h.changedAt | date: 'd MMM y, HH:mm' }}</td>
            </tr>
          </ng-template>
          <ng-template #emptymessage>
            <tr><td colspan="7"><div class="empty">No salary records yet.</div></td></tr>
          </ng-template>
        </p-table>
      </p-card>
    </div>
  `,
  styles: `
    .chart {
      position: relative;
      height: 240px;
    }
    .strong {
      font-weight: 600;
    }
    .note {
      max-width: 280px;
      white-space: normal;
    }
  `,
})
export class SalaryHistoryTab {
  private readonly api = inject(EmployeesApi);

  readonly employeeId = input.required<number>();
  readonly refreshKey = input(0);

  protected readonly entries = signal<SalaryHistoryEntry[]>([]);
  protected readonly loaded = signal(false);
  protected readonly pager = new ClientPager(this.entries);
  protected readonly pageReport = PAGE_REPORT;
  protected readonly reasonLabel = reasonLabel;

  /**
   * A step chart on a real time axis: each salary holds until the next change, and the current salary is
   * drawn up to today. Several changes on the same date become one vertical step. Shown only when the
   * salaries span more than one date; otherwise the table says it all.
   */
  protected readonly chart = computed(() => {
    const oldestFirst = [...this.entries()].reverse();
    if (new Set(oldestFirst.map((h) => h.effectiveDate)).size < 2) return null;

    const currency = oldestFirst[0].currency;
    const points = oldestFirst.map((h) => ({ x: fromIsoDate(h.effectiveDate).getTime(), y: h.amount }));
    const realPoints = points.length;
    const now = today().getTime();
    const latest = points[points.length - 1];
    if (latest.x < now) {
      points.push({ x: now, y: latest.y }); // extends the current salary to today; not a change itself
    }

    const start = points[0].x;
    // Short histories get day-level tick labels; longer ones get month and year.
    const tickFormat = now - start < 120 * DAY_MS ? DAY_FORMAT : MONTH_FORMAT;
    const color = cssColor('--chart-bar');

    const data: ChartConfiguration<'line'>['data'] = {
      datasets: [
        {
          data: points,
          label: 'Salary',
          stepped: 'before', // hold each salary until the next point, then step
          borderColor: color,
          backgroundColor: color,
          borderWidth: 2,
          pointBackgroundColor: cssColor('--p-content-background'),
          pointBorderWidth: 2,
          pointRadius: points.map((_, i) => (i < realPoints ? 4 : 0)),
          pointHoverRadius: points.map((_, i) => (i < realPoints ? 6 : 0)),
        },
      ],
    };
    const options: ChartConfiguration<'line'>['options'] = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          filter: (item) => item.dataIndex < realPoints,
          callbacks: {
            title: (items) => DAY_FORMAT.format(items[0].parsed.x ?? 0),
            label: (item) => formatMoney(item.parsed.y ?? 0, currency),
          },
        },
      },
      scales: {
        x: {
          type: 'linear',
          min: start,
          max: Math.max(now, latest.x),
          grid: { display: false },
          ticks: { maxTicksLimit: 8, callback: (value) => tickFormat.format(Number(value)) },
        },
        y: { ticks: { callback: (value) => formatMoney(Number(value), currency, true) } },
      },
    };
    return { data, options };
  });

  constructor() {
    // Reload whenever the employee changes or the profile reports a save; a newer request cancels an older one.
    toObservable(computed(() => ({ id: this.employeeId(), key: this.refreshKey() })))
      .pipe(
        switchMap(({ id }) => this.api.salaryHistory(id).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe((entries) => {
        this.entries.set(entries);
        this.loaded.set(true);
      });
  }
}
