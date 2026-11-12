import { Params, ParamMap } from '@angular/router';

import { EmployeeQuery, JobLevel, StatusFilter } from '../../core/models';
import { PAGE_SIZES, TABLE_PAGE_SIZE } from '../../shared/paging';

/**
 * The directory keeps its whole state in the URL (ui-design.md 4.3), e.g.
 * /employees?q=lena&country=DE&country=FR&page=2&sort=salary,desc
 * These two functions convert between the URL and the query sent to the API.
 */
export const DEFAULT_QUERY: EmployeeQuery = {
  q: '',
  country: [],
  departmentId: [],
  jobTitleId: [],
  jobLevel: [],
  status: 'ACTIVE',
  page: 0,
  size: TABLE_PAGE_SIZE,
  sort: 'name,asc',
};

export { PAGE_SIZES };

/** Show salaries in each employee's own currency, or converted to USD (?salaryIn=usd). */
export type SalaryView = 'local' | 'usd';

export function salaryViewFromParams(params: ParamMap): SalaryView {
  return params.get('salaryIn') === 'usd' ? 'usd' : 'local';
}
const STATUSES: StatusFilter[] = ['ACTIVE', 'TERMINATED', 'ALL'];
const LEVELS: JobLevel[] = ['L1', 'L2', 'L3', 'L4', 'L5'];

function ids(values: string[]): number[] {
  return values.map(Number).filter((n) => Number.isInteger(n) && n > 0);
}

/** Tolerates hand-edited URLs: unknown values fall back to the defaults instead of failing. */
export function queryFromParams(params: ParamMap): EmployeeQuery {
  const status = params.get('status') as StatusFilter | null;
  const page = Number(params.get('page'));
  const size = Number(params.get('size'));
  return {
    q: params.get('q') ?? '',
    country: params.getAll('country').map((c) => c.toUpperCase()),
    departmentId: ids(params.getAll('departmentId')),
    jobTitleId: ids(params.getAll('jobTitleId')),
    jobLevel: params.getAll('jobLevel').filter((l): l is JobLevel => LEVELS.includes(l as JobLevel)),
    status: status && STATUSES.includes(status) ? status : DEFAULT_QUERY.status,
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: PAGE_SIZES.includes(size) ? size : DEFAULT_QUERY.size,
    sort: params.get('sort') || DEFAULT_QUERY.sort,
  };
}

/** Only non-default values go into the URL, so links stay short. */
export function paramsFromQuery(query: EmployeeQuery, salaryView: SalaryView = 'local'): Params {
  const params: Params = {};
  if (salaryView === 'usd') params['salaryIn'] = 'usd';
  if (query.q.trim()) params['q'] = query.q.trim();
  if (query.country.length) params['country'] = query.country;
  if (query.departmentId.length) params['departmentId'] = query.departmentId;
  if (query.jobTitleId.length) params['jobTitleId'] = query.jobTitleId;
  if (query.jobLevel.length) params['jobLevel'] = query.jobLevel;
  if (query.status !== DEFAULT_QUERY.status) params['status'] = query.status;
  if (query.page > 0) params['page'] = query.page;
  if (query.size !== DEFAULT_QUERY.size) params['size'] = query.size;
  if (query.sort !== DEFAULT_QUERY.sort) params['sort'] = query.sort;
  return params;
}
