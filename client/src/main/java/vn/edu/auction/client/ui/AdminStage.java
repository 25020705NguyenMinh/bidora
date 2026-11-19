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
 * Trang quản trị riêng cho ADMIN, cùng phong cách với trang "Bán sản phẩm":
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
        Tab productsTab = new Tab("Quản lý sản phẩm", loadProductsView(items));

        // ---------- Tab 2: tài khoản ----------
        Label usersMsg = messageLabel();
        TableView<AdminUserResponse> users = table("Chưa có tài khoản nào.");
        UiKit.col(users, "MÃ", AdminUserResponse::id);
        UiKit.col(users, "HỌ TÊN", AdminUserResponse::fullName);
        UiKit.col(users, "EMAIL", AdminUserResponse::email);
        UiKit.col(users, "VAI TRÒ", u -> roleText(u.role()));
        UiKit.col(users, "TRẠNG THÁI", u -> userStatusText(u.status()));
        Runnable loadUsers = () -> FxAsync.run(api::users, l -> users.getItems().setAll(l),
                e -> usersMsg.setText(BaseApiClient.describe(e)));

        Button toggle = new Button("Vô hiệu hóa / Kích hoạt");
        toggle.getStyleClass().add("outline-button");
        toggle.setOnAction(e -> {
            AdminUserResponse u = users.getSelectionModel().getSelectedItem();
            if (u == null) { usersMsg.setText("Hãy chọn một tài khoản."); return; }
            UserStatus next = u.status() == UserStatus.ACTIVE ? UserStatus.INACTIVE : UserStatus.ACTIVE;
            UiKit.act(usersMsg, () -> { api.setUserStatus(u.id(), next); return Boolean.TRUE; }, loadUsers);
        });

        Button promote = new Button("Nâng lên Admin");
        promote.getStyleClass().add("primary-button");
        promote.setOnAction(e -> {
            AdminUserResponse u = users.getSelectionModel().getSelectedItem();
            if (u == null) { usersMsg.setText("Hãy chọn một tài khoản."); return; }
            // Chặn sớm ở client cho đỡ tốn một request; server vẫn kiểm tra lại (nguồn sự thật)
            if (u.role() == UserRole.ADMIN) { usersMsg.setText("Tài khoản này đã là quản trị viên."); return; }
            if (confirm("Nâng " + u.email() + " lên ADMIN? Tài khoản này sẽ không còn đặt giá/bán hàng được.")) {
                UiKit.act(usersMsg, () -> { api.promoteToAdmin(u.id()); return Boolean.TRUE; }, loadUsers);
            }
        });

        Button delete = new Button("Xóa tài khoản");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> {
            AdminUserResponse u = users.getSelectionModel().getSelectedItem();
            if (u == null) { usersMsg.setText("Hãy chọn một tài khoản."); return; }
            if (confirm("Xóa tài khoản " + u.email() + "?")) {
                UiKit.act(usersMsg, () -> { api.deleteUser(u.id()); return Boolean.TRUE; }, loadUsers);
            }
        });

        Tab usersTab = new Tab("Tài khoản", page(
                "QUẢN LÝ NGƯỜI DÙNG", "Tài khoản",
                "Kích hoạt, vô hiệu hóa, xóa hoặc nâng quyền quản trị cho tài khoản.",
                "Danh sách tài khoản", "Chọn một tài khoản để thao tác.",
                users, usersMsg, loadUsers, toggle, promote, delete));

        // ---------- Tab 3: phiên đấu giá ----------
        Label auctionsMsg = messageLabel();
        TableView<AdminAuctionResponse> auctions = table("Chưa có phiên đấu giá nào.");
        UiKit.col(auctions, "MÃ", AdminAuctionResponse::id);
        UiKit.col(auctions, "SẢN PHẨM", AdminAuctionResponse::itemName);
        UiKit.col(auctions, "GIÁ HIỆN TẠI", a -> UiKit.money(a.currentPrice()));
        UiKit.col(auctions, "NGƯỜI DẪN ĐẦU", a -> a.highestBidderId() == null ? "—" : "#" + a.highestBidderId());
        UiKit.col(auctions, "LƯỢT BID", AdminAuctionResponse::bidCount);
        UiKit.col(auctions, "KẾT THÚC", a -> UiKit.dt(a.endTime()));
        UiKit.col(auctions, "TRẠNG THÁI", a -> auctionStatusText(a.status()));
        UiKit.col(auctions, "HIỂN THỊ", a -> a.hidden() ? "Đang ẩn" : "Hiển thị");
        Runnable loadAuctions = () -> FxAsync.run(api::auctions, l -> auctions.getItems().setAll(l),
                e -> auctionsMsg.setText(BaseApiClient.describe(e)));

        Button stop = new Button("Dừng đấu giá");
        stop.getStyleClass().add("danger-button");
        stop.setOnAction(e -> {
            AdminAuctionResponse a = auctions.getSelectionModel().getSelectedItem();
            if (a == null) { auctionsMsg.setText("Hãy chọn một phiên."); return; }
            if (confirm("Dừng phiên đấu giá \"" + a.itemName() + "\"?")) {
                UiKit.act(auctionsMsg, () -> { api.stopAuction(a.id()); return Boolean.TRUE; }, loadAuctions);
            }
        });
        Button hide = new Button("Ẩn / Hiện phiên");
        hide.getStyleClass().add("outline-button");
        hide.setOnAction(e -> {
            AdminAuctionResponse a = auctions.getSelectionModel().getSelectedItem();
            if (a == null) { auctionsMsg.setText("Hãy chọn một phiên."); return; }
            UiKit.act(auctionsMsg, () -> { api.setHidden(a.id(), !a.hidden()); return Boolean.TRUE; }, loadAuctions);
        });

        Tab auctionsTab = new Tab("Phiên đấu giá", page(
                "GIÁM SÁT ĐẤU GIÁ", "Phiên đấu giá",
                "Theo dõi giá, lượt bid và dừng hoặc ẩn các phiên cần xử lý.",
                "Danh sách phiên đấu giá", "Danh sách tự làm mới khi có bid mới hoặc phiên kết thúc.",
                auctions, auctionsMsg, loadAuctions, stop, hide));

        TabPane tabs = new TabPane(productsTab, usersTab, auctionsTab);
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
        Label badge = new Label("QUẢN TRỊ");
        badge.getStyleClass().add("section-kicker");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("Đóng");
        close.getStyleClass().add("quiet-button");
        close.setOnAction(e -> stage.close());
        HBox nav = new HBox(12, brand, badge, spacer, close);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getStyleClass().add("market-nav");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("market-shell");
        root.setTop(nav);
        root.setCenter(tabs);

        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Trang quản trị");
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
            Parent view = loader.load();
            holder[0] = loader.getController();
            // Đã nằm trong cửa sổ quản trị nên bỏ thanh "Trở về khám phá"
            if (view instanceof BorderPane pane) pane.setTop(null);
            return view;
        } catch (Exception ex) {
            Label err = messageLabel();
            err.setText("Không mở được màn hình quản lý sản phẩm: " + ex.getMessage());
            return err;
        }
    }

    /** Một trang tab: tiêu đề + thẻ trắng chứa thanh nút, bảng và dòng thông báo. */
    private static Node page(String kicker, String title, String subtitle, String panelTitle, String panelSubtitle,
                             TableView<?> table, Label msg, Runnable reload, Button... actions) {
        Label sub = styled(new Label(subtitle), "section-subtitle");
        sub.setWrapText(true);
        VBox head = new VBox(5, styled(new Label(kicker), "section-kicker"),
                styled(new Label(title), "section-title"), sub);

        Button refresh = new Button("Làm mới");
        refresh.getStyleClass().add("outline-button");
        refresh.setOnAction(e -> reload.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().addAll(actions);
        bar.getChildren().addAll(spacer, refresh);

        Label panelSub = styled(new Label(panelSubtitle), "section-subtitle");
        panelSub.setWrapText(true);
        VBox panel = new VBox(14,
                new VBox(4, styled(new Label(panelTitle), "management-title"), panelSub),
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
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new Label(placeholder));
        return t;
    }

    private static boolean confirm(String text) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        if (stage != null) a.initOwner(stage);
        UiTheme.apply(a.getDialogPane());
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    private static String roleText(UserRole r) {
        if (r == null) return "—";
        return switch (r) {
            case ADMIN -> "Quản trị viên";
            case SELLER -> "Người bán";
            case BIDDER -> "Người mua";
            default -> r.name();
        };
    }

    private static String userStatusText(UserStatus s) {
        if (s == null) return "—";
        return switch (s) {
            case ACTIVE -> "Hoạt động";
            case INACTIVE -> "Không hoạt động";
            default -> s.name();
        };
    }

    private static String auctionStatusText(AuctionStatus s) {
        if (s == null) return "—";
        return switch (s) {
            case OPEN -> "Sắp diễn ra";
            case RUNNING -> "Đang diễn ra";
            case FINISHED -> "Đã kết thúc";
            case PAID -> "Đã thanh toán";
            case CANCELED -> "Đã hủy";
        };
    }
}
