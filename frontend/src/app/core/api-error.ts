import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail } from './models';

/** The server's RFC 7807 body, or null for network errors and non-JSON responses. */
export function problemOf(error: unknown): ProblemDetail | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object' && 'code' in error.error) {
    return error.error as ProblemDetail;
  }
  return null;
}

export function isStaleVersion(error: unknown): boolean {
  return problemOf(error)?.code === 'STALE_VERSION';
}

/** Errors the global interceptor already reports with a toast; forms and dialogs should not repeat them. */
export function isUnexpected(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}

/** A message for a 4xx error to show inside a form or dialog. */
export function messageOf(error: unknown): string {
  const problem = problemOf(error);
  if (!problem) {
    return 'Something went wrong. Please try again.';
  }
  if (problem.errors?.length) {
    return problem.errors.map((e) => `${e.field} ${e.message}`).join('; ');
  }
  return problem.detail ?? problem.title;
}
