import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, finalize, throwError } from 'rxjs';

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
