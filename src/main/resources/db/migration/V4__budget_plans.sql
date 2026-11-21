CREATE TABLE budget_plans (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL CHECK (end_date >= start_date),
    limit_amount INTEGER NOT NULL CHECK (limit_amount > 0),
    currency TEXT NOT NULL CHECK (currency IN ('USD', 'ILS', 'RUB')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE budget_plan_categories (
    plan_id TEXT NOT NULL REFERENCES budget_plans (id),
    category_id TEXT NOT NULL REFERENCES categories (id),
    PRIMARY KEY (plan_id, category_id)
);

ALTER TABLE transactions ADD COLUMN budget_plan_id TEXT REFERENCES budget_plans (id);

CREATE INDEX idx_tx_budget_plan ON transactions (budget_plan_id, occurred_on) WHERE deleted = 0;
