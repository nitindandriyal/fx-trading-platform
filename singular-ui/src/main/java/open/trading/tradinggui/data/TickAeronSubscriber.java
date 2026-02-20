package open.trading.tradinggui.data;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import javafx.application.Platform;
import javafx.scene.layout.TilePane;
import open.trading.tradinggui.widget.BigTile;
import open.trading.tradinggui.widget.BigTileFactory;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.CurrencyPair;
import play.lab.model.sbe.MessageHeaderDecoder;
import play.lab.model.sbe.QuoteMessageDecoder;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.model.pricing.QuoteView;

import java.util.LinkedHashMap;
import java.util.Map;

public class TickAeronSubscriber implements FragmentHandler, Worker, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickAeronSubscriber.class);
    private static final int LEVELS = 5;

    private final QuoteView quoteView = new QuoteView();
    private final Aeron aeron;
    private final Subscription sub;
    private final Map<CurrencyPair, BigTile> tiles = new LinkedHashMap<>();
    private final TilePane tilePane;

    public TickAeronSubscriber(TilePane tilePane) {
        this.tilePane = tilePane;
        this.aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
        this.sub = aeron.addSubscription(
                AeronConfigs.LIVE_CHANNEL,
                StreamId.DATA_RAW_QUOTE.getCode());
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        quoteView.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH);

        CurrencyPair currencyPair = quoteView.getSymbol();
        if(!tiles.containsKey(currencyPair)) {
            try {
                final BigTile tile = BigTileFactory.create(currencyPair.name());
                tiles.put(currencyPair, tile);
                Platform.runLater(() -> {
                    tilePane.getChildren().add(tile.getPane());
                });
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        long timestamp = quoteView.priceCreationTimestamp();
        int tenor = quoteView.getTenor();
        long valueDate = quoteView.getValueDate();
        long clientTier = quoteView.getClientTier();

        double[] bidPrices = new double[LEVELS];
        double[] askPrices = new double[LEVELS];
        int[] bidSizes = new int[LEVELS];
        int[] askSizes = new int[LEVELS];
        QuoteMessageDecoder.RungDecoder rungDecoder = quoteView.getRung();
        BigTile tile = tiles.get(currencyPair);
        
        short level = 0;
        while (rungDecoder.hasNext()) {
            QuoteMessageDecoder.RungDecoder nextRung = rungDecoder.next();
            bidPrices[level] = nextRung.bid();
            askPrices[level] = nextRung.ask();
            bidSizes[level] = (int) nextRung.volume(); // Assuming volume represents size
            askSizes[level] = (int) nextRung.volume(); // Adjust if ask size is different
            level++;
        }

        tile.updateBook(bidPrices[0], askPrices[0], bidPrices, bidSizes, askPrices, askSizes);
    }

    @Override
    public int doWork() throws Exception {
        // Poll Aeron for new fragments
        int fragmentsRead = sub.poll(this, 10);

        // If we processed data, flush to QuestDB based on your latency requirements
        if (fragmentsRead > 0) {

        }

        return fragmentsRead;
    }

    @Override
    public String roleName() {
        return "TickDataIngestionAeronSubscriber";
    }

    @Override
    public void close() {
        LOGGER.info("Closing TickDataIngestionAeronSubscriber...");
        CloseHelper.close(sub);
        CloseHelper.close(aeron);
    }
}

