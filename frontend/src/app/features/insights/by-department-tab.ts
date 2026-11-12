import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { EMPTY, catchError, switchMap } from 'rxjs';

import { AnalyticsApi } from '../../core/analytics.api';
import { DepartmentPay } from '../../core/models';
import { ReferenceDataService } from '../../core/reference-data';
import { MoneyPipe } from '../../shared/money.pipe';
import { ClientPager, PAGE_REPORT } from '../../shared/paging';
import { PAY_COLUMNS } from '../../shared/pay-columns';
import { PayViewState, PayViewToggle } from '../../shared/pay-view';
import { RangeBar } from '../../shared/range-bar';
import { RangeLegend } from '../../shared/range-legend';

/**
 * Pay by department (US-13). Across all countries every figure is converted to USD; for one country the
 * figures stay in its currency. A caption always says which.
 */
@Component({
  selector: 'app-by-department-tab',
  imports: [FormsModule, CardModule, SelectModule, TableModule, TooltipModule, MoneyPipe, DecimalPipe, RangeBar, RangeLegend, PayViewToggle],
  template: `
    <p-card>
      <div class="table-toolbar">
        <div class="filters">
          <div class="field">
            <label for="department-country">Country</label>
            <p-select inputId="department-country" [options]="countryOptions" optionLabel="name" optionValue="code"
                      [ngModel]="country()" (ngModelChange)="country.set($event)" [fluid]="true" />
          </div>
        </div>
        <app-pay-view-toggle />
      </div>
      <p class="caption intro">{{ caption() }}</p>
      @if (view() === 'bars') {
        <app-range-legend class="legend" />
      }
      <p-table [value]="data()?.rows ?? []" class="pay-table" [loading]="!data()" [paginator]="true" [rows]="pager.size()"
               [first]="pager.first()" (firstChange)="pager.first.set($event)"
               (rowsChange)="pager.size.set($event)" [rowsPerPageOptions]="pager.sizes" [defaultSortOrder]="-1"
               [showCurrentPageReport]="true" [currentPageReportTemplate]="pageReport">
        <ng-template #header>
          <tr>
            <th scope="col">Department</th>
            <th scope="col" class="num" pSortableColumn="stats.headcount" [pTooltip]="cols.people.hint"><p-sortIcon field="stats.headcount" /> {{ cols.people.label }}</th>
            @if (view() === 'numbers') {
              <th scope="col" class="num" pSortableColumn="stats.min" [pTooltip]="cols.min.hint"><p-sortIcon field="stats.min" /> {{ cols.min.label }}</th>
              <th scope="col" class="num" pSortableColumn="stats.p25" [pTooltip]="cols.range.hint"><p-sortIcon field="stats.p25" /> {{ cols.range.label }}</th>
              <th scope="col" class="num" pSortableColumn="stats.median" [pTooltip]="cols.median.hint"><p-sortIcon field="stats.median" /> {{ cols.median.label }}</th>
              <th scope="col" class="num" pSortableColumn="stats.max" [pTooltip]="cols.max.hint"><p-sortIcon field="stats.max" /> {{ cols.max.label }}</th>
            } @else {
              <th scope="col" class="bar-col" pSortableColumn="stats.median" [pTooltip]="cols.bar.hint">{{ cols.bar.label }} <p-sortIcon field="stats.median" /></th>
            }
            <th scope="col" class="num" pSortableColumn="stats.average" [pTooltip]="cols.average.hint"><p-sortIcon field="stats.average" /> {{ cols.average.label }}</th>
          </tr>
        </ng-template>
        <ng-template #body let-r>
          <tr>
            <td class="strong">{{ r.department.name }}</td>
            <td class="num">{{ r.stats.headcount | number }}</td>
            @if (view() === 'numbers') {
              <td class="num">{{ r.stats.min | money: currency() }}</td>
              <td class="num">{{ r.stats.p25 | money: currency() }} – {{ r.stats.p75 | money: currency() }}</td>
              <td class="num strong">{{ r.stats.median | money: currency() }}</td>
              <td class="num">{{ r.stats.max | money: currency() }}</td>
            } @else {
              <td class="bar-col"><app-range-bar [stats]="r.stats" [currency]="currency()" [labels]="true" /></td>
            }
            <td class="num">{{ r.stats.average | money: currency() }}</td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="7"><div class="empty">No active employees in this country.</div></td></tr>
        </ng-template>
      </p-table>
    </p-card>
  `,
  styles: `
    .strong {
      font-weight: 600;
    }
    .intro,
    .legend {
      margin-bottom: 16px;
    }
  `,
})
export class ByDepartmentTab {
  private readonly reference = inject(ReferenceDataService);
  /** "All countries" first; it shows every figure in USD. */
  protected readonly countryOptions = [
    { code: null, name: 'All countries (USD)' },
    ...this.reference.snapshot.countries.map((c) => ({ code: c.code as string | null, name: c.name })),
  ];

  protected readonly country = signal<string | null>(null);
  protected readonly data = signal<DepartmentPay | null>(null);
  protected readonly pager = new ClientPager(computed(() => this.data()?.rows ?? []));
  protected readonly currency = computed(() => this.data()?.currency ?? 'USD');
  protected readonly pageReport = PAGE_REPORT;

  protected readonly caption = computed(() => {
    const code = this.country();
    if (!code) return 'All countries. Every figure is converted to USD so that currencies are not mixed. Hover a column name for what it means.';
    const country = this.reference.country(code);
    return `${country?.name}. Figures are in ${country?.currency}. Hover a column name for what it means.`;
  });

  protected readonly cols = PAY_COLUMNS;
  /** In the bar view, the bar (with its figures) takes the place of the four range columns. */
  protected readonly view = inject(PayViewState).view;

  constructor() {
    const api = inject(AnalyticsApi);
    toObservable(this.country)
      .pipe(
        switchMap((country) => api.byDepartment(country).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe((data) => this.data.set(data));
  }
}
