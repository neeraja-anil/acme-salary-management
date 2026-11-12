# Requirements: ACME Salary Management

> One-page requirements document. The detailed stories and acceptance criteria are in [`userstory.md`](./userstory.md) (IDs `US-xx`).

## 1. Goal
ACME's HR team manages salaries for **10,000 employees across several countries** in Excel workbooks. That is slow, error-prone and has no audit trail. We will replace the workbooks with a web application in which the **HR Manager** can:
1. **Manage** employee and salary records with validation and a full change history.
2. **Answer questions about pay** (for example, "What is the median salary by country?" or "What do we pay a Senior Engineer in Germany?") in seconds.

**Success:** the HR Manager can move the existing Excel data into the system with one CSV upload, find any employee, change their salary with a recorded reason, and answer the common pay questions listed below without exporting to Excel.

## 2. Questions the product must answer
| Question | Feature |
|---|---|
| How many people do we employ, where, and in which departments? | Dashboard (US-11) |
| What is our total payroll, and what are the average and median salaries? | Dashboard (US-11) |
| How does pay compare across countries and across departments? | Country and department analytics (US-12, US-13) |
| What is the pay range for a given role and level in a given country? | Pay bands (US-14) |
| Who got a raise recently, and how big were the raises? | Recent changes (US-15) |
| Who changed this salary, when, and why? | Salary history and audit trail (US-09, US-10) |
| How do I get our existing Excel data into the system? | CSV import (US-21) |

## 3. In scope
- **Employee directory (Must):** server-side pagination, sorting, search, and combinable filters (country, department, job title, status). Create, view and edit employees; deactivate instead of deleting. *(US-01 to US-07)*
- **Salary management (Must):** salary changes are recorded with an effective date and a reason, salary history is append-only, and each salary change and its audit entry are written in one transaction. *(US-08 to US-10)*
- **Pay analytics (Must/Should):** org overview, country and department breakdowns (min, max, average, median), pay bands by title and level, and recent changes. *(US-11 to US-15)*
- **CSV import (Must):** the HR manager downloads a template, uploads a CSV exported from their Excel workbooks, previews row-level errors, and imports all rows in one transaction. *(US-21)*
- **Seed data (Must):** a deterministic script that creates 10,000 employees with realistic pay and salary history, for demos, tests and performance checks. It can also write a sample CSV in the import format. *(US-17)*
- **Sign-in and users (Must):** username and password login, a server-side session with a 30-minute idle timeout, and sign-out. The signed-in user is recorded on every change. Two roles: a super admin, who also adds, disables and resets HR managers, and HR managers. *(US-18 to US-20, US-22)*
- **Delivery:** a Spring Boot REST API, an Angular UI, SQLite, unit and integration tests, a deployed instance and a demo video.

## 4. Key product and design decisions
- **Multi-currency:** each salary is stored in the **employee's local currency**, which is set by their country. Per-country analytics use local currency. Org-wide totals are converted to **USD** using a **static, versioned FX rate table**, and the rates are shown in the UI. This keeps the figures reproducible and needs no external service.
- **Salary means annual gross base pay.** Bonus, equity and allowances are excluded (see §6).
- **Salary is append-only:** each change inserts a new `salary_history` row, and the current salary is the latest row by effective date. Salary is therefore never edited in place, so the history cannot be lost.
- **Soft delete:** terminated employees remain in the database for audit and are excluded from analytics.
- **Lookup tables for country, department and job title:** these keep filters consistent and avoid free-text variants such as "Engg" vs "Engineering", a common problem in the Excel files.
- **Seed and import are separate on purpose:** the seed is for developers and reviewers, and needs multi-year salary history to make the history and analytics screens meaningful. The import is for HR's real data, which arrives as one row per employee with the current salary. Both use the same column structure and the same validation rules as "Add employee".
- **Import is all-or-nothing:** the file is saved only if every row is valid. A half-imported migration file is harder to fix than a rejected one.
- **Session-based login, not JWT:** the Angular app is served from the same origin as the API, so a server-side session in an HttpOnly cookie, plus Spring Security's CSRF protection, is simpler and safer than JWTs held in browser storage. Sessions can also be revoked on sign-out.

## 5. Non-functional requirements
- **Performance (10,000 employees):** directory queries take under 300 ms (p95) and analytics endpoints under 1 s. Indexes cover the filter and sort columns.
- **Data integrity:** validation in both the API and the UI, unique constraints on email and employee code, and transactions around salary changes.
- **Quality:** fast, deterministic unit tests for the core rules (validation, salary change, aggregations), plus integration tests for the API.
- **Usability:** the main tasks (find an employee, update a salary, answer a pay question) each take three clicks or fewer.

## 6. Deliberately out of scope (and why)
| Left out | Why |
|---|---|
| **SSO, user management, multiple roles, password reset** | The brief defines a single persona, so one HR Manager role behind a simple login is enough. A `users` table with a `role` column means corporate SSO (OIDC) and further roles, such as a read-only viewer, can be added later without changing the schema. |
| **Payroll processing** (tax, deductions, payslips, payouts) | This is a separate, regulated domain. The goal is to *manage* salary data and *analyse* pay, not to run payroll. |
| **Live FX rates** | They would make figures non-reproducible and add an external dependency. A static rate table is enough for comparisons, and the rates can be updated. |
| **Future-dated salary changes and relocations** | Scheduled raises would need a job to apply them on the effective date. Moving an employee to another country would change their salary currency. Both add rules that the core use cases don't need, so v1 allows only changes effective today or earlier, and country is fixed after creation. |
| **Bonus, equity, benefits** | Including them would multiply the data model. Base pay answers most of the questions in §2. |
| **Approval workflows** (for example, manager approves a raise) | That needs several personas and notifications, which goes beyond a single HR Manager user. The *reason* field and the audit trail cover accountability for now. |
| **`.xlsx` upload, and bulk updates by file** | Excel saves CSV natively, so CSV covers the migration with simpler, safer parsing. Updating *existing* employees from a file (bulk edit) would need matching and conflict rules. v1 imports new employees only. |
| **Importing past salary history** | HR's spreadsheets hold current salaries. Importing history would need a multi-row-per-employee format and ordering rules, so history starts from the import date. |
| **Demographic or pay-equity analytics** (for example, gender pay gap) | It requires sensitive personal data with consent and legal review. The schema can be extended once those are in place. |
| **Multiple organisations, internationalisation, mobile apps** | They are not needed for one organisation's HR team. The UI is responsive but desktop-first. |

## 7. Assumptions
- Each employee has one country, which sets their currency, and one current department, job title and level.
- About 10,000 employees with a single HR user means SQLite is sufficient (one writer at a time is acceptable). With JPA, moving to PostgreSQL later only requires changing configuration.
