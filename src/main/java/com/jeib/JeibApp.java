package com.jeib;

import com.jeib.db.Database;
import com.jeib.repository.CategoryRepository;
import com.jeib.ui.MainController;
import com.jeib.ui.MainViewModel;
import java.io.IOException;
import java.util.Objects;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class JeibApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        Database database = Database.openDefault();
        MainViewModel viewModel = new MainViewModel(database, new CategoryRepository(database));

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(JeibApp.class.getResource("ui/main.fxml")));
        loader.setControllerFactory(type -> new MainController(viewModel));
        Parent root = loader.load();

        stage.setScene(new Scene(root, 1280, 800));
        stage.setTitle("Jeib");
        stage.getIcons().add(new Image(Objects.requireNonNull(
                JeibApp.class.getResourceAsStream("ui/logo.png"))));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();

        viewModel.load();
    }

    public static void main(String[] args) {
        launch(JeibApp.class, args);
    }
}
