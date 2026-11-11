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
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Dev tool, not part of the app: starts the real main window on a throwaway database and saves
 * screenshots, so UI changes can be checked without clicking through the app. Build first
 * (mvnw package), then from the repo root:
 *
 *   "%JAVA_HOME%\bin\java" --class-path "target/classes;target/libs/*" tools/Snap.java <temp.db> <out-prefix> [yyyy-mm-dd]
 *
 * Writes <out-prefix>-light-dashboard.png, -light-transactions.png, -dark-transactions.png and
 * -dark-transactions-empty.png (the month before). The optional date fixes "today" (and so the
 * month shown). Use a temp path for the database, never the real one.
 */
public class Snap {
    public static void main(String[] args) {
        Clock clock = args.length > 2
                ? Clock.fixed(LocalDate.parse(args[2]).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
                : Clock.systemDefaultZone();
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
                    sleep(3000);
                    Platform.runLater(() -> save(scene, args[1] + "-light-dashboard.png"));
                    step(() -> open(scene, "Transactions"));
                    step(() -> save(scene, args[1] + "-light-transactions.png"));
                    step(vm::toggleTheme);
                    step(() -> save(scene, args[1] + "-dark-transactions.png"));
                    step(vm::previousMonth);
                    step(() -> {
                        save(scene, args[1] + "-dark-transactions-empty.png");
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
        sleep(800);
        Platform.runLater(action);
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
