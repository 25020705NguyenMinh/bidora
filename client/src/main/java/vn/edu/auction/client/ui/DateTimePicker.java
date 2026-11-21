package vn.edu.auction.client.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javafx.geometry.Pos;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

/**
 * Chọn ngày + giờ + phút theo giờ Việt Nam: bấm vào lịch để chọn hoặc gõ trực tiếp (dd/MM/yyyy, HH, mm).
 * Không phụ thuộc skin nội bộ của JavaFX; màu sắc lấy từ token sáng/tối trong theme.css.
 */
public final class DateTimePicker extends HBox {
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DatePicker date = new DatePicker();
    private final Spinner<Integer> hour = spinner(23);
    private final Spinner<Integer> minute = spinner(59);

    public DateTimePicker(LocalDateTime initial, String datePrompt) {
        getStyleClass().add("datetime-picker");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);

        date.setEditable(true);
        date.setPromptText(datePrompt);
        date.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate value) { return value == null ? "" : DATE.format(value); }
            @Override public LocalDate fromString(String text) { return parseDate(text); }
        });
        // Không cho chọn ngày đã qua trên lịch (server vẫn kiểm tra lại)
        date.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null && item.isBefore(UiKitClock.today())) setDisable(true);
            }
        });
        HBox.setHgrow(date, Priority.ALWAYS);
        date.setMaxWidth(Double.MAX_VALUE);
        date.setPrefWidth(150);

        hour.setPrefWidth(76);
        minute.setPrefWidth(76);
        Label colon = new Label(":");
        colon.getStyleClass().add("datetime-separator");
        getChildren().addAll(date, hour, colon, minute);

        if (initial != null) {
            date.setValue(initial.toLocalDate());
            hour.getValueFactory().setValue(initial.getHour());
            minute.getValueFactory().setValue(initial.getMinute());
        }
    }

    /** Ngày giờ (giờ Việt Nam) người dùng đang nhập; null nếu ô ngày trống hoặc sai định dạng. */
    public LocalDateTime value() {
        commit(hour);
        commit(minute);
        LocalDate day = parseDate(date.getEditor().getText());
        if (day == null) return null;
        return LocalDateTime.of(day, LocalTime.of(hour.getValue(), minute.getValue()));
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text.trim(), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Spinner<Integer> spinner(int max) {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, 0);
        factory.setWrapAround(true);
        factory.setConverter(new StringConverter<>() {
            @Override public String toString(Integer value) { return value == null ? "00" : String.format("%02d", value); }
            @Override public Integer fromString(String text) {
                try {
                    return Math.max(0, Math.min(max, Integer.parseInt(text.trim())));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        });
        Spinner<Integer> spinner = new Spinner<>(factory);
        spinner.setEditable(true);
        spinner.getEditor().setAlignment(Pos.CENTER);
        // gõ tay xong rồi bấm sang ô khác cũng được ghi nhận
        spinner.getEditor().focusedProperty().addListener((obs, was, focused) -> { if (!focused) commit(spinner); });
        return spinner;
    }

    private static void commit(Spinner<Integer> spinner) {
        SpinnerValueFactory<Integer> factory = spinner.getValueFactory();
        Integer parsed = factory.getConverter().fromString(spinner.getEditor().getText());
        factory.setValue(parsed);
        spinner.getEditor().setText(factory.getConverter().toString(parsed));
    }

    /** Ngày hôm nay theo giờ Việt Nam. */
    private static final class UiKitClock {
        static LocalDate today() { return LocalDate.now(UiKit.APP_ZONE); }
    }
}
