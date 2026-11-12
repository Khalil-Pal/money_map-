package com.jeib.ui;

import com.jeib.model.Category;
import java.util.function.Consumer;

/**
 * Moving between pages from inside a page: the Dashboard's drill-downs ("show me Food's
 * transactions"). MainController owns the sidebar and plugs in how to switch pages.
 */
public final class Navigation {

    private final TransactionsViewModel transactions;
    private Consumer<String> pageOpener = page -> { };

    public Navigation(TransactionsViewModel transactions) {
        this.transactions = transactions;
    }

    /** Called once by MainController: selects the sidebar item with that name. */
    void attach(Consumer<String> pageOpener) {
        this.pageOpener = pageOpener;
    }

    /** Called by MainController whenever a page is shown, also from a plain sidebar click. */
    void pageShown(String page) {
        if ("Transactions".equals(page)) {
            transactions.filterBy(null); // the sidebar always shows everything
        }
    }

    /** Opens Transactions, showing only {@code category} (or everything for null). */
    public void openTransactions(Category category) {
        pageOpener.accept("Transactions"); // resets the filter (pageShown) …
        transactions.filterBy(category);   // … then applies this one
    }

    public void openBudgets() {
        pageOpener.accept("Budgets");
    }
}
