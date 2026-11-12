import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CountryPayRow, PayStatistics } from '../../core/models';
import { pageTestProviders } from '../../../testing/test-providers';
import { PayViewState } from '../../shared/pay-view';
import { ByCountryTab } from './by-country-tab';

function stats(median: number, headcount: number): PayStatistics {
  return { headcount, min: median * 0.6, p25: median * 0.8, median, p75: median * 1.2, max: median * 1.6, average: median, total: median * headcount };
}

/** India pays the largest number (in rupees) but the smallest amount in USD: the case that makes sorting tricky. */
const ROWS: CountryPayRow[] = [
  { country: { code: 'DE', name: 'Germany' }, currency: 'EUR', stats: stats(64000, 30), statsUsd: stats(75000, 30) },
  { country: { code: 'IN', name: 'India' }, currency: 'INR', stats: stats(1490000, 50), statsUsd: stats(16800, 50) },
  { country: { code: 'US', name: 'United States' }, currency: 'USD', stats: stats(101000, 20), statsUsd: stats(101000, 20) },
];

/** Pay by country (US-12), and which of its columns can be sorted. */
describe('ByCountryTab (pay insights)', () => {
  let fixture: ComponentFixture<ByCountryTab>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [ByCountryTab], providers: pageTestProviders() });
    fixture = TestBed.createComponent(ByCountryTab);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/analytics/by-country').flush(ROWS);
    await render();
  });

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const element = () => fixture.nativeElement as HTMLElement;
  const countryOrder = () =>
    Array.from(element().querySelectorAll('tbody tr td:first-child')).map((td) => td.textContent?.trim());
  const sortableHeaders = () =>
    Array.from(element().querySelectorAll('th.p-datatable-sortable-column')).map((th) => th.textContent?.trim());

  it('shows each country with its local typical salary and the USD one', () => {
    expect(countryOrder()).toEqual(['Germany', 'India', 'United States']);
    expect(element().textContent).toContain('₹14,90,000'); // Indian digit grouping
    expect(element().textContent).toContain('$16,800');
  });

  it('only lets People and Typical (USD) be sorted, because the other figures mix currencies', () => {
    expect(sortableHeaders()).toEqual(['People', 'Typical (USD)']);
  });

  it('puts the highest USD salary first on the first click', async () => {
    const usdHeader = Array.from(element().querySelectorAll('th')).find((th) => th.textContent?.includes('Typical (USD)'))!;
    usdHeader.click();
    await render();

    expect(countryOrder()).toEqual(['United States', 'Germany', 'India']);
  });

  it('replaces the four range columns with one range bar in the bar view', async () => {
    TestBed.inject(PayViewState).view.set('bars');
    await render();

    const headers = Array.from(element().querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toContain('Range');
    expect(headers).not.toContain('Lowest');
    expect(element().querySelectorAll('app-range-bar')).toHaveLength(3);
  });
});
