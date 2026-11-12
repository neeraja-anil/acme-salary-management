import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { problemOf } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';

/**
 * Sign in (ui-design.md section 4.1, US-18). After signing in, the user goes back to the page they were on
 * (`?returnUrl=`) or to the dashboard. `?expired=1` means the session ran out and says so.
 */
@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, MessageModule],
  template: `
    <main class="page">
      <div class="panel">
        <div class="brand">
          <span class="logo" aria-hidden="true"><i class="pi pi-wallet"></i></span>
          <div>
            <h1>ACME Salary management</h1>
            <p class="caption">Sign in to continue</p>
          </div>
        </div>

        @if (expired() && !error()) {
          <p-message severity="warn" icon="pi pi-clock">Your session expired. Please sign in again.</p-message>
        }
        @if (error()) {
          <p-message severity="error" icon="pi pi-times-circle">{{ error() }}</p-message>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" class="dialog-form" novalidate>
          <div class="field">
            <label for="username">Username</label>
            <input pInputText id="username" formControlName="username" autocomplete="username" [fluid]="true"
                   autofocus />
          </div>
          <div class="field">
            <label for="password">Password</label>
            <input pInputText id="password" type="password" formControlName="password"
                   autocomplete="current-password" [fluid]="true" />
          </div>
          <!-- Never disabled: a disabled submit button also blocks pressing Enter in the fields. -->
          <p-button type="submit" label="Sign in" icon="pi pi-sign-in" [loading]="signingIn()" [fluid]="true" />
        </form>
      </div>
    </main>
  `,
  styles: `
    .page {
      display: grid;
      place-items: center;
      min-height: 100vh;
      padding: 16px;
      box-sizing: border-box;
    }
    .panel {
      display: flex;
      flex-direction: column;
      gap: 20px;
      width: 100%;
      max-width: 400px;
      padding: 32px;
      box-sizing: border-box;
      border-radius: 12px;
      background: var(--p-content-background);
      border: 1px solid var(--p-content-border-color);
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .logo {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: var(--p-primary-color);
      color: var(--p-primary-contrast-color);
      font-size: 20px;
    }
    h1 {
      font-size: 18px;
      font-weight: 600;
    }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Query parameters, bound as inputs. */
  readonly returnUrl = input<string>();
  readonly expiredParam = input<string>(undefined, { alias: 'expired' });

  protected readonly expired = computed(() => this.expiredParam() === '1');
  protected readonly signingIn = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: Validators.required }),
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.signingIn.set(true);
    this.error.set(null);
    this.auth
      .login(username, password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.router.navigateByUrl(this.safeReturnUrl()),
        error: (error: unknown) => {
          this.signingIn.set(false);
          this.form.controls.password.reset();
          // The server's message never says whether the username or the password was wrong.
          this.error.set(problemOf(error)?.status === 401 ? 'Invalid credentials' : 'Could not sign in. Please try again.');
        },
      });
  }

  /** Only paths inside this app: "//evil.example" or a full URL would send the user to another site. */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : '/dashboard';
  }
}
