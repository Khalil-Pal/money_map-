package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.service.BudgetService.LimitLine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * Opens the app's dialogs in the {@link DialogHost}. Each opening gets a fresh view-model, so no
 * state leaks from one dialog to the next. Only one dialog is open at a time.
 */
public final class Dialogs {

    private final DialogHost host;
    private final Supplier<SetupViewModel> setupViewModels;
    private final Supplier<TransactionDialogViewModel> transactionViewModels;
    private final BiFunction<LimitLine, SupportedCurrency, LimitDialogViewModel> limitViewModels;
    private final BiFunction<Category, TransactionType, CategoryDialogViewModel> categoryViewModels;
    private final Runnable afterChange;
    private final Toasts toasts;
    private final HistoryViewModel history;

    /**
     * @param afterChange           runs on the JavaFX thread after any save or delete (see DataChanged)
     * @param history               for the toast's Undo after a delete
     */
    public Dialogs(DialogHost host, Supplier<SetupViewModel> setupViewModels,
                   Supplier<TransactionDialogViewModel> transactionViewModels,
                   BiFunction<LimitLine, SupportedCurrency, LimitDialogViewModel> limitViewModels,
                   BiFunction<Category, TransactionType, CategoryDialogViewModel> categoryViewModels,
                   Runnable afterChange,
                   Toasts toasts, HistoryViewModel history) {
        this.host = host;
        this.setupViewModels = setupViewModels;
        this.transactionViewModels = transactionViewModels;
        this.limitViewModels = limitViewModels;
        this.categoryViewModels = categoryViewModels;
        this.afterChange = afterChange;
        this.toasts = toasts;
        this.history = history;
    }

    /** First-launch setup. It can't be cancelled; {@code onDone} runs once the currency is saved. */
    public void openSetup(Runnable onDone) {
        if (host.isOpen()) {
            host.close(); // setup comes before anything else
        }
        SetupViewModel viewModel = setupViewModels.get();
        host.show(load("setup.fxml", new SetupController(viewModel, () -> {
            host.close();
            onDone.run();
        })));
    }

    public void openNewTransaction() {
        openTransaction(TransactionDialogViewModel::startNew);
    }

    public void openEditTransaction(Transaction transaction) {
        openTransaction(viewModel -> viewModel.startEdit(transaction));
    }

    private void openTransaction(Consumer<TransactionDialogViewModel> prepare) {
        if (host.isOpen()) {
            return;
        }
        TransactionDialogViewModel viewModel = transactionViewModels.get();
        prepare.accept(viewModel);
        host.show(load("transaction-dialog.fxml", new TransactionDialogController(viewModel, () -> {
            host.close();
            afterChange.run(); // a category may have been added inline
        }, () -> {
            host.close();
            afterChange.run();
        }, () -> {
            host.close();
            afterChange.run();
            toasts.show(t("Transaction deleted."), t("Undo"), history::undo);
        })));
        viewModel.load();
    }

    /** Sets, changes, or removes one limit (a category's, or the overall one). */
    public void openLimit(LimitLine line, SupportedCurrency mainCurrency) {
        if (host.isOpen()) {
            return;
        }
        LimitDialogViewModel viewModel = limitViewModels.apply(line, mainCurrency);
        host.show(load("limit-dialog.fxml", new LimitDialogController(viewModel, mainCurrency.name(),
                host::close, () -> {
                    host.close();
                    afterChange.run();
                })));
    }

    /** New category of {@code kind} (editing null), or edit / delete {@code editing}. */
    public void openCategory(Category editing, TransactionType kind) {
        if (host.isOpen()) {
            return;
        }
        CategoryDialogViewModel viewModel = categoryViewModels.apply(editing, kind);
        host.show(load("category-dialog.fxml", new CategoryDialogController(viewModel, host::close, message -> {
            host.close();
            afterChange.run();
            toasts.show(message);
        })));
        viewModel.load();
    }

    private static Parent load(String fxml, Object controller) {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(Dialogs.class.getResource(fxml), fxml));
        loader.setResources(com.jeib.i18n.Messages.bundle());
        loader.setControllerFactory(type -> controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't load " + fxml, e);
        }
    }
}
