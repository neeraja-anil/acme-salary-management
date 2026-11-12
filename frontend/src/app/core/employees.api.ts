import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AuditEntry,
  CreateEmployeeRequest,
  EmployeeDetail,
  EmployeeQuery,
  EmployeeSummary,
  Page,
  SalaryChangeRequest,
  SalaryHistoryEntry,
  UpdateEmployeeRequest,
} from './models';

/** Employees, salaries and audit (api-contracts.md sections 5 to 7). */
@Injectable({ providedIn: 'root' })
export class EmployeesApi {
  private readonly http = inject(HttpClient);

  search(query: EmployeeQuery): Observable<Page<EmployeeSummary>> {
    let params = new HttpParams()
      .set('status', query.status)
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort);
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    query.country.forEach((v) => (params = params.append('country', v)));
    query.departmentId.forEach((v) => (params = params.append('departmentId', v)));
    query.jobTitleId.forEach((v) => (params = params.append('jobTitleId', v)));
    query.jobLevel.forEach((v) => (params = params.append('jobLevel', v)));
    return this.http.get<Page<EmployeeSummary>>('/api/employees', { params });
  }

  get(id: number): Observable<EmployeeDetail> {
    return this.http.get<EmployeeDetail>(`/api/employees/${id}`);
  }

  create(request: CreateEmployeeRequest): Observable<EmployeeDetail> {
    return this.http.post<EmployeeDetail>('/api/employees', request);
  }

  update(id: number, request: UpdateEmployeeRequest): Observable<EmployeeDetail> {
    return this.http.put<EmployeeDetail>(`/api/employees/${id}`, request);
  }

  deactivate(id: number, terminationDate: string, version: number): Observable<EmployeeDetail> {
    return this.http.post<EmployeeDetail>(`/api/employees/${id}/deactivate`, { terminationDate, version });
  }

  salaryHistory(id: number): Observable<SalaryHistoryEntry[]> {
    return this.http.get<SalaryHistoryEntry[]>(`/api/employees/${id}/salaries`);
  }

  changeSalary(id: number, request: SalaryChangeRequest): Observable<SalaryHistoryEntry> {
    return this.http.post<SalaryHistoryEntry>(`/api/employees/${id}/salaries`, request);
  }

  audit(id: number, page: number, size: number): Observable<Page<AuditEntry>> {
    return this.http.get<Page<AuditEntry>>(`/api/employees/${id}/audit`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }
}
