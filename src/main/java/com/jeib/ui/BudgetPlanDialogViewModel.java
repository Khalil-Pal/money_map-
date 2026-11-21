package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.BudgetPlan;
import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.service.BudgetPlanService;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.ValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

public final class BudgetPlanDialogViewModel {

    record Options(SupportedCurrency mainCurrency, List<Category> categories) { }

    private final BudgetPlanService plans;
    private final CategoryService categories;
    private final CurrencyService currencies;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final StringProperty error = new SimpleStringProperty("");

    public BudgetPlanDialogViewModel(BudgetPlanService plans, CategoryService categories, CurrencyService currencies) {
        this.plans = plans;
        this.categories = categories;
        this.currencies = currencies;
    }

    void load(Consumer<Options> onLoaded) {
        run(() -> new Options(currencies.rates().main(), categories.list().stream()
                .filter(category -> category.kind() == TransactionType.EXPENSE).toList()), onLoaded);
    }

    void addCategory(String name, Consumer<Category> onAdded) {
        run(() -> categories.create(name, TransactionType.EXPENSE, null), onAdded);
    }

    void save(String name, LocalDate start, LocalDate end, String amount,
              SupportedCurrency currency, Set<String> selected, Consumer<BudgetPlan> onSaved) {
        run(() -> plans.create(name, start, end, amount, currency, selected), onSaved);
    }

    BooleanProperty busy() {
        return busy;
    }

    StringProperty error() {
        return error;
    }

    private <T> void run(Callable<T> action, Consumer<T> onSuccess) {
        if (busy.get()) {
            return;
        }
        var task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                return action.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            Throwable failure = task.getException();
            error.set(failure instanceof ValidationException ? failure.getMessage()
                    : t("Something went wrong: {0}", failure.getMessage()));
        });
        error.set("");
        busy.set(true);
        Thread.ofVirtual().name("jeib-plan-dialog").start(task);
    }
}
