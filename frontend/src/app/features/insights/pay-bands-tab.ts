import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { EMPTY, catchError, switchMap } from 'rxjs';

import { AnalyticsApi } from '../../core/analytics.api';
import { JobLevel, PayBands } from '../../core/models';
import { ReferenceDataService } from '../../core/reference-data';
import { levelLabel } from '../../shared/labels';
import { MoneyPipe } from '../../shared/money.pipe';
import { ClientPager, PAGE_REPORT } from '../../shared/paging';
import { RangeBar } from '../../shared/range-bar';
import { RangeLegend } from '../../shared/range-legend';
import { PAY_COLUMNS } from '../../shared/pay-columns';
import { PayViewState, PayViewToggle } from '../../shared/pay-view';

/**
 * Pay bands by job title and level (US-14): "what do we pay a Senior Software Engineer in Germany?".
 * One row per title and level with min, 25th percentile, median, 75th percentile and max.
 */
@Component({
  selector: 'app-pay-bands-tab',
  imports: [
    FormsModule,
    CardModule,
    SelectModule,
    TableModule,
    TagModule,
    TooltipModule,
    MoneyPipe,
    DecimalPipe,
    RangeBar,
    RangeLegend,
    PayViewToggle,
  ],
  templateUrl: './pay-bands-tab.html',
  styleUrl: './pay-bands-tab.scss',
})
export class PayBandsTab {
  private readonly reference = inject(ReferenceDataService);
  protected readonly ref = this.reference.snapshot;
  protected readonly levelLabel = levelLabel;
  protected readonly columns = PAY_COLUMNS;
  protected readonly view = inject(PayViewState).view;

  protected readonly departmentId = signal<number | null>(null);
  protected readonly jobTitleId = signal<number | null>(null);
  protected readonly jobLevel = signal<JobLevel | null>(null);
  protected readonly country = signal<string | null>(null);
  protected readonly data = signal<PayBands | null>(null);
  protected readonly pager = new ClientPager(computed(() => this.data()?.rows ?? []));
  protected readonly currency = computed(() => this.data()?.currency ?? 'USD');
  protected readonly pageReport = PAGE_REPORT;

  // Each filter starts with an "All …" option, whose value (null) means no filter.
  protected readonly departmentOptions = [
    { id: null as number | null, name: 'All departments' },
    ...this.ref.departments.map((d) => ({ id: d.id as number | null, name: d.name })),
  ];
  protected readonly levelOptions = [
    { value: null as JobLevel | null, label: 'All levels' },
    ...this.ref.jobLevels.map((l) => ({ value: l.code as JobLevel | null, label: `${l.code} ${l.name}` })),
  ];
  protected readonly countryOptions = [
    { code: null as string | null, name: 'All countries (USD)' },
    ...this.ref.countries.map((c) => ({ code: c.code as string | null, name: c.name })),
  ];

  /** Job titles belong to a department, so the list follows the chosen department. */
  protected readonly jobTitleOptions = computed(() => {
    const departmentId = this.departmentId();
    const titles = departmentId == null ? this.ref.jobTitles : this.reference.jobTitlesFor([departmentId]);
    return [{ id: null as number | null, name: 'All job titles' }, ...titles.map((t) => ({ id: t.id as number | null, name: t.name }))];
  });

  private readonly filter = computed(() => ({
    departmentId: this.departmentId(),
    jobTitleId: this.jobTitleId(),
    jobLevel: this.jobLevel(),
    country: this.country(),
  }));

  protected readonly caption = computed(() => {
    const code = this.country();
    return code
      ? `Figures are in ${this.reference.country(code)?.currency}.`
      : 'All countries, converted to USD. Choose a country to see local pay.';
  });

  /** A chosen job title that is not in the new department is cleared. */
  protected selectDepartment(departmentId: number | null): void {
    const title = this.ref.jobTitles.find((t) => t.id === this.jobTitleId());
    if (departmentId != null && title && title.departmentId !== departmentId) {
      this.jobTitleId.set(null);
    }
    this.departmentId.set(departmentId);
  }

  constructor() {
    const api = inject(AnalyticsApi);
    toObservable(this.filter)
      .pipe(
        switchMap((filter) => api.payBands(filter).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe((data) => this.data.set(data));
  }
}
