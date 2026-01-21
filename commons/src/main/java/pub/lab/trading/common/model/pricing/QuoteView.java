package pub.lab.trading.common.model.pricing;

import org.agrona.DirectBuffer;
import play.lab.model.sbe.CurrencyPair;
import play.lab.model.sbe.QuoteMessageDecoder;

import java.util.ArrayList;
import java.util.List;

public class QuoteView {
    private final QuoteMessageDecoder decoder = new QuoteMessageDecoder();

    // Wrap the buffer for decoding
    public QuoteView wrap(DirectBuffer buffer, int offset) {
        // Wrap with block length and schema version from the message header
        decoder.wrap(buffer, offset, QuoteMessageDecoder.BLOCK_LENGTH, QuoteMessageDecoder.SCHEMA_VERSION);
        return this;
    }

    // Accessor for symbol (string8)
    public CurrencyPair getSymbol() {
        // Assuming string8 is a fixed-length string of 8 bytes, adjust if variable-length
        return CurrencyPair.get(decoder.symbol().value());
    }

    // Accessor for priceCreationTimestamp (uint64)
    public long priceCreationTimestamp() {
        return decoder.priceCreationTimestamp();
    }

    // Accessor for tenor (uint32)
    public long getTenor() {
        return decoder.tenor();
    }

    // Accessor for valueDate (uint64)
    public long getValueDate() {
        return decoder.valueDate();
    }

    // Accessor for clientTier (uint32)
    public long getClientTier() {
        return decoder.clientTier();
    }

    // Get all rungs as a list for easier processing
    public List<Rung> getRungs() {
        List<Rung> rungs = new ArrayList<>();
        QuoteMessageDecoder.RungDecoder rungDecoder = decoder.rung();
        while (rungDecoder.hasNext()) {
            rungDecoder.next();
            rungs.add(new Rung(rungDecoder.bid(), rungDecoder.ask(), rungDecoder.volume()));
        }
        return rungs;
    }

    // Alternative: Direct access to RungDecoder for iterative processing
    public QuoteMessageDecoder.RungDecoder getRung() {
        return decoder.rung();
    }

    // Class to represent a single rung (bid, ask, volume)
    public static class Rung {
        private final double bid;
        private final double ask;
        private final double volume;

        public Rung(double bid, double ask, double volume) {
            this.bid = bid;
            this.ask = ask;
            this.volume = volume;
        }

        public double getBid() {
            return bid;
        }

        public double getAsk() {
            return ask;
        }

        public double getVolume() {
            return volume;
        }

        @Override
        public String toString() {
            return "Rung{" +
                    "bid=" + bid +
                    ", ask=" + ask +
                    ", volume=" + volume +
                    '}';
        }
    }

}

