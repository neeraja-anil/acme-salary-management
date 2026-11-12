import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ImportReport } from '../../core/models';
import { pageTestProviders } from '../../../testing/test-providers';
import { ImportPage } from './import.page';

const CSV = 'first_name,last_name,email\nLena,Fischer,lena@example.com\n';

function report(overrides: Partial<ImportReport> = {}): ImportReport {
  return {
    fileName: 'people.csv',
    totalRows: 3,
    validRows: 3,
    invalidRows: 0,
    errors: [],
    errorsTruncated: false,
    sample: [
      {
        row: 2, firstName: 'Lena', lastName: 'Fischer', email: 'lena@example.com', countryCode: 'DE',
        department: 'Engineering', jobTitle: 'Software Engineer', jobLevel: 'L3', hireDate: '2021-03-15',
        annualSalary: 78000, currency: 'EUR',
      },
    ],
    ...overrides,
  };
}

/** Importing employees from a CSV file (US-21): choosing the file, the check, the review and the import. */
describe('ImportPage (CSV import)', () => {
  let fixture: ComponentFixture<ImportPage>;
  let page: ImportPage;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [ImportPage], providers: pageTestProviders() });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ImportPage);
    page = fixture.componentInstance;
    await render();
  });

  afterEach(() => http.verify());

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  function button(label: string): HTMLButtonElement {
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    const found = buttons.find((b) => b.textContent?.trim().includes(label));
    if (!found) throw new Error(`No button "${label}"`);
    return found;
  }

  async function choose(file: File): Promise<void> {
    page['onFileChosen']({ target: { files: [file], value: '' } } as unknown as Event);
    await render();
  }

  async function chooseAndCheck(): Promise<void> {
    await choose(new File([CSV], 'people.csv', { type: 'text/csv' }));
    button('Check file').click();
    await render();
  }

  it('refuses a file that is not a .csv before uploading anything', async () => {
    await choose(new File(['x'], 'people.xlsx'));

    expect(text()).toContain('Choose a .csv file');
    expect(button('Check file').disabled).toBe(true);
  });

  it('refuses a file over 5 MB before uploading anything', async () => {
    const big = new File(['x'], 'big.csv');
    Object.defineProperty(big, 'size', { value: 6 * 1024 * 1024 });
    await choose(big);

    expect(text()).toContain('over 5 MB');
  });

  it('sends the chosen file to the preview and shows each problem with its spreadsheet row', async () => {
    await chooseAndCheck();

    const request = http.expectOne({ method: 'POST', url: '/api/employees/import/preview' });
    const sent = (request.request.body as FormData).get('file') as File;
    expect(sent.name).toBe('people.csv');
    expect(await sent.text()).toBe(CSV);

    request.flush(report({
      validRows: 1,
      invalidRows: 2,
      errors: [
        { row: 3, column: 'country_code', value: 'DX', message: 'unknown country' },
        { row: 4, column: 'currency', value: 'USD', message: 'must be EUR for country DE' },
      ],
    }));
    await render();

    expect(text()).toContain('unknown country');
    expect(text()).toContain('must be EUR for country DE');
    expect(text()).toContain('Nothing has been imported');
    expect(text()).toContain('Lena Fischer'); // the first rows, as read from the file
    expect(button('Import 3 employees').disabled).toBe(true);
  });

  it('imports a valid file and shows how many employees were added', async () => {
    await chooseAndCheck();
    http.expectOne('/api/employees/import/preview').flush(report());
    await render();

    button('Import 3 employees').click();
    await render();
    const request = http.expectOne({ method: 'POST', url: '/api/employees/import' });
    expect(((request.request.body as FormData).get('file') as File).name).toBe('people.csv');
    request.flush({ fileName: 'people.csv', importedCount: 3 });
    await render();

    expect(text()).toContain('3 employees imported');
    expect(text()).toContain('Go to employees');
  });

  it('shows the new problems if the data changed between the check and the import', async () => {
    await chooseAndCheck();
    http.expectOne('/api/employees/import/preview').flush(report());
    await render();

    button('Import 3 employees').click();
    await render();
    http.expectOne('/api/employees/import').flush(
      {
        status: 422, title: 'Import has errors', code: 'IMPORT_HAS_ERRORS',
        detail: '1 of 3 rows have errors. Nothing was imported.',
        report: report({
          validRows: 2,
          invalidRows: 1,
          errors: [{ row: 2, column: 'email', value: 'lena@example.com', message: 'already used by an existing employee' }],
        }),
      },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await render();

    expect(text()).toContain('already used by an existing employee');
    expect(text()).toContain('Nothing was imported');
    expect(text()).not.toContain('employees imported');
  });

  it('explains a file the server cannot read, on the upload step', async () => {
    await chooseAndCheck();
    http.expectOne('/api/employees/import/preview').flush(
      { status: 400, title: 'Invalid file', code: 'INVALID_FILE', detail: 'Missing columns: email, country_code.' },
      { status: 400, statusText: 'Bad Request' },
    );
    await render();

    expect(text()).toContain('Missing columns: email, country_code.');
    expect(text()).toContain('Upload a CSV file'); // still on step 1
  });
});
