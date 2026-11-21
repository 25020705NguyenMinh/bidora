package vn.edu.auction.shared.dto;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Product images travel as a bounded JPEG payload in the existing item API. */
public final class ProductImages {
    private ProductImages() {}

    public static boolean valid(String encoded) {
        if (encoded.length() > 2_000_000) return false;
        try (var input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            var reader = readers.next();
            try {
                reader.setInput(input);
                return "JPEG".equalsIgnoreCase(reader.getFormatName())
                        && reader.getWidth(0) > 0 && reader.getHeight(0) > 0
                        && reader.getWidth(0) <= 1280 && reader.getHeight(0) <= 1280
                        && reader.read(0) != null;
            } finally { reader.dispose(); }
        } catch (Exception ex) { return false; }
    }
}
