# ACME Salary Management

A web app for an HR team to manage salaries for 10,000 employees across several countries, replacing Excel
workbooks: an employee directory, salary changes with a full history and audit trail, pay insights, CSV import
of existing data, and sign-in with two roles.

**Live demo:** _(link added after deployment)_ · Sign-in details are shared separately; none are in this repository.

## What it does

| Area | Highlights |
|---|---|
| **Employee directory** | Search, combinable filters, sorting and paging done on the server. Salaries in local currency or converted to USD. Filters are kept in the address, so a filtered list can be shared |
| **Salaries** | Every change needs a reason and a confirmation step showing old vs new pay and the % change. History is append-only, and the database refuses edits to it |
| **Audit log** | Who changed what and when, recorded under the signed-in user |
| **Pay insights** | By country, department and job title: lowest, typical range, median, highest, average, plus range bars. Statistics never mix currencies; groups that span countries are converted to USD |
| **CSV import** | Download a template, check the file (every problem listed with its spreadsheet row), then import all rows in one transaction, or nothing |
| **Sign-in and users** | Session cookie with CSRF protection and a 30-minute idle timeout. A super admin adds, disables and resets HR managers |

## Stack

Java 17 · Spring Boot 4 · SQLite (Flyway migrations, JPA) · Angular 21 · PrimeNG 21 · Chart.js · Docker

How it fits together, and why: [`docs/architecture.md`](docs/architecture.md).

## Run it

### With Docker (everything in one container)

```bash
docker build -t acme-salary .
docker run -p 8080:8080 -e APP_ADMIN_PASSWORD='choose-a-password' acme-salary
```

Open http://localhost:8080 and sign in as `admin` with that password. On first start, the database is filled
with 10,000 realistic demo employees.

### For development (backend and frontend separately)

```bash
# Backend: http://localhost:8080 (use PORT=8081 if 8080 is taken)
cd backend
APP_ADMIN_PASSWORD='choose-a-password' ./mvnw spring-boot:run

# Frontend: http://localhost:4200, with /api forwarded to the backend
cd frontend
nvm use                                         # Node 24.22.3, pinned in .nvmrc
npm ci
API_TARGET=http://localhost:8080 npm start
```

**First sign-in:** the first super admin is created at start-up when none exists, as `admin` (or
`APP_ADMIN_USERNAME`), with the password from `APP_ADMIN_PASSWORD`. If no password is set, a random one is written
to the backend's log once. Usernames are case-sensitive.

**Start over:** stop the backend, delete `backend/acme.db` (and `acme.db-wal`, `acme.db-shm`), and start it again.

### Tests

```bash
cd backend && ./mvnw test                       # unit and integration tests, on temporary SQLite files
cd frontend && npx ng test --watch=false        # component tests, with the API faked
```

## Settings

All optional except the admin password in production.

| Variable | Default | What it does |
|---|---|---|
| `APP_ADMIN_PASSWORD` | _(generated and logged)_ | Password of the first super admin, used only when no super admin exists |
| `APP_ADMIN_USERNAME` | `admin` | Username of the first super admin |
| `APP_ADMIN_DISPLAY_NAME` | `Super admin` | Name shown in the app |
| `PORT` | `8080` | Port the server listens on |
| `APP_DB_PATH` | `acme.db` | The SQLite file (`/app/data/acme.db` in Docker) |
| `APP_SEED_ENABLED` | `true` | Fill an empty database with demo employees on start-up |
| `APP_SEED_EMPLOYEES` | `10000` | How many demo employees |
| `APP_SEED_SAMPLE_CSV` | _(none)_ | Also write a sample import file, e.g. `samples/employees-sample.csv` |
| `APP_COOKIE_SECURE` | `false` | Send cookies only over HTTPS. Set `true` in production |
| `SERVER_FORWARD_HEADERS_STRATEGY` | _(none)_ | Set `framework` behind an HTTPS proxy (such as Render), so cookies are marked secure |
| `API_TARGET` | `http://localhost:8080` | Frontend dev server only: where `/api` is forwarded |

A ready-made test file for the import: [`samples/employees-sample-50.csv`](samples/employees-sample-50.csv).

## Deployment

The app is deployed as one Docker container on Render's free plan, using the Blueprint in
[`render.yaml`](render.yaml): in Render, **New → Blueprint**, pick this repository, and enter `APP_ADMIN_PASSWORD`
when asked.

**Free plan trade-offs:**
- The service sleeps after 15 minutes without visitors. The first visit after that takes about a minute and a half
  while it starts; after that it is quick.
- Its disk is wiped on every restart and redeploy, so the **demo data resets**: the 10,000 employees are recreated
  on each start and the super admin comes back from `APP_ADMIN_PASSWORD`. Anything added in the meantime (users,
  imports, salary changes) is lost. A paid plan with a persistent disk, or a small VM, would keep it.

## Documentation

| Doc | Contents |
|---|---|
| [`docs/requirements.md`](docs/requirements.md) | Goal, scope, key decisions, what is out of scope |
| [`docs/userstory.md`](docs/userstory.md) | User stories with acceptance criteria |
| [`docs/architecture.md`](docs/architecture.md) | System design, flows, and the reasons behind each decision |
| [`docs/database-schema.md`](docs/database-schema.md) | Tables, constraints, indexes |
| [`docs/api-contracts.md`](docs/api-contracts.md) | Every endpoint with examples and error codes |
| [`docs/ui-design.md`](docs/ui-design.md) | Screens and behaviour |
| [`docs/ai-usage.md`](docs/ai-usage.md) | How AI tools were used, and what I changed or rejected |
