package pub.lab.trading.tickdata.ingestor;

import io.aeron.Aeron;
import io.aeron.Subscription;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.StreamId;

public class TickIngestorMain {
    public static void main(String[] args) {
        String qdbConfig = "tcp::addr=localhost:9009;";

        try (QuestDBWriter writer = new QuestDBWriter(qdbConfig);
             Aeron aeron = Aeron.connect();
             Subscription sub = aeron.addSubscription(AeronConfigs.LIVE_CHANNEL, StreamId.CLIENT_QUOTE.getCode())) {
            AeronSubscriber subscriber = new AeronSubscriber(writer);

            while (true) {
                // Poll Aeron for new fragments
                int fragmentsRead = sub.poll(subscriber, 10);

                // If we processed data, flush to QuestDB based on your latency requirements
                if (fragmentsRead > 0) {
                    writer.flush();
                }
            }
        }
    }
}
