package vn.edu.auction.client.browse;

import java.io.IOException;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import vn.edu.auction.client.I18n;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.controller.NavigationController;
import vn.edu.auction.client.navigation.Navigator;

public class BrowseController {
    private static Runnable refreshCurrent = () -> {};
    public static void refreshCurrentAccount() { refreshCurrent.run(); }
    @FXML private Button manageButton;
    @FXML private Button themeButton;
    @FXML private Label previewNotice;
    @FXML private HBox userArea;
    @FXML private TabPane browseTabs;
    @FXML private FeaturedController featuredController;
    @FXML private AuctionListController auctionListController;
    @FXML private ScrollPane pageScroll;
    @FXML private void initialize() {
        previewNotice.setVisible(SessionData.getInstance().isPreviewMode());
        previewNotice.setManaged(previewNotice.isVisible());
        featuredController.connect(pageScroll, auctionListController, () -> {
            if (SessionData.getInstance().isLoggedIn()) showAuctions();
            else requestLogin(pageScroll);
        });
        refreshCurrent = this::refreshAccount;
        refreshAccount();
        updateThemeButton();
        browseTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, tab) -> {
            if (tab != null && tab.getContent() instanceof Parent content)
                vn.edu.auction.client.ui.UiMotion.reveal(content,
                        oldTab != null && browseTabs.getTabs().indexOf(tab) < browseTabs.getTabs().indexOf(oldTab) ? -16 : 16, 0, 0);
        });
    }
    private Boolean lastLoggedIn;
    @FXML private void toggleTheme() {
        vn.edu.auction.client.ui.UiTheme.setDark(!vn.edu.auction.client.ui.UiTheme.isDark());
        updateThemeButton();
    }
    private void updateThemeButton() {
        boolean dark = vn.edu.auction.client.ui.UiTheme.isDark();
        String label = dark ? "Chế độ sáng" : "Chế độ tối";
        themeButton.setTooltip(vn.edu.auction.client.ui.UiKit.tooltip(label));
        themeButton.setAccessibleText(label);
        themeButton.setGraphic(vn.edu.auction.client.ui.UiKit.icon(dark
                ? "M12 2 V4 M12 20 V22 M2 12 H4 M20 12 H22 M5 5 L6.5 6.5 M17.5 17.5 L19 19 M19 5 L17.5 6.5 M6.5 17.5 L5 19 M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12"
                : "M19 15.5 A8 8 0 0 1 8.5 5 A8 8 0 1 0 19 15.5 Z"));
    }
    @FXML private void showAuctions() { selectTab(0); }
    private void selectTab(int index) {
        browseTabs.getSelectionModel().select(index);
        javafx.application.Platform.runLater(() -> {
            double full = pageScroll.getContent().getBoundsInLocal().getHeight() - pageScroll.getViewportBounds().getHeight();
            if (full > 0) pageScroll.setVvalue(Math.min(1, browseTabs.getBoundsInParent().getMinY() / full));
        });
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
        boolean isAdmin = SessionData.getInstance().getRole() == vn.edu.auction.shared.enums.UserRole.ADMIN;
        // "Bán sản phẩm" dành cho người bán và người mua; admin quản lý sản phẩm trong Trang quản trị
        boolean canSell = in && !isAdmin;
        manageButton.setVisible(canSell);
        manageButton.setManaged(canSell);
        manageButton.setText("Bán sản phẩm");
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
        if (SessionData.getInstance().getRole() == vn.edu.auction.shared.enums.UserRole.ADMIN) return;
        NavigationController.showView("/vn/edu/auction/client/fxml/item-management-view.fxml", "Bán sản phẩm");
    }
    public static void openLogin(Stage owner, Runnable onClose) {
        if (SessionData.getInstance().isPreviewMode()) {
            SessionData.getInstance().enterPreview();
            onClose.run();
            return;
        }
        try {
            vn.edu.auction.client.auth.RegisterData.reset();
            FXMLLoader loader = new FXMLLoader(BrowseController.class.getResource("/vn/edu/auction/client/fxml/auth/login-view.fxml"), I18n.getBundle());
            Parent root = loader.load();
            Stage popup = new Stage();
            popup.initOwner(owner);
            popup.initModality(Modality.WINDOW_MODAL);
            popup.setTitle(I18n.get("title.login"));
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
                Object callback = popup.getProperties().remove("auction.google.callback");
                if (callback instanceof com.sun.net.httpserver.HttpServer server) server.stop(0);
                Stage primary = owner;
                while (primary.getOwner() instanceof Stage parent) primary = parent;
                Navigator.setStage(primary); onClose.run(); });
            popup.show();
        } catch (IOException | RuntimeException e) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, "Không thể mở đăng nhập. Vui lòng thử lại.").showAndWait();
        }
    }
}
