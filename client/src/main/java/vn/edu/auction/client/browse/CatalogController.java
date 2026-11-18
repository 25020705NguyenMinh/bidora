package vn.edu.auction.client.browse;

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

    protected void prepareLoad(boolean canBrowse, String loadingText) {
        loaded = false;
        grid.getChildren().clear();
        if (guestPanel != null) show(guestPanel, !canBrowse);
        show(tools, canBrowse);
        show(catalogScroll, canBrowse);
        show(messageLabel, canBrowse);
        searchField.setDisable(true);
        refreshButton.setDisable(true);
        if (canBrowse) {
            messageLabel.setText(loadingText);
            for (int i = 0; i < 4; i++) grid.getChildren().add(MarketCard.loading());
        } else {
            searchField.clear();
        }
    }

    protected void showResultCount(int count, String noun) {
        loaded = true;
        searchField.setDisable(false);
        refreshButton.setDisable(false);
        String query = searchField.getText().strip();
        if (count == 0 && !query.isEmpty()) {
            messageLabel.setText("Không tìm thấy " + noun + " cho “" + query + "”.");
        } else {
            messageLabel.setText(count == 0 ? "Chưa có " + noun + " nào." : count + " " + noun);
        }
    }

    protected void showLoadError(String message) {
        loaded = false;
        grid.getChildren().clear();
        refreshButton.setDisable(false);
        messageLabel.setText(message);
    }

    private static void show(Node node, boolean visible) {
        node.setManaged(visible);
        node.setVisible(visible);
    }
}
