import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { ActiveElement, ChartConfiguration, ChartEvent } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';

import { AnalyticsApi } from '../../core/analytics.api';
import { Overview, RecentChanges } from '../../core/models';
import { cssColor, provideAppCharts } from '../../shared/charts';
import { MoneyPipe } from '../../shared/money.pipe';
import { FxRatesDialog } from './fx-rates.dialog';

type BarChart = ChartConfiguration<'bar'>;

/** Org overview (US-11) with the last 30 days of salary changes (US-15). */
@Component({
  selector: 'app-dashboard-page',
  imports: [
    CardModule,
    ButtonModule,
    MessageModule,
    RouterLink,
    BaseChartDirective,
    MoneyPipe,
    DecimalPipe,
    DatePipe,
    FxRatesDialog,
  ],
  // Chart.js is registered here rather than app-wide, so it loads only with the pages that draw charts.
  providers: [provideAppCharts()],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage {
  private readonly analytics = inject(AnalyticsApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly overview = signal<Overview | null>(null);
  protected readonly recent = signal<RecentChanges | null>(null);
  protected readonly showRates = signal(false);

  protected readonly countryChart = computed(() => {
    const rows = this.overview()?.headcountByCountry ?? [];
    return this.barChart(
      rows.map((r) => r.name),
      rows.map((r) => r.headcount),
    );
  });

  protected readonly departmentChart = computed(() => {
    const rows = this.overview()?.headcountByDepartment ?? [];
    return this.barChart(
      rows.map((r) => r.name),
      rows.map((r) => r.headcount),
    );
  });

  // Clicking a bar opens the directory filtered to that country or department (ui-design.md 4.2).
  protected readonly countryOptions = this.barOptions((index) => {
    const code = this.overview()?.headcountByCountry[index]?.code;
    if (code) this.router.navigate(['/employees'], { queryParams: { country: code } });
  });

  protected readonly departmentOptions = this.barOptions((index) => {
    const id = this.overview()?.headcountByDepartment[index]?.id;
    if (id) this.router.navigate(['/employees'], { queryParams: { departmentId: id } });
  });

  constructor() {
    this.analytics
      .overview()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((overview) => this.overview.set(overview));
    this.analytics
      .recentChanges(30, 0, 1)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((recent) => this.recent.set(recent));
  }

  protected chartHeight(bars: number): number {
    return Math.max(160, bars * 36 + 40);
  }

  private barChart(labels: string[], values: number[]): BarChart['data'] {
    return {
      labels,
      datasets: [
        {
          data: values,
          label: 'Employees',
          backgroundColor: cssColor('--chart-bar'),
          hoverBackgroundColor: cssColor('--p-primary-700'),
          borderRadius: 6,
          maxBarThickness: 24,
        },
      ],
    };
  }

  private barOptions(onBarClick: (index: number) => void): BarChart['options'] {
    return {
      indexAxis: 'y', // horizontal bars keep long names readable
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { beginAtZero: true, ticks: { precision: 0 } },
        y: { grid: { display: false }, ticks: { color: cssColor('--p-text-color') } },
      },
      onHover: (event: ChartEvent, elements: ActiveElement[]) => {
        const canvas = event.native?.target as HTMLElement | undefined;
        if (canvas) canvas.style.cursor = elements.length ? 'pointer' : 'default';
      },
      onClick: (_event: ChartEvent, elements: ActiveElement[]) => {
        if (elements.length) onBarClick(elements[0].index);
      },
    };
  }
}
