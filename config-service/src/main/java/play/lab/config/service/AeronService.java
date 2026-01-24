package play.lab.config.service;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.archive.codecs.SourceLocation;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.marketdata.model.MarketDataTick;
import play.lab.model.sbe.ClientTierConfigMessageDecoder;
import play.lab.model.sbe.ClientTierConfigMessageEncoder;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.model.config.ClientTierFlyweight;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static pub.lab.trading.common.config.AeronConfigs.CONFIG_CHANNEL;
import static pub.lab.trading.common.config.AeronConfigs.CONTROL_REQUEST_CHANNEL;
import static pub.lab.trading.common.config.AeronConfigs.CONTROL_RESPONSE_CHANNEL;

public enum AeronService {
    INSTANCE;

    private final Logger LOGGER = LoggerFactory.getLogger(AeronService.class);
    private final List<ClientTierFlyweight> cache = new ArrayList<>();
    private final OneToOneConcurrentArrayQueue<UnsafeBuffer> publishQueue = new OneToOneConcurrentArrayQueue<>(10);
    private final ConcurrentMap<String, MarketDataTick> latestTicks = new ConcurrentHashMap<>();
    private Subscription subscription;
    private ExclusivePublication publication;

    private final List<Recording> recordings = new ArrayList<>();

    private record Recording(long recordingId, long startPosition, long stopPosition) {
    }

    ;

    public void sendTier(int tierId, String tierName, double markupBps, double spreadTighteningFactor,
                         long quoteThrottleMs, long latencyProtectionMs, long quoteExpiryMs,
                         double minNotional, double maxNotional, byte pricePrecision,
                         boolean streamingEnabled, boolean limitOrderEnabled, boolean accessToCrosses,
                         double creditLimitUsd, byte tierPriority) {
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
        ClientTierConfigMessageEncoder clientTierConfigMessageEncoder = new ClientTierConfigMessageEncoder();
        clientTierConfigMessageEncoder.wrap(buffer, 0)
                .tierId(tierId)
                .tierName(tierName)
                .markupBps(markupBps)
                .spreadTighteningFactor(spreadTighteningFactor)
                .quoteThrottleMs(quoteThrottleMs)
                .latencyProtectionMs(latencyProtectionMs)
                .quoteExpiryMs(quoteExpiryMs)
                .minNotional(minNotional)
                .maxNotional(maxNotional)
                .pricePrecision(pricePrecision)
                .streamingEnabled(streamingEnabled ? play.lab.model.sbe.BooleanEnum.True : play.lab.model.sbe.BooleanEnum.False)
                .limitOrderEnabled(limitOrderEnabled ? play.lab.model.sbe.BooleanEnum.True : play.lab.model.sbe.BooleanEnum.False)
                .accessToCrosses(accessToCrosses ? play.lab.model.sbe.BooleanEnum.True : play.lab.model.sbe.BooleanEnum.False)
                .creditLimitUsd(creditLimitUsd)
                .tierPriority(tierPriority);

        publishQueue.offer(buffer);
        LOGGER.info("Enqueued for publishing: {}", clientTierConfigMessageEncoder);
    }

    public List<ClientTierFlyweight> getCachedTiers() {
        synchronized (cache) {
            return new ArrayList<>(cache);
        }
    }

    public void loop() {
        Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
        AeronArchive archive = AeronArchive.connect(
                new AeronArchive.Context()
                        .aeron(aeron)
                        .controlRequestChannel(CONTROL_REQUEST_CHANNEL)
                        .controlResponseChannel(CONTROL_RESPONSE_CHANNEL));
        LOGGER.info("CONFIG_CHANNEL={}, STREAM_ID={}", AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
        subscription = aeron.addSubscription(AeronConfigs.CONFIG_CHANNEL,
                StreamId.CONFIG_STREAM.getCode());
        publication = aeron.addExclusivePublication(AeronConfigs.CONFIG_CHANNEL,
                StreamId.CONFIG_STREAM.getCode()
        );

        long liveRecordingId = extractArchivedAndLiveRecordings(archive, CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
        for (Recording recording : recordings) {
            LOGGER.info("Recording found: {}", recording);
            if (recording.stopPosition == AeronArchive.NULL_POSITION) {
                liveRecordingId = recording.recordingId;
                break;
            }
            archive.replay(recording.recordingId, 0L, Long.MAX_VALUE, AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
        }
        startSubscription(aeron, archive, liveRecordingId);
    }

    private void startSubscription(Aeron aeron, AeronArchive archive, long foundRecordingId) {

        if (foundRecordingId < 0) {
            LOGGER.info("No existing recording found {} {}", foundRecordingId, AeronConfigs.CONFIG_CHANNEL);
            archive.startRecording(AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode(), SourceLocation.LOCAL);
        }

        while (!subscription.isConnected()) {
            new NoOpIdleStrategy().idle();
        }
        LOGGER.info("Subscription connected to {} on stream {}", subscription.channel(), subscription.streamId());

        while (true) {

            UnsafeBuffer buffer = publishQueue.poll();

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

            int fragments = subscription.poll((buffer1, offset1, length1, header1) -> {
                onClientTierConfigUpdate(buffer1, offset1);
            }, 10);

            if (fragments == 0) {
                Thread.yield();
            }
        }
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

    private long extractArchivedAndLiveRecordings(final AeronArchive archive, final String channel, final int stream) {
        AtomicLong lastRecordingId = new AtomicLong(-1L);
        recordings.clear();
        final RecordingDescriptorConsumer consumer =
                (controlSessionId, correlationId, recordingId,
                 startTimestamp, stopTimestamp, startPosition,
                 stopPosition, initialTermId, segmentFileLength,
                 termBufferLength, mtuLength, sessionId,
                 streamId, strippedChannel, originalChannel,
                 sourceIdentity) -> {
                    LOGGER.info("Found recordingId {} sessionId {} streamId {} channel {} POS : {}->{} ", recordingId, sessionId, streamId, channel, startPosition, stopPosition);
                    recordings.add(new Recording(recordingId, startPosition, stopPosition));
                    lastRecordingId.set(recordingId);
                };

        final long fromRecordingId = 0L;
        final int recordCount = 100;
        long countOfItemsReturned = archive.listRecordingsForUri(fromRecordingId, recordCount, channel, stream, consumer);
        if (countOfItemsReturned == 0) {
            LOGGER.info("no existing recordings found");
        } else {
            recordings.sort(Comparator.comparing(d -> d.recordingId));
            LOGGER.info("recordings {}", recordings);
        }
        return lastRecordingId.get();
    }

    public Collection<MarketDataTick> getPrices() {
        return latestTicks.values();
    }
}