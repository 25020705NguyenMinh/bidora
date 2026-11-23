package vn.edu.auction.client.browse;

import java.io.IOException;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import vn.edu.auction.client.I18n;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.controller.NavigationController;
import vn.edu.auction.client.navigation.Navigator;

public class BrowseController {
    private static Runnable refreshCurrent = () -> {};
    public static void refreshCurrentAccount() { refreshCurrent.run(); }
    @FXML private Button manageButton;
    @FXML private Label previewNotice;
    @FXML private HBox userArea;
    @FXML private TabPane browseTabs;
    @FXML private FeaturedController featuredController;
    @FXML private AuctionListController auctionListController;
    @FXML private ScrollPane pageScroll;
    private Timeline catalogScroll;
    private int scrollRequest;
    @FXML private void initialize() {
        pageScroll.addEventFilter(ScrollEvent.ANY, event -> cancelCatalogScroll());
        pageScroll.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> cancelCatalogScroll());
        pageScroll.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case UP, DOWN, PAGE_UP, PAGE_DOWN, HOME, END, SPACE -> cancelCatalogScroll();
                default -> { }
            }
        });
        pageScroll.sceneProperty().addListener((obs, before, after) -> {
            if (after == null) cancelCatalogScroll();
        });
        pageScroll.viewportBoundsProperty().addListener((obs, before, after) -> {
            if (Math.abs(before.getWidth() - after.getWidth()) > .5
                    || Math.abs(before.getHeight() - after.getHeight()) > .5) cancelCatalogScroll();
        });
        previewNotice.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> SessionData.getInstance().isPreviewMode() && pageScroll.getWidth() >= 1000,
                pageScroll.widthProperty()));
        previewNotice.managedProperty().bind(previewNotice.visibleProperty());
        featuredController.connect(pageScroll, auctionListController, this::showAuctions);
        refreshCurrent = this::refreshAccount;
        refreshAccount();
        browseTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, tab) -> {
            if (tab != null && tab.getContent() instanceof Parent content)
                vn.edu.auction.client.ui.UiMotion.reveal(content,
                        oldTab != null && browseTabs.getTabs().indexOf(tab) < browseTabs.getTabs().indexOf(oldTab) ? -16 : 16, 0, 0);
        });
    }
    private Boolean lastLoggedIn;
    @FXML private void showAuctions() { selectTab(0); }
    private void selectTab(int index) {
        cancelCatalogScroll();
        browseTabs.getSelectionModel().select(index);
        int request = scrollRequest;
        javafx.application.Platform.runLater(() -> {
            if (request != scrollRequest || pageScroll.getScene() == null) return;
            pageScroll.getScene().getRoot().applyCss();
            pageScroll.getScene().getRoot().layout();
            double full = pageScroll.getContent().getLayoutBounds().getHeight() - pageScroll.getViewportBounds().getHeight();
            if (full <= 0) return;
            double range = pageScroll.getVmax() - pageScroll.getVmin();
            if (range <= 0) return;
            double target = pageScroll.getVmin() + Math.clamp(browseTabs.getBoundsInParent().getMinY() / full, 0, 1) * range;
            double distance = Math.abs(target - pageScroll.getVvalue()) / range * full;
            if (distance < 1) return;
            // A smooth deceleration reaches the exact destination without a logarithmic tail.
            catalogScroll = new Timeline(new KeyFrame(Duration.millis(Math.clamp(380 + distance * .35, 420, 760)),
                    new KeyValue(pageScroll.vvalueProperty(), target, Interpolator.SPLINE(.16, 1, .3, 1))));
            catalogScroll.setOnFinished(event -> catalogScroll = null);
            catalogScroll.play();
        });
    }
    private void cancelCatalogScroll() {
        scrollRequest++;
        if (catalogScroll != null) {
            catalogScroll.stop();
            catalogScroll = null;
        }
    }
    public static void requestLogin(Node source) {
        if (source.getScene() != null && source.getScene().getWindow() instanceof Stage stage)
            openLogin(stage, BrowseController::refreshCurrentAccount);
    }
    private void refreshAccount() {
        boolean in = SessionData.getInstance().isLoggedIn();
        boolean changed = lastLoggedIn != null && lastLoggedIn != in;
        lastLoggedIn = in;
        browseTabs.setPrefHeight(570);
        manageButton.setVisible(in);
        manageButton.setManaged(in);
        I18n.localize(manageButton.textProperty(), "seller.open");
        // Ô user góc phải: Đăng nhập / chuông / chat / menu (Ví, Tài khoản, Quản trị, Đăng xuất)
        vn.edu.auction.client.ui.UserArea.render(userArea, this::refreshAccount);
        if (changed) { // đăng nhập / đăng xuất => tải lại dữ liệu thật
            AuctionListController.reload();
            ItemListController.reload();
            featuredController.refresh();
        }
    }
    @FXML private void handleManageItems() {
        if (!SessionData.getInstance().isLoggedIn()) return;
        NavigationController.showView("/vn/edu/auction/client/fxml/item-management-view.fxml", "seller.open");
    }
    public static void openLogin(Stage owner, Runnable onClose) {
        if (SessionData.getInstance().isPreviewMode()) {
            SessionData.getInstance().enterPreview();
            onClose.run();
            return;
        }
        openAuthWindow(owner, null, onClose);
    }

    public static void openAuthWindow(Stage owner, String recoveryEmail, Runnable callback) {
        boolean recovery = recoveryEmail != null;
        try {
            vn.edu.auction.client.auth.RegisterData.reset();
            String view = recovery ? "reset-password-view.fxml" : "login-view.fxml";
            FXMLLoader loader = new FXMLLoader(BrowseController.class.getResource("/vn/edu/auction/client/fxml/auth/" + view), I18n.getBundle());
            Parent root = loader.load();
            if (recovery) {
                vn.edu.auction.client.auth.AuthController controller = loader.getController();
                controller.startProfileReset(recoveryEmail, callback);
            }
            Stage popup = new Stage();
            popup.initOwner(owner);
            popup.initModality(Modality.WINDOW_MODAL);
            I18n.localize(popup.titleProperty(), recovery ? "title.reset" : "title.login");
            popup.getProperties().put("auction.auth.popup", Boolean.TRUE);
            var screen = javafx.stage.Screen.getScreensForRectangle(owner.getX(), owner.getY(),
                    owner.getWidth(), owner.getHeight()).stream().findFirst().orElse(javafx.stage.Screen.getPrimary());
            var bounds = screen.getVisualBounds();
            Scene scene = new Scene(root, Math.min(root.prefWidth(-1), bounds.getWidth() - 32),
                    Math.min(root.prefHeight(-1), bounds.getHeight() - 56));
            scene.getStylesheets().add(BrowseController.class.getResource("/vn/edu/auction/client/css/auth.css").toExternalForm());
            vn.edu.auction.client.ui.UiTheme.apply(scene);
            popup.setScene(scene);
            popup.setMinWidth(Math.min(360, bounds.getWidth()));
            popup.setMinHeight(Math.min(340, bounds.getHeight()));
            popup.addEventHandler(KeyEvent.KEY_PRESSED, e -> { if (e.getCode() == KeyCode.ESCAPE) { popup.close(); e.consume(); } });
            popup.setOnHidden(e -> {
                vn.edu.auction.client.auth.RegisterData.reset();
                if (recovery) return;
                Object googleCallback = popup.getProperties().remove("auction.google.callback");
                if (googleCallback instanceof com.sun.net.httpserver.HttpServer server) server.stop(0);
                Stage primary = owner;
                while (primary.getOwner() instanceof Stage parent) primary = parent;
                Navigator.setStage(primary); callback.run(); });
            popup.show();
        } catch (IOException | RuntimeException e) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, I18n.get("auth.openFailed")).showAndWait();
        }
    }
}
