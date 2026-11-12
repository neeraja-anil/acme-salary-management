// Types that mirror the JSON in docs/api-contracts.md. Dates are ISO strings ('2026-04-01'),
// timestamps ISO UTC strings, and money is whole units of the stated currency.

export type JobLevel = 'L1' | 'L2' | 'L3' | 'L4' | 'L5';
export type EmployeeStatus = 'ACTIVE' | 'TERMINATED';
export type StatusFilter = EmployeeStatus | 'ALL';
export type SalaryChangeReason =
  | 'NEW_HIRE'
  | 'PROMOTION'
  | 'ANNUAL_REVIEW'
  | 'MARKET_ADJUSTMENT'
  | 'CORRECTION'
  | 'OTHER';
export type AuditAction = 'CREATE' | 'IMPORT' | 'UPDATE' | 'DEACTIVATE' | 'SALARY_CHANGE';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 7807 error body returned by every endpoint (api-contracts.md section 2). */
export interface ProblemDetail {
  status: number;
  title: string;
  detail?: string;
  code: string;
  rule?: string;
  errors?: { field: string; message: string }[];
}

// ---- Reference data -------------------------------------------------------

export interface Country {
  code: string;
  name: string;
  currency: string;
}

export interface ReferenceData {
  countries: Country[];
  departments: { id: number; name: string }[];
  jobTitles: { id: number; name: string; departmentId: number }[];
  jobLevels: { code: JobLevel; name: string }[];
  salaryChangeReasons: SalaryChangeReason[];
  fxRates: { currency: string; rateToUsd: string; asOf: string }[];
}

// ---- Employees ------------------------------------------------------------

export interface DepartmentRef {
  id: number;
  name: string;
}

export interface JobTitleRef {
  id: number;
  name: string;
}

export interface EmployeeSummary {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  country: Country;
  department: DepartmentRef;
  jobTitle: JobTitleRef;
  jobLevel: JobLevel;
  status: EmployeeStatus;
  currentSalary: { amount: number; currency: string; amountUsd: number };
}

export interface EmployeeDetail {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  country: Country;
  department: DepartmentRef;
  jobTitle: JobTitleRef;
  jobLevel: JobLevel;
  hireDate: string;
  status: EmployeeStatus;
  terminationDate: string | null;
  currentSalary: { amount: number; currency: string; amountUsd: number; effectiveDate: string };
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeQuery {
  q: string;
  country: string[];
  departmentId: number[];
  jobTitleId: number[];
  jobLevel: JobLevel[];
  status: StatusFilter;
  page: number;
  size: number;
  sort: string;
}

export interface CreateEmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  departmentId: number;
  jobTitleId: number;
  jobLevel: JobLevel;
  hireDate: string;
  startingSalary: number;
}

export interface UpdateEmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  departmentId: number;
  jobTitleId: number;
  jobLevel: JobLevel;
  version: number;
}

// ---- Salaries and audit ---------------------------------------------------

export interface SalaryHistoryEntry {
  id: number;
  amount: number;
  currency: string;
  previousAmount: number | null;
  changePercent: number | null;
  effectiveDate: string;
  /** Null only on an imported first salary whose reason was not recorded. */
  reason: SalaryChangeReason | null;
  note: string | null;
  changedBy: string;
  changedAt: string;
}

export interface SalaryChangeRequest {
  amount: number;
  effectiveDate: string;
  reason: SalaryChangeReason;
  note: string | null;
  version: number;
}

export interface FieldChange {
  from?: unknown;
  to?: unknown;
}

export interface AuditEntry {
  id: number;
  action: AuditAction;
  entityType: 'EMPLOYEE' | 'SALARY';
  entityId: number;
  changes: Record<string, FieldChange>;
  performedBy: string;
  performedAt: string;
}

// ---- Analytics ------------------------------------------------------------

export interface PayStatistics {
  headcount: number;
  min: number;
  p25: number;
  median: number;
  p75: number;
  max: number;
  average: number;
  total: number;
}

export interface Overview {
  currency: string;
  headcount: number;
  totalPayroll: number;
  averageSalary: number | null;
  medianSalary: number | null;
  headcountByCountry: { code: string; name: string; headcount: number }[];
  headcountByDepartment: { id: number; name: string; headcount: number }[];
  fxRatesAsOf: string | null;
}

export interface CountryPayRow {
  country: { code: string; name: string };
  currency: string;
  stats: PayStatistics;
  statsUsd: PayStatistics;
}

export interface DepartmentPay {
  currency: string;
  country: string | null;
  rows: { department: DepartmentRef; stats: PayStatistics }[];
}

export interface PayBandRow {
  jobTitle: JobTitleRef;
  jobLevel: JobLevel;
  stats: PayStatistics;
  lowSample: boolean;
}

export interface PayBands {
  currency: string;
  country: string | null;
  rows: PayBandRow[];
}

export interface RecentChange {
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  country: string;
  currency: string;
  previousAmount: number;
  amount: number;
  changePercent: number;
  reason: SalaryChangeReason;
  effectiveDate: string;
  changedBy: string;
  changedAt: string;
}

export interface RecentChanges {
  days: number;
  from: string;
  to: string;
  summary: {
    changeCount: number;
    averageChangePercent: number | null;
    byReason: { reason: SalaryChangeReason; count: number; averageChangePercent: number }[];
  };
  changes: Page<RecentChange>;
}

// ---- Users and sign-in (api-contracts.md section 3 and 3a) ----------------------------------

/** A super admin can do everything an HR manager can, and also manages users. */
export type Role = 'SUPER_ADMIN' | 'HR_MANAGER';

export interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  username: string;
  displayName: string;
  password: string;
}

// ---- CSV import (api-contracts.md section 5a) -------------------------------------------------

export interface ImportRowError {
  /** The line in the file, with the header as line 1, as HR sees it in Excel. */
  row: number;
  column: string;
  value: string;
  message: string;
}

export interface ImportSampleRow {
  row: number;
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  department: string;
  jobTitle: string;
  jobLevel: JobLevel;
  hireDate: string;
  annualSalary: number;
  currency: string;
}

export interface ImportReport {
  fileName: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  /** At most 500; `errorsTruncated` says whether there were more. */
  errors: ImportRowError[];
  errorsTruncated: boolean;
  /** The first 5 valid rows, to check the columns were read correctly. */
  sample: ImportSampleRow[];
}

export interface ImportResult {
  fileName: string;
  importedCount: number;
}
