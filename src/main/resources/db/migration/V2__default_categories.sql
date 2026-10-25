-- Default categories. Each one has its own palette color (jeib-color-system.md §5, assignment rules);
-- color_index is 0-based. "Other" and "Other income" are built in: the fallback for quick-add and
-- CSV import rows that match no category, so they can't be deleted.

INSERT INTO categories (id, name, kind, color_index, built_in, created_at, updated_at)
SELECT id, name, kind, color_index, built_in,
       strftime('%Y-%m-%dT%H:%M:%SZ', 'now'), strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
FROM (
    SELECT 'a87a6066-c95c-48d4-b8c7-849a88bcab1f' AS id, 'Food' AS name, 'EXPENSE' AS kind, 0 AS color_index, 0 AS built_in
    UNION ALL SELECT 'baa5872b-84d2-4d48-b6f0-a5bd9051e341', 'Rent',          'EXPENSE', 1,  0
    UNION ALL SELECT '6259165f-d796-48e0-a505-3c861b02e114', 'Shopping',      'EXPENSE', 2,  0
    UNION ALL SELECT 'f39f7642-42bd-45e0-bde0-639e1e3a090f', 'Transport',     'EXPENSE', 3,  0
    UNION ALL SELECT '816015e5-df17-4751-bafa-48f3b4f3185b', 'Entertainment', 'EXPENSE', 4,  0
    UNION ALL SELECT '3be8525e-82ad-49ae-b0dc-19b789ff2f6a', 'Subscriptions', 'EXPENSE', 5,  0
    UNION ALL SELECT '85e8c087-43da-4b4f-8800-5b7afb5a2592', 'Other',         'EXPENSE', 6,  1
    UNION ALL SELECT '8ef40db2-169b-41ad-80eb-55312f02c840', 'Health',        'EXPENSE', 7,  0
    UNION ALL SELECT 'ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb', 'Salary',        'INCOME',  8,  0
    UNION ALL SELECT 'bc80ba0f-b779-4321-96f4-aeada52bef68', 'Freelance',     'INCOME',  9,  0
    UNION ALL SELECT 'd9417abf-d031-43ef-bf00-cf67abf03e24', 'Other income',  'INCOME',  13, 1
);
