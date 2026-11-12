import { CanDeactivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Observable, of } from 'rxjs';

import { Confirmations } from './confirm-dialog';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/** Asks "Discard changes?" before leaving a form with unsaved edits (ui-design.md section 4.7). */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component): Observable<boolean> => {
  if (!component.hasUnsavedChanges()) {
    return of(true);
  }
  return inject(Confirmations).ask({
    title: 'Discard changes?',
    message: 'You have unsaved changes on this form. If you leave now they will be lost.',
    confirmLabel: 'Discard',
    cancelLabel: 'Keep editing',
    warn: true,
  });
};
