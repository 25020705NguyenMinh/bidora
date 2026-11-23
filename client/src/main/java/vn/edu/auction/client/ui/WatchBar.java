package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import java.util.List;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;
import vn.edu.auction.client.api.ItemApiClient;
import vn.edu.auction.client.api.UserApiClient;
import vn.edu.auction.client.model.SessionData;

/** Theo dõi phiên, tắt/bật thông báo của phiên, và theo dõi người bán. Nhúng vào dialog chi tiết phiên. */
public final class WatchBar extends HBox {
    private final UserApiClient api = new UserApiClient();
    private final Button watchBtn = new Button();
    private final Button muteBtn = new Button();
    private final Button followBtn = new Button();
    private final Long auctionId;
    private boolean watching;
    private boolean muted;
    private boolean following;
    private boolean watchBusy;
    private boolean followBusy;
    private Long sellerId;

    public WatchBar(Long auctionId, Long itemId) {
        super(8);
        this.auctionId = auctionId;
        for (Button b : List.of(watchBtn, muteBtn, followBtn)) b.getStyleClass().add("outline-button");
        getChildren().addAll(watchBtn, muteBtn, followBtn);
        followBtn.setVisible(false);
        followBtn.setManaged(false);
        refreshText();

        if (!SessionData.getInstance().isLoggedIn()) {
            watchBtn.setDisable(true);
            muteBtn.setDisable(true);
            watchBtn.setTooltip(new Tooltip(I18n.get("watch.login")));
            return;
        }
        watchBtn.setOnAction(e -> pushWatch(!watching, muted));
        muteBtn.setOnAction(e -> pushWatch(true, !muted));
        followBtn.setOnAction(e -> toggleFollow());

        watchBusy = true;
        refreshText();
        FxAsync.run(() -> api.getWatch(auctionId), s -> {
            watching = s.watching(); muted = s.muted(); watchBusy = false; refreshText();
        }, e -> { watchBusy = false; refreshText(); error(e); });
        FxAsync.run(() -> new ItemApiClient().getItemById(itemId).getSellerId(), sid -> {
            sellerId = sid;
            if (sid != null && !sid.equals(SessionData.getInstance().getUserId())) {
                followBtn.setVisible(true);
                followBtn.setManaged(true);
                followBusy = true;
                refreshText();
                FxAsync.run(() -> api.getFollow(sid), f -> {
                    following = f.following(); followBusy = false; refreshText();
                }, e -> { followBusy = false; refreshText(); error(e); });
            }
        }, this::error);
    }

    private void pushWatch(boolean w, boolean m) {
        watchBusy = true;
        refreshText();
        FxAsync.run(() -> api.setWatch(auctionId, w, m),
                s -> { watching = s.watching(); muted = s.muted(); watchBusy = false; refreshText(); },
                e -> { watchBusy = false; refreshText(); error(e); });
    }

    private void toggleFollow() {
        boolean target = !following;
        followBusy = true;
        refreshText();
        FxAsync.run(() -> {
            if (target) api.follow(sellerId); else api.unfollow(sellerId);
            return target;
        }, v -> { following = v; followBusy = false; refreshText(); },
                e -> { followBusy = false; refreshText(); error(e); });
    }

    private void error(Throwable t) {
        new Alert(Alert.AlertType.WARNING, BaseApiClient.describe(t)).show();
    }

    private void refreshText() {
        watchBtn.setText(watchBusy ? I18n.get("common.loading") : watching ? I18n.get("watch.watching") : I18n.get("watch.add"));
        watchBtn.setDisable(watchBusy);
        muteBtn.setText(muted ? I18n.get("watch.unmute") : I18n.get("watch.mute"));
        muteBtn.setDisable(watchBusy || !watching);
        followBtn.setDisable(followBusy);
        followBtn.setText(following ? I18n.get("watch.following") : I18n.get("watch.follow"));
    }
}
