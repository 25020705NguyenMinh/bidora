package vn.edu.auction.client.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.auction.client.I18n;
import vn.edu.auction.client.browse.BrowseController;

/** Shared guest state for features that need an authenticated account. */
final class SignInPrompt {
    private SignInPrompt() {}

    static void show(Window owner, boolean chat) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        I18n.localize(dialog.titleProperty(), chat ? "chat.title" : "notice.title");
        Label heading = I18n.caption(new Label(), "guest.signInTitle");
        heading.getStyleClass().add("management-title");
        Label description = I18n.caption(new Label(), chat ? "guest.chat" : "guest.notifications");
        description.setWrapText(true);
        description.setMaxWidth(320);
        description.setAlignment(Pos.CENTER);
        description.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        description.getStyleClass().add("section-subtitle");
        UiIcon icon = new UiIcon(chat ? UiIcon.Kind.CHAT : UiIcon.Kind.NOTICE);
        VBox content = new VBox(16, icon, heading, description);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-padding: 30 24; -fx-pref-width: 380;");
        dialog.getDialogPane().setContent(content);
        ButtonType signIn = new ButtonType("", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE, signIn);
        ButtonBase button = (ButtonBase) dialog.getDialogPane().lookupButton(signIn);
        I18n.localize(button.textProperty(), "account.signIn");
        button.setGraphic(new UiIcon(UiIcon.Kind.LOGIN));
        button.getStyleClass().add("primary-button");
        UiTheme.apply(dialog.getDialogPane());
        UiMotion.install(dialog);
        dialog.setOnHidden(event -> {
            if (dialog.getResult() == signIn && owner instanceof Stage stage)
                Platform.runLater(() -> BrowseController.openLogin(stage, BrowseController::refreshCurrentAccount));
        });
        dialog.show();
    }
}
