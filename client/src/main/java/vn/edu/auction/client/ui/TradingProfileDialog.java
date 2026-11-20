package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.UserApiClient;
import vn.edu.auction.shared.dto.TradingProfiles;
import vn.edu.auction.shared.dto.TradingProfiles.Card;

public final class TradingProfileDialog {
    private TradingProfileDialog() {}

    private static Dialog<Void> dialog(Window owner, String titleKey, VBox content) {
        Dialog<Void> d = new Dialog<>();
        d.initOwner(owner);
        I18n.localize(d.titleProperty(), titleKey);
        content.setPadding(new Insets(14));
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiTheme.apply(d.getDialogPane());
        vn.edu.auction.client.ui.UiMotion.install(d);
        d.show();
        return d;
    }

    public static void show(Window owner, long userId, boolean seller) {
        Label status = I18n.caption(new Label(), "profile.loading");
        VBox content = new VBox(10, status);
        Dialog<Void> d = dialog(owner, seller ? "profile.seller" : "profile.buyer", content);
        FxAsync.run(() -> new UserApiClient().publicProfile(userId, seller), card -> {
            if (!d.isShowing()) return;
            Label name = new Label(card.name());
            name.getStyleClass().add("section-title");
            Label bio = new Label(card.bio());
            bio.setWrapText(true);
            bio.setMaxWidth(360);
            content.getChildren().setAll(name);
            if (!card.avatar().isEmpty()) content.getChildren().add(0, portrait(card.avatar()));
            if (!card.bio().isEmpty()) content.getChildren().add(bio);
            d.getDialogPane().getScene().getWindow().sizeToScene();
        }, err -> I18n.track(status.textProperty(), () -> BaseApiClient.describe(err)));
    }

    public static void edit(Window owner) { edit(owner, () -> {}); }

    public static void edit(Window owner, Runnable saved) {
        UserApiClient api = new UserApiClient();
        Label status = I18n.caption(new Label(), "profile.loading");
        VBox content = new VBox(10, status);
        Dialog<Void> d = dialog(owner, "profile.title", content);
        FxAsync.run(api::tradingProfiles, profiles -> {
            if (!d.isShowing()) return;
            Editor buyer = new Editor(profiles.buyer(), status);
            Editor seller = new Editor(profiles.seller(), status);
            CheckBox split = I18n.caption(new CheckBox(), "profile.separate");
            split.getStyleClass().add("profile-separation");
            split.setSelected(profiles.separate());
            Tab buyerTab = new Tab("", buyer);
            buyerTab.setGraphic(new UiIcon(UiIcon.Kind.USER));
            Tab sellerTab = new Tab("", seller);
            sellerTab.setGraphic(new UiIcon(UiIcon.Kind.SELL));
            I18n.localize(buyerTab.textProperty(), split.isSelected() ? "role.buyer" : "profile.shared");
            I18n.localize(sellerTab.textProperty(), "role.seller");
            TabPane tabs = new TabPane(buyerTab);
            tabs.getStyleClass().add("profile-tabs");
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            if (split.isSelected()) tabs.getTabs().add(sellerTab);
            boolean[] copyOnFirstSplit = {!profiles.separate() && profiles.buyer().equals(profiles.seller())};
            split.setOnAction(e -> {
                if (split.isSelected()) {
                    if (copyOnFirstSplit[0]) seller.setCard(buyer.card());
                    copyOnFirstSplit[0] = false;
                    tabs.getTabs().add(sellerTab);
                    I18n.localize(buyerTab.textProperty(), "role.buyer");
                } else {
                    tabs.getTabs().remove(sellerTab);
                    I18n.localize(buyerTab.textProperty(), "profile.shared");
                }
            });
            Button save = I18n.caption(new Button(), "profile.save");
            save.setGraphic(new UiIcon(UiIcon.Kind.SAVE));
            save.getStyleClass().add("primary-button");
            save.setOnAction(e -> {
                Card sale = copyOnFirstSplit[0] ? buyer.card() : seller.card();
                TradingProfiles data = new TradingProfiles(split.isSelected(), buyer.card(), sale);
                content.setDisable(true);
                I18n.localize(status.textProperty(), "common.saving");
                FxAsync.run(() -> api.saveTradingProfiles(data), result -> { vn.edu.auction.client.model.SessionData.getInstance().setTradingProfiles(result); saved.run(); d.close(); }, err -> {
                    content.setDisable(false);
                    I18n.track(status.textProperty(), () -> BaseApiClient.describe(err));
                });
            });
            status.setText("");
            content.getChildren().setAll(split, tabs, status, save);
            d.getDialogPane().getScene().getWindow().sizeToScene();
        }, err -> I18n.track(status.textProperty(), () -> BaseApiClient.describe(err)));
    }

    public static String chooseAvatar(Window owner) throws IOException {
        FileChooser picker = new FileChooser();
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                I18n.get("profile.imageTypes"), "*.png", "*.jpg", "*.jpeg"));
        File file = picker.showOpenDialog(owner);
        if (file == null) return null;
        return encodeAvatar(file);
    }

    static String encodeAvatar(File file) throws IOException {
        if (file.length() > 12 * 1024 * 1024) throw new IOException("Image too large");
        try (var input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("Cannot read image");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int originalWidth = reader.getWidth(0);
                int originalHeight = reader.getHeight(0);
                double scale = Math.min(1.0, 256.0 / Math.max(originalWidth, originalHeight));
                int width = Math.max(1, (int) Math.round(originalWidth * scale));
                int height = Math.max(1, (int) Math.round(originalHeight * scale));

                // Decode large photos at a reduced resolution before the final resize.
                var parameters = reader.getDefaultReadParam();
                int sampling = Math.max(1, Math.max(originalWidth, originalHeight) / 512);
                parameters.setSourceSubsampling(sampling, sampling, 0, 0);
                BufferedImage source = reader.read(0, parameters);
                BufferedImage pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                var graphics = pixels.createGraphics();
                try {
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, width, height, null);
                } finally {
                    graphics.dispose();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(pixels, "png", out);
                return Base64.getEncoder().encodeToString(out.toByteArray());
            } finally {
                reader.dispose();
            }
        }
    }

    public static StackPane avatar(String encoded, double size) {
        StackPane frame = new StackPane();
        frame.setMinSize(size, size);
        frame.setPrefSize(size, size);
        frame.setMaxSize(size, size);
        frame.getStyleClass().add("profile-avatar");
        if (encoded == null || encoded.isBlank()) {
            frame.getChildren().add(UiKit.icon("M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 M4 20 C4 15 7 14 12 14 C17 14 20 15 20 20"));
            return frame;
        }
        try {
            Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            ImageView photo = new ImageView(image);
            photo.setPreserveRatio(true);
            double ratio = size / Math.min(image.getWidth(), image.getHeight());
            photo.setFitWidth(image.getWidth() * ratio);
            photo.setFitHeight(image.getHeight() * ratio);
            frame.getChildren().add(photo);
            frame.setClip(new javafx.scene.shape.Circle(size / 2, size / 2, size / 2));
        } catch (IllegalArgumentException ex) { return avatar("", size); }
        return frame;
    }

    private static StackPane portrait(String encoded) { return avatar(encoded, 84); }

    private static final class Editor extends VBox {
        final TextField name = new TextField();
        final TextArea bio = new TextArea();
        final HBox photo = new HBox(8);
        String avatar;

        Editor(Card card, Label status) {
            super(8);
            setPadding(new Insets(16, 0, 4, 0));
            bio.setPrefRowCount(3);
            bio.setWrapText(true);
            Button choose = I18n.caption(new Button(), "profile.choosePhoto");
            choose.setGraphic(new UiIcon(UiIcon.Kind.UPLOAD));
            Button clear = I18n.caption(new Button(), "profile.removePhoto");
            clear.setGraphic(new UiIcon(UiIcon.Kind.DELETE));
            photo.getChildren().setAll(portrait(""), choose, clear);
            photo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            clear.setOnAction(e -> setAvatar(""));
            choose.setOnAction(e -> {
                try {
                    String selected = chooseAvatar(getScene().getWindow());
                    if (selected != null) { setAvatar(selected); status.setText(""); }
                } catch (IOException | IllegalArgumentException ex) {
                    I18n.localize(status.textProperty(), "profile.imageLimit");
                }
            });
            getChildren().setAll(photo, I18n.caption(new Label(), "profile.name"), name, I18n.caption(new Label(), "profile.bio"), bio);
            setCard(card);
        }

        void setCard(Card card) {
            name.setText(card.name());
            bio.setText(card.bio());
            setAvatar(card.avatar());
        }
        void setAvatar(String value) {
            avatar = value;
            photo.getChildren().set(0, portrait(value));
        }
        Card card() { return new Card(name.getText(), bio.getText(), avatar); }
    }
}
