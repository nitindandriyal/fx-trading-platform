package pub.lab.trading.tickdata.ingestor;

import io.questdb.client.Sender;

public class QuestDBWriter implements AutoCloseable {
    // Configured for ILP over TCP/HTTP
    private final Sender sender;

    public QuestDBWriter(String config) {
        this.sender = Sender.fromConfig(config);
    }

    public void writeQuote(String symbol,
                           long creationTs,
                           long tenor,
                           long valueDate,
                           long tier,
                           double bid,
                           double ask,
                           double volume,
                           long level) {
        sender.table("quote_ticks")
                .symbol("symbol", symbol)
                .longColumn("priceCreationTimestamp", creationTs) // micros
                .longColumn("tenor", tenor)
                .longColumn("valueDate", valueDate)
                .longColumn("clientTier", tier)
                .doubleColumn("bid", bid)
                .doubleColumn("ask", ask)
                .doubleColumn("volume", volume)
                .longColumn("level", level)
                .atNow(); // Use ingestion time as designated timestamp
    }

    public void flush() {
        sender.flush();
    }

    @Override
    public void close() {
        sender.close();
    }
}
