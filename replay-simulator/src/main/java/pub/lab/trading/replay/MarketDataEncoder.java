package pub.lab.trading.replay;

import org.agrona.MutableDirectBuffer;

public interface MarketDataEncoder {
    /**
     * Encode one tick into dst starting at offset 0.
     * @return encoded length
     */
    int encodeTick(MutableDirectBuffer dst,
                   long tsNanos,
                   int instrumentId,
                   long bidPx,
                   long askPx,
                   long bidQty,
                   long askQty);
}
