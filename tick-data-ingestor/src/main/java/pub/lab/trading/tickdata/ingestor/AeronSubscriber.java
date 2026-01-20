package pub.lab.trading.tickdata.ingestor;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import play.lab.model.sbe.CurrencyPair;
import play.lab.model.sbe.MessageHeaderDecoder;
import play.lab.model.sbe.QuoteMessageDecoder;
import pub.lab.trading.common.model.pricing.QuoteView;

public class AeronSubscriber implements FragmentHandler {
    private final QuestDBWriter writer;
    private final QuoteView quoteView = new QuoteView();

    public AeronSubscriber(QuestDBWriter writer) {
        this.writer = writer;
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        quoteView.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH);

        CurrencyPair currencyPair = quoteView.getSymbol();
        long timestamp = quoteView.priceCreationTimestamp();
        long tenor = quoteView.getTenor();
        long valueDate = quoteView.getValueDate();
        long clientTier = quoteView.getClientTier();

        int level = 0;
        while (quoteView.getRung().hasNext()) {
            QuoteMessageDecoder.RungDecoder nextRung = quoteView.getRung().next();
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
}

