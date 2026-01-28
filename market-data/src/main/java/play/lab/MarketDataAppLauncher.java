package play.lab;

import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.marketdata.generator.FxPriceGenerator;
import pub.lab.trading.common.lifecycle.MultiStreamPoller;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.util.CachedClock;

public class MarketDataAppLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataAppLauncher.class);

    public static void main(String[] args) {
        LOGGER.info("Application Starting Up");
        try (
                AgentRunner agentRunner = new AgentRunner(new BackoffIdleStrategy(),
                        Throwable::printStackTrace,
                        null,
                        new MultiStreamPoller(
                                "pricing-engine-poller",
                                new Worker[]{
                                        new FxPriceGenerator(new CachedClock())
                                }
                        ));
                var barrier = new ShutdownSignalBarrier()
        ) {
            AgentRunner.startOnThread(agentRunner);
            LOGGER.info("Started {}", agentRunner.agent());
            barrier.await();
            LOGGER.info("Shutting down {}", agentRunner.agent());
        }
        LOGGER.info("Application Stopped");
    }
}
