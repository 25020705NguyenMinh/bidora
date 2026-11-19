package vn.edu.auction.client.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.auction.client.api.AdminApiClient;
import vn.edu.auction.client.api.AuctionApiClient;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.ItemApiClient;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.ui.UiKit;
import vn.edu.auction.shared.dto.AuctionRequest;
import vn.edu.auction.shared.dto.ItemRequest;
import vn.edu.auction.shared.dto.ItemResponse;
import vn.edu.auction.shared.enums.AuctionStatus;
import vn.edu.auction.shared.enums.ItemStatus;
import vn.edu.auction.shared.enums.UserRole;

/**
 * Quản lý sản phẩm.
 * - Cho người bán: Thêm mới (chờ admin phê duyệt) -> admin duyệt -> tạo phiên đấu giá.
 * - Cho Admin: Xem danh sách cần phê duyệt / sắp bán / đang bán / tất cả, phê duyệt kèm feedback gửi thông báo, từ chối, xóa sản phẩm.
 */
public class ItemManagementController {

    @FXML private Label kickerLabel;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label tableTitleLabel;
    @FXML private Label tableSubtitleLabel;
    @FXML private Button createAuctionButton;

    @FXML private HBox filterBox;
    @FXML private Button filterPendingBtn;
    @FXML private Button filterUpcomingBtn;
    @FXML private Button filterActiveBtn;
    @FXML private Button filterAllBtn;

    @FXML private TableView<ItemResponse> itemTable;
    @FXML private TableColumn<ItemResponse, Long> idColumn;
    @FXML private TableColumn<ItemResponse, String> sellerColumn;
    @FXML private TableColumn<ItemResponse, String> nameColumn;
    @FXML private TableColumn<ItemResponse, String> descriptionColumn;
    @FXML private TableColumn<ItemResponse, String> priceColumn;
    @FXML private TableColumn<ItemResponse, String> statusColumn;
    @FXML private Label messageLabel;

    @FXML private Label formTitleLabel;
    @FXML private Label formSubtitleLabel;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField startingPriceField;
    @FXML private VBox feedbackBox;
    @FXML private TextArea feedbackArea;

    @FXML private Button createItemButton;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;
    @FXML private HBox bottomActionRow;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;
    @FXML private Button clearButton;

    private final ItemApiClient itemApi = new ItemApiClient();
    private final AuctionApiClient auctionApi = new AuctionApiClient();
    private final AdminApiClient adminApi = new AdminApiClient();

    private final ObservableList<ItemResponse> itemList = FXCollections.observableArrayList();
    private List<ItemResponse> allAdminItems = new ArrayList<>();

    /** true khi server chưa có /api/admin/items nên chỉ lấy được danh sách chờ duyệt. */
    private volatile boolean pendingOnly;

    private enum AdminFilter { PENDING, UPCOMING, ACTIVE, ALL }
    private AdminFilter currentFilter = AdminFilter.PENDING;

    @FXML
    public void initialize() {
        boolean isAdmin = SessionData.getInstance().getRole() == UserRole.ADMIN;

        idColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue().getId()));
        sellerColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(
                row.getValue().getSellerId() != null ? "#" + row.getValue().getSellerId() : "—"));
        nameColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue().getName()));
        descriptionColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue().getDescription()));
        priceColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(UiKit.money(row.getValue().getStartingPrice())));
        statusColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(statusText(row.getValue())));

        itemTable.setItems(itemList);
        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                nameField.setText(newSelection.getName());
                descriptionArea.setText(newSelection.getDescription());
                startingPriceField.setText(newSelection.getStartingPrice().toPlainString());
                if (isAdmin) {
                    boolean isDraft = newSelection.getStatus() == ItemStatus.DRAFT;
                    approveButton.setDisable(!isDraft);
                    rejectButton.setDisable(!isDraft);
                    if (feedbackArea != null) feedbackArea.clear();
                }
            }
        });

        setupRoleView(isAdmin);
        loadItems();
    }

    private void setupRoleView(boolean isAdmin) {
        if (isAdmin) {
            if (kickerLabel != null) kickerLabel.setText("KHÔNG GIAN QUẢN TRỊ");
            if (titleLabel != null) titleLabel.setText("Quản lý sản phẩm");
            if (subtitleLabel != null) subtitleLabel.setText("Phê duyệt sản phẩm, theo dõi danh sách chờ duyệt, sắp bán, đang bán.");
            if (tableTitleLabel != null) tableTitleLabel.setText("Danh sách sản phẩm hệ thống");
            if (tableSubtitleLabel != null) tableSubtitleLabel.setText("Chọn một sản phẩm để xem chi tiết và phê duyệt.");
            if (formTitleLabel != null) formTitleLabel.setText("Chi tiết & Phê duyệt");
            if (formSubtitleLabel != null) formSubtitleLabel.setText("Phê duyệt hoặc từ chối, kèm feedback để người bán đọc trong thông báo.");

            if (createAuctionButton != null) {
                createAuctionButton.setVisible(false);
                createAuctionButton.setManaged(false);
            }
            if (filterBox != null) {
                filterBox.setVisible(true);
                filterBox.setManaged(true);
            }
            if (sellerColumn != null) sellerColumn.setVisible(true);

            nameField.setEditable(false);
            descriptionArea.setEditable(false);
            startingPriceField.setEditable(false);

            if (feedbackBox != null) {
                feedbackBox.setVisible(true);
                feedbackBox.setManaged(true);
            }
            if (createItemButton != null) {
                createItemButton.setVisible(false);
                createItemButton.setManaged(false);
            }
            if (approveButton != null) {
                approveButton.setVisible(true);
                approveButton.setManaged(true);
            }
            if (rejectButton != null) {
                rejectButton.setVisible(true);
                rejectButton.setManaged(true);
            }
            if (updateButton != null) {
                updateButton.setVisible(false);
                updateButton.setManaged(false);
            }
            if (clearButton != null) {
                clearButton.setVisible(false);
                clearButton.setManaged(false);
            }
            if (deleteButton != null) {
                deleteButton.setVisible(false);
                deleteButton.setManaged(false);
            }
        } else {
            if (kickerLabel != null) kickerLabel.setText("KHÔNG GIAN BÁN HÀNG");
            if (titleLabel != null) titleLabel.setText("Bán sản phẩm");
            if (subtitleLabel != null) subtitleLabel.setText("Thêm sản phẩm, theo dõi phê duyệt và tạo phiên đấu giá. Feedback của quản trị viên sẽ hiện ở chuông thông báo.");
            if (createAuctionButton != null) {
                createAuctionButton.setVisible(true);
                createAuctionButton.setManaged(true);
            }
            if (filterBox != null) {
                filterBox.setVisible(false);
                filterBox.setManaged(false);
            }
            if (sellerColumn != null) sellerColumn.setVisible(false);

            nameField.setEditable(true);
            descriptionArea.setEditable(true);
            startingPriceField.setEditable(true);

            if (feedbackBox != null) {
                feedbackBox.setVisible(false);
                feedbackBox.setManaged(false);
            }
            if (createItemButton != null) {
                createItemButton.setVisible(true);
                createItemButton.setManaged(true);
            }
            if (approveButton != null) {
                approveButton.setVisible(false);
                approveButton.setManaged(false);
            }
            if (rejectButton != null) {
                rejectButton.setVisible(false);
                rejectButton.setManaged(false);
            }
            if (updateButton != null) {
                updateButton.setVisible(true);
                updateButton.setManaged(true);
            }
            if (clearButton != null) {
                clearButton.setVisible(true);
                clearButton.setManaged(true);
            }
            if (deleteButton != null) {
                deleteButton.setVisible(true);
                deleteButton.setManaged(true);
                deleteButton.setText("Xóa");
            }
        }
    }

    private static String statusText(ItemResponse i) {
        if (i == null || i.getStatus() == null) return "—";
        if (i.getStatus() == ItemStatus.DRAFT) return "Chờ phê duyệt";
        if (i.getStatus() == ItemStatus.ACTIVE) {
            if (i.getAuctionStatus() == null) return "Đã duyệt (Chờ lên phiên)";
            if (i.getAuctionStatus() == AuctionStatus.OPEN) return "Sắp bán (Đã lên lịch)";
            if (i.getAuctionStatus() == AuctionStatus.RUNNING) return "Đang bán (Đang đấu giá)";
            if (i.getAuctionStatus() == AuctionStatus.FINISHED || i.getAuctionStatus() == AuctionStatus.PAID) return "Đã kết thúc";
            if (i.getAuctionStatus() == AuctionStatus.CANCELED) return "Đã hủy phiên";
            return "Đã duyệt";
        }
        if (i.getStatus() == ItemStatus.SOLD) return "Đã bán";
        if (i.getStatus() == ItemStatus.REMOVED) return "Đã gỡ";
        return i.getStatus().name();
    }

    public void loadItems() {
        boolean isAdmin = SessionData.getInstance().getRole() == UserRole.ADMIN;
        System.out.println("[ItemManagement] loadItems - isAdmin: " + isAdmin + ", role: " + SessionData.getInstance().getRole() + ", userId: " + SessionData.getInstance().getUserId());
        showMessage("Đang tải danh sách sản phẩm...");
        if (isAdmin) {
            FxAsync.run(() -> {
                try {
                    pendingOnly = false;
                    return adminApi.allItems();
                } catch (vn.edu.auction.client.api.AuthApiClient.ApiException ex) {
                    // Server cũ chưa có /api/admin/items: vẫn lấy được danh sách chờ duyệt để duyệt sản phẩm
                    System.err.println("[ItemManagement] /api/admin/items lỗi (" + ex.getMessage() + "), chuyển sang /pending");
                    pendingOnly = true;
                    return adminApi.pendingItems();
                }
            }, all -> {
                allAdminItems = all;
                System.out.println("[ItemManagement] admin items nhận được: " + all.size());
                for (var item : all) {
                    System.out.println("  - id=" + item.getId() + " status=" + item.getStatus() + " name=" + item.getName());
                }
                long pendingCount = all.stream().filter(i -> i.getStatus() == ItemStatus.DRAFT || i.getStatus() == null).count();
                long upcomingCount = all.stream().filter(i -> i.getStatus() == ItemStatus.ACTIVE && (i.getAuctionStatus() == null || i.getAuctionStatus() == AuctionStatus.OPEN)).count();
                long activeCount = all.stream().filter(i -> i.getAuctionStatus() == AuctionStatus.RUNNING).count();
                long allCount = all.size();

                if (filterPendingBtn != null) filterPendingBtn.setText("Cần phê duyệt (" + pendingCount + ")");
                if (filterUpcomingBtn != null) filterUpcomingBtn.setText("Sắp bán (" + upcomingCount + ")");
                if (filterActiveBtn != null) filterActiveBtn.setText("Đang bán (" + activeCount + ")");
                if (filterAllBtn != null) filterAllBtn.setText("Tất cả (" + allCount + ")");

                applyFilter();
                if (pendingOnly) {
                    showMessage("Server chưa hỗ trợ xem toàn bộ sản phẩm nên chỉ hiện danh sách chờ duyệt ("
                            + all.size() + "). Deploy lại server để dùng đủ các bộ lọc.");
                }
            }, err -> {
                System.err.println("[ItemManagement] admin items lỗi: " + err.getMessage());
                err.printStackTrace();
                String msg = BaseApiClient.describe(err);
                showMessage("Lỗi: " + msg);
                new Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                    "Không thể tải danh sách sản phẩm:\n\n" + msg, 
                    javafx.scene.control.ButtonType.OK).showAndWait();
            });
        } else {
            Long me = SessionData.getInstance().getUserId();
            FxAsync.run(itemApi::getAllItems, all -> {
                System.out.println("[ItemManagement] seller items nhận được: " + all.size() + ", me=" + me);
                for (var item : all) {
                    System.out.println("  - id=" + item.getId() + " sellerId=" + item.getSellerId() + " status=" + item.getStatus() + " name=" + item.getName());
                }
                var filtered = all.stream().filter(i -> i.getSellerId().equals(me)).toList();
                System.out.println("[ItemManagement] sau filter theo sellerId: " + filtered.size());
                itemList.setAll(filtered);
                showMessage(itemList.isEmpty() ? "Bạn chưa có sản phẩm nào." : "Đã tải danh sách sản phẩm.");
            }, err -> {
                System.err.println("[ItemManagement] seller items lỗi: " + err.getMessage());
                err.printStackTrace();
                String msg = BaseApiClient.describe(err);
                showMessage("Lỗi: " + msg);
                new Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                    "Không thể tải danh sách sản phẩm:\n\n" + msg, 
                    javafx.scene.control.ButtonType.OK).showAndWait();
            });
        }
    }

    @FXML public void handleFilterPending() { currentFilter = AdminFilter.PENDING; applyFilter(); }
    @FXML public void handleFilterUpcoming() { currentFilter = AdminFilter.UPCOMING; applyFilter(); }
    @FXML public void handleFilterActive() { currentFilter = AdminFilter.ACTIVE; applyFilter(); }
    @FXML public void handleFilterAll() { currentFilter = AdminFilter.ALL; applyFilter(); }

    private void updateFilterButtonsUI() {
        setFilterBtnStyle(filterPendingBtn, currentFilter == AdminFilter.PENDING);
        setFilterBtnStyle(filterUpcomingBtn, currentFilter == AdminFilter.UPCOMING);
        setFilterBtnStyle(filterActiveBtn, currentFilter == AdminFilter.ACTIVE);
        setFilterBtnStyle(filterAllBtn, currentFilter == AdminFilter.ALL);
    }

    private void setFilterBtnStyle(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("primary-button", "outline-button");
        btn.getStyleClass().add(active ? "primary-button" : "outline-button");
    }

    private void applyFilter() {
        updateFilterButtonsUI();
        if (allAdminItems == null) {
            System.out.println("[ItemManagement] applyFilter - allAdminItems is null!");
            return;
        }
        List<ItemResponse> filtered = switch (currentFilter) {
            case PENDING -> allAdminItems.stream()
                    .filter(i -> i.getStatus() == ItemStatus.DRAFT || i.getStatus() == null)
                    .toList();
            case UPCOMING -> allAdminItems.stream()
                    .filter(i -> i.getStatus() == ItemStatus.ACTIVE && (i.getAuctionStatus() == null || i.getAuctionStatus() == AuctionStatus.OPEN))
                    .toList();
            case ACTIVE -> allAdminItems.stream()
                    .filter(i -> i.getAuctionStatus() == AuctionStatus.RUNNING)
                    .toList();
            case ALL -> allAdminItems;
        };
        System.out.println("[ItemManagement] applyFilter - filter: " + currentFilter + ", result size: " + filtered.size());
        itemList.setAll(filtered);
        showMessage("Hiển thị " + filtered.size() + " sản phẩm.");
    }

    @FXML
    public void handleApproveItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Vui lòng chọn một sản phẩm cần phê duyệt!");
            return;
        }
        if (selected.getStatus() != ItemStatus.DRAFT) {
            showMessage("Sản phẩm này không ở trạng thái chờ duyệt (đã được duyệt hoặc đã xử lý).");
            return;
        }
        String feedback = feedbackArea != null ? feedbackArea.getText().trim() : "";
        showMessage("Đang phê duyệt sản phẩm...");
        FxAsync.run(() -> {
            adminApi.approveItem(selected.getId(), feedback);
            return Boolean.TRUE;
        }, ok -> {
            showMessage("Đã phê duyệt sản phẩm \"" + selected.getName() + "\" và gửi thông báo (kèm feedback) tới người bán.");
            if (feedbackArea != null) feedbackArea.clear();
            clearForm();
            loadItems();
        }, err -> {
            if (err instanceof vn.edu.auction.client.api.AuthApiClient.ApiException e) {
                System.err.println("admin items lỗi: type=" + e.getType() + ", http=" + e.getStatusCode());
            } else {
                err.printStackTrace();
            }
            showMessage(BaseApiClient.describe(err));
        });
    }

    @FXML
    public void handleRejectItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Vui lòng chọn một sản phẩm để từ chối!");
            return;
        }
        if (selected.getStatus() != ItemStatus.DRAFT) {
            showMessage("Chỉ có thể từ chối sản phẩm đang ở trạng thái chờ duyệt.");
            return;
        }
        String feedback = feedbackArea != null ? feedbackArea.getText().trim() : "";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc muốn từ chối sản phẩm \"" + selected.getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            showMessage("Đang từ chối sản phẩm...");
            FxAsync.run(() -> {
                adminApi.rejectItem(selected.getId(), feedback);
                return Boolean.TRUE;
            }, ok -> {
                showMessage("Đã từ chối sản phẩm \"" + selected.getName() + "\" và gửi thông báo (kèm feedback) tới người bán.");
                if (feedbackArea != null) feedbackArea.clear();
                clearForm();
                loadItems();
            }, err -> showMessage(BaseApiClient.describe(err)));
        });
    }

    @FXML
    public void handleCreateItem() {
        ItemRequest request = readForm();
        if (request == null) return;
        FxAsync.run(() -> itemApi.createItem(request), created -> {
            showMessage("Đã thêm sản phẩm. Đang chờ quản trị viên phê duyệt.");
            clearForm();
            loadItems();
        }, err -> showMessage(BaseApiClient.describe(err)));
    }

    @FXML
    public void handleUpdateItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Vui lòng chọn một sản phẩm từ bảng để sửa!"); return; }
        ItemRequest request = readForm();
        if (request == null) return;
        FxAsync.run(() -> itemApi.updateItem(selected.getId(), request), updated -> {
            showMessage("Cập nhật sản phẩm thành công!");
            clearForm();
            loadItems();
        }, err -> showMessage(BaseApiClient.describe(err)));
    }

    @FXML
    public void handleDeleteItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Vui lòng chọn một sản phẩm để xóa!"); return; }
        boolean isAdmin = SessionData.getInstance().getRole() == UserRole.ADMIN;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc chắn muốn xóa sản phẩm \"" + selected.getName() + "\" không?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            showMessage("Đang xóa sản phẩm...");
            FxAsync.run(() -> {
                if (isAdmin) {
                    adminApi.deleteItem(selected.getId());
                } else {
                    itemApi.deleteItem(selected.getId());
                }
                return Boolean.TRUE;
            }, ok -> {
                showMessage("Xóa sản phẩm thành công!");
                clearForm();
                loadItems();
            }, err -> showMessage(BaseApiClient.describe(err)));
        });
    }

    /** Tạo phiên đấu giá cho sản phẩm ĐÃ ĐƯỢC DUYỆT. Giờ gửi lên là UTC (server dùng đồng hồ UTC). */
    @FXML
    public void handleCreateAuction() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Hãy chọn một sản phẩm để tạo phiên đấu giá."); return; }
        if (selected.getStatus() != ItemStatus.ACTIVE) {
            showMessage("Sản phẩm chưa được quản trị viên phê duyệt (hoặc đã có phiên / đã bán).");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("10");
        dialog.setTitle("Tạo phiên đấu giá");
        dialog.setHeaderText(selected.getName());
        dialog.setContentText("Thời lượng phiên (phút):");
        dialog.showAndWait().ifPresent(text -> {
            long minutes;
            try {
                minutes = Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                showMessage("Thời lượng phải là số nguyên (phút).");
                return;
            }
            if (minutes <= 0 || minutes > 60 * 24 * 30) { showMessage("Thời lượng phải từ 1 phút đến 30 ngày."); return; }
            LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
            AuctionRequest request = new AuctionRequest(selected.getId(), start, start.plusMinutes(minutes));
            FxAsync.run(() -> auctionApi.createAuction(request), created -> {
                showMessage("Đã tạo phiên đấu giá #" + created.getId() + ". Người theo dõi bạn sẽ nhận thông báo.");
                loadItems();
            }, err -> showMessage(BaseApiClient.describe(err)));
        });
    }

    @FXML
    public void clearForm() {
        nameField.clear();
        descriptionArea.clear();
        startingPriceField.clear();
        if (feedbackArea != null) feedbackArea.clear();
        itemTable.getSelectionModel().clearSelection();
    }

    public void showMessage(String message) {
        if (messageLabel != null) messageLabel.setText(message);
    }

    /** Kiểm tra cơ bản ở giao diện; server vẫn kiểm tra lại. */
    private ItemRequest readForm() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { showMessage("Tên sản phẩm không được để trống!"); return null; }
        BigDecimal price;
        try {
            price = new BigDecimal(startingPriceField.getText().trim());
        } catch (NumberFormatException e) {
            showMessage("Giá khởi điểm phải là một số hợp lệ!");
            return null;
        }
        if (price.signum() <= 0) { showMessage("Giá khởi điểm phải lớn hơn 0!"); return null; }
        return new ItemRequest(name, descriptionArea.getText().trim(), price);
    }

    @FXML
    public void handleBackToHome() {
        NavigationController.showBrowse();
    }
}
