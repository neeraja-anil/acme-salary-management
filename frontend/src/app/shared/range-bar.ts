import { Component, computed, input } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';

import { PayStatistics } from '../core/models';
import { formatMoney } from './money.pipe';

/** Lightest to darkest: bottom quarter, second, third, top quarter. */
const QUARTER_OPACITY = [0.28, 0.5, 0.75, 1];

/** Two labels closer than this (as % of the bar width) would overlap, so the less important one is hidden. */
const MIN_LABEL_GAP = 16;

interface BarLabel {
  text: string;
  left: number;
  /** Labels near the ends are aligned to the edge instead of centred, so they stay inside the bar. */
  shift: string;
  strong: boolean;
}

/**
 * One row's pay as a bar split into quarters of its people: lowest → 25th pct → median → 75th pct → highest,
 * each quarter a darker shade, with a dark mark at the typical salary (median). Design 2 ("Quartile
 * segments") from the design canvas.
 *
 * Each bar is scaled to its own row (lowest at the left edge, highest at the right), so it shows how that
 * group's pay is spread. With `labels`, the figures are printed under the bar: lowest, typical and highest
 * always, the 25th and 75th percentiles when there is room. All five are always in the tooltip.
 */
@Component({
  selector: 'app-range-bar',
  imports: [TooltipModule],
  template: `
    <div class="bar" [class.with-labels]="labels()" role="img" [attr.aria-label]="summary()" [pTooltip]="summary()" tooltipPosition="top">
      <div class="segments">
        @for (segment of segments(); track $index) {
          <div class="segment" [style.width.%]="segment.width" [style.opacity]="segment.opacity"></div>
        }
      </div>
      <div class="median" [style.left.%]="pos(stats().median)"></div>
      @if (labels()) {
        @for (label of visibleLabels(); track label.text + label.left) {
          <span class="label" [class.strong]="label.strong" [style.left.%]="label.left" [style.transform]="label.shift">
            {{ label.text }}
          </span>
        }
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
      min-width: 160px;
    }
    .bar {
      position: relative;
      height: 28px;
    }
    .bar.with-labels {
      height: 48px;
    }
    .segments {
      position: absolute;
      top: 4px;
      left: 0;
      right: 0;
      height: 20px;
      border-radius: 6px;
      overflow: hidden;
      display: flex;
    }
    .segment {
      height: 100%;
      background: var(--chart-bar);
      box-shadow: inset -2px 0 0 var(--p-content-background); /* white gap between quarters */
    }
    .median {
      position: absolute;
      top: 0;
      width: 3px;
      height: 28px;
      margin-left: -1.5px;
      border-radius: 2px;
      background: var(--range-median);
    }
    .label {
      position: absolute;
      top: 30px;
      font-size: 12px;
      line-height: 16px;
      white-space: nowrap;
      color: var(--p-text-muted-color);
      font-variant-numeric: tabular-nums;
    }
    .label.strong {
      color: var(--range-median);
      font-weight: 600;
    }
  `,
})
export class RangeBar {
  readonly stats = input.required<PayStatistics>();
  readonly currency = input.required<string>();
  /** Print the figures under the bar (used when the bar replaces the number columns). */
  readonly labels = input(false);

  /** Everyone in the group earns the same (often a group of one): one full bar with the mark in the middle. */
  private readonly flat = computed(() => this.stats().max === this.stats().min);

  protected readonly segments = computed(() => {
    const s = this.stats();
    if (this.flat()) {
      return [{ width: 100, opacity: QUARTER_OPACITY[1] }];
    }
    const stops = [s.min, s.p25, s.median, s.p75, s.max];
    return [0, 1, 2, 3].map((i) => ({
      width: this.pos(stops[i + 1]) - this.pos(stops[i]),
      opacity: QUARTER_OPACITY[i],
    }));
  });

  /** Placed in order of importance; a label that would overlap one already placed is skipped. */
  protected readonly visibleLabels = computed<BarLabel[]>(() => {
    const s = this.stats();
    const short = (amount: number) => formatMoney(amount, this.currency(), true);
    if (this.flat()) {
      return [this.label(short(s.median), 50, true)];
    }
    const byImportance: [number, boolean][] = [
      [s.median, true],
      [s.min, false],
      [s.max, false],
      [s.p25, false],
      [s.p75, false],
    ];
    const placed: BarLabel[] = [];
    for (const [amount, strong] of byImportance) {
      const left = this.pos(amount);
      if (placed.every((p) => Math.abs(p.left - left) >= MIN_LABEL_GAP)) {
        placed.push(this.label(short(amount), left, strong));
      }
    }
    return placed;
  });

  protected readonly summary = computed(() => {
    const s = this.stats();
    const f = (amount: number) => formatMoney(amount, this.currency());
    return this.flat()
      ? `Everyone in this group earns ${f(s.median)}`
      : `Lowest ${f(s.min)} · 25th pct ${f(s.p25)} · typical ${f(s.median)} · 75th pct ${f(s.p75)} · highest ${f(s.max)}`;
  });

  /** Position of an amount within this row's own range, as a percentage of the bar width. */
  protected pos(amount: number): number {
    const s = this.stats();
    return this.flat() ? 50 : ((amount - s.min) / (s.max - s.min)) * 100;
  }

  private label(text: string, left: number, strong: boolean): BarLabel {
    const shift = left < 8 ? 'translateX(0)' : left > 92 ? 'translateX(-100%)' : 'translateX(-50%)';
    return { text, left, shift, strong };
  }
}
