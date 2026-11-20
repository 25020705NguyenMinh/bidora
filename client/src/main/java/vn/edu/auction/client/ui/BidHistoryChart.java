package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import vn.edu.auction.shared.dto.BidResponse;
import vn.edu.auction.shared.enums.BidStatus;

public final class BidHistoryChart extends VBox {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    public BidHistoryChart(BigDecimal startingPrice, LocalDateTime startTime,
                           List<BidResponse> history, boolean demo) {
        super(10);
        getStyleClass().add("history-panel");
        List<BidResponse> accepted = history.stream()
                .filter(b -> b != null && b.getStatus() == BidStatus.ACCEPTED
                        && b.getBidTime() != null && b.getAmount() != null)
                .sorted(Comparator.comparing(BidResponse::getBidTime)
                        .thenComparing(BidResponse::getBidId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Label title = new Label(demo ? I18n.get("chart.sample") : I18n.get("chart.title"));
        title.getStyleClass().add("history-title");
        getChildren().add(title);
        if (accepted.isEmpty()) {
            Label empty = I18n.caption(new Label(), "chart.empty");
            empty.getStyleClass().add("history-empty");
            getChildren().add(empty);
            return;
        }
        BigDecimal unit = accepted.get(accepted.size() - 1).getAmount()
                .compareTo(new BigDecimal("1000000")) >= 0 ? new BigDecimal("1000000") : new BigDecimal("1000");
        LocalDateTime origin = startTime == null ? accepted.get(0).getBidTime() : startTime;
        NumberAxis timeAxis = new NumberAxis();
        timeAxis.setForceZeroInRange(true);
        timeAxis.setMinorTickVisible(false);
        timeAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) {
                return TIME.format(UiKit.toLocal(origin.plusSeconds(value.longValue())));
            }
            @Override public Number fromString(String value) { throw new UnsupportedOperationException(); }
        });
        NumberAxis priceAxis = new NumberAxis();
        priceAxis.setForceZeroInRange(false);
        priceAxis.setMinorTickVisible(false);
        priceAxis.setLabel(unit.intValue() == 1000000 ? I18n.get("chart.millions") : I18n.get("chart.thousands"));
        LineChart<Number, Number> chart = new LineChart<>(timeAxis, priceAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(235);
        chart.setMinHeight(235);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(false);
        chart.getStyleClass().add("bid-history-chart");
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        if (startingPrice != null && startTime != null) {
            addPoint(series, origin, startTime, startingPrice, unit, I18n.get("item.startingPrice"), null);
        }
        for (BidResponse bid : accepted) {
            addPoint(series, origin, bid.getBidTime(), bid.getAmount(), unit,
                    I18n.get("chart.bidder") + bid.getBidderId(), demo ? null : bid.getBidderId());
        }
        chart.getData().add(series);
        getChildren().add(chart);
        Label hint = new Label(I18n.get("chart.hint", accepted.size())
                + (demo ? "" : I18n.get("chart.profileHint")));
        hint.getStyleClass().add("detail-note");
        hint.setWrapText(true);
        getChildren().add(hint);
    }

    private void addPoint(XYChart.Series<Number, Number> series, LocalDateTime start,
                          LocalDateTime time, BigDecimal amount, BigDecimal unit, String title, Long bidderId) {
        XYChart.Data<Number, Number> point = new XYChart.Data<>(
                Duration.between(start, time).toMillis() / 1000.0,
                amount.divide(unit).doubleValue());
        String text = title + "\n" + UiKit.money(amount) + "\n" + FULL_TIME.format(UiKit.toLocal(time));
        point.nodeProperty().addListener((observable, oldNode, node) -> {
            installTooltip(node, text);
            if (node != null && bidderId != null) {
                node.setCursor(javafx.scene.Cursor.HAND);
                node.setFocusTraversable(true);
                Runnable open = () -> TradingProfileDialog.show(node.getScene().getWindow(), bidderId, false);
                node.setOnMouseClicked(e -> open.run());
                node.setOnKeyPressed(e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.ENTER) { e.consume(); open.run(); }
                });
            }
        });
        series.getData().add(point);
    }

    private void installTooltip(Node node, String text) {
        if (node != null) {
            if (!node.accessibleTextProperty().isBound()) {
                node.setAccessibleText(text.replace('\n', ' '));
            }
            Tooltip.install(node, UiKit.tooltip(text));
        }
    }
}
