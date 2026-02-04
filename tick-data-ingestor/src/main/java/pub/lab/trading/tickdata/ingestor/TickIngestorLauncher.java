package pub.lab.trading.tickdata.ingestor;

import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pub.lab.trading.common.lifecycle.AgentAffinityLocker;
import pub.lab.trading.common.lifecycle.MultiStreamPoller;
import pub.lab.trading.common.lifecycle.Worker;

public class TickIngestorLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickIngestorLauncher.class);

    public static void main(String[] args) {
        String questDbConfig = "tcp::addr=localhost:9009;";
        LOGGER.info("Application Starting Up");
        try (
                QuestDBWriter writer = new QuestDBWriter(questDbConfig);
                TickAeronSubscriber subscriber = new TickAeronSubscriber(writer);
                AgentRunner agentRunner = new AgentRunner(new BackoffIdleStrategy(),
                        Throwable::printStackTrace,
                        null,
                        new MultiStreamPoller(
                                "tick-ingestion-poller",
                                new Worker[]{
                                        subscriber,
                                        writer
                                }
                        ));
                var barrier = new ShutdownSignalBarrier()
        ) {
            AgentAffinityLocker.pin(agentRunner);
            LOGGER.info("Started {}", agentRunner.agent());
            barrier.await();
            LOGGER.info("Shutting down {}", agentRunner.agent());
        }

        LOGGER.info("Application Stopped");
    }
}
