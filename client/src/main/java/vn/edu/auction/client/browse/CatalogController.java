package vn.edu.auction.client.browse;

import vn.edu.auction.client.I18n;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import vn.edu.auction.client.ui.MarketCard;
import vn.edu.auction.client.ui.UiKit;

/** Shared layout and mutually exclusive guest, loading, result and error states. */
abstract class CatalogController {
    @FXML protected TextField searchField;
    @FXML protected TilePane grid;
    @FXML private ScrollPane catalogScroll;
    @FXML private HBox tools;
    @FXML private HBox guestPanel;
    @FXML private Label messageLabel;
    @FXML private Button refreshButton;
    protected boolean loaded;

    protected void initializeCatalog(Runnable filter) {
        I18n.onChange(searchField, () -> { if (loaded) filter.run(); });
        refreshButton.setGraphic(UiKit.icon("M18 6 A7 7 0 1 0 19 13 M18 2 V7 H13"));
        searchField.textProperty().addListener((obs, old, value) -> {
            if (loaded) filter.run();
        });
        catalogScroll.viewportBoundsProperty().addListener((obs, old, viewport) -> {
            Insets padding = grid.getInsets();
            double width = viewport.getWidth() - padding.getLeft() - padding.getRight();
            if (width <= 0) return;
            double gap = grid.getHgap();
            int columns = Math.max(1, (int) ((width + gap) / (245 + gap)));
            grid.setPrefTileWidth(Math.floor((width - gap * (columns - 1)) / columns));
        });
    }

    @FXML protected void signIn() { BrowseController.requestLogin(guestPanel); }

    protected void prepareLoad(boolean canBrowse, String loadingKey) {
        loaded = false;
        grid.getChildren().clear();
        if (guestPanel != null) show(guestPanel, !canBrowse);
        show(tools, canBrowse);
        show(catalogScroll, canBrowse);
        show(messageLabel, canBrowse);
        searchField.setDisable(true);
        refreshButton.setDisable(true);
        if (canBrowse) {
            I18n.localize(messageLabel.textProperty(), loadingKey);
            for (int i = 0; i < 4; i++) grid.getChildren().add(MarketCard.loading());
        } else {
            searchField.clear();
        }
    }

    protected void showResultCount(int count, String group) {
        int delay = 0;
        for (Node card : grid.getChildren()) {
            vn.edu.auction.client.ui.UiMotion.reveal(card, 0, 10, delay);
            delay = Math.min(delay + 28, 196);
        }
        loaded = true;
        searchField.setDisable(false);
        refreshButton.setDisable(false);
        String query = searchField.getText().strip();
        String noun = I18n.get(group + ".noun");
        if (count == 0 && !query.isEmpty()) {
            I18n.localize(messageLabel.textProperty(), "catalog.noMatch", noun, query);
        } else {
            messageLabel.setText(count == 0 ? I18n.get("catalog.empty", noun) : I18n.get(group + ".count", count));
        }
    }

    protected void showLoadError(Throwable error) {
        loaded = false;
        grid.getChildren().clear();
        refreshButton.setDisable(false);
        I18n.track(messageLabel.textProperty(), () -> vn.edu.auction.client.api.BaseApiClient.describe(error));
    }

    private static void show(Node node, boolean visible) {
        node.setManaged(visible);
        node.setVisible(visible);
    }
}
