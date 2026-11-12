import { Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';

import { EmployeeStatus } from '../core/models';

/** Status as a text label, so it never depends on colour alone (ui-design.md section 6). */
@Component({
  selector: 'app-status-chip',
  imports: [TagModule],
  template: `<p-tag [value]="label()" [severity]="severity()" [rounded]="true" />`,
})
export class StatusChip {
  readonly status = input.required<EmployeeStatus>();

  protected readonly label = computed(() => (this.status() === 'ACTIVE' ? 'Active' : 'Terminated'));
  protected readonly severity = computed(() => (this.status() === 'ACTIVE' ? 'success' : 'secondary'));
}
