package vn.edu.auction.client.browse;

import vn.edu.auction.client.I18n;

import java.util.List;
import java.util.Objects;
import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.util.Duration;
import vn.edu.auction.client.ui.MarketCard;
import vn.edu.auction.client.ui.UiKit;
import vn.edu.auction.client.ui.UiMotion;
import vn.edu.auction.shared.dto.AuctionResponse;
import vn.edu.auction.shared.enums.AuctionStatus;

public final class FeaturedController {
    private record Slide(String name, int photo, AuctionResponse auction) {
        String title() { return auction == null ? I18n.get(name) : name; }
        Long id() { return auction == null ? null : auction.getId(); }
    }
    private static final String[] ART = {"photo-camera", "photo-watch", "photo-headphones", "photo-chair"};
    private static final String[] FILES = {"camera", "watch", "headphones", "chair"};
    private static final List<Slide> GALLERY = List.of(
            new Slide("gallery.camera", 0, null), new Slide("gallery.watch", 1, null),
            new Slide("gallery.audio", 2, null), new Slide("gallery.chair", 3, null));
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final Duration DWELL = Duration.seconds(7);
    @FXML private GridPane hero;
    @FXML private VBox copy;
    @FXML private HBox thumbnails;
    @FXML private Label context, title, price, description, position, imageCredit;
    @FXML private Button previous, next, playback, action, expand;
    @FXML private StackPane imageFrame, imageLayers;
    @FXML private ProgressBar dwellProgress;
    private final Timeline dwell = new Timeline();
    private final InvalidationListener visibilityChanged = ignored -> checkPlayback();
    private final ChangeListener<Window> windowChanged = (obs, before, after) -> observeWindow(before, after);
    private final ListChangeListener<Window> openWindowsChanged = c -> checkPlayback();
    private final WeakListChangeListener<Window> windowObserver = new WeakListChangeListener<>(openWindowsChanged);
    private List<Slide> slides = GALLERY;
    private int index;
    private boolean paused;
    private double dragStart;
    private ParallelTransition transition;
    private ScrollPane scroll;
    private AuctionListController catalog;
    private Runnable openCatalog;

    @FXML private void initialize() {
        javafx.animation.TranslateTransition depth = new javafx.animation.TranslateTransition(Duration.millis(220), imageLayers);
        depth.setInterpolator(Interpolator.EASE_OUT);
        hero.setOnMouseMoved(event -> {
            double ratio = event.getX() / Math.max(1, hero.getWidth()) - .5;
            depth.stop();
            depth.setToX(ratio * 8);
            depth.setToY((event.getY() / Math.max(1, hero.getHeight()) - .5) * 5);
            depth.playFromStart();
        });
        hero.setOnMouseExited(event -> {
            depth.stop();
            depth.setToX(0);
            depth.setToY(0);
            depth.playFromStart();
        });
        imageLayers.setScaleX(1.025);
        imageLayers.setScaleY(1.025);
        dwell.getKeyFrames().setAll(new KeyFrame(Duration.ZERO, new KeyValue(dwellProgress.progressProperty(), 0)),
                new KeyFrame(DWELL, new KeyValue(dwellProgress.progressProperty(), 1, Interpolator.LINEAR)));
        dwell.setOnFinished(e -> move(1));
        setup(previous, "gallery.previous", "M15 4 L8 11 L15 18");
        setup(next, "gallery.next", "M8 4 L15 11 L8 18");
        setup(expand, "gallery.fullPhoto", "M4 10 V4 H10 M14 4 H20 V10 M20 14 V20 H14 M10 20 H4 V14");
        updatePlaybackButton();
        action.setGraphic(UiKit.icon("M3 10 H18 M12 4 L18 10 L12 16"));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(imageLayers.widthProperty());
        clip.heightProperty().bind(imageLayers.heightProperty());
        imageLayers.setClip(clip);
        Rectangle frameClip = new Rectangle();
        frameClip.widthProperty().bind(imageFrame.widthProperty());
        frameClip.heightProperty().bind(imageFrame.heightProperty());
        imageFrame.setClip(frameClip);
        imageFrame.setOnMousePressed(e -> dragStart = e.getSceneX());
        imageFrame.setOnMouseReleased(e -> {
            double distance = e.getSceneX() - dragStart;
            if (Math.abs(distance) >= 45) move(distance < 0 ? 1 : -1);
        });
        hero.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
                move(e.getCode() == KeyCode.RIGHT ? 1 : -1);
                e.consume();
            }
        });
        hero.sceneProperty().addListener((obs, before, after) -> {
            if (before != null) {
                before.windowProperty().removeListener(windowChanged);
                before.focusOwnerProperty().removeListener(visibilityChanged);
            }
            if (after != null) {
                after.windowProperty().addListener(windowChanged);
                after.focusOwnerProperty().addListener(visibilityChanged);
            }
            observeWindow(before == null ? null : before.getWindow(), after == null ? null : after.getWindow());
        });
        for (Node control : List.of(action, previous, next, expand, thumbnails)) {
            control.hoverProperty().addListener(visibilityChanged);
        }
    }

    void connect(ScrollPane scroll, AuctionListController catalog, Runnable openCatalog) {
        this.scroll = scroll;
        this.catalog = catalog;
        this.openCatalog = openCatalog;
        scroll.vvalueProperty().addListener(visibilityChanged);
        scroll.viewportBoundsProperty().addListener(visibilityChanged);
        catalog.auctions().addListener((ListChangeListener<AuctionResponse>) change -> refresh());
        I18n.onChange(hero, () -> {
            for (int i = 0; i < thumbnails.getChildren().size(); i++) {
                Button thumb = (Button) thumbnails.getChildren().get(i);
                thumb.setAccessibleText(slides.get(i).title());
                thumb.getTooltip().setText(slides.get(i).title());
            }
            updateCopy();
        });
        refresh();
    }

    void refresh() {
        Slide last = slides.get(index);
        List<Slide> active = catalog.auctions().stream()
                .filter(a -> a.getId() != null && a.getItemName() != null
                        && (a.getStatus() == AuctionStatus.OPEN || a.getStatus() == AuctionStatus.RUNNING))
                .limit(4).map(a -> new Slide(a.getItemName(), MarketCard.illustrationIndex(a.getItemName()), a)).toList();
        List<Slide> updated = active.isEmpty() ? GALLERY : active;
        boolean rebuild = thumbnails.getChildren().isEmpty() || !updated.equals(slides);
        slides = updated;
        if (rebuild) {
            index = 0;
            for (int i = 0; i < slides.size(); i++) {
                if (Objects.equals(slides.get(i).id(), last.id()) && slides.get(i).name().equals(last.name())) index = i;
            }
            buildThumbnails();
            display(0);
        } else {
            updateCopy();
        }
    }

    private void buildThumbnails() {
        thumbnails.getChildren().clear();
        for (int i = 0; i < slides.size(); i++) {
            int destination = i;
            Slide slide = slides.get(i);
            Button button = new Button();
            button.getStyleClass().add("featured-thumb");
            if (slide.photo() >= 0) button.getStyleClass().add(ART[slide.photo()]);
            else button.setText(Integer.toString(i + 1));
            button.setAccessibleText(slide.title());
            button.setTooltip(UiKit.tooltip(slide.title()));
            button.setOnAction(e -> move(destination - index));
            thumbnails.getChildren().add(button);
        }
        for (Node node : List.of(thumbnails, previous.getParent(), dwellProgress)) {
            node.setVisible(slides.size() > 1);
            node.setManaged(node.isVisible());
        }
    }

    @FXML private void previous() { move(-1); }
    @FXML private void next() { move(1); }
    private void move(int step) {
        if (step == 0 || slides.size() == 1) return;
        index = Math.floorMod(index + step, slides.size());
        display(Integer.signum(step));
    }

    @FXML private void openCurrent() {
        Slide slide = slides.get(index);
        if (slide.auction() != null) catalog.openFeatured(slide.auction());
        else openCatalog.run();
    }

    @FXML private void expandPhoto() {
        Slide slide = slides.get(index);
        if (slide.photo() >= 0 || (slide.auction() != null && slide.auction().getImage() != null)) showPhoto(slide);
    }

    private void showPhoto(Slide slide) {
        Window owner = hero.getScene().getWindow();
        var screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        var url = getClass().getResource("/vn/edu/auction/client/icons/placeholder-" + FILES[Math.max(0, slide.photo())] + ".jpg");
        Image actual = slide.auction() != null && slide.auction().getImage() != null && !slide.auction().getImage().isBlank()
                ? new Image(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(slide.auction().getImage())))
                : new Image(url.toExternalForm());
        ImageView photo = new ImageView(actual);
        photo.setPreserveRatio(true);
        StackPane canvas = new StackPane(photo);
        photo.fitWidthProperty().bind(canvas.widthProperty());
        photo.fitHeightProperty().bind(canvas.heightProperty());
        canvas.setMinSize(0, 0);
        canvas.setPrefSize(Math.min(960, screen.getWidth() * .85), Math.min(640, screen.getHeight() * .72));
        canvas.setStyle("-fx-background-color: #14171c;");
        Button close = I18n.caption(new Button(), "common.close");
        close.getStyleClass().add("featured-control");
        I18n.localize(close.accessibleTextProperty(), "gallery.close");
        StackPane.setAlignment(close, javafx.geometry.Pos.TOP_RIGHT);
        StackPane.setMargin(close, new javafx.geometry.Insets(12));
        canvas.getChildren().add(close);
        Stage viewer = new Stage();
        viewer.initOwner(owner);
        viewer.initModality(Modality.WINDOW_MODAL);
        viewer.setTitle(slide.title());
        Scene scene = new Scene(canvas);
        vn.edu.auction.client.ui.UiTheme.apply(scene);
        viewer.setScene(scene);
        close.setOnAction(e -> viewer.close());
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) viewer.close(); });
        viewer.show();
    }

    private void updateCopy() {
        Slide slide = slides.get(index);
        AuctionResponse a = slide.auction();
        context.setText(a == null ? I18n.get("gallery.collection") : (a.getStatus() == AuctionStatus.RUNNING ? I18n.get("gallery.live") : I18n.get("gallery.scheduled")));
        title.setText(slide.title());
        title.setTooltip(UiKit.tooltip(slide.title()));
        price.setText(a == null ? "" : UiKit.money(a.getCurrentPrice()));
        description.setText(a == null ? "" : I18n.get("auction.endsPrefix", UiKit.dt(a.getEndTime())));
        for (Label label : List.of(price, description)) {
            label.setVisible(!label.getText().isEmpty());
            label.setManaged(label.isVisible());
        }
        I18n.localize(action.textProperty(), a != null ? "gallery.open" : "gallery.browse");
        position.setText((index + 1) + " / " + slides.size());
        boolean hasPhoto = slide.photo() >= 0 || (a != null && a.getImage() != null && !a.getImage().isBlank());
        expand.setVisible(hasPhoto);
        expand.setManaged(hasPhoto);
        imageCredit.setVisible(a != null && hasPhoto && (a.getImage() == null || a.getImage().isBlank()));
        imageCredit.setManaged(imageCredit.isVisible());
        for (int i = 0; i < thumbnails.getChildren().size(); i++)
            thumbnails.getChildren().get(i).pseudoClassStateChanged(CURRENT, i == index);
    }

    private void display(int direction) {
        updateCopy();
        if (transition != null) transition.stop();
        var layers = imageLayers.getChildren();
        Node old = layers.isEmpty() ? null : layers.getLast();
        StackPane art = new StackPane();
        int photo = slides.get(index).photo();
        var featured = slides.get(index).auction();
        if (featured != null && featured.getImage() != null && !featured.getImage().isBlank()) {
            art.getChildren().add(vn.edu.auction.client.ui.ProductPhoto.view(featured.getImage(), featured.getItemName(), 400));
        } else if (photo >= 0) art.getStyleClass().addAll("featured-art", ART[photo]);
        else {
            art.getStyleClass().add("featured-missing");
            art.getChildren().add(I18n.caption(new Label(), "gallery.noPhoto"));
        }
        if (old == null || direction == 0) layers.setAll(art);
        else {
            old.setTranslateX(0);
            old.setOpacity(1);
            layers.setAll(old, art);
            FadeTransition fade = new FadeTransition(Duration.millis(350), art);
            fade.setFromValue(0);
            fade.setToValue(1);
            TranslateTransition drift = new TranslateTransition(Duration.millis(440), art);
            drift.setFromX(direction * 32);
            drift.setToX(0);
            drift.setInterpolator(Interpolator.SPLINE(.2, .8, .2, 1));
            transition = new ParallelTransition(fade, drift);
            transition.setOnFinished(e -> layers.remove(old));
            transition.play();
            UiMotion.reveal(copy, direction * 14, 0, 0);
        }
        dwell.stop();
        dwellProgress.setProgress(0);
        checkPlayback();
    }

    @FXML private void togglePlayback() {
        paused = !paused;
        updatePlaybackButton();
        checkPlayback();
    }

    private void updatePlaybackButton() {
        setup(playback, paused ? "gallery.play" : "gallery.pause",
                paused ? "M7 4 L18 11 L7 18 Z" : "M8 4 V18 M15 4 V18");
    }

    private static void setup(Button button, String key, String path) {
        button.setGraphic(UiKit.icon(path));
        button.setTooltip(UiKit.localizedTooltip(key));
        I18n.localize(button.accessibleTextProperty(), key);
    }

    private void observeWindow(Window before, Window after) {
        if (before != null) {
            Window.getWindows().removeListener(windowObserver);
            if (before instanceof Stage stage) stage.iconifiedProperty().removeListener(visibilityChanged);
            before.showingProperty().removeListener(visibilityChanged);
        }
        if (after != null) {
            Window.getWindows().addListener(windowObserver);
            if (after instanceof Stage stage) stage.iconifiedProperty().addListener(visibilityChanged);
            after.showingProperty().addListener(visibilityChanged);
        }
        checkPlayback();
    }

    private void checkPlayback() {
        Scene scene = hero.getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (scroll == null || window == null || !window.isShowing() || paused
                || (window instanceof Stage stage && stage.isIconified())
                || Window.getWindows().stream().anyMatch(w -> w instanceof Stage child
                        && child.getOwner() == window && child.getModality() != Modality.NONE)
                || slides.size() < 2 || expand.isHover() || action.isHover() || previous.isHover() || next.isHover() || thumbnails.isHover()) {
            dwell.pause();
            return;
        }
        Node focus = scene.getFocusOwner();
        if (focus != null && focus != playback && focus.isFocusVisible()) {
            for (Node parent = focus; parent != null; parent = parent.getParent()) {
                if (parent == hero) { dwell.pause(); return; }
            }
        }
        if (hero.localToScene(hero.getLayoutBounds()).intersects(scroll.localToScene(scroll.getLayoutBounds()))) dwell.play();
        else dwell.pause();
    }
}
