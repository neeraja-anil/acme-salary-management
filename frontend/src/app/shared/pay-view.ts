import { Component, Injectable, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';

export type PayView = 'numbers' | 'bars';

/**
 * Whether the pay tables show figures in columns or as range bars. One choice for all three tables,
 * kept while the app is open, so switching tabs keeps the same view.
 */
@Injectable({ providedIn: 'root' })
export class PayViewState {
  readonly view = signal<PayView>('numbers');
}

let nextId = 0;

/** The "Numbers | Range bars" switch shown above each pay table. */
@Component({
  selector: 'app-pay-view-toggle',
  imports: [FormsModule, SelectButtonModule],
  template: `
    <span class="label" [id]="labelId">Show pay as</span>
    <p-selectbutton
      [options]="options"
      optionLabel="label"
      optionValue="value"
      [allowEmpty]="false"
      [ngModel]="state.view()"
      (ngModelChange)="state.view.set($event)"
      [ariaLabelledBy]="labelId"
      size="small"
    />
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }
    .label {
      font-size: 13px;
      color: var(--p-text-muted-color);
    }
  `,
})
export class PayViewToggle {
  protected readonly state = inject(PayViewState);
  /** Each tab has its own switch, so each label needs its own id. */
  protected readonly labelId = `pay-view-label-${nextId++}`;
  protected readonly options: { label: string; value: PayView }[] = [
    { label: 'Numbers', value: 'numbers' },
    { label: 'Range bars', value: 'bars' },
  ];
}
