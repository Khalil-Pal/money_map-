package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.model.BudgetPlan;
import com.jeib.model.DateRange;
import com.jeib.model.SupportedCurrency;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BudgetPlanRepository {

    private final Database database;
    private final Clock clock;

    public BudgetPlanRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public void insert(BudgetPlan plan) throws SQLException {
        try (var connection = database.connect();
             var insertPlan = connection.prepareStatement("""
                     INSERT INTO budget_plans (id, name, start_date, end_date, limit_amount, currency, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)""");
             var insertCategory = connection.prepareStatement(
                     "INSERT INTO budget_plan_categories (plan_id, category_id) VALUES (?, ?)")) {
            connection.setAutoCommit(false);
            try {
                String now = Timestamps.now(clock);
                insertPlan.setString(1, plan.id());
                insertPlan.setString(2, plan.name());
                insertPlan.setString(3, plan.dates().start().toString());
                insertPlan.setString(4, plan.dates().end().toString());
                insertPlan.setLong(5, plan.limitMinor());
                insertPlan.setString(6, plan.currency().name());
                insertPlan.setString(7, now);
                insertPlan.setString(8, now);
                insertPlan.executeUpdate();
                for (String categoryId : plan.categoryIds()) {
                    insertCategory.setString(1, plan.id());
                    insertCategory.setString(2, categoryId);
                    insertCategory.addBatch();
                }
                insertCategory.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public List<BudgetPlan> list() throws SQLException {
        return read(null);
    }

    /** Setup is complete once a plan has been created, even if it is later removed. */
    public boolean hasCreatedPlan() throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("SELECT 1 FROM budget_plans LIMIT 1");
             var rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    public Optional<BudgetPlan> find(String id) throws SQLException {
        return read(id).stream().findFirst();
    }

    private List<BudgetPlan> read(String id) throws SQLException {
        String sql = """
                SELECT p.id, p.name, p.start_date, p.end_date, p.limit_amount, p.currency, c.category_id
                FROM budget_plans p
                JOIN budget_plan_categories c ON c.plan_id = p.id
                WHERE p.deleted = 0
                """ + (id == null ? "" : " AND p.id = ?") + " ORDER BY p.start_date DESC, p.name, p.id";
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setString(1, id);
            }
            try (var rows = statement.executeQuery()) {
                record Header(String name, DateRange dates, long limit, SupportedCurrency currency) { }
                var headers = new LinkedHashMap<String, Header>();
                var categories = new LinkedHashMap<String, Set<String>>();
                while (rows.next()) {
                    String planId = rows.getString("id");
                    if (!headers.containsKey(planId)) {
                        headers.put(planId, new Header(rows.getString("name"),
                                new DateRange(LocalDate.parse(rows.getString("start_date")),
                                        LocalDate.parse(rows.getString("end_date"))),
                                rows.getLong("limit_amount"), SupportedCurrency.valueOf(rows.getString("currency"))));
                        categories.put(planId, new LinkedHashSet<>());
                    }
                    categories.get(planId).add(rows.getString("category_id"));
                }
                var result = new ArrayList<BudgetPlan>();
                headers.forEach((planId, plan) -> result.add(new BudgetPlan(planId, plan.name(), plan.dates(),
                        plan.limit(), plan.currency(), categories.get(planId))));
                return List.copyOf(result);
            }
        }
    }
}
