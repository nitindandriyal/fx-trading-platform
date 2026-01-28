package pub.lab.trading.tickdata.ingestor;

import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pub.lab.trading.common.lifecycle.MultiStreamPoller;
import pub.lab.trading.common.lifecycle.Worker;

public class TickIngestorLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickIngestorLauncher.class);

    public static void main(String[] args) {
        String questDbConfig = "tcp::addr=localhost:9009;";

        QuestDBWriter writer = new QuestDBWriter(questDbConfig);
        AeronSubscriber subscriber = new AeronSubscriber(writer);

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
        AgentRunner.startOnThread(agentRunner);
        LOGGER.info("Started {}", agentRunner.agent());

    }
}
