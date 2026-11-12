import { DatePipe } from '@angular/common';
import { Component, inject, output, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TableModule } from 'primeng/table';

import { ReferenceDataService } from '../../core/reference-data';

/**
 * The fixed reporting rates used for every USD figure (requirements.md: no live FX).
 * Render it with `@if` to open it; it emits `closed` once it has closed.
 */
@Component({
  selector: 'app-fx-rates-dialog',
  imports: [DialogModule, ButtonModule, TableModule, DatePipe],
  template: `
    <p-dialog header="Exchange rates" [visible]="visible()" (visibleChange)="visible.set($event)"
              (onHide)="closed.emit()" [modal]="true" [draggable]="false"
              [style]="{ width: '400px', maxWidth: 'calc(100vw - 32px)' }">
      <p-table [value]="rates" size="small">
        <ng-template #header>
          <tr><th>Currency</th><th class="num">1 unit in USD</th></tr>
        </ng-template>
        <ng-template #body let-rate>
          <tr><td>{{ rate.currency }}</td><td class="num">{{ rate.rateToUsd }}</td></tr>
        </ng-template>
      </p-table>
      @if (rates.length) {
        <p class="caption note">Fixed rates as of {{ rates[0].asOf | date: 'd MMM y' }}, used for reporting only.</p>
      }
      <ng-template #footer>
        <p-button label="Close" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .note {
      margin-top: 12px;
    }
  `,
})
export class FxRatesDialog {
  readonly closed = output<void>();
  protected readonly visible = signal(true);
  protected readonly rates = inject(ReferenceDataService).snapshot.fxRates;
}
