import { Component } from '@angular/core';

/** Key for the range-bar column, shown above each pay table. */
@Component({
  selector: 'app-range-legend',
  template: `
    <span class="item"><span class="swatch" style="opacity: 0.28" aria-hidden="true"></span>Bottom quarter</span>
    <span class="item"><span class="swatch" style="opacity: 0.5" aria-hidden="true"></span>2nd quarter</span>
    <span class="item"><span class="swatch" style="opacity: 0.75" aria-hidden="true"></span>3rd quarter</span>
    <span class="item"><span class="swatch" aria-hidden="true"></span>Top quarter</span>
    <span class="item"><span class="mark" aria-hidden="true"></span>Typical (median)</span>
    <span class="muted">Each bar runs from lowest to highest in its own row.</span>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 18px;
      font-size: 13px;
    }
    .item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .swatch {
      display: inline-block;
      width: 16px;
      height: 12px;
      border-radius: 3px;
      background: var(--chart-bar);
    }
    .mark {
      display: inline-block;
      width: 3px;
      height: 16px;
      border-radius: 2px;
      background: var(--range-median);
    }
  `,
})
export class RangeLegend {}
