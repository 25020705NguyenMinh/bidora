package vn.edu.auction.client.ui;

import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import vn.edu.auction.client.api.UserSocketClient;
import vn.edu.auction.client.browse.BrowseController;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.shared.enums.UserRole;

/** Ô user ở góc phải thanh trên: chưa đăng nhập => nút Đăng nhập; đã đăng nhập => chuông, chat, menu tài khoản. */
public final class UserArea {
    private UserArea() {}

    public static void render(HBox box, Runnable onChanged) {
        box.getChildren().clear();
        SessionData s = SessionData.getInstance();

        if (!s.isLoggedIn()) {
            UserSocketClient.getInstance().stop();
            ChatWindow.close();
            Button login = new Button("Đăng nhập");
            login.getStyleClass().add("outline-button");
            login.setOnAction(e -> BrowseController.openLogin((Stage) box.getScene().getWindow(), onChanged));
            if (s.isPreviewMode()) {
                login.setText("Vào lại bản xem thử");
                login.setOnAction(e -> { s.enterPreview(); onChanged.run(); });
            }
            box.getChildren().add(login);
            return;
        }

        if (!s.isPreviewMode()) UserSocketClient.getInstance().start();

        Button chat = new Button();
        chat.setGraphic(UiKit.icon("M3 4 H19 V15 H10 L6 19 V15 H3 Z"));
        chat.setTooltip(UiKit.tooltip("Tin nhắn"));
        chat.setAccessibleText("Tin nhắn");
        chat.getStyleClass().add("outline-button");
        chat.setOnAction(e -> ChatWindow.toggle(box.getScene().getWindow()));

        MenuButton me = new MenuButton(s.getFullName());
        me.setMaxWidth(170);
        me.setTooltip(UiKit.tooltip(s.getFullName()));
        me.setGraphic(UiKit.icon("M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 M4 20 C4 15 7 14 12 14 C17 14 20 15 20 20"));
        me.getStyleClass().add("outline-button");
        MenuItem wallet = new MenuItem("Ví");
        MenuItem profile = new MenuItem("Thông tin tài khoản");
        MenuItem logout = new MenuItem("Đăng xuất");
        wallet.setOnAction(e -> WalletDialog.show(box.getScene().getWindow()));
        profile.setOnAction(e -> ProfileDialog.show(box.getScene().getWindow(), onChanged));
        logout.setOnAction(e -> { ChatWindow.close(); s.clearSession(); onChanged.run(); });
        me.getItems().addAll(wallet, profile);
        if (s.getRole() == UserRole.ADMIN) {
            MenuItem admin = new MenuItem("Trang quản trị");
            admin.setOnAction(e -> AdminStage.show(box.getScene().getWindow()));
            me.getItems().add(admin);
        }
        me.getItems().addAll(new SeparatorMenuItem(), logout);

        box.getChildren().addAll(new BellButton(), chat, me);
    }
}
