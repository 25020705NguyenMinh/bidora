package vn.edu.auction.client.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;
import javafx.stage.PopupWindow;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class UiTheme {
    private static final String CSS = UiTheme.class.getResource(
            "/vn/edu/auction/client/css/theme.css").toExternalForm();
    private static final String DARK_CSS = UiTheme.class.getResource(
            "/vn/edu/auction/client/css/dark.css").toExternalForm();
    private static final List<WeakReference<Scene>> scenes = new ArrayList<>();
    private static final List<WeakReference<DialogPane>> dialogs = new ArrayList<>();
    private static final ReadOnlyBooleanWrapper dark = new ReadOnlyBooleanWrapper(readPreference());
    static {
        for (String face : List.of("Regular", "SemiBold", "Bold")) {
            var url = UiTheme.class.getResource("/vn/edu/auction/client/fonts/BeVietnamPro-" + face + ".ttf");
            if (url != null) javafx.scene.text.Font.loadFont(url.toExternalForm(), 13);
        }
    }

    private UiTheme() {}

    public static void apply(Scene scene) {
        if (!scene.getStylesheets().contains(CSS)) scene.getStylesheets().add(CSS);
        updateScene(scene);
        scenes.removeIf(ref -> ref.get() == null || ref.get() == scene);
        scenes.add(new WeakReference<>(scene));
        observeSurface(scene);
        UiMotion.install(scene);
    }

    private static void observeSurface(Scene scene) {
        if (scene.getProperties().putIfAbsent("bidora.sceneBackground", Boolean.TRUE) == null) {
            scene.rootProperty().addListener((obs, oldRoot, newRoot) -> {
                if (newRoot instanceof javafx.scene.layout.Region region) {
                    region.backgroundProperty().addListener((o, before, after) -> synchronizeFill(scene));
                }
                synchronizeFill(scene);
            });
            if (scene.getRoot() instanceof javafx.scene.layout.Region root) {
                root.backgroundProperty().addListener((obs, before, after) -> synchronizeFill(scene));
            }
        }
        scene.getRoot().applyCss();
        synchronizeFill(scene);
    }

    public static void apply(DialogPane pane) {
        if (!pane.getStylesheets().contains(CSS)) pane.getStylesheets().add(CSS);
        applyDark(pane.getStylesheets());
        var standardButtons = java.util.Map.of(
                javafx.scene.control.ButtonType.CLOSE, "common.close",
                javafx.scene.control.ButtonType.CANCEL, "common.cancel",
                javafx.scene.control.ButtonType.NO, "common.cancel",
                javafx.scene.control.ButtonType.YES, "common.confirm",
                javafx.scene.control.ButtonType.OK, "common.confirm");
        for (var type : pane.getButtonTypes()) {
            String key = standardButtons.get(type);
            if (key != null && pane.lookupButton(type) instanceof javafx.scene.control.ButtonBase button) {
                vn.edu.auction.client.I18n.localize(button.textProperty(), key);
            }
        }
        dialogs.removeIf(ref -> ref.get() == null || ref.get() == pane);
        dialogs.add(new WeakReference<>(pane));
        if (!pane.getStyleClass().contains("auction-dialog")) pane.getStyleClass().add("auction-dialog");
        if (pane.getScene() != null) {
            observeSurface(pane.getScene());
            UiMotion.install(pane.getScene());
        }
    }

    public static boolean isDark() { return dark.get(); }
    static ReadOnlyBooleanProperty darkProperty() { return dark.getReadOnlyProperty(); }

    public static void setDark(boolean enabled) {
        if (isDark() == enabled) return;
        dark.set(enabled);
        scenes.removeIf(ref -> ref.get() == null);
        dialogs.removeIf(ref -> ref.get() == null);
        scenes.forEach(ref -> { Scene scene = ref.get(); if (scene != null) transitionScene(scene); });
        dialogs.forEach(ref -> {
            DialogPane pane = ref.get();
            if (pane != null) {
                applyDark(pane.getStylesheets());
                pane.applyCss();
                if (pane.getScene() != null) synchronizeFill(pane.getScene());
            }
        });
        try { java.util.prefs.Preferences.userNodeForPackage(UiTheme.class).putBoolean("darkMode", enabled); }
        catch (SecurityException ignored) { /* Theme still works for the current session. */ }
    }

    private static boolean readPreference() {
        try { return java.util.prefs.Preferences.userNodeForPackage(UiTheme.class).getBoolean("darkMode", false); }
        catch (SecurityException ex) { return false; }
    }

    private static void applyDark(javafx.collections.ObservableList<String> stylesheets) {
        if (!isDark()) stylesheets.remove(DARK_CSS);
        else if (!stylesheets.contains(DARK_CSS)) stylesheets.add(DARK_CSS);
    }

    private record Wipe(ImageView image, Timeline motion) {}

    private static void transitionScene(Scene scene) {
        if (!(scene.getRoot() instanceof Pane pane) || scene.getWindow() == null
                || !scene.getWindow().isShowing() || scene.getWidth() <= 0 || scene.getHeight() <= 0) {
            updateScene(scene);
            return;
        }
        double width = scene.getWidth();
        double height = scene.getHeight();
        SnapshotParameters capture = new SnapshotParameters();
        double scale = scene.getWindow().getOutputScaleX();
        capture.setTransform(new Scale(scale, scale));
        capture.setViewport(new Rectangle2D(0, 0, width * scale, height * scale));
        // Include any in-flight wipe so reversing the switch starts from the visible frame.
        ImageView old = new ImageView(pane.snapshot(capture, null));
        if (pane.getProperties().remove(Wipe.class) instanceof Wipe previous) {
            previous.motion().stop();
            pane.getChildren().remove(previous.image());
        }
        old.setManaged(false);
        old.setMouseTransparent(true);
        old.setFitWidth(width);
        old.setFitHeight(height);
        Rectangle clip = new Rectangle(width, height);
        old.setClip(clip);
        updateScene(scene);
        pane.getChildren().add(old);
        var ease = Interpolator.SPLINE(.25, .75, .22, 1);
        var motion = new Timeline(new KeyFrame(Duration.millis(340),
                new KeyValue(clip.widthProperty(), 0, ease),
                new KeyValue(clip.xProperty(), isDark() ? width : 0, ease)));
        pane.getProperties().put(Wipe.class, new Wipe(old, motion));
        motion.setOnFinished(e -> {
            pane.getChildren().remove(old);
            pane.getProperties().remove(Wipe.class);
        });
        motion.play();
    }

    private static void updateScene(Scene scene) {
        applyDark(scene.getStylesheets());
        scene.getRoot().applyCss();
        synchronizeFill(scene);
    }

    static void synchronizeFill(Scene scene) {
        if (scene.getWindow() instanceof PopupWindow) {
            scene.setFill(Color.TRANSPARENT);
        } else if (scene.getRoot() instanceof javafx.scene.layout.Region root && root.getBackground() != null
                && !root.getBackground().getFills().isEmpty()) {
            scene.setFill(root.getBackground().getFills().getFirst().getFill());
        }
    }
}
