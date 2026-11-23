package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import java.math.BigDecimal;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.UserApiClient;
import vn.edu.auction.shared.dto.ExtraDto.WalletResponse;
import vn.edu.auction.shared.dto.ExtraDto.WalletTxResponse;

/** Ví: số dư, nạp tiền, rút tiền, lịch sử giao dịch. */
public final class WalletDialog {
    private WalletDialog() {}

    private interface MoneyCall { WalletResponse run(BigDecimal v) throws Exception; }

    public static void show(Window owner) {
        UserApiClient api = new UserApiClient();
        Dialog<Void> d = new Dialog<>();
        d.initOwner(owner);
        I18n.localize(d.titleProperty(), "wallet.myWallet");

        Label balance = new Label("…");
        balance.getStyleClass().add("detail-price");
        Label caption = I18n.caption(new Label(), "wallet.balance");
        caption.getStyleClass().add("detail-caption");
        Label msg = new Label();
        msg.setWrapText(true);
        TextField amount = new TextField();
        I18n.localize(amount.promptTextProperty(), "wallet.amountPrompt");
        Button dep = I18n.caption(new Button(), "wallet.deposit");
        dep.setGraphic(new UiIcon(UiIcon.Kind.DEPOSIT));
        dep.getStyleClass().add("primary-button");
        Button wd = I18n.caption(new Button(), "wallet.withdraw");
        wd.setGraphic(new UiIcon(UiIcon.Kind.UPLOAD));
        TableView<WalletTxResponse> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label historyState = I18n.caption(new Label(), "wallet.loading");
        table.setPlaceholder(historyState);
        UiKit.col(table, "wallet.time", t -> UiKit.dtSec(t.createdAt()));
        UiKit.col(table, "wallet.type", t -> "DEPOSIT".equals(t.type()) ? I18n.get("wallet.deposit") : I18n.get("wallet.withdraw"));
        UiKit.col(table, "wallet.amount", t -> UiKit.money(t.amount()));
        UiKit.col(table, "wallet.after", t -> UiKit.money(t.balanceAfter()));
        table.getColumns().get(2).getStyleClass().add("amount-column");
        table.getColumns().get(3).getStyleClass().add("amount-column");

        HBox actions = new HBox(8, amount, dep, wd);
        Runnable loadHistory = new Runnable() {
            private int latest;
            @Override public void run() {
                int request = ++latest;
                FxAsync.run(api::walletHistory, h -> {
                    if (request != latest) return;
                    table.getItems().setAll(h);
                    I18n.localize(historyState.textProperty(), "wallet.empty");
                }, e -> {
                    if (request != latest) return;
                    I18n.track(msg.textProperty(), () -> BaseApiClient.describe(e));
                    I18n.localize(historyState.textProperty(), "wallet.loadError");
                });
            }
        };
        dep.setOnAction(e -> submit(amount, actions, balance, msg, api::deposit, loadHistory));
        wd.setOnAction(e -> submit(amount, actions, balance, msg, api::withdraw, loadHistory));
        HBox.setHgrow(amount, Priority.ALWAYS);
        Label history = I18n.caption(new Label(), "wallet.history");
        history.setGraphic(new UiIcon(UiIcon.Kind.HISTORY));
        history.getStyleClass().add("history-title");
        VBox content = new VBox(14, new VBox(3, caption, balance), actions, msg, history, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.setPrefSize(560, 420);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiTheme.apply(d.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(d);
        actions.setDisable(true);
        FxAsync.run(api::wallet, w -> {
            balance.setText(UiKit.money(w.balance()));
            actions.setDisable(false);
        }, e -> {
            balance.setText("—");
            I18n.track(msg.textProperty(), () -> BaseApiClient.describe(e));
            actions.setDisable(false);
        });
        loadHistory.run();
        d.show();
    }

    private static void submit(TextField f, HBox actions, Label balance, Label msg, MoneyCall call, Runnable loadHistory) {
        BigDecimal v;
        try {
            v = new BigDecimal(f.getText().trim());
        } catch (NumberFormatException e) {
            I18n.localize(msg.textProperty(), "auction.invalidBid");
            UiMotion.error(f);
            return;
        }
        if (v.signum() <= 0) {
            I18n.localize(msg.textProperty(), "wallet.positive");
            UiMotion.error(f);
            return;
        }
        actions.setDisable(true);
        I18n.localize(msg.textProperty(), "wallet.processing");
        FxAsync.run(() -> call.run(v), wallet -> {
            balance.setText(UiKit.money(wallet.balance()));
            UiMotion.pulse(balance);
            f.clear();
            I18n.localize(msg.textProperty(), "wallet.success");
            actions.setDisable(false);
            loadHistory.run();
        }, err -> {
            I18n.track(msg.textProperty(), () -> BaseApiClient.describe(err));
            actions.setDisable(false);
        });
    }
}
