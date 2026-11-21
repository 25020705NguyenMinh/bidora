package vn.edu.auction.client.browse;

import vn.edu.auction.client.I18n;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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

    /** Ba nhóm hiển thị trên sàn: OPEN = sắp diễn ra, RUNNING = đang diễn ra, còn lại = đã kết thúc. */
    private enum Tab { UPCOMING, LIVE, ENDED }
    private Tab tab = Tab.LIVE;
    @FXML private Button tabUpcoming;
    @FXML private Button tabLive;
    @FXML private Button tabEnded;
    private final Timeline ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> tick()));
    private long lastQuietRefresh;

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
            if (scene == null) ticker.stop(); else ticker.play(); // đồng hồ đếm ngược chỉ chạy khi trang đang hiển thị
        });
        ticker.setCycleCount(Animation.INDEFINITE);
        updateTabStyles();
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
        prepareLoad(true, "auction.loading");
        if (!loggedIn) {
            if (unsubscribe != null) { unsubscribe.run(); unsubscribe = null; }
        }
        subscribeIfNeeded();
        FxAsync.run(auctionApi::getAllAuctions, list -> {
            if (version != loadVersion) return;
            auctions.setAll(list);
            renderCards();
        }, err -> {
            if (version == loadVersion) showLoadError(err);
        });
    }

    private void renderCards() {
        String keyword = searchField.getText().strip().toLowerCase(Locale.ROOT);
        grid.getChildren().clear();
        visibleCards.clear();
        for (AuctionResponse a : auctions) {
            if (a.getItemName() == null || !a.getItemName().toLowerCase(Locale.ROOT).contains(keyword)) continue;
            if (tabOf(a) != tab) continue;
            MarketCard card = new MarketCard(a.getItemName(), I18n.get("auction.currentPrice"), status(effective(a)),
                    UiKit.money(a.getCurrentPrice()), secondary(a), I18n.get("auction.open"), () -> openFeatured(a));
            card.setProductImage(a.getImage(), a.getItemName());
            visibleCards.put(a.getId(), card);
            grid.getChildren().add(card);
        }
        showResultCount(visibleCards.size(), "auction");
    }

    private void onAuctionEvent(AuctionRealtimeClient.Event ev) {
        for (int i = 0; i < auctions.size(); i++) {
            AuctionResponse a = auctions.get(i);
            if (!a.getId().equals(ev.auctionId())) continue;
            Tab before = tabOf(a);
            a.setCurrentPrice(ev.currentPrice());
            a.setHighestBidderId(ev.highestBidderId());
            a.setStatus(ev.status());
            a.setEndTime(ev.endTime());
            auctions.set(i, a);
            if (tabOf(a) != before) { // ví dụ AUCTION_STARTED: phiên chuyển từ "Sắp diễn ra" sang "Đang diễn ra"
                renderCards();
                return;
            }
            MarketCard card = visibleCards.get(a.getId());
            if (card != null) card.updateQuote(status(effective(a)), UiKit.money(a.getCurrentPrice()), secondary(a));
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
        I18n.localize(detail.titleProperty(), "auction.details");
        detail.setHeaderText(selected.getItemName());

        Label id = new Label();
        id.getStyleClass().add("detail-caption");
        Label caption = I18n.caption(new Label(), "auction.currentPrice");
        caption.getStyleClass().add("detail-caption");
        Label price = new Label();
        price.getStyleClass().add("detail-price");
        VBox priceBlock = new VBox(4, caption, price);
        Label starting = new Label(I18n.get("auction.startingPrice", UiKit.money(selected.getStartingPrice())));
        var leader = new javafx.scene.control.Hyperlink();
        leader.setOnAction(e -> vn.edu.auction.client.ui.TradingProfileDialog.show(
                leader.getScene().getWindow(), selected.getHighestBidderId(), false));
        Label end = new Label();
        Label starts = new Label();
        VBox metadata = new VBox(10, starting, leader, starts, end);
        if (selected.getSellerId() != null) {
            var seller = I18n.caption(new javafx.scene.control.Hyperlink(), "profile.seller");
            seller.setOnAction(e -> vn.edu.auction.client.ui.TradingProfileDialog.show(
                    seller.getScene().getWindow(), selected.getSellerId(), true));
            metadata.getChildren().add(seller);
        }
        metadata.getStyleClass().add("detail-meta");
        Label note = new Label();
        note.getStyleClass().add("detail-note");
        note.setWrapText(true);
        VBox historyHolder = new VBox();

        // Ô đặt giá
        TextField amountField = new TextField(draft);
        I18n.localize(amountField.promptTextProperty(), "auction.bidPrompt");
        HBox.setHgrow(amountField, Priority.ALWAYS);
        Button bidButton = I18n.caption(new Button(), "auction.bid");
        bidButton.getStyleClass().add("primary-button");
        HBox bidRow = new HBox(8, amountField, bidButton);
        bidRow.setAlignment(Pos.CENTER_LEFT);

        // Hiển thị trạng thái hiện tại của phiên (dùng chung cho dữ liệu ban đầu và event realtime)
        java.util.function.Consumer<AuctionResponse> render = a -> {
            AuctionStatus current = effective(a);
            boolean notStarted = current == AuctionStatus.OPEN;
            boolean live = current == AuctionStatus.RUNNING;
            id.setText(I18n.get("auction.number") + a.getId() + "   •   " + status(current));
            starts.setManaged(notStarted);
            starts.setVisible(notStarted);
            if (notStarted) starts.setText(I18n.get("auction.startsPrefix", UiKit.dt(a.getStartTime()))
                    + " · " + I18n.get("auction.startsIn", countdown(a)));
            price.setText(UiKit.money(a.getCurrentPrice()));
            leader.setText(I18n.get("auction.leader", a.getHighestBidderId() == null ? "—" : "#" + a.getHighestBidderId()));
            leader.setDisable(a.getHighestBidderId() == null);
            end.setText(I18n.get("auction.ends", UiKit.dt(a.getEndTime())));
            bidRow.setDisable(!live); // phiên chưa bắt đầu: xem được sản phẩm nhưng chưa đặt giá
            if (notStarted) note.setText(I18n.get("auction.notStarted"));
            else if (!live) note.setText(a.getStatus() == AuctionStatus.CANCELED
                    ? I18n.get("auction.stopped") : I18n.get("auction.finished"));
            else if (I18n.get("auction.notStarted").equals(note.getText())) note.setText("");
        };
        render.accept(selected);
        I18n.onChange(price, () -> render.accept(selected));

        Runnable loadHistory = () -> FxAsync.run(() -> bidApi.getBidHistory(selected.getId()), (List<BidResponse> list) ->
                historyHolder.getChildren().setAll(new BidHistoryChart(selected.getStartingPrice(), selected.getStartTime(), list, false)),
                err -> I18n.track(note.textProperty(), () -> BaseApiClient.describe(err)));
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
                    }, err -> { bidButton.setDisable(false); I18n.track(note.textProperty(), () -> BaseApiClient.describe(err)); });
                });
                return;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountField.getText().trim());
            } catch (NumberFormatException ex) {
                I18n.localize(note.textProperty(), "auction.invalidBid");
                return;
            }
            bidButton.setDisable(true);
            FxAsync.run(() -> bidApi.placeBid(selected.getId(), new PlaceBidRequest(amount)),
                    res -> {
                        bidButton.setDisable(false);
                        amountField.clear();
                        I18n.localize(note.textProperty(), "auction.bidAccepted");
                        if (SessionData.getInstance().isPreviewMode()) {
                            selected.setCurrentPrice(res.getAmount());
                            selected.setHighestBidderId(res.getBidderId());
                            render.accept(selected);
                            MarketCard card = visibleCards.get(selected.getId());
                            if (card != null) card.updateQuote(status(effective(selected)),
                                    UiKit.money(selected.getCurrentPrice()), secondary(selected));
                            int index = auctions.indexOf(selected);
                            if (index >= 0) auctions.set(index, selected);
                        }
                        loadHistory.run();
                    },
                    err -> { bidButton.setDisable(false); I18n.track(note.textProperty(), () -> BaseApiClient.describe(err)); });
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
        // Đếm ngược trong hộp thoại: tới giờ thì tự mở ô đặt giá, không cần tải lại
        Timeline dialogTicker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
            if (selected.getStatus() == AuctionStatus.OPEN) render.accept(selected);
        }));
        dialogTicker.setCycleCount(Animation.INDEFINITE);
        dialogTicker.play();
        detail.setOnHidden(e -> { unsub.run(); dialogTicker.stop(); });

        var photo = vn.edu.auction.client.ui.ProductPhoto.view(selected.getImage(), selected.getItemName(), 210);
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
        vn.edu.auction.client.ui.UiMotion.install(detail);
        ButtonType close = new ButtonType(I18n.get("common.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        detail.getDialogPane().getButtonTypes().add(close);
        detail.show();
    }

    // ---------- phân loại / đồng hồ ----------
    /** Phiên OPEN mà đã tới giờ bắt đầu coi như đang diễn ra (scheduler server chuyển trạng thái trong ~1 giây). */
    private static AuctionStatus effective(AuctionResponse a) {
        if (a.getStatus() == AuctionStatus.OPEN && a.getStartTime() != null
                && !LocalDateTime.now(ZoneOffset.UTC).isBefore(a.getStartTime())) {
            return AuctionStatus.RUNNING;
        }
        return a.getStatus();
    }

    private static Tab tabOf(AuctionResponse a) {
        AuctionStatus st = effective(a);
        if (st == AuctionStatus.OPEN) return Tab.UPCOMING;
        if (st == AuctionStatus.RUNNING) return Tab.LIVE;
        return Tab.ENDED;
    }

    private static String secondary(AuctionResponse a) {
        if (effective(a) == AuctionStatus.OPEN) {
            return I18n.get("auction.startsPrefix", UiKit.dt(a.getStartTime())) + " · "
                    + I18n.get("auction.startsIn", countdown(a));
        }
        return I18n.get("auction.endsPrefix", UiKit.dt(a.getEndTime()));
    }

    private static String countdown(AuctionResponse a) {
        if (a.getStartTime() == null) return "—";
        long seconds = Math.max(0, Duration.between(LocalDateTime.now(ZoneOffset.UTC), a.getStartTime()).getSeconds());
        long days = seconds / 86400;
        String clock = String.format("%02d:%02d:%02d", (seconds % 86400) / 3600, (seconds % 3600) / 60, seconds % 60);
        return days > 0 ? days + (I18n.isVietnamese() ? " ngày " : "d ") + clock : clock;
    }

    /** Mỗi giây: cập nhật đồng hồ các thẻ "Sắp diễn ra"; tới giờ thì chuyển nhóm và đồng bộ lại với server. */
    private void tick() {
        boolean started = false;
        for (AuctionResponse a : auctions) {
            if (a.getStatus() != AuctionStatus.OPEN) continue;
            if (effective(a) != AuctionStatus.OPEN) { started = true; continue; }
            MarketCard card = visibleCards.get(a.getId());
            if (card != null) card.updateQuote(status(AuctionStatus.OPEN), UiKit.money(a.getCurrentPrice()), secondary(a));
        }
        long now = System.currentTimeMillis();
        if (started && loaded && now - lastQuietRefresh > 3000) {
            lastQuietRefresh = now;
            renderCards();
            long version = loadVersion;
            FxAsync.run(auctionApi::getAllAuctions, list -> {
                if (version != loadVersion) return;
                auctions.setAll(list);
                renderCards();
            }, err -> { });
        }
    }

    @FXML private void showUpcoming() { selectTab(Tab.UPCOMING); }
    @FXML private void showLive() { selectTab(Tab.LIVE); }
    @FXML private void showEnded() { selectTab(Tab.ENDED); }

    private void selectTab(Tab next) {
        tab = next;
        updateTabStyles();
        if (loaded) renderCards();
    }

    private void updateTabStyles() {
        style(tabUpcoming, tab == Tab.UPCOMING);
        style(tabLive, tab == Tab.LIVE);
        style(tabEnded, tab == Tab.ENDED);
    }

    private static void style(Button button, boolean selected) {
        if (button == null) return;
        button.getStyleClass().removeAll("primary-button", "outline-button");
        button.getStyleClass().add(selected ? "primary-button" : "outline-button");
    }

    private static String status(AuctionStatus value) {
        if (value == null) return "—";
        return switch (value) {
            case RUNNING -> I18n.get("status.live");
            case OPEN -> I18n.get("status.upcoming");
            case FINISHED -> I18n.get("status.ended");
            case PAID -> I18n.get("status.paid");
            case CANCELED -> I18n.get("status.cancelled");
        };
    }
}
