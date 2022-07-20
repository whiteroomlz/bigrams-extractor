package ru.whiteroomlz;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import ru.whiteroomlz.controller.Controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.ResourceBundle;

public class App extends Application {
    public static final int MIN_APP_WIDTH = 800;
    public static final int MIN_APP_HEIGHT = 600;
    public static final String DEFAULT_LOCAL_CODE = "ru_RU";

    public enum BundleName {
        STRINGS
    }

    private static Locale locale;

    public static void setLocale(String localeCode) {
        App.locale = new Locale(localeCode);
    }

    @Override
    public void start(Stage stage) {
        stage.setMinWidth(MIN_APP_WIDTH);
        stage.setMinHeight(MIN_APP_HEIGHT);
        setLocale(DEFAULT_LOCAL_CODE);

        Scene scene = new Scene(loadFXML(stage, "view/primary.fxml"), MIN_APP_WIDTH, MIN_APP_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    public static <T extends Controller> Parent loadFXML(Stage stage, String fxmlPath) {
        try {
            ResourceBundle bundle = getResourceBundle(BundleName.STRINGS);
            FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxmlPath), bundle);
            Parent root = fxmlLoader.load();

            T controller = fxmlLoader.getController();
            controller.setStage(stage);

            return root;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static ResourceBundle getResourceBundle(BundleName bundleName) {
        String packageName = App.class.getPackageName();
        return ResourceBundle.getBundle(packageName + switch (bundleName) {
            case STRINGS -> ".values.strings";
        }, locale);
    }

    public static void showAlert(String message, String header, Alert.AlertType alertType, ButtonType... buttonTypes) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType, message, buttonTypes);
            alert.setHeaderText(header);
            alert.show();
        });
    }

    public static void main(String[] args) {
        launch();
    }
}