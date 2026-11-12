import { ValidatorFn } from '@angular/forms';

/**
 * The API uses ISO dates ('2026-11-01'); the date picker works with JS Dates at local midnight.
 * These helpers convert using local date parts, never UTC, so a date cannot shift by a day.
 */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function fromIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function today(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

/**
 * Checks a date picker's value. The picker (with `keepInvalid`) leaves typed text it can't read as a
 * string, and an out-of-range date as a Date, so both can be reported instead of silently ignored.
 * It parses DD/MM/YYYY itself and rejects impossible dates such as 31/02/2026.
 * Errors: `dateParse`, `dateMin`, `dateMax`. An empty value is left to `Validators.required`.
 */
export function dateInput(range: { min?: Date; max?: Date }): ValidatorFn {
  return (control) => {
    const value: unknown = control.value;
    if (value === null || value === undefined || value === '') return null;
    if (!(value instanceof Date) || Number.isNaN(value.getTime())) return { dateParse: true };
    if (range.min && value < range.min) return { dateMin: true };
    if (range.max && value > range.max) return { dateMax: true };
    return null;
  };
}
