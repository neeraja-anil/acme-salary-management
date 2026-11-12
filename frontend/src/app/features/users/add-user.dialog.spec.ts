import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { pageTestProviders } from '../../../testing/test-providers';
import { AddUserDialog } from './add-user.dialog';

/** Adding an HR manager (US-22). */
describe('AddUserDialog', () => {
  let fixture: ComponentFixture<AddUserDialog>;
  let dialog: AddUserDialog;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [AddUserDialog], providers: pageTestProviders() });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddUserDialog);
    dialog = fixture.componentInstance;
    await render();
  });

  afterEach(() => http.verify());

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  // The dialog is drawn in an overlay outside the component, so look in the whole document.
  const text = () => document.body.textContent ?? '';

  async function submit(values: { username: string; displayName: string; password: string }): Promise<void> {
    dialog['form'].setValue(values);
    dialog['save']();
    await render();
  }

  it('checks the username and password before sending anything', async () => {
    await submit({ username: 'x', displayName: 'X', password: 'short' });

    expect(text()).toContain('Use 3-50 letters, digits, dots, dashes or underscores');
    expect(text()).toContain('Use at least 10 characters');
    http.expectNone({ method: 'POST', url: '/api/users' });
  });

  it('sends the new user exactly as typed (usernames are case-sensitive)', async () => {
    await submit({ username: 'Lena.HR', displayName: 'Lena Fischer', password: 'lena-password-1' });

    const request = http.expectOne({ method: 'POST', url: '/api/users' });
    expect(request.request.body).toEqual({ username: 'Lena.HR', displayName: 'Lena Fischer', password: 'lena-password-1' });
    request.flush({ id: 2, username: 'Lena.HR', displayName: 'Lena Fischer', role: 'HR_MANAGER', enabled: true, createdAt: '2026-11-12T10:00:00Z' });
    await render();

    expect(dialog['created']?.username).toBe('Lena.HR');
    expect(dialog['visible']()).toBe(false); // closes, and reports the new user once it has closed
  });

  it('shows a taken username under the Username field and stays open', async () => {
    await submit({ username: 'lena.hr', displayName: 'Someone Else', password: 'another-password' });

    http.expectOne('/api/users').flush(
      { status: 409, title: 'Conflict', code: 'DUPLICATE_USERNAME', detail: 'Username lena.hr is already taken' },
      { status: 409, statusText: 'Conflict' },
    );
    await render();

    expect(text()).toContain('That username is already taken');
    expect(dialog['visible']()).toBe(true);
  });
});
