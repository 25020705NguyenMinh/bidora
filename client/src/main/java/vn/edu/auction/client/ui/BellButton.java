package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.UserApiClient;
import vn.edu.auction.client.api.UserSocketClient;
import vn.edu.auction.shared.dto.ExtraDto.NotificationResponse;

/** Chuông thông báo: số chưa đọc + danh sách thả xuống, nhận thông báo mới theo thời gian thực. */
public final class BellButton extends StackPane {
    private final UserApiClient api = new UserApiClient();
    private final ObservableList<NotificationResponse> data = FXCollections.observableArrayList();
    private final Label badge = new Label();
    private final Popup popup = new Popup();
    private Runnable unsub;
    private final Button bell = new Button();
    private javafx.animation.Timeline ringing;
    private boolean nearby;
    private VBox panel;
    private boolean dismissing;
    private final javafx.event.EventHandler<javafx.scene.input.MouseEvent> pointerMoved = this::nearBell;
    private final javafx.event.EventHandler<javafx.scene.input.MouseEvent> outsidePressed = event -> {
        if (popup.isShowing() && !bell.localToScene(bell.getBoundsInLocal()).contains(event.getSceneX(), event.getSceneY())) dismiss();
    };
    private final javafx.event.EventHandler<javafx.scene.input.KeyEvent> escapePressed = event -> {
        if (popup.isShowing() && event.getCode() == javafx.scene.input.KeyCode.ESCAPE) dismiss();
    };

    public BellButton() {
        bell.setId("notificationsButton");
        bell.setGraphic(UiKit.icon("M5 16 H19 L17 13 V9 C17 6 15 4 12 4 C9 4 7 6 7 9 V13 Z M10 19 C11 21 13 21 14 19"));
        bell.setTooltip(UiKit.localizedTooltip("notice.title"));
        I18n.localize(bell.accessibleTextProperty(), "notice.title");
        bell.getStyleClass().add("outline-button");
        badge.getStyleClass().add("notification-badge");
        badge.setVisible(false);
        badge.setMouseTransparent(true);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        getChildren().addAll(bell, badge);

        ListView<NotificationResponse> list = new ListView<>(data);
        list.getStyleClass().add("notification-list");
        list.setPrefSize(340, 160);
        data.addListener((javafx.collections.ListChangeListener<NotificationResponse>) change ->
                list.setPrefHeight(data.isEmpty() ? 160 : Math.min(360, data.size() * 86)));
        list.setPlaceholder(I18n.caption(new Label(), "notice.empty"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(NotificationResponse n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { setText(null); setStyle(""); return; }
                setText(n.message() + "\n" + UiKit.dt(n.createdAt()));
                setWrapText(true);
                setPrefWidth(320);
                setStyle(n.seen() ? "" : "-fx-font-weight:bold;");
            }
        });
        Label heading = I18n.caption(new Label(), "notice.title");
        heading.getStyleClass().add("notification-title");
        VBox box = new VBox(10, heading, list);
        panel = box;
        box.getStyleClass().add("notification-panel");
        StackPane surface = new StackPane(box);
        surface.setPadding(new Insets(12));
        popup.getContent().add(surface);
        UiTheme.apply(popup.getScene());
        popup.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
        popup.getScene().getRoot().setStyle("-fx-background-color: transparent;");
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);
        popup.setOnHidden(e -> markAllSeen());

        bell.setOnAction(e -> {
            if (!vn.edu.auction.client.model.SessionData.getInstance().isLoggedIn()) {
                SignInPrompt.show(getScene().getWindow(), false);
                return;
            }
            if (popup.isShowing()) { dismiss(); return; }
            Bounds b = bell.localToScreen(bell.getBoundsInLocal());
            popup.show(bell, b.getMaxX() - 340, b.getMaxY() + 4);
            popup.setX(b.getMaxX() - popup.getWidth());
            dismissing = false;
            UiMotion.openPopover(box);
        });

        if (vn.edu.auction.client.model.SessionData.getInstance().isLoggedIn()) reload();
        if (vn.edu.auction.client.model.SessionData.getInstance().isLoggedIn()
                && !vn.edu.auction.client.model.SessionData.getInstance().isPreviewMode())
            unsub = UserSocketClient.getInstance().subscribe(this::onSocket);
        sceneProperty().addListener((o, old, sc) -> {
            if (old != null) {
                old.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, pointerMoved);
                old.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, outsidePressed);
                old.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, escapePressed);
            }
            if (sc != null) {
                sc.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, pointerMoved);
                sc.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, outsidePressed);
                sc.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, escapePressed);
            } else {
                popup.hide();
                if (ringing != null) ringing.stop();
            }
            if (sc == null && unsub != null) { unsub.run(); unsub = null; }
        });
    }

    private void nearBell(javafx.scene.input.MouseEvent event) {
        Bounds bounds = bell.localToScene(bell.getBoundsInLocal());
        boolean close = event.getSceneX() >= bounds.getMinX() - 16 && event.getSceneX() <= bounds.getMaxX() + 16
                && event.getSceneY() >= bounds.getMinY() - 12 && event.getSceneY() <= bounds.getMaxY() + 12;
        if (close && !nearby) ring(badge.isVisible() ? 20 : 8, 1);
        nearby = close;
    }

    private void ring(double angle, int cycles) {
        if (ringing != null) ringing.stop();
        javafx.scene.Node icon = bell.getGraphic();
        ringing = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, new javafx.animation.KeyValue(icon.rotateProperty(), 0)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(60), new javafx.animation.KeyValue(icon.rotateProperty(), -angle)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(140), new javafx.animation.KeyValue(icon.rotateProperty(), angle)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(220), new javafx.animation.KeyValue(icon.rotateProperty(), -angle * .55)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(300), new javafx.animation.KeyValue(icon.rotateProperty(), angle * .3)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(400), new javafx.animation.KeyValue(icon.rotateProperty(), 0)));
        ringing.setCycleCount(cycles);
        ringing.play();
    }

    private void dismiss() {
        if (dismissing || !popup.isShowing()) return;
        dismissing = true;
        UiMotion.closeWindow(panel, popup::hide);
    }

    private void reload() {
        FxAsync.run(api::notifications, l -> { data.setAll(l); updateBadge(); }, e -> { });
    }

    private void onSocket(JsonNode msg) {
        if (!"NOTIFICATION".equals(msg.path("kind").asText())) return;
        try {
            data.add(0, BaseApiClient.MAPPER.treeToValue(msg.get("payload"), NotificationResponse.class));
            updateBadge();
            ring(18, 2);
            UiMotion.pulse(badge);
        } catch (Exception ignored) {
            // gói tin hỏng thì bỏ qua
        }
    }

    private void markAllSeen() {
        if (data.stream().allMatch(NotificationResponse::seen)) return;
        FxAsync.run(() -> { api.readAllNotifications(); return Boolean.TRUE; }, ok -> {
            data.replaceAll(n -> new NotificationResponse(n.id(), n.type(), n.message(), n.refId(), true, n.createdAt()));
            updateBadge();
        }, e -> { });
    }

    private void updateBadge() {
        long unread = data.stream().filter(n -> !n.seen()).count();
        badge.setText(unread > 99 ? "99+" : String.valueOf(unread));
        badge.setVisible(unread > 0);
    }
}
