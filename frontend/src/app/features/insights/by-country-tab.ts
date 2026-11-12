import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';

import { AnalyticsApi } from '../../core/analytics.api';
import { CountryPayRow } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';
import { ClientPager, PAGE_REPORT } from '../../shared/paging';
import { PAY_COLUMNS } from '../../shared/pay-columns';
import { PayViewState, PayViewToggle } from '../../shared/pay-view';
import { RangeBar } from '../../shared/range-bar';
import { RangeLegend } from '../../shared/range-legend';

/** Pay by country (US-12): local currency, with the USD median alongside for comparison. */
@Component({
  selector: 'app-by-country-tab',
  imports: [CardModule, TableModule, TooltipModule, MoneyPipe, DecimalPipe, RangeBar, RangeLegend, PayViewToggle],
  template: `
    <p-card>
      <div class="view-bar">
        <p class="caption">
          Each country in its own currency. The last column converts the typical salary to USD so countries can be
          compared. Hover a column name for what it means.
        </p>
        <app-pay-view-toggle />
      </div>
      @if (view() === 'bars') {
        <app-range-legend class="legend" />
      }
      <p-table [value]="rows()" class="pay-table" [loading]="!loaded()" [paginator]="true" [rows]="pager.size"
               [first]="pager.first()" (firstChange)="pager.first.set($event)" [defaultSortOrder]="-1"
               [showCurrentPageReport]="true" [currentPageReportTemplate]="pageReport">
        <!-- Only People and Typical (USD) sort: the other figures are each in their country's currency,
             so ordering them across countries would rank rupees against dollars. -->
        <ng-template #header>
          <tr>
            <th scope="col">Country</th>
            <th scope="col">Currency</th>
            <th scope="col" class="num" pSortableColumn="stats.headcount" [pTooltip]="cols.people.hint"><p-sortIcon field="stats.headcount" /> {{ cols.people.label }}</th>
            @if (view() === 'numbers') {
              <th scope="col" class="num" [pTooltip]="cols.min.hint">{{ cols.min.label }}</th>
              <th scope="col" class="num" [pTooltip]="cols.range.hint">{{ cols.range.label }}</th>
              <th scope="col" class="num" [pTooltip]="cols.median.hint">{{ cols.median.label }}</th>
              <th scope="col" class="num" [pTooltip]="cols.max.hint">{{ cols.max.label }}</th>
            } @else {
              <th scope="col" class="bar-col" [pTooltip]="cols.bar.hint">{{ cols.bar.label }}</th>
            }
            <th scope="col" class="num" [pTooltip]="cols.average.hint">{{ cols.average.label }}</th>
            <th scope="col" class="num" pSortableColumn="statsUsd.median" [pTooltip]="cols.medianUsd.hint"><p-sortIcon field="statsUsd.median" /> {{ cols.medianUsd.label }}</th>
          </tr>
        </ng-template>
        <ng-template #body let-r>
          <tr>
            <td class="strong">{{ r.country.name }}</td>
            <td class="muted">{{ r.currency }}</td>
            <td class="num">{{ r.stats.headcount | number }}</td>
            @if (view() === 'numbers') {
              <td class="num">{{ r.stats.min | money: r.currency }}</td>
              <td class="num">{{ r.stats.p25 | money: r.currency }} – {{ r.stats.p75 | money: r.currency }}</td>
              <td class="num strong">{{ r.stats.median | money: r.currency }}</td>
              <td class="num">{{ r.stats.max | money: r.currency }}</td>
            } @else {
              <td class="bar-col"><app-range-bar [stats]="r.stats" [currency]="r.currency" [labels]="true" /></td>
            }
            <td class="num">{{ r.stats.average | money: r.currency }}</td>
            <td class="num">{{ r.statsUsd.median | money: 'USD' }}</td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="9"><div class="empty">No active employees.</div></td></tr>
        </ng-template>
      </p-table>
    </p-card>
  `,
  styles: `
    .strong {
      font-weight: 600;
    }
    .legend {
      margin-bottom: 16px;
    }
  `,
})
export class ByCountryTab {
  protected readonly rows = signal<CountryPayRow[]>([]);
  protected readonly loaded = signal(false);
  protected readonly pager = new ClientPager(this.rows);
  protected readonly pageReport = PAGE_REPORT;
  protected readonly cols = PAY_COLUMNS;
  /** In the bar view, the bar (with its figures) takes the place of the four range columns. */
  protected readonly view = inject(PayViewState).view;

  constructor() {
    inject(AnalyticsApi)
      .byCountry()
      .pipe(takeUntilDestroyed())
      .subscribe((rows) => {
        this.rows.set(rows);
        this.loaded.set(true);
      });
  }
}
