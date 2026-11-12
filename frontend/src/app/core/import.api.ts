import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ImportReport, ImportResult } from './models';

/** CSV import (api-contracts.md section 5a, US-21). */
@Injectable({ providedIn: 'root' })
export class ImportApi {
  private readonly http = inject(HttpClient);

  template(): Observable<Blob> {
    return this.http.get('/api/employees/import/template', { responseType: 'blob' });
  }

  /** Checks every row; saves nothing. */
  preview(file: File): Observable<ImportReport> {
    return this.http.post<ImportReport>('/api/employees/import/preview', this.form(file));
  }

  /** Imports every row, or nothing if any row is invalid (422 IMPORT_HAS_ERRORS with the report). */
  importFile(file: File): Observable<ImportResult> {
    return this.http.post<ImportResult>('/api/employees/import', this.form(file));
  }

  private form(file: File): FormData {
    const form = new FormData();
    form.append('file', file, file.name);
    return form;
  }
}
