package vn.edu.auction.client.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/** Semantic icons share a 24px frame; motion never changes control layout. */
public final class UiIcon extends StackPane {
    public enum Kind {
        AUCTION("M4 20 H14 M6 17 H12 M10 3 L17 10 M8 5 L15 12 M7 6 L10 3 L19 12 L16 15 Z M12 10 L5 17"),
        PRODUCT("M4 7 L12 3 L20 7 V17 L12 21 L4 17 Z M4 7 L12 11 L20 7 M12 11 V21 M8 5 L16 9"),
        SELL("M3 4 H12 L21 13 L13 21 L3 11 Z M7 8 H7.01"),
        USER("M8 7 A4 4 0 1 0 16 7 A4 4 0 1 0 8 7 M4 21 V19 C4 12 20 12 20 19 V21"),
        WALLET("M20 8 V5 H5 A2 2 0 0 0 5 9 H21 V20 H5 A2 2 0 0 1 3 18 V7 M21 12 H16 V17 H21 M17 14.5 H17.01"),
        NOTICE("M5 17 H19 L17 14 V9 C17 2 7 2 7 9 V14 Z M10 20 Q12 23 14 20"),
        CHAT("M3 4 H21 V17 H10 L5 21 V17 H3 Z M7 9 H17 M7 13 H13"),
        LOGIN("M14 3 H21 V21 H14 M3 12 H16 M11 7 L16 12 L11 17"),
        LOGOUT("M10 3 H3 V21 H10 M10 12 H21 M16 7 L21 12 L16 17"),
        SHIELD("M12 3 L20 6 V12 C20 17 16 20 12 22 C8 20 4 17 4 12 V6 Z M8 12 L11 15 L16 9"),
        SWITCH("M4 7 H20 M16 3 L20 7 L16 11 M20 17 H4 M8 13 L4 17 L8 21"),
        UPLOAD("M12 16 V3 M7 8 L12 3 L17 8 M4 15 V21 H20 V15"),
        DEPOSIT("M12 3 V16 M7 11 L12 16 L17 11 M4 17 V21 H20 V17"),
        IMAGE("M3 4 H21 V20 H3 Z M3 16 L8 11 L13 16 L17 12 L21 16 M14 8 H14.01"),
        DELETE("M3 6 H21 M9 6 V3 H15 V6 M5 6 L6 21 H18 L19 6 M10 10 V17 M14 10 V17"),
        SAVE("M4 3 H17 L21 7 V21 H3 V3 Z M7 3 V9 H16 V3 M7 21 V14 H17 V21"),
        LOCK("M6 10 V7 A6 6 0 0 1 18 7 V10 M4 10 H20 V22 H4 Z M12 15 V18"),
        HISTORY("M3 5 V11 H9 M3 11 A9 9 0 1 1 5 19 M12 7 V12 L16 14"),
        VISIBILITY("M2 12 C7 3 17 3 22 12 C17 21 7 21 2 12 Z M9 12 A3 3 0 1 0 15 12 A3 3 0 1 0 9 12"),
        PLUS("M12 4 V20 M4 12 H20"),
        CHECK("M4 12 L9 17 L20 6"),
        CLOSE("M5 5 L19 19 M19 5 L5 19"),
        BACK("M20 12 H4 M10 6 L4 12 L10 18"),
        SEND("M3 10 L21 3 L14 21 L11 13 Z M11 13 L21 3");

        private final String path;
        Kind(String path) { this.path = path; }
    }

    private final SVGPath drawing = UiKit.icon(Kind.PRODUCT.path);
    private Kind kind = Kind.PRODUCT;
    private Timeline motion;
    private final ChangeListener<Boolean> hovered = (obs, before, after) -> hover(after);

    public UiIcon() {
        setMinSize(24, 24);
        setPrefSize(24, 24);
        setMaxSize(24, 24);
        setMouseTransparent(true);
        getStyleClass().add("semantic-icon");
        getChildren().add(drawing);
        parentProperty().addListener((obs, before, after) -> {
            if (before != null) before.hoverProperty().removeListener(hovered);
            if (after != null) after.hoverProperty().addListener(hovered);
            hover(false);
        });
    }

    public UiIcon(Kind kind) { this(); setKind(kind); }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; drawing.setContent(kind.path); }

    void hover(boolean active) {
        if (motion != null) motion.stop();
        double x = active && (kind == Kind.SEND || kind == Kind.LOGIN || kind == Kind.LOGOUT) ? 2 : 0;
        double y = active && kind == Kind.UPLOAD ? -2 : active && kind == Kind.DEPOSIT ? 2 : 0;
        double angle = active && (kind == Kind.AUCTION || kind == Kind.CHAT) ? -8 : 0;
        motion = new Timeline(new KeyFrame(Duration.millis(active ? 180 : 220),
                new KeyValue(drawing.translateXProperty(), x, Interpolator.EASE_BOTH),
                new KeyValue(drawing.translateYProperty(), y, Interpolator.EASE_BOTH),
                new KeyValue(drawing.rotateProperty(), angle, Interpolator.EASE_BOTH),
                new KeyValue(drawing.scaleXProperty(), active ? 1.07 : 1, Interpolator.EASE_BOTH),
                new KeyValue(drawing.scaleYProperty(), active ? 1.07 : 1, Interpolator.EASE_BOTH)));
        motion.play();
    }
}
