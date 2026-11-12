import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { isUnexpected, messageOf } from '../../core/api-error';
import { User } from '../../core/models';
import { UsersApi } from '../../core/users.api';
import { DescribedBy } from '../../shared/described-by';

/**
 * Give an HR manager a new password, e.g. when they have forgotten theirs (US-22).
 * Render it with `@if` to open it; it emits `closed` with true when the password was changed.
 */
@Component({
  selector: 'app-reset-password-dialog',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, InputTextModule, MessageModule, DescribedBy],
  template: `
    <p-dialog [header]="'Reset password for ' + user().displayName" [visible]="visible()"
              (visibleChange)="visible.set($event)" (onHide)="closed.emit(changed)" [modal]="true"
              [draggable]="false" [closable]="!saving()" [style]="{ width: '440px', maxWidth: 'calc(100vw - 32px)' }">
      <form id="reset-password-form" (ngSubmit)="save()" class="dialog-form" novalidate>
        @if (error()) {
          <p-message severity="error" icon="pi pi-times-circle">{{ error() }}</p-message>
        }
        <div class="field">
          <label for="reset-password">New password</label>
          <input pInputText id="reset-password" type="password" [formControl]="password" autocomplete="new-password"
                 [fluid]="true" [invalid]="showError()" autofocus
                 [appDescribedBy]="showError() ? 'reset-password-error' : 'reset-password-hint'" />
          @if (showError()) {
            <small class="error" id="reset-password-error"><i class="pi pi-exclamation-circle" aria-hidden="true"></i>Use 10-72 characters</small>
          } @else {
            <small class="hint" id="reset-password-hint">At least 10 characters. Share it with them privately</small>
          }
        </div>
      </form>
      <ng-template #footer>
        <p-button label="Cancel" severity="secondary" [text]="true" (onClick)="visible.set(false)" [disabled]="saving()" />
        <button pButton type="submit" form="reset-password-form" [loading]="saving()">
          <i class="pi pi-key" aria-hidden="true"></i> Reset password
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class ResetPasswordDialog {
  private readonly api = inject(UsersApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly user = input.required<User>();
  readonly closed = output<boolean>();
  protected readonly visible = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected changed = false;

  protected readonly password = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(10), Validators.maxLength(72)],
  });

  protected showError(): boolean {
    return this.password.invalid && this.password.touched;
  }

  protected save(): void {
    if (this.password.invalid) {
      this.password.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .resetPassword(this.user().id, this.password.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.changed = true;
          this.visible.set(false);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          if (!isUnexpected(error)) this.error.set(messageOf(error));
        },
      });
  }
}
