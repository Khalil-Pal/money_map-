package com.jeib;

import com.jeib.db.Database;
import com.jeib.i18n.Language;
import com.jeib.i18n.Messages;
import com.jeib.repository.BudgetRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.BudgetService;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.PreferencesService;
import com.jeib.service.TransactionCommands;
import com.jeib.service.TransactionService;
import com.jeib.service.UndoManager;
import com.jeib.ui.BudgetsController;
import com.jeib.ui.CategoriesController;
import com.jeib.ui.CategoriesViewModel;
import com.jeib.ui.CategoryDialogViewModel;
import com.jeib.ui.BudgetsViewModel;
import com.jeib.ui.DashboardController;
import com.jeib.ui.DashboardViewModel;
import com.jeib.ui.DataChanged;
import com.jeib.ui.DialogHost;
import com.jeib.ui.Dialogs;
import com.jeib.ui.HistoryViewModel;
import com.jeib.ui.LanguageSwitcher;
import com.jeib.ui.LimitDialogViewModel;
import com.jeib.ui.LimitWatcher;
import com.jeib.ui.MainController;
import com.jeib.ui.MainViewModel;
import com.jeib.ui.Navigation;
import com.jeib.ui.QuickAddViewModel;
import com.jeib.ui.SettingsController;
import com.jeib.ui.SettingsViewModel;
import com.jeib.ui.SetupViewModel;
import com.jeib.ui.Toasts;
import com.jeib.ui.TransactionDialogViewModel;
import com.jeib.ui.TransactionsController;
import com.jeib.ui.TransactionsViewModel;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.Objects;
import javafx.application.Application;
import javafx.geometry.NodeOrientation;
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
     * Texts and the left/right direction come from {@link Messages}, so set the language first.
     *
     * @param clock     the system clock in the app; a fixed one in tools and tests
     * @param rebuildIn called after the user picks another language, to rebuild the window in it
     */
    public static MainWindow createMainWindow(Database database, Clock clock, Consumer<Language> rebuildIn)
            throws IOException {
        PreferencesService preferences = new PreferencesService(new SettingsRepository(database));
        LanguageSwitcher languages = new LanguageSwitcher(preferences, rebuildIn);
        CategoryRepository categoryRepository = new CategoryRepository(database, clock);
        CategoryService categoryService = new CategoryService(categoryRepository);
        CurrencyService currencyService = new CurrencyService(new SettingsRepository(database));
        TransactionRepository transactionRepository = new TransactionRepository(database, clock);
        TransactionService transactionService = new TransactionService(
                transactionRepository, categoryRepository, currencyService, clock);
        BudgetService budgetService = new BudgetService(new BudgetRepository(database, clock), transactionRepository,
                categoryRepository, currencyService);
        // One undo history for the session; every UI change goes through these commands.
        TransactionCommands transactionCommands = new TransactionCommands(transactionService, new UndoManager());

        MainViewModel main = new MainViewModel(database, categoryRepository, currencyService, preferences, clock);
        // Any save, delete, undo, or limit change fires this; every page that shows data listens.
        DataChanged dataChanged = new DataChanged();
        TransactionsViewModel transactions = new TransactionsViewModel(transactionService, transactionCommands,
                categoryService, main.monthProperty(), main.readyProperty(), dataChanged);
        BudgetsViewModel budgets = new BudgetsViewModel(budgetService, main.monthProperty(), main.readyProperty());
        DashboardViewModel dashboard = new DashboardViewModel(budgetService, main.monthProperty(),
                main.readyProperty(), clock);
        Navigation navigation = new Navigation(transactions);
        CategoriesViewModel categoriesPage = new CategoriesViewModel(categoryService, main.readyProperty());
        Toasts toasts = new Toasts();
        LimitWatcher limitWatcher = new LimitWatcher(budgetService, clock, toasts::show);
        dataChanged.listen(transactions::load);
        dataChanged.listen(budgets::load);
        dataChanged.listen(dashboard::load);
        dataChanged.listen(categoriesPage::load);
        dataChanged.listen(limitWatcher::check);
        // First snapshot for the alerts (silent), and a refresh once setup has chosen the currency.
        main.readyProperty().addListener((obs, was, ready) -> {
            if (ready) {
                limitWatcher.check();
            }
        });
        main.setupNeededProperty().addListener((obs, was, needed) -> {
            if (!needed) {
                dataChanged.run();
            }
        });
        HistoryViewModel history = new HistoryViewModel(transactionCommands.history(), dataChanged, toasts::show);
        SettingsViewModel settings = new SettingsViewModel(currencyService, budgetService, dataChanged, toasts::show);
        dataChanged.listen(settings::load); // a rate can also change in the transaction dialog
        main.readyProperty().addListener((obs, was, ready) -> {
            if (ready) {
                settings.load();
            }
        });
        QuickAddViewModel quickAdd = new QuickAddViewModel(transactionCommands, categoryService, currencyService,
                clock, dataChanged);
        DialogHost dialogHost = new DialogHost();
        Dialogs dialogs = new Dialogs(dialogHost,
                () -> new SetupViewModel(currencyService, languages),
                () -> new TransactionDialogViewModel(transactionCommands, categoryService, currencyService, clock),
                (line, mainCurrency) -> new LimitDialogViewModel(budgetService, line, mainCurrency),
                (category, kind) -> new CategoryDialogViewModel(categoryService, category, kind),
                dataChanged, toasts, history);

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(JeibApp.class.getResource("ui/main.fxml")));
        loader.setResources(Messages.bundle()); // text="%Show all" in FXML is translated
        // fx:include uses the same factory, so it must know every controller type.
        loader.setControllerFactory(type -> {
            if (type == MainController.class) {
                return new MainController(main, dialogHost, dialogs, toasts, history, quickAdd, navigation);
            }
            if (type == DashboardController.class) {
                return new DashboardController(dashboard, navigation, dialogs);
            }
            if (type == SettingsController.class) {
                return new SettingsController(settings, main, database.file(), languages);
            }
            if (type == CategoriesController.class) {
                return new CategoriesController(categoriesPage, dialogs);
            }
            if (type == BudgetsController.class) {
                return new BudgetsController(budgets, dialogs);
            }
            if (type == TransactionsController.class) {
                return new TransactionsController(transactions, dialogs, toasts, history);
            }
            throw new IllegalArgumentException("No controller for " + type);
        });
        Parent root = loader.load();
        // Arabic mirrors the whole window: sidebar on the right, text and rows right-to-left.
        root.setNodeOrientation(Messages.rightToLeft() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        return new MainWindow(root, main);
    }

    private Database database;
    private Stage stage;
    // The local time zone decides "today" and which month the app opens on.
    private final Clock clock = Clock.systemDefaultZone();

    /** Runs before the window exists (not on the JavaFX thread): pick the language to build in. */
    @Override
    public void init() {
        database = Database.openDefault();
        Messages.setLanguage(savedLanguage(database));
    }

    /** The language saved in the database, else Arabic on an Arabic Windows, else English. */
    static Language savedLanguage(Database database) {
        if (Files.exists(database.file())) {
            try {
                var saved = new PreferencesService(new SettingsRepository(database)).language();
                if (saved.isPresent()) {
                    return saved.get();
                }
            } catch (Exception e) {
                // An old or damaged file: fall back to the system language.
            }
        }
        return "ar".equals(Locale.getDefault().getLanguage()) ? Language.ARABIC : Language.ENGLISH;
    }

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        MainWindow window = createMainWindow(database, clock, this::rebuildIn);

        stage.setScene(new Scene(window.root(), 1280, 800));
        stage.setTitle("Jeib");
        stage.getIcons().add(new Image(Objects.requireNonNull(
                JeibApp.class.getResourceAsStream("ui/logo.png"))));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();

        window.viewModel().load();
    }

    /** Switches the language: rebuilds the window's content in it, keeping the same window. */
    private void rebuildIn(Language language) {
        Messages.setLanguage(language);
        try {
            MainWindow window = createMainWindow(database, clock, this::rebuildIn);
            stage.getScene().setRoot(window.root());
            window.viewModel().load();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static void main(String[] args) {
        launch(JeibApp.class, args);
    }
}
