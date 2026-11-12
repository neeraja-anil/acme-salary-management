import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecentChanges } from '../../core/models';
import { pageTestProviders } from '../../../testing/test-providers';
import { RecentChangesTab } from './recent-changes-tab';

const DATA: RecentChanges = {
  days: 30,
  from: '2026-10-14',
  to: '2026-11-12',
  summary: { changeCount: 2, averageChangePercent: 6, byReason: [{ reason: 'PROMOTION', count: 2, averageChangePercent: 6 }] },
  changes: {
    content: [
      {
        employeeId: 1, employeeCode: 'EMP-000001', employeeName: 'Lena Fischer', country: 'DE', currency: 'EUR',
        previousAmount: 70000, amount: 75600, changePercent: 8, reason: 'PROMOTION', effectiveDate: '2026-11-01',
        changedBy: 'admin', changedAt: '2026-11-01T09:00:00Z',
      },
    ],
    page: 0, size: 10, totalElements: 2, totalPages: 1,
  },
};

/** Recent salary changes (US-15): sorting happens on the server, so it covers every page. */
describe('RecentChangesTab (pay insights)', () => {
  let fixture: ComponentFixture<RecentChangesTab>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [RecentChangesTab], providers: pageTestProviders() });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RecentChangesTab);
    await render();
  });

  afterEach(() => http.verify());

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function nextRequest(): TestRequest {
    return http.expectOne((req) => req.url === '/api/analytics/recent-changes');
  }

  it('asks for the newest changes first by default', async () => {
    const request = nextRequest();
    expect(request.request.params.get('sort')).toBe('effectiveDate,desc');
    expect(request.request.params.get('days')).toBe('30');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(DATA);
    await render();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Lena Fischer');
  });

  it('asks the server for the largest % change first when the Change column is sorted', async () => {
    nextRequest().flush(DATA);
    await render();

    const changeHeader = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('th'))
      .find((th) => th.textContent?.trim() === 'Change')!;
    changeHeader.click();
    await render();

    const request = nextRequest();
    expect(request.request.params.get('sort')).toBe('changePercent,desc');
    expect(request.request.params.get('page')).toBe('0'); // a new sort starts again from page 1
    request.flush(DATA);
  });

  it('does not let the old → new amounts be sorted, because they mix currencies', async () => {
    nextRequest().flush(DATA);
    await render();

    const amountHeader = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('th'))
      .find((th) => th.textContent?.includes('Old → new'))!;
    expect(amountHeader.classList).not.toContain('p-datatable-sortable-column');
  });
});
