import com.jeib.JeibApp;
import com.jeib.db.Database;
import com.jeib.ui.MainViewModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Dev tool, not part of the app: starts the real main window on a throwaway database, clicks
 * through the main flows, and saves a screenshot after each step, so UI changes can be checked
 * without clicking through the app by hand. Build first (mvnw package), then from the repo root:
 *
 *   "%JAVA_HOME%\bin\java" --class-path "target/classes;target/libs/*" tools/Snap.java <temp.db> <out-prefix> [yyyy-mm-dd]
 *
 * On a fresh database it also captures the first-launch setup (choosing RUB). The optional date
 * fixes "today" (and so the month shown). Use a temp path for the database, never the real one.
 */
public class Snap {
    public static void main(String[] args) {
        Clock clock = args.length > 2
                ? Clock.fixed(LocalDate.parse(args[2]).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
                : Clock.systemDefaultZone();
        String out = args[1];
        Platform.startup(() -> {
            try {
                JeibApp.MainWindow window = JeibApp.createMainWindow(new Database(Path.of(args[0])), clock);
                MainViewModel vm = window.viewModel();
                Scene scene = new Scene(window.root(), 1280, 800);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                vm.load();
                Thread.ofPlatform().daemon().start(() -> {
                    sleep(2500);
                    step(() -> save(scene, out + "-01-start.png"));
                    if (scene.lookup("#setup-RUB") != null) {
                        step(() -> fire(scene, "#setup-RUB"));
                        step(() -> save(scene, out + "-02-setup-chosen.png"));
                        step(() -> fire(scene, "#continueButton"));
                    }
                    step(() -> open(scene, "Transactions"));
                    step(() -> save(scene, out + "-03-light-transactions.png"));
                    step(() -> type(scene, "#quickAdd", "250 food lunch"));
                    step(() -> save(scene, out + "-03b-quick-add-preview.png"));
                    step(() -> {
                        javafx.scene.control.TextField field = (javafx.scene.control.TextField) scene.lookup("#quickAdd");
                        field.getOnAction().handle(null); // Enter
                    });
                    step(() -> save(scene, out + "-03c-quick-add-saved.png"));
                    step(() -> type(scene, "#quickAdd", "lunch 250"));
                    step(() -> save(scene, out + "-03d-quick-add-error.png"));
                    step(() -> type(scene, "#quickAdd", ""));
                    step(() -> open(scene, "Budgets"));
                    step(() -> save(scene, out + "-03e-light-budgets.png"));
                    step(() -> fire(scene, "#setOverallLimit"));
                    step(() -> type(scene, "#limitField", "25000"));
                    step(() -> save(scene, out + "-03f-limit-dialog.png"));
                    step(() -> fire(scene, "#limitSave"));
                    step(() -> save(scene, out + "-03g-overall-set.png"));
                    step(() -> {
                        Node row = scene.lookup(".limit-row");
                        if (row != null) {
                            row.getOnMouseClicked().handle(null);
                        }
                    });
                    step(() -> type(scene, "#limitField", "2000"));
                    step(() -> fire(scene, "#limitSave"));
                    step(() -> save(scene, out + "-03h-food-limit-alert.png"));
                    step(() -> open(scene, "Dashboard"));
                    step(() -> save(scene, out + "-03i-dashboard-limits.png"));
                    step(() -> {
                        Node legendRow = scene.lookup(".legend-row");
                        if (legendRow != null) {
                            legendRow.getOnMouseClicked().handle(null); // drill down into the biggest category
                        }
                    });
                    step(() -> save(scene, out + "-03j-drill-down.png"));
                    step(() -> open(scene, "Categories"));
                    step(() -> save(scene, out + "-03k-categories.png"));
                    step(() -> fire(scene, "#newExpense"));
                    step(() -> type(scene, "#nameField", "Coffee"));
                    step(() -> save(scene, out + "-03l-new-category.png"));
                    step(() -> fire(scene, "#categorySave"));
                    step(() -> save(scene, out + "-03m-category-added.png"));
                    step(() -> clickFirst(scene, ".category-row")); // Food
                    step(() -> fire(scene, "#deleteButton"));
                    step(() -> save(scene, out + "-03n-delete-confirm.png"));
                    step(() -> fire(scene, "#keepButton"));
                    step(() -> fire(scene, "#cancelButton"));
                    step(() -> fire(scene, "#newTransaction"));
                    step(() -> fire(scene, "#newCategoryChip"));
                    step(() -> type(scene, "#newCategoryField", "Gym"));
                    step(() -> fire(scene, "#addCategoryButton"));
                    step(() -> save(scene, out + "-03o-inline-category.png"));
                    step(() -> fire(scene, "#cancelButton"));
                    step(() -> open(scene, "Transactions"));
                    step(() -> fire(scene, "#newTransaction"));
                    step(() -> save(scene, out + "-04-light-new.png"));
                    step(() -> fire(scene, "#saveButton"));
                    step(() -> save(scene, out + "-05-light-new-errors.png"));
                    step(() -> fire(scene, "#currency-USD"));
                    step(() -> save(scene, out + "-06-light-new-usd.png"));
                    step(() -> {
                        // Fill in and save for real: 12.50 USD in the first category.
                        ((javafx.scene.control.TextField) scene.lookup("#amountField")).setText("12.50");
                        ((ButtonBase) scene.lookup(".category-chip")).fire();
                        fire(scene, "#saveButton");
                    });
                    step(() -> save(scene, out + "-06b-light-saved.png"));
                    step(() -> clickFirstRow(scene));
                    step(() -> fire(scene, "#deleteButton"));
                    step(() -> save(scene, out + "-06c-deleted-toast.png"));
                    step(() -> fire(scene, "#toastAction"));
                    step(() -> save(scene, out + "-06d-undo-clicked.png"));
                    step(() -> javafx.event.Event.fireEvent(scene.getRoot(), new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.Z,
                            false, true, false, false)));
                    step(() -> save(scene, out + "-06e-ctrl-z.png"));
                    step(vm::toggleTheme);
                    step(() -> save(scene, out + "-07-dark-transactions.png"));
                    step(() -> clickFirstRow(scene));
                    step(() -> save(scene, out + "-08-dark-edit.png"));
                    step(() -> fire(scene, "#cancelButton"));
                    step(() -> open(scene, "Budgets"));
                    step(() -> save(scene, out + "-08b-dark-budgets.png"));
                    step(() -> open(scene, "Dashboard"));
                    step(() -> save(scene, out + "-08c-dark-dashboard.png"));
                    step(() -> open(scene, "Transactions"));
                    step(vm::previousMonth);
                    step(() -> save(scene, out + "-09-dark-empty-month.png"));
                    step(vm::nextMonth);
                    step(() -> open(scene, "Settings"));
                    step(() -> save(scene, out + "-10-dark-settings.png"));
                    step(() -> fire(scene, "#main-USD"));
                    step(() -> save(scene, out + "-11-switch-confirm.png"));
                    step(() -> fire(scene, "#switchConfirm"));
                    step(() -> save(scene, out + "-12-switched.png"));
                    step(() -> {
                        System.out.println("status: " + vm.statusProperty().get());
                        Platform.exit();
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.exit();
            }
        });
    }

    /** Waits for the previous step to render, then runs the next one on the JavaFX thread. */
    static void step(Runnable action) {
        sleep(700);
        Platform.runLater(action);
    }

    static void fire(Scene scene, String selector) {
        Node node = scene.lookup(selector);
        if (node instanceof ButtonBase button) {
            button.fire();
        } else {
            System.out.println("not found: " + selector);
        }
    }

    static void type(Scene scene, String selector, String text) {
        ((javafx.scene.control.TextField) scene.lookup(selector)).setText(text);
    }

    static void clickFirst(Scene scene, String selector) {
        Node node = scene.lookup(selector);
        if (node != null) {
            node.getOnMouseClicked().handle(null);
        }
    }

    static void clickFirstRow(Scene scene) {
        Node row = scene.lookup(".tx-row-clickable");
        if (row != null) {
            row.getOnMouseClicked().handle(null);
        }
    }

    static void open(Scene scene, String page) {
        ((ToggleButton) scene.getRoot().lookupAll(".nav-item").stream()
                .filter(n -> page.equals(((ToggleButton) n).getText())).findFirst().orElseThrow()).fire();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    static void save(Scene scene, String file) {
        WritableImage image = scene.snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setRGB(x, y, reader.getArgb(x, y));
        try { ImageIO.write(out, "png", new File(file)); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
