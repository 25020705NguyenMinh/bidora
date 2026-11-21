package vn.edu.auction.client.browse;

import vn.edu.auction.client.I18n;

import java.util.List;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.ItemApiClient;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.ui.MarketCard;
import vn.edu.auction.client.ui.UiKit;
import vn.edu.auction.client.ui.UiTheme;
import vn.edu.auction.shared.dto.ItemResponse;
import vn.edu.auction.shared.enums.ItemStatus;

public class ItemListController extends CatalogController {
    private final ItemApiClient itemApi = new ItemApiClient();
    private List<ItemResponse> items = List.of();
    private long loadVersion;
    private static Runnable reloadHook = () -> {};

    @FXML private void initialize() {
        initializeCatalog(this::renderCards);
        reloadHook = this::loadItems;
        loadItems();
    }

    public static void reload() { reloadHook.run(); }

    @FXML public void loadItems() {
        long version = ++loadVersion;
        boolean loggedIn = SessionData.getInstance().isLoggedIn();
        items = List.of();
        prepareLoad(loggedIn, "item.loading");
        if (!loggedIn) return;
        FxAsync.run(itemApi::getAllItems, list -> {
            if (version != loadVersion) return;
            items = list;
            renderCards();
        }, error -> {
            if (version == loadVersion) showLoadError(error);
        });
    }

    private void renderCards() {
        String query = searchField.getText().strip().toLowerCase(Locale.ROOT);
        grid.getChildren().clear();
        for (ItemResponse item : items) {
            if (item.getName() == null || !item.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            MarketCard card = new MarketCard(item.getName(), I18n.get("item.startingPrice"), status(item.getStatus()),
                    UiKit.money(item.getStartingPrice()), I18n.get("item.sellerId") + item.getSellerId(),
                    I18n.get("item.open"), () -> showDetail(item));
            card.setProductImage(item.getImage(), item.getName());
            grid.getChildren().add(card);
        }
        showResultCount(grid.getChildren().size(), "item");
    }

    private void showDetail(ItemResponse item) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(grid.getScene().getWindow());
        I18n.localize(dialog.titleProperty(), "item.details");
        dialog.setHeaderText(item.getName());
        Label caption = I18n.caption(new Label(), "item.startingPrice");
        caption.getStyleClass().add("detail-caption");
        Label price = new Label(UiKit.money(item.getStartingPrice()));
        price.getStyleClass().add("detail-price");
        Label description = new Label(item.getDescription() == null || item.getDescription().isBlank()
                ? I18n.get("item.noDescription") : item.getDescription());
        description.setWrapText(true);
        var photo = vn.edu.auction.client.ui.ProductPhoto.view(item.getImage(), item.getName(), 210);
        photo.getStyleClass().add("detail-photo");
        VBox content = new VBox(12, photo, new Label(status(item.getStatus())), new VBox(2, caption, price), description);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(460);
        scroll.setPrefViewportHeight(395);
        scroll.getStyleClass().add("detail-scroll");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType(I18n.get("common.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setResizable(true);
        UiTheme.apply(dialog.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(dialog);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/vn/edu/auction/client/css/catalog.css").toExternalForm());
        dialog.show();
    }

    private static String status(ItemStatus value) {
        if (value == null) return I18n.get("status.unknown");
        return switch (value) {
            case ACTIVE -> I18n.get("status.listed");
            case DRAFT -> I18n.get("status.pending");
            case SOLD -> I18n.get("status.sold");
            case REMOVED -> I18n.get("status.removed");
        };
    }
}
