package open.trading.tradinggui.data;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import javafx.scene.layout.TilePane;
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

public class TickAeronSubscriber implements FragmentHandler, Worker, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickAeronSubscriber.class);
    private static final int LEVELS = 5;

    private final QuoteView quoteView = new QuoteView();
    private final Aeron aeron;
    private final Subscription sub;

    private final GuiUpdateThrottler updateThrottler;

    public TickAeronSubscriber(TilePane tilePane) {
        this.aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
        this.sub = aeron.addSubscription(
                AeronConfigs.LIVE_CHANNEL,
                StreamId.DATA_RAW_QUOTE.getCode());
        // Update GUI at max 30 Hz (33ms interval) to prevent stalling
        this.updateThrottler = new GuiUpdateThrottler(33, tilePane);
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        quoteView.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH);

        CurrencyPair currencyPair = quoteView.getSymbol();
        String symbol = currencyPair.name();

        long timestamp = quoteView.priceCreationTimestamp();
        int tenor = quoteView.getTenor();
        long valueDate = quoteView.getValueDate();
        long clientTier = quoteView.getClientTier();

        double[] bidPrices = new double[LEVELS];
        double[] askPrices = new double[LEVELS];
        int[] bidSizes = new int[LEVELS];
        int[] askSizes = new int[LEVELS];
        QuoteMessageDecoder.RungDecoder rungDecoder = quoteView.getRung();

        short level = 0;
        while (rungDecoder.hasNext()) {
            QuoteMessageDecoder.RungDecoder nextRung = rungDecoder.next();
            bidPrices[level] = nextRung.bid();
            askPrices[level] = nextRung.ask();
            bidSizes[level] = (int) nextRung.volume();
            askSizes[level] = (int) nextRung.volume();
            level++;
        }

        // Enqueue latest update to throttler (non-blocking, from Aeron thread)
        QuoteUpdate quoteUpdate = new QuoteUpdate(
                symbol,
                bidPrices[0],
                askPrices[0],
                bidPrices,
                bidSizes,
                askPrices,
                askSizes,
                timestamp
        );
        updateThrottler.enqueueUpdate(currencyPair, quoteUpdate);
        LOGGER.info("Enqueued update for {}: bid={}, ask={}", symbol, bidPrices, askPrices);
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

    /**
     * Get the GUI update throttler for use in JavaFX Application Thread
     */
    public GuiUpdateThrottler getUpdateThrottler() {
        return updateThrottler;
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

