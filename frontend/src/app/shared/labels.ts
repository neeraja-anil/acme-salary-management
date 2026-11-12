import { AuditAction, JobLevel, SalaryChangeReason } from '../core/models';

const REASON_LABELS: Record<SalaryChangeReason, string> = {
  NEW_HIRE: 'New hire',
  PROMOTION: 'Promotion',
  ANNUAL_REVIEW: 'Annual review',
  MARKET_ADJUSTMENT: 'Market adjustment',
  CORRECTION: 'Correction',
  OTHER: 'Other',
};

export function reasonLabel(reason: SalaryChangeReason | null): string {
  return reason ? REASON_LABELS[reason] : 'Not recorded';
}

const LEVEL_NAMES: Record<JobLevel, string> = {
  L1: 'Associate',
  L2: 'Mid',
  L3: 'Senior',
  L4: 'Lead',
  L5: 'Principal',
};

/** "L3 Senior" */
export function levelLabel(level: JobLevel): string {
  return `${level} ${LEVEL_NAMES[level]}`;
}

const ACTION_LABELS: Record<AuditAction, string> = {
  CREATE: 'Employee created',
  IMPORT: 'Employee imported',
  UPDATE: 'Details updated',
  DEACTIVATE: 'Employee deactivated',
  SALARY_CHANGE: 'Salary changed',
};

export function actionLabel(action: AuditAction): string {
  return ACTION_LABELS[action];
}

const FIELD_LABELS: Record<string, string> = {
  employeeCode: 'Employee code',
  firstName: 'First name',
  lastName: 'Last name',
  email: 'Email',
  country: 'Country',
  department: 'Department',
  jobTitle: 'Job title',
  jobLevel: 'Level',
  hireDate: 'Hire date',
  salary: 'Salary',
  currency: 'Currency',
  status: 'Status',
  terminationDate: 'Termination date',
  amount: 'Amount',
  effectiveDate: 'Effective date',
  reason: 'Reason',
  note: 'Note',
};

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}
