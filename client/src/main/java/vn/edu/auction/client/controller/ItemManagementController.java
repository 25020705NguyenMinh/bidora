package vn.edu.auction.client.controller;

import vn.edu.auction.client.I18n;

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
    @FXML private javafx.scene.layout.StackPane productPhoto;
    @FXML private Button chooseImageButton, removeImageButton;
    private String selectedImage = "";

    private void showImage(String image) {
        selectedImage = image == null ? "" : image;
        productPhoto.getChildren().setAll(vn.edu.auction.client.ui.ProductPhoto.view(selectedImage, "", 140));
    }

    @FXML private void chooseImage() {
        try {
            String image = vn.edu.auction.client.ui.ProductPhoto.choose(nameField.getScene().getWindow());
            if (image != null) showImage(image);
        } catch (java.io.IOException | RuntimeException ex) { showLocalizedMessage("product.imageError"); }
    }

    @FXML private void removeImage() { showImage(""); }

    @FXML private TextArea descriptionArea;
    @FXML private TextField startingPriceField;
    @FXML private VBox feedbackBox;
    @FXML private TextArea feedbackArea;

    @FXML private Button createItemButton;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;
    @FXML private javafx.scene.layout.FlowPane bottomActionRow;
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

    private final boolean isAdmin;

    public ItemManagementController() { this(false); }
    public ItemManagementController(boolean moderation) {
        isAdmin = moderation && SessionData.getInstance().getRole() == UserRole.ADMIN;
    }

    @FXML
    public void initialize() {

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
                showImage(newSelection.getImage());
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

        I18n.onChange(itemTable, itemTable::refresh);
        showImage("");
        chooseImageButton.setDisable(isAdmin);
        removeImageButton.setDisable(isAdmin);
        setupRoleView(isAdmin);
        loadItems();
    }

    private void setupRoleView(boolean isAdmin) {
        if (isAdmin) {
            if (titleLabel != null) I18n.localize(titleLabel.textProperty(), "manage.title");
            if (subtitleLabel != null) I18n.localize(subtitleLabel.textProperty(), "manage.adminIntro");
            if (tableTitleLabel != null) I18n.localize(tableTitleLabel.textProperty(), "manage.inventory");
            if (tableSubtitleLabel != null) I18n.localize(tableSubtitleLabel.textProperty(), "manage.selectReview");
            if (formTitleLabel != null) I18n.localize(formTitleLabel.textProperty(), "manage.review");
            if (formSubtitleLabel != null) I18n.localize(formSubtitleLabel.textProperty(), "manage.feedbackHint");

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
            if (titleLabel != null) I18n.localize(titleLabel.textProperty(), "seller.open");
            if (subtitleLabel != null) I18n.localize(subtitleLabel.textProperty(), "manage.sellerIntro");
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
                I18n.localize(deleteButton.textProperty(), "common.delete");
            }
        }
    }

    private static String statusText(ItemResponse i) {
        if (i == null || i.getStatus() == null) return "—";
        if (i.getStatus() == ItemStatus.DRAFT) return I18n.get("status.pending");
        if (i.getStatus() == ItemStatus.ACTIVE) {
            if (i.getAuctionStatus() == null) return I18n.get("manage.approvedWaiting");
            if (i.getAuctionStatus() == AuctionStatus.OPEN) return I18n.get("manage.scheduled");
            if (i.getAuctionStatus() == AuctionStatus.RUNNING) return I18n.get("manage.live");
            if (i.getAuctionStatus() == AuctionStatus.FINISHED || i.getAuctionStatus() == AuctionStatus.PAID) return I18n.get("status.ended");
            if (i.getAuctionStatus() == AuctionStatus.CANCELED) return I18n.get("manage.cancelled");
            return I18n.get("status.approved");
        }
        if (i.getStatus() == ItemStatus.SOLD) return I18n.get("status.sold");
        if (i.getStatus() == ItemStatus.REMOVED) return I18n.get("status.removed");
        return i.getStatus().name();
    }

    public void loadItems() {
        System.out.println("[ItemManagement] loadItems - isAdmin: " + isAdmin + ", role: " + SessionData.getInstance().getRole() + ", userId: " + SessionData.getInstance().getUserId());
        showLocalizedMessage("manage.loading");
        if (isAdmin) {
            FxAsync.run(() -> {
                try {
                    pendingOnly = false;
                    return adminApi.allItems();
                } catch (vn.edu.auction.client.api.AuthApiClient.ApiException ex) {
                    // Server cũ chưa có /api/admin/items: vẫn lấy được danh sách chờ duyệt để duyệt sản phẩm
                    System.err.println("[ItemManagement] /api/admin/items loi (" + ex.getMessage() + "), chuyen sang /pending");
                    pendingOnly = true;
                    return adminApi.pendingItems();
                }
            }, all -> {
                allAdminItems = all;
                System.out.println("[ItemManagement] admin items nhan duoc: " + all.size());
                for (var item : all) {
                    System.out.println("  - id=" + item.getId() + " status=" + item.getStatus());
                }
                long pendingCount = all.stream().filter(i -> i.getStatus() == ItemStatus.DRAFT || i.getStatus() == null).count();
                long upcomingCount = all.stream().filter(i -> i.getStatus() == ItemStatus.ACTIVE && (i.getAuctionStatus() == null || i.getAuctionStatus() == AuctionStatus.OPEN)).count();
                long activeCount = all.stream().filter(i -> i.getAuctionStatus() == AuctionStatus.RUNNING).count();
                long allCount = all.size();

                if (filterPendingBtn != null) I18n.localize(filterPendingBtn.textProperty(), "manage.pendingFilter", pendingCount);
                if (filterUpcomingBtn != null) I18n.localize(filterUpcomingBtn.textProperty(), "manage.upcomingFilter", upcomingCount);
                if (filterActiveBtn != null) I18n.localize(filterActiveBtn.textProperty(), "manage.liveFilter", activeCount);
                if (filterAllBtn != null) I18n.localize(filterAllBtn.textProperty(), "manage.allFilter", allCount);

                applyFilter();
                if (pendingOnly) {
                    showLocalizedMessage("manage.limited", all.size());
                }
            }, err -> {
                System.err.println("[ItemManagement] admin items loi: " + err.getMessage());
                err.printStackTrace();
                String msg = BaseApiClient.describe(err);
                showLocalizedMessage("common.errorPrefix", msg);
                new Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                    I18n.get("manage.loadError") + msg,
                    javafx.scene.control.ButtonType.OK).showAndWait();
            });
        } else {
            Long me = SessionData.getInstance().getUserId();
            FxAsync.run(itemApi::getAllItems, all -> {
                System.out.println("[ItemManagement] seller items nhan duoc: " + all.size() + ", me=" + me);
                for (var item : all) {
                    System.out.println("  - id=" + item.getId() + " sellerId=" + item.getSellerId() + " status=" + item.getStatus());
                }
                var filtered = all.stream().filter(i -> i.getSellerId().equals(me)).toList();
                System.out.println("[ItemManagement] sau filter theo sellerId: " + filtered.size());
                itemList.setAll(filtered);
                showLocalizedMessage(itemList.isEmpty() ? "manage.empty" : "manage.loaded");
            }, err -> {
                System.err.println("[ItemManagement] seller items loi: " + err.getMessage());
                err.printStackTrace();
                String msg = BaseApiClient.describe(err);
                showLocalizedMessage("common.errorPrefix", msg);
                new Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                    I18n.get("manage.loadError") + msg,
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
        showLocalizedMessage("item.count", filtered.size());
    }

    @FXML
    public void handleApproveItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showLocalizedMessage("manage.pickApprove");
            return;
        }
        if (selected.getStatus() != ItemStatus.DRAFT) {
            showLocalizedMessage("manage.noLongerPending");
            return;
        }
        String feedback = feedbackArea != null ? feedbackArea.getText().trim() : "";
        showLocalizedMessage("manage.approving");
        FxAsync.run(() -> {
            adminApi.approveItem(selected.getId(), feedback);
            return Boolean.TRUE;
        }, ok -> {
            showLocalizedMessage("manage.approvedItem", selected.getName());
            if (feedbackArea != null) feedbackArea.clear();
            clearForm();
            loadItems();
        }, err -> {
            if (err instanceof vn.edu.auction.client.api.AuthApiClient.ApiException e) {
                System.err.println("admin items loi: type=" + e.getType() + ", http=" + e.getStatusCode());
            } else {
                err.printStackTrace();
            }
            I18n.track(messageLabel.textProperty(), () -> BaseApiClient.describe(err));
        });
    }

    @FXML
    public void handleRejectItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showLocalizedMessage("manage.pickReject");
            return;
        }
        if (selected.getStatus() != ItemStatus.DRAFT) {
            showLocalizedMessage("manage.pendingOnly");
            return;
        }
        String feedback = feedbackArea != null ? feedbackArea.getText().trim() : "";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("manage.rejectConfirm", selected.getName()),
                ButtonType.YES, ButtonType.NO);
        vn.edu.auction.client.ui.UiTheme.apply(confirm.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(confirm);
        confirm.setHeaderText(null);
        confirm.setTitle(I18n.get("common.confirm"));
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            showLocalizedMessage("manage.rejecting");
            FxAsync.run(() -> {
                adminApi.rejectItem(selected.getId(), feedback);
                return Boolean.TRUE;
            }, ok -> {
                showLocalizedMessage("manage.rejectedItem", selected.getName());
                if (feedbackArea != null) feedbackArea.clear();
                clearForm();
                loadItems();
            }, err -> I18n.track(messageLabel.textProperty(), () -> BaseApiClient.describe(err)));
        });
    }

    @FXML
    public void handleCreateItem() {
        ItemRequest request = readForm();
        if (request == null) return;
        FxAsync.run(() -> itemApi.createItem(request), created -> {
            showLocalizedMessage("manage.submitted");
            clearForm();
            loadItems();
        }, err -> I18n.track(messageLabel.textProperty(), () -> BaseApiClient.describe(err)));
    }

    @FXML
    public void handleUpdateItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showLocalizedMessage("manage.pickEdit"); return; }
        ItemRequest request = readForm();
        if (request == null) return;
        FxAsync.run(() -> itemApi.updateItem(selected.getId(), request), updated -> {
            showLocalizedMessage("manage.updated");
            clearForm();
            loadItems();
        }, err -> I18n.track(messageLabel.textProperty(), () -> BaseApiClient.describe(err)));
    }

    @FXML
    public void handleDeleteItem() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showLocalizedMessage("manage.pickDelete"); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("manage.deleteConfirm", selected.getName()),
                ButtonType.YES, ButtonType.NO);
        vn.edu.auction.client.ui.UiTheme.apply(confirm.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(confirm);
        confirm.setHeaderText(null);
        confirm.setTitle(I18n.get("common.confirm"));
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            showLocalizedMessage("manage.deleting");
            FxAsync.run(() -> {
                if (isAdmin) {
                    adminApi.deleteItem(selected.getId());
                } else {
                    itemApi.deleteItem(selected.getId());
                }
                return Boolean.TRUE;
            }, ok -> {
                showLocalizedMessage("manage.deleted");
                clearForm();
                loadItems();
            }, err -> I18n.track(messageLabel.textProperty(), () -> BaseApiClient.describe(err)));
        });
    }

    /** Tạo phiên đấu giá cho sản phẩm ĐÃ ĐƯỢC DUYỆT. Giờ gửi lên là UTC (server dùng đồng hồ UTC). */
    @FXML
    public void handleCreateAuction() {
        ItemResponse selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showLocalizedMessage("manage.pickAuction"); return; }
        if (selected.getStatus() != ItemStatus.ACTIVE) {
            showLocalizedMessage("manage.unavailable");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("10");
        I18n.localize(dialog.titleProperty(), "manage.createAuction");
        dialog.setHeaderText(selected.getName());
        dialog.setContentText(I18n.get("manage.duration"));
        vn.edu.auction.client.ui.UiTheme.apply(dialog.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(dialog);
        dialog.showAndWait().ifPresent(text -> {
            long minutes;
            try {
                minutes = Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                showLocalizedMessage("manage.wholeMinutes");
                return;
            }
            if (minutes <= 0 || minutes > 60 * 24 * 30) { showLocalizedMessage("manage.durationRange"); return; }
            LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
            AuctionRequest request = new AuctionRequest(selected.getId(), start, start.plusMinutes(minutes));
            FxAsync.run(() -> auctionApi.createAuction(request), created -> {
                showLocalizedMessage("manage.created", created.getId().toString());
                loadItems();
            }, err -> I18n.track(messageLabel.textProperty(), () -> BaseApiClient.describe(err)));
        });
    }

    @FXML
    public void clearForm() {
        showImage("");
        nameField.clear();
        descriptionArea.clear();
        startingPriceField.clear();
        if (feedbackArea != null) feedbackArea.clear();
        itemTable.getSelectionModel().clearSelection();
    }

    private void showLocalizedMessage(String key, Object... args) {
        I18n.localize(messageLabel.textProperty(), key, args);
    }

    public void showMessage(String message) {
        if (messageLabel != null) messageLabel.setText(message);
    }

    /** Kiểm tra cơ bản ở giao diện; server vẫn kiểm tra lại. */
    private ItemRequest readForm() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { showLocalizedMessage("manage.nameRequired"); return null; }
        BigDecimal price;
        try {
            price = new BigDecimal(startingPriceField.getText().trim());
        } catch (NumberFormatException e) {
            showLocalizedMessage("manage.validPrice");
            return null;
        }
        if (price.signum() <= 0) { showLocalizedMessage("manage.positivePrice"); return null; }
        ItemRequest request = new ItemRequest(name, descriptionArea.getText().trim(), price);
        request.setImage(selectedImage);
        return request;
    }

    @FXML
    public void handleBackToHome() {
        NavigationController.showBrowse();
    }
}
