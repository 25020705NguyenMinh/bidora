package vn.edu.auction.client.ui;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.auction.client.I18n;
import vn.edu.auction.client.api.AdminApiClient;
import vn.edu.auction.client.api.AuctionRealtimeClient;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.controller.ItemManagementController;
import vn.edu.auction.shared.dto.ExtraDto.AdminAuctionResponse;
import vn.edu.auction.shared.dto.ExtraDto.AdminUserResponse;
import vn.edu.auction.shared.enums.AuctionStatus;
import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

/**
 * Trang quản trị riêng cho ADMIN, cùng phong cách với trang bán sản phẩm:
 * (1) Quản lý sản phẩm (duyệt / từ chối kèm feedback gửi vào thông báo của người bán),
 * (2) Tài khoản, (3) Phiên đấu giá.
 */
public final class AdminStage {
    private static Stage stage;

    private AdminStage() {}

    public static void show(Window owner) {
        if (stage != null && stage.isShowing()) { stage.toFront(); return; }
        AdminApiClient api = new AdminApiClient();

        // ---------- Tab 1: Quản lý sản phẩm (dùng chung màn hình với trang bán sản phẩm) ----------
        ItemManagementController[] items = new ItemManagementController[1];
        Tab productsTab = new Tab(I18n.get("manage.title"), loadProductsView(items));
        productsTab.setGraphic(new UiIcon(UiIcon.Kind.PRODUCT));

        // ---------- Tab 2: tài khoản ----------
        Label usersMsg = messageLabel();
        TableView<AdminUserResponse> users = table("admin.noAccounts");
        UiKit.col(users, "table.id", AdminUserResponse::id);
        UiKit.col(users, "table.name", AdminUserResponse::fullName);
        UiKit.col(users, "email.label", AdminUserResponse::email);
        UiKit.col(users, "table.role", u -> roleText(u.role()));
        UiKit.col(users, "table.status", u -> userStatusText(u.status()));
        Runnable loadUsers = () -> FxAsync.run(api::users, l -> users.getItems().setAll(l),
                e -> I18n.track(usersMsg.textProperty(), () -> BaseApiClient.describe(e)));

        Button toggle = I18n.caption(new Button(), "admin.toggleActive");
        toggle.setGraphic(new UiIcon(UiIcon.Kind.SWITCH));
        toggle.getStyleClass().add("outline-button");
        toggle.setOnAction(e -> {
            AdminUserResponse u = users.getSelectionModel().getSelectedItem();
            if (u == null) { I18n.localize(usersMsg.textProperty(), "admin.pickAccount"); return; }
            UserStatus next = u.status() == UserStatus.ACTIVE ? UserStatus.INACTIVE : UserStatus.ACTIVE;
            UiKit.act(usersMsg, () -> { api.setUserStatus(u.id(), next); return Boolean.TRUE; }, loadUsers);
        });

        Button promote = I18n.caption(new Button(), "admin.grant");
        promote.setGraphic(new UiIcon(UiIcon.Kind.SHIELD));
        promote.getStyleClass().add("primary-button");
        promote.setOnAction(e -> {
            AdminUserResponse u = users.getSelectionModel().getSelectedItem();
            if (u == null) { I18n.localize(usersMsg.textProperty(), "admin.pickAccount"); return; }
            // Chặn sớm ở client cho đỡ tốn một request; server vẫn kiểm tra lại (nguồn sự thật)
            if (u.role() == UserRole.ADMIN) { I18n.localize(usersMsg.textProperty(), "admin.alreadyAdmin"); return; }
            if (confirm(I18n.get("admin.grantConfirm", u.email()))) {
                UiKit.act(usersMsg, () -> { api.promoteToAdmin(u.id()); return Boolean.TRUE; }, loadUsers);
            }
        });

        Button delete = I18n.caption(new Button(), "admin.deleteAccount");
        delete.setGraphic(new UiIcon(UiIcon.Kind.DELETE));
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> {
            AdminUserResponse u = users.getSelectionModel().getSelectedItem();
            if (u == null) { I18n.localize(usersMsg.textProperty(), "admin.pickAccount"); return; }
            if (confirm(I18n.get("admin.deleteConfirm", u.email()))) {
                UiKit.act(usersMsg, () -> { api.deleteUser(u.id()); return Boolean.TRUE; }, loadUsers);
            }
        });

        Tab usersTab = new Tab(I18n.get("admin.accounts"), page(
                "admin.accounts",
                "admin.usersIntro",
                "admin.userList", "admin.selectUser",
                users, usersMsg, loadUsers, toggle, promote, delete));

        // ---------- Tab 3: phiên đấu giá ----------
        Label auctionsMsg = messageLabel();
        TableView<AdminAuctionResponse> auctions = table("admin.noAuctions");
        UiKit.col(auctions, "table.id", AdminAuctionResponse::id);
        UiKit.col(auctions, "table.item", AdminAuctionResponse::itemName);
        UiKit.col(auctions, "table.price", a -> UiKit.money(a.currentPrice()));
        UiKit.col(auctions, "table.leading", a -> a.highestBidderId() == null ? "—" : "#" + a.highestBidderId());
        UiKit.col(auctions, "table.bids", AdminAuctionResponse::bidCount);
        UiKit.col(auctions, "table.ends", a -> UiKit.dt(a.endTime()));
        UiKit.col(auctions, "table.status", a -> auctionStatusText(a.status()));
        UiKit.col(auctions, "table.visibility", a -> a.hidden() ? I18n.get("status.hidden") : I18n.get("status.visible"));
        Runnable loadAuctions = () -> FxAsync.run(api::auctions, l -> auctions.getItems().setAll(l),
                e -> I18n.track(auctionsMsg.textProperty(), () -> BaseApiClient.describe(e)));

        Button stop = I18n.caption(new Button(), "admin.stopAuction");
        stop.setGraphic(new UiIcon(UiIcon.Kind.CLOSE));
        stop.getStyleClass().add("danger-button");
        stop.setOnAction(e -> {
            AdminAuctionResponse a = auctions.getSelectionModel().getSelectedItem();
            if (a == null) { I18n.localize(auctionsMsg.textProperty(), "admin.pickAuction"); return; }
            if (confirm(I18n.get("admin.stopConfirm", a.itemName()))) {
                UiKit.act(auctionsMsg, () -> { api.stopAuction(a.id()); return Boolean.TRUE; }, loadAuctions);
            }
        });
        Button hide = I18n.caption(new Button(), "admin.visibility");
        hide.setGraphic(new UiIcon(UiIcon.Kind.VISIBILITY));
        hide.getStyleClass().add("outline-button");
        hide.setOnAction(e -> {
            AdminAuctionResponse a = auctions.getSelectionModel().getSelectedItem();
            if (a == null) { I18n.localize(auctionsMsg.textProperty(), "admin.pickAuction"); return; }
            UiKit.act(auctionsMsg, () -> { api.setHidden(a.id(), !a.hidden()); return Boolean.TRUE; }, loadAuctions);
        });

        Tab auctionsTab = new Tab(I18n.get("browse.auctions"), page(
                "browse.auctions",
                "admin.auctionsIntro",
                "admin.auctionList", "admin.auctionUpdates",
                auctions, auctionsMsg, loadAuctions, stop, hide));

        TabPane tabs = new TabPane(productsTab, usersTab, auctionsTab);
        I18n.localize(productsTab.textProperty(), "manage.title");
        usersTab.setGraphic(new UiIcon(UiIcon.Kind.USER));
        I18n.localize(usersTab.textProperty(), "admin.accounts");
        auctionsTab.setGraphic(new UiIcon(UiIcon.Kind.AUCTION));
        I18n.localize(auctionsTab.textProperty(), "browse.auctions");
        I18n.onChange(tabs, () -> { users.refresh(); auctions.refresh(); });
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().addAll("market-tabs", "admin-tabs");
        tabs.getSelectionModel().selectedItemProperty().addListener((o, old, now) -> {
            if (now == productsTab && items[0] != null) items[0].loadItems();
            else if (now == usersTab) loadUsers.run();
            else if (now == auctionsTab) loadAuctions.run();
        });

        // ---------- Khung cửa sổ ----------
        Label brand = new Label("Bidora.");
        brand.getStyleClass().add("market-brand");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = I18n.caption(new Button(), "common.close");
        close.getStyleClass().add("quiet-button");
        close.setOnAction(e -> stage.close());
        HBox nav = new HBox(12, brand, spacer, new AppearanceControls(), close);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getStyleClass().add("market-nav");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("market-shell");
        root.setTop(nav);
        root.setCenter(tabs);

        stage = new Stage();
        stage.initOwner(owner);
        I18n.localize(stage.titleProperty(), "admin.title");
        Scene scene = new Scene(root, 1280, 820);
        UiTheme.apply(scene);
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(620);

        loadUsers.run();
        loadAuctions.run();
        // Theo dõi realtime: có bid mới / phiên kết thúc thì tab phiên đấu giá tự làm mới
        Runnable unsub = AuctionRealtimeClient.getInstance().subscribe(ev -> loadAuctions.run());
        stage.setOnHidden(e -> unsub.run());
        stage.show();
    }

    /** Nạp màn hình quản lý sản phẩm; ở chế độ admin nó có sẵn bộ lọc, ô feedback, nút duyệt / từ chối. */
    private static Node loadProductsView(ItemManagementController[] holder) {
        try {
            FXMLLoader loader = new FXMLLoader(AdminStage.class.getResource(
                    "/vn/edu/auction/client/fxml/item-management-view.fxml"), I18n.getBundle());
            loader.setControllerFactory(type -> new ItemManagementController(true));
            Parent view = loader.load();
            holder[0] = loader.getController();
            // Đã nằm trong cửa sổ quản trị nên bỏ thanh "Trở về khám phá"
            if (view instanceof BorderPane pane) pane.setTop(null);
            return view;
        } catch (Exception ex) {
            Label err = messageLabel();
            err.setText(I18n.get("admin.openFailed", ex.getMessage()));
            return err;
        }
    }

    /** Một trang tab: tiêu đề + thẻ trắng chứa thanh nút, bảng và dòng thông báo. */
    private static Node page(String title, String subtitle, String panelTitle, String panelSubtitle,
                             TableView<?> table, Label msg, Runnable reload, Button... actions) {
        Label sub = styled(I18n.caption(new Label(), subtitle), "section-subtitle");
        sub.setWrapText(true);
        VBox head = new VBox(5, styled(I18n.caption(new Label(), title), "section-title"), sub);

        Button refresh = I18n.caption(new Button(), "common.refresh");
        refresh.setGraphic(new UiIcon(UiIcon.Kind.HISTORY));
        refresh.getStyleClass().add("outline-button");
        refresh.setOnAction(e -> reload.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().addAll(actions);
        bar.getChildren().addAll(spacer, refresh);

        Label panelSub = styled(I18n.caption(new Label(), panelSubtitle), "section-subtitle");
        panelSub.setWrapText(true);
        VBox panel = new VBox(14,
                new VBox(4, styled(I18n.caption(new Label(), panelTitle), "management-title"), panelSub),
                bar, table, msg);
        panel.getStyleClass().add("management-panel");
        table.setMinHeight(280);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox content = new VBox(18, head, panel);
        content.getStyleClass().add("management-page");
        VBox.setVgrow(panel, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("market-scroll");
        return scroll;
    }

    private static Label styled(Label label, String styleClass) {
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Label messageLabel() {
        Label l = new Label();
        l.setWrapText(true);
        l.getStyleClass().add("message-label");
        return l;
    }

    private static <T> TableView<T> table(String placeholder) {
        TableView<T> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(I18n.caption(new Label(), placeholder));
        return t;
    }

    private static boolean confirm(String text) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        if (stage != null) a.initOwner(stage);
        UiTheme.apply(a.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(a);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    private static String roleText(UserRole r) {
        if (r == null) return "—";
        return switch (r) {
            case ADMIN -> I18n.get("role.admin");
            case SELLER -> I18n.get("role.seller");
            case BIDDER -> I18n.get("role.buyer");
            default -> r.name();
        };
    }

    private static String userStatusText(UserStatus s) {
        if (s == null) return "—";
        return switch (s) {
            case ACTIVE -> I18n.get("account.active");
            case INACTIVE -> I18n.get("account.inactive");
            default -> s.name();
        };
    }

    private static String auctionStatusText(AuctionStatus s) {
        if (s == null) return "—";
        return switch (s) {
            case OPEN -> I18n.get("status.upcoming");
            case RUNNING -> I18n.get("status.live");
            case FINISHED -> I18n.get("status.ended");
            case PAID -> I18n.get("status.paid");
            case CANCELED -> I18n.get("status.cancelled");
        };
    }
}
