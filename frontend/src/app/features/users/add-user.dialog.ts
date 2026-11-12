import { Component, DestroyRef, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { isUnexpected, messageOf, problemOf } from '../../core/api-error';
import { User } from '../../core/models';
import { UsersApi } from '../../core/users.api';
import { DescribedBy } from '../../shared/described-by';

/**
 * Add an HR manager (US-22). The super admin chooses a temporary password and shares it privately.
 * Render it with `@if` to open it; it emits `closed` with the new user, or undefined if cancelled.
 */
@Component({
  selector: 'app-add-user-dialog',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, InputTextModule, MessageModule, DescribedBy],
  template: `
    <p-dialog header="Add HR user" [visible]="visible()" (visibleChange)="visible.set($event)"
              (onHide)="closed.emit(created)" [modal]="true" [draggable]="false" [closable]="!saving()"
              [style]="{ width: '480px', maxWidth: 'calc(100vw - 32px)' }">
      <form [formGroup]="form" id="add-user-form" (ngSubmit)="save()" class="dialog-form" novalidate>
        @if (error()) {
          <p-message severity="error" icon="pi pi-times-circle">{{ error() }}</p-message>
        }
        <div class="field">
          <label for="new-username">Username</label>
          <input pInputText id="new-username" formControlName="username" autocomplete="off" [fluid]="true"
                 [invalid]="show(form.controls.username)" autofocus
                 [appDescribedBy]="show(form.controls.username) ? 'new-username-error' : 'new-username-hint'" />
          @if (show(form.controls.username)) {
            <small class="error" id="new-username-error"><i class="pi pi-exclamation-circle" aria-hidden="true"></i>{{ usernameError() }}</small>
          } @else {
            <small class="hint" id="new-username-hint">Used to sign in, case-sensitive: letters, digits, dots, dashes or underscores</small>
          }
        </div>
        <div class="field">
          <label for="new-display-name">Name</label>
          <input pInputText id="new-display-name" formControlName="displayName" autocomplete="off" [fluid]="true"
                 [invalid]="show(form.controls.displayName)"
                 [appDescribedBy]="show(form.controls.displayName) ? 'new-display-name-error' : null" />
          @if (show(form.controls.displayName)) {
            <small class="error" id="new-display-name-error"><i class="pi pi-exclamation-circle" aria-hidden="true"></i>Enter their name</small>
          }
        </div>
        <div class="field">
          <label for="new-password">Temporary password</label>
          <input pInputText id="new-password" type="password" formControlName="password" autocomplete="new-password"
                 [fluid]="true" [invalid]="show(form.controls.password)"
                 [appDescribedBy]="show(form.controls.password) ? 'new-password-error' : 'new-password-hint'" />
          @if (show(form.controls.password)) {
            <small class="error" id="new-password-error"><i class="pi pi-exclamation-circle" aria-hidden="true"></i>{{ passwordError() }}</small>
          } @else {
            <small class="hint" id="new-password-hint">At least 10 characters. Share it with them privately</small>
          }
        </div>
      </form>
      <ng-template #footer>
        <p-button label="Cancel" severity="secondary" [text]="true" (onClick)="visible.set(false)" [disabled]="saving()" />
        <button pButton type="submit" form="add-user-form" [loading]="saving()">
          <i class="pi pi-user-plus" aria-hidden="true"></i> Add user
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class AddUserDialog {
  private readonly api = inject(UsersApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly closed = output<User | undefined>();
  protected readonly visible = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Reported through `closed` once the dialog has finished closing. */
  protected created: User | undefined;

  protected readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{3,50}$/)],
    }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10), Validators.maxLength(72)],
    }),
  });

  protected show(control: AbstractControl): boolean {
    return control.invalid && control.touched;
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .create(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.created = user;
          this.visible.set(false);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          if (isUnexpected(error)) return; // already shown as a toast
          if (problemOf(error)?.code === 'DUPLICATE_USERNAME') {
            this.form.controls.username.setErrors({ taken: true });
            this.form.controls.username.markAsTouched();
          } else {
            this.error.set(messageOf(error));
          }
        },
      });
  }

  protected usernameError(): string {
    const errors = this.form.controls.username.errors ?? {};
    if (errors['required']) return 'Enter a username';
    if (errors['taken']) return 'That username is already taken (usernames that differ only in case count as the same)';
    return 'Use 3-50 letters, digits, dots, dashes or underscores';
  }

  protected passwordError(): string {
    const errors = this.form.controls.password.errors ?? {};
    if (errors['required']) return 'Enter a temporary password';
    if (errors['maxlength']) return 'Use at most 72 characters';
    return 'Use at least 10 characters';
  }
}
