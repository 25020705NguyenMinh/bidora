package vn.edu.auction.client.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import vn.edu.auction.client.I18n;

public final class ProductPhoto {
    private ProductPhoto() {}

    public static String choose(Window owner) throws java.io.IOException {
        FileChooser picker = new FileChooser();
        picker.setTitle(I18n.get("product.chooseImage"));
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG, JPEG", "*.png", "*.jpg", "*.jpeg"));
        var file = picker.showOpenDialog(owner);
        if (file == null) return null;
        if (Files.size(file.toPath()) > 12_000_000) throw new java.io.IOException("Image too large");
        try (var input = ImageIO.createImageInputStream(file)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new java.io.IOException("Invalid image");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"PNG".equalsIgnoreCase(format) && !"JPEG".equalsIgnoreCase(format)) throw new java.io.IOException("Invalid format");
                reader.setInput(input);
                int largest = Math.max(reader.getWidth(0), reader.getHeight(0));
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, (largest + 1279) / 1280);
                params.setSourceSubsampling(sample, sample, 0, 0);
                var source = reader.read(0, params);
                var jpeg = new java.awt.image.BufferedImage(source.getWidth(), source.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = jpeg.createGraphics();
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, jpeg.getWidth(), jpeg.getHeight());
                graphics.drawImage(source, 0, 0, null);
                graphics.dispose();
                var bytes = new ByteArrayOutputStream();
                ImageIO.write(jpeg, "jpeg", bytes);
                String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
                if (!vn.edu.auction.shared.dto.ProductImages.valid(encoded)) throw new java.io.IOException("Invalid image");
                return encoded;
            } finally { reader.dispose(); }
        }
    }

    public static StackPane view(String encoded, String name, double height) {
        if (encoded == null || encoded.isBlank()) return MarketCard.photo(MarketCard.illustrationIndex(name), height);
        try {
            var image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            if (image.isError()) return MarketCard.photo(-1, height);
            ImageView photo = new ImageView(image);
            photo.setPreserveRatio(true);
            photo.setFitHeight(height);
            StackPane frame = new StackPane(photo);
            frame.setMinHeight(height);
            frame.setPrefHeight(height);
            frame.setMinWidth(0);
            photo.fitWidthProperty().bind(frame.widthProperty());
            frame.getStyleClass().add("product-photo");
            javafx.animation.ScaleTransition zoom = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(240), photo);
            zoom.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            frame.hoverProperty().addListener((obs, before, hovered) -> {
                zoom.stop();
                zoom.setToX(hovered ? 1.025 : 1);
                zoom.setToY(hovered ? 1.025 : 1);
                zoom.playFromStart();
            });
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(frame.widthProperty());
            clip.heightProperty().bind(frame.heightProperty());
            clip.setArcWidth(20);
            clip.setArcHeight(20);
            frame.setClip(clip);
            return frame;
        } catch (IllegalArgumentException ex) { return MarketCard.photo(-1, height); }
    }
}
