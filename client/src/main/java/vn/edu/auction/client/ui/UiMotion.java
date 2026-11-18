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
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/** Motion is applied to content and controls so the window background stays still. */
public final class UiMotion {
    private enum Effect { SCALE, REVEAL, ERROR }
    private static final Interpolator SETTLE = Interpolator.SPLINE(.2, .8, .2, 1);
    private static final String INSTALLED = UiMotion.class.getName();

    private UiMotion() {}

    public static void install(Scene scene) {
        if (scene.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> observeButton(event.getTarget()));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> observeButton(event.getTarget()));
    }

    private static void observeButton(Object target) {
        if (!(target instanceof Node node)) return;
        while (node != null && !(node instanceof ButtonBase)) node = node.getParent();
        if (!(node instanceof ButtonBase button)
                || button.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;
        // The control disarms on release, cancelled drags and keyboard activation.
        button.armedProperty().addListener((obs, wasArmed, armed) -> {
            double scale = armed ? .97 : 1;
            ScaleTransition press = new ScaleTransition(Duration.millis(armed ? 75 : 150), button);
            press.setToX(scale);
            press.setToY(scale);
            press.setInterpolator(SETTLE);
            play(button, Effect.SCALE, press);
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
