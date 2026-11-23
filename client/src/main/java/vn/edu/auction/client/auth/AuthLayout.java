package vn.edu.auction.client.auth;

import javafx.css.PseudoClass;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import vn.edu.auction.client.ui.UiMotion;

/** Keeps the form usable on small screens and the watch face in frame on larger ones. */
public final class AuthLayout extends BorderPane {
    private static final Image WATCH = new Image(AuthLayout.class.getResource(
            "/vn/edu/auction/client/icons/placeholder-watch.jpg").toExternalForm());
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private final StackPane formArea = new StackPane();

    public AuthLayout() {
        getStyleClass().add("auth-root");
        setPrefSize(880, 592);
        ScrollPane scroll = new ScrollPane(formArea);
        scroll.getStyleClass().add("auth-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinSize(0, 0);
        setCenter(scroll);

        ImageView photo = new ImageView(WATCH);
        photo.setManaged(false);
        Pane artwork = new Pane(photo);
        artwork.setMouseTransparent(true);
        artwork.prefWidthProperty().bind(widthProperty().multiply(0.39));
        artwork.visibleProperty().bind(widthProperty().greaterThanOrEqualTo(760));
        artwork.managedProperty().bind(artwork.visibleProperty());
        artwork.layoutBoundsProperty().addListener((obs, before, bounds) -> {
            double w = bounds.getWidth(), h = bounds.getHeight();
            if (w <= 0 || h <= 0) return;
            double scale = Math.max(w / WATCH.getWidth(), h / WATCH.getHeight());
            double cropW = w / scale, cropH = h / scale;
            photo.setViewport(new Rectangle2D((WATCH.getWidth() - cropW) / 2,
                    (WATCH.getHeight() - cropH) / 2, cropW, cropH));
            photo.setFitWidth(w);
            photo.setFitHeight(h);
        });
        setRight(artwork);
        widthProperty().addListener((obs, oldWidth, width) ->
                pseudoClassStateChanged(COMPACT, width.doubleValue() < 490));
    }

    public Region getForm() {
        return formArea.getChildren().isEmpty() ? null : (Region) formArea.getChildren().get(0);
    }

    public void setForm(Region form) {
        form.setMaxWidth(560);
        form.setMinHeight(USE_PREF_SIZE);
        form.setMaxHeight(USE_PREF_SIZE);
        formArea.getChildren().setAll(form);
        form.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) UiMotion.reveal(form, 0, 6, 0);
        });
    }
}
