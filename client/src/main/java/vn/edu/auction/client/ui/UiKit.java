package vn.edu.auction.client.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import vn.edu.auction.client.I18n;
import java.util.concurrent.Callable;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.client.api.FxAsync;

/** Tiện ích giao diện dùng chung. */
public final class UiKit {
    private UiKit() {}

    public static Tooltip tooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(javafx.util.Duration.millis(200));
        tip.setHideDelay(javafx.util.Duration.millis(120));
        return tip;
    }

    public static Tooltip localizedTooltip(String key) {
        Tooltip tip = tooltip("");
        I18n.localize(tip.textProperty(), key);
        return tip;
    }

    public static SVGPath icon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(Color.web("#343d50"));
        icon.getStyleClass().add("line-icon");
        icon.setStrokeWidth(1.7);
        icon.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        return icon;
    }

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter EN_DT = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter EN_DT_SEC = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm:ss", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter DT_SEC = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static String money(BigDecimal v) {
        if (v == null) return "—";
        NumberFormat format = NumberFormat.getCurrencyInstance(I18n.getLocale());
        format.setCurrency(Currency.getInstance("VND"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        return format.format(v);
    }

    /** Server lưu giờ UTC => đổi sang giờ máy người dùng trước khi hiển thị. */
    public static LocalDateTime toLocal(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static String dt(LocalDateTime utc) { return utc == null ? "—" : (I18n.isVietnamese() ? DT : EN_DT).format(toLocal(utc)); }
    public static String dtSec(LocalDateTime utc) { return utc == null ? "—" : (I18n.isVietnamese() ? DT_SEC : EN_DT_SEC).format(toLocal(utc)); }

    /** Thêm 1 cột chữ vào bảng. Truyền table vào để Java tự suy ra kiểu T. */
    public static <T> void col(TableView<T> table, String titleKey, Function<T, Object> value) {
        TableColumn<T, String> c = new TableColumn<>();
        I18n.localize(c.textProperty(), titleKey);
        c.setCellValueFactory(cd -> {
            Object v = value.apply(cd.getValue());
            return new SimpleStringProperty(v == null ? "—" : v.toString());
        });
        table.getColumns().add(c);
    }

    /** Gọi API ở nền; thành công thì chạy reload, lỗi thì hiện câu dễ hiểu lên label. */
    public static void act(Label msg, Callable<?> call, Runnable reload) {
        FxAsync.run(call, r -> { msg.setText(""); reload.run(); }, e -> {
            I18n.track(msg.textProperty(), () -> BaseApiClient.describe(e)); UiMotion.error(msg);
        });
    }
}
