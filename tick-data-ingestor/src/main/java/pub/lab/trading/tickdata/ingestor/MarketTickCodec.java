package pub.lab.trading.tickdata.ingestor;

import io.questdb.client.Sender;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MarketTickCodec {
    public static final int MAX_SYMBOL_BYTES = 32;
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketTickCodec.class);
    private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

    public void encode(
            final int offset,
            final String symbol,
            final long creationTsMicros,
            final int tenor,
            final long valueDate,
            final long clientTier,
            final double bid,
            final double ask,
            final double volume,
            final int level
    ) {
        // Encode symbol as UTF-8 with length prefix
        final byte[] sym = symbol.getBytes(StandardCharsets.UTF_8);
        if (sym.length > MAX_SYMBOL_BYTES) {
            throw new IllegalArgumentException("symbol too long: " + sym.length);
        }

        int p = offset;
        buffer.putInt(p, sym.length);
        p += Integer.BYTES;

        buffer.putBytes(p, sym);
        p += sym.length;

        buffer.putLong(p, creationTsMicros);
        p += Long.BYTES;
        buffer.putInt(p, tenor);
        p += Integer.BYTES;
        buffer.putLong(p, valueDate);
        p += Long.BYTES;
        buffer.putLong(p, clientTier);
        p += Long.BYTES;

        buffer.putDouble(p, bid);
        p += Double.BYTES;
        buffer.putDouble(p, ask);
        p += Double.BYTES;
        buffer.putDouble(p, volume);
        p += Double.BYTES;

        buffer.putInt(p, level);

    }

    public int decodeAndSend(final Sender sender) {
        int p = 0;

        final int symLen = buffer.getInt(p);
        p += Integer.BYTES;

        if (symLen < 0 || symLen > MAX_SYMBOL_BYTES) {
            throw new IllegalArgumentException("bad symbolLen=" + symLen);
        }

        final byte[] symBytes = new byte[symLen];
        buffer.getBytes(p, symBytes);
        p += symLen;

        final String symbol = new String(symBytes, StandardCharsets.UTF_8);

        final long creationTsMicros = buffer.getLong(p);
        p += Long.BYTES;
        final long tenor = buffer.getInt(p);
        p += Integer.BYTES;
        final long valueDate = buffer.getLong(p);
        p += Long.BYTES;
        final long clientTier = buffer.getLong(p);
        p += Long.BYTES;

        final double bid = buffer.getDouble(p);
        p += Double.BYTES;
        final double ask = buffer.getDouble(p);
        p += Double.BYTES;
        final double volume = buffer.getDouble(p);
        p += Double.BYTES;

        final long level = buffer.getInt(p);

        sender.table("quote_ticks")
                .symbol("symbol", symbol)
                .longColumn("priceCreationTimestamp", creationTsMicros) // micros
                .longColumn("tenor", tenor)
                .longColumn("valueDate", valueDate)
                .longColumn("clientTier", clientTier)
                .doubleColumn("bid", bid)
                .doubleColumn("ask", ask)
                .doubleColumn("volume", volume)
                .longColumn("level", level)
                .atNow(); // Use ingestion time as designated timestamp
        LOGGER.debug("Wrote tick to QuestDB: {} @ {} tenor={} valueDate={} tier={} bid={} ask={} vol={} level={}",
                symbol,
                creationTsMicros,
                tenor,
                valueDate,
                clientTier,
                bid,
                ask,
                volume,
                level);
        return p;
    }
}
