package play.lab.config.service;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.ClientTierConfigMessageDecoder;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.model.config.ClientTierFlyweight;

import java.nio.ByteBuffer;
import java.util.Set;

public class ConfigUpdatePoller implements Worker, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpdatePoller.class);

    private final Subscription subscription;
    private final ExclusivePublication publication;
    private final Set<ClientTierFlyweight> cache;
    private final OneToOneConcurrentArrayQueue<UnsafeBuffer> publishQueue = new OneToOneConcurrentArrayQueue<>(10);
    private boolean connected = false;

    public ConfigUpdatePoller(final Aeron aeron,
                              final Set<ClientTierFlyweight> cache) {
        this.subscription = aeron.addSubscription(AeronConfigs.REPLAY_CONFIG_CHANNEL,
                StreamId.DATA_CONFIG_STREAM.getCode());
        this.publication = aeron.addExclusivePublication(AeronConfigs.PUBLISH_CONFIG_CHANNEL,
                StreamId.DATA_CONFIG_STREAM.getCode()
        );
        this.cache = cache;
    }

    @Override
    public int doWork() throws Exception {
        while (!subscription.isConnected()) {
            new NoOpIdleStrategy().idle();
        }
        if (!connected) {
            LOGGER.info("✅ Config subscription connected streamId={} channel={}", subscription.streamId(), subscription.channel());
            connected = true;
        }

        if (!publishQueue.isEmpty()) {
            UnsafeBuffer buffer = publishQueue.poll();
            LOGGER.info("Publishing config from queue, remaining size={}", publishQueue.size());
            if (buffer != null) {
                ClientTierConfigMessageDecoder clientTierConfigMessageDecoder = new ClientTierConfigMessageDecoder();
                clientTierConfigMessageDecoder.wrap(buffer, 0, ClientTierConfigMessageDecoder.BLOCK_LENGTH, ClientTierConfigMessageDecoder.SCHEMA_VERSION);
                LOGGER.info("Publishing tierId={} tierName={}", clientTierConfigMessageDecoder.tierId(), clientTierConfigMessageDecoder.tierName());
                long result = publication.offer(buffer, 0, ClientTierConfigMessageDecoder.BLOCK_LENGTH);
                if (result < 0) {
                    LOGGER.error("❌ Failed to publish config result {} size {} stream {}", result, ClientTierFlyweight.messageSize(), publication.streamId());
                } else {
                    LOGGER.info("✅ Published config stream {} tierId={}, tierName={}", publication.streamId(), clientTierConfigMessageDecoder.tierId(), clientTierConfigMessageDecoder.tierName());
                }
            }
        }

        int fragments = subscription.poll((buffer1, offset1, length1, header1) -> {
            onClientTierConfigUpdate(buffer1, offset1);
        }, 10);

        if (fragments == 0) {
            Thread.yield();
        }

        return fragments;
    }

    private void onClientTierConfigUpdate(DirectBuffer buffer1, int offset1) {
        if (buffer1 != null) {
            ClientTierFlyweight clientTierFlyweight = new ClientTierFlyweight();
            ClientTierConfigMessageDecoder clientTierConfigMessageDecoder1 = new ClientTierConfigMessageDecoder();
            clientTierConfigMessageDecoder1.wrap(buffer1, offset1, ClientTierConfigMessageDecoder.BLOCK_LENGTH, ClientTierConfigMessageDecoder.SCHEMA_VERSION);

            UnsafeBuffer buffer2 = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
            clientTierFlyweight.wrap(buffer2, 0)
                    .initMessage()
                    .setTierId(clientTierConfigMessageDecoder1.tierId())
                    .setTierName(clientTierConfigMessageDecoder1.tierName())
                    .setMarkupBps(clientTierConfigMessageDecoder1.markupBps())
                    .setSpreadTighteningFactor(clientTierConfigMessageDecoder1.spreadTighteningFactor())
                    .setQuoteThrottleMs(clientTierConfigMessageDecoder1.quoteThrottleMs())
                    .setLatencyProtectionMs(clientTierConfigMessageDecoder1.latencyProtectionMs())
                    .setQuoteExpiryMs(clientTierConfigMessageDecoder1.quoteExpiryMs())
                    .setMinNotional(clientTierConfigMessageDecoder1.minNotional())
                    .setMaxNotional(clientTierConfigMessageDecoder1.maxNotional())
                    .setPricePrecision((byte) clientTierConfigMessageDecoder1.pricePrecision())
                    .setStreamingEnabled(clientTierConfigMessageDecoder1.streamingEnabled() == play.lab.model.sbe.BooleanEnum.True)
                    .setLimitOrderEnabled(clientTierConfigMessageDecoder1.limitOrderEnabled() == play.lab.model.sbe.BooleanEnum.True)
                    .setAccessToCrosses(clientTierConfigMessageDecoder1.accessToCrosses() == play.lab.model.sbe.BooleanEnum.True)
                    .setCreditLimitUsd(clientTierConfigMessageDecoder1.creditLimitUsd())
                    .setTierPriority((byte) clientTierConfigMessageDecoder1.tierPriority());
            LOGGER.info("Received tierId={} tierName={}", clientTierConfigMessageDecoder1.tierId(), clientTierConfigMessageDecoder1.tierName());
            cache.add(clientTierFlyweight);
        }
    }

    @Override
    public String roleName() {
        return "ConfigUpdatePoller";
    }

    @Override
    public void close() {
        LOGGER.info("Closing ConfigUpdatePoller Pub/Sub");
        publication.close();
        subscription.close();
    }

    public void updateNewTier(UnsafeBuffer buffer) {
        publishQueue.offer(buffer);
    }
}
