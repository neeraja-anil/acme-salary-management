import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { Observable, startWith } from 'rxjs';

import { isStaleVersion, isUnexpected, messageOf, problemOf } from '../../core/api-error';
import { dateInput, today, toIsoDate } from '../../core/dates';
import { EmployeesApi } from '../../core/employees.api';
import { EmployeeDetail, JobLevel } from '../../core/models';
import { Notify } from '../../core/notify';
import { ReferenceDataService } from '../../core/reference-data';
import { Confirmations } from '../../shared/confirm-dialog';
import { DescribedBy } from '../../shared/described-by';
import { HasUnsavedChanges } from '../../shared/unsaved-changes.guard';

/**
 * Add employee (US-05) and edit employee (US-06). On edit, the country cannot change (it fixes the salary
 * currency) and there is no salary field: salary only changes through the salary dialog.
 */
@Component({
  selector: 'app-employee-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    DescribedBy,
  ],
  templateUrl: './employee-form.page.html',
  styleUrl: './employee-form.page.scss',
})
export class EmployeeFormPage implements HasUnsavedChanges {
  private readonly api = inject(EmployeesApi);
  private readonly router = inject(Router);
  private readonly notify = inject(Notify);
  private readonly confirmations = inject(Confirmations);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reference = inject(ReferenceDataService);
  protected readonly ref = this.reference.snapshot;
  protected readonly levelOptions = this.ref.jobLevels.map((l) => ({ value: l.code, label: `${l.code} ${l.name}` }));

  /** Route parameter; absent when adding a new employee. */
  readonly id = input<string>();
  protected readonly isEdit = computed(() => this.id() !== undefined);

  protected readonly employee = signal<EmployeeDetail | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly notFound = signal(false);
  protected readonly maxDate = today();
  private saved = false;

  protected readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    countryCode: new FormControl<string | null>(null, Validators.required),
    departmentId: new FormControl<number | null>(null, Validators.required),
    jobTitleId: new FormControl<number | null>(null, Validators.required),
    jobLevel: new FormControl<JobLevel | null>(null, Validators.required),
    hireDate: new FormControl<Date | null>(null, [Validators.required, dateInput({ max: this.maxDate })]),
    startingSalary: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(1),
      Validators.max(100_000_000),
    ]),
  });

  private readonly departmentId = toSignal(
    this.form.controls.departmentId.valueChanges.pipe(startWith(this.form.controls.departmentId.value)),
  );
  private readonly countryCode = toSignal(
    this.form.controls.countryCode.valueChanges.pipe(startWith(this.form.controls.countryCode.value)),
  );

  /** Job titles belong to a department, so the options follow the chosen department. */
  protected readonly jobTitleOptions = computed(() => {
    const departmentId = this.departmentId();
    return departmentId == null ? [] : this.reference.jobTitlesFor([departmentId]);
  });

  protected readonly currency = computed(() => {
    const code = this.countryCode();
    return code ? (this.reference.country(code)?.currency ?? '') : '';
  });

  constructor() {
    // A selected title that does not belong to the new department is cleared.
    this.form.controls.departmentId.valueChanges.pipe(takeUntilDestroyed()).subscribe((departmentId) => {
      const titleId = this.form.controls.jobTitleId.value;
      const title = this.ref.jobTitles.find((t) => t.id === titleId);
      if (title && title.departmentId !== departmentId) {
        this.form.controls.jobTitleId.setValue(null);
      }
    });
  }

  ngOnInit(): void {
    const id = this.id();
    if (id !== undefined) {
      this.form.controls.countryCode.disable();
      this.form.controls.hireDate.disable();
      this.form.controls.startingSalary.disable();
      this.load(Number(id));
    }
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saved;
  }

  protected save(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const employee = this.employee();
    const request$: Observable<EmployeeDetail> = employee
      ? this.api.update(employee.id, {
          firstName: v.firstName,
          lastName: v.lastName,
          email: v.email,
          departmentId: v.departmentId!,
          jobTitleId: v.jobTitleId!,
          jobLevel: v.jobLevel!,
          version: employee.version,
        })
      : this.api.create({
          firstName: v.firstName,
          lastName: v.lastName,
          email: v.email,
          countryCode: v.countryCode!,
          departmentId: v.departmentId!,
          jobTitleId: v.jobTitleId!,
          jobLevel: v.jobLevel!,
          hireDate: toIsoDate(v.hireDate!),
          startingSalary: v.startingSalary!,
        });

    this.saving.set(true);
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.saved = true;
        this.notify.success(employee ? 'Employee updated' : 'Employee created');
        this.router.navigate(['/employees', saved.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.showError(error);
      },
    });
  }

  protected cancelLink(): unknown[] {
    const id = this.id();
    return id === undefined ? ['/employees'] : ['/employees', id];
  }

  /** A field's error shows once the user has left it (or tried to save), not while they type. */
  protected show(control: AbstractControl): boolean {
    return control.invalid && control.touched;
  }

  /** The id of the field's error message while it is shown, for aria-describedby. */
  protected errorId(control: AbstractControl, field: string): string | null {
    return this.show(control) ? `${field}-error` : null;
  }

  /** The first message for a field, from client-side validators or the server. */
  protected errorFor(control: AbstractControl, label: string): string | null {
    const errors = control.errors;
    if (!errors) return null;
    if (errors['server']) return errors['server'];
    if (errors['required']) return `${label} is required`;
    if (errors['email']) return 'Enter a valid email address';
    if (errors['maxlength']) return `${label} must be at most ${errors['maxlength'].requiredLength} characters`;
    if (errors['min']) return `${label} must be greater than 0`;
    if (errors['max']) return `${label} is too large`;
    if (errors['dateParse']) return 'Enter a date as DD/MM/YYYY';
    if (errors['dateMax']) return `${label} cannot be in the future`;
    return `${label} is invalid`;
  }

  private load(id: number): void {
    this.api
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (employee) => {
          this.employee.set(employee);
          this.form.reset({
            firstName: employee.firstName,
            lastName: employee.lastName,
            email: employee.email,
            countryCode: employee.country.code,
            departmentId: employee.department.id,
            jobTitleId: employee.jobTitle.id,
            jobLevel: employee.jobLevel,
            hireDate: null,
            startingSalary: null,
          });
          if (employee.status === 'TERMINATED') {
            this.form.disable();
          }
        },
        error: (error: unknown) => {
          if (problemOf(error)?.code === 'NOT_FOUND') this.notFound.set(true);
        },
      });
  }

  private showError(error: unknown): void {
    if (isUnexpected(error)) return; // already shown as a toast
    if (isStaleVersion(error)) {
      this.confirmations.staleVersion().subscribe(() => this.load(this.employee()!.id));
      return;
    }
    const problem = problemOf(error);
    if (problem?.code === 'DUPLICATE_EMAIL') {
      this.setServerError('email', 'is already used by another employee');
      return;
    }
    if (problem?.errors?.length) {
      const unmatched = problem.errors.filter((e) => !this.setServerError(e.field, e.message));
      if (unmatched.length) this.formError.set(unmatched.map((e) => `${e.field} ${e.message}`).join('; '));
      return;
    }
    this.formError.set(messageOf(error));
  }

  /** Puts a server message under the matching field. Returns false if there is no such field. */
  private setServerError(field: string, message: string): boolean {
    const control = this.form.get(field);
    if (!control) return false;
    control.setErrors({ ...control.errors, server: `${this.labelFor(field)} ${message}` });
    control.markAsTouched();
    return true;
  }

  private labelFor(field: string): string {
    const labels: Record<string, string> = {
      firstName: 'First name',
      lastName: 'Last name',
      email: 'Email',
      countryCode: 'Country',
      departmentId: 'Department',
      jobTitleId: 'Job title',
      jobLevel: 'Level',
      hireDate: 'Hire date',
      startingSalary: 'Salary',
    };
    return labels[field] ?? field;
  }
}
