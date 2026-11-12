import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';

import { isStaleVersion, isUnexpected, messageOf } from '../../core/api-error';
import { dateInput, fromIsoDate, today, toIsoDate } from '../../core/dates';
import { EmployeesApi } from '../../core/employees.api';
import { EmployeeDetail } from '../../core/models';
import { DescribedBy } from '../../shared/described-by';
import { DialogOutcome } from './change-salary.dialog';

/**
 * Deactivate an employee (US-07). Records are kept; the employee leaves analytics.
 * Render it with `@if` to open it; it emits `closed` with the outcome once it has closed.
 */
@Component({
  selector: 'app-deactivate-dialog',
  imports: [ReactiveFormsModule, ButtonModule, DatePickerModule, DialogModule, MessageModule, DescribedBy],
  template: `
    <p-dialog [header]="'Deactivate ' + employee().firstName + ' ' + employee().lastName + '?'"
              [visible]="visible()" (visibleChange)="visible.set($event)" (onHide)="closed.emit(outcome)"
              [modal]="true" [draggable]="false" [closable]="!saving()" [closeOnEscape]="!saving()"
              [style]="{ width: '480px', maxWidth: 'calc(100vw - 32px)' }">
      <div class="dialog-form">
        @if (error()) {
          <p-message severity="error" icon="pi pi-times-circle">{{ error() }}</p-message>
        }
        <p class="intro">
          The employee will be marked as Terminated and excluded from analytics. Their records and salary history
          are kept.
        </p>
        <div class="field">
          <label for="terminationDate">Termination date</label>
          <p-datepicker inputId="terminationDate" [formControl]="terminationDate" [minDate]="minDate()"
                        [maxDate]="maxDate" [keepInvalid]="true" placeholder="DD/MM/YYYY" [showIcon]="true"
                        iconDisplay="input" appendTo="body" [fluid]="true" [invalid]="showError()"
                        [appDescribedBy]="showError() ? 'termination-error' : null" />
          @if (showError()) {
            <small class="error" id="termination-error">
              <i class="pi pi-exclamation-circle" aria-hidden="true"></i>{{ dateError() }}
            </small>
          }
        </div>
      </div>
      <ng-template #footer>
        <p-button label="Cancel" severity="secondary" [text]="true" (onClick)="visible.set(false)" [disabled]="saving()" />
        <p-button label="Deactivate" icon="pi pi-user-minus" severity="danger" (onClick)="confirm()"
                  [loading]="saving()" [disabled]="terminationDate.invalid" />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .intro {
      margin: 0;
    }
  `,
})
export class DeactivateDialog implements OnInit {
  private readonly api = inject(EmployeesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly employee = input.required<EmployeeDetail>();
  readonly closed = output<DialogOutcome | undefined>();

  protected readonly minDate = () => fromIsoDate(this.employee().hireDate);
  protected readonly maxDate = today();
  protected readonly terminationDate = new FormControl<Date | null>(today(), Validators.required);
  protected readonly visible = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Reported through `closed` once the dialog has finished closing. */
  protected outcome: DialogOutcome | undefined;

  ngOnInit(): void {
    this.terminationDate.addValidators(dateInput({ min: this.minDate(), max: this.maxDate }));
    this.terminationDate.updateValueAndValidity();
  }

  protected showError(): boolean {
    return this.terminationDate.invalid && this.terminationDate.touched;
  }

  protected confirm(): void {
    this.saving.set(true);
    this.error.set(null);
    const employee = this.employee();
    this.api
      .deactivate(employee.id, toIsoDate(this.terminationDate.value!), employee.version)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.close('saved'),
        error: (error: unknown) => {
          this.saving.set(false);
          if (isStaleVersion(error)) {
            this.close('stale');
          } else if (!isUnexpected(error)) {
            this.error.set(messageOf(error));
          }
        },
      });
  }

  protected dateError(): string {
    const errors = this.terminationDate.errors ?? {};
    if (errors['required'] || errors['dateParse']) return 'Enter a date as DD/MM/YYYY';
    if (errors['dateMin']) return "Can't be before the hire date";
    if (errors['dateMax']) return "Can't be in the future";
    return '';
  }

  private close(outcome: DialogOutcome): void {
    this.outcome = outcome;
    this.saving.set(false);
    this.visible.set(false);
  }
}
