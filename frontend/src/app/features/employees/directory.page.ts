import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SkeletonModule } from 'primeng/skeleton';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';

import { isUnexpected, messageOf } from '../../core/api-error';
import { EmployeesApi } from '../../core/employees.api';
import { EmployeeQuery, EmployeeSummary, Page, StatusFilter } from '../../core/models';
import { ReferenceDataService } from '../../core/reference-data';
import { levelLabel } from '../../shared/labels';
import { MoneyPipe } from '../../shared/money.pipe';
import { PAGE_REPORT } from '../../shared/paging';
import { StatusChip } from '../../shared/status-chip';
import { DEFAULT_QUERY, PAGE_SIZES, SalaryView, paramsFromQuery, queryFromParams, salaryViewFromParams } from './directory-query';

interface ActiveFilter {
  label: string;
  remove: Partial<EmployeeQuery>;
}

/** Employee directory: server-side search, filters, sorting and paging (US-01 to US-03). */
@Component({
  selector: 'app-directory-page',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    ChipModule,
    IconFieldModule,
    InputIconModule,
    InputTextModule,
    MessageModule,
    MultiSelectModule,
    SelectModule,
    SelectButtonModule,
    SkeletonModule,
    TableModule,
    TooltipModule,
    MoneyPipe,
    StatusChip,
  ],
  templateUrl: './directory.page.html',
  styleUrl: './directory.page.scss',
})
export class DirectoryPage {
  private readonly api = inject(EmployeesApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly ref = inject(ReferenceDataService).snapshot;
  private readonly reference = inject(ReferenceDataService);

  protected readonly pageSizes = PAGE_SIZES;
  protected readonly pageReport = PAGE_REPORT;
  protected readonly levelOptions = this.ref.jobLevels.map((l) => ({ value: l.code, label: `${l.code} ${l.name}` }));
  protected readonly statusOptions: { label: string; value: StatusFilter }[] = [
    { label: 'Active', value: 'ACTIVE' },
    { label: 'Terminated', value: 'TERMINATED' },
    { label: 'All', value: 'ALL' },
  ];
  /** Placeholder rows for the first load: one width per column. */
  protected readonly skeletonRows = [1, 2, 3, 4, 5, 6, 7, 8];
  protected readonly skeletonWidths = ['48px', '140px', '160px', '110px', '32px', '90px', '64px'];

  /** The URL is the single source of truth for what the table shows. */
  protected readonly query = toSignal(this.route.queryParamMap.pipe(map(queryFromParams)), {
    initialValue: DEFAULT_QUERY,
  });

  /** Local currency or USD, from ?salaryIn=usd. */
  protected readonly salaryView = toSignal(this.route.queryParamMap.pipe(map(salaryViewFromParams)), {
    initialValue: 'local' as SalaryView,
  });

  /** USD adds nothing when every chosen country already pays in USD (today: only the United States). */
  protected readonly usdUnavailable = computed(() => {
    const countries = this.query().country;
    return countries.length > 0 && countries.every((code) => this.reference.country(code)?.currency === 'USD');
  });

  protected readonly showUsd = computed(() => this.salaryView() === 'usd' && !this.usdUnavailable());

  protected readonly salaryViewOptions = computed(() => [
    { label: 'Local currency', value: 'local', disabled: false },
    { label: 'USD', value: 'usd', disabled: this.usdUnavailable() },
  ]);

  /** In the USD view, "sort by salary" means by the USD amount, so currencies are compared fairly. */
  private readonly apiQuery = computed<EmployeeQuery>(() => {
    const query = this.query();
    const [field, direction = 'asc'] = query.sort.split(',');
    return this.showUsd() && field === 'salary' ? { ...query, sort: `salaryUsd,${direction}` } : query;
  });

  protected readonly result = signal<Page<EmployeeSummary> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly search = new FormControl('', { nonNullable: true });

  protected readonly jobTitleOptions = computed(() => this.reference.jobTitlesFor(this.query().departmentId));

  protected readonly sortField = computed(() => this.query().sort.split(',')[0]);
  /** PrimeNG's sort order: 1 ascending, -1 descending. */
  protected readonly sortOrder = computed(() => (this.query().sort.split(',')[1] === 'desc' ? -1 : 1));

  protected readonly activeFilters = computed<ActiveFilter[]>(() => {
    const q = this.query();
    const filters: ActiveFilter[] = [];
    q.country.forEach((code) =>
      filters.push({
        label: this.reference.country(code)?.name ?? code,
        remove: { country: q.country.filter((c) => c !== code) },
      }),
    );
    q.departmentId.forEach((id) =>
      filters.push({
        label: this.ref.departments.find((d) => d.id === id)?.name ?? `Department ${id}`,
        remove: this.withDepartments(q.departmentId.filter((d) => d !== id)),
      }),
    );
    q.jobTitleId.forEach((id) =>
      filters.push({
        label: this.ref.jobTitles.find((t) => t.id === id)?.name ?? `Job title ${id}`,
        remove: { jobTitleId: q.jobTitleId.filter((t) => t !== id) },
      }),
    );
    q.jobLevel.forEach((level) =>
      filters.push({ label: levelLabel(level), remove: { jobLevel: q.jobLevel.filter((l) => l !== level) } }),
    );
    if (q.status !== 'ACTIVE') {
      filters.push({ label: q.status === 'ALL' ? 'All statuses' : 'Terminated', remove: { status: 'ACTIVE' } });
    }
    return filters;
  });

  /** Local amounts in several currencies can't be compared, so suggest the USD view when sorting them. */
  protected readonly showCurrencyHint = computed(() => {
    const rows = this.result()?.content ?? [];
    return (
      !this.showUsd() && this.sortField() === 'salary' && new Set(rows.map((r) => r.currentSalary.currency)).size > 1
    );
  });

  constructor() {
    const destroyRef = inject(DestroyRef);

    toObservable(this.apiQuery)
      .pipe(
        tap(() => this.loading.set(true)),
        switchMap((query) =>
          this.api.search(query).pipe(
            catchError((error: unknown) => {
              this.error.set(isUnexpected(error) ? null : messageOf(error));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.error.set(null);
          this.result.set(page);
        }
      });

    // Search is sent 300 ms after the user stops typing.
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(destroyRef))
      .subscribe((q) => this.update({ q }));

    // Keep the box in sync when the URL changes some other way (back button, "Clear all").
    effect(() => {
      const q = this.query().q;
      if (this.search.value.trim() !== q) {
        this.search.setValue(q, { emitEvent: false });
      }
    });
  }

  /** Any filter change goes back to page 1. */
  protected update(changes: Partial<EmployeeQuery>, resetPage = true): void {
    const next: EmployeeQuery = { ...this.query(), ...changes };
    if (resetPage && changes.page === undefined) {
      next.page = 0;
    }
    this.router.navigate([], { relativeTo: this.route, queryParams: paramsFromQuery(next, this.salaryView()) });
  }

  /** Switching currency can reorder a salary sort, so it starts again from page 1. */
  protected setSalaryView(view: SalaryView): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: paramsFromQuery({ ...this.query(), page: 0 }, view),
    });
  }

  /** Changing departments drops selected job titles that no longer belong to them. */
  protected withDepartments(departmentId: number[]): Partial<EmployeeQuery> {
    const allowed = new Set(this.reference.jobTitlesFor(departmentId).map((t) => t.id));
    return { departmentId, jobTitleId: this.query().jobTitleId.filter((id) => allowed.has(id)) };
  }

  protected clearSearch(): void {
    this.search.setValue('', { emitEvent: false });
    this.update({ q: '' });
  }

  protected clearAll(): void {
    this.search.setValue('', { emitEvent: false });
    this.update({ ...DEFAULT_QUERY, size: this.query().size, sort: this.query().sort });
  }

  /**
   * Sorting and paging come from the table as one lazy-load event. The table also fires it when the URL
   * has already changed (e.g. the sort column is set from the URL), so an event that matches the current
   * query does nothing. A new sort or page size goes back to page 1.
   */
  protected onLazyLoad(event: TableLazyLoadEvent): void {
    const q = this.query();
    const size = event.rows ?? q.size;
    const field = typeof event.sortField === 'string' && event.sortField ? event.sortField : this.sortField();
    const sort = `${field},${event.sortOrder === -1 ? 'desc' : 'asc'}`;
    const page = sort !== q.sort || size !== q.size ? 0 : Math.floor((event.first ?? 0) / size);
    if (page === q.page && size === q.size && sort === q.sort) return;
    this.update({ page, size, sort }, false);
  }

  protected open(employee: EmployeeSummary): void {
    this.router.navigate(['/employees', employee.id]);
  }
}
