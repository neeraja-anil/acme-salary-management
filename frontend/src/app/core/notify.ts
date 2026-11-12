import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';

/**
 * Toasts (ui-design.md section 5). Successes disappear on their own; failures stay until dismissed,
 * because a critical message should never time out.
 */
@Injectable({ providedIn: 'root' })
export class Notify {
  private readonly messages = inject(MessageService);

  success(summary: string): void {
    this.messages.add({ severity: 'success', summary, life: 3000 });
  }

  error(summary: string, detail?: string): void {
    this.messages.add({ severity: 'error', summary, detail, sticky: true });
  }
}
