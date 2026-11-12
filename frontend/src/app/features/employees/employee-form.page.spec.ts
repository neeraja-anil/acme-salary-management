import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';

import { loadReferenceData, pageTestProviders } from '../../../testing/test-providers';
import { EmployeeFormPage } from './employee-form.page';

/** Adding an employee (US-05): validation, the request sent, and how server errors are shown. */
describe('EmployeeFormPage (add employee)', () => {
  let fixture: ComponentFixture<EmployeeFormPage>;
  let page: EmployeeFormPage;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [EmployeeFormPage], providers: pageTestProviders() });
    http = TestBed.inject(HttpTestingController);
    await loadReferenceData();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(EmployeeFormPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const form = () => page['form'];
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  async function submit(): Promise<void> {
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function fillValidEmployee(): void {
    form().setValue({
      firstName: '  Lena ',
      lastName: 'Fischer',
      email: 'lena.fischer@example.com',
      countryCode: 'DE',
      departmentId: 1,
      jobTitleId: 1,
      jobLevel: 'L3',
      hireDate: new Date(2021, 2, 15),
      startingSalary: 78000,
    });
    form().markAsDirty();
  }

  it('shows an error under every missing field and sends nothing', async () => {
    await submit();

    expect(text()).toContain('First name is required');
    expect(text()).toContain('Email is required');
    expect(text()).toContain('Country is required');
    expect(text()).toContain('Hire date is required');
    expect(text()).toContain('Salary is required');
    http.expectNone({ method: 'POST', url: '/api/employees' });
  });

  it('sends the new employee with an ISO hire date, then opens their profile', async () => {
    fillValidEmployee();
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/employees' });
    expect(request.request.body).toEqual({
      firstName: '  Lena ', // trimming is the server's job, so the client sends what was typed
      lastName: 'Fischer',
      email: 'lena.fischer@example.com',
      countryCode: 'DE',
      departmentId: 1,
      jobTitleId: 1,
      jobLevel: 'L3',
      hireDate: '2021-03-15', // local date parts, never shifted by a time zone
      startingSalary: 78000,
    });
    const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
    request.flush({ id: 42 });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/employees', 42]);
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Employee created' }));
  });

  it('shows a duplicate email under the Email field', async () => {
    fillValidEmployee();
    await submit();

    http.expectOne('/api/employees').flush(
      { status: 409, title: 'Conflict', code: 'DUPLICATE_EMAIL', detail: 'Email is taken' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Email is already used by another employee');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('rejects a hire date in the future', async () => {
    fillValidEmployee();
    const nextYear = new Date();
    nextYear.setFullYear(nextYear.getFullYear() + 1);
    form().controls.hireDate.setValue(nextYear);
    await submit();

    expect(text()).toContain('Hire date cannot be in the future');
    http.expectNone({ method: 'POST', url: '/api/employees' });
  });

  it('offers only the job titles of the chosen department, and clears one that no longer fits', async () => {
    form().controls.departmentId.setValue(1);
    expect(page['jobTitleOptions']().map((t) => t.name)).toEqual(['Software Engineer', 'QA Engineer']);

    form().controls.jobTitleId.setValue(1);
    form().controls.departmentId.setValue(4);
    expect(page['jobTitleOptions']().map((t) => t.name)).toEqual(['Account Executive']);
    expect(form().controls.jobTitleId.value).toBeNull();
  });
});
