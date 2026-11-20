package vn.edu.auction.client.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import javafx.util.Duration;
import vn.edu.auction.client.I18n;

public final class AppearanceControls extends HBox {
    private final Button languageButton = new Button();
    private final ToggleButton themeButton = new ToggleButton();
    private final ChangeListener<Boolean> themeChanged = (obs, before, after) -> updateThemeButton(true);
    private Boolean displayedTheme;
    private Timeline drawing;

    public AppearanceControls() {
        super(2);
        setAlignment(Pos.CENTER);
        languageButton.setId("languageButton");
        languageButton.getStyleClass().add("language-switch");
        languageButton.setOnAction(e -> toggleLanguage());
        themeButton.setId("themeButton");
        themeButton.getStyleClass().add("theme-switch");
        themeButton.setSelected(UiTheme.isDark());
        themeButton.setOnAction(e -> setTheme(themeButton.isSelected()));
        getChildren().addAll(languageButton, themeButton);
        updateThemeButton(false);
        updateLanguage();
        I18n.onChange(this, this::updateLanguage);
        UiTheme.darkProperty().addListener(new WeakChangeListener<>(themeChanged));
    }

    private void toggleLanguage() {
        I18n.setLocale(I18n.isVietnamese() ? java.util.Locale.ENGLISH : I18n.VIETNAMESE);
    }

    private void updateLanguage() {
        languageButton.setText(I18n.isVietnamese() ? "VI" : "EN");
        String next = I18n.isVietnamese() ? "English" : "Tiếng Việt";
        languageButton.setAccessibleText(I18n.get("language.choose") + " · " + next);
        languageButton.setTooltip(UiKit.tooltip(next));
        updateThemeButton(false);
    }

    private void setTheme(boolean dark) {
        if (UiTheme.isDark() == dark) updateThemeButton(false);
        else UiTheme.setDark(dark);
    }

    private void updateThemeButton(boolean animate) {
        boolean dark = UiTheme.isDark();
        themeButton.setSelected(dark);
        themeButton.setText(I18n.get(dark ? "theme.modeDark" : "theme.modeLight"));
        if (displayedTheme == null || displayedTheme != dark) {
            if (drawing != null) drawing.stop();
            Pane icon = new Pane();
            icon.setMinSize(22, 22);
            icon.setPrefSize(22, 22);
            icon.setMaxSize(22, 22);
            drawing = new Timeline();
            if (dark) {
                var moon = UiKit.icon("M20 14 A8 8 0 0 1 9 3 A8 8 0 1 0 20 14 Z");
                icon.getChildren().add(moon);
                drawStroke(moon, 58, 0, animate);
            } else {
                Circle sun = new Circle(11, 11, 4);
                sun.setFill(Color.TRANSPARENT);
                sun.getStyleClass().add("line-icon");
                icon.getChildren().add(sun);
                drawStroke(sun, 2 * Math.PI * 4, 0, animate);
                for (int ray = 0; ray < 8; ray++) {
                    double angle = ray * Math.PI / 4;
                    Line line = new Line(11 + Math.cos(angle) * 7, 11 + Math.sin(angle) * 7,
                            11 + Math.cos(angle) * 9, 11 + Math.sin(angle) * 9);
                    line.getStyleClass().add("line-icon");
                    icon.getChildren().add(line);
                    drawStroke(line, 3, 45 + ray * 18, animate);
                }
            }
            themeButton.setGraphic(icon);
            displayedTheme = dark;
            if (animate) drawing.play();
        }
        String action = I18n.get(dark ? "theme.light" : "theme.dark");
        themeButton.setTooltip(UiKit.tooltip(action));
        themeButton.setAccessibleText(action);
    }
    private void drawStroke(Shape shape, double length, int delay, boolean animate) {
        if (!animate) return;
        shape.getStrokeDashArray().setAll(length, length);
        shape.setStrokeDashOffset(length);
        drawing.getKeyFrames().addAll(
                new KeyFrame(Duration.millis(delay), new KeyValue(shape.strokeDashOffsetProperty(), length)),
                new KeyFrame(Duration.millis(delay + 320),
                        new KeyValue(shape.strokeDashOffsetProperty(), 0, javafx.animation.Interpolator.EASE_BOTH)));
        drawing.setOnFinished(event -> {
            if (themeButton.getGraphic() instanceof Pane icon) {
                icon.getChildren().stream().filter(Shape.class::isInstance).map(Shape.class::cast)
                        .forEach(stroke -> stroke.getStrokeDashArray().clear());
            }
        });
    }

}
