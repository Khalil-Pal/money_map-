-- Jeib local database (SQLite), first version.
-- Rules (see CLAUDE.md): ids are client-generated UUIDs stored as TEXT; money is INTEGER minor units
-- (cents), never REAL; every synced table has created_at / updated_at (ISO-8601 UTC), deleted
-- (soft delete, 0/1) and version, so sync (v2.0) can use last-write-wins.

CREATE TABLE accounts (
    id              TEXT PRIMARY KEY,
    name            TEXT    NOT NULL,
    currency        TEXT    NOT NULL DEFAULT 'USD',
    initial_balance INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL,
    deleted         INTEGER NOT NULL DEFAULT 0,
    version         INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE categories (
    id          TEXT PRIMARY KEY,
    name        TEXT    NOT NULL,
    kind        TEXT    NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    color_index INTEGER NOT NULL CHECK (color_index BETWEEN 0 AND 13), -- chart palette, jeib-color-system.md §5
    icon        TEXT,
    built_in    INTEGER NOT NULL DEFAULT 0,                           -- 1 = "Other" / "Other income": can't be deleted
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL,
    deleted     INTEGER NOT NULL DEFAULT 0,
    version     INTEGER NOT NULL DEFAULT 1
);

-- One live category per name and kind, ignoring case ("Food" and "food" clash).
CREATE UNIQUE INDEX ux_categories_kind_name ON categories (kind, name COLLATE NOCASE) WHERE deleted = 0;

CREATE TABLE transactions (
    id          TEXT PRIMARY KEY,
    account_id  TEXT REFERENCES accounts (id),
    category_id TEXT    NOT NULL REFERENCES categories (id),
    type        TEXT    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount      INTEGER NOT NULL CHECK (amount > 0),                  -- minor units
    currency    TEXT    NOT NULL,
    occurred_on TEXT    NOT NULL,                                     -- date, YYYY-MM-DD
    note        TEXT,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL,
    deleted     INTEGER NOT NULL DEFAULT 0,
    version     INTEGER NOT NULL DEFAULT 1,
    sync_state  TEXT    NOT NULL DEFAULT 'PENDING' CHECK (sync_state IN ('PENDING', 'SYNCED'))
);

CREATE INDEX idx_tx_date ON transactions (occurred_on);
CREATE INDEX idx_tx_category ON transactions (category_id, occurred_on);

CREATE TABLE budgets (
    id           TEXT PRIMARY KEY,
    category_id  TEXT REFERENCES categories (id),                     -- NULL = overall monthly limit
    month        TEXT,                                                -- 'YYYY-MM', NULL = default for every month
    limit_amount INTEGER NOT NULL CHECK (limit_amount > 0),
    created_at   TEXT    NOT NULL,
    updated_at   TEXT    NOT NULL,
    deleted      INTEGER NOT NULL DEFAULT 0,
    version      INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE recurring_payments (
    id           TEXT PRIMARY KEY,
    category_id  TEXT    NOT NULL REFERENCES categories (id),
    amount       INTEGER NOT NULL CHECK (amount > 0),
    day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    note         TEXT,
    active       INTEGER NOT NULL DEFAULT 1,
    created_at   TEXT    NOT NULL,
    updated_at   TEXT    NOT NULL,
    deleted      INTEGER NOT NULL DEFAULT 0,
    version      INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE sync_state (
    key   TEXT PRIMARY KEY,                                           -- e.g. 'last_pull_token', 'device_id'
    value TEXT NOT NULL
);

CREATE TABLE settings (
    key   TEXT PRIMARY KEY,                                           -- e.g. 'currency', 'theme', 'warning_threshold'
    value TEXT NOT NULL
);
