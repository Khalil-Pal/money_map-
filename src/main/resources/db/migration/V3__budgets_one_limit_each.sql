-- At most one live limit per category and month. category_id NULL is the overall limit and month
-- NULL is the default for every month, so NULLs are folded to '' for the uniqueness check.
CREATE UNIQUE INDEX ux_budgets_category_month
    ON budgets (ifnull(category_id, ''), ifnull(month, ''))
    WHERE deleted = 0;
