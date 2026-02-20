package open.trading.tradinggui.data;

import javafx.application.Platform;
import javafx.scene.layout.TilePane;
import open.trading.tradinggui.widget.BigTile;
import open.trading.tradinggui.widget.BigTileFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.CurrencyPair;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles high-frequency data updates and batches them for GUI updates.
 * <p>
 * This allows Aeron threads to enqueue latest updates without blocking,
 * while the JavaFX Application Thread processes batches at a controlled interval.
 */
public class GuiUpdateThrottler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuiUpdateThrottler.class);
    private final ConcurrentHashMap<CurrencyPair, QuoteUpdate> latestUpdates = new ConcurrentHashMap<>();
    private final Map<CurrencyPair, BigTile> tiles = new LinkedHashMap<>();
    private final long updateIntervalNanos;
    private final TilePane tilePane;
    private long lastUpdateNanos = 0;// Set to true for debugging

    /**
     * @param updateIntervalMs Minimum milliseconds between GUI updates (e.g., 33 for 30 Hz)
     * @param tilePane
     */
    public GuiUpdateThrottler(long updateIntervalMs, TilePane tilePane) {
        this.updateIntervalNanos = updateIntervalMs * 1_000_000L;
        this.tilePane = tilePane;
    }

    /**
     * Called from Aeron thread - Non-blocking enqueue of latest data
     *
     * @param key   Unique identifier for the update (e.g., "EURUSD")
     * @param value Latest data object
     */
    public void enqueueUpdate(CurrencyPair key, QuoteUpdate value) {
        latestUpdates.put(key, value);
    }

    /**
     * Called from JavaFX Application Thread to process batched updates
     * Should be called from AnimationTimer or periodic task
     */
    public void processBatchUpdates() {
        long now = System.nanoTime();
        if (now - lastUpdateNanos >= updateIntervalNanos) {
            if (!latestUpdates.isEmpty()) {
                for (var entry : latestUpdates.entrySet()) {
                    if (!tiles.containsKey(entry.getKey())) {
                        try {
                            final BigTile tile = BigTileFactory.create(entry.getKey().name());
                            tiles.put(entry.getKey(), tile);
                            Platform.runLater(() -> {
                                tilePane.getChildren().add(tile.getPane());
                            });
                            LOGGER.info("Created tile for: {}", entry.getKey());
                        } catch (InterruptedException e) {
                            LOGGER.error(e.getMessage());
                            Thread.currentThread().interrupt();
                        }
                    }

                    QuoteUpdate update = entry.getValue();
                    // Update the corresponding tile with the latest quote
                    // This assumes you have a method to get the tile by symbol
                    BigTile tile = tiles.get(entry.getKey());
                    if (tile != null) {
                        tile.updateBook(
                                update.bid(),
                                update.ask(),
                                update.bidPrices(),
                                update.bidSizes(),
                                update.askPrices(),
                                update.askSizes()
                        );
                        lastUpdateNanos = now;
                    }
                }
            }
        }
    }

    /**
     * Clear all pending updates
     */
    public void clear() {
        latestUpdates.clear();
    }

}

