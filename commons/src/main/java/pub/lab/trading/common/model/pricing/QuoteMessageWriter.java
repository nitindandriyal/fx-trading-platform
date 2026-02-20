package pub.lab.trading.common.model.pricing;

import org.agrona.concurrent.UnsafeBuffer;
import play.lab.model.sbe.CurrencyPair;
import play.lab.model.sbe.MessageHeaderEncoder;
import play.lab.model.sbe.QuoteMessageEncoder;

import java.nio.ByteBuffer;

public class QuoteMessageWriter {
    private static final int MAX_LEVELS = 10;
    private static final int INITIAL_BUFFER_CAPACITY = 512; // Increased to handle multiple rungs

    private final UnsafeBuffer buffer;
    private final QuoteMessageEncoder quoteMessageEncoder;
    private final MessageHeaderEncoder headerEncoder;
    private QuoteMessageEncoder.RungEncoder rungEncoder;
    private int rungCounter;

    public QuoteMessageWriter() {
        this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(INITIAL_BUFFER_CAPACITY));
        this.quoteMessageEncoder = new QuoteMessageEncoder();
        this.headerEncoder = new MessageHeaderEncoder();
        this.rungCounter = 0;
    }

    public QuoteMessageWriter beginQuote(CurrencyPair symbol, long valueDate, long timestamp, int tenor, long clientTier, int totalRungCount) {
        if (totalRungCount > MAX_LEVELS) {
            throw new IllegalArgumentException("Total rung count (" + totalRungCount + ") exceeds maximum (" + MAX_LEVELS + ")");
        }

        // Reset buffer and state
        buffer.putInt(0, 0); // Clear first 4 bytes to avoid stale data
        quoteMessageEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        quoteMessageEncoder
                .symbol(symbol)
                .valueDate(valueDate)
                .priceCreationTimestamp(timestamp)
                .tenor(tenor)
                .clientTier(clientTier);

        rungEncoder = quoteMessageEncoder.rungCount(totalRungCount);
        rungCounter = 0;
        return this;
    }

    public QuoteMessageWriter addRung(double bid, double ask, double volume) {
        if (rungCounter >= MAX_LEVELS) {
            throw new IllegalStateException("Rung count (" + (rungCounter + 1) + ") exceeds maximum (" + MAX_LEVELS + ")");
        }
        rungEncoder.next()
                .bid(bid)
                .ask(ask)
                .volume(volume);
        rungCounter++;
        return this;
    }

    public int encodedLength() {
        return MessageHeaderEncoder.ENCODED_LENGTH + quoteMessageEncoder.encodedLength();
    }

    public UnsafeBuffer buffer() {
        return buffer;
    }

    // Convenience method for QuotePublisher
    public QuoteMessageWriter write(CurrencyPair pair, double bid, double ask) {
        return beginQuote(pair, 0L, System.currentTimeMillis(), 0, 0L, 1)
                .addRung(bid, ask, 1_000_000.0); // Default volume
    }
}
