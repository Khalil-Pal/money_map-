ALTER TABLE recurring_payments ADD COLUMN name TEXT;
ALTER TABLE recurring_payments ADD COLUMN currency TEXT CHECK (currency IN ('USD', 'ILS', 'RUB'));

CREATE TABLE savings_goals (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    target_amount INTEGER NOT NULL CHECK (target_amount > 0),
    saved_amount INTEGER NOT NULL DEFAULT 0 CHECK (saved_amount >= 0),
    currency TEXT NOT NULL CHECK (currency IN ('USD', 'ILS', 'RUB')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 1
);
