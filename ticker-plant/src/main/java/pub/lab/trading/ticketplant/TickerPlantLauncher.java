package pub.lab.trading.ticketplant;

import io.aeron.Publication;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import play.lab.model.sbe.MessageHeaderDecoder;
import play.lab.model.sbe.VenueTickDecoder;
import pub.lab.trading.common.util.CachedClock;

import java.nio.ByteBuffer;

public class TickerPlantLauncher {
    private static final int RING_BUFFER_CAPACITY = 1024 * 1024; // 1MB, adjustable
    private static final int VENUE_TICK_MESSAGE_TYPE = BitUtil.align(BitUtil.SIZE_OF_INT, BitUtil.CACHE_LINE_LENGTH); // Custom msg type for ring buffer

    private final ManyToOneRingBuffer ingressQueue;
    private final Journaler journaler;
    private final Aggregator aggregator;
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final VenueTickDecoder venueTickDecoder = new VenueTickDecoder();
    private final UnsafeBuffer tempBuffer = new UnsafeBuffer(new byte[1024]); // Temp for polling

    public TickerPlantLauncher(Publication goldenPublication, CachedClock clock, String journalPath) {
        this.ingressQueue = new ManyToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(RING_BUFFER_CAPACITY + RingBufferDescriptor.TRAILER_LENGTH)));
        this.journaler = new Journaler(journalPath);
        this.aggregator = new Aggregator(goldenPublication, clock);
    }

    public ManyToOneRingBuffer getIngressQueue() {
        return ingressQueue;
    }

    public void run() {
        IdleStrategy idleStrategy = new BackoffIdleStrategy();
        while (true) {
            int workCount = ingressQueue.read(this::handleMessage, 10);
            idleStrategy.idle(workCount);
        }
    }

    private void handleMessage(int msgTypeId, DirectBuffer buffer, int index, int length) {
        if (msgTypeId == VENUE_TICK_MESSAGE_TYPE) {
            // Copy to temp for decoding (if needed)
            tempBuffer.putBytes(0, buffer, index, length);

            // Journal the raw message first for durability
            journaler.write(tempBuffer, 0, length);

            // Decode and process
            headerDecoder.wrap(tempBuffer, 0);
            int templateId = headerDecoder.templateId();
            if (templateId == VenueTickDecoder.TEMPLATE_ID) {
                venueTickDecoder.wrap(tempBuffer, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
                aggregator.onVenueTick(venueTickDecoder);
            }
        }
    }

    public void shutdown() {
        journaler.close();
    }
}
