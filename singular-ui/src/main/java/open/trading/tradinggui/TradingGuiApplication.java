package open.trading.tradinggui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import open.trading.tradinggui.config.PairConfig;
import open.trading.tradinggui.data.TickAeronSubscriber;
import open.trading.tradinggui.widget.BigTile;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import pub.lab.trading.common.lifecycle.MultiStreamPoller;
import pub.lab.trading.common.lifecycle.Worker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TradingGuiApplication extends Application {

    public static void main(String[] args) {
        launch();
    }

    private static VBox getSettingsVBox(ListView<PairConfig> list) {
        VBox spreadSettings = getSpreadSettings(list);

        return new VBox(10,
                new HBox(new Label("Settings") {{
                    setStyle("""
                                -fx-font-family: 'Roboto Condensed';
                                -fx-font-size: 16px;
                                -fx-font-weight: 900;
                                -fx-text-fill: rgba(245,245,255,0.80);
                            """);
                }}) {{
                    setStyle("""
                                -fx-background-color:
                                    linear-gradient(to bottom, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.04) 18%, rgba(255,255,255,0.00) 45%),
                                    linear-gradient(to bottom, #141018 0%, #0f0c12 60%, #0b0910 100%);
                                    -fx-font-family: 'Roboto Condensed';
                                -fx-background-radius: 10;
                                -fx-border-radius: 10;
                                -fx-border-width: 1;
                                -fx-border-color: rgba(255,255,255,0.06);
                                -fx-padding: 8px 12px;
                            """);
                }},
                spreadSettings
        );
    }

    private static VBox getSpreadSettings(ListView<PairConfig> list) {
        VBox spreadSettings = new VBox(10,
                new Label("Core Spread(Pips)") {{
                    setStyle("""
                                -fx-font-family: 'Roboto Condensed';
                                -fx-font-size: 14px;
                                -fx-font-weight: 900;
                                -fx-text-fill: rgba(245,245,255,0.80);
                                -fx-padding: 8px 12px;
                            """);
                }},
                list
        );

        spreadSettings.setStyle("""
                    -fx-background-color:
                                    linear-gradient(to bottom, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.04) 18%, rgba(255,255,255,0.00) 45%),
                                    linear-gradient(to bottom, #141018 0%, #0f0c12 60%, #0b0910 100%);
                                    -fx-font-family: 'Roboto Condensed';
                    -fx-font-size: 12px;
                    -fx-font-weight: 700;
                    -fx-text-fill: rgba(245,245,255,0.75);
                """);

        return spreadSettings;
    }

    @Override
    public void start(Stage stage) throws InterruptedException {
        System.setProperty("prism.lcdtext", "false");

        // Root becomes BorderPane so we can have a right drawer
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #191038;");

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add("https://fonts.googleapis.com/css2?family=Roboto+Condensed:wght@700&display=swap");
        scene.getStylesheets().add("https://fonts.googleapis.com/css2?family=Merriweather:wght@700");

        // --- Title bar ---
        Label title = new Label("SingularityFX");
        title.setStyle("""
                    -fx-font-family: 'Merriweather';
                    -fx-font-size: 36px;
                    -fx-text-fill: #d6d6d6;
                    -fx-font-smoothing-type: gray;
                """);
        HBox titleBar = new HBox(title);
        titleBar.setPadding(new Insets(16));
        titleBar.setStyle("-fx-background-color: #191038;");
        root.setTop(titleBar);

        // --- Center tiles ---
        TilePane tilePane = new TilePane();
        tilePane.setStyle("-fx-background-color: #191038;");
        tilePane.setHgap(8);
        tilePane.setVgap(16);
        tilePane.setPadding(new Insets(20));

        // put tilePane in a ScrollPane so you can scale to many tiles
        ScrollPane scroll = new ScrollPane(tilePane);
        scroll.setFitToWidth(true);
        scroll.setStyle("""
                    -fx-background: #191038;
                    -fx-background-color: #191038;
                    -fx-control-inner-background: #191038;
                """);
        root.setCenter(scroll);

        // --- Build tiles + config map ---

        Map<String, PairConfig> configs = new LinkedHashMap<>();

        for (String sym : List.of("EURUSD", "GBPUSD", "EURJPY", "USDCAD", "EURAUD", "USDJPY", "EURDKK", "EURSEK", "EURNOK")) {
            configs.put(sym, new PairConfig(sym));
        }

        // --- Right drawer: configs ---
        Node drawer = buildRightDrawer(configs);
        root.setRight(drawer);

        // --- Start demo pricing service reading the slider configs ---
        //new DemoPriceService().start(tiles, configs);

        // Create Aeron subscriber with throttler
        TickAeronSubscriber tickSubscriber = new TickAeronSubscriber(tilePane);

        // Setup AnimationTimer to process batched GUI updates at 60 FPS,
        // but throttled to 30 Hz (33ms) to prevent stalling
        AnimationTimer guiUpdateTimer = new AnimationTimer() {
            private final Map<String, BigTile> tileCache = new LinkedHashMap<>();

            @Override
            public void handle(long now) {
                // Process batched updates from throttler
                tickSubscriber.getUpdateThrottler().processBatchUpdates();
            }
        };
        guiUpdateTimer.start();

        AgentRunner agentRunner = new AgentRunner(new BackoffIdleStrategy(),
                Throwable::printStackTrace,
                null,
                new MultiStreamPoller(
                        "marketdata-ingestion-poller",
                        new Worker[]{
                                tickSubscriber
                        }
                ));
        AgentRunner.startOnThread(agentRunner);

        stage.setTitle("Singularity FX");
        stage.setScene(scene);
        stage.show();
    }

    private Node buildRightDrawer(Map<String, PairConfig> configsBySymbol) {

        // Backing list (virtualized by ListView)
        ObservableList<PairConfig> backing = FXCollections.observableArrayList(configsBySymbol.values());
        FilteredList<PairConfig> filtered = new FilteredList<>(backing, x -> true);

        ListView<PairConfig> list = new ListView<>(filtered);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PairConfig pc, boolean empty) {
                super.updateItem(pc, empty);
                if (empty || pc == null) {
                    setGraphic(null);
                    return;
                }

                Label sym = new Label(pc.symbol());
                sym.setStyle("""
                            -fx-font-family: 'Roboto Condensed';
                            -fx-font-size: 14px;
                            -fx-font-weight: 800;
                            -fx-text-fill: rgba(245,245,255,0.85);
                        """);
                sym.setMinWidth(80);

                Slider spread = new Slider(0.001, 0.080, pc.getCoreSpreadPips()); // demo range
                spread.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(spread, Priority.ALWAYS);

                // bind slider <-> config property
                pc.coreSpreadPipsProperty().bind(spread.valueProperty());

                Label val = new Label();
                val.setStyle("""
                            -fx-font-family: 'Roboto Condensed';
                            -fx-font-size: 12px;
                            -fx-font-weight: 900;
                            -fx-text-fill: rgba(245,245,255,0.75);
                        """);
                val.textProperty().bind(Bindings.format("%.3f", spread.valueProperty()));
                val.setMinWidth(54);
                val.setAlignment(Pos.CENTER_RIGHT);

                HBox row = new HBox(10, sym, spread, val);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6));
                row.setStyle("""
                            -fx-background-color: #22122f;
                            -fx-background-radius: 10;
                            -fx-border-radius: 10;
                            -fx-border-width: 1;
                            -fx-border-color: rgba(255,255,255,0.06);
                        """);

                setGraphic(row);
            }
        });

        list.setStyle("""
                    -fx-background-color:
                        linear-gradient(to bottom, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.04) 18%, rgba(255,255,255,0.00) 45%),
                        linear-gradient(to bottom, #141018 0%, #0f0c12 60%, #0b0910 100%);
                    -fx-control-inner-background: transparent;
                    -fx-background-insets: 0;
                """
        );

        VBox panel = getSettingsVBox(list);
        panel.setPadding(new Insets(12));
        panel.setPrefWidth(360);

        HBox drawer = new HBox(panel);

        // Expanded by default
        panel.setVisible(true);
        panel.setManaged(true);
        drawer.setPrefWidth(360);


        return drawer;
    }
}