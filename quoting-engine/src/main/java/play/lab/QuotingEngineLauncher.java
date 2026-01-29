package play.lab;

import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.AppId;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.lifecycle.HeartBeatAgent;
import pub.lab.trading.common.lifecycle.MultiStreamPoller;
import pub.lab.trading.common.lifecycle.Worker;

public class QuotingEngineLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuotingEngineLauncher.class);

    public static void main(String[] args) {
        try (Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR));
             Subscription rawMarketDataSub = aeron.addSubscription(AeronConfigs.LIVE_CHANNEL, StreamId.DATA_MARKET_QUOTE.getCode());
             Subscription clientStreamsControlSub = aeron.addSubscription(AeronConfigs.LIVE_CHANNEL, StreamId.CONTROL_CLIENT_QUOTE.getCode());
             ShutdownSignalBarrier shutdownSignalBarrier = new ShutdownSignalBarrier()
        ) {
            LOGGER.info("Starting Application QuotingEngine");
            AgentRunner agentRunner = new AgentRunner(
                    new BackoffIdleStrategy(),
                    Throwable::printStackTrace,
                    null,
                    new MultiStreamPoller(
                            "quoting-engine-poller",
                            new Worker[]{
                                    new HeartBeatAgent(AppId.QUOTING_ENGINE, 5_000, aeron),
                                    new RawMarketDataPoller(AppId.QUOTING_ENGINE, rawMarketDataSub)
                            }
                    ));
            AgentRunner.startOnThread(agentRunner);
            shutdownSignalBarrier.await();
            LOGGER.info("Shutting down {}", agentRunner.agent());
        }
    }
}
