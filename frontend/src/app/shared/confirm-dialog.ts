import { Injectable, inject } from '@angular/core';
import { ConfirmationService } from 'primeng/api';
import { Observable } from 'rxjs';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  /** Omit for a single-button notice. */
  cancelLabel?: string;
  /** A destructive action: the confirm button is red and a warning icon is shown. */
  warn?: boolean;
}

/**
 * Small yes/no (or OK-only) questions, shown by the one `<p-confirmdialog>` in the shell.
 * Each call emits once (true when confirmed) and completes.
 */
@Injectable({ providedIn: 'root' })
export class Confirmations {
  private readonly confirmation = inject(ConfirmationService);

  ask(data: ConfirmDialogData): Observable<boolean> {
    return new Observable<boolean>((subscriber) => {
      const answer = (confirmed: boolean) => {
        subscriber.next(confirmed);
        subscriber.complete();
      };
      this.confirmation.confirm({
        header: data.title,
        message: data.message,
        icon: data.warn ? 'pi pi-exclamation-triangle' : undefined,
        acceptLabel: data.confirmLabel,
        rejectLabel: data.cancelLabel,
        rejectVisible: data.cancelLabel !== undefined,
        acceptButtonProps: { severity: data.warn ? 'danger' : undefined },
        rejectButtonProps: { severity: 'secondary', variant: 'text' },
        // Escape and the close button count as "no".
        closable: data.cancelLabel !== undefined,
        closeOnEscape: data.cancelLabel !== undefined,
        defaultFocus: 'accept',
        accept: () => answer(true),
        reject: () => answer(false),
      });
    });
  }

  /** 409 STALE_VERSION (ui-design.md section 5). Emits once the user chooses to reload. */
  staleVersion(): Observable<boolean> {
    return this.ask({
      title: 'Changed by someone else',
      message: 'This record was changed by someone else. Reload to see the latest version.',
      confirmLabel: 'Reload',
    });
  }
}
