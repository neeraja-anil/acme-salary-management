import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CountryPayRow, DepartmentPay, JobLevel, Overview, PayBands, RecentChanges } from './models';

/** Pay analytics (api-contracts.md section 8). */
@Injectable({ providedIn: 'root' })
export class AnalyticsApi {
  private readonly http = inject(HttpClient);

  overview(): Observable<Overview> {
    return this.http.get<Overview>('/api/analytics/overview');
  }

  byCountry(): Observable<CountryPayRow[]> {
    return this.http.get<CountryPayRow[]>('/api/analytics/by-country');
  }

  byDepartment(country: string | null): Observable<DepartmentPay> {
    const params = country ? new HttpParams().set('country', country) : undefined;
    return this.http.get<DepartmentPay>('/api/analytics/by-department', { params });
  }

  payBands(filter: {
    departmentId: number | null;
    jobTitleId: number | null;
    jobLevel: JobLevel | null;
    country: string | null;
  }): Observable<PayBands> {
    let params = new HttpParams();
    if (filter.departmentId != null) params = params.set('departmentId', filter.departmentId);
    if (filter.jobTitleId != null) params = params.set('jobTitleId', filter.jobTitleId);
    if (filter.jobLevel) params = params.set('jobLevel', filter.jobLevel);
    if (filter.country) params = params.set('country', filter.country);
    return this.http.get<PayBands>('/api/analytics/pay-bands', { params });
  }

  /** `sort` is `effectiveDate` or `changePercent` with `,asc` or `,desc`; the server defaults to newest first. */
  recentChanges(days: number, page: number, size: number, sort = 'effectiveDate,desc'): Observable<RecentChanges> {
    return this.http.get<RecentChanges>('/api/analytics/recent-changes', {
      params: new HttpParams().set('days', days).set('page', page).set('size', size).set('sort', sort),
    });
  }
}
