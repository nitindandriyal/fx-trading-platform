package play.lab.pricing.engine;

import io.aeron.Aeron;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
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

public class SpotPricingEngineLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpotPricingEngineLauncher.class);

    public static void main(String[] args) {
        int heartbeatIntervalMs = 5_000;
        try (
                Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
                ConfigAgent configAgent = new ConfigAgent(aeron, AppId.PRICING_ENGINE, EnvId.valueOf(System.getProperty("env")));
                AgentRunner agentRunner = new AgentRunner(new BackoffIdleStrategy(),
                        Throwable::printStackTrace,
                        null,
                        new MultiStreamPoller(
                                "pricing-engine-poller",
                                new Worker[]{
                                        configAgent,
                                        new SpotPricerPipe(aeron, configAgent),
                                        new HeartBeatAgent(AppId.PRICING_ENGINE, heartbeatIntervalMs, aeron)
                                }
                        ));
                var barrier = new ShutdownSignalBarrier()
        ) {
            LOGGER.info("Application Starting Up");
            AgentRunner.startOnThread(agentRunner);
            LOGGER.info("Started {}", agentRunner.agent());
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
