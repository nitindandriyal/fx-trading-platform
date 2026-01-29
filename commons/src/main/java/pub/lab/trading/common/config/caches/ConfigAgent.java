package pub.lab.trading.common.config.caches;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.BootstrapAckDecoder;
import play.lab.model.sbe.BootstrapCompleteDecoder;
import play.lab.model.sbe.BootstrapRequestEncoder;
import play.lab.model.sbe.ClientTierConfigMessageDecoder;
import play.lab.model.sbe.CurrencyPairConfigMessageDecoder;
import play.lab.model.sbe.MessageHeaderDecoder;
import play.lab.model.sbe.MessageHeaderEncoder;
import pub.lab.trading.common.config.AppId;
import pub.lab.trading.common.config.EnvId;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.lifecycle.Worker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static pub.lab.trading.common.config.AeronConfigs.BOOTSTRAP_CHANNEL;
import static pub.lab.trading.common.config.AeronConfigs.CONFIG_CHANNEL;

public class ConfigAgent implements Worker, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigAgent.class);

    private final Subscription configSubscription;
    private final Publication bootstrapPublication;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final CurrencyPairConfigMessageDecoder currencyDecoder = new CurrencyPairConfigMessageDecoder();
    private final ClientTierConfigMessageDecoder clientTierDecoder = new ClientTierConfigMessageDecoder();
    private final BootstrapAckDecoder ackDecoder = new BootstrapAckDecoder();
    private final BootstrapCompleteDecoder completeDecoder = new BootstrapCompleteDecoder();

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[4096]);

    private final long instanceId = ThreadLocalRandom.current().nextLong();
    private final CountDownLatch bootstrapCompleteLatch = new CountDownLatch(1);
    private final CurrencyConfigCache currencyConfigCache = new CurrencyConfigCache();
    private final ClientTierConfigCache clientTierConfigCache = new ClientTierConfigCache();
    private final AppId appId;
    private final EnvId envId;
    private long sessionId = 0L;                    // will be set on Ack
    private volatile boolean requestSent = false;

    public ConfigAgent(Aeron aeron, AppId appId, EnvId envId) {
        this.configSubscription = aeron.addSubscription(CONFIG_CHANNEL, StreamId.DATA_CONFIG_STREAM.getCode());
        this.bootstrapPublication = aeron.addExclusivePublication(BOOTSTRAP_CHANNEL, StreamId.BOOTSTRAP_STREAM.getCode());
        this.appId = appId;
        this.envId = envId;

        LOGGER.info("ConfigAgent starting — instanceId={}", instanceId);
    }

    @Override
    public String roleName() {
        return "ConfigAgent";
    }

    @Override
    public int doWork() {
        // Keep sending BootstrapRequest until we get Ack
        if (!requestSent || sessionId == 0) {
            sendBootstrapRequest();
        }

        return configSubscription.poll(this::processFragment, 10);
    }

    private void sendBootstrapRequest() {
        if (!bootstrapPublication.isConnected()) return;

        var headerEncoder = new MessageHeaderEncoder();
        var encoder = new BootstrapRequestEncoder();
        var tempBuffer = new UnsafeBuffer(new byte[512]);

        encoder.wrapAndApplyHeader(tempBuffer, 0, headerEncoder)
                .serviceId(appId.getCode())
                .env(envId.getCode())
                .instanceId(instanceId)
                .requestSeq(0)
                .timestamp(System.currentTimeMillis());

        long result = bootstrapPublication.offer(tempBuffer, 0,
                headerEncoder.encodedLength() + encoder.encodedLength());

        if (result > 0) {
            requestSent = true;
            LOGGER.debug("BootstrapRequest sent (instanceId={})", instanceId);
        }
    }

    private void processFragment(DirectBuffer buf, int offset, int length, io.aeron.logbuffer.Header header) {
        buffer.putBytes(0, buf, offset, length);
        headerDecoder.wrap(buffer, 0);
        int templateId = headerDecoder.templateId();

        // 1. Wait for our Ack
        if (templateId == BootstrapAckDecoder.TEMPLATE_ID && sessionId == 0) {
            ackDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
            if (ackDecoder.instanceId() == instanceId) {
                this.sessionId = ackDecoder.sessionId();  // ← now we know our session
                LOGGER.info("BootstrapAck received — sessionId={}", this.sessionId);
            }
            return;
        }

        // 2. During bootstrap: accept ALL config messages (safe because only one client bootstraps at a time)
        if (this.sessionId != 0) {
            switch (templateId) {
                case CurrencyPairConfigMessageDecoder.TEMPLATE_ID -> {
                    currencyDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
                    currencyConfigCache.update(currencyDecoder);
                }
                case ClientTierConfigMessageDecoder.TEMPLATE_ID -> {
                    clientTierDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
                    clientTierConfigCache.update(clientTierDecoder);
                }
            }
        }

        // 3. Final EOS
        if (templateId == BootstrapCompleteDecoder.TEMPLATE_ID) {
            completeDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
            if (completeDecoder.sessionId() == this.sessionId) {
                LOGGER.info("BootstrapComplete received — all config loaded ({} messages)", completeDecoder.configCount());
                bootstrapCompleteLatch.countDown();
            }
        }
    }

    // Zero-CPU, deterministic wait
    public void awaitBootstrapComplete() throws InterruptedException {
        LOGGER.info("Awaiting full config bootstrap...");
        if (!bootstrapCompleteLatch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Bootstrap timeout — config service not responding");
        }
        LOGGER.info("Bootstrap successful — quoting engine ready");
    }

    public CurrencyConfigCache getCurrencyConfigCache() {
        return currencyConfigCache;
    }

    public ClientTierConfigCache getClientTierConfigCache() {
        return clientTierConfigCache;
    }

    @Override
    public void close() throws Exception {
        CloseHelper.close(configSubscription);
        CloseHelper.close(bootstrapPublication);
    }
}