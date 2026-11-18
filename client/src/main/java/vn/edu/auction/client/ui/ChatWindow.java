package vn.edu.auction.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.StringConverter;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.UserApiClient;
import vn.edu.auction.client.api.UserSocketClient;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.shared.dto.ExtraDto.ChatMessageResponse;
import vn.edu.auction.shared.dto.ExtraDto.UserBriefResponse;

/** Cửa sổ chat nhỏ ở góc dưới bên phải. Gửi qua REST, nhận realtime qua /ws/user. */
public final class ChatWindow {
    private static ChatWindow instance;

    public static void toggle(Window owner) {
        if (instance != null && instance.stage.isShowing()) { close(); return; }
        instance = new ChatWindow(owner);
    }

    public static void close() {
        if (instance != null) { instance.dispose(); instance = null; }
    }

    private final UserApiClient api = new UserApiClient();
    private final Stage stage = new Stage();
    private final ComboBox<UserBriefResponse> partner = new ComboBox<>();
    private final ListView<ChatMessageResponse> list = new ListView<>();
    private final TextField input = new TextField();
    private final Label hint = new Label();
    private final Map<Long, String> names = new HashMap<>();
    private final BooleanProperty sending = new SimpleBooleanProperty();
    private int conversationVersion;
    private Runnable unsub;

    private ChatWindow(Window owner) {
        stage.initOwner(owner);
        stage.initStyle(StageStyle.UTILITY);
        stage.setTitle("Tin nhắn");

        partner.setPromptText("Đang tải danh bạ…");
        partner.setAccessibleText("Người nhận");
        partner.setMaxWidth(Double.MAX_VALUE);
        partner.setConverter(new StringConverter<>() {
            @Override public String toString(UserBriefResponse u) { return u == null ? "" : u.fullName(); }
            @Override public UserBriefResponse fromString(String s) { return null; }
        });
        partner.valueProperty().addListener((o, a, u) -> loadConversation(u));

        Long me = SessionData.getInstance().getUserId();
        list.getStyleClass().add("chat-list");
        list.setPlaceholder(new Label("Chọn người để bắt đầu trò chuyện."));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(ChatMessageResponse m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setText(null); setGraphic(null); return; }
                setText(null);
                Label bubble = new Label(m.content());
                bubble.setWrapText(true);
                bubble.setMaxWidth(230);
                bubble.getStyleClass().add("chat-bubble");
                if (m.senderId().equals(me)) bubble.getStyleClass().add("chat-bubble-own");
                setGraphic(bubble);
                setAlignment(m.senderId().equals(me) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            }
        });

        Button send = new Button("Gửi");
        send.getStyleClass().add("primary-button");
        input.setPromptText("Viết tin nhắn…");
        input.disableProperty().bind(sending.or(partner.valueProperty().isNull()));
        send.disableProperty().bind(input.disableProperty().or(Bindings.createBooleanBinding(
                () -> input.getText().isBlank(), input.textProperty())));
        send.setOnAction(e -> send());
        input.setOnAction(e -> send());
        HBox row = new HBox(6, input, send);
        HBox.setHgrow(input, Priority.ALWAYS);
        Label title = new Label("Tin nhắn");
        title.getStyleClass().add("chat-header");
        hint.setWrapText(true);
        hint.managedProperty().bind(hint.textProperty().isNotEmpty());
        VBox root = new VBox(10, title, partner, list, row, hint);
        root.setPadding(new Insets(18));
        VBox.setVgrow(list, Priority.ALWAYS);
        Scene scene = new Scene(root, 370, 500);
        UiTheme.apply(scene);
        stage.setScene(scene);
        // góc dưới bên phải cửa sổ chính
        stage.setX(owner.getX() + owner.getWidth() - 370 - 20);
        stage.setY(owner.getY() + owner.getHeight() - 500 - 40);

        FxAsync.run(api::directory, users -> {
            users.forEach(u -> names.put(u.id(), u.fullName()));
            partner.getItems().setAll(users);
            partner.setPromptText(users.isEmpty() ? "Danh bạ trống" : "Chọn người nhận");
        }, e -> {
            partner.setPromptText("Chưa tải được danh bạ");
            hint.setText(BaseApiClient.describe(e));
        });
        if (!SessionData.getInstance().isPreviewMode())
            unsub = UserSocketClient.getInstance().subscribe(this::onSocket);
        stage.setOnHidden(e -> dispose());
        stage.show();
    }

    private void loadConversation(UserBriefResponse u) {
        int request = ++conversationVersion;
        list.getItems().clear();
        hint.setText("");
        if (u == null) return;
        list.setPlaceholder(new Label("Đang tải tin nhắn…"));
        FxAsync.run(() -> api.chat(u.id()), msgs -> {
                    if (request != conversationVersion) return;
                    list.setPlaceholder(new Label("Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện."));
                    var merged = new TreeMap<Long, ChatMessageResponse>();
                    msgs.forEach(m -> merged.put(m.id(), m));
                    list.getItems().forEach(m -> merged.put(m.id(), m));
                    list.getItems().setAll(merged.values());
                    scrollEnd();
                }, e -> {
                    if (request != conversationVersion) return;
                    list.setPlaceholder(new Label("Chưa tải được tin nhắn."));
                    hint.setText(BaseApiClient.describe(e));
                });
    }

    private void send() {
        UserBriefResponse to = partner.getValue();
        String text = input.getText().trim();
        if (sending.get() || to == null || text.isEmpty()) return;
        sending.set(true);
        hint.setText("Đang gửi…");
        FxAsync.run(() -> api.sendChat(to.id(), text), m -> {
            sending.set(false);
            input.clear();
            hint.setText("");
            if (to.equals(partner.getValue())) appendMessage(m);
            input.requestFocus();
        }, e -> { sending.set(false); hint.setText(BaseApiClient.describe(e)); });
    }

    private void onSocket(JsonNode msg) {
        if (!"CHAT".equals(msg.path("kind").asText())) return;
        try {
            ChatMessageResponse m = BaseApiClient.MAPPER.treeToValue(msg.get("payload"), ChatMessageResponse.class);
            UserBriefResponse cur = partner.getValue();
            if (cur != null && cur.id().equals(m.senderId())) {
                appendMessage(m);
            } else {
                hint.setText("Tin nhắn mới từ " + names.getOrDefault(m.senderId(), "#" + m.senderId()));
            }
        } catch (Exception ignored) {
            // gói tin hỏng thì bỏ qua
        }
    }

    private void appendMessage(ChatMessageResponse message) {
        if (list.getItems().stream().noneMatch(m -> m.id().equals(message.id()))) {
            list.getItems().add(message);
            scrollEnd();
        }
    }

    private void scrollEnd() {
        if (!list.getItems().isEmpty()) list.scrollTo(list.getItems().size() - 1);
    }

    private void dispose() {
        if (unsub != null) { unsub.run(); unsub = null; }
        stage.close();
    }
}
