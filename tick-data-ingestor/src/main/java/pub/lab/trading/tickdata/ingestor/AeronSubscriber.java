package pub.lab.trading.tickdata.ingestor;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import play.lab.model.sbe.QuoteMessageDecoder;


public class AeronSubscriber implements FragmentHandler {
    private final QuoteMessageDecoder decoder = new QuoteMessageDecoder();
    private final QuestDBWriter writer;

    public AeronSubscriber(QuestDBWriter writer) {
        this.writer = writer;
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        decoder.wrap(buffer, offset, length, 0);

        String symbol = decoder.symbol().name();
        long creationTs = decoder.priceCreationTimestamp();
        long tenor = decoder.tenor();
        long valueDate = decoder.valueDate();
        long tier = decoder.clientTier();

        // Access the nested group: 'rung'
        QuoteMessageDecoder.RungDecoder rung = decoder.rung();
        int level = 0;

        while (rung.hasNext()) {
            rung.next();
            // Flatten: Write one QuestDB row per rung
            writer.writeQuote(
                    symbol,
                    creationTs,
                    tenor,
                    valueDate,
                    tier,
                    rung.bid(),
                    rung.ask(),
                    rung.volume(),
                    level++
            );
        }
    }
}

