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
            System.out.println("Bidora. xem thu cuc bo tai 127.0.0.1:" + port);
        }
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/vn/edu/auction/client/fxml/browse/browse-view.fxml"), I18n.getBundle());
        Scene scene = new Scene(loader.load(), 1120, 720);
        vn.edu.auction.client.ui.UiTheme.apply(scene);
        Navigator.setStage(stage);
        NavigationController.setPrimaryStage(stage);
        I18n.localize(stage.titleProperty(), "title.explore");
        stage.setMinWidth(880);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.setOnHidden(event -> javafx.application.Platform.exit());
        stage.show();
    }

    @Override public void stop() {
        vn.edu.auction.client.api.UserSocketClient.getInstance().stop();
        vn.edu.auction.client.api.AuctionRealtimeClient.getInstance().stop();
        PreviewServer.stop();
    }
    public static void main(String[] args) { launch(args); }
}
