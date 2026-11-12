import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { startWith } from 'rxjs';

import { isStaleVersion, isUnexpected, messageOf } from '../../core/api-error';
import { dateInput, fromIsoDate, today, toIsoDate } from '../../core/dates';
import { EmployeesApi } from '../../core/employees.api';
import { EmployeeDetail, SalaryChangeReason } from '../../core/models';
import { ReferenceDataService } from '../../core/reference-data';
import { DescribedBy } from '../../shared/described-by';
import { reasonLabel } from '../../shared/labels';
import { MoneyPipe } from '../../shared/money.pipe';

/** How a dialog that saves something ended; undefined when the user cancelled. */
export type DialogOutcome = 'saved' | 'stale';

function differentFrom(current: number): ValidatorFn {
  return (control: AbstractControl) => (control.value === current ? { sameAsCurrent: true } : null);
}

/**
 * Change salary (US-08) in two steps: enter the change, then confirm it. Every change is confirmed,
 * whatever its size, showing the current and new salary side by side.
 * Render it with `@if` to open it; it emits `closed` with the outcome once it has closed.
 */
@Component({
  selector: 'app-change-salary-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    DatePickerModule,
    DialogModule,
    InputNumberModule,
    MessageModule,
    SelectModule,
    TagModule,
    TextareaModule,
    DescribedBy,
    MoneyPipe,
    DatePipe,
  ],
  templateUrl: './change-salary.dialog.html',
  styles: `
    .current {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: 8px;
      padding: 14px 16px;
      margin-bottom: 20px;
      border-radius: 10px;
      background: var(--p-surface-50);
      border: 1px solid var(--p-content-border-color);
    }
    .current-amount {
      margin-top: 2px;
      font-size: 20px;
      font-weight: 600;
      color: var(--p-surface-900);
      font-variant-numeric: tabular-nums;
    }
    .compare {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 16px;
      padding: 16px;
      margin-bottom: 20px;
      border-radius: 10px;
      background: var(--p-surface-50);
      border: 1px solid var(--p-content-border-color);
    }
    .compare-amount {
      font-size: 20px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .compare p-tag {
      margin-left: auto;
    }
    .confirm-error {
      display: block;
      margin-bottom: 16px;
    }
  `,
})
export class ChangeSalaryDialog implements OnInit {
  private readonly api = inject(EmployeesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly employee = input.required<EmployeeDetail>();
  readonly closed = output<DialogOutcome | undefined>();

  protected readonly reasonOptions = inject(ReferenceDataService).snapshot.salaryChangeReasons.map((value) => ({
    value,
    label: reasonLabel(value),
  }));
  protected readonly reasonLabel = reasonLabel;

  protected readonly visible = signal(true);
  protected readonly step = signal<'edit' | 'confirm'>('edit');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Reported through `closed` once the dialog has finished closing. */
  protected outcome: DialogOutcome | undefined;

  // A change cannot take effect before the current salary did, or in the future.
  protected readonly minDate = computed(() => fromIsoDate(this.employee().currentSalary.effectiveDate));
  protected readonly maxDate = today();

  protected readonly form = new FormGroup({
    amount: new FormControl<number | null>(null, [Validators.required, Validators.min(1), Validators.max(100_000_000)]),
    effectiveDate: new FormControl<Date | null>(today(), Validators.required),
    reason: new FormControl<SalaryChangeReason | null>(null, Validators.required),
    note: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
  });

  private readonly reason = toSignal(
    this.form.controls.reason.valueChanges.pipe(startWith(this.form.controls.reason.value)),
  );
  protected readonly noteRequired = computed(() => this.reason() === 'OTHER');

  /** The change as a percentage of the current salary, shown on the confirm step. */
  protected readonly changePercent = signal(0);
  protected readonly changeLabel = computed(() => {
    const percent = this.changePercent();
    return `${percent > 0 ? '+' : ''}${percent.toFixed(1)}%`;
  });

  constructor() {
    // A note is required only when the reason is "Other".
    this.form.controls.reason.valueChanges.pipe(takeUntilDestroyed()).subscribe((reason) => {
      const note = this.form.controls.note;
      note.setValidators(
        reason === 'OTHER' ? [Validators.required, Validators.maxLength(500)] : [Validators.maxLength(500)],
      );
      note.updateValueAndValidity();
    });
  }

  ngOnInit(): void {
    // These rules depend on the employee, which is an input, so they are added once it is set.
    this.form.controls.amount.addValidators(differentFrom(this.employee().currentSalary.amount));
    this.form.controls.effectiveDate.addValidators(dateInput({ min: this.minDate(), max: this.maxDate }));
  }

  /** A field's error shows once the user has left it (or pressed Review), not while they type. */
  protected show(control: AbstractControl): boolean {
    return control.invalid && control.touched;
  }

  protected review(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const current = this.employee().currentSalary.amount;
    this.changePercent.set(((this.form.controls.amount.value! - current) / current) * 100);
    this.error.set(null);
    this.step.set('confirm');
  }

  protected back(): void {
    this.error.set(null);
    this.step.set('edit');
  }

  protected confirm(): void {
    const v = this.form.getRawValue();
    const employee = this.employee();
    this.saving.set(true);
    this.error.set(null);
    this.api
      .changeSalary(employee.id, {
        amount: v.amount!,
        effectiveDate: toIsoDate(v.effectiveDate!),
        reason: v.reason!,
        note: v.note.trim() || null,
        version: employee.version,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.close('saved'),
        error: (error: unknown) => {
          this.saving.set(false);
          if (isStaleVersion(error)) {
            this.close('stale');
          } else if (!isUnexpected(error)) {
            this.error.set(messageOf(error)); // e.g. a 422 rule, shown inside the dialog
          }
        },
      });
  }

  protected amountError(): string {
    const errors = this.form.controls.amount.errors ?? {};
    if (errors['required']) return 'Enter the new annual salary';
    if (errors['min']) return 'Salary must be greater than 0';
    if (errors['max']) return 'Salary is too large';
    if (errors['sameAsCurrent']) return 'The new salary is the same as the current salary';
    return '';
  }

  protected dateError(): string {
    const errors = this.form.controls.effectiveDate.errors ?? {};
    if (errors['required'] || errors['dateParse']) return 'Enter a date as DD/MM/YYYY';
    if (errors['dateMin']) return "Can't be before the current salary's effective date";
    if (errors['dateMax']) return "Can't be in the future";
    return '';
  }

  private close(outcome: DialogOutcome): void {
    this.outcome = outcome;
    this.saving.set(false);
    this.visible.set(false);
  }
}
