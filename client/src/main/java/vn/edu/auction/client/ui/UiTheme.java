package vn.edu.auction.client.ui;

import javafx.scene.Scene;
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
    private static boolean dark = readPreference();
    static {
        for (String face : List.of("Regular", "SemiBold", "Bold")) {
            var url = UiTheme.class.getResource("/vn/edu/auction/client/fonts/BeVietnamPro-" + face + ".ttf");
            if (url != null) javafx.scene.text.Font.loadFont(url.toExternalForm(), 13);
        }
    }

    private UiTheme() {}

    public static void apply(Scene scene) {
        scene.getStylesheets().remove(CSS);
        scene.getStylesheets().add(CSS);
        updateScene(scene);
        scenes.removeIf(ref -> ref.get() == null || ref.get() == scene);
        scenes.add(new WeakReference<>(scene));
        UiMotion.install(scene);
    }

    public static void apply(DialogPane pane) {
        if (!pane.getStylesheets().contains(CSS)) pane.getStylesheets().add(CSS);
        applyDark(pane.getStylesheets());
        dialogs.removeIf(ref -> ref.get() == null || ref.get() == pane);
        dialogs.add(new WeakReference<>(pane));
        if (!pane.getStyleClass().contains("auction-dialog")) pane.getStyleClass().add("auction-dialog");
        if (pane.getScene() != null) UiMotion.install(pane.getScene());
    }

    public static boolean isDark() { return dark; }

    public static void setDark(boolean enabled) {
        dark = enabled;
        scenes.removeIf(ref -> ref.get() == null);
        dialogs.removeIf(ref -> ref.get() == null);
        scenes.forEach(ref -> { Scene scene = ref.get(); if (scene != null) updateScene(scene); });
        dialogs.forEach(ref -> { DialogPane pane = ref.get(); if (pane != null) applyDark(pane.getStylesheets()); });
        try { java.util.prefs.Preferences.userNodeForPackage(UiTheme.class).putBoolean("darkMode", enabled); }
        catch (SecurityException ignored) { /* Theme still works for the current session. */ }
    }

    private static boolean readPreference() {
        try { return java.util.prefs.Preferences.userNodeForPackage(UiTheme.class).getBoolean("darkMode", false); }
        catch (SecurityException ex) { return false; }
    }

    private static void applyDark(javafx.collections.ObservableList<String> stylesheets) {
        stylesheets.remove(DARK_CSS);
        if (dark) stylesheets.add(DARK_CSS);
    }

    private static void updateScene(Scene scene) {
        applyDark(scene.getStylesheets());
        if (!(scene.getWindow() instanceof PopupWindow)) {
            scene.setFill(dark ? Color.web("#181a23") : Color.web("#f8f7f4"));
        }
    }
}
