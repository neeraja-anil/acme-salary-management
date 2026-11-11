# API Contracts: ACME Salary Management

All endpoints are under `/api` and use JSON over HTTPS. User stories are referenced as `US-xx` (see [`userstory.md`](./userstory.md)).

## 1. Conventions

| Topic | Convention |
|---|---|
| **Auth** | Every endpoint except `POST /api/auth/login` and `GET /api/auth/csrf` requires a session cookie. Requests with no session get `401` |
| **CSRF** | State-changing requests (`POST`/`PUT`/`DELETE`) must send the `X-XSRF-TOKEN` header with the value of the `XSRF-TOKEN` cookie. Angular `HttpClient` does this automatically |
| **Dates** | `YYYY-MM-DD` (e.g. `2026-04-01`). Timestamps are ISO-8601 UTC (`2026-04-01T09:15:00Z`) |
| **Money** | Integers in whole currency units, always next to a `currency` field. USD values are also whole dollars, rounded half-up |
| **Paging** | `page` (0-based, default `0`), `size` (default `25`, max `100`) |
| **Sorting** | `sort=<field>,<asc\|desc>`. Only fields on each endpoint's allow-list are accepted; anything else returns `400` |
| **Paged response** | `{ "content": [...], "page": 0, "size": 25, "totalElements": 10000, "totalPages": 400 }` |
| **Concurrency** | Update requests carry the employee `version`. If it is stale the API returns `409 STALE_VERSION` |
| **Scope of analytics** | Only `ACTIVE` employees are included |

## 2. Errors

Errors follow **RFC 7807 Problem Details** (`application/problem+json`), plus a stable machine-readable `code` and optional field errors:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request has 2 invalid fields",
  "instance": "/api/employees",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "email", "message": "must be a valid email address" },
    { "field": "startingSalary.amount", "message": "must be greater than 0" }
  ]
}
```

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation failed (missing fields, bad format, out of range) |
| 400 | `INVALID_PARAMETER` | Bad query parameter (unknown sort field, size > 100, unknown enum) |
| 401 | `UNAUTHENTICATED` | No or expired session; wrong credentials on login |
| 403 | `FORBIDDEN` | CSRF token missing or invalid |
| 404 | `NOT_FOUND` | The employee (or other resource) doesn't exist |
| 409 | `DUPLICATE_EMAIL` | The email is already used by another employee |
| 409 | `STALE_VERSION` | The record changed since it was loaded |
| 400 | `INVALID_FILE` | The upload is not a readable CSV, or required columns are missing |
| 413 | `FILE_TOO_LARGE` | The upload is over 5 MB or 10,000 rows |
| 422 | `IMPORT_HAS_ERRORS` | An import was confirmed but some rows are invalid. The body includes the same report as preview |
| 422 | `BUSINESS_RULE_VIOLATION` | The input is valid but breaks a rule. `detail` explains which; `rule` names it (see §5) |
| 500 | `INTERNAL_ERROR` | Unexpected error. No stack trace is returned; details are logged on the server |

**400 vs 422:** `400` means the request is malformed. `422` means the request is well-formed but not allowed (for example, an effective date before the current one). The UI can then show field errors for 400 and a message banner for 422.

---

## 3. Authentication *(US-18, US-19, US-20)*

### `GET /api/auth/csrf`
Called once when the app starts. It sets the `XSRF-TOKEN` cookie. → `204 No Content`

### `POST /api/auth/login`
```json
// request
{ "username": "hr.manager", "password": "••••••••" }
// 200 OK  (sets JSESSIONID cookie)
{ "username": "hr.manager", "displayName": "Priya Sharma", "role": "HR_MANAGER" }
```
`401 UNAUTHENTICATED` with `detail: "Invalid credentials"`. The message is the same whether the username or the password is wrong.

### `POST /api/auth/logout`
Invalidates the session. → `204 No Content`

### `GET /api/auth/me`
Returns the current user (same shape as the login response). Returns `401` if not signed in, which the Angular auth guard uses on page load.

---

## 4. Reference data

### `GET /api/reference`
A single call returns all lookups. The UI fetches it once and caches it for filters and forms.
```json
{
  "countries":   [ { "code": "DE", "name": "Germany", "currency": "EUR" } ],
  "departments": [ { "id": 1, "name": "Engineering" } ],
  "jobTitles":   [ { "id": 1, "name": "Software Engineer", "departmentId": 1 } ],
  "jobLevels":   [ { "code": "L3", "name": "Senior" } ],
  "salaryChangeReasons": [ "PROMOTION", "ANNUAL_REVIEW", "MARKET_ADJUSTMENT", "CORRECTION", "OTHER" ],
  "fxRates":     [ { "currency": "EUR", "rateToUsd": "1.0850", "asOf": "2026-01-01" } ]
}
```
`NEW_HIRE` and `IMPORT` are set by the system only, so they are not in `salaryChangeReasons`.

---

## 5. Employees *(US-01 to US-07, US-16)*

### `GET /api/employees`: directory search
| Param | Type | Notes |
|---|---|---|
| `q` | string | Partial, case-insensitive match on first name, last name, full name, employee code or email |
| `country` | string, repeatable | `?country=DE&country=FR` |
| `departmentId` | int, repeatable | |
| `jobTitleId` | int, repeatable | |
| `jobLevel` | enum, repeatable | `L1`…`L5` |
| `status` | `ACTIVE` \| `TERMINATED` \| `ALL` | default `ACTIVE` |
| `page`, `size` | | See conventions |
| `sort` | `name` \| `department` \| `hireDate` \| `salary`, `asc` \| `desc` | default `name,asc` |

Filters combine with AND. Repeated values of one filter combine with OR.

> **Note on `sort=salary`:** salaries are compared as stored, in local currency. When the results span several currencies this ordering isn't meaningful across them, so the UI suggests filtering to one country first.

```json
// 200 OK
{
  "content": [
    {
      "id": 42,
      "employeeCode": "EMP-000042",
      "firstName": "Lena", "lastName": "Fischer",
      "email": "lena.fischer@acme.example",
      "country": { "code": "DE", "name": "Germany" },
      "department": { "id": 1, "name": "Engineering" },
      "jobTitle": { "id": 1, "name": "Software Engineer" },
      "jobLevel": "L3",
      "status": "ACTIVE",
      "currentSalary": { "amount": 78000, "currency": "EUR" }
    }
  ],
  "page": 0, "size": 25, "totalElements": 1243, "totalPages": 50
}
```

### `GET /api/employees/{id}`: profile
```json
// 200 OK
{
  "id": 42,
  "employeeCode": "EMP-000042",
  "firstName": "Lena", "lastName": "Fischer",
  "email": "lena.fischer@acme.example",
  "country": { "code": "DE", "name": "Germany", "currency": "EUR" },
  "department": { "id": 1, "name": "Engineering" },
  "jobTitle": { "id": 1, "name": "Software Engineer" },
  "jobLevel": "L3",
  "hireDate": "2021-03-15",
  "status": "ACTIVE",
  "terminationDate": null,
  "currentSalary": { "amount": 78000, "currency": "EUR", "amountUsd": 84630, "effectiveDate": "2026-04-01" },
  "version": 3,
  "createdAt": "2021-03-15T09:00:00Z",
  "updatedAt": "2026-04-01T10:12:44Z"
}
```
`404 NOT_FOUND` if the employee doesn't exist.

### `POST /api/employees`: add a new hire
```json
// request
{
  "firstName": "Arjun", "lastName": "Mehta",
  "email": "arjun.mehta@acme.example",
  "countryCode": "IN",
  "departmentId": 1,
  "jobTitleId": 1,
  "jobLevel": "L2",
  "hireDate": "2026-11-01",
  "startingSalary": 1800000
}
```
→ `201 Created`, with `Location: /api/employees/{id}` and the profile body.

The server generates `employeeCode`, takes the currency from the country, and creates the first `salary_history` row (`reason = NEW_HIRE`, `effectiveDate = hireDate`) and a `CREATE` audit entry, all in one transaction.

| Validation | Error |
|---|---|
| Required fields; email format; names 1–100 characters; `startingSalary` > 0 | 400 `VALIDATION_FAILED` |
| `hireDate` in the future | 422, `rule: HIRE_DATE_IN_FUTURE` |
| Unknown country, department or title; title not in the chosen department | 422, `rule: INVALID_REFERENCE` |
| Email already used | 409 `DUPLICATE_EMAIL` |

### `PUT /api/employees/{id}`: edit details (not salary, not country)
```json
{
  "firstName": "Lena", "lastName": "Fischer-Wolf",
  "email": "lena.fischer@acme.example",
  "departmentId": 1, "jobTitleId": 4, "jobLevel": "L4",
  "version": 3
}
```
→ `200 OK` with the updated profile. An `UPDATE` audit entry records only the fields that changed.

Errors: 400, 404, 409 `DUPLICATE_EMAIL`, 409 `STALE_VERSION`, and 422 `EMPLOYEE_TERMINATED` (terminated records are read-only).

### `POST /api/employees/{id}/deactivate`
```json
{ "terminationDate": "2026-10-31", "version": 3 }
```
→ `200 OK` with the profile (`status: TERMINATED`), plus a `DEACTIVATE` audit entry.

Errors: 422 `ALREADY_TERMINATED`, 422 `TERMINATION_BEFORE_HIRE`, 409 `STALE_VERSION`.

This is an explicit action rather than `DELETE`, because records are never removed.

### `GET /api/employees/export` *(US-16, Could)*
Takes the same filters as the directory search. Returns `text/csv` with `Content-Disposition: attachment; filename="employees-YYYY-MM-DD.csv"`. It is capped at 10,000 rows.

---

## 5a. CSV import *(US-21)*

### File format
UTF-8 (a byte-order mark is allowed), comma-separated, RFC 4180 quoting, with a header row. Column order does not matter; header names do.

| Column | Required | Example | Rule |
|---|---|---|---|
| `first_name` | ✅ | `Lena` | 1–100 characters |
| `last_name` | ✅ | `Fischer` | 1–100 characters |
| `email` | ✅ | `lena.fischer@acme.example` | Valid format. Must not already exist in the system or appear twice in the file |
| `country_code` | ✅ | `DE` | A known country code |
| `department` | ✅ | `Engineering` | A known department name (case-insensitive) |
| `job_title` | ✅ | `Software Engineer` | A known job title in that department (case-insensitive) |
| `job_level` | ✅ | `L3` | `L1`…`L5` |
| `hire_date` | ✅ | `2021-03-15` | `YYYY-MM-DD`, not in the future |
| `annual_salary` | ✅ | `78000` | Whole number, > 0 |
| `currency` | ✅ | `EUR` | Must equal the country's currency. This catches salaries entered in the wrong currency |
| `salary_effective_date` | ➖ | `2025-04-01` | Defaults to `hire_date`. Must be between `hire_date` and today |

### `GET /api/employees/import/template`
Returns `text/csv` (`employees-import-template.csv`): the header row plus one example row.

### `POST /api/employees/import/preview`
`multipart/form-data` with a single `file` part. It validates every row and **saves nothing**.
```json
// 200 OK
{
  "fileName": "acme-germany.csv",
  "totalRows": 1250,
  "validRows": 1247,
  "invalidRows": 3,
  "errors": [
    { "row": 214, "column": "country_code", "value": "DX", "message": "unknown country" },
    { "row": 560, "column": "email", "value": "a.roy@acme.example", "message": "duplicate of row 12" },
    { "row": 902, "column": "currency", "value": "USD", "message": "must be EUR for country DE" }
  ],
  "errorsTruncated": false,
  "sample": [
    { "row": 2, "firstName": "Lena", "lastName": "Fischer", "email": "lena.fischer@acme.example",
      "countryCode": "DE", "department": "Engineering", "jobTitle": "Software Engineer",
      "jobLevel": "L3", "hireDate": "2021-03-15", "annualSalary": 78000, "currency": "EUR" }
  ]
}
```
`row` is the line number in the file, with the header as line 1, so it matches what HR sees in Excel. At most 500 errors are listed; `errorsTruncated` says whether more exist. `sample` holds the first 5 valid rows so HR can check that the columns were read correctly.

Errors: 400 `INVALID_FILE`, 413 `FILE_TOO_LARGE`.

### `POST /api/employees/import`
The same `multipart/form-data` file. The server validates it again (it keeps nothing from the preview) and imports **only if every row is valid**, in one transaction.
```json
// 201 Created
{ "fileName": "acme-germany.csv", "importedCount": 1250 }
```
Each imported employee gets an employee code, a salary row with `reason = IMPORT`, and an `IMPORT` audit entry with `performedBy` set to the session user.

Errors: 400 `INVALID_FILE`, 413 `FILE_TOO_LARGE`, 422 `IMPORT_HAS_ERRORS` (body includes the preview report).

---

## 6. Salaries *(US-08, US-09)*

### `GET /api/employees/{id}/salaries`: history, newest first
```json
[
  {
    "id": 9001,
    "amount": 78000, "currency": "EUR",
    "previousAmount": 72000, "changePercent": 8.33,
    "effectiveDate": "2026-04-01",
    "reason": "ANNUAL_REVIEW", "note": null,
    "changedBy": "hr.manager", "changedAt": "2026-04-01T10:12:44Z"
  },
  {
    "id": 4120,
    "amount": 72000, "currency": "EUR",
    "previousAmount": null, "changePercent": null,
    "effectiveDate": "2021-03-15",
    "reason": "NEW_HIRE", "note": null,
    "changedBy": "system-seed", "changedAt": "2021-03-15T09:00:00Z"
  }
]
```
The list is not paginated, because one employee has only a handful of rows.

### `POST /api/employees/{id}/salaries`: change salary
```json
{
  "amount": 84000,
  "effectiveDate": "2026-11-01",
  "reason": "PROMOTION",
  "note": "Promoted to Lead",
  "version": 3
}
```
→ `201 Created` with the new history entry. In the same transaction it updates the employee's current salary, increments `version`, and writes a `SALARY_CHANGE` audit entry.

| Rule | Error |
|---|---|
| `amount` > 0 and ≤ 100,000,000; `reason` is a known value; `note` ≤ 500 characters | 400 `VALIDATION_FAILED` |
| `note` required when `reason = OTHER` | 400 `VALIDATION_FAILED` |
| `effectiveDate` earlier than the current salary's effective date | 422, `rule: EFFECTIVE_DATE_BEFORE_CURRENT` |
| `effectiveDate` in the future | 422, `rule: EFFECTIVE_DATE_IN_FUTURE` |
| `amount` equal to the current salary | 422, `rule: NO_CHANGE` |
| Employee is terminated | 422, `rule: EMPLOYEE_TERMINATED` |
| Stale `version` | 409 `STALE_VERSION` |

---

## 7. Audit *(US-10)*

### `GET /api/employees/{id}/audit?page=0&size=20`
Paged, newest first.
```json
{
  "content": [
    {
      "id": 31007,
      "action": "UPDATE",
      "entityType": "EMPLOYEE",
      "entityId": 42,
      "changes": {
        "jobTitleId": { "from": 1, "to": 4 },
        "jobLevel":   { "from": "L3", "to": "L4" }
      },
      "performedBy": "hr.manager",
      "performedAt": "2026-11-01T08:30:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1
}
```

---

## 8. Analytics *(US-11 to US-15)*

The **currency rule** applies to every analytics endpoint:
- A group within **one country** reports in that country's local currency, plus `*Usd` equivalents.
- A group that **spans countries** reports in **USD only**.

Each response's `currency` field states which one applies.

The **stats object** is reused by all endpoints:
```json
{ "headcount": 1243, "min": 41000, "p25": 58200, "median": 66500, "p75": 78000, "max": 162000, "average": 69871, "total": 86849653 }
```

### `GET /api/analytics/overview` *(US-11)*
```json
{
  "currency": "USD",
  "headcount": 9512,
  "totalPayroll": 812345600,
  "averageSalary": 85403,
  "medianSalary": 72100,
  "headcountByCountry":    [ { "code": "US", "name": "United States", "headcount": 3010 } ],
  "headcountByDepartment": [ { "id": 1, "name": "Engineering", "headcount": 3120 } ],
  "fxRatesAsOf": "2026-01-01"
}
```

### `GET /api/analytics/by-country` *(US-12)*
```json
[
  {
    "country": { "code": "DE", "name": "Germany" },
    "currency": "EUR",
    "stats":    { "headcount": 1243, "min": 41000, "p25": 58200, "median": 66500, "p75": 78000, "max": 162000, "average": 69871, "total": 86849653 },
    "statsUsd": { "headcount": 1243, "min": 44485, "median": 72153, "max": 175770, "average": 75810, "total": 94231874, "p25": 63147, "p75": 84630 }
  }
]
```

### `GET /api/analytics/by-department?country=DE` *(US-13)*
`country` is optional. With it, figures are in local currency; without it, in USD.
```json
{
  "currency": "EUR",
  "country": "DE",
  "rows": [ { "department": { "id": 1, "name": "Engineering" }, "stats": { "headcount": 410, "min": 52000, "p25": 66000, "median": 76000, "p75": 92000, "max": 162000, "average": 79800, "total": 32718000 } } ]
}
```

### `GET /api/analytics/pay-bands?jobTitleId=1&jobLevel=L3&country=DE` *(US-14)*
All three parameters are optional. Rows are grouped by title × level. As above, the currency is local when `country` is given and USD otherwise.
```json
{
  "currency": "EUR",
  "country": "DE",
  "rows": [
    { "jobTitle": { "id": 1, "name": "Software Engineer" }, "jobLevel": "L3",
      "stats": { "headcount": 96, "min": 68000, "p25": 74000, "median": 78500, "p75": 84000, "max": 96000, "average": 79100, "total": 7593600 } }
  ]
}
```
Bands with fewer than 3 people return `"lowSample": true` so that the UI can flag them. A median of 1–2 people is not a reliable band.

### `GET /api/analytics/recent-changes?days=30&page=0&size=25` *(US-15)*
`days` is 1–365 (default 30). Changes are selected by `effectiveDate`. `NEW_HIRE` and `IMPORT` rows are excluded, because a starting or migrated salary is not a raise.
```json
{
  "days": 30,
  "summary": {
    "changeCount": 412,
    "averageChangePercent": 6.4,
    "byReason": [ { "reason": "ANNUAL_REVIEW", "count": 350, "averageChangePercent": 5.1 } ]
  },
  "changes": {
    "content": [
      { "employeeId": 42, "employeeCode": "EMP-000042", "employeeName": "Lena Fischer",
        "country": "DE", "currency": "EUR",
        "previousAmount": 72000, "amount": 78000, "changePercent": 8.33,
        "reason": "ANNUAL_REVIEW", "effectiveDate": "2026-04-01",
        "changedBy": "hr.manager", "changedAt": "2026-04-01T10:12:44Z" }
    ],
    "page": 0, "size": 25, "totalElements": 412, "totalPages": 17
  }
}
```

---

## 9. Endpoint summary

| Method | Path | Story |
|---|---|---|
| GET | `/api/auth/csrf` | US-18 |
| POST | `/api/auth/login` | US-18 |
| POST | `/api/auth/logout` | US-19 |
| GET | `/api/auth/me` | US-18 |
| GET | `/api/reference` | All forms and filters |
| GET | `/api/employees` | US-01, 02, 03 |
| GET | `/api/employees/{id}` | US-04 |
| POST | `/api/employees` | US-05 |
| PUT | `/api/employees/{id}` | US-06 |
| POST | `/api/employees/{id}/deactivate` | US-07 |
| GET | `/api/employees/export` | US-16 |
| GET | `/api/employees/import/template` | US-21 |
| POST | `/api/employees/import/preview` | US-21 |
| POST | `/api/employees/import` | US-21 |
| GET | `/api/employees/{id}/salaries` | US-09 |
| POST | `/api/employees/{id}/salaries` | US-08 |
| GET | `/api/employees/{id}/audit` | US-10 |
| GET | `/api/analytics/overview` | US-11 |
| GET | `/api/analytics/by-country` | US-12 |
| GET | `/api/analytics/by-department` | US-13 |
| GET | `/api/analytics/pay-bands` | US-14 |
| GET | `/api/analytics/recent-changes` | US-15 |
