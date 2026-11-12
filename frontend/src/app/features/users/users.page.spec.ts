import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Confirmation, ConfirmationService } from 'primeng/api';

import { User } from '../../core/models';
import { pageTestProviders } from '../../../testing/test-providers';
import { UsersPage } from './users.page';

const ADMIN: User = { id: 1, username: 'admin', displayName: 'Super admin', role: 'SUPER_ADMIN', enabled: true, createdAt: '2026-11-12T09:00:00Z' };
const LENA: User = { id: 2, username: 'Lena.HR', displayName: 'Lena Fischer', role: 'HR_MANAGER', enabled: true, createdAt: '2026-11-12T10:00:00Z' };

/** User management for super admins (US-22). */
describe('UsersPage (user management)', () => {
  let fixture: ComponentFixture<UsersPage>;
  let http: HttpTestingController;
  let confirm: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [UsersPage], providers: pageTestProviders() });
    http = TestBed.inject(HttpTestingController);
    confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
    fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: '/api/users' }).flush([ADMIN, LENA]);
    await render();
  });

  afterEach(() => http.verify());

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function row(name: string): HTMLTableRowElement {
    const rows = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr')) as HTMLTableRowElement[];
    return rows.find((r) => r.textContent?.includes(name))!;
  }

  function buttonIn(r: HTMLElement, label: string): HTMLButtonElement | undefined {
    return Array.from(r.querySelectorAll('button')).find((b) => b.textContent?.trim().includes(label));
  }

  it('lists every user with their role and status', () => {
    expect(row('Lena Fischer').textContent).toContain('HR manager');
    expect(row('Lena Fischer').textContent).toContain('Active');
    expect(row('Super admin').textContent).toContain('Super admin');
  });

  it('offers no actions on super admin accounts, so the last one cannot be locked out', () => {
    expect(row('Super admin').textContent).toContain("Can't be changed here");
    expect(row('Super admin').querySelectorAll('button')).toHaveLength(0);
    expect(buttonIn(row('Lena Fischer'), 'Reset password')).toBeDefined();
    expect(buttonIn(row('Lena Fischer'), 'Disable')).toBeDefined();
  });

  it('asks before disabling someone, then disables them', async () => {
    buttonIn(row('Lena Fischer'), 'Disable')!.click();
    const question = confirm.mock.calls[0][0] as Confirmation;
    expect(question.header).toBe('Disable Lena Fischer?');

    question.accept!();
    const request = http.expectOne({ method: 'PATCH', url: '/api/users/2' });
    expect(request.request.body).toEqual({ enabled: false });
    request.flush({ ...LENA, enabled: false });
    await render();

    expect(row('Lena Fischer').textContent).toContain('Disabled');
    expect(buttonIn(row('Lena Fischer'), 'Enable')).toBeDefined();
  });

  it('does nothing when disabling is cancelled', () => {
    buttonIn(row('Lena Fischer'), 'Disable')!.click();
    (confirm.mock.calls[0][0] as Confirmation).reject!();

    http.expectNone({ method: 'PATCH', url: '/api/users/2' });
  });

  it('enables a disabled user without asking', async () => {
    buttonIn(row('Lena Fischer'), 'Disable')!.click();
    (confirm.mock.calls[0][0] as Confirmation).accept!();
    http.expectOne('/api/users/2').flush({ ...LENA, enabled: false });
    await render();
    confirm.mockClear();

    buttonIn(row('Lena Fischer'), 'Enable')!.click();
    expect(confirm).not.toHaveBeenCalled();
    const request = http.expectOne({ method: 'PATCH', url: '/api/users/2' });
    expect(request.request.body).toEqual({ enabled: true });
    request.flush(LENA);
  });
});
