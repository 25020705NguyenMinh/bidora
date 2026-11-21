package vn.edu.auction.client.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.DialogPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/** Native windows fade as a whole; their contents move against an opaque themed surface. */
public final class UiMotion {
    private enum Effect { SCALE, REVEAL, ERROR, WINDOW }
    private static final Interpolator SETTLE = Interpolator.SPLINE(.2, .8, .2, 1);
    private static final String INSTALLED = UiMotion.class.getName();

    private UiMotion() {}

    public static void install(Scene scene) {
        if (scene.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;
        installWindow(scene, scene.getWindow());
        scene.windowProperty().addListener((obs, old, window) -> installWindow(scene, window));
        scene.rootProperty().addListener((obs, old, root) -> {
            if (scene.getWindow() != null && scene.getWindow().isShowing()) {
                UiTheme.synchronizeFill(scene);
                Node content = windowContent(root);
                if (content != null) reveal(content, 0, 6, 0);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, event -> observeButton(event.getTarget()));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> observeButton(event.getTarget()));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> observeButton(event.getTarget()));
    }

    private static void installWindow(Scene scene, Window window) {
        if (!(window instanceof Stage stage)
                || stage.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;
        boolean[] closing = {false};
        stage.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> {
            closing[0] = false;
            if (!(scene.getRoot() instanceof DialogPane)) stage.setOpacity(0);
        });
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            if (!(scene.getRoot() instanceof DialogPane)) openWindow(scene.getRoot());
        });
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (closing[0] || scene.getRoot() instanceof DialogPane
                    || scene.getRoot().getStyleClass().contains("chat-window")) return;
            event.consume();
            closing[0] = true;
            closeWindow(scene.getRoot(), () -> {
                stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                closing[0] = false;
            });
        });
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            stopWindowMotion(scene.getRoot());
        });
    }

    private static void observeButton(Object target) {
        if (!(target instanceof Node node)) return;
        while (node != null && !(node instanceof ButtonBase)) node = node.getParent();
        if (!(node instanceof ButtonBase button) || button.getStyleClass().contains("theme-switch")
                || button.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;
        button.hoverProperty().addListener((obs, before, hovering) -> {
            if (button.isArmed()) return;
            ScaleTransition hover = new ScaleTransition(Duration.millis(150), button);
            hover.setToX(hovering ? 1.015 : 1);
            hover.setToY(hovering ? 1.015 : 1);
            hover.setInterpolator(SETTLE);
            play(button, Effect.SCALE, hover);
        });
        // The control disarms on release, cancelled drags and keyboard activation.
        button.armedProperty().addListener((obs, wasArmed, armed) -> {
            double scale = armed ? .97 : button.isHover() ? 1.015 : 1;
            ScaleTransition press = new ScaleTransition(Duration.millis(armed ? 75 : 150), button);
            press.setToX(scale);
            press.setToY(scale);
            press.setInterpolator(SETTLE);
            play(button, Effect.SCALE, press);
        });
    }

    public static void openWindow(Node content) {
        Scene scene = content.getScene();
        if (scene == null || scene.getRoot() != content || !(scene.getWindow() instanceof Stage window)) {
            reveal(content, 0, 8, 0);
            return;
        }
        stopWindowMotion(content);
        content.applyCss();
        UiTheme.synchronizeFill(scene);
        content.setOpacity(1);
        content.setScaleX(1);
        content.setScaleY(1);
        content.setTranslateY(0);
        window.setOpacity(0);
        Timeline motion = new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(window.opacityProperty(), 1, SETTLE)));
        Node body = windowContent(content);
        if (body != null) {
            body.setOpacity(0);
            body.setScaleX(.97);
            body.setScaleY(.97);
            body.setTranslateY(10);
            motion.getKeyFrames().add(new KeyFrame(Duration.millis(280),
                    new KeyValue(body.opacityProperty(), 1, SETTLE),
                    new KeyValue(body.scaleXProperty(), 1, SETTLE),
                    new KeyValue(body.scaleYProperty(), 1, SETTLE),
                    new KeyValue(body.translateYProperty(), 0, SETTLE)));
        }
        content.getProperties().put(Effect.WINDOW, motion);
        motion.setOnFinished(event -> content.getProperties().remove(Effect.WINDOW, motion));
        motion.play();
    }

    private static Node windowContent(Node root) {
        if (root instanceof DialogPane dialog) return dialog.getContent();
        if (root instanceof BorderPane border) return border.getCenter();
        if (root instanceof Pane pane && !pane.getChildren().isEmpty()) return pane.getChildren().getLast();
        return null;
    }

    private static void stopWindowMotion(Node content) {
        Animation previous = (Animation) content.getProperties().remove(Effect.WINDOW);
        if (previous != null) previous.stop();
        Node body = windowContent(content);
        if (body != null) {
            body.setOpacity(1);
            body.setScaleX(1);
            body.setScaleY(1);
            body.setTranslateY(0);
        }
    }

    public static void closeWindow(Node content, Runnable closed) {
        Scene scene = content.getScene();
        if (scene != null && scene.getRoot() == content && scene.getWindow() instanceof javafx.stage.Stage window) {
            stopWindowMotion(content);
            content.setOpacity(1);
            content.setScaleX(1);
            content.setScaleY(1);
            content.setTranslateY(0);
            Timeline fade = new Timeline(new KeyFrame(Duration.millis(140),
                    new KeyValue(window.opacityProperty(), 0, SETTLE)));
            content.getProperties().put(Effect.WINDOW, fade);
            fade.setOnFinished(event -> {
                content.getProperties().remove(Effect.WINDOW);
                closed.run();
                if (window.isShowing()) window.setOpacity(1);
            });
            fade.play();
        } else {
            animateWindow(content, 0, .985, 6, 140, closed);
        }
    }

    public static void openPopover(javafx.scene.layout.Region panel) {
        Animation running = (Animation) panel.getProperties().remove(Effect.WINDOW);
        if (running != null) running.stop();
        panel.getTransforms().removeIf(transform -> transform instanceof javafx.scene.transform.Scale);
        javafx.scene.transform.Scale scale = new javafx.scene.transform.Scale(.96, .96, panel.getWidth(), 0);
        panel.getTransforms().add(scale);
        panel.setOpacity(0);
        panel.setTranslateY(-6);
        Timeline reveal = new Timeline(
                new KeyFrame(Duration.millis(170),
                        new KeyValue(panel.opacityProperty(), 1, SETTLE)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(scale.xProperty(), 1.005, SETTLE),
                        new KeyValue(scale.yProperty(), 1.005, SETTLE),
                        new KeyValue(panel.translateYProperty(), 0, SETTLE)),
                new KeyFrame(Duration.millis(250),
                        new KeyValue(scale.xProperty(), 1, SETTLE),
                        new KeyValue(scale.yProperty(), 1, SETTLE)));
        Animation previous = (Animation) panel.getProperties().put(Effect.WINDOW, reveal);
        if (previous != null) previous.stop();
        reveal.setOnFinished(event -> {
            panel.getTransforms().remove(scale);
            panel.getProperties().remove(Effect.WINDOW, reveal);
        });
        reveal.play();
    }


    private static void animateWindow(Node node, double opacity, double scale, double y, int millis, Runnable done) {
        Timeline motion = new Timeline(new KeyFrame(Duration.millis(millis),
                new KeyValue(node.opacityProperty(), opacity, SETTLE),
                new KeyValue(node.scaleXProperty(), scale, SETTLE),
                new KeyValue(node.scaleYProperty(), scale, SETTLE),
                new KeyValue(node.translateYProperty(), y, SETTLE)));
        Animation previous = (Animation) node.getProperties().put(Effect.WINDOW, motion);
        if (previous != null) previous.stop();
        motion.setOnFinished(event -> {
            node.getProperties().remove(Effect.WINDOW);
            done.run();
        });
        motion.play();
    }

    public static <T> void install(javafx.scene.control.Dialog<T> dialog) {
        dialog.setOnShowing(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null && scene.getWindow() instanceof Stage stage) stage.setOpacity(0);
        });
        dialog.setOnShown(event -> openWindow(dialog.getDialogPane()));
        boolean[] closing = {false};
        dialog.setOnCloseRequest(event -> {
            if (closing[0]) return;
            event.consume();
            closing[0] = true;
            closeWindow(dialog.getDialogPane(), dialog::close);
        });
    }

    public static void enter(Parent content) {
        reveal(content, 0, 6, 0);
    }

    public static void error(Node node) {
        play(node, Effect.ERROR, new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(40), new KeyValue(node.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(85), new KeyValue(node.translateXProperty(), 3)),
                new KeyFrame(Duration.millis(135), new KeyValue(node.translateXProperty(), -1.5)),
                new KeyFrame(Duration.millis(180), new KeyValue(node.translateXProperty(), 0))
        ));
    }

    public static void reveal(Node node, double fromX, double fromY, int delay) {
        FadeTransition fade = new FadeTransition(Duration.millis(160), node);
        fade.setFromValue(.35);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(230), node);
        slide.setFromX(fromX);
        slide.setToX(0);
        slide.setFromY(fromY);
        slide.setToY(0);
        slide.setInterpolator(SETTLE);
        ParallelTransition reveal = new ParallelTransition(fade, slide);
        reveal.setDelay(Duration.millis(delay));
        play(node, Effect.REVEAL, reveal);
    }

    public static void pulse(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(210), node);
        pulse.setFromX(1.035);
        pulse.setFromY(1.035);
        pulse.setToX(1);
        pulse.setToY(1);
        pulse.setInterpolator(SETTLE);
        play(node, Effect.SCALE, pulse);
    }

    private static void play(Node node, Effect effect, Animation next) {
        Animation previous = (Animation) node.getProperties().put(effect, next);
        if (previous != null) previous.stop();
        next.setOnFinished(e -> node.getProperties().remove(effect, next));
        next.play();
    }
}
