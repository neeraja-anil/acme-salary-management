# AI Usage Log

This log records how AI tools were used to build this project: what I asked for, what the AI produced, and what I accepted, changed or rejected. The aim is to show that AI sped up the work while I kept the decisions and checked the output.

## Tools
| Tool | Used for |
|---|---|
| Claude Code (Claude Opus 5.5), in the terminal | Planning, drafting docs, generating and refactoring code, writing tests, reviewing changes |

## How I work with AI
1. **I decide, AI drafts.** Scope, stack, trade-offs and priorities are my decisions. The AI proposes options and writes first drafts.
2. **Docs before code.** Requirements, user stories, schema and API contracts are written and reviewed before any implementation, so generated code has a clear spec to follow.
3. **Small, reviewable steps.** I work one phase at a time, with no automatic commits. I read each change before it is committed.
4. **Verification beats trust.** Generated code counts as done only when it compiles, tests pass, and I have checked it against the acceptance criteria in `userstory.md`.
5. **Record the corrections.** Every place where I overrode or fixed the AI's output is logged below.

---

## Session log

### Session 1: Requirements and user stories (Phase 1, step 1)
**Prompt (summary):** I pasted the assignment brief and my six-phase plan and asked the AI to plan first, create `docs/requirements.md` and `docs/userstory.md`, and not commit anything.

**AI produced:**
- 17 user stories (US-01 to US-17) in four epics, with acceptance criteria and MoSCoW priorities.
- A one-page requirements doc covering the goal, the pay questions the product must answer, scope, design decisions (local currency with a static USD conversion, append-only salary history, soft delete, lookup tables), non-functional targets, and what is deliberately out of scope.

**My changes and decisions:**
- Added *department* as a sortable column in US-01.
- **Frontend: Angular instead of the AI's React suggestion.** The company uses Angular, so I chose to build in it.
- **Backend:** Java 17 with Spring Boot.
- **Login: brought into scope.** The AI's first draft had left it out because there is only one persona. Because the app will be publicly deployed with salary data, I asked for it. This added US-18 to US-20 (sign in, sign out and session expiry, attributing changes to the signed-in user).

**Accepted AI design choice (after review):** session cookies instead of JWT. The app and API are served from the same origin, so an HttpOnly session cookie with CSRF protection avoids keeping tokens in browser storage, and sessions can be revoked on sign-out.

---

### Session 2: Architecture, database schema, API contracts (Phase 1, steps 2–4)
**Prompt (summary):** I asked the AI to write the architecture diagram, database schema and API contracts based on the approved requirements and user stories, without committing.

**AI produced:**
- `architecture.md`: a Mermaid diagram of the single container, the repository layout, backend layering, a sequence diagram of a salary change, the authentication design, the analytics approach, the seeding approach, the testing strategy, and a table of architecture decisions.
- `database-schema.md`: an ER diagram, table definitions with constraints, append-only triggers, indexes, and the expected data volume.
- `ui-design.md`: routes, a navigation diagram, text sketches of every screen, shared error and loading behaviour, formatting, accessibility, and how screens map to Angular code.
- `api-contracts.md`: conventions, an RFC 7807 error format, and every endpoint with request and response examples mapped to user stories.
- A `.gitignore`.

**Design rules the AI added, which I reviewed and accepted** (also added to `userstory.md` and `requirements.md`):
- No future-dated salary changes, and country cannot be changed after creation. Both are recorded as out of scope for v1.
- Optimistic locking (`version`) on employee edits and salary changes.
- Statistics are never mixed across currencies: groups spanning several countries are reported in USD.

**What I changed / rejected and why:**
- **Brought CSV import into scope (US-21).** The AI had listed bulk import as out of scope, with the seed script standing in for the initial load. I pointed out that the seed is demo data, while HR's real need is to move their existing Excel data into the system. We kept both:
  - **Seed:** required by the brief, and used for demos, tests and performance checks.
  - **CSV upload for HR:** uses the same column structure, with a preview step and an all-or-nothing import.
- **Simplified salary confirmation.** The AI had designed a calculated change % with forced confirmation only above ±30%. I replaced it with a confirmation step on every salary change, which is simpler, has no threshold to justify, and treats every pay change as deliberate. The `confirmLargeChange` API field and its error code were removed.
- **Fixed a Mermaid parse error** in the salary-change sequence diagram. A `;` in a message is a line break in Mermaid.
- **Questioned why Spring Boot serves the UI.** The AI explained that the built Angular app is just static files, which Spring Boot can serve next to the API. That puts the UI and the API on one origin, so there's no CORS setup and the login cookie can use the strictest settings. In development, the Angular dev server's proxy gives the same effect. I was satisfied, and kept the existing note in `architecture.md`.

---

### Session 3: Backend foundation (Phase 2)
**Prompt (summary):** "Start Phase 2." The AI built the Spring Boot backend from the Phase 1 docs and checked it against the API contract with curl. Nothing was committed.

**AI produced:**
- A Spring Boot 4.1 project, generated from Spring Initializr so the starter names match that version. Java 17, SQLite, JPA, Flyway, validation and actuator.
- Flyway migrations `V1` (the full schema from `database-schema.md`, including append-only triggers and indexes) and `V2` (reference data).
- The employee directory API: search with combinable filters, allow-listed sorting and server-side paging; get, create, update and deactivate.
- Error handling: RFC 7807 problem responses with the codes from `api-contracts.md`.

**Issues found while verifying, and how they were fixed:**
- **Hibernate rejected SQLite `INTEGER` ids for Java `Long` fields** (`ddl-auto=validate`). Rather than turning validation off, a small dialect subclass declares SQLite's integer types equivalent. In SQLite they really are the same 64-bit type.
- **Spring's built-in errors (405, 415) were missing the `code` field,** because Spring builds that body inside the superclass. The handler now adds `code` after the superclass call.
- **API timestamps had nanoseconds while the database stored seconds.** They are now truncated in the service, so the API and the database agree.
- **Searching for `_` matched every row,** because it is a LIKE wildcard. User input is now escaped.

**Verified manually (tests come in Phase 5):** every endpoint and error code with curl; the database rows with Python's `sqlite3` module; that the triggers block UPDATE and DELETE; and that the JDBC URL enables foreign keys and WAL mode.
- **Kept login and CSV import out of Phase 3.** The AI suggested fitting both in. I kept Phase 3 to salary management; they were built later (Sessions 11 and 12).
---

### Session 4: Salary management and audit (Phase 3)
**Prompt (summary):** "Start Phase 3." The AI first asked me to commit Phase 2, so the two phases wouldn't end up mixed in one commit.

**AI produced:**
- `POST`/`GET /api/employees/{id}/salaries`: salary changes and history, with % change.
- `GET /api/employees/{id}/audit`: a paged audit trail.
- Audit entries for create, update (only the fields that changed; a no-op save writes nothing), deactivate and salary change.
- `SalaryChangePolicy`: the salary rules in a plain class, for unit testing. A custom validation constraint handles the cross-field checks (reason must be user-selectable; note required for OTHER).

**Design choices:**
- `AuditService.record` uses `Propagation.MANDATORY`, so it cannot run outside the caller's transaction.
- Audit entries store department and job title by name rather than ID, so they stay readable after a rename.

**Issue found while verifying:**
- **Two concurrent salary changes to the same employee: one got 500** (`SQLITE_BUSY`) instead of 409. In SQLite's default DEFERRED mode, both transactions read first, then raced for the single write lock, and the loser failed without waiting. The same would have hit concurrent changes to *different* employees.
- **Fix:** `transaction_mode=IMMEDIATE`, so writers queue for the lock. Lock timeouts beyond 5 s now map to 503 `DATABASE_BUSY` (safe to retry) instead of 500.
- **Re-verified:** 5/5 rounds of concurrent same-employee changes gave 201 + 409, concurrent changes to different employees all gave 201, and current salary, latest history row and audit count agreed for every employee.

**Verified manually (tests come in Phase 5):** every salary rule and error code with curl, the audit contents for each action, and that rejected requests leave no partial writes.

**What I changed / rejected and why:** reviewing this phase led to one change: removing the `IMPORT` salary reason (Session 5). Nothing else was changed.

---

### Session 5: Removing the `IMPORT` salary reason (Phase 3 review)
**Prompt (summary):** While walking through the Phase 3 changes, I questioned why `IMPORT` was a salary change reason. The AI first defended it: there's no reason column in the CSV, `NEW_HIRE` would be wrong for a salary set after the hire date, and analytics must not count imports as raises. I pushed back that mapping "imported" to a *reason* isn't right, and the AI agreed that it mixes two facts: *why* the pay is what it is, and *how* the row got into the system.

**AI proposed:** remove the `IMPORT` reason and add a separate `source` column (`APP`/`IMPORT`) on `salary_history`.

**What I changed / rejected and why:** I rejected the `source` column. The `IMPORT` audit entry already records where the employee came from. I kept the rest:
- `IMPORT` removed from the reasons. `reason` can now be NULL ("not recorded"), but only on an employee's first salary: `CHECK (reason IS NOT NULL OR previous_amount IS NULL)`.
- New optional CSV column `salary_reason`. If the salary is still the starting salary, the reason is `NEW_HIRE`. If it was set later, the reason is the value in the file, or "not recorded" if that's blank.
- "Recent changes" analytics now counts only rows with a previous amount. That excludes every first salary without a special reason.
- V1 migration edited in place, because nothing has been released. Local databases have to be recreated.

**How I verified it:** rebuilt and started on a fresh database. `IMPORT` is now rejected by the API (400) and by the database. `NEW_HIRE` can't be chosen (400). A NULL reason is accepted on a first row and rejected on later rows. A normal change still records the previous amount and % change.

---

### Session 6: Analytics APIs and the Angular frontend (Phase 4)
**Prompt (summary):** "Start Phase 4."

**AI produced:**
- **Backend `analytics` package:** overview, by country, by department, pay bands and recent changes. `PayStatistics` is a pure class that computes count, total, min, p25, median, p75, max and average. Percentiles use linear interpolation, like Excel's `PERCENTILE.INC`.
- **Angular 21 app with Angular Material:**
  - Dashboard: KPI cards, plus headcount charts where clicking a bar opens the filtered directory.
  - Directory: server-side search, filters, sort and paging, all kept in the URL.
  - Profile: details, salary history (with a step chart) and audit log tabs.
  - Add/edit form, with server errors shown under the matching fields and a "Discard changes?" guard.
  - Two-step change-salary dialog that always asks for confirmation, and a deactivate dialog.
  - Pay insights: four tabs, including CSS range bars for pay bands.

**Design choices:**
- **All statistics in Java from one projection query** (10k rows), instead of SQL `GROUP BY` plus Java for the median as first planned. One code path, easy to unit-test. `architecture.md` was updated.
- **Cross-country groups are converted to USD per employee before computing statistics.** A single-country group converts its finished statistics.
- **Angular 21, not 22:** Angular 22 needs Node 24.15 and the machine has 24.6.
- **Stale responses can't win:** requests that depend on changing filters go through `switchMap`, which cancels the older request.

**Issues found while verifying:**
- **npm 11 crashed** (`reading 'edgesOut'`) resolving Vitest's optional peer dependencies. Generated the lockfile with npm 12. `npm ci` on npm 11 works from that lockfile.
- **Initial bundle was 866 kB**, because Chart.js was registered app-wide. Moved it to the two chart pages: 656 kB (154 kB compressed).

**How I verified it:**
- **API:** every analytics endpoint and its 400 cases with curl, on 160 employees created through the API. Overview, every country, every department (USD) and the recent-changes average were recomputed independently in Python from the database file, and all matched.
- **UI, in Chrome:**
  - Dashboard bar click → filtered directory.
  - Search, sort and filters reflected in the URL.
  - Salary dialog: "same as current" error, confirm step, toast, then history and audit updated.
  - Duplicate email shown under the Email field.
  - All four insight tabs.
  - A salary changed via the API while the dialog was open → "Changed by someone else" → Reload showed the latest data.

**Follow-up: pay bands (same session):** at my request, removed the range-bar ("candle") column from Pay bands. The 25th and 75th percentiles were only visible in the bar's tooltip, so they became table columns, and amounts are shown in full instead of the short form.

**Follow-up: table headers (same session):** I found the pay tables hard to read and asked what HR should learn from Min / 25th pct / Median / 75th pct / Max / Average. The AI explained each one with an example and what decision it supports. It also proposed a redesign (merged tabs, a "pay band finder"); I rejected that, kept the tables, and asked only for clearer headers. I first got Lowest / Typical low / Typical (median) / Typical high / Highest / Average, then asked for my own layout: **People · Lowest · Typical range (middle half) · Typical (median) · Highest · Average**, with the 25th and 75th percentiles merged into one "€66,000 – €92,000" column. Applied to all three pay tables, with the plain meaning on hover, defined once in `shared/pay-columns.ts`.

**Follow-up: table paging and spacing (same session):** at my request, no table shows more than 20 rows. The directory offers 10 or 20 per page and recent changes 20 (server-paged). Pay tables and salary history get a small shared browser-side pager (`shared/paging.ts`) that only appears above 20 rows. Cells got more padding, and tables more space below their captions. Checked in Chrome: pay bands "1 – 20 of 92", directory "1 – 20 of 150", recent changes "1 – 20 of 103".

**Follow-up: range bars from a design canvas (same session):** I shared a design canvas with 7 salary-range visualisations and asked whether designs 2 (quartile segments) and 3 (role cards) could be built. The AI read both and listed the gaps: they show one row per job title, which needs a new backend grouping; small groups divide by zero; labels overlap; there are more than 20 items. My decision: keep our tables and rows, allow each bar its own scale, and add design 2's bar as a column. The AI first started with design 3's bar style; I stopped it and asked for design 2's. Result: a shared `RangeBar` component (four shaded quarters plus a median mark, scaled per row, figures on hover) in the Country, Department and Pay bands tables, with a key above each. The figures under the bar were left out, because the columns already show them. No backend change was needed.

**Follow-up: numbers or bars, not both (same session):** I asked why the four range columns stay next to the bar, since they repeat the same figures. Now a **Numbers | Range bars** switch sits above each pay table: Numbers by default, and Range bars replaces the four columns with one wide bar that prints its figures underneath, as in design 2. Labels that would overlap are dropped in order of importance (median, lowest, highest, then the 25th and 75th percentiles). All five figures stay in the hover text. The choice is shared across the three tables.

**Follow-up: department filter and tab rename (same session):**
- **Department filter:** added a `departmentId` filter to `GET /api/analytics/pay-bands`, done in the backend like the other filters on that endpoint, with 400 for an unknown department. In the UI, a Department dropdown sits on that tab, and the Job title list follows the chosen department.
- **Rename:** the tab is now "By job title" (I asked for "By Job titles"; the AI matched the other tabs' style) and its URL key is `?tab=job-title`.
- **Verified:** with curl (93 rows → 19 for Engineering; a Sales title combined with Engineering → 0 rows; unknown department → 400) and in Chrome.

**Follow-up: always show paging (same session):** at my request, every table shows its page control even with 20 rows or fewer, so the count is always visible. Checked in Chrome: by country "1 – 8 of 8", by department "1 – 10 of 10", by job title "1 – 20 of 92", recent changes "1 – 20 of 21", salary history "1 – 2 of 2", audit log "1 – 3 of 3".

**Follow-up: switch position and row height (same session):**
- **Switch:** moved to the right edge.
- **Row height:** rows were 52px in Numbers and 76–77px in Range bars, so the table jumped on switching. All pay-table rows are now a fixed 66px (bar and labels 48px + 8px padding top and bottom + 1px border).
- **Two first-try mistakes, found by measuring in Chrome:** the job-title tab's own cell padding overrode the shared rule, and a 1px border made bar rows 65px instead of 64px. After fixing both, Chrome measured 66px everywhere.

**Follow-up: salaries in USD on the employee list (same session):** I asked for an option to show salaries in USD, disabled when the pay currency is already USD.
- **Needed a backend change, not just the UI:** the AI pointed out that sorting by salary in a USD view would still order local amounts (₹ salaries on top).
- **Backend:** list rows now include `amountUsd`. A new `sort=salaryUsd` orders by `amount × rate` with a CASE expression in the query. That's no new column and no migration, and the count query that paging runs is left unordered.
- **UI:** a "Salary in: Local currency | USD" switch, kept in the URL. Converted amounts show as "≈ $…" with the local amount on hover. USD is disabled when the country filter covers only USD-paid countries.
- **Verified:** the USD sort across all pages of 160 test employees matched an independent recalculation. In Chrome: the USD view ranks US $243,400 first and Singapore ≈ $146,562 next; filtering to the US disables USD; US + Germany enables it.

**Not done in this phase:** sign-in, CSV import and export are not built. There are no automated tests yet (Phase 5), so `ng test` fails with "no test files" until then.

**What I changed / rejected and why** (the follow-ups above have the detail):
- **Rejected the AI's pay-insights redesign** (merged tabs and a "pay band finder"). I kept the tables, asked only for clearer headers, and chose the final column layout myself.
- **Shaped the range bars:** removed the first "candle" chart, picked design 2's quartile bar over design 3's, then asked for a Numbers | Range bars switch instead of showing the figures twice, with the switch on the right and a fixed row height.
- **Asked for the table behaviour:** at most 20 rows per page with the page control always visible, a department filter on pay bands, and the tab renamed "By job title".
- **"Non-USD currencies not working."** I reported that non-USD salaries showed "≈ –" in the new USD view. The AI found my backend was still running code from before the change: it was started before the new code was compiled, so rows had no `amountUsd` and the `salaryUsd` sort was unknown. Restarting it fixed this; no code change was needed.
- **Asked whether the frontend could convert to USD itself.** It already has the rates. The AI recommended keeping the conversion on the server, and I agreed:
  - sorting by USD salary must cover every page, not just the 20 rows in the browser;
  - converting with exact decimals in one place keeps the list, the profile and the analytics in agreement.

  Live rates fetched by the browser were rejected too: the figures would change daily, differ between users, and need an API key visible in the frontend.

---

### Session 7: Moving the UI to PrimeNG, with design research first
**Context:** the Angular Material UI worked but looked very basic. I asked the AI to compare it with PrimeNG, using PrimeNG's docs, its MCP server and its AI docs.

**AI suggested:**
- **PrimeNG 21, not the latest 22.** PrimeNG 22 needs Angular 22, and it is no longer MIT: it needs a PrimeUI licence key, free only for very small companies. PrimeNG 21 is MIT and works with our Angular 21.
- **An honest caveat:** the switch means rewriting every template for little new function, so it is worth it for the look, not for features.
- **A plan:** a Material → PrimeNG mapping for every component, plus PrimeNG's own rules (theme through design tokens, CSS layers, no deprecated APIs).
- To read the docs, the AI ran the official `@primeng/mcp` server locally and used its `validate_usage` tool to check a draft table.

**Problems I identified:**
1. **It started building straight away.** After I said "move completely to PrimeNG", the AI installed packages and began rewriting screens. I stopped it: *before a full redesign, research general design guidelines to follow.* A "better look" needs agreed rules first, not just new components.
2. **I didn't want a design guidelines file in the repo.** The research was written up as `docs/design-guidelines.md` and referenced from code comments and docs. Later, while splitting the commits, I asked for it to be removed.

**Reasoning and what changed:**
- **Research first:** the AI drew on NN/g (data tables, dashboards, visual hierarchy, form errors), IBM Carbon (tables, empty states, notifications), Atlassian spacing, GitHub Primer layout, GOV.UK forms and WCAG 2.2. From that it proposed concrete rules: an 8px spacing scale, a grey page with white cards, labels above fields, errors shown when you leave a field, numbers right-aligned in tabular figures, and toasts for success but persistent messages for failures. I accepted its defaults: the Inter font, an indigo theme, labels above fields, light mode only.
- **The rules caught real problems in PrimeNG's defaults:**
  - White text on Aura's indigo-500 is 4.47:1, just under WCAG AA, so the theme uses indigo-600.
  - Aura's input border is 1.48:1 against white; the theme uses a darker slate at 3.15:1.
  - PrimeIcons 8 and `@primeuix/themes` 3 had also moved to the PrimeUI licence, so they were pinned to the last MIT versions.
- **Removing the file:** the AI deleted it (keeping a backup copy outside the repo) and removed its links from two docs and five code comments, so nothing points to a missing file.

**Final output:**
- Every screen rebuilt on PrimeNG 21: a sidebar shell, KPI tiles, card-based tables with toolbars, placeholder rows while loading, empty states, form sections, and a two-step salary dialog that shows the % change.
- `@angular/material` removed. The initial download went from 731 kB to 685 kB once the shell was lazy-loaded.

**Issues found by checking in Chrome:**
- The directory's filters wrapped onto two lines; search and the currency switch now sit on their own row.
- My legend override broke the range-bar key's layout.
- Link-style buttons were underlined.
- PrimeNG's date picker silently ignores a typed date outside its min/max. A small shared validator now reports "can't be in the future" and similar errors again.

---

### Session 8: Seed data (US-17)
**Context:** the brief asks for 10,000 realistic employees for demos, tests and performance checks.

**AI produced:**
- A deterministic generator (`Random(42)` and a fixed reference date, no `now()`).
- Weighted countries and departments, names suited to each country, managers never below L3, and about 5% leavers.
- Pay from country × department × job title × level, ±15%, with 0–3 later changes. The starting salary is worked back from today's, so current pay always fits the role.
- JDBC batch inserts in one transaction, run only when the employee table is empty.
- Audit entries written with the app's own `AuditChanges` format, so seeded history looks the same as history created in the app.
- An optional sample CSV in the import format. Its rows are extra people generated after the 10,000, so the file can be imported into a seeded database without duplicate emails.

**Trade-off I accepted:** a fixed reference date keeps every run identical, but "last 30 days" on the dashboard empties out as real time moves on. The date is a setting, so it can be moved and the database reseeded.

**How I verified it:** 9 unit tests (same seed gives identical data; history rules), plus an integration test that seeds through the real migrations so every constraint and trigger applies. A full run gave 10,000 employees, 21,507 salary rows and 22,004 audit entries in about 1 second, a 13 MB file, and plausible medians (US $101k, India ₹14.9L, UK £56k).

---

### Session 9: Sorting the pay insights tables
**Context:** I asked for the numeric columns in Pay insights to be sortable.

**AI's reasoning and what it changed:**
- **"By country" can't sort most columns.** Lowest, Typical, Highest and Average are each in that country's own currency. Its first version made them sortable, and testing showed India's ₹14,90,300 ranked above America's $101,200. Only **People** and **Typical (USD)** sort on that tab. The department and job-title tabs use one currency per view, so every numeric column sorts there.
- **Recent changes needed a backend change.** That list is paged on the server, so sorting one page in the browser would be wrong. The API now takes `sort=changePercent|effectiveDate`, computed in the query. Amounts stay unsortable because they mix currencies.

**How I verified it:** a backend test proves the largest % change on page 1 is the largest of the whole period, not just of that page. Checked in Chrome on a seeded 10,000-employee database.

---

### Session 10: Splitting the work into reviewable commits
**Context:** I had undone a draft commit, so the git index held a mix: the analytics API, the whole frontend, and the AI's later seed and sorting work, partly in the same files. I asked the AI to check the changes and stage related files for separate commits.

**AI suggested:** five commits (analytics API, seed, recent-changes sort, the whole frontend, and `ai-usage.md` last), staged one at a time. Doc files were split hunk by hunk so each commit carried only its own documentation.

**Problems I identified:**
1. **The frontend commit was far too big:** 71 files in one commit can't be reviewed. I asked for it to be broken up by screen.
2. **The commit messages were too technical.** I asked for them in simpler terms.
3. **The design guidelines file shouldn't be committed** (see Session 7).

**Reasoning and final output:**
- **Seven frontend commits:** set-up and layout, dashboard, employee list, add/edit form, profile, pay insights, then the UI design doc.
- **The difficulty:** `app.routes.ts` points at every screen, so a commit with only some screens wouldn't build. The AI wrote a small staging script that staged, for each commit, a routes file listing only the screens committed so far. My working copy was never changed.
- **Every commit was checked before I committed it:** the staged snapshot was exported to a scratch folder and built, or its backend tests run. The AI also kept a snapshot of the original index, so the regrouping could be undone exactly.
- The messages were rewritten in plain words, for example "the employee list keeps its search and filters in the page address".

---

### Session 11: Sign-in, roles and user management (US-18 to US-20, US-22)
**Context:** sign-in was designed in Phase 1 but not built. I asked the AI to compare JWT and session cookies before building it.

**AI suggested:**
- **Session cookies,** keeping the Phase 1 decision. The UI and API share one origin, and the app needs instant sign-out and a 30-minute idle timeout: sessions give both for free, while JWT would need refresh tokens and a block list (server state again). JWT would only win with other clients or several servers.
- **Then it started building** to the original spec: one HR manager account created at startup from environment variables.

**Problems I identified:**
1. **One HR account isn't enough.** I stopped the implementation: *we need a super user who can create multiple HR users, so two roles, super and HR. Plan this in a simpler way.*
2. **The super admin password was hard to find.** It was generated and printed once in the startup log. I asked how to know it, then how to set my own.
3. **Usernames shouldn't be case-insensitive.** They were saved and matched in lower case, and I asked for them to be case-sensitive.

**Reasoning and final output:**
- **Two roles:** `SUPER_ADMIN` can do everything `HR_MANAGER` can, plus manage users at `/api/users`: list, add HR users, disable or enable, reset passwords. New users are always HR managers. Super admin accounts can't be changed through the API, so the last one can't be locked out. A disabled user's open session ends on their next request: one indexed lookup per request.
- **No credentials in the repo:** the first super admin comes from `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`. If no password is set, a random one is logged once. The AI explained the steps to set my own; the admin is only created when none exists, so an existing one has to be removed first.
- **Case-sensitive usernames:** stored as typed, and sign-in must match the case. The AI added one guard I hadn't asked for and told me about: two usernames that differ only in case (`admin` / `Admin`) are refused, because the change log would show two names that look like one person. That check is in the service, with a case-insensitive unique index as a safety net (migration V4).
- **Frontend:** a login page that returns you to the page you asked for, route guards, a session-expired redirect, the signed-in user and Sign out in the sidebar, and a Users page only super admins can reach.

**Issues found while verifying:**
- **Signing in after signing out failed in Chrome:** Spring Security clears the CSRF cookie on sign-out, so the next sign-in got 403. The app now fetches a fresh CSRF token before every sign-in.
- **Enter didn't submit the login form after a failed attempt,** because the button was still disabled from the cleared password. It's no longer disabled.
- **The initial bundle went over budget** once the shell loaded with the login page. The shell is now lazy-loaded.
- **A test that passed alone failed in the full run:** spring-security-test's `csrf()` helper permanently wraps the app's CSRF token store. That test now gets a fresh context.

**How I verified it:** 13 security tests through the real filter chain: 401/403 rules, generic "Invalid credentials", disabled users signed out, password reset, protected super admins, case rules, and changes recorded under the signed-in user. In Chrome: a super admin added an HR user, the HR user signed in without seeing Users, and disabling them from another session signed them out on their next click.

---

### Session 12: CSV import (US-21)
**Context:** HR needs to load their existing Excel data. The format, preview and all-or-nothing rules were specified in Phase 1.

**AI produced:**
- **Backend:** template download, a preview that saves nothing, and an import that checks the whole file again rather than trusting the preview. It writes employees, first salary rows and `IMPORT` audit entries in batches, in one transaction, under the signed-in user.
- **Checks:** the "Add employee" rules plus the file-only ones (the currency must match the country; no email twice in the file). Every problem in a row is reported, with the row number HR sees in Excel, and at most 500 are listed. Unreadable files get a clear reason: missing columns are named, a non-UTF-8 file gets the Excel tip, and anything over 5 MB or 10,000 rows is refused.
- **Frontend:** a three-step page (Upload → Review → Done), with drag and drop, the column rules beside the upload, the problems table, a preview of the first rows, and Import disabled until every row is valid.
- **A 50-row test file** (`samples/employees-sample-50.csv`), generated with the seeder so it passes every rule and doesn't clash with seeded data.

**How I verified it:** 8 integration tests, including Excel-style row numbers across a blank line, and the seeder's sample file importing cleanly into a seeded database. In Chrome: a file with deliberate mistakes showed each problem and kept Import disabled; the 194-row sample imported, with employee codes continuing from `EMP-010001` and audit entries under the signed-in user.

---

### Session 13: Page size the user can change

**Prompt (summary):** make every table default to 10 rows per page, with a page size the user can change. Develop it in a git worktree since the csv upload feature was in progress

**AI produced:**
- One shared setting in `shared/paging.ts`: `PAGE_SIZES` (10, 20, 50) and a default of 10. The AI first kept 20 as the largest size because of my earlier "at most 20 rows" rule; I asked for 50 (the API allows up to 100).
- Every paged table now has a rows-per-page dropdown in its page control: the directory, recent changes, the pay tables, salary history, users, the audit log and the import error list. The browser-side `ClientPager` now has a size the user can change. The directory keeps a non-default size in the URL (`?size=50`). Recent changes and the audit log send the chosen size to the server, and a new size goes back to page 1.
- The audit log used to hide its page control at 10 entries or fewer. It now stays visible whenever there are more than 10 entries, so after picking 20 or 50 the user can still switch back.

**Developing in a git worktree (my choice):**
- **Why:** my CSV import work was still uncommitted in the main checkout. A worktree is a second working folder on its own branch, sharing the same repository, so the page-size change could be built without touching or mixing with that work.
- **Set-up:** a worktree at `../acme-salary-management-pagination` on branch `feat/pagination-page-size`. Its `node_modules` was linked to the main checkout's copy, so nothing had to be reinstalled. Its dev server ran on port 4300, so it didn't clash with mine on 4200.
- **"Merge the worktree to main," and why it wasn't a git merge:** while the worktree was open, I had committed the CSV import on `main`. The worktree branch had no commits of its own, because I make the commits myself. So the AI copied the worktree's changes onto `main` as uncommitted changes for me to review and commit, instead of creating a merge or a commit itself.
- **What the copy exposed:** the import page's error table, which didn't exist when the worktree was made, still used the old fixed page size and broke the build. The AI gave it the same dropdown, and the build passed on `main`.
- **Clean-up:** the worktree, its branch and its dev server were removed, leaving only `main`.
---

<!-- Template for later sessions
### Session N: <title> (Phase X)
**Prompt (summary):**
**AI produced:**
**What I changed / rejected and why:**
**How I verified it:**
-->
