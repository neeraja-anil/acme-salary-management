-- Two roles (US-18, US-22): SUPER_ADMIN manages HR users and can do everything an HR manager can;
-- HR_MANAGER uses the salary app. SQLite can't change a CHECK constraint in place, so the table is
-- rebuilt with the same columns. Users are created at runtime, so it is normally empty here.

CREATE TABLE app_user_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    display_name  TEXT    NOT NULL,
    role          TEXT    NOT NULL CHECK (role IN ('SUPER_ADMIN', 'HR_MANAGER')),
    enabled       INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at    TEXT    NOT NULL
);

INSERT INTO app_user_new (id, username, password_hash, display_name, role, enabled, created_at)
SELECT id, username, password_hash, display_name, role, enabled, created_at FROM app_user;

DROP TABLE app_user;
ALTER TABLE app_user_new RENAME TO app_user;
