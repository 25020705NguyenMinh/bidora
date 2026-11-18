package vn.edu.auction.client.browse;

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
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.ui.MarketCard;
import vn.edu.auction.client.ui.UiKit;
import vn.edu.auction.client.ui.UiMotion;
import vn.edu.auction.shared.dto.AuctionResponse;
import vn.edu.auction.shared.enums.AuctionStatus;

public final class FeaturedController {
    private record Slide(String name, int photo, AuctionResponse auction) {
        Long id() { return auction == null ? null : auction.getId(); }
    }
    private static final String[] ART = {"photo-camera", "photo-watch", "photo-headphones", "photo-chair"};
    private static final String[] FILES = {"camera", "watch", "headphones", "chair"};
    private static final List<Slide> GALLERY = List.of(
            new Slide("Máy ảnh & ống kính", 0, null), new Slide("Đồng hồ dây da", 1, null),
            new Slide("Tai nghe chụp tai", 2, null), new Slide("Ghế cao bằng gỗ", 3, null));
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final Duration DWELL = Duration.seconds(7);
    @FXML private GridPane hero;
    @FXML private VBox copy;
    @FXML private HBox thumbnails;
    @FXML private Label context, title, price, description, position, imageCredit;
    @FXML private Button previous, next, playback, action;
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
        dwell.getKeyFrames().setAll(new KeyFrame(Duration.ZERO, new KeyValue(dwellProgress.progressProperty(), 0)),
                new KeyFrame(DWELL, new KeyValue(dwellProgress.progressProperty(), 1, Interpolator.LINEAR)));
        dwell.setOnFinished(e -> move(1));
        setup(previous, "Ảnh trước", "M15 4 L8 11 L15 18");
        setup(next, "Ảnh tiếp theo", "M8 4 L15 11 L8 18");
        updatePlaybackButton();
        action.setGraphic(UiKit.icon("M3 10 H18 M12 4 L18 10 L12 16"));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(imageLayers.widthProperty());
        clip.heightProperty().bind(imageLayers.heightProperty());
        imageLayers.setClip(clip);
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
        for (Node control : List.of(action, previous, next, thumbnails)) {
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
            button.setAccessibleText(slide.name());
            button.setTooltip(UiKit.tooltip(slide.name()));
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
        else if (SessionData.getInstance().isLoggedIn()) openCatalog.run();
        else showPhoto(slide);
    }

    private void showPhoto(Slide slide) {
        Window owner = hero.getScene().getWindow();
        var screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        var url = getClass().getResource("/vn/edu/auction/client/icons/placeholder-" + FILES[slide.photo()] + ".jpg");
        ImageView photo = new ImageView(new Image(url.toExternalForm()));
        photo.setPreserveRatio(true);
        StackPane canvas = new StackPane(photo);
        photo.fitWidthProperty().bind(canvas.widthProperty());
        photo.fitHeightProperty().bind(canvas.heightProperty());
        canvas.setMinSize(0, 0);
        canvas.setPrefSize(Math.min(960, screen.getWidth() * .85), Math.min(640, screen.getHeight() * .72));
        canvas.setStyle("-fx-background-color: #14171c;");
        Button close = new Button("Đóng");
        close.getStyleClass().add("featured-control");
        close.setAccessibleText("Đóng ảnh");
        StackPane.setAlignment(close, javafx.geometry.Pos.TOP_RIGHT);
        StackPane.setMargin(close, new javafx.geometry.Insets(12));
        canvas.getChildren().add(close);
        Stage viewer = new Stage();
        viewer.initOwner(owner);
        viewer.initModality(Modality.WINDOW_MODAL);
        viewer.setTitle(slide.name() + " · Ảnh minh họa");
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
        context.setText(a == null ? "Bộ ảnh chọn lọc" : (a.getStatus() == AuctionStatus.RUNNING ? "Đang đấu giá" : "Sắp mở phiên"));
        title.setText(slide.name());
        title.setTooltip(UiKit.tooltip(slide.name()));
        price.setText(a == null ? "" : UiKit.money(a.getCurrentPrice()));
        description.setText(a == null ? "" : "Kết thúc " + UiKit.dt(a.getEndTime()));
        for (Label label : List.of(price, description)) {
            label.setVisible(!label.getText().isEmpty());
            label.setManaged(label.isVisible());
        }
        action.setText(a != null ? "Xem phiên đấu giá" : SessionData.getInstance().isLoggedIn()
                ? "Xem danh sách phiên" : "Xem trọn ảnh");
        position.setText((index + 1) + " / " + slides.size());
        imageCredit.setVisible(slide.photo() >= 0);
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
        if (photo >= 0) art.getStyleClass().addAll("featured-art", ART[photo]);
        else {
            art.getStyleClass().add("featured-missing");
            art.getChildren().add(new Label("Chưa có ảnh sản phẩm"));
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
        setup(playback, paused ? "Tiếp tục tự chuyển ảnh" : "Tạm dừng tự chuyển ảnh",
                paused ? "M7 4 L18 11 L7 18 Z" : "M8 4 V18 M15 4 V18");
    }

    private static void setup(Button button, String text, String path) {
        button.setGraphic(UiKit.icon(path));
        button.setTooltip(UiKit.tooltip(text));
        button.setAccessibleText(text);
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
                || slides.size() < 2 || action.isHover() || previous.isHover() || next.isHover() || thumbnails.isHover()) {
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
