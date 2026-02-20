package open.trading.tradinggui.widget;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class BigTile {

    private static final Logger LOGGER = LoggerFactory.getLogger(BigTile.class);

    private static final int LADDER_LEVELS = 5;

    // ---- Glassy trading-terminal palette ----
    private static final String TILE_BG = """
            -fx-background-color:
                linear-gradient(to bottom, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.04) 18%, rgba(255,255,255,0.00) 45%),
                linear-gradient(to bottom, #141018 0%, #0f0c12 60%, #0b0910 100%);
            -fx-background-radius: 16;
            -fx-border-radius: 16;
            -fx-border-width: 1;
            -fx-border-color: rgba(255,255,255,0.10);
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.60), 18, 0.25, 0, 8);
            """;

    private static final String HEADER_CSS = """
            -fx-font-family: 'Roboto Condensed';
            -fx-font-weight: 700;
            -fx-font-size: 15px;
            -fx-text-fill: rgba(245,245,255,0.88);
            -fx-padding: 10 12 6 12;
            """;

    private static final String SUBPANEL_CSS = """
            -fx-background-color: rgba(255,255,255,0.04);
            -fx-background-radius: 14;
            -fx-border-radius: 14;
            -fx-border-width: 1;
            -fx-border-color: rgba(255,255,255,0.06);
            -fx-padding: 1;
            """;

    private static final String GLASS_BUTTON_BASE = """
            -fx-background-color:
                linear-gradient(to bottom, rgba(255,255,255,0.22) 0%, rgba(255,255,255,0.06) 18%, rgba(255,255,255,0.00) 48%),
                linear-gradient(to bottom, #2a163a 0%, #1b1028 40%, #120b1e 100%),
                linear-gradient(to bottom, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.00) 65%),
                linear-gradient(to bottom, rgba(120,70,170,0.22) 0%, rgba(40,20,60,0.00) 75%);
            -fx-background-insets: 0, 0, 1, 0;
            -fx-background-radius: 14, 14, 13, 14;
            
            -fx-border-width: 1;
            -fx-border-radius: 14;
            -fx-border-color: rgba(255,255,255,0.12);
            
            -fx-padding: 1;
            -fx-font-family: 'Roboto Condensed';
            -fx-font-size: 34px;
            -fx-font-weight: 800;
            -fx-text-fill: rgba(245,245,255,0.92);
            
            -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.60), 14, 0.22, 0, 6);
            -fx-cursor: hand;
            """;

    private static final String BID_ACCENT = """
            -fx-border-color: rgba(120,255,170,0.30);
            -fx-text-fill: rgba(210,255,230,0.95);
            """;

    private static final String ASK_ACCENT = """
            -fx-border-color: rgba(255,120,160,0.30);
            -fx-text-fill: rgba(255,225,235,0.95);
            """;

    private static final String LADDER_CELL_BASE = """
            -fx-background-color:
                linear-gradient(to bottom, rgba(255,255,255,0.14) 0%, rgba(255,255,255,0.05) 22%, rgba(255,255,255,0.00) 55%),
                linear-gradient(to bottom, #22122f 0%, #160d22 55%, #110b1b 100%);
            -fx-background-radius: 10;
            -fx-border-radius: 10;
            -fx-border-width: 1;
            -fx-border-color: rgba(255,255,255,0.10);
            
            -fx-font-family: 'Roboto Condensed';
            -fx-font-weight: 800;
            -fx-font-size: 16px;
            
            -fx-padding: 7 10 7 10;
            -fx-text-fill: rgba(245,245,255,0.86);
            -fx-cursor: hand;
            
            -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.42), 9, 0.18, 0, 3);
            """;

    private static final String QTY_CELL_EXTRA = """
            -fx-font-size: 14px;
            -fx-font-weight: 800;
            -fx-text-fill: rgba(245,245,255,0.70);
            """;

    private static final String TOB_SPREAD_PILL_CSS = """
            -fx-background-color:
                linear-gradient(to right,
                                   transparent 0%,
                                   rgba(0,0,0,0.6) 20%,
                                   black,
                                   rgba(0,0,0,0.6) 80%,
                                   transparent 100%
                                 );
            -fx-background-radius: 0;
            -fx-border-width: 0;
            -fx-font-family: 'Roboto Condensed';
            -fx-font-weight: 700;
            -fx-text-fill: rgba(245,245,255,0.82);
            -fx-font-size: 14px;
            """;

    private static final String SPREAD_PILL_CSS = """
            -fx-background-color: rgba(25,25,25,0.85);
            -fx-background-radius: 8;
            -fx-border-radius: 8;
            -fx-border-width: 1;
            -fx-border-color: rgba(255,255,255,0.01);
            
            -fx-font-family: 'Roboto Condensed';
            -fx-font-weight: 900;
            -fx-text-fill: rgba(245,245,255,0.82);
            -fx-padding: 4 8 4 8;
            -fx-font-size: 12px;
            """;

    private static final String TOGGLE_LINK_CSS = """
            -fx-background-color: transparent;
            -fx-padding: 4 8 4 8;
            -fx-font-family: 'Roboto Condensed';
            -fx-font-size: 13px;
            -fx-font-weight: 800;
            -fx-text-fill: rgba(200,180,255,0.85);
            -fx-cursor: hand;
            """;

    private static final String MINI_HEADER_CSS = """
            -fx-font-family: 'Roboto Condensed';
            -fx-font-size: 11px;
            -fx-font-weight: 900;
            -fx-text-fill: rgba(245,245,255,0.65);
            """;

    // ---- Segmented switch (Ladder / Ticks) ----
    private static final String SEGMENT_BAR_CSS = """
            -fx-background-color: rgba(255,255,255,0.04);
            -fx-background-radius: 10;
            -fx-border-radius: 10;
            -fx-border-color: rgba(255,255,255,0.06);
            -fx-border-width: 1;
            -fx-padding: 4;
            """;

    private static final String SEGMENT_BTN_BASE = """
            -fx-background-color: transparent;
            -fx-background-radius: 8;
            -fx-border-radius: 8;
            -fx-border-width: 0;
            -fx-font-family: 'Roboto Condensed';
            -fx-font-size: 12px;
            -fx-font-weight: 900;
            -fx-text-fill: rgba(245,245,255,0.70);
            -fx-padding: 6 12 6 12;
            -fx-cursor: hand;
            """;

    private static final String SEGMENT_BTN_SELECTED = """
            -fx-background-color:
                linear-gradient(to bottom, rgba(255,255,255,0.12) 0%, rgba(255,255,255,0.04) 40%, rgba(255,255,255,0.00) 100%),
                linear-gradient(to bottom, #22122f 0%, #160d22 55%, #110b1b 100%);
            -fx-text-fill: rgba(245,245,255,0.92);
            -fx-border-color: rgba(255,255,255,0.10);
            -fx-border-width: 1;
            """;

    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final DecimalFormat pxFmt = new DecimalFormat("0.0000");
    private final DecimalFormat sprFmt = new DecimalFormat("0.0000");

    private final Label label;
    private final Pane pane;

    private final Button bid;
    private final Button ask;

    private final Label spreadOverlay;
    private final Button ladderToggle;

    // Segmented switch + stack
    private final ToggleGroup modeGroup;
    private final ToggleButton ladderMode;
    private final ToggleButton ticksMode;
    private final HBox segmentBar;

    private final StackPane panelStack;
    private final VBox ladderPanel;
    private final VBox chartPanel;

    // Ladder grid
    private final GridPane ladderGrid;

    private final XYChart.Series<Number, Number> midSeries;
    private final XYChart.Series<Number, Number> bidSeries;
    private final XYChart.Series<Number, Number> askSeries;

    // Keep references to chart and axes so we can force relayout / styling when window changes
    private LineChart<Number, Number> tobChart;
    private NumberAxis xAxis;
    private NumberAxis yAxis;

    private final ComboBox<String> windowBox;

    private volatile long windowMs = 5 * 60_000L; // default 5 minutes

    // bucket state (minute/hour coalesce)
    private long lastBucketX = Long.MIN_VALUE;

    // 5 levels x (BidQty, BidPx, Spread, AskPx, AskQty)
    private final Label[] bidQty = new Label[LADDER_LEVELS];
    private final Button[] bidPx = new Button[LADDER_LEVELS];
    private final Label[] lvlSpr = new Label[LADDER_LEVELS];
    private final Button[] askPx = new Button[LADDER_LEVELS];
    private final Label[] askQty = new Label[LADDER_LEVELS];

    public BigTile(String instrument) {

        this.bid = new Button("--");
        this.ask = new Button("--");

        this.spreadOverlay = new Label("0.00");
        this.spreadOverlay.setStyle(TOB_SPREAD_PILL_CSS);
        this.spreadOverlay.setMouseTransparent(true);

        this.ladderToggle = new Button("▴ "); // expanded by default
        this.ladderToggle.setStyle(TOGGLE_LINK_CSS);

        this.label = new Label(instrument);
        this.label.setStyle(HEADER_CSS);

        // ---- Ladder ----
        this.ladderGrid = new GridPane();
        buildLadderGrid();
        VBox ladderContent = new VBox(ladderGrid);
        ladderContent.setPadding(new Insets(4));
        ladderContent.setStyle(SUBPANEL_CSS);
        this.ladderPanel = new VBox(ladderContent);
        this.ladderPanel.setPadding(new Insets(4));

        // ---- Chart ----
        LineChart<Number, Number> tobChart = getLineChart();

        this.midSeries = new XYChart.Series<>();
        this.bidSeries = new XYChart.Series<>();
        this.askSeries = new XYChart.Series<>();
        tobChart.getData().addAll(midSeries, bidSeries, askSeries);

        this.windowBox = new ComboBox<>();
        this.windowBox.getItems().addAll("5m", "15m", "30m", "1h");
        this.windowBox.getSelectionModel().select(0);
        this.windowBox.setStyle("""
                -fx-background-radius: 10;
                -fx-border-radius: 10;
                -fx-border-color: rgba(255,255,255,0.10);
                -fx-border-width: 1;
                -fx-font-family: 'Roboto Condensed';
                -fx-font-weight: 800;
                -fx-text-fill: rgba(245,245,255,0.85);
                """);

        Label windowLbl = new Label("Window");
        windowLbl.setStyle("""
                -fx-font-family: 'Roboto Condensed';
                -fx-font-size: 12px;
                -fx-font-weight: 900;
                -fx-text-fill: rgba(245,245,255,0.75);
                """);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox chartHeader = new HBox(10, windowLbl, windowBox, spacer);
        chartHeader.setAlignment(Pos.CENTER_LEFT);
        chartHeader.setPadding(new Insets(0, 2, 4, 2));

        VBox chartContent = new VBox(6, chartHeader, tobChart);
        chartContent.setPadding(new Insets(4));
        chartContent.setStyle(SUBPANEL_CSS);
        this.chartPanel = new VBox(chartContent);
        this.chartPanel.setPadding(new Insets(4));

        // default: show ladder
        chartPanel.setVisible(false);
        chartPanel.setManaged(false);

        // ---- Stack holds ladder/chart panels ----
        this.panelStack = new StackPane(ladderPanel, chartPanel);

        // ---- Segmented switch ----
        this.modeGroup = new ToggleGroup();
        this.ladderMode = new ToggleButton("LADDER");
        this.ticksMode = new ToggleButton("TICKS");
        ladderMode.setToggleGroup(modeGroup);
        ticksMode.setToggleGroup(modeGroup);
        ladderMode.setSelected(true);

        ladderMode.setStyle(SEGMENT_BTN_BASE + SEGMENT_BTN_SELECTED);
        ticksMode.setStyle(SEGMENT_BTN_BASE);

        ladderMode.selectedProperty().addListener((obs, was, is) ->
                ladderMode.setStyle(is ? SEGMENT_BTN_BASE + SEGMENT_BTN_SELECTED : SEGMENT_BTN_BASE));
        ticksMode.selectedProperty().addListener((obs, was, is) ->
                ticksMode.setStyle(is ? SEGMENT_BTN_BASE + SEGMENT_BTN_SELECTED : SEGMENT_BTN_BASE));

        modeGroup.selectedToggleProperty().addListener((obs, o, t) -> {
            boolean showChart = (t == ticksMode);
            chartPanel.setVisible(showChart);
            chartPanel.setManaged(showChart);
            ladderPanel.setVisible(!showChart);
            ladderPanel.setManaged(!showChart);
        });

        this.segmentBar = new HBox(6, ladderMode, ticksMode);
        this.segmentBar.setAlignment(Pos.CENTER_LEFT);
        this.segmentBar.setStyle(SEGMENT_BAR_CSS);

        this.windowBox.setOnAction(e -> {
            String w = windowBox.getSelectionModel().getSelectedItem();
            this.windowMs = switch (w) {
                case "15m" -> 15 * 60_000L;
                case "30m" -> 30 * 60_000L;
                case "1h" -> 60 * 60_000L;
                default -> 5 * 60_000L;
            };

            // Immediately prune existing points and update axis bounds to reflect the new window
            Platform.runLater(() -> {
                long now = System.currentTimeMillis();
                long cutoff = now - this.windowMs;
                pruneOldPoints(cutoff);
                // Update X-axis bounds to show the new window size
                if (this.xAxis != null) {
                    xAxis.setAutoRanging(false);
                    xAxis.setLowerBound(cutoff);
                    xAxis.setUpperBound(now);
                }
                if (this.tobChart != null) this.tobChart.requestLayout();
            });
        });

        this.pane = createTile();
    }

    private LineChart<Number, Number> getLineChart() {
        // store axes on the instance so other handlers can force re-ranging
        this.xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(false);  // Don't auto-range; we set explicit bounds for the window
        xAxis.setMinorTickVisible(true);
        xAxis.setAutoRanging(false);
        xAxis.setTickLabelRotation(-90);
        xAxis.setTickUnit(60_000); // 1 minute ticks
        xAxis.setMinorTickCount(4);

        // Set initial bounds to show full window from now
        long now = System.currentTimeMillis();
        long tick = 60_000L;                 // 1 minute
        long upper = (now / tick) * tick;    // floor to whole minute
        long lower = upper - windowMs;

        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(lower);
        xAxis.setUpperBound(upper);
        xAxis.setTickUnit(tick);

        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                long ms = object.longValue();
                return HHMM.format(Instant.ofEpochMilli(ms));
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        this.yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        yAxis.setMinorTickVisible(false);

        // Chart + UI controls
        this.tobChart = new LineChart<>(xAxis, yAxis);
        tobChart.setAnimated(false);
        tobChart.setLegendVisible(false);
        tobChart.setCreateSymbols(false);

        // grids ON (subtle)
        tobChart.setHorizontalGridLinesVisible(true);
        tobChart.setVerticalGridLinesVisible(true);
        tobChart.setAlternativeColumnFillVisible(true);
        tobChart.setAlternativeRowFillVisible(true);
        tobChart.setTitle(null);
        tobChart.setStyle(SUBPANEL_CSS);
        tobChart.setMinHeight(180);
        tobChart.setPrefHeight(180);
        tobChart.setPrefWidth(210);

        // When the chart is attached to a scene, style internal nodes so the chart matches
        // the rest of the tile theme (plot background, grid lines, series line colors).
        tobChart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            // run later to ensure nodes are created in the scene graph
            Platform.runLater(() -> {
                try {
                    var plot = tobChart.lookup(".chart-plot-background");
                    if (plot != null) {
                        // make plot background match the subpanel look
                        plot.setStyle(SUBPANEL_CSS + " -fx-background-insets: 0; -fx-background-radius: 10;");
                    }

                    var horizon = tobChart.lookupAll(".chart-horizontal-grid-lines");
                    for (var n : horizon) {
                        n.setStyle("-fx-stroke: rgba(255,255,255,0.04); -fx-stroke-width: 1;");
                    }
                    var vert = tobChart.lookupAll(".chart-vertical-grid-lines");
                    for (var n : vert) {
                        n.setStyle("-fx-stroke: rgba(255,255,255,0.02); -fx-stroke-width: 1;");
                    }

                    // style series lines to be more visible on dark theme
                    var lines = tobChart.lookupAll(".chart-series-line");
                    int idx = 0;
                    for (var n : lines) {
                        // give mid/bid/ask distinct but theme-friendly colors
                        String color = (idx == 0) ? "rgba(180,180,255,0.95)" : (idx == 1) ? "rgba(120,255,170,0.95)" : "rgba(255,120,160,0.95)";
                        n.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 1.5;");
                        idx++;
                    }
                } catch (Exception ex) {
                    // best-effort styling — don't fail if lookup changes between JavaFX versions
                }
            });
        });

        return tobChart;
    }

    private static void applySpreadSizing(Label pill, boolean top) {
        String text = pill.getText();
        int len = (text == null) ? 0 : text.length();

        double verticalPadding = top ? 7 : 5;
        double baseMinWidth = top ? 64 : 56;

        double extra = Math.max(0, len - 4) * (top ? 6.0 : 5.0);

        pill.setPadding(new Insets(
                verticalPadding,
                10 + extra / 2.0,
                verticalPadding,
                10 + extra / 2.0
        ));
        pill.setMinWidth(baseMinWidth + extra);
        pill.setAlignment(Pos.CENTER);
    }

    private static String formatQty(int q) {
        if (q >= 1_000_000) return String.format("%.1fm", q / 1000000.0);
        if (q >= 10_000) return String.format("%.1fk", q / 1000.0);
        if (q >= 1_000) return String.format("%.2fk", q / 1000.0);
        return Integer.toString(q);
    }

    public void updateBook(
            double topBid,
            double topAsk,
            double[] bidPrices, int[] bidSizes,
            double[] askPrices, int[] askSizes
    ) {
        Platform.runLater(() -> {
            LOGGER.info("Updating {}: topBid={}, topAsk={}, bidPrices={}, bidSizes={}, askPrices={}, askSizes={}", this.label.getText(),
                    topBid, topAsk, bidPrices, bidSizes, askPrices, askSizes);
            bid.setText(pxFmt.format(topBid));
            ask.setText(pxFmt.format(topAsk));

            double topSpr = topAsk - topBid;
            spreadOverlay.setText(sprFmt.format(topSpr));
            applySpreadSizing(spreadOverlay, true);

            // ---- Tick capture for chart ----
            double mid = (topBid + topAsk) / 2.0;
            appendTick(topBid, topAsk, mid);

            for (int i = 0; i < LADDER_LEVELS; i++) {
                bidPx[i].setText(pxFmt.format(bidPrices[i]));
                askPx[i].setText(pxFmt.format(askPrices[i]));

                double s = askPrices[i] - bidPrices[i];
                lvlSpr[i].setText(sprFmt.format(s));
                applySpreadSizing(lvlSpr[i], false);

                bidQty[i].setText(formatQty(bidSizes[i]));
                askQty[i].setText(formatQty(askSizes[i]));
            }
        });
    }

    private void appendTick(double b, double a, double m) {
        long x = System.currentTimeMillis();
        pruneOldPoints(x - windowMs);

        lastBucketX = x;

        XYChart.Data<Number, Number> midP = new XYChart.Data<>(x, m);
        XYChart.Data<Number, Number> bidP = new XYChart.Data<>(x, b);
        XYChart.Data<Number, Number> askP = new XYChart.Data<>(x, a);

        midSeries.getData().add(midP);
        bidSeries.getData().add(bidP);
        askSeries.getData().add(askP);

        // Update X-axis bounds to keep the window showing the last 'windowMs' milliseconds
        if (xAxis != null && !xAxis.isAutoRanging()) {
            long tick = 60_000L;
            long upper = (x / tick) * tick;
            long lower = upper - windowMs;

            xAxis.setLowerBound(lower);
            xAxis.setUpperBound(upper);
        }

    }

    private void pruneOldPoints(long cutoffEpochMs) {
        pruneSeries(midSeries, cutoffEpochMs);
        pruneSeries(bidSeries, cutoffEpochMs);
        pruneSeries(askSeries, cutoffEpochMs);
    }

    private void pruneSeries(XYChart.Series<Number, Number> s, long cutoffEpochMs) {
        var data = s.getData();
        int idx = 0;
        while (idx < data.size()) {
            long x = data.get(idx).getXValue().longValue();
            if (x >= cutoffEpochMs) break;
            idx++;
        }
        if (idx > 0) data.remove(0, idx);
    }

    private Pane createTile() {
        bid.setPrefSize(140, 80);
        bid.setStyle(GLASS_BUTTON_BASE + BID_ACCENT);
        bid.setOnMouseEntered(e ->
                bid.setStyle(GLASS_BUTTON_BASE + BID_ACCENT + "-fx-border-color: rgba(120,255,170,0.55);"));
        bid.setOnMouseExited(e -> bid.setStyle(GLASS_BUTTON_BASE + BID_ACCENT));

        ask.setPrefSize(140, 80);
        ask.setStyle(GLASS_BUTTON_BASE + ASK_ACCENT);
        ask.setOnMouseEntered(e ->
                ask.setStyle(GLASS_BUTTON_BASE + ASK_ACCENT + "-fx-border-color: rgba(255,120,160,0.55);"));
        ask.setOnMouseExited(e -> ask.setStyle(GLASS_BUTTON_BASE + ASK_ACCENT));

        HBox bidAsk = new HBox(40, bid, ask);
        bidAsk.setAlignment(Pos.CENTER);
        bidAsk.setPadding(new Insets(2));

        StackPane topRow = new StackPane(bidAsk, spreadOverlay);
        StackPane.setAlignment(spreadOverlay, Pos.CENTER);
        topRow.setStyle(SUBPANEL_CSS);

        ladderToggle.setOnAction(e -> togglePanel());

        HBox headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(0, 6, 0, 0));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(label, spacer, ladderToggle);

        VBox all = new VBox(8, headerRow, topRow, segmentBar, panelStack);
        all.setPadding(new Insets(4));
        all.setStyle(TILE_BG);

        return all;
    }

    private void buildLadderGrid() {
        ladderGrid.getChildren().clear();
        ladderGrid.setHgap(8);
        ladderGrid.setVgap(6);
        ladderGrid.setAlignment(Pos.CENTER);

        Label bq = new Label("BID QTY");
        bq.setStyle(MINI_HEADER_CSS + "-fx-text-fill: rgba(120,255,170,0.65);");

        Label bp = new Label("BID");
        bp.setStyle(MINI_HEADER_CSS + "-fx-text-fill: rgba(120,255,170,0.75);");

        Label sp = new Label("Δ");
        sp.setStyle(MINI_HEADER_CSS);

        Label ap = new Label("ASK");
        ap.setStyle(MINI_HEADER_CSS + "-fx-text-fill: rgba(255,120,160,0.75);");

        Label aq = new Label("ASK QTY");
        aq.setStyle(MINI_HEADER_CSS + "-fx-text-fill: rgba(255,120,160,0.65);");

        ladderGrid.add(bq, 0, 0);
        ladderGrid.add(bp, 1, 0);
        ladderGrid.add(sp, 2, 0);
        ladderGrid.add(ap, 3, 0);
        ladderGrid.add(aq, 4, 0);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHalignment(javafx.geometry.HPos.RIGHT);
        //c0.setMinWidth(60);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        //c1.setMinWidth(90);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHalignment(javafx.geometry.HPos.CENTER);
        //c2.setMinWidth(56);

        ColumnConstraints c3 = new ColumnConstraints();
        c3.setHgrow(Priority.ALWAYS);
        //c3.setMinWidth(90);

        ColumnConstraints c4 = new ColumnConstraints();
        c4.setHalignment(javafx.geometry.HPos.LEFT);
        //c4.setMinWidth(60);

        ladderGrid.getColumnConstraints().setAll(c0, c1, c2, c3, c4);

        for (int i = 0; i < LADDER_LEVELS; i++) {
            Label bQty = new Label("--");
            bQty.setStyle(LADDER_CELL_BASE + QTY_CELL_EXTRA + "-fx-background-color: transparent; -fx-border-color: transparent; -fx-effect: null;");
            bQty.setMinWidth(60);
            bQty.setAlignment(Pos.CENTER_RIGHT);

            Label aQty = new Label("--");
            aQty.setStyle(LADDER_CELL_BASE + QTY_CELL_EXTRA + "-fx-background-color: transparent; -fx-border-color: transparent; -fx-effect: null;");
            aQty.setMinWidth(60);
            aQty.setAlignment(Pos.CENTER_LEFT);

            Button bPx = new Button("--");
            bPx.setMaxWidth(Double.MAX_VALUE);
            bPx.setAlignment(Pos.CENTER);
            bPx.setTextAlignment(TextAlignment.CENTER);
            bPx.setStyle(LADDER_CELL_BASE + BID_ACCENT);
            bPx.setOnMouseEntered(e -> bPx.setStyle(LADDER_CELL_BASE + BID_ACCENT + "-fx-border-color: rgba(120,255,170,0.55);"));
            bPx.setOnMouseExited(e -> bPx.setStyle(LADDER_CELL_BASE + BID_ACCENT));

            Button aPx = new Button("--");
            aPx.setMaxWidth(Double.MAX_VALUE);
            aPx.setAlignment(Pos.CENTER);
            aPx.setTextAlignment(TextAlignment.CENTER);
            aPx.setStyle(LADDER_CELL_BASE + ASK_ACCENT);
            aPx.setOnMouseEntered(e -> aPx.setStyle(LADDER_CELL_BASE + ASK_ACCENT + "-fx-border-color: rgba(255,120,160,0.55);"));
            aPx.setOnMouseExited(e -> aPx.setStyle(LADDER_CELL_BASE + ASK_ACCENT));

            Label spreadMid = new Label("--");
            spreadMid.setStyle(SPREAD_PILL_CSS);
            spreadMid.setAlignment(Pos.CENTER);
            spreadMid.setMinWidth(56);
            spreadMid.setMouseTransparent(true);

            bidQty[i] = bQty;
            askQty[i] = aQty;
            bidPx[i] = bPx;
            askPx[i] = aPx;
            lvlSpr[i] = spreadMid;

            int row = i + 1;
            ladderGrid.add(bQty, 0, row);
            ladderGrid.add(bPx, 1, row);
            ladderGrid.add(spreadMid, 2, row);
            ladderGrid.add(aPx, 3, row);
            ladderGrid.add(aQty, 4, row);

            GridPane.setHgrow(bPx, Priority.ALWAYS);
            GridPane.setHgrow(aPx, Priority.ALWAYS);
        }
    }

    private void togglePanel() {
        boolean show = !panelStack.isVisible();
        panelStack.setVisible(show);
        panelStack.setManaged(show);

        segmentBar.setVisible(show);
        segmentBar.setManaged(show);

        ladderToggle.setText(show ? "▴ " : "▾ ");
    }

    public Pane getPane() {
        return pane;
    }
}
