package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.UserApiClient;
import vn.edu.auction.client.api.UserSocketClient;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.shared.dto.ExtraDto.ChatMessageResponse;
import vn.edu.auction.shared.dto.ExtraDto.UserBriefResponse;

/** Separate conversation window. REST loads history and /ws/user delivers new messages. */
public final class ChatWindow {
    private static ChatWindow instance;

    public static void toggle(Window owner) {
        if (!SessionData.getInstance().isLoggedIn()) { SignInPrompt.show(owner, true); return; }
        if (instance != null && instance.stage.isShowing()) { instance.hideAnimated(); return; }
        instance = new ChatWindow(owner);
    }

    public static void close() {
        if (instance != null) instance.stage.hide();
    }

    private final UserApiClient api = new UserApiClient();
    private final Stage stage = new Stage();
    private final ComboBox<UserBriefResponse> partner = new ComboBox<>();
    private final ListView<ChatMessageResponse> list = new ListView<>();
    private final TextField input = new TextField();
    private final Label hint = new Label();
    private final Map<Long, String> names = new HashMap<>();
    private final Set<Long> newlyReceived = new HashSet<>();
    private final BooleanProperty sending = new SimpleBooleanProperty();
    private int conversationVersion;
    private Runnable unsub;
    private VBox root;
    private boolean closing;

    private ChatWindow(Window owner) {
        stage.initOwner(owner);
        I18n.localize(stage.titleProperty(), "chat.title");

        I18n.localize(partner.promptTextProperty(), "chat.loadingContacts");
        I18n.localize(partner.accessibleTextProperty(), "chat.recipient");
        partner.setMaxWidth(Double.MAX_VALUE);
        partner.setMinHeight(48);
        partner.setConverter(new StringConverter<>() {
            @Override public String toString(UserBriefResponse u) { return u == null ? "" : u.fullName(); }
            @Override public UserBriefResponse fromString(String s) { return null; }
        });
        partner.valueProperty().addListener((o, a, u) -> loadConversation(u));

        Long me = SessionData.getInstance().getUserId();
        list.getStyleClass().add("chat-list");
        list.setMinHeight(0);
        list.setPlaceholder(emptyState("chat.select"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(ChatMessageResponse m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setText(null); setGraphic(null); return; }
                setText(null);
                Label bubble = new Label(m.content());
                bubble.setWrapText(true);
                bubble.setMaxWidth(370);
                bubble.getStyleClass().add("chat-bubble");
                if (m.senderId().equals(me)) bubble.getStyleClass().add("chat-bubble-own");
                HBox line = new HBox(bubble);
                line.setAlignment(m.senderId().equals(me) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                setGraphic(line);
                if (newlyReceived.remove(m.id())) UiMotion.reveal(line, 0, 8, 0);
            }
        });

        Button send = I18n.caption(new Button(), "chat.send");
        send.getStyleClass().addAll("primary-button", "chat-send");
        send.setGraphic(new UiIcon(UiIcon.Kind.SEND));
        I18n.localize(input.promptTextProperty(), "chat.compose");
        input.disableProperty().bind(sending.or(partner.valueProperty().isNull()));
        send.disableProperty().bind(input.disableProperty().or(Bindings.createBooleanBinding(
                () -> input.getText().isBlank(), input.textProperty())));
        send.setOnAction(e -> send());
        input.setOnAction(e -> send());
        HBox row = new HBox(10, input, send);
        row.getStyleClass().add("chat-composer");
        row.setAlignment(Pos.CENTER);
        HBox.setHgrow(input, Priority.ALWAYS);
        Label brand = new Label("Bidora.");
        brand.getStyleClass().add("chat-brand");
        Label title = I18n.caption(new Label(), "chat.title");
        title.getStyleClass().add("chat-header");
        HBox masthead = new HBox(18, brand, title);
        masthead.setAlignment(Pos.CENTER_LEFT);
        masthead.getStyleClass().add("chat-masthead");
        hint.setWrapText(true);
        hint.managedProperty().bind(hint.textProperty().isNotEmpty());
        VBox body = new VBox(14, partner, list, hint, row);
        body.getStyleClass().add("chat-body");
        VBox.setVgrow(list, Priority.ALWAYS);
        root = new VBox(masthead, body);
        root.getStyleClass().add("chat-window");
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox.setVgrow(list, Priority.ALWAYS);
        Scene scene = new Scene(root, 680, 590);
        UiTheme.apply(scene);
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(480);
        var bounds = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        stage.setX(Math.clamp(owner.getX() + (owner.getWidth() - 680) / 2, bounds.getMinX(), bounds.getMaxX() - 680));
        stage.setY(Math.clamp(owner.getY() + (owner.getHeight() - 590) / 2, bounds.getMinY(), bounds.getMaxY() - 590));

        FxAsync.run(api::directory, users -> {
            users.forEach(u -> names.put(u.id(), u.fullName()));
            partner.getItems().setAll(users);
            I18n.localize(partner.promptTextProperty(), users.isEmpty() ? "chat.noContacts" : "chat.choose");
            if (!users.isEmpty()) partner.getSelectionModel().selectFirst();
        }, e -> {
            I18n.localize(partner.promptTextProperty(), "chat.contactsError");
            I18n.track(hint.textProperty(), () -> BaseApiClient.describe(e));
        });
        if (!SessionData.getInstance().isPreviewMode())
            unsub = UserSocketClient.getInstance().subscribe(this::onSocket);
        stage.setOnCloseRequest(e -> { e.consume(); hideAnimated(); });
        stage.setOnHidden(e -> {
            if (unsub != null) { unsub.run(); unsub = null; }
            if (instance == this) instance = null;
        });
        stage.show();
    }

    private void loadConversation(UserBriefResponse u) {
        int request = ++conversationVersion;
        list.getItems().clear();
        hint.setText("");
        if (u == null) return;
        list.setPlaceholder(emptyState("chat.loading"));
        FxAsync.run(() -> api.chat(u.id()), msgs -> {
                    if (request != conversationVersion) return;
                    list.setPlaceholder(emptyState("chat.empty"));
                    var merged = new TreeMap<Long, ChatMessageResponse>();
                    msgs.forEach(m -> merged.put(m.id(), m));
                    list.getItems().forEach(m -> merged.put(m.id(), m));
                    list.getItems().setAll(merged.values());
                    scrollEnd();
                    UiMotion.reveal(list, 0, 8, 0);
                }, e -> {
                    if (request != conversationVersion) return;
                    list.setPlaceholder(emptyState("chat.loadError"));
                    I18n.track(hint.textProperty(), () -> BaseApiClient.describe(e));
                });
    }

    private void send() {
        UserBriefResponse to = partner.getValue();
        String text = input.getText().trim();
        if (sending.get() || to == null || text.isEmpty()) return;
        sending.set(true);
        I18n.localize(hint.textProperty(), "chat.sending");
        FxAsync.run(() -> api.sendChat(to.id(), text), m -> {
            sending.set(false);
            input.clear();
            hint.setText("");
            if (to.equals(partner.getValue())) appendMessage(m);
            input.requestFocus();
        }, e -> { sending.set(false); I18n.track(hint.textProperty(), () -> BaseApiClient.describe(e)); });
    }

    private void onSocket(JsonNode msg) {
        if (!"CHAT".equals(msg.path("kind").asText())) return;
        try {
            ChatMessageResponse m = BaseApiClient.MAPPER.treeToValue(msg.get("payload"), ChatMessageResponse.class);
            UserBriefResponse cur = partner.getValue();
            if (cur != null && cur.id().equals(m.senderId())) {
                appendMessage(m);
            } else {
                I18n.localize(hint.textProperty(), "chat.from", names.getOrDefault(m.senderId(), "#" + m.senderId()));
            }
        } catch (Exception ignored) {
            // gói tin hỏng thì bỏ qua
        }
    }

    private void appendMessage(ChatMessageResponse message) {
        if (list.getItems().stream().noneMatch(m -> m.id().equals(message.id()))) {
            newlyReceived.add(message.id());
            list.getItems().add(message);
            scrollEnd();
        }
    }

    private void scrollEnd() {
        if (!list.getItems().isEmpty()) list.scrollTo(list.getItems().size() - 1);
    }

    private VBox emptyState(String key) {
        Label mark = new Label();
        mark.setGraphic(UiKit.icon("M3 4 H21 V16 H11 L6 21 V16 H3 Z"));
        mark.getStyleClass().add("chat-empty-icon");
        Label copy = I18n.caption(new Label(), key);
        copy.setWrapText(true);
        copy.getStyleClass().add("chat-empty-copy");
        VBox state = new VBox(14, mark, copy);
        state.setAlignment(Pos.CENTER);
        state.getStyleClass().add("chat-empty");
        return state;
    }

    private void hideAnimated() {
        if (closing) return;
        closing = true;
        UiMotion.closeWindow(root, stage::hide);
    }
}
