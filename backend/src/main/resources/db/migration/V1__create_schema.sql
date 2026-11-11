-- ACME Salary Management: initial schema.
-- See docs/database-schema.md for the reasoning behind each table and index.
-- Conventions: dates are ISO-8601 TEXT ('2026-04-01'), timestamps ISO-8601 UTC TEXT
-- ('2026-04-01T10:12:44Z'), money is INTEGER in whole currency units.

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------
CREATE TABLE fx_rate (
    currency_code TEXT PRIMARY KEY CHECK (length(currency_code) = 3),
    rate_to_usd   TEXT NOT NULL,          -- decimal string, parsed as BigDecimal to avoid float drift
    as_of_date    TEXT NOT NULL
);

CREATE TABLE country (
    code          TEXT PRIMARY KEY CHECK (length(code) = 2),
    name          TEXT NOT NULL UNIQUE,
    currency_code TEXT NOT NULL REFERENCES fx_rate (currency_code)
);

CREATE TABLE department (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE job_title (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL COLLATE NOCASE,
    department_id INTEGER NOT NULL REFERENCES department (id),
    UNIQUE (department_id, name)
);

-- ---------------------------------------------------------------------------
-- Employees
-- ---------------------------------------------------------------------------
CREATE TABLE employee (
    id                            INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_code                 TEXT    NOT NULL UNIQUE,
    first_name                    TEXT    NOT NULL CHECK (length(first_name) BETWEEN 1 AND 100),
    last_name                     TEXT    NOT NULL CHECK (length(last_name) BETWEEN 1 AND 100),
    email                         TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    country_code                  TEXT    NOT NULL REFERENCES country (code),
    department_id                 INTEGER NOT NULL REFERENCES department (id),
    job_title_id                  INTEGER NOT NULL REFERENCES job_title (id),
    job_level                     TEXT    NOT NULL CHECK (job_level IN ('L1', 'L2', 'L3', 'L4', 'L5')),
    hire_date                     TEXT    NOT NULL,
    status                        TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TERMINATED')),
    termination_date              TEXT,
    -- Denormalised copy of the latest salary_history row, kept in sync in the same transaction.
    current_salary_amount         INTEGER NOT NULL CHECK (current_salary_amount > 0),
    current_salary_effective_date TEXT    NOT NULL,
    version                       INTEGER NOT NULL DEFAULT 0,
    created_at                    TEXT    NOT NULL,
    updated_at                    TEXT    NOT NULL,
    CHECK ((status = 'TERMINATED') = (termination_date IS NOT NULL))
);

-- ---------------------------------------------------------------------------
-- Salary history (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE salary_history (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id     INTEGER NOT NULL REFERENCES employee (id),
    amount          INTEGER NOT NULL CHECK (amount > 0),
    currency_code   TEXT    NOT NULL,
    previous_amount INTEGER CHECK (previous_amount IS NULL OR previous_amount > 0),
    effective_date  TEXT    NOT NULL,
    reason          TEXT    NOT NULL CHECK (reason IN ('NEW_HIRE', 'IMPORT', 'PROMOTION', 'ANNUAL_REVIEW',
                                                      'MARKET_ADJUSTMENT', 'CORRECTION', 'OTHER')),
    note            TEXT    CHECK (note IS NULL OR length(note) <= 500),
    changed_by      TEXT    NOT NULL,
    changed_at      TEXT    NOT NULL
);

CREATE TRIGGER salary_history_no_update BEFORE UPDATE ON salary_history
BEGIN
    SELECT RAISE(ABORT, 'salary_history is append-only');
END;

CREATE TRIGGER salary_history_no_delete BEFORE DELETE ON salary_history
BEGIN
    SELECT RAISE(ABORT, 'salary_history is append-only');
END;

-- ---------------------------------------------------------------------------
-- Audit log (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type  TEXT    NOT NULL CHECK (entity_type IN ('EMPLOYEE', 'SALARY')),
    entity_id    INTEGER NOT NULL,
    employee_id  INTEGER NOT NULL REFERENCES employee (id),
    action       TEXT    NOT NULL CHECK (action IN ('CREATE', 'IMPORT', 'UPDATE', 'DEACTIVATE', 'SALARY_CHANGE')),
    changes      TEXT    NOT NULL,          -- JSON: {"field": {"from": ..., "to": ...}}
    performed_by TEXT    NOT NULL,
    performed_at TEXT    NOT NULL
);

CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'audit_log is append-only');
END;

CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'audit_log is append-only');
END;

-- ---------------------------------------------------------------------------
-- Application users
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    display_name  TEXT    NOT NULL,
    role          TEXT    NOT NULL CHECK (role IN ('HR_MANAGER')),
    enabled       INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at    TEXT    NOT NULL
);

-- ---------------------------------------------------------------------------
-- Indexes (see docs/database-schema.md section 4)
-- ---------------------------------------------------------------------------
CREATE INDEX idx_employee_status_country_dept ON employee (status, country_code, department_id);
CREATE INDEX idx_employee_title_level         ON employee (job_title_id, job_level);
CREATE INDEX idx_employee_name                ON employee (last_name, first_name);
CREATE INDEX idx_employee_salary              ON employee (current_salary_amount);
CREATE INDEX idx_employee_hire_date           ON employee (hire_date);
CREATE INDEX idx_salary_history_employee      ON salary_history (employee_id, effective_date DESC, id DESC);
CREATE INDEX idx_salary_history_effective     ON salary_history (effective_date);
CREATE INDEX idx_audit_log_employee           ON audit_log (employee_id, performed_at DESC);
