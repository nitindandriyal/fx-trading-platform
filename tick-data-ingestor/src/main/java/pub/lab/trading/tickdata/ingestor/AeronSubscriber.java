package pub.lab.trading.tickdata.ingestor;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
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

public class AeronSubscriber implements FragmentHandler, Worker, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AeronSubscriber.class);

    private final QuestDBWriter writer;
    private final QuoteView quoteView = new QuoteView();
    private final Aeron aeron;
    private final Subscription sub;

    public AeronSubscriber(QuestDBWriter writer) {
        this.aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
        this.sub = aeron.addSubscription(
                AeronConfigs.LIVE_CHANNEL,
                StreamId.RAW_QUOTE.getCode());
        this.writer = writer;
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        quoteView.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH);

        CurrencyPair currencyPair = quoteView.getSymbol();
        long timestamp = quoteView.priceCreationTimestamp();
        int tenor = quoteView.getTenor();
        long valueDate = quoteView.getValueDate();
        long clientTier = quoteView.getClientTier();
        QuoteMessageDecoder.RungDecoder rungDecoder = quoteView.getRung();

        short level = 0;
        while (rungDecoder.hasNext()) {
            QuoteMessageDecoder.RungDecoder nextRung = rungDecoder.next();
            writer.writeQuote(
                    currencyPair.name(),
                    timestamp,
                    tenor,
                    valueDate,
                    clientTier,
                    nextRung.bid(),
                    nextRung.ask(),
                    nextRung.volume(),
                    level++
            );
        }
    }

    @Override
    public int doWork() throws Exception {
        // Poll Aeron for new fragments
        int fragmentsRead = sub.poll(this, 10);

        // If we processed data, flush to QuestDB based on your latency requirements
        if (fragmentsRead > 0) {
            writer.flush();
        }

        return fragmentsRead;
    }

    @Override
    public String roleName() {
        return "TickDataOIngestionAeronSubscriber";
    }

    @Override
    public void close() {
        LOGGER.info("Closing TickDataOIngestionAeronSubscriber...");
        CloseHelper.close(sub);
        CloseHelper.close(aeron);
    }
}

