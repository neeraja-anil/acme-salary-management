import { Directive, ElementRef, afterRenderEffect, inject, input } from '@angular/core';

/**
 * Links a form control to its hint or error text (`aria-describedby`), so screen readers read the message
 * with the field (WCAG 3.3.1). PrimeNG's Select and DatePicker render their focusable element inside the
 * host and have no input for this, so the directive finds it: `<p-select [appDescribedBy]="errorId">`.
 * On a native input or textarea it sets the attribute directly.
 */
@Directive({ selector: '[appDescribedBy]' })
export class DescribedBy {
  readonly appDescribedBy = input<string | null | undefined>();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    afterRenderEffect(() => {
      const ids = this.appDescribedBy();
      const host = this.host.nativeElement;
      const target = host.matches('input, textarea')
        ? host
        : host.querySelector<HTMLElement>('[role="combobox"], input:not([type="hidden"]), textarea');
      if (!target) return;
      if (ids) {
        target.setAttribute('aria-describedby', ids);
      } else {
        target.removeAttribute('aria-describedby');
      }
    });
  }
}
