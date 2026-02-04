package play.lab.config.service;

import io.aeron.Aeron;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.archive.codecs.SourceLocation;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import play.lab.marketdata.model.MarketDataTick;
import play.lab.model.sbe.ClientTierConfigMessageEncoder;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.lifecycle.MultiStreamPoller;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.model.config.ClientTierFlyweight;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static pub.lab.trading.common.config.AeronConfigs.CONTROL_REQUEST_CHANNEL;
import static pub.lab.trading.common.config.AeronConfigs.CONTROL_RESPONSE_CHANNEL;
import static pub.lab.trading.common.config.AeronConfigs.PUBLISH_CONFIG_CHANNEL;
import static pub.lab.trading.common.config.AeronConfigs.REPLAY_CONFIG_CHANNEL;

@Service
public class AeronService {

    private final Logger LOGGER = LoggerFactory.getLogger(AeronService.class);

    private final CountDownLatch aeronStarted;

    private final Set<ClientTierFlyweight> cache = new HashSet<>();
    private final ConcurrentMap<String, MarketDataTick> latestTicks = new ConcurrentHashMap<>();
    private final List<Recording> recordings = new ArrayList<>();
    private ConfigUpdatePoller configUpdatePoller;

    public AeronService(CountDownLatch aeronStarted) {
        this.aeronStarted = aeronStarted;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        new Thread(this::startEventLoop, "AeronService-EventLoop").start();
    }

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

        configUpdatePoller.updateNewTier(buffer);

        LOGGER.info("Enqueued for publishing: {}", clientTierConfigMessageEncoder);
    }

    public List<ClientTierFlyweight> getCachedTiers() {
        synchronized (cache) {
            return new ArrayList<>(cache);
        }
    }

    public void startEventLoop() {
        try (
                Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
                AeronArchive archive = AeronArchive.connect(
                        new AeronArchive.Context()
                                .aeron(aeron)
                                .controlRequestChannel(CONTROL_REQUEST_CHANNEL)
                                .controlResponseChannel(CONTROL_RESPONSE_CHANNEL));
                ConfigUpdatePoller configUpdatePoller = new ConfigUpdatePoller(aeron, cache);
                var barrier = new ShutdownSignalBarrier();
                AgentRunner agentRunner = new AgentRunner(new BackoffIdleStrategy(),
                        Throwable::printStackTrace,
                        null,
                        new MultiStreamPoller(
                                "config-service-poller",
                                new Worker[]{
                                        configUpdatePoller
                                }
                        ))
        ) {
            this.configUpdatePoller = configUpdatePoller;
            AgentRunner.startOnThread(agentRunner);
            LOGGER.info("Started {}", agentRunner.agent());

            long liveRecordingId = extractArchivedAndLiveRecordings(archive, PUBLISH_CONFIG_CHANNEL, StreamId.DATA_CONFIG_STREAM.getCode());
            for (Recording recording : recordings) {
                LOGGER.info("Recording found: {}", recording);
                if (recording.stopPosition == AeronArchive.NULL_POSITION) {
                    liveRecordingId = recording.recordingId;
                    break;
                }
                try {
                    if (recording.stopPosition > recording.startPosition) {
                        LOGGER.info("Replaying recordingId={} from {} to {}",
                                recording.recordingId,
                                recording.startPosition,
                                recording.stopPosition);
                        archive.replay(recording.recordingId, recording.startPosition, recording.stopPosition - recording.startPosition, REPLAY_CONFIG_CHANNEL, StreamId.DATA_CONFIG_STREAM.getCode());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to replay recordingId={}", recording, e);
                }
            }
            if (liveRecordingId < 0) {
                LOGGER.info("No existing recording found {} {}", liveRecordingId, AeronConfigs.PUBLISH_CONFIG_CHANNEL);
                archive.startRecording(AeronConfigs.PUBLISH_CONFIG_CHANNEL, StreamId.DATA_CONFIG_STREAM.getCode(), SourceLocation.LOCAL);
            }
            aeronStarted.countDown();
            barrier.await();
            LOGGER.info("Shutting down {}", agentRunner.agent());
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
                    if (stopPosition > startPosition) {
                        LOGGER.info("Found recordingId {} sessionId {} streamId {} channel {} POS : {}->{} ", recordingId, sessionId, streamId, originalChannel, startPosition, stopPosition);
                        recordings.add(new Recording(recordingId, startPosition, stopPosition, sessionId));
                        lastRecordingId.set(recordingId);
                    }
                    if (stopPosition == AeronArchive.NULL_POSITION) {
                        LOGGER.info("Found LIVE recordingId {} sessionId {} streamId {} channel {} POS : {}->LIVE ", recordingId, sessionId, streamId, originalChannel, startPosition);
                        recordings.add(new Recording(recordingId, startPosition, stopPosition, sessionId));
                        lastRecordingId.set(recordingId);
                    }
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

    public void stopEventLoop() {
        LOGGER.info("Stopping AeronService event loop...");
    }

    private record Recording(long recordingId, long startPosition, long stopPosition, int sessionId) {
    }
}