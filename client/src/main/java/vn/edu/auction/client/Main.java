package vn.edu.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import vn.edu.auction.client.controller.NavigationController;
import vn.edu.auction.client.navigation.Navigator;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.preview.PreviewServer;

public class Main extends Application {
    @Override public void start(Stage stage) throws Exception {
        if (getParameters().getRaw().contains("--preview")) {
            int port = PreviewServer.start();
            System.setProperty("auction.server.url", "http://127.0.0.1:" + port);
            SessionData.getInstance().enablePreview();
            System.out.println("Bidora. xem thử cục bộ tại 127.0.0.1:" + port);
        }
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/vn/edu/auction/client/fxml/browse/browse-view.fxml"), I18n.getBundle());
        Scene scene = new Scene(loader.load(), 1120, 720);
        scene.getStylesheets().add(Main.class.getResource("/vn/edu/auction/client/css/browse.css").toExternalForm());
        vn.edu.auction.client.ui.UiTheme.apply(scene);
        Navigator.setStage(stage);
        NavigationController.setPrimaryStage(stage);
        stage.setTitle("Bidora. | Khám phá");
        stage.setMinWidth(880);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
