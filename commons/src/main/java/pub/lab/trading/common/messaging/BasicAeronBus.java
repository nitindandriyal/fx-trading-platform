package pub.lab.trading.common.messaging;

import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;

import java.util.Objects;

/**
 * Basic AeronBus implementation
 *
 * Design goals:
 * - Single-threaded: call poll() and publish() from the same dedicated thread (per service).
 * - No allocations on hot path (other than what Aeron does internally).
 * - Works with your Aeron/SBE style: you pass DirectBuffer slices already encoded (SBE header + body).
 *
 * Notes:
 * - If you need multi-threaded publishing, use Aeron ExclusivePublication or add synchronization.
 * - If you use SBE, you'll typically encode into an UnsafeBuffer and publish(...) that.
 */
public class BasicAeronBus implements AeronBus, AutoCloseable {

    private final Subscription subscription;   // nullable if this bus is publish-only
    private final Publication publication;     // nullable if this bus is subscribe-only

    private volatile SubscriptionHandler subscriptionHandler;

    // We use a FragmentAssembler to reassemble fragmented messages and deliver them as whole fragments.
    private final FragmentAssembler fragmentAssembler;

    /**
     * Create a bus with both subscription and publication (either can be null).
     */
    public BasicAeronBus(final Subscription subscription, final Publication publication) {
        this.subscription = subscription;
        this.publication = publication;

        // FragmentAssembler ensures if Aeron fragments a big message, you still get one callback per message.
        // Bound handler to avoid capturing lambdas per poll
        FragmentHandler aeronFragmentHandler = this::handleFragment;
        this.fragmentAssembler = new FragmentAssembler(aeronFragmentHandler);
    }

    /**
     * Convenience factory for subscribe-only bus.
     */
    public static BasicAeronBus forSubscription(final Subscription subscription) {
        return new BasicAeronBus(Objects.requireNonNull(subscription, "subscription"), null);
    }

    /**
     * Convenience factory for publish-only bus.
     */
    public static BasicAeronBus forPublication(final Publication publication) {
        return new BasicAeronBus(null, Objects.requireNonNull(publication, "publication"));
    }

    @Override
    public void onMessage(final SubscriptionHandler handler) {
        this.subscriptionHandler = handler;
    }

    /**
     * Poll the Aeron subscription.
     *
     * IMPORTANT: Must be called on the service thread that owns this bus.
     */
    @Override
    public void poll(final int fragmentLimit) {
        if (subscription == null) return;
        // Aeron returns number of fragments processed (not messages). FragmentAssembler converts fragments to messages.
        subscription.poll(fragmentAssembler, fragmentLimit);
    }

    /**
     * Publish a message buffer to Aeron.
     *
     * @return true if the message was accepted by Aeron, false if back-pressured / not connected / etc.
     */
    @Override
    public boolean publish(final DirectBuffer buffer, final int offset, final int length) {
        if (publication == null) {
            throw new IllegalStateException("This AeronBusImpl has no Publication (subscribe-only bus).");
        }

        // Publication.offer returns a positive position on success, or a negative code on failure.
        final long result = publication.offer(buffer, offset, length);

        // Typical failure codes:
        // Publication.NOT_CONNECTED, BACK_PRESSURED, ADMIN_ACTION, CLOSED, MAX_POSITION_EXCEEDED
        return result > 0;
    }

    /**
     * Variant that retries a few times on back pressure (optional).
     * Keep it simple; your service idle strategy often handles retrying naturally.
     */
    public boolean publishWithRetry(final DirectBuffer buffer, final int offset, final int length, final int attempts) {
        if (publication == null) {
            throw new IllegalStateException("This AeronBusImpl has no Publication (subscribe-only bus).");
        }

        for (int i = 0; i < attempts; i++) {
            final long result = publication.offer(buffer, offset, length);
            if (result > 0) return true;

            // If not connected or closed, no point retrying aggressively
            if (result == Publication.NOT_CONNECTED || result == Publication.CLOSED) return false;
        }
        return false;
    }

    /**
     * Expose last offer failure for metrics/logging (optional).
     */
    public long offer(final DirectBuffer buffer, final int offset, final int length) {
        if (publication == null) {
            throw new IllegalStateException("This AeronBusImpl has no Publication (subscribe-only bus).");
        }
        return publication.offer(buffer, offset, length);
    }

    private void handleFragment(final DirectBuffer buffer, final int offset, final int length, final Header header) {
        final SubscriptionHandler handler = this.subscriptionHandler;
        if (handler != null) {
            handler.onFragment(buffer, offset, length);
        }
    }

    /**
     * Close underlying Aeron resources if you own them.
     * If your app owns Subscription/Publication elsewhere, don’t call close() here (or pass null).
     */
    @Override
    public void close() {
        // Aeron resources are AutoCloseable since newer versions; close quietly.
        try {
            if (subscription != null) subscription.close();
        } catch (final Exception ignored) { /* no-op */ }

        try {
            if (publication != null) publication.close();
        } catch (final Exception ignored) { /* no-op */ }
    }

    // Optional: helpers for health/metrics

    public boolean isConnected() {
        return publication != null && publication.isConnected();
    }

    public int imageCount() {
        return subscription != null ? subscription.imageCount() : 0;
    }

    public int streamId() {
        if (subscription != null) return subscription.streamId();
        if (publication != null) return publication.streamId();
        return -1;
    }

    public String channel() {
        if (subscription != null) return subscription.channel();
        if (publication != null) return publication.channel();
        return "";
    }
}
