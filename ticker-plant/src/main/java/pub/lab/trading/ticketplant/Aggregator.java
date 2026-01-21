package pub.lab.trading.ticketplant;

import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;
import play.lab.model.sbe.GoldenTickEncoder;
import play.lab.model.sbe.MessageHeaderEncoder;
import play.lab.model.sbe.VenueID;
import play.lab.model.sbe.VenueTickDecoder;
import pub.lab.trading.common.util.CachedClock;

import java.nio.ByteBuffer;

public class Aggregator {
    private final int INITIAL_BUFFER_CAPACITY = 512;
    private final long[][] venueStates = new long[8][4];
    private final GoldenTickEncoder encoder = new GoldenTickEncoder();
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(INITIAL_BUFFER_CAPACITY));
    private final Publication publication;
    private final CachedClock clock;
    public Aggregator(final Publication publication, final CachedClock clock) {
        this.publication = publication;
        this.clock = clock;
    }

    public void onVenueTick(VenueTickDecoder tick) {
        int vId = tick.venueId().value();

        // 1. Update Internal VOB State
        venueStates[vId][0] = tick.bidPrice();
        venueStates[vId][1] = tick.bidSize();
        venueStates[vId][2] = tick.askPrice();
        venueStates[vId][3] = tick.askSize();

        // 2. Calculate Global Best (Golden BBO)
        long bestBid = 0;
        long bestAsk = Long.MAX_VALUE;
        short bestBidVenue = 0, bestAskVenue = 0;

        for (short i = 0; i < venueStates.length; i++) {
            if (venueStates[i][0] > bestBid) { // Find Max Bid
                bestBid = venueStates[i][0];
                bestBidVenue = i;
            }
            if (venueStates[i][2] > 0 && venueStates[i][2] < bestAsk) { // Find Min Ask
                bestAsk = venueStates[i][2];
                bestAskVenue = i;
            }
        }

        // 3. Arbitrage Check (Crossed Market Protection)
        if (bestBid >= bestAsk) {
            // LOGIC: Market is crossed! Potentially ignore or widen.
            return;
        }

        // 4. Publish to Aeron if the BBO has changed
        publishGoldenTick(tick.securityId(), bestBid, bestAsk, bestBidVenue, bestAskVenue);
    }

    private void publishGoldenTick(long secId, long bid, long ask, short bV, short aV) {
        encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
                .securityId(secId)
                .bestBidPrice(bid)
                .bestAskPrice(ask)
                .bestBidVenue(VenueID.get(bV))
                .bestAskVenue(VenueID.get(aV))
                .tpTimestamp(clock.nanoTime());

        publication.offer(buffer, 0, encoder.encodedLength());
    }
}
