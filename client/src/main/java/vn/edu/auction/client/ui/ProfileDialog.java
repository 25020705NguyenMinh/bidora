package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.UserApiClient;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.shared.dto.ExtraDto.ChangePasswordRequest;
import vn.edu.auction.shared.dto.ExtraDto.UpdateProfileRequest;

/** Thông tin tài khoản: tên, email, địa chỉ, số điện thoại, đổi mật khẩu. */
public final class ProfileDialog {
    private ProfileDialog() {}

    public static void show(Window owner, Runnable onSaved) {
        UserApiClient api = new UserApiClient();
        Dialog<Void> d = new Dialog<>();
        d.initOwner(owner);
        I18n.localize(d.titleProperty(), "account.details");

        TextField name = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        TextField address = new TextField();
        email.setDisable(true);
        PasswordField oldPw = new PasswordField();
        PasswordField newPw = new PasswordField();
        Label msg = new Label();
        msg.setWrapText(true);
        Button save = I18n.caption(new Button(), "account.save");
        save.setGraphic(new UiIcon(UiIcon.Kind.SAVE));
        Button change = I18n.caption(new Button(), "account.changePassword");
        change.setGraphic(new UiIcon(UiIcon.Kind.LOCK));

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(10));
        g.addRow(0, I18n.caption(new Label(), "account.fullName"), name);
        g.addRow(1, new Label("Email"), email);
        g.addRow(2, I18n.caption(new Label(), "account.address"), address);
        g.addRow(3, I18n.caption(new Label(), "account.phone"), phone);
        g.add(save, 1, 4);
        g.add(new Separator(), 0, 5, 2, 1);
        g.addRow(6, I18n.caption(new Label(), "account.oldPassword"), oldPw);
        g.addRow(7, I18n.caption(new Label(), "account.newPassword"), newPw);
        g.add(change, 1, 8);
        var forgot = I18n.caption(new javafx.scene.control.Hyperlink(), "account.forgot");
        forgot.disableProperty().bind(email.textProperty().isEmpty());
        forgot.setOnAction(e -> {
            if (SessionData.getInstance().isPreviewMode()) {
                I18n.localize(msg.textProperty(), "account.previewPassword");
                return;
            }
            vn.edu.auction.client.browse.BrowseController.openAuthWindow(
                    (javafx.stage.Stage) g.getScene().getWindow(), email.getText(), () -> {
                        oldPw.clear();
                        newPw.clear();
                        I18n.localize(msg.textProperty(), "account.resetDone");
                    });
        });
        g.add(forgot, 1, 9);
        g.add(msg, 0, 10, 2, 1);
        var publicProfiles = I18n.caption(new Button(), "profile.title");
        publicProfiles.setOnAction(e -> TradingProfileDialog.edit(g.getScene().getWindow(), onSaved));
        g.add(publicProfiles, 1, 11);
        javafx.scene.layout.StackPane avatar = TradingProfileDialog.avatar("", 64);
        Button photo = I18n.caption(new Button(), "profile.choosePhoto");
        photo.setGraphic(new UiIcon(UiIcon.Kind.IMAGE));
        photo.setDisable(true);
        var profiles = new vn.edu.auction.shared.dto.TradingProfiles[1];
        FxAsync.run(api::tradingProfiles, current -> {
            if (current.buyer() == null || current.seller() == null) {
                I18n.localize(msg.textProperty(), "error.data");
                return;
            }
            profiles[0] = current;
            avatar.getChildren().setAll(TradingProfileDialog.avatar((SessionData.getInstance().isSellerProfile() ? current.seller() : current.buyer()).avatar(), 64));
            photo.setDisable(false);
        }, error -> I18n.track(msg.textProperty(), () -> BaseApiClient.describe(error)));
        photo.setOnAction(event -> {
            try {
                String image = TradingProfileDialog.chooseAvatar(g.getScene().getWindow());
                if (image == null) return;
                var current = profiles[0];
                boolean sellerSide = SessionData.getInstance().isSellerProfile();
                var active = sellerSide ? current.seller() : current.buyer();
                var changed = new vn.edu.auction.shared.dto.TradingProfiles.Card(active.name(), active.bio(), image);
                var update = new vn.edu.auction.shared.dto.TradingProfiles(
                        current.separate(), current.separate() && sellerSide ? current.buyer() : changed,
                        current.separate() && !sellerSide ? current.seller() : changed);
                photo.setDisable(true);
                FxAsync.run(() -> api.saveTradingProfiles(update), result -> {
                    profiles[0] = result;
                    SessionData.getInstance().setTradingProfiles(result);
                    avatar.getChildren().setAll(TradingProfileDialog.avatar((sellerSide ? result.seller() : result.buyer()).avatar(), 64));
                    photo.setDisable(false);
                    onSaved.run();
                }, error -> {
                    photo.setDisable(false);
                    I18n.track(msg.textProperty(), () -> BaseApiClient.describe(error));
                });
            } catch (java.io.IOException | IllegalArgumentException ex) {
                I18n.localize(msg.textProperty(), "profile.imageLimit");
            }
        });
        g.add(avatar, 0, 12);
        g.add(photo, 1, 12);
        for (TextField f : List.of(name, email, phone, address, oldPw, newPw)) f.setPrefWidth(280);

        FxAsync.run(api::profile, p -> {
            name.setText(p.fullName());
            email.setText(p.email());
            phone.setText(p.phone() == null ? "" : p.phone());
            address.setText(p.address() == null ? "" : p.address());
        }, e -> I18n.track(msg.textProperty(), () -> BaseApiClient.describe(e)));

        save.setOnAction(e -> FxAsync.run(
                () -> api.updateProfile(new UpdateProfileRequest(name.getText(), phone.getText(), address.getText())),
                p -> {
                    SessionData.getInstance().setFullName(p.fullName());
                    I18n.localize(msg.textProperty(), "account.saved");
                    onSaved.run();
                },
                err -> I18n.track(msg.textProperty(), () -> BaseApiClient.describe(err))));

        change.setOnAction(e -> FxAsync.run(
                () -> { api.changePassword(new ChangePasswordRequest(oldPw.getText(), newPw.getText())); return Boolean.TRUE; },
                ok -> { oldPw.clear(); newPw.clear(); I18n.localize(msg.textProperty(), "account.passwordChanged"); },
                err -> I18n.track(msg.textProperty(), () -> BaseApiClient.describe(err))));

        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiTheme.apply(d.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(d);
        d.show();
    }
}
