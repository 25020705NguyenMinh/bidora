package vn.edu.auction.client.ui;

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
        d.setTitle("Ví của tôi");

        Label balance = new Label("…");
        balance.getStyleClass().add("detail-price");
        Label caption = new Label("Số dư khả dụng");
        caption.getStyleClass().add("detail-caption");
        Label msg = new Label();
        msg.setWrapText(true);
        TextField amount = new TextField();
        amount.setPromptText("Số tiền (đ)");
        Button dep = new Button("Nạp tiền");
        dep.getStyleClass().add("primary-button");
        Button wd = new Button("Rút tiền");
        TableView<WalletTxResponse> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label historyState = new Label("Đang tải giao dịch…");
        table.setPlaceholder(historyState);
        UiKit.col(table, "Thời gian", t -> UiKit.dtSec(t.createdAt()));
        UiKit.col(table, "Loại", t -> "DEPOSIT".equals(t.type()) ? "Nạp tiền" : "Rút tiền");
        UiKit.col(table, "Số tiền", t -> UiKit.money(t.amount()));
        UiKit.col(table, "Số dư sau", t -> UiKit.money(t.balanceAfter()));
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
                    historyState.setText("Chưa có giao dịch.");
                }, e -> {
                    if (request != latest) return;
                    msg.setText(BaseApiClient.describe(e));
                    historyState.setText("Chưa tải được lịch sử.");
                });
            }
        };
        dep.setOnAction(e -> submit(amount, actions, balance, msg, api::deposit, loadHistory));
        wd.setOnAction(e -> submit(amount, actions, balance, msg, api::withdraw, loadHistory));
        HBox.setHgrow(amount, Priority.ALWAYS);
        Label history = new Label("Lịch sử giao dịch");
        history.getStyleClass().add("history-title");
        VBox content = new VBox(14, new VBox(3, caption, balance), actions, msg, history, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.setPrefSize(560, 420);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiTheme.apply(d.getDialogPane());
        actions.setDisable(true);
        FxAsync.run(api::wallet, w -> {
            balance.setText(UiKit.money(w.balance()));
            actions.setDisable(false);
        }, e -> {
            balance.setText("—");
            msg.setText(BaseApiClient.describe(e));
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
            msg.setText("Số tiền không hợp lệ.");
            UiMotion.error(f);
            return;
        }
        if (v.signum() <= 0) {
            msg.setText("Nhập số tiền lớn hơn 0.");
            UiMotion.error(f);
            return;
        }
        actions.setDisable(true);
        msg.setText("Đang xử lý giao dịch…");
        FxAsync.run(() -> call.run(v), wallet -> {
            balance.setText(UiKit.money(wallet.balance()));
            UiMotion.pulse(balance);
            f.clear();
            msg.setText("Giao dịch thành công.");
            actions.setDisable(false);
            loadHistory.run();
        }, err -> {
            msg.setText(BaseApiClient.describe(err));
            actions.setDisable(false);
        });
    }
}
