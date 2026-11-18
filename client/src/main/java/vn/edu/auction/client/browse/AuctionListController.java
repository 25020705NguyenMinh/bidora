package vn.edu.auction.client.browse;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import vn.edu.auction.client.ui.MarketCard;
import vn.edu.auction.client.api.AuctionApiClient;
import vn.edu.auction.client.api.AuctionRealtimeClient;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.BidApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.ui.BidHistoryChart;
import vn.edu.auction.client.ui.UiKit;
import vn.edu.auction.client.ui.WatchBar;
import vn.edu.auction.shared.dto.AuctionResponse;
import vn.edu.auction.shared.dto.BidResponse;
import vn.edu.auction.shared.dto.PlaceBidRequest;
import vn.edu.auction.shared.enums.AuctionStatus;

public class AuctionListController extends CatalogController {
    private final ObservableList<AuctionResponse> auctions = FXCollections.observableArrayList();
    private final AuctionApiClient auctionApi = new AuctionApiClient();
    private final BidApiClient bidApi = new BidApiClient();
    private static Runnable reloadHook = () -> { };
    private Runnable unsubscribe;
    private final Map<Long, MarketCard> visibleCards = new HashMap<>();
    private long loadVersion;

    /** Được BrowseController gọi khi đăng nhập / đăng xuất. */
    public static void reload() { reloadHook.run(); }

    @FXML private void initialize() {
        initializeCatalog(this::renderCards);
        grid.sceneProperty().addListener((obs, before, scene) -> {
            if (scene == null && unsubscribe != null) {
                unsubscribe.run();
                unsubscribe = null;
            } else if (scene != null) {
                subscribeIfNeeded();
            }
        });
        reloadHook = this::loadAuctions;
        loadAuctions();
    }

    ObservableList<AuctionResponse> auctions() { return auctions; }

    private void subscribeIfNeeded() {
        if (unsubscribe == null && SessionData.getInstance().isLoggedIn()
                && !SessionData.getInstance().isPreviewMode() && grid.getScene() != null) {
            unsubscribe = AuctionRealtimeClient.getInstance().subscribe(this::onAuctionEvent);
        }
    }

    @FXML public void loadAuctions() {
        long version = ++loadVersion;
        boolean loggedIn = SessionData.getInstance().isLoggedIn();
        auctions.clear();
        visibleCards.clear();
        prepareLoad(true, "Đang tải phiên đấu giá…");
        if (!loggedIn) {
            if (unsubscribe != null) { unsubscribe.run(); unsubscribe = null; }
        }
        subscribeIfNeeded();
        FxAsync.run(auctionApi::getAllAuctions, list -> {
            if (version != loadVersion) return;
            auctions.setAll(list);
            renderCards();
        }, err -> {
            if (version == loadVersion) showLoadError(BaseApiClient.describe(err));
        });
    }

    private void renderCards() {
        String keyword = searchField.getText().strip().toLowerCase(Locale.ROOT);
        grid.getChildren().clear();
        visibleCards.clear();
        for (AuctionResponse a : auctions) {
            if (a.getItemName() == null || !a.getItemName().toLowerCase(Locale.ROOT).contains(keyword)) continue;
            MarketCard card = new MarketCard(a.getItemName(), "Giá hiện tại", status(a.getStatus()),
                    money(a.getCurrentPrice()), "Kết thúc " + UiKit.dt(a.getEndTime()), "Xem phiên", () -> openFeatured(a));
            visibleCards.put(a.getId(), card);
            grid.getChildren().add(card);
        }
        showResultCount(visibleCards.size(), "phiên đấu giá");
    }

    private void onAuctionEvent(AuctionRealtimeClient.Event ev) {
        for (int i = 0; i < auctions.size(); i++) {
            AuctionResponse a = auctions.get(i);
            if (!a.getId().equals(ev.auctionId())) continue;
            a.setCurrentPrice(ev.currentPrice());
            a.setHighestBidderId(ev.highestBidderId());
            a.setStatus(ev.status());
            a.setEndTime(ev.endTime());
            MarketCard card = visibleCards.get(a.getId());
            if (card != null) card.updateQuote(status(a.getStatus()), money(a.getCurrentPrice()), "Kết thúc " + UiKit.dt(a.getEndTime()));
            auctions.set(i, a);
            return;
        }
    }

    void openFeatured(AuctionResponse selected) {
        openAuction(selected, "");
    }

    private void openAuction(AuctionResponse selected, String draft) {
        if (grid.getScene() == null) return;
        Dialog<Void> detail = new Dialog<>();
        detail.initOwner(grid.getScene().getWindow());
        detail.setTitle("Chi tiết phiên đấu giá");
        detail.setHeaderText(selected.getItemName());

        Label id = new Label();
        id.getStyleClass().add("detail-caption");
        Label caption = new Label("Giá hiện tại");
        caption.getStyleClass().add("detail-caption");
        Label price = new Label();
        price.getStyleClass().add("detail-price");
        VBox priceBlock = new VBox(4, caption, price);
        Label starting = new Label("Giá khởi điểm    " + money(selected.getStartingPrice()));
        Label leader = new Label();
        Label end = new Label();
        VBox metadata = new VBox(10, starting, leader, end);
        metadata.getStyleClass().add("detail-meta");
        Label note = new Label();
        note.getStyleClass().add("detail-note");
        note.setWrapText(true);
        VBox historyHolder = new VBox();

        // Ô đặt giá
        TextField amountField = new TextField(draft);
        amountField.setPromptText("Nhập giá bạn muốn đặt (đ)");
        HBox.setHgrow(amountField, Priority.ALWAYS);
        Button bidButton = new Button("Đặt giá");
        bidButton.getStyleClass().add("primary-button");
        HBox bidRow = new HBox(8, amountField, bidButton);
        bidRow.setAlignment(Pos.CENTER_LEFT);

        // Hiển thị trạng thái hiện tại của phiên (dùng chung cho dữ liệu ban đầu và event realtime)
        java.util.function.Consumer<AuctionResponse> render = a -> {
            id.setText("PHIÊN #" + a.getId() + "   •   " + status(a.getStatus()));
            price.setText(money(a.getCurrentPrice()));
            leader.setText("Người dẫn đầu    " + (a.getHighestBidderId() == null ? "—" : "#" + a.getHighestBidderId()));
            end.setText("Kết thúc              " + UiKit.dt(a.getEndTime()));
            boolean active = a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN;
            bidRow.setDisable(!active);
            if (!active) note.setText(a.getStatus() == AuctionStatus.CANCELED
                    ? "Phiên đã bị quản trị viên dừng." : "Phiên đã kết thúc.");
        };
        render.accept(selected);

        Runnable loadHistory = () -> FxAsync.run(() -> bidApi.getBidHistory(selected.getId()), (List<BidResponse> list) ->
                historyHolder.getChildren().setAll(new BidHistoryChart(selected.getStartingPrice(), selected.getStartTime(), list, false)),
                err -> note.setText(BaseApiClient.describe(err)));
        loadHistory.run();

        bidButton.setOnAction(e -> {
            if (!SessionData.getInstance().isLoggedIn()) {
                BrowseController.openLogin((javafx.stage.Stage) detail.getDialogPane().getScene().getWindow(), () -> {
                    if (!SessionData.getInstance().isLoggedIn()) return;
                    BrowseController.refreshCurrentAccount();
                    bidButton.setDisable(true);
                    FxAsync.run(() -> auctionApi.getAuctionById(selected.getId()), current -> {
                        if (!detail.isShowing()) return;
                        detail.close();
                        openAuction(current, amountField.getText());
                    }, err -> { bidButton.setDisable(false); note.setText(BaseApiClient.describe(err)); });
                });
                return;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountField.getText().trim());
            } catch (NumberFormatException ex) {
                note.setText("Số tiền không hợp lệ.");
                return;
            }
            bidButton.setDisable(true);
            FxAsync.run(() -> bidApi.placeBid(selected.getId(), new PlaceBidRequest(amount)),
                    res -> {
                        bidButton.setDisable(false);
                        amountField.clear();
                        note.setText(res.getMessage());
                        if (SessionData.getInstance().isPreviewMode()) {
                            selected.setCurrentPrice(res.getAmount());
                            selected.setHighestBidderId(res.getBidderId());
                            render.accept(selected);
                            MarketCard card = visibleCards.get(selected.getId());
                            if (card != null) card.updateQuote(status(selected.getStatus()),
                                    money(selected.getCurrentPrice()), "Kết thúc " + UiKit.dt(selected.getEndTime()));
                            int index = auctions.indexOf(selected);
                            if (index >= 0) auctions.set(index, selected);
                        }
                        loadHistory.run();
                    },
                    err -> { bidButton.setDisable(false); note.setText(BaseApiClient.describe(err)); });
        });

        // Realtime trong dialog: giá, người dẫn đầu, giờ kết thúc (gia hạn chống sniping), lịch sử bid
        Runnable unsub = !SessionData.getInstance().isLoggedIn() || SessionData.getInstance().isPreviewMode() ? () -> {} : AuctionRealtimeClient.getInstance().subscribe(ev -> {
            if (!selected.getId().equals(ev.auctionId())) return;
            selected.setCurrentPrice(ev.currentPrice());
            selected.setHighestBidderId(ev.highestBidderId());
            selected.setStatus(ev.status());
            selected.setEndTime(ev.endTime());
            render.accept(selected);
            loadHistory.run();
        });
        detail.setOnHidden(e -> unsub.run());

        var photo = MarketCard.photo(MarketCard.illustrationIndex(selected.getItemName()), 210);
        photo.setPrefWidth(230);
        photo.setMinWidth(200);
        photo.getStyleClass().add("detail-photo");
        VBox overview = new VBox(15, id, priceBlock, metadata);
        HBox.setHgrow(overview, Priority.ALWAYS);
        HBox summary = new HBox(20, photo, overview);
        VBox content = new VBox(16, summary, new WatchBar(selected.getId(), selected.getItemId()),
                bidRow, historyHolder, note);
        content.getStyleClass().add("detail-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(540);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("detail-scroll");
        detail.getDialogPane().setContent(scroll);
        detail.setResizable(true);
        vn.edu.auction.client.ui.UiTheme.apply(detail.getDialogPane());
        ButtonType close = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        detail.getDialogPane().getButtonTypes().add(close);
        detail.show();
    }

    private static String money(BigDecimal value) { return value == null ? "—" : NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ"; }
    private static String status(AuctionStatus value) {
        if (value == null) return "—";
        return switch (value) {
            case RUNNING -> "Đang diễn ra";
            case OPEN -> "Sắp diễn ra";
            case FINISHED -> "Đã kết thúc";
            case PAID -> "Đã thanh toán";
            case CANCELED -> "Đã hủy";
        };
    }
}
