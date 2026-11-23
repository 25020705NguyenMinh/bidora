package vn.edu.auction.client.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class AvatarResizeTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @CsvSource({
            "2048, 1024, 256, 128, jpg",
            "1024, 2048, 128, 256, png",
            "64, 48, 64, 48, png",
            "1, 1024, 1, 256, png",
            "256, 256, 256, 256, png"
    })
    void uploadPreservesAspectRatioWithoutEnlargingSmallImages(
            int width, int height, int expectedWidth, int expectedHeight, String format) throws Exception {
        boolean transparent = format.equals("png");
        var source = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        try {
            graphics.setColor(new Color(20, 40, 60, transparent ? 128 : 255));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        var file = temporary.resolve("avatar." + format).toFile();
        assertTrue(ImageIO.write(source, format, file));

        String encoded = TradingProfileDialog.encodeAvatar(file);
        var uploaded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));

        assertNotNull(uploaded);
        assertEquals(expectedWidth, uploaded.getWidth());
        assertEquals(expectedHeight, uploaded.getHeight());
        assertEquals(transparent ? 128 : 255, uploaded.getRGB(0, 0) >>> 24);
        assertTrue(encoded.length() <= 400_000, "Avatar must fit the server payload limit");
    }
}
