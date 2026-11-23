package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

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
            Button login = I18n.caption(new Button(), "account.signIn");
            login.setGraphic(new UiIcon(UiIcon.Kind.LOGIN));
            login.getStyleClass().addAll("outline-button", "account-login");
            login.setOnAction(e -> BrowseController.openLogin((Stage) box.getScene().getWindow(), onChanged));
            if (s.isPreviewMode()) {
                I18n.localize(login.textProperty(), "preview.reopen");
                login.setOnAction(e -> { s.enterPreview(); onChanged.run(); });
            }
            Button guestChat = new Button("", new UiIcon(UiIcon.Kind.CHAT));
            guestChat.getStyleClass().add("outline-button");
            guestChat.setId("guestChat");
            guestChat.setTooltip(UiKit.localizedTooltip("chat.title"));
            I18n.localize(guestChat.accessibleTextProperty(), "chat.title");
            guestChat.setOnAction(e -> ChatWindow.toggle(box.getScene().getWindow()));
            box.getChildren().addAll(new BellButton(), guestChat, login);
            return;
        }

        if (!s.isPreviewMode()) UserSocketClient.getInstance().start();

        Button chat = new Button();
        chat.setGraphic(new UiIcon(UiIcon.Kind.CHAT));
        chat.setTooltip(UiKit.localizedTooltip("chat.title"));
        I18n.localize(chat.accessibleTextProperty(), "chat.title");
        chat.getStyleClass().add("outline-button");
        chat.setOnAction(e -> ChatWindow.toggle(box.getScene().getWindow()));

        MenuButton me = new MenuButton(s.getFullName());
        me.setMaxWidth(170);
        me.setTooltip(UiKit.tooltip(s.getFullName()));
        MenuItem switchProfile = new MenuItem();
        switchProfile.setGraphic(new UiIcon(UiIcon.Kind.SWITCH));
        switchProfile.setId("switchTradingProfile");
        updateIdentity(me, switchProfile);
        I18n.onChange(me, () -> updateIdentity(me, switchProfile));
        switchProfile.setOnAction(event -> {
            s.setSellerProfile(!s.isSellerProfile());
            updateIdentity(me, switchProfile);
            UiMotion.pulse(me.getGraphic());
        });
        Long avatarOwner = s.getUserId();
        vn.edu.auction.client.api.FxAsync.run(new vn.edu.auction.client.api.UserApiClient()::tradingProfiles, profiles -> {
            if (!java.util.Objects.equals(avatarOwner, SessionData.getInstance().getUserId())) return;
            if (profiles.buyer() == null || profiles.seller() == null) return;
            s.setTradingProfiles(profiles);
            updateIdentity(me, switchProfile);
        }, error -> { /* Keep the account icon when the deployed server lacks profiles. */ });
        me.getStyleClass().add("outline-button");
        MenuItem wallet = I18n.menuItem("wallet.title");
        wallet.setGraphic(new UiIcon(UiIcon.Kind.WALLET));
        MenuItem profile = I18n.menuItem("account.details");
        profile.setGraphic(new UiIcon(UiIcon.Kind.USER));
        MenuItem logout = I18n.menuItem("account.signOut");
        logout.setGraphic(new UiIcon(UiIcon.Kind.LOGOUT));
        wallet.setOnAction(e -> WalletDialog.show(box.getScene().getWindow()));
        profile.setOnAction(e -> ProfileDialog.show(box.getScene().getWindow(), onChanged));
        logout.setOnAction(e -> { ChatWindow.close(); s.clearSession(); onChanged.run(); });
        me.getItems().addAll(wallet, profile, switchProfile);
        if (s.getRole() == UserRole.ADMIN) {
            MenuItem admin = I18n.menuItem("admin.title");
            admin.setGraphic(new UiIcon(UiIcon.Kind.SHIELD));
            admin.setOnAction(e -> AdminStage.show(box.getScene().getWindow()));
            me.getItems().add(admin);
        }
        me.getItems().addAll(new SeparatorMenuItem(), logout);

        box.getChildren().addAll(new BellButton(), chat, me);
    }
    private static void updateIdentity(MenuButton menu, MenuItem toggle) {
        SessionData session = SessionData.getInstance();
        var profiles = session.getTradingProfiles();
        String name = session.getFullName();
        String image = "";
        boolean separate = profiles != null && profiles.separate();
        toggle.setVisible(separate);
        if (profiles != null) {
            var active = session.isSellerProfile() ? profiles.seller() : profiles.buyer();
            name = active.name();
            image = active.avatar();
        }
        menu.setText(name);
        menu.getTooltip().setText(name);
        menu.setGraphic(TradingProfileDialog.avatar(image, 26));
        I18n.localize(toggle.textProperty(), session.isSellerProfile() ? "profile.switchBuyer" : "profile.switchSeller");
    }

}
