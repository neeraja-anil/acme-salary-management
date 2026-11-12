import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { StepperModule } from 'primeng/stepper';
import { TableModule } from 'primeng/table';

import { isUnexpected, messageOf, problemOf } from '../../core/api-error';
import { ImportApi } from '../../core/import.api';
import { ImportReport, ImportResult } from '../../core/models';
import { Notify } from '../../core/notify';
import { MoneyPipe } from '../../shared/money.pipe';
import { ClientPager, PAGE_REPORT } from '../../shared/paging';

const MAX_FILE_BYTES = 5 * 1024 * 1024;

/** The column rules shown beside the upload (api-contracts.md section 5a). */
const COLUMNS = [
  { name: 'first_name', required: true, rule: 'Up to 100 characters' },
  { name: 'last_name', required: true, rule: 'Up to 100 characters' },
  { name: 'email', required: true, rule: 'A valid email, not used by another employee or row' },
  { name: 'country_code', required: true, rule: 'A known country, e.g. DE, IN, US' },
  { name: 'department', required: true, rule: 'A known department, e.g. Engineering' },
  { name: 'job_title', required: true, rule: 'A job title in that department' },
  { name: 'job_level', required: true, rule: 'L1 to L5' },
  { name: 'hire_date', required: true, rule: 'YYYY-MM-DD, not in the future' },
  { name: 'annual_salary', required: true, rule: 'A whole number, no symbols, e.g. 78000' },
  { name: 'currency', required: true, rule: "The country's currency, e.g. EUR for DE" },
  { name: 'salary_effective_date', required: false, rule: 'When the current salary started; blank means the hire date' },
  { name: 'salary_reason', required: false, rule: 'PROMOTION, ANNUAL_REVIEW, MARKET_ADJUSTMENT or CORRECTION' },
];

/**
 * Import employees from a CSV file (US-21, ui-design.md section 4.8), in three steps: upload and check,
 * review the problems, done. "Check file" saves nothing; the import sends the same file again and is all
 * or nothing.
 */
@Component({
  selector: 'app-import-page',
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    ButtonModule,
    CardModule,
    MessageModule,
    StepperModule,
    TableModule,
    MoneyPipe,
  ],
  templateUrl: './import.page.html',
  styleUrl: './import.page.scss',
})
export class ImportPage {
  private readonly api = inject(ImportApi);
  private readonly notify = inject(Notify);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = COLUMNS;
  protected readonly pageReport = PAGE_REPORT;

  protected readonly step = signal<1 | 2 | 3>(1);
  protected readonly file = signal<File | null>(null);
  protected readonly fileError = signal<string | null>(null);
  protected readonly dragging = signal(false);
  protected readonly checking = signal(false);
  protected readonly report = signal<ImportReport | null>(null);
  protected readonly importing = signal(false);
  protected readonly importError = signal<string | null>(null);
  protected readonly result = signal<ImportResult | null>(null);
  protected readonly pager = new ClientPager(computed(() => this.report()?.errors ?? []));

  protected onFileChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.choose(input.files?.[0] ?? null);
    input.value = ''; // so choosing the same file again (after fixing it) still counts as a change
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault(); // allows dropping
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.choose(event.dataTransfer?.files?.[0] ?? null);
  }

  protected check(): void {
    const file = this.file();
    if (!file) return;
    this.checking.set(true);
    this.fileError.set(null);
    this.api
      .preview(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (report) => {
          this.checking.set(false);
          this.report.set(report);
          this.importError.set(null);
          this.step.set(2);
        },
        error: (error: unknown) => {
          this.checking.set(false);
          // INVALID_FILE and FILE_TOO_LARGE explain themselves (missing columns, not UTF-8, too many rows).
          if (!isUnexpected(error)) this.fileError.set(messageOf(error));
        },
      });
  }

  protected runImport(): void {
    const file = this.file();
    if (!file) return;
    this.importing.set(true);
    this.importError.set(null);
    this.api
      .importFile(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.importing.set(false);
          this.result.set(result);
          this.step.set(3);
          this.notify.success(`${result.importedCount} employees imported`);
        },
        error: (error: unknown) => {
          this.importing.set(false);
          if (isUnexpected(error)) return;
          // The data changed since the check (e.g. someone added one of these emails): show the new report.
          const report = (problemOf(error) as { report?: ImportReport } | null)?.report;
          if (report) {
            this.report.set(report);
          }
          this.importError.set(messageOf(error));
        },
      });
  }

  /** Back to step 1. After fixing the file in Excel, choose it again: browsers won't re-read a file changed on disk. */
  protected chooseAnother(): void {
    this.report.set(null);
    this.importError.set(null);
    this.step.set(1);
  }

  protected startOver(): void {
    this.file.set(null);
    this.report.set(null);
    this.result.set(null);
    this.step.set(1);
  }

  protected downloadTemplate(): void {
    this.api
      .template()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'employees-import-template.csv';
        link.click();
        URL.revokeObjectURL(url);
      });
  }

  protected sizeLabel(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  /** Obvious problems are caught here, before uploading anything. */
  private choose(file: File | null): void {
    this.fileError.set(null);
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.fileError.set('Choose a .csv file. In Excel, use File → Save as → CSV UTF-8.');
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.fileError.set('The file is over 5 MB. Split it into smaller files.');
      return;
    }
    this.file.set(file);
  }
}
