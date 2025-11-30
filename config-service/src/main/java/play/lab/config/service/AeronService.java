package play.lab.config.service;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongArrayList;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.marketdata.model.MarketDataTick;
import play.lab.model.sbe.ClientTierConfigMessageDecoder;
import play.lab.model.sbe.MessageHeaderDecoder;
import play.lab.model.sbe.QuoteMessageDecoder;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.model.config.ClientTierFlyweight;
import pub.lab.trading.common.model.pricing.QuoteView;
import pub.lab.trading.common.util.MutableString;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;
import static pub.lab.trading.common.config.AeronConfigs.CONTROL_REQUEST_CHANNEL;
import static pub.lab.trading.common.config.AeronConfigs.CONTROL_RESPONSE_CHANNEL;

public enum AeronService {
    INSTANCE;
    private static final int MAX_RETRIES = 2;
    private static final String REPLAY_CHANNEL = "aeron:ipc?alias=tiers-replay";// 5 seconds
    private final Logger LOGGER = LoggerFactory.getLogger(AeronService.class);
    private final List<ClientTierFlyweight> cache = new ArrayList<>();
    private final ClientTierFlyweight flyweight = new ClientTierFlyweight();
    private final OneToOneRingBuffer ringBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(8192 + TRAILER_LENGTH)));
    private final QuoteView quoteView = new QuoteView();
    private final ConcurrentMap<String, MarketDataTick> latestTicks = new ConcurrentHashMap<>();
    private final MutableString symbolMutableString = new MutableString();
    private Aeron aeron;
    private AeronArchive archive;
    private long publicationId;
    private Publication publication;
    private Subscription subscription;
    private Subscription quoteSub;
    private long existingRecordingId;
    private volatile boolean running = true;

    public static List<Long> findRecordingsWithData(
            AeronArchive archive,
            String channel,
            int streamId
    ) {
        LongArrayList validRecordingIds = new LongArrayList();

        archive.listRecordings(0, Integer.MAX_VALUE,
                (controlSessionId, correlationId, recordingId, startTimestamp, stopTimestamp,
                 startPosition, stopPosition, initialTermId, segmentFileLength,
                 termBufferLength, mtuLength, sessionId, sId, strippedChannel,
                 originalChannel, sourceIdentity) -> {

                    if (sId == streamId &&
                            originalChannel.equals(channel) &&
                            stopPosition > startPosition) {
                        validRecordingIds.add(recordingId);
                    }
                });

        validRecordingIds.sort(Long::compareTo); // Optional: sorted by ID (ascending)
        return validRecordingIds;
    }

    private long findLatestRecording() {
        final MutableLong lastRecordingId = new MutableLong();
        final RecordingDescriptorConsumer consumer =
                (controlSessionId,
                 correlationId,
                 recordingId,
                 startTimestamp,
                 stopTimestamp,
                 startPosition,
                 stopPosition,
                 initialTermId,
                 segmentFileLength,
                 termBufferLength,
                 mtuLength,
                 sessionId,
                 streamId,
                 strippedChannel,
                 originalChannel,
                 sourceIdentity) -> lastRecordingId.set(recordingId);

        long fromRecordingId = 0L;
        int recordCount = 100;
        int foundCount = archive.listRecordingsForUri(fromRecordingId, recordCount, AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode(), consumer);

        LOGGER.info("Found latest recording : {} foundCount {}", lastRecordingId.get(), foundCount);
        return lastRecordingId.get();
    }

    public void sendTier(int tierId, String tierName, double markupBps, double spreadTighteningFactor,
                         long quoteThrottleMs, long latencyProtectionMs, long quoteExpiryMs,
                         double minNotional, double maxNotional, byte pricePrecision,
                         boolean streamingEnabled, boolean limitOrderEnabled, boolean accessToCrosses,
                         double creditLimitUsd, byte tierPriority) {

        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ClientTierFlyweight.messageSize()));

        flyweight.wrap(buffer, 0)
                .initMessage()
                .setTierId(tierId)
                .setTierName(tierName)
                .setMarkupBps(markupBps)
                .setSpreadTighteningFactor(spreadTighteningFactor)
                .setQuoteThrottleMs(quoteThrottleMs)
                .setLatencyProtectionMs(latencyProtectionMs)
                .setQuoteExpiryMs(quoteExpiryMs)
                .setMinNotional(minNotional)
                .setMaxNotional(maxNotional)
                .setPricePrecision(pricePrecision)
                .setStreamingEnabled(streamingEnabled)
                .setLimitOrderEnabled(limitOrderEnabled)
                .setAccessToCrosses(accessToCrosses)
                .setCreditLimitUsd(creditLimitUsd)
                .setTierPriority(tierPriority);

        ringBuffer.write(ClientTierConfigMessageDecoder.TEMPLATE_ID, buffer, 0, ClientTierFlyweight.messageSize());

        ClientTierFlyweight cachedFlyweight = new ClientTierFlyweight();
        MutableDirectBuffer cacheBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ClientTierFlyweight.messageSize()));
        cacheBuffer.putBytes(0, buffer, 0, ClientTierFlyweight.messageSize());
        cachedFlyweight.wrap(cacheBuffer, 0);
        cache.add(cachedFlyweight);

        LOGGER.info("Sent tier: tierId={}, tierName={}", tierId, tierName);
    }

    private void replayTiers() {
        synchronized (cache) {
            cache.clear();
        }

        long recordingId = existingRecordingId;
        if (recordingId == -1) {
            LOGGER.warn("No recordings found for {}:{}. Returning empty cache.",
                    AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
            return;
        }

        int attempt = 1;

        subscription = aeron.addSubscription(REPLAY_CHANNEL, StreamId.CONFIG_STREAM.getCode());

        LOGGER.info("Started replay for recordingId={} on {}:{} (attempt {}/{})",
                recordingId, REPLAY_CHANNEL, StreamId.CONFIG_STREAM.getCode(), attempt, MAX_RETRIES);

        FragmentHandler fragmentAssembler = (buffer1, offset, length1, header) -> {
            try {
                LOGGER.debug("Received message: length={}, offset={}", length1, offset);
                flyweight.wrap(buffer1, offset);
                flyweight.validate();
                LOGGER.debug("Validated tier: tierId={}, tierName={}", flyweight.getTierId(), flyweight.getTierNameAsString());
                ClientTierFlyweight cachedFlyweight = new ClientTierFlyweight();
                MutableDirectBuffer cacheBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ClientTierFlyweight.messageSize()));
                cacheBuffer.putBytes(0, buffer1, offset, ClientTierFlyweight.messageSize());
                cachedFlyweight.wrap(cacheBuffer, 0);
                cache.add(cachedFlyweight);
                LOGGER.info("Replayed tier: tierId={}, tierName={}", cachedFlyweight.getTierId(), cachedFlyweight.getTierNameAsString());
            } catch (Exception e) {
                LOGGER.error("Error decoding tier: {}", e.getMessage(), e);
            }
        };

        this.quoteSub = aeron.addSubscription(AeronConfigs.LIVE_CHANNEL,
                StreamId.RAW_QUOTE.getCode(),
                image -> LOGGER.info("Image available: sessionId={}, channel={}, streamId={}",
                        image.sessionId(), image.sourceIdentity(), image.subscription().streamId()),
                image -> LOGGER.warn("Image unavailable: sessionId={}, channel={}, streamId={}",
                        image.sessionId(), image.sourceIdentity(), image.subscription().streamId())
        );
        FragmentHandler quoteFragmentHandler = (buf, offset, len, hdr) -> consumeQuotes(buf, offset);
        final IdleStrategy idleStrategy = new NoOpIdleStrategy();
        while (running) {
            subscription.poll(fragmentAssembler, 10);
            quoteSub.poll(quoteFragmentHandler, 10);
            Thread.yield();
            ringBuffer.read((msgTypeId, buffer, index, length2) -> {
                // Read message from ring buffer and forward to publication
                while (publication.offer(buffer, index, length2) < 0) {
                    idleStrategy.idle(); // apply back pressure handling
                }
            }, 1);
        }

        for (long recId : findRecordingsWithData(archive, REPLAY_CHANNEL, StreamId.CONFIG_STREAM.getCode())) {
            archive.startReplay(
                    recId, 0, Integer.MAX_VALUE, REPLAY_CHANNEL, StreamId.CONFIG_STREAM.getCode()
            );
        }

    }

    private void consumeQuotes(DirectBuffer buf, int offset) {
        quoteView.wrap(buf, offset + MessageHeaderDecoder.ENCODED_LENGTH);
        quoteView.getSymbol(symbolMutableString.init());
        long timestamp = quoteView.priceCreationTimestamp();
        long tenor = quoteView.getTenor();
        long valueDate = quoteView.getValueDate();
        long clientTier = quoteView.getClientTier();

        while (quoteView.getRung().hasNext()) {
            QuoteMessageDecoder.RungDecoder nextRung = quoteView.getRung().next();
            double mid = (nextRung.bid() + nextRung.ask()) / 2.0;
            String symbol = symbolMutableString.toString();
            MarketDataTick marketDataTick;
            if (latestTicks.containsKey(symbol)) {
                marketDataTick = latestTicks.get(symbol);
                marketDataTick.setAsk(nextRung.ask());
                marketDataTick.setBid(nextRung.bid());
                marketDataTick.setMid(mid);
                marketDataTick.setValueDateEpoch(valueDate);
                marketDataTick.setTimestamp(timestamp);
            } else {
                marketDataTick = new MarketDataTick(symbol, mid, nextRung.bid(), nextRung.ask(), valueDate, timestamp);
                latestTicks.put(symbol, marketDataTick);
            }
        }

    }

    public List<ClientTierFlyweight> getCachedTiers() {
        synchronized (cache) {
            return new ArrayList<>(cache);
        }
    }

    public void shutdown() {
        try {
            running = false;
            if (publicationId != 0) {
                archive.stopRecording(publicationId);
                LOGGER.info("Stopped recording for publicationId={}", publicationId);
            }
            archive.stopRecording(AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
            LOGGER.info("Stopped recording for {}:{}", AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
            archive.close();
            aeron.close();
            LOGGER.info("AeronService shutdown completed");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown: {}", e.getMessage(), e);
        }
    }

    public void loop() {
        String aeronDir = System.getProperty("aeron.base.path") + AeronConfigs.LIVE_DIR;
        aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(aeronDir));
        archive = AeronArchive.connect(
                new AeronArchive.Context()
                        .aeron(aeron)
                        .controlRequestChannel(CONTROL_REQUEST_CHANNEL)
                        .controlResponseChannel(CONTROL_RESPONSE_CHANNEL));
        LOGGER.info("CONFIG_CHANNEL={}, STREAM_ID={}", AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
        existingRecordingId = findLatestRecording();
        AtomicBoolean alreadyRecording = new AtomicBoolean(false);
        if (!alreadyRecording.get()) {
            LOGGER.info("Stopping existing recording for {}:{} (recordingId={})",
                    AeronConfigs.CONFIG_CHANNEL,
                    StreamId.CONFIG_STREAM.getCode(),
                    existingRecordingId
            );

            try {
                if (-1 != existingRecordingId) {
                    archive.stopRecording(existingRecordingId);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to stop existing recording: {}", e.getMessage());
            }
            if (-1 == existingRecordingId) {
                existingRecordingId = archive.startRecording(AeronConfigs.CONFIG_CHANNEL,
                        StreamId.CONFIG_STREAM.getCode(),
                        SourceLocation.LOCAL
                );
            }
            alreadyRecording.set(true);
            this.publication = aeron.addPublication(AeronConfigs.CONFIG_CHANNEL,
                    StreamId.CONFIG_STREAM.getCode()
            );

        } else {
            LOGGER.info("No existing recording found for {}:{}", AeronConfigs.CONFIG_CHANNEL, StreamId.CONFIG_STREAM.getCode());
        }

        replayTiers();
    }

    public Collection<MarketDataTick> getPrices() {
        return latestTicks.values();
    }
}