package com.jeib;

import com.jeib.db.Database;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.TransactionService;
import com.jeib.ui.DialogHost;
import com.jeib.ui.Dialogs;
import com.jeib.ui.MainController;
import com.jeib.ui.MainViewModel;
import com.jeib.ui.SetupViewModel;
import com.jeib.ui.TransactionDialogViewModel;
import com.jeib.ui.TransactionsController;
import com.jeib.ui.TransactionsViewModel;
import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class JeibApp extends Application {

    /** The main window's content and its view-model, so callers can start loading. */
    public record MainWindow(Parent root, MainViewModel viewModel) {
    }

    /**
     * Builds every repository, service, view-model, and controller for one database. The only place
     * that wires the app together; also used by tools/Snap.java.
     *
     * @param clock the system clock in the app; a fixed one in tools and tests
     */
    public static MainWindow createMainWindow(Database database, Clock clock) throws IOException {
        CategoryRepository categoryRepository = new CategoryRepository(database, clock);
        CategoryService categoryService = new CategoryService(categoryRepository);
        CurrencyService currencyService = new CurrencyService(new SettingsRepository(database));
        TransactionService transactionService = new TransactionService(
                new TransactionRepository(database, clock), categoryRepository, currencyService, clock);

        MainViewModel main = new MainViewModel(database, categoryRepository, currencyService, clock);
        TransactionsViewModel transactions = new TransactionsViewModel(
                transactionService, categoryService, main.monthProperty(), main.readyProperty());
        DialogHost dialogHost = new DialogHost();
        Dialogs dialogs = new Dialogs(dialogHost,
                () -> new SetupViewModel(currencyService),
                () -> new TransactionDialogViewModel(transactionService, categoryService, currencyService, clock),
                transactions::load);

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(JeibApp.class.getResource("ui/main.fxml")));
        // fx:include uses the same factory, so it must know every controller type.
        loader.setControllerFactory(type -> {
            if (type == MainController.class) {
                return new MainController(main, dialogHost, dialogs);
            }
            if (type == TransactionsController.class) {
                return new TransactionsController(transactions, dialogs);
            }
            throw new IllegalArgumentException("No controller for " + type);
        });
        return new MainWindow(loader.load(), main);
    }

    @Override
    public void start(Stage stage) throws IOException {
        // The local time zone decides "today" and which month the app opens on.
        MainWindow window = createMainWindow(Database.openDefault(), Clock.systemDefaultZone());

        stage.setScene(new Scene(window.root(), 1280, 800));
        stage.setTitle("Jeib");
        stage.getIcons().add(new Image(Objects.requireNonNull(
                JeibApp.class.getResourceAsStream("ui/logo.png"))));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();

        window.viewModel().load();
    }

    public static void main(String[] args) {
        launch(JeibApp.class, args);
    }
}
