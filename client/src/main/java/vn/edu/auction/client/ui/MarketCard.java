package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** A listing uses a labelled editorial photo only when the subject matches. */
public final class MarketCard extends VBox {
    private static final String[] PHOTOS = {"photo-camera", "photo-watch", "photo-headphones", "photo-chair"};
    private static final Pattern[] SUBJECTS = {
            Pattern.compile("\\b(may anh|camera|dslr|mirrorless)\\b"),
            Pattern.compile("\\b(dong ho|watch|wristwatch|chronograph)\\b"),
            Pattern.compile("\\b(tai nghe|headphones?|headsets?)\\b"),
            Pattern.compile("\\b(ghe|chair|armchair)\\b")
    };
    private final Label state = label("listing-status");
    private final Label price = label("listing-price");
    private final Label detail = label("listing-secondary");

    public MarketCard(String title, String caption, String status, String value, String secondary,
                      String action, Runnable onAction) {
        getStyleClass().add("listing-card");
        setMaxWidth(Double.MAX_VALUE);
        StackPane photo = photo(illustrationIndex(title), 180);
        Label name = label("listing-title");
        name.setText(title);
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setMaxHeight(52);
        Label priceCaption = label("listing-caption");
        priceCaption.setText(caption);
        Button open = new Button(action, UiKit.icon("M4 9 H16 M11 4 L16 9 L11 14"));
        open.getStyleClass().add("listing-action");
        open.setContentDisplay(ContentDisplay.RIGHT);
        open.setAccessibleText(action + " · " + title);
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(e -> onAction.run());
        VBox info = new VBox(7, state, name, new VBox(2, priceCaption, price), detail, open);
        info.getStyleClass().add("listing-info");
        getChildren().addAll(photo, info);
        updateQuote(status, value, secondary);

        if (photo.getChildren().get(0) instanceof Region art && art.getStyleClass().contains("listing-art")) {
            ScaleTransition zoom = new ScaleTransition(Duration.millis(200), art);
            zoom.setInterpolator(Interpolator.EASE_OUT);
            hoverProperty().addListener((obs, old, hovering) -> {
                zoom.stop();
                zoom.setToX(hovering ? 1.035 : 1);
                zoom.setToY(hovering ? 1.035 : 1);
                zoom.playFromStart();
            });
        }
    }

    public void setProductImage(String image, String name) {
        if (image != null && !image.isBlank()) getChildren().set(0, ProductPhoto.view(image, name, 180));
    }

    public void updateQuote(String status, String amount, String secondary) {
        state.setText(status);
        boolean changed = !price.getText().isEmpty() && !price.getText().equals(amount);
        price.setText(amount);
        detail.setText(secondary);
        if (changed) UiMotion.pulse(price);
    }

    public static int illustrationIndex(String name) {
        if (name == null) return -1;
        String folded = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd');
        for (int i = 0; i < SUBJECTS.length; i++) {
            if (SUBJECTS[i].matcher(folded).find()) return i;
        }
        return -1;
    }

    public static StackPane photo(int index, double height) {
        StackPane photo = new StackPane();
        photo.getStyleClass().add("listing-photo");
        photo.setMinHeight(height);
        photo.setPrefHeight(height);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(photo.widthProperty());
        clip.heightProperty().bind(photo.heightProperty());
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        photo.setClip(clip);
        if (index < 0 || index >= PHOTOS.length) {
            Label empty = new Label(I18n.get("item.noPhoto"), UiKit.icon(
                    "M3 4 H21 V20 H3 Z M3 16 L9 10 L15 16 L18 13 L21 16 M15 8 H16"));
            empty.getStyleClass().add("listing-secondary");
            empty.setContentDisplay(ContentDisplay.TOP);
            empty.setGraphicTextGap(9);
            photo.getStyleClass().add("listing-skeleton-image");
            photo.getChildren().add(empty);
        } else {
            Region art = new Region();
            art.getStyleClass().addAll("listing-art", PHOTOS[index]);
            Label note = I18n.caption(new Label(), "item.illustration");
            note.getStyleClass().add("listing-image-note");
            StackPane.setAlignment(note, Pos.BOTTOM_LEFT);
            StackPane.setMargin(note, new Insets(0, 0, 8, 8));
            photo.getChildren().addAll(art, note);
        }
        return photo;
    }

    public static VBox loading() {
        Region image = new Region();
        image.getStyleClass().add("listing-skeleton-image");
        image.setPrefHeight(180);
        Region title = new Region();
        title.getStyleClass().add("listing-skeleton-line");
        title.setMaxWidth(160);
        Region price = new Region();
        price.getStyleClass().add("listing-skeleton-line");
        price.setMaxWidth(100);
        VBox info = new VBox(10, title, price);
        info.getStyleClass().add("listing-info");
        VBox card = new VBox(image, info);
        card.getStyleClass().add("listing-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label label(String styleClass) {
        Label label = new Label();
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
