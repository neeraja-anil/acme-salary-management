import { TestBed } from '@angular/core/testing';

import { PayStatistics } from '../core/models';
import { RangeBar } from './range-bar';

function render(stats: Partial<PayStatistics>, currency = 'USD', labels = true) {
  TestBed.configureTestingModule({ imports: [RangeBar] });
  const fixture = TestBed.createComponent(RangeBar);
  fixture.componentRef.setInput('stats', { headcount: 5, average: 0, total: 0, ...stats });
  fixture.componentRef.setInput('currency', currency);
  fixture.componentRef.setInput('labels', labels);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

/** The range bar in the pay tables: four quarters of the people, with a mark at the typical salary. */
describe('RangeBar', () => {
  it('describes all five figures for screen readers and the hover text', () => {
    const element = render({ min: 40000, p25: 50000, median: 60000, p75: 70000, max: 100000 });

    expect(element.querySelector('[role="img"]')!.getAttribute('aria-label')).toBe(
      'Lowest $40,000 · 25th pct $50,000 · typical $60,000 · 75th pct $70,000 · highest $100,000');
    expect(element.querySelectorAll('.segment')).toHaveLength(4);
  });

  it('places the typical-salary mark in proportion within the row', () => {
    const element = render({ min: 0, p25: 25, median: 50, p75: 75, max: 200 });
    expect((element.querySelector('.median') as HTMLElement).style.left).toBe('25%');
  });

  it('shows one shade and one figure when everyone earns the same', () => {
    const element = render({ min: 50000, p25: 50000, median: 50000, p75: 50000, max: 50000, headcount: 1 });

    expect(element.querySelectorAll('.segment')).toHaveLength(1);
    expect(element.querySelectorAll('.label')).toHaveLength(1);
    expect(element.querySelector('[role="img"]')!.getAttribute('aria-label')).toBe('Everyone in this group earns $50,000');
  });

  it('drops labels that would overlap instead of printing them on top of each other', () => {
    // p25 and p75 sit right next to the median, so only lowest, typical and highest have room.
    const element = render({ min: 0, p25: 48, median: 50, p75: 52, max: 100 });
    expect(Array.from(element.querySelectorAll('.label')).map((l) => l.textContent?.trim())).toEqual(['$50', '$0', '$100']);
  });
});
