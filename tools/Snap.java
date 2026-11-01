import com.jeib.JeibApp;
import com.jeib.db.Database;
import com.jeib.repository.CategoryRepository;
import com.jeib.ui.MainController;
import com.jeib.ui.MainViewModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Dev tool, not part of the app: starts the real main window on a throwaway database and saves
 * screenshots (light dashboard, dark budgets, dark dashboard), so UI changes can be checked without
 * clicking through the app. Build first (mvnw package), then from the repo root:
 *
 *   "%JAVA_HOME%\bin\java" --class-path "target/classes;target/libs/*" tools/Snap.java <temp.db> <out-prefix>
 *
 * Writes <out-prefix>-light-dashboard.png etc. Use a temp path for the database, never the real one.
 */
public class Snap {
    public static void main(String[] args) {
        Platform.startup(() -> {
            try {
                Database db = new Database(Path.of(args[0]));
                MainViewModel vm = new MainViewModel(db, new CategoryRepository(db));
                FXMLLoader loader = new FXMLLoader(JeibApp.class.getResource("ui/main.fxml"));
                loader.setControllerFactory(t -> new MainController(vm));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1280, 800);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                vm.load();
                Thread.ofPlatform().daemon().start(() -> {
                    sleep(3000);
                    Platform.runLater(() -> {
                        save(scene, args[1] + "-light-dashboard.png");
                        ((ToggleButton) scene.getRoot().lookupAll(".nav-item").stream()
                                .filter(n -> "Budgets".equals(((ToggleButton) n).getText())).findFirst().orElseThrow()).fire();
                        vm.toggleTheme();
                    });
                    sleep(800);
                    Platform.runLater(() -> {
                        save(scene, args[1] + "-dark-budgets.png");
                        ((ToggleButton) scene.getRoot().lookupAll(".nav-item").stream()
                                .filter(n -> "Dashboard".equals(((ToggleButton) n).getText())).findFirst().orElseThrow()).fire();
                    });
                    sleep(800);
                    Platform.runLater(() -> {
                        save(scene, args[1] + "-dark-dashboard.png");
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
