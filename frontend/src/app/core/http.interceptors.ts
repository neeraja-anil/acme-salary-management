import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { Notify } from './notify';

/** Counts requests in flight so the shell can show a progress bar. */
@Injectable({ providedIn: 'root' })
export class LoadingState {
  private readonly pending = signal(0);
  readonly loading = computed(() => this.pending() > 0);

  start(): void {
    this.pending.update((n) => n + 1);
  }

  stop(): void {
    this.pending.update((n) => Math.max(0, n - 1));
  }
}

export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loading = inject(LoadingState);
  loading.start();
  return next(req).pipe(finalize(() => loading.stop()));
};

/**
 * Network failures and 5xx responses get one generic toast here (ui-design.md section 5); the technical
 * details go to the console only. 4xx errors are left to the page, which knows how to show them.
 */
export const unexpectedErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const notify = inject(Notify);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
        console.error(`${req.method} ${req.url} failed`, error);
        const message =
          error.status === 503 ? 'The system is busy. Please try again.' : 'Something went wrong. Please try again.';
        notify.error(message);
      }
      return throwError(() => error);
    }),
  );
};

/**
 * A 401 from any API call except the sign-in endpoints means the session has gone: it timed out after
 * 30 idle minutes, or a super admin disabled the account (US-19). Go to the sign-in page, which says so
 * and comes back to this page afterwards. A 403 means the user isn't allowed to do that.
 */
export const sessionInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notify = inject(Notify);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !req.url.startsWith('/api/auth/')) {
        if (error.status === 401) {
          auth.sessionEnded();
          if (!router.url.startsWith('/login')) {
            router.navigate(['/login'], { queryParams: { returnUrl: router.url, expired: 1 } });
          }
        } else if (error.status === 403) {
          notify.error("You don't have access to this.");
        }
      }
      return throwError(() => error);
    }),
  );
};
