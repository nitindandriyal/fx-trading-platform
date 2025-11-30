package play.lab.pricing.engine;

import io.aeron.Aeron;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.pricing.engine.feed.SpotPricerPipe;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.AppId;
import pub.lab.trading.common.config.EnvId;
import pub.lab.trading.common.config.caches.ConfigAgent;
import pub.lab.trading.common.lifecycle.HeartBeatAgent;
import pub.lab.trading.common.lifecycle.MultiStreamPoller;
import pub.lab.trading.common.lifecycle.Worker;

public class CoreEventLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreEventLoop.class);

    private final AgentRunner agentRunner;
    private final Aeron aeron;

    public CoreEventLoop(final IdleStrategy idleStrategy, final long heartbeatIntervalMs) {
        this.aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
        ConfigAgent configAgent = new ConfigAgent(aeron, AppId.PRICING_ENGINE, EnvId.valueOf(System.getenv("env")));
        agentRunner = new AgentRunner(idleStrategy, Throwable::printStackTrace, null, new MultiStreamPoller(
                "pricing-engine-poller",
                new Worker[]{
                        configAgent,
                        new SpotPricerPipe(aeron, configAgent),
                        new HeartBeatAgent(AppId.PRICING_ENGINE, heartbeatIntervalMs, aeron)
                }
        ));
    }

    void start() {
        AgentRunner.startOnThread(agentRunner);
        LOGGER.info("Started {}", agentRunner.agent());
    }

    void stop() {
        agentRunner.close();
        aeron.close();
    }
}
